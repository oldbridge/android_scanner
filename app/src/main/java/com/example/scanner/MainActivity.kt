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
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileWriter
import java.util.*
import kotlin.collections.ArrayList

class CSVResults() {
    private var BTArray: MutableList<String>
    private var WifiArray: MutableList<String>

    init {
        BTArray = ArrayList()
        WifiArray = ArrayList()
    }

    fun add_bt(element: String) {
        BTArray.add(element)
    }

    fun add_wifi(element: String) {
        WifiArray.add(element)
    }

    fun write_to_csv(file: File) {
        println(file)
        var fileWriter = FileWriter(file, true)

        // Write all BT ...
        for (bt in BTArray) {
            println(bt)
            fileWriter.write("BT,$bt\n")
        }

        // and all Wifi ...
        for (w in WifiArray) {
            println(w)
            fileWriter.write("WIFI,$w\n")
        }

        fileWriter.flush()
        fileWriter.close()
    }
}

var result_db = CSVResults()

class mLocation() {
    private var lat = 0.0
    private var long = 0.0
    private var height = 0.0

    fun print() : String {
        return "$lat,$long,$height"
    }

    fun set_location(mlat: Double, mlong: Double, mHeight: Double) {
        lat = mlat
        long = mlong
        height = mHeight
    }
}

class MainActivity : AppCompatActivity() {
    // inside a basic activity
    private var locationManager : LocationManager? = null
    private val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var wifiManager: WifiManager? = null
    private var scan_time = 0L
    private var current_location = mLocation()

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
                //setText("${device.name} ${device.address} $device_rssi dBm")
                result_db.add_bt("${current_location.print()},$scan_time,${device.name},${device.address},$device_rssi")
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
            handler.postDelayed(this, 15000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getPermissions()

        // Create persistent LocationManager reference
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager?
        //locationManager?.registerGnssStatusCallback(gnss_callbacks)
        start_positioning()

        // Set filename to current time
        val start_time = System.currentTimeMillis() / 1000
        findViewById<EditText>(R.id.filenameText).setText("log_" + start_time.toString() + ".csv")
        // Get the default adapter
        init_bt()
        init_wifi()

        // Setup click listeners
        //findViewById<Button>(R.id.buttonStart).setOnClickListener{clickButtonStart()}
        findViewById<Button>(R.id.buttonStart).setOnClickListener{start_scan()}
        //findViewById<Button>(R.id.buttonStop).setOnClickListener{clickButtonStop()}
        findViewById<Button>(R.id.buttonStop).setOnClickListener{save_results()}

        // Schedule periodic scans

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
        val results  = wifiManager?.getScanResults() as List<ScanResult>
        var console = findViewById(R.id.textView) as TextView
        var prevres = console.text as String
        for (r in results) {
            result_db.add_wifi("${current_location.print()},$scan_time,${r.SSID},${r.BSSID},${r.level},${r.frequency},${r.channelWidth},${r.capabilities}")
            prevres += "${r.SSID} ${r.BSSID} ${r.frequency} ${r.level} dBm\n"
        }
        //console.setText(prevres)
    }

    private fun start_scan() {
        handler.post(periodicScans)
    }

    private fun perform_scan() {
        scan_time = System.currentTimeMillis() / 1000

        start_bt_scan()
        start_wifi_scan()
    }

    private fun get_log_file(): File {
        return File(getExternalFilesDir(null), get_log_filename())
    }
    private fun get_log_filename(): String {
        return findViewById<EditText>(R.id.filenameText).getText().toString()
    }

    private fun save_results() {
        val f = get_log_file()
        result_db.write_to_csv(f)
    }

    private fun start_bt_scan() {
        bluetoothAdapter.startDiscovery()
        registerReceiver(bReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
    }

    private fun getPermissions() {
        // Get permission from user
        requestPermissions(arrayOf(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION,
            WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE, READ_PHONE_STATE), 0)
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
            }
            else {
                setText("No sat on track!")
            }

        }
    }

    //
    // Implement locationListener callbacks
    private val locationListener: LocationListener = object : LocationListener {

        private fun setText(value: String) {
            findViewById<TextView>(R.id.textView).setText(value)
        }
        override fun onLocationChanged(location: Location?) {
            current_location.set_location(location!!.getLatitude(), location!!.getLongitude(), location!!.getAltitude())
            setText("New location: ${location?.latitude} ${location?.longitude}")
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

    fun start_positioning() {
        locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, locationListener)
    }
    fun clickButtonStop() {
        locationManager?.removeUpdates(locationListener)
    }
}
