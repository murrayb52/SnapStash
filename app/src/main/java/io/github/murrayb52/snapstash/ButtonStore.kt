package io.github.murrayb52.snapstash

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/** The user's buttons, persisted as JSON in SharedPreferences. */
object ButtonStore {
    private const val PREFS = "snapstash"
    private const val KEY_BUTTONS = "buttons"

    /** Observable by Compose; mutate only through [upsert] / [delete] / [move]. */
    val buttons = mutableStateListOf<CaptureButton>()
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        buttons.addAll(decode(p.getString(KEY_BUTTONS, null)))
    }

    fun get(id: String?): CaptureButton? = buttons.firstOrNull { it.id == id }

    fun nextColorIndex(): Int = (buttons.maxOfOrNull { it.colorIndex } ?: -1) + 1

    fun upsert(context: Context, button: CaptureButton) {
        val i = buttons.indexOfFirst { it.id == button.id }
        if (i >= 0) buttons[i] = button else buttons.add(button)
        save(context)
    }

    fun delete(context: Context, id: String) {
        buttons.removeAll { it.id == id }
        save(context)
    }

    fun move(context: Context, id: String, delta: Int) {
        val from = buttons.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in buttons.indices) return
        buttons.add(to, buttons.removeAt(from))
        save(context)
    }

    private fun save(context: Context) {
        prefs?.edit()?.putString(KEY_BUTTONS, encode(buttons))?.commit()
        Shortcuts.sync(context, buttons)
        FolderAccess.releaseUnused(context, buttons)
    }

    private fun encode(list: List<CaptureButton>): String = JSONArray().apply {
        list.forEach {
            put(
                JSONObject()
                    .put("id", it.id)
                    .put("label", it.label)
                    .put("folderUri", it.folderUri)
                    .put("colorIndex", it.colorIndex)
            )
        }
    }.toString()

    private fun decode(json: String?): List<CaptureButton> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                CaptureButton(
                    id = o.getString("id"),
                    label = o.getString("label"),
                    folderUri = o.getString("folderUri"),
                    colorIndex = o.optInt("colorIndex", i),
                )
            }
        }.getOrDefault(emptyList())
    }
}
