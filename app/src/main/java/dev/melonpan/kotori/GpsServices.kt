package dev.melonpan.kotori

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.location.Location
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.Manifest
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.os.RemoteException
import android.widget.Toast

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import androidx.preference.PreferenceManager

import kotlin.concurrent.timer

import java.util.Timer

import dev.melonpan.kotori.utils.UnitConversion

const val REGISTER = 0
const val UNREGISTER = 1
const val DATA_UPDATE = 2
const val RUNNING_UPDATE = 3
const val RESET = 4
const val GPS_DISABLED = 5
const val SHUTDOWN = 6

class GpsServices : Service(), LocationListenerCompat, OnSharedPreferenceChangeListener {

    private val mGnssCallback = CustomGnssStatusCallback(this::handleSatelliteStatusChanged)
    private var mClient: Messenger? = null
    private val mMessenger: Messenger = Messenger(CustomHandler(this::handleMessage))

    private lateinit var mLocationManager: LocationManager

    private var mData = PositioningData()

    private lateinit var mContentIntent: PendingIntent

    private var mLastLocation = Location("kotori_gps")

    private var currentLongitude = 0.0

    private var currentLatitude = 0.0

    private var lastLongitude = 0.0

    private var lastLatitude = 0.0

    private var lastTimeStopped: Long = 0L

    private lateinit var mTimer: Timer
    
    private var useImperialUnits = false
    
    private lateinit var mPreferences: SharedPreferences

    override fun onCreate() {
        mContentIntent = Intent(this, MainActivity::class.java).let { intent ->
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            PendingIntent.getActivity(this, 0, intent, 0)
        }

        //setupLocationService()
        
        mPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        
        useImperialUnits = mPreferences.getBoolean("imperial", false)
        
        mPreferences.registerOnSharedPreferenceChangeListener(this)
        
        createNotificationChannel()

        //updateNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return mMessenger.binder
    }

    override fun onDestroy() { }

    override fun onProviderDisabled(provider: String) {
        if (mData.isRunning) {
            mTimer.cancel()
        }

        mData.isRunning = false
        mData.isFirstTime = true
        mData.accuracy = 0.0f
        mData.satellitesUsed = 0

        Toast.makeText(applicationContext, R.string.gps_disabled, Toast.LENGTH_SHORT).show()
        sendMessage(Message.obtain(null, DATA_UPDATE, mData))
        sendMessage(Message.obtain(null, GPS_DISABLED))
    }

    override fun onProviderEnabled(provider: String) { }

    override fun onLocationChanged(location: Location) {
        if (mData.isRunning) {
            currentLatitude = location.latitude
            currentLongitude = location.longitude

            if (mData.isFirstTime) {
                lastLatitude = currentLatitude
                lastLongitude = currentLongitude
                mData.isFirstTime = false
            }

            mLastLocation.latitude = lastLatitude
            mLastLocation.longitude = lastLongitude
            val distance = mLastLocation.distanceTo(location)

            if (location.accuracy < distance) {
                mData.distance += distance

                lastLatitude = currentLatitude
                lastLongitude = currentLongitude
            }
        }

        if (location.hasSpeed()) {
            mData.currentSpeed = location.speed

            if (mData.currentSpeed > mData.maxSpeed) {
                mData.maxSpeed = mData.currentSpeed
            }

            if (location.speed == 0.0f) {
                if (lastTimeStopped != 0L) {
                    mData.timeStopped += mData.time - lastTimeStopped
                }
                lastTimeStopped = mData.time
            }
            else {
                lastTimeStopped = 0L
            }
        }
        else {
            mData.currentSpeed = -1.0f
        }

        mData.accuracy = if (location.hasAccuracy()) location.accuracy else -1.0f
        mData.altitude = if (location.hasAltitude()) location.altitude else 0.0

        updateNotification()

        sendMessage(Message.obtain(null, DATA_UPDATE, mData))
    }
    
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == "imperial") {
            useImperialUnits = sharedPreferences.getBoolean(key, false)
            updateNotification()
        }
    }

    fun onNmeaMessage(message: String) {
        val ALTITUDE_INDEX = 9
        val tokens = message.split(",")

        if (message.startsWith("\$GPGGA") || message.startsWith("\$GNGNS") || message.startsWith("\$GNGGA")) {
            val altitude: String?

            try {
                altitude = tokens[ALTITUDE_INDEX]
            }
            catch (e: ArrayIndexOutOfBoundsException) {
                return
            }

            if (!altitude.isEmpty()) {
                try {
                    mData.altitudeMeanSeaLevel = altitude.toDouble()
                }
                catch (e: NumberFormatException) {
                    mData.altitudeMeanSeaLevel = 0.0
                }
            }
        }
    }

    fun handleMessage(msg: Message) {
        when (msg.what) {
            REGISTER -> {
                mClient = msg.replyTo
                sendMessage(Message.obtain(null, DATA_UPDATE, mData))
                setupLocationService()
                updateNotification()
            }
            UNREGISTER -> mClient = null
            RUNNING_UPDATE -> {
                mData.isRunning = msg.obj as Boolean
                if (mData.isRunning) {
                    mTimer = timer(period = 1000, action = { setTime() })
                } else {
                    mTimer.cancel()
                }
            }
            RESET -> reset()
            SHUTDOWN -> shutdown()
        }
    }

    fun handleSatelliteStatusChanged(status: GnssStatusCompat) {
        mData.satellites = status.getSatelliteCount()
        mData.satellitesUsed = 0

        for (i in 0..mData.satellites - 1) {
            if (status.usedInFix(i)) {
                mData.satellitesUsed += 1
            }
        }

        if (mData.satellitesUsed == 0) {
            mData.isFirstTime = true
        }

        sendMessage(Message.obtain(null, DATA_UPDATE, mData))
    }

    fun sendMessage(msg: Message) {
        try {
            mClient?.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannelCompat.Builder("kotori", NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Kotori")
                .setDescription("Kotori Notification")
                .build()

            NotificationManagerCompat.from(applicationContext).createNotificationChannel(channel)
        }
    }

    fun updateNotification() {   
        var speed = UnitConversion.metersPerSecondToKmPerHour(mData.currentSpeed)
        var distance = mData.distance.toFloat()
        
        if (useImperialUnits) {
            speed = UnitConversion.kmPerHourToMph(speed)
            distance = UnitConversion.metersToFeet(distance)
        }
        
        val notification = NotificationCompat.Builder(this, "kotori")
            .setContentTitle(getString(R.string.running))
            .setContentText(getString(
                                R.string.notification,
                                speed,
                                if (useImperialUnits) "mph" else "km/h",
                                distance,
                                if (useImperialUnits) "ft" else "m"))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(mContentIntent)
            .build()

        startForeground(R.string.noti_id, notification)
    }

    fun reset() {
        mData = PositioningData()
        mLastLocation = Location("kotori_gps")
        lastTimeStopped = 0L
        currentLatitude = 0.0
        currentLongitude = 0.0
        lastLatitude = 0.0
        lastLongitude = 0.0
    }

    // TODO: use lambda instead?
    fun setTime() {
        mData.time += 1
    }
    
    fun shutdown() {
        if (PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PermissionChecker.PERMISSION_GRANTED) {
            LocationManagerCompat.unregisterGnssStatusCallback(mLocationManager, mGnssCallback)
            LocationManagerCompat.removeUpdates(mLocationManager, this)
        }
        
        mPreferences.unregisterOnSharedPreferenceChangeListener(this)

        reset()

        stopForeground(true)
        stopSelf()
    }
    
    fun setupLocationService() {
        if (PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PermissionChecker.PERMISSION_GRANTED) {
            Toast.makeText(applicationContext, R.string.gps_disabled, Toast.LENGTH_SHORT).show()
            return
        }

        mLocationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        LocationManagerCompat.requestLocationUpdates(
            mLocationManager,
            LocationManager.GPS_PROVIDER,
            LocationRequestCompat.Builder(500).build(),
            ContextCompat.getMainExecutor(this),
            this
        )

        LocationManagerCompat.registerGnssStatusCallback(
            mLocationManager,
            ContextCompat.getMainExecutor(this),
            mGnssCallback
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            mLocationManager.addNmeaListener({
                message, _ -> onNmeaMessage(message)
            }, null)
        }
    }
}
