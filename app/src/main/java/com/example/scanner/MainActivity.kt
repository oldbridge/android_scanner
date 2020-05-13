package com.example.scanner

import android.Manifest.permission.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.telephony.*
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileWriter

class MainActivity : AppCompatActivity() {
    // inside a basic activity
    private var locationManager: LocationManager? = null
    private val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var wifiManager: WifiManager? = null
    private var telephonyManager: TelephonyManager? = null
    private var scan_time = 0L
    private var current_location = Location("dummyprovider")
    private var stop: Boolean = false
    private var scan_delay: Long = 4000
    private var db: DeviceDatabase? = null
    private var storage_path: String? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // Initialize the broadcast receiver
    val bReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        var show_text = ""
        private fun setText(value: String) {
            show_text += value + "\n"
            findViewById<TextView>(R.id.textViewGNSS).setText(show_text)
        }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getPermissions()

        // Setup db
        db = DeviceDatabase(this, null, getExternalFilesDir(null).toString())

        // Create telephony manager reference
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // Create persistent LocationManager reference
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager?
        //locationManager?.registerGnssStatusCallback(gnss_callbacks)
        start_positioning()

        // Get the default adapter
        init_bt()
        init_wifi()

        // Setup click listeners
        findViewById<Button>(R.id.buttonStart).setOnClickListener { start_scan() }
        findViewById<Button>(R.id.buttonStop).setOnClickListener { stop_scan() }
        findViewById<Button>(R.id.buttonStore).setOnClickListener { save_results() }
        findViewById<SeekBar>(R.id.scanfrequencyBar).setOnSeekBarChangeListener(rate_changed)

        // Setup wakelock
        val mgr: PowerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "app:MyWakeLock")
        // Setup scan rate indicators
        findViewById<SeekBar>(R.id.scanfrequencyBar).setProgress(scan_delay.toInt() / 1000)
    }

    var rate_changed: SeekBar.OnSeekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
            scan_delay = progress.toLong() * 1000
            findViewById<TextView>(R.id.scanfreq).setText(progress.toString() + " s")
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {

        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {

        }
    }

    private fun init_bt() {
        val turnOnIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        if (bluetoothAdapter.isEnabled() == false) {
            startActivityForResult(turnOnIntent, 1)
        }
    }

    private fun init_wifi() {
        wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private fun start_wifi_scan() {
        val results = wifiManager?.getScanResults() as List<ScanResult>
        var console = findViewById<TextView>(R.id.textView)
        var prevres = console.text as String
        for (r in results) {
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
            prevres += "${r.SSID} ${r.BSSID} ${r.frequency} ${r.level} dBm\n"
        }
    }

    private fun start_scan() {
        stop = false
        wakeLock?.acquire()

        handler.post(periodicScans)
    }

    private fun stop_scan() {
        wakeLock?.release()
        stop = true
    }

    private fun perform_scan() {
        scan_time = System.currentTimeMillis() / 1000
        start_bt_scan()
        get_cell_updates()
        start_wifi_scan()

        // Also update the GUI with current or previous results and time indicators
        findViewById<TextView>(R.id.textViewGNSS).setText(db?.getCount_str())
    }

    private fun get_log_file(): File {
        println()
        return File(getExternalFilesDir(null), get_log_filename())
    }

    private fun get_log_filename(): String {
        return "dump.csv"
        //return findViewById<EditText>(R.id.filenameText).getText().toString()
    }

    private fun save_results() {
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

    private fun start_bt_scan() {
        bluetoothAdapter.startDiscovery()
        registerReceiver(bReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
    }

    private fun getPermissions() {
        // Get permission from user
        requestPermissions(
            arrayOf(
                ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION,
                WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE, READ_PHONE_STATE,
                WAKE_LOCK
            ), 0
        )
    }

    private val gnss_callbacks: GnssStatus.Callback = object : GnssStatus.Callback() {
        private var show_text = ""
        private fun setText(value: String) {
            show_text += value + "\n"
            findViewById<TextView>(R.id.textViewGNSS).setText(show_text)
        }

        override fun onFirstFix(ttffMillis: Int) {
            super.onFirstFix(ttffMillis)
            setText("Took $ttffMillis milliseconds to fix!")
        }

        override fun onStarted() {
            super.onStarted()
            setText("Started")
        }

        override fun onSatelliteStatusChanged(status: GnssStatus?) {
            super.onSatelliteStatusChanged(status)
            val sat_count: Int = status?.satelliteCount as Int
            if (sat_count > 0) {
                var value = "Sats: "
                for (i in 0 until sat_count) {
                    value += "${status.getSvid(i)} [${status.getCn0DbHz(i)}], "
                }
                setText(value)
            } else {
                setText("No sat on track!")
            }

        }
    }

    // Implement locationListener callbacks
    private val locationListener: LocationListener = object : LocationListener {

        private fun setText(value: String) {
            findViewById<TextView>(R.id.textView).setText(value)
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

    fun start_positioning() {
        locationManager?.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            0L,
            0f,
            locationListener
        )
    }

    fun clickButtonStop() {
        locationManager?.removeUpdates(locationListener)
    }
}
