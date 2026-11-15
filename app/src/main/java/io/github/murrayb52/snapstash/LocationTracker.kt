package io.github.murrayb52.snapstash

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * Follows the phone's location while the camera is open, so each photo can be tagged with where
 * it was taken (GPS in the photo's EXIF, like the built-in camera does). Uses Android's own
 * location service, so it needs no Google services.
 */
class LocationTracker(context: Context) : LocationListener {
    private val manager = context.getSystemService(LocationManager::class.java)

    /** Latest fix; observable from Compose. */
    var latest by mutableStateOf<Location?>(null)
        private set

    /** The latest fix if it's recent enough to describe where the photo is being taken. */
    fun current(now: Long = System.currentTimeMillis()): Location? =
        latest?.takeIf { now - it.time < MAX_AGE_MS }

    @SuppressLint("MissingPermission") // checked by the caller via hasPermission()
    fun start() {
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.hasProvider(LocationManager.FUSED_PROVIDER)) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        providers.forEach { provider ->
            runCatching {
                manager.getLastKnownLocation(provider)?.let(::offer)
                manager.requestLocationUpdates(provider, 2_000L, 0f, this, Looper.getMainLooper())
            }
        }
    }

    fun stop() {
        runCatching { manager.removeUpdates(this) }
    }

    /** Keeps the better of the current fix and [fix]: clearly newer, or at least as accurate. */
    private fun offer(fix: Location) {
        val cur = latest
        if (cur == null || fix.time - cur.time > 30_000 || fix.accuracy <= cur.accuracy) latest = fix
    }

    override fun onLocationChanged(location: Location) = offer(location)

    // Must be implemented explicitly: on Android 10 these have no default implementation.
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit
    @Deprecated("Required on Android 10")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    companion object {
        /** A fix older than this is from somewhere else; don't stamp it on a photo. */
        const val MAX_AGE_MS = 2 * 60_000L

        val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

        fun hasPermission(context: Context): Boolean = PERMISSIONS.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
