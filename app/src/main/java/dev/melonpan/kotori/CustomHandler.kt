package dev.melonpan.kotori

import android.os.Handler
import android.os.Looper
import android.os.Message

class CustomHandler(callback: (msg: Message) -> Unit) : Handler(Looper.getMainLooper()) {
    private val mCallback = callback

    override fun handleMessage(msg: Message) {
        when (msg.what) {
            REGISTER, UNREGISTER, DATA_UPDATE, RUNNING_UPDATE, RESET,
            GPS_DISABLED, SHUTDOWN -> mCallback.invoke(msg)
            
            else -> super.handleMessage(msg)
        }
    }
}
