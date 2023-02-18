package dev.melonpan.kotori

import androidx.core.location.GnssStatusCompat

class CustomGnssStatusCallback(callback: (status: GnssStatusCompat) -> Unit) : GnssStatusCompat.Callback() {
    private val mCallback = callback

    override fun onSatelliteStatusChanged(status: GnssStatusCompat) = mCallback.invoke(status)
}
