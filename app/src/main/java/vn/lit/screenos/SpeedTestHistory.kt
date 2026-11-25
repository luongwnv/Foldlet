package vn.lit.screenos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** The most recent speed test results, kept only on this phone. */
object SpeedTestHistory {
    data class Entry(val time: Long, val network: String, val server: String?, val download: Double, val upload: Double, val ping: Double?)

    private const val KEY = "speedtest_history"
    private const val LIMIT = 20

    fun read(context: Context): List<Entry> = runCatching {
        val array = JSONArray(Platform.preferences(context).getString(KEY, "[]"))
        List(array.length()) { i ->
            val item = array.getJSONObject(i)
            Entry(item.getLong("time"), item.optString("network"), item.optString("server").takeIf { it.isNotBlank() },
                item.getDouble("download"), item.getDouble("upload"), item.optDouble("ping").takeIf { !it.isNaN() })
        }
    }.getOrDefault(emptyList())

    fun add(context: Context, entry: Entry) {
        val entries = (listOf(entry) + read(context)).take(LIMIT)
        val array = JSONArray()
        for (item in entries) array.put(JSONObject().apply {
            put("time", item.time); put("network", item.network); put("server", item.server ?: "")
            put("download", item.download); put("upload", item.upload); item.ping?.let { put("ping", it) }
        })
        Platform.preferences(context).edit().putString(KEY, array.toString()).apply()
    }
}
