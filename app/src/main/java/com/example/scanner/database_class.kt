package com.example.scanner

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location

open class Device(name: String, timestamp: Long, position: Location, address: String, power: Int) {
    val name = name
    val timestamp = timestamp
    val position = position
    val address = address
    val power = power

    fun basicCsv(): String {
        return "$timestamp,${position.getLatitude()},${position.getLongitude()},${position.getAltitude()},$name,$address,$power"
    }
    open fun toCsv(): String {
        return "BT,${basicCsv()}"
    }
}

class CellDevice(name: String, timestamp: Long, position: Location, address: String, power: Int, inUse: Int):Device(name, timestamp, position, address, power) {
    val inUse = inUse
    override fun toCsv(): String {
        return "CELL,${basicCsv()},$inUse"
    }
}

class WifiDevice(name: String, timestamp: Long, position: Location, address: String, power: Int, frequency: Int, channel_width:Int, capabilities: String) : Device(name, timestamp, position, address, power) {
    val frequency = frequency
    val channel_width = channel_width
    val capabilities = capabilities

    override fun toCsv(): String {
        return "WIFI,${basicCsv()},0,$frequency,$channel_width,$capabilities"
    }
}

class DeviceDatabase(
    context: Context,
    factory: SQLiteDatabase.CursorFactory?,
    storage_path: String
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
                COLUMN_INUSE + " INTEGER" +
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

    fun getAllData(): Cursor? {
        val db = this.readableDatabase
        return db.rawQuery("SELECT * FROM $TABLE_NAME", null)
    }

    companion object {
        private val DATABASE_VERSION = 8
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
        val TYPE_BT = "BT"
        val TYPE_WIFI = "WIFI"
        val TYPE_CELL = "CELL"
    }
}