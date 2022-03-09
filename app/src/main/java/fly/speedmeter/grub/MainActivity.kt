package fly.speedmeter.grub

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.Manifest
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.os.SystemClock
import android.text.format.DateUtils
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker

import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton


class MainActivity : AppCompatActivity() {

    private var mService: Messenger? = null

    private var bound: Boolean = false

    private var isFirstFix = false

    private var isRunning = false

    private var time: Long = 0L

    private val mMessenger: Messenger = Messenger(CustomHandler(this::handleMessage))

    private lateinit var tvSatelliteData: TextView

    private lateinit var tvAccuracyData: TextView

    private lateinit var tvCurrentSpeed: TextView

    private lateinit var tvMaxSpeedData: TextView

    private lateinit var tvAverageSpeedData: TextView

    private lateinit var tvAltitudeData: TextView

    private lateinit var tvDistance: TextView

    private lateinit var fabStart: FloatingActionButton

    private var menuOptions: Menu? = null

    private lateinit var mToolbar: MaterialToolbar

    private lateinit var tvTime: TextView

    private lateinit var mSharedPreferences: SharedPreferences

    private val mConnection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            mService = Messenger(service)
            bound = true

            var msg = Message.obtain(null, REGISTER)
            msg.replyTo = mMessenger
            sendMessage(msg)

        }

        override fun onServiceDisconnected(className: ComponentName) {
            mService = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mSharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)

        tvSatelliteData = findViewById(R.id.satelliteData)

        tvAccuracyData = findViewById(R.id.accuracyData)

        tvCurrentSpeed = findViewById(R.id.currentSpeed)

        tvMaxSpeedData = findViewById(R.id.maxSpeedData)

        tvAverageSpeedData = findViewById(R.id.averageSpeedData)

        tvAltitudeData = findViewById(R.id.altitudeData)

        tvDistance = findViewById(R.id.distance)
        tvDistance.text = "---"

        fabStart = findViewById(R.id.fab)
        fabStart.setVisibility(View.INVISIBLE)

        fabStart.setOnClickListener {
            if (!isRunning) {
                fabStart.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_action_pause))
                menuOptions?.findItem(R.id.action_refresh)?.setVisible(false)
                isRunning = true
            }
            else {
                fabStart.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_action_play))
                mToolbar.setTitle(R.string.app_name)
                menuOptions?.findItem(R.id.action_refresh)?.setVisible(true)
                isRunning = false
            }
            sendMessage(Message.obtain(null, RUNNING_UPDATE, isRunning))
        }

        mToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(mToolbar)

        tvTime = findViewById(R.id.time)
        
        val theme = mSharedPreferences.getString("theme", "system")
        
        when (theme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        checkLocationPermission()
    }

    override fun onStart() {
        super.onStart()
        Intent(baseContext, GpsServices::class.java).also { intent ->
            startService(intent)
        }

        Intent(this, GpsServices::class.java).also { intent ->
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()

        unboundService()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menuOptions = menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_refresh -> {
                sendMessage(Message.obtain(null, RESET))
                return true
            }
            R.id.action_exit -> {
                exitApplication()
                return true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }
            else -> {
                return super.onOptionsItemSelected(item)
            }
        }
    }

    override fun onBackPressed() {
        Intent(Intent.ACTION_MAIN).also { intent ->
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }

    fun showLocationDisabledDialog() {
        MaterialAlertDialogBuilder(this)
        .setMessage(resources.getString(R.string.please_enable_gps))
        .setPositiveButton(resources.getString(R.string.accept)) { _, _, ->
            startActivity(Intent("android.settings.LOCATION_SOURCE_SETTINGS"))
        }
        .show()
    }

    fun checkLocationPermission() {
        if (PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PermissionChecker.PERMISSION_GRANTED
            && PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PermissionChecker.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1337)
        }
    }

    fun sendMessage(msg: Message) {
        try {
            mService?.send(msg)
        }
        catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    fun handleMessage(msg: Message) {
        when (msg.what) {
            DATA_UPDATE -> handleDataUpdate(msg.obj as PositioningData)
            GPS_DISABLED -> showLocationDisabledDialog()
        }
    }

    fun handleDataUpdate(data_: PositioningData) {
        isFirstFix = data_.isFirstTime
        handleRunningUpdate(data_.isRunning)
        handleTimeUpdate(data_.time)
        handleAccuracyUpdate(data_.accuracy)
        handleSatellitesUpdate(data_.satellitesUsed, data_.satellites)
        handleSpeedUpdate(data_.currentSpeed, data_.maxSpeed, calculateAverageSpeed(data_))
        handleAltitudeUpdate(data_.altitude)
        handleDistanceUpdate(data_.distance)
    }

    fun handleSatellitesUpdate(satellitesUsed: Int, satelliteCount: Int) {
        tvSatelliteData.text = "$satellitesUsed/$satelliteCount"

        if (satellitesUsed == 0) {
            fabStart.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_action_play))
            fabStart.hide()

            menuOptions?.findItem(R.id.action_refresh)?.setVisible(false)

            mToolbar.setTitle(R.string.waiting_for_fix)
        }
    }

    fun handleAccuracyUpdate(accuracy: Float) {
        var acc = accuracy
        if (acc != -1.0f) {
            val units = if (mSharedPreferences.getBoolean("imperial", false)) {
                acc *= 3.28084f
                "ft"
            } else {
                "m"
            }

            var span = SpannableString("%.0f %s".format(acc, units))
            span.setSpan(RelativeSizeSpan(0.75f), span.length - units.length - 1, span.length, 0)
            tvAccuracyData.text = span

            if (isFirstFix) {
                mToolbar.setTitle(R.string.app_name)
                fabStart.show()
                isFirstFix = false
            }
        }
        else {
            isFirstFix = true
        }
    }

    fun handleSpeedUpdate(speed: Float, maxSpeed: Float, averageSpeed: Float) {
        //TODO: hide speed views
        if (speed == -1.0f) {
            return
        }

        // convert from m/s to km/h
        var spd = speed * 3.6f
        var maxSpd = maxSpeed * 3.6f
        var avgSpd = averageSpeed

        val units = if (mSharedPreferences.getBoolean("imperial", false)) {
            spd *= 0.62137119f
            maxSpd *= 0.62137119f
            avgSpd *= 0.62137119f
            "mph"
        } else {
            "km/h"
        }

        var span = SpannableString("%.0f %s".format(spd, units))
        span.setSpan(RelativeSizeSpan(0.25f), span.length - units.length - 1, span.length, 0)
        tvCurrentSpeed.text = span

        span = SpannableString("%.0f %s".format(maxSpd, units))
        span.setSpan(RelativeSizeSpan(0.5f), span.length - units.length - 1, span.length, 0)
        tvMaxSpeedData.text = span

        span = SpannableString("%.0f %s".format(avgSpd, units))
        span.setSpan(RelativeSizeSpan(0.5f), span.length - units.length - 1, span.length, 0)
        tvAverageSpeedData.text = span
    }

    fun handleAltitudeUpdate(altitude: Double) {
        if (altitude == -1.0) {
            return
        }

        var altd = altitude

        val units = if (mSharedPreferences.getBoolean("imperial", false)) {
            altd *= 3.28084
            "ft"
        } else {
            "m"
        }

        var span = SpannableString("%.0f %s".format(altd, units))
        span.setSpan(RelativeSizeSpan(0.5f), span.length - units.length - 1, span.length, 0)
        tvAltitudeData.text = span
    }

    fun handleDistanceUpdate(distance: Double) {
        var dist = distance

        val units = if (mSharedPreferences.getBoolean("imperial", false)) {
            if (dist >= 1700) {
                dist *= 0.0006213712
                "mi"
            } else {
                dist *= 3.28084
                "ft"
            }
        } else {
            if (dist >= 1000.0) {
                dist /= 1000.0
                "km"
            }
            else {
                "m"
            }
        }

        var span = SpannableString("%.0f %s".format(dist, units))
        span.setSpan(RelativeSizeSpan(0.5f), span.length - units.length - 1, span.length, 0)
        tvDistance.text = span
    }

    fun handleRunningUpdate(running: Boolean) {
        isRunning = running

        if (isFirstFix) {
            return
        }

        if (isRunning) {
            fabStart.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_action_pause))
        }
        else {
            fabStart.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_action_play))
        }

        if (fabStart.visibility == View.INVISIBLE) {
            fabStart.show()
        }

    }

    fun handleTimeUpdate(time_: Long) {
        time = time_

        tvTime.text = DateUtils.formatElapsedTime(time)
    }

    fun calculateAverageSpeed(data_: PositioningData): Float {
        val autoAvg = mSharedPreferences.getBoolean("auto_average", false)

        val motionTime = if (autoAvg) {
            time - data_.timeStopped
        } else {
            time
        }

        val avgSpeed: Float = if (motionTime <= 0.0f) {
            0.0f
        } else {
            (data_.distance.toFloat() / motionTime.toFloat() * 3.6f)
        }

        return avgSpeed
    }

    fun unboundService() {
        if (bound) {
            if (mService != null) {
                sendMessage(Message.obtain(null, UNREGISTER))
            }

            unbindService(mConnection)
            bound = false
        }
    }

    fun exitApplication() {
        unboundService()

        Intent(applicationContext, GpsServices::class.java).also { intent ->
            stopService(intent)
        }

        finish()
    }

}
