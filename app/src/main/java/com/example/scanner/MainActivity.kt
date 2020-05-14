package com.example.scanner

import android.Manifest.permission.*
import android.location.GnssStatus
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
class MainActivity : AppCompatActivity() {
    val mScanner = Scanner()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getPermissions()

        // Initialize the scanner class instance
        mScanner.initialize_all(getApplicationContext(), findViewById(R.id.consoleTextView), findViewById(R.id.positionTextView))

        // Setup click listeners
        findViewById<Button>(R.id.buttonStart).setOnClickListener { mScanner.start_scan() }
        findViewById<Button>(R.id.buttonStop).setOnClickListener { mScanner.stop_scan() }
        findViewById<Button>(R.id.buttonStore).setOnClickListener { mScanner.save_results() }
        findViewById<SeekBar>(R.id.scanfrequencyBar).setOnSeekBarChangeListener(rate_changed)

        // Setup scan rate indicators
        findViewById<SeekBar>(R.id.scanfrequencyBar).setProgress(mScanner.scan_delay.toInt() / 1000)

    }

    var rate_changed: SeekBar.OnSeekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
            mScanner.scan_delay = progress.toLong() * 1000
            findViewById<TextView>(R.id.scanfreq).setText(progress.toString() + " s")
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {

        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {

        }
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
}
