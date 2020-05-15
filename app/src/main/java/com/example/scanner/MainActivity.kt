package com.example.scanner

import android.Manifest.permission.*
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileWriter

class MainActivity : AppCompatActivity() {
    var scan_delay = 4000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getPermissions()

        // Initialize the scanner class instance

        // Setup click listeners
        findViewById<Button>(R.id.buttonStart).setOnClickListener { start_scan() }
        findViewById<Button>(R.id.buttonStop).setOnClickListener { stop_scan() }
        findViewById<Button>(R.id.buttonStore).setOnClickListener { save_results() }
        findViewById<SeekBar>(R.id.scanfrequencyBar).setOnSeekBarChangeListener(rate_changed)

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

    private fun start_scan() {
        ScannerService.startService(this, scan_delay)
    }

    private fun stop_scan() {
        ScannerService.stopService(this)
    }
    private fun getPermissions() {
        // Get permission from user
        requestPermissions(
            arrayOf(
                ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION,
                WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE, READ_PHONE_STATE,
                WAKE_LOCK, ACCESS_BACKGROUND_LOCATION
            ), 0
        )
    }

    fun save_results() {
        val db = DeviceDatabase(applicationContext, null, applicationContext.getExternalFilesDir(null).toString())
        var fileWriter = FileWriter(File(applicationContext.getExternalFilesDir(null), "dump.csv"), false)
        val cursor = db.getAllData()

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

}
