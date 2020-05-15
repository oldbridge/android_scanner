package com.example.scanner

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.telephony.*
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileWriter

class Scanner() {
    // inside a basic activity
    private var context: Context? = null

    private var locationManager: LocationManager? = null
    private val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var wifiManager: WifiManager? = null
    private var telephonyManager: TelephonyManager? = null
    private var scan_time = 0L
    var current_location = Location("dummyprovider")
    private var stop: Boolean = false
    var scan_delay: Long = 4000
    private var db: DeviceDatabase? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Initialize the broadcast receiver
    val bReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND == action) {
                // Get the BluetoothDevice object from the Intent
                val device =
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val device_rssi =
                    intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0)
                // add the name and the MAC address of the object to the arrayAdapter
                //result_db.add_bt("${current_location.print()},$scan_time,${device.name},${device.address},$device_rssi")
                println(device.toString())
                db?.addBTDevice(
                    Device(
                        device?.getName(),
                        scan_time,
                        current_location,
                        device?.getAddress(),
                        device_rssi.toInt()
                    )
                )
            }
        }
    }

    // Create the Handler object (on the main thread by default)
    val handler = Handler()

    val periodicScans: Runnable = object : Runnable {
        override fun run() {
            // Repeat this the same runnable code block again another 2 seconds
            // 'this' is referencing the Runnable object
            perform_scan()
            if (!stop) handler.postDelayed(this, scan_delay)
            else stop = false
        }
    }

    fun initialize_all(pass_context: Context) {
        // Initialize context
        context = pass_context

        // Setup db
        db = DeviceDatabase(context!!, null, context?.getExternalFilesDir(null).toString())

        // Create telephony manager reference
        telephonyManager = context?.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // Create persistent LocationManager reference
        locationManager = context?.getSystemService(AppCompatActivity.LOCATION_SERVICE) as LocationManager?
        //locationManager?.registerGnssStatusCallback(gnss_callbacks)
        start_positioning()

        // Get the default adapter
        init_bt()
        init_wifi()

        // Setup wakelock
        val mgr: PowerManager = context?.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "app:MyWakeLock")

    }
    private fun init_bt() {
        val turnOnIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        if (bluetoothAdapter.isEnabled() == false) {
            context?.startActivity(turnOnIntent)
        }
        context?.registerReceiver(bReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
    }

    private fun init_wifi() {
        wifiManager = context?.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    fun start_scan() {
        stop = false
        wakeLock?.acquire()

        handler.post(periodicScans)
    }

    fun stop_scan() {
        wakeLock?.release()
        stop = true
        stop_positioning()
    }

    private fun perform_scan() {
        scan_time = System.currentTimeMillis() / 1000
        start_bt_scan()
        get_cell_updates()
        start_wifi_scan()

        // Also update the GUI with current or previous results and time indicators
        //consoleTextView?.setText(db?.getCount_str())
    }

    private fun start_wifi_scan() {
        val results = wifiManager?.getScanResults() as List<ScanResult>
        for (r in results) {
            println(r.toString())
            db?.addWifiDevice(
                WifiDevice(
                    r.SSID,
                    scan_time,
                    current_location,
                    r.BSSID,
                    r.level,
                    r.frequency,
                    r.channelWidth,
                    r.capabilities
                )
            )
        }
    }

    private fun start_bt_scan() {
        bluetoothAdapter.startDiscovery()
    }


    fun get_cell_updates() {
        val cells = telephonyManager?.getAllCellInfo() as List<CellInfo>
        for (cell in cells) {
            println(cell.toString())
            if (cell is CellInfoLte) {
                val cell_id = cell.cellIdentity
                if (cell.isRegistered()) {
                    println("Got a LTE cell (registered)")
                    db?.addCellDevice(
                        CellDevice(
                            cell_id.operatorAlphaLong.toString(),
                            scan_time,
                            current_location,
                            "${cell_id.getMccString()}_${cell_id.getMncString()}_${cell_id.getTac()}_${cell_id.getCi()}_${cell_id.getEarfcn()}",
                            cell.cellSignalStrength.getDbm(),
                            1,
                            "LTE"
                        )
                    )
                }
                else {
                    println("Got a LTE cell (not registered)")
                    db?.addCellDevice(
                        CellDevice(
                            cell_id.operatorAlphaLong.toString(),
                            scan_time,
                            current_location,
                            "${cell_id.getPci()}_${cell_id.getEarfcn()}",
                            cell.cellSignalStrength.getDbm(),
                            0,
                            "LTE"
                        )
                    )
                }
            } else if (cell is CellInfoGsm) {
                val cell_id = cell.cellIdentity
                if (cell.isRegistered()) {  // Only registered cells seem to contain MCC, MNC, CID and LAT info
                    println("Got a GSM cell (registered)")

                    db?.addCellDevice(
                        CellDevice(
                            cell_id.operatorAlphaLong.toString(),
                            scan_time,
                            current_location,
                            "${cell_id.getMccString()}_${cell_id.getMncString()}_${cell_id.getLac()}_${cell_id.getCid()}_${cell_id.getArfcn()}",
                            cell.cellSignalStrength.getDbm(),
                            1,
                            "GSM"
                        )
                    )
                } else {
                    println("Got a GSM cell (not registered)")
                    db?.addCellDevice(
                        CellDevice(
                            cell_id.operatorAlphaLong.toString(),
                            scan_time,
                            current_location,
                            "${cell_id.getCid()}_${cell_id.getArfcn()}",
                            cell.cellSignalStrength.getDbm(),
                            0,
                            "GSM"
                        )
                    )
                }

            } else if (cell is CellInfoWcdma) {
                val cell_id = cell.cellIdentity
                if (cell.isRegistered()) {  // Only registered cells seem to contain MCC, MNC, CID and LAT info
                    println("Got a WCDMA cell (registered)")

                    db?.addCellDevice(
                        CellDevice(
                            cell_id.operatorAlphaLong.toString(),
                            scan_time,
                            current_location,
                            "${cell_id.getMccString()}_${cell_id.getMncString()}_${cell_id.getLac()}_${cell_id.getCid()}_${cell_id.getUarfcn()}",
                            cell.cellSignalStrength.getDbm(),
                            1,
                            "WCDMA"
                        )
                    )
                } else {
                    println("Got a WCDMA cell (not registered)")
                    db?.addCellDevice(
                        CellDevice(
                            cell_id.operatorAlphaLong.toString(),
                            scan_time,
                            current_location,
                            "${cell_id.getCid()}_${cell_id.getPsc()}_${cell_id.getUarfcn()}",
                            cell.cellSignalStrength.getDbm(),
                            0,
                            "WCDMA"
                        )
                    )
                }
            } else if (cell is CellInfoCdma) {
                println("Got a CDMA cell") // CDMA just used in America, left out of implementation
            }
            // TDSCDMA is implemented first from API 29 on (left out)
        }
    }

    private fun get_log_file(): File {
        println()
        return File(context?.getExternalFilesDir(null), "dump.csv")
    }

    fun save_results() {
        var fileWriter = FileWriter(get_log_file(), false)
        val cursor = db?.getAllData()

        //cursor!!.moveToFirst()
        val headers = cursor?.getColumnNames() as Array<String>
        var header_row = ""
        for (h in headers) header_row = "$header_row$h,"
        fileWriter.write("$header_row\n")
        while (cursor.moveToNext()) {
            //Which column you want to exprort
            var row_csv = ""
            for (column in headers.indices)  row_csv = "$row_csv${cursor.getString(column)},"
            fileWriter.write("$row_csv\n")
        }
        fileWriter.flush()
        fileWriter.close()
    }

    // Implement locationListener callbacks
    private val locationListener: LocationListener = object : LocationListener {

        private fun setText(value: String) {
            println(value)
            //positionTextView?.setText(value)
        }

        override fun onLocationChanged(location: Location?) {
            current_location = location as Location

            setText("New location: ${location.getLatitude()} ${location.getLongitude()}")
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            setText("New status!")
        }

        override fun onProviderEnabled(provider: String?) {
            setText("Location service enabled!")
        }

        override fun onProviderDisabled(provider: String?) {
            setText("Provider off!")
        }
    }

    fun stop_positioning() {
        locationManager?.removeUpdates(locationListener)
    }

    fun start_positioning() {
        locationManager?.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            0L,
            0f,
            locationListener
        )
    }
}

class ScannerService : Service() {
    private var mScanner: Scanner? = null
    private val CHANNEL_ID = "ForegroundService Kotlin"

    companion object {
        fun startService(context: Context, scan_delay: Long) {
            val startIntent = Intent(context, ScannerService::class.java)
            startIntent.putExtra("inputExtra", scan_delay)
            ContextCompat.startForegroundService(context, startIntent)
        }
        fun stopService(context: Context) {
            val stopIntent = Intent(context, ScannerService::class.java)
            context.stopService(stopIntent)
        }
    }

    private fun createNotificationChannel() {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //do heavy work on a background thread
        val scan_delay = intent?.getLongExtra("inputExtra", 4000L)
        createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, 0
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Scanning BT + WIFi + CELL...")
            .setContentText("Scanning with ${scan_delay!! / 1000} s rate")
            .setSmallIcon(android.R.drawable.ic_dialog_alert    )
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)
        mScanner = Scanner()
        mScanner?.initialize_all(applicationContext)
        mScanner?.scan_delay = scan_delay!!
        mScanner?.start_scan()
        //stopSelf();
        return(START_NOT_STICKY)
    }

    override fun onBind(intent: Intent): IBinder? {
        // A client is binding to the service with bindService()
        return null
    }

    override fun onDestroy() {
        // The service is no longer used and is being destroyed
        println("Stopping service")
        mScanner?.stop_scan()
    }
}