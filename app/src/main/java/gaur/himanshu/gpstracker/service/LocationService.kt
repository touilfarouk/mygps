package gaur.himanshu.gpstracker.service

import android.Manifest
import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import gaur.himanshu.gpstracker.CHANNEL_ID
import gaur.himanshu.gpstracker.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface LocationApi {
    @POST("index.php")
    suspend fun sendCoordinates(@Body coords: Coordinates): Response<Unit>
}
data class Coordinates(
    val latitude: String,
    val longitude: String
)
object ApiClient {
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://onta.dz/api/location/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val locationApi: LocationApi by lazy {
        retrofit.create(LocationApi::class.java)
    }
}
class LocationService : Service() {
    private var isCollecting = true
    private var stationaryStartTime: Long? = null
    private val STATIONARY_THRESHOLD_MS = 15_000L // 15 seconds
    private val SPEED_THRESHOLD = 1.0f // m/s, adjust to ~0.5f for walking
    private val locationRequest by lazy {
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setIntervalMillis(
                10000
            ).build()
    }

    private val locationCallback by lazy {
        object : LocationCallback() {
            override fun onLocationAvailability(p0: LocationAvailability) {
                super.onLocationAvailability(p0)
            }

            override fun onLocationResult(location: LocationResult) {
                val loc = location.lastLocation ?: return
                val lat = loc.latitude.toString()
                val lng = loc.longitude.toString()
                val speed = loc.speed // in meters/second

                Log.d("SPEED_CHECK", "Speed: $speed m/s")

                if (speed < SPEED_THRESHOLD) {
                    if (stationaryStartTime == null) {
                        stationaryStartTime = System.currentTimeMillis()
                    } else {
                        val elapsed = System.currentTimeMillis() - stationaryStartTime!!
                        if (elapsed >= STATIONARY_THRESHOLD_MS) {
                            if (isCollecting) {
                                Log.d("GPS_TRACKER", "User is stationary. Pausing collection.")
                                isCollecting = false
                            }
                        }
                    }
                } else {
                    if (!isCollecting) {
                        Log.d("GPS_TRACKER", "User started moving. Resuming collection.")
                        isCollecting = true
                    }
                    stationaryStartTime = null
                }

                if (!isCollecting) return

                // Send coordinates
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = ApiClient.locationApi.sendCoordinates(Coordinates(lat, lng))
                        if (response.isSuccessful) {
                            Log.d("UPLOAD", "Coordinates sent successfully")
                        } else {
                            Log.e("UPLOAD", "Error: ${response.code()}")
                        }
                    } catch (e: Exception) {
                        Log.e("UPLOAD", "Exception: ${e.message}")
                    }
                }

                startServiceOfForeground(lat, lng)
            }

        }
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        locationUpdates()
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        super.onDestroy()

    }

    private fun locationUpdates() {
        val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest, locationCallback, null
        )
    }

    private fun startServiceOfForeground(lat: String, lng: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentTitle("Location Updates")
            .setContentText("$lat - $lng")
            .setPriority(NotificationCompat.PRIORITY_LOW) // Lower priority = no sound
            .setSilent(true) // Makes it completely silent
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startForeground(1, notification)
            }
        } else {
            startForeground(1, notification)
        }
    }
}