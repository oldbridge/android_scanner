package com.example.scanner

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location

open class Device(var name: String?, val timestamp: Long, val position: Location, val address: String?, val power: Int) {
    fun basicCsv(): String {
        return "$timestamp,${position.getLatitude()},${position.getLongitude()},${position.getAltitude()},$name,$address,$power"
    }
    open fun toCsv(): String {
        return "BT,${basicCsv()}"
    }
}

class CellDevice(name: String, timestamp: Long, position: Location, address: String, power: Int, val inUse: Int, val type: String):Device(name, timestamp, position, address, power) {
    override fun toCsv(): String {
        return "CELL,${basicCsv()},$inUse,$type"
    }
}

class WifiDevice(name: String, timestamp: Long, position: Location, address: String, power: Int, frequency: Int, channel_width:Int, capabilities: String) : Device(name, timestamp, position, address, power) {
    val frequency = frequency
    val channel_width = channel_width
    val capabilities = capabilities

    override fun toCsv(): String {
        return "WIFI,${basicCsv()},0,WIFI,$frequency,$channel_width,$capabilities"
    }
}

class DeviceDatabase(
    context: Context,
    factory: SQLiteDatabase.CursorFactory?,
    storage_path: String,
    val session_start: Long
) :
    SQLiteOpenHelper(
        context, storage_path + "/devices.db3",
        factory, DATABASE_VERSION
    ) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME) // only to avoid changing version number, drop table always
        val CREATE_TABLE = ("CREATE TABLE " +
                TABLE_NAME + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY," +
                COLUMN_NAME + " TEXT," +
                COLUMN_TYPE + " TEXT," +
                COLUMN_POS_LAT + " REAL, " +
                COLUMN_POS_LONG + " REAL, " +
                COLUMN_POS_HEIGHT + " REAL, " +
                COLUMN_ADDRESS + " TEXT," +
                COLUMN_POWER + " INTEGER," +
                COLUMN_TIMESTAMP + " INTEGER," +
                COLUMN_CAPABILITIES + " TEXT," +
                COLUMN_CHANNEL_WIDTH + " INTEGER," +
                COLUMN_FREQUENCY + " INTEGER," +
                COLUMN_INUSE + " INTEGER," +
                COLUMN_CELL_TYPE + " TEXT" +
                ")")
        db.execSQL(CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME)
        onCreate(db)
    }

    fun add_basics(values: ContentValues, device: Device) {
        values.put(COLUMN_NAME, device.name)
        values.put(COLUMN_POS_LAT, device.position.getLatitude())
        values.put(COLUMN_POS_LONG, device.position.getLongitude())
        values.put(COLUMN_POS_HEIGHT, device.position.getAltitude())
        values.put(COLUMN_TIMESTAMP, device.timestamp)
        values.put(COLUMN_ADDRESS, device.address)
        values.put(COLUMN_POWER, device.power)
    }

    fun addBTDevice(device: Device) {
        val values = ContentValues()
        values.put(COLUMN_TYPE, TYPE_BT)
        add_basics(values, device)

        val db = this.writableDatabase
        db.insert(TABLE_NAME, null, values)
        db.close()
    }

    fun addCellDevice(device: CellDevice) {
        val values = ContentValues()
        values.put(COLUMN_TYPE, TYPE_CELL)
        add_basics(values, device)
        values.put(COLUMN_INUSE, device.inUse)
        values.put(COLUMN_CELL_TYPE, device.type)
        val db = this.writableDatabase
        db.insert(TABLE_NAME, null, values)
        db.close()
    }

    fun addWifiDevice(device: WifiDevice) {
        val values = ContentValues()
        values.put(COLUMN_TYPE, TYPE_WIFI)
        add_basics(values, device)
        values.put(COLUMN_FREQUENCY, device.frequency)
        values.put(COLUMN_CHANNEL_WIDTH, device.channel_width)
        values.put(COLUMN_CAPABILITIES, device.capabilities)

        val db = this.writableDatabase
        db.insert(TABLE_NAME, null, values)
        db.close()
    }

    private fun get_new_request(db: SQLiteDatabase, type_column: String) : Int{
        return db.rawQuery("SELECT DISTINCT $COLUMN_ADDRESS FROM $TABLE_NAME WHERE $COLUMN_TYPE == \"$type_column\" AND $COLUMN_TIMESTAMP > $session_start EXCEPT SELECT $COLUMN_ADDRESS FROM $TABLE_NAME WHERE $COLUMN_TYPE == \"$type_column\" and $COLUMN_TIMESTAMP < $session_start", null).getCount()
    }
    fun getCount_str(): String {
        val db = this.readableDatabase
        // Get total count from dB
        val n_cells = db.rawQuery("SELECT DISTINCT $COLUMN_ADDRESS FROM $TABLE_NAME WHERE $COLUMN_TYPE == \"$TYPE_CELL\"", null).getCount()
        val n_bt = db.rawQuery("SELECT DISTINCT $COLUMN_ADDRESS FROM $TABLE_NAME WHERE $COLUMN_TYPE == \"$TYPE_BT\"", null).getCount()
        val n_wifi = db.rawQuery("SELECT DISTINCT $COLUMN_ADDRESS FROM $TABLE_NAME WHERE $COLUMN_TYPE ==\"$TYPE_WIFI\"", null).getCount()

        // Get count of new ones
        val n_new_cells = get_new_request(db, TYPE_CELL)
        val n_new_bt = get_new_request(db, TYPE_BT)
        val n_new_wifi = get_new_request(db, TYPE_WIFI)

        val last_time_c = db.rawQuery("SELECT DISTINCT $COLUMN_TIMESTAMP FROM $TABLE_NAME ORDER BY $COLUMN_ID DESC LIMIT 1;", null)
        last_time_c.moveToFirst()
        val last_time = last_time_c.getLong(last_time_c.getColumnIndex(COLUMN_TIMESTAMP))
        val last_time_str = java.time.format.DateTimeFormatter.ISO_INSTANT
            .format(java.time.Instant.ofEpochSecond(last_time))
        db.close()
        return "Last time: $last_time_str\nCell-stations: $n_cells, Bluetooth: $n_bt, WIFI: $n_wifi\n New cells: $n_new_cells, New BT: $n_new_bt, New WIFI: $n_new_wifi"

    }

    fun getLastPos_str(): String {
        val db = this.readableDatabase
        var c = db.rawQuery("SELECT $COLUMN_POS_LAT,$COLUMN_POS_LONG,$COLUMN_POS_HEIGHT FROM $TABLE_NAME ORDER BY $COLUMN_ID DESC LIMIT 1", null) as Cursor
        c.moveToFirst()
        return "Last position: ${"%.4f".format(c.getDouble(0))} / ${"%.4f".format(c.getDouble(1))} / ${"%.1f".format(c.getDouble(2))}"
    }

    fun getAllData(): Cursor? {
        val db = this.readableDatabase
        return db.rawQuery("SELECT * FROM $TABLE_NAME", null)
    }

    companion object {
        private val DATABASE_VERSION = 9
        val TABLE_NAME = "devices"
        val COLUMN_ID = "_id"
        val COLUMN_NAME = "name"
        val COLUMN_POS_LAT = "latitude"
        val COLUMN_POS_LONG = "longitude"
        val COLUMN_POS_HEIGHT = "height"
        val COLUMN_TIMESTAMP = "timestamp"
        val COLUMN_ADDRESS = "address"
        val COLUMN_POWER = "power"
        val COLUMN_INUSE = "linked"
        val COLUMN_CAPABILITIES = "capabilities"
        val COLUMN_FREQUENCY = "frequency"
        val COLUMN_CHANNEL_WIDTH = "channel_width"
        val COLUMN_TYPE = "type"
        val COLUMN_CELL_TYPE = "cell_type"
        val TYPE_BT = "BT"
        val TYPE_WIFI = "WIFI"
        val TYPE_CELL = "CELL"
    }
}