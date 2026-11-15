package io.github.murrayb52.snapstash

import android.content.Context

/** Camera settings. */
object CameraPrefs {
    private const val PREFS = "snapstash"
    private const val KEY_LOCATION = "save_location"
    private const val KEY_ASKED_LOCATION = "asked_location"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Tag photos with where they were taken. On by default; needs the location permission. */
    fun saveLocation(context: Context): Boolean = prefs(context).getBoolean(KEY_LOCATION, true)

    fun setSaveLocation(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCATION, on).apply()
    }

    /** Whether the camera already asked for location once (it doesn't nag after a "no"). */
    fun askedLocation(context: Context): Boolean = prefs(context).getBoolean(KEY_ASKED_LOCATION, false)

    fun setAskedLocation(context: Context) {
        prefs(context).edit().putBoolean(KEY_ASKED_LOCATION, true).apply()
    }
}
