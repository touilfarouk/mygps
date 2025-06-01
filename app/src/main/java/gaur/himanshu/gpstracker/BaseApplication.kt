package gaur.himanshu.gpstracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

const val CHANNEL_ID = "channel_id"
const val CHANNEL_NAME = "GPS Tracker Service"

class BaseApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // No sound or vibration
            ).apply {
                setSound(null, null) // Disable sound
                enableVibration(false) // Disable vibration
                setShowBadge(false) // Optional: don't show badge on app icon
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
