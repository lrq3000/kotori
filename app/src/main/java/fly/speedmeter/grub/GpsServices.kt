package fly.speedmeter.grub

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.Manifest
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.os.RemoteException
import android.widget.Toast

import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat

import kotlin.concurrent.timer

import java.util.Timer

const val REGISTER = 0
const val UNREGISTER = 1
const val DATA_UPDATE = 2
const val RUNNING_UPDATE = 3
const val RESET = 4
const val GPS_DISABLED = 5

class GpsServices : Service(), LocationListenerCompat {

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

    override fun onCreate() {
        mContentIntent = Intent(this, MainActivity::class.java).let { intent ->
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            PendingIntent.getActivity(this, 0, intent, 0)
        }

        createNotificationChannel()

        updateNotification(false)

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return mMessenger.binder
    }

    override fun onDestroy() {
        if (PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PermissionChecker.PERMISSION_GRANTED) {
            LocationManagerCompat.unregisterGnssStatusCallback(mLocationManager, mGnssCallback)
            LocationManagerCompat.removeUpdates(mLocationManager, this)
        }

        stopForeground(true)
    }

    override fun onProviderDisabled(@NonNull provider: String) {
        if (mData.isRunning) {
            mData = mData.copy(
                isRunning = false,
                isFirstTime = true,
                accuracy = 0.0f,
                satellitesUsed = 0,
                satellites = 0
            )
            mTimer.cancel()
        }
        Toast.makeText(applicationContext, R.string.gps_disabled, Toast.LENGTH_SHORT).show()
        sendMessage(Message.obtain(null, DATA_UPDATE, mData))
        sendMessage(Message.obtain(null, GPS_DISABLED))
    }

    override fun onProviderEnabled(@NonNull provider: String) { }

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
                    mData.timeStopped = SystemClock.elapsedRealtime() - lastTimeStopped
                }
                lastTimeStopped = SystemClock.elapsedRealtime()
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

        updateNotification(true)

        sendMessage(Message.obtain(null, DATA_UPDATE, mData))
    }

    fun handleMessage(msg: Message) {
        when (msg.what) {
            REGISTER -> {
                mClient = msg.replyTo
                sendMessage(Message.obtain(null, DATA_UPDATE, mData))
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
            var channelBuilder = NotificationChannelCompat.Builder("kotori", NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Kotori")
                .setDescription("Kotori Notification")

            val notificationChannel = channelBuilder.build()
            NotificationManagerCompat.from(applicationContext).createNotificationChannel(notificationChannel)
        }
    }

    fun updateNotification(hasData: Boolean) {
        var notificationBuilder = NotificationCompat.Builder(this, "Kotori")
            .setContentTitle(getString(R.string.running))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(mContentIntent)

        if (hasData) {
            notificationBuilder.setContentText(getString(R.string.notification, mData.currentSpeed, mData.distance))
        }
        else {
            notificationBuilder.setContentText(getString(R.string.notification, 0.0f, 0.0f))
        }

        val notification = notificationBuilder.build()
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

    fun setTime() {
        mData.time += 1
    }
}