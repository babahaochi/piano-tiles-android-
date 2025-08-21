package com.example.pianotiles.stats

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local leaderboard and aggregates stored in SharedPreferences as JSON.
 */
class StatsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("piano_tiles_stats", Context.MODE_PRIVATE)

    data class Entry(
        val score: Int,
        val combo: Int,
        val lanes: Int,
        val difficulty: String,
        val useBeat: Boolean,
        val bpm: Float,
        val timestamp: Long
    )

    data class Aggregates(
        val totalRuns: Int,
        val totalPlayTimeSec: Long,
        val avgScore: Double,
        val bestScore: Int
    )

    fun addEntry(e: Entry) {
        val arr = JSONArray(prefs.getString(KEY_LEADERBOARD, "[]"))
        arr.put(JSONObject().apply {
            put("score", e.score)
            put("combo", e.combo)
            put("lanes", e.lanes)
            put("difficulty", e.difficulty)
            put("useBeat", e.useBeat)
            put("bpm", e.bpm)
            put("ts", e.timestamp)
        })
        // Sort desc by score and trim to TOP_N
        val list = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
        list.sortByDescending { it.optInt("score", 0) }
        val trimmed = JSONArray()
        list.take(TOP_N).forEach { trimmed.put(it) }
        prefs.edit().putString(KEY_LEADERBOARD, trimmed.toString()).apply()

        // Aggregates
        val runs = prefs.getInt(KEY_TOTAL_RUNS, 0) + 1
        val totalScore = prefs.getLong(KEY_TOTAL_SCORE, 0L) + e.score
        val best = maxOf(prefs.getInt(KEY_BEST_SCORE, 0), e.score)
        val totalTime = prefs.getLong(KEY_TOTAL_TIME, 0L) // updated externally
        prefs.edit()
            .putInt(KEY_TOTAL_RUNS, runs)
            .putLong(KEY_TOTAL_SCORE, totalScore)
            .putInt(KEY_BEST_SCORE, best)
            .putLong(KEY_TOTAL_TIME, totalTime)
            .apply()
    }

    fun setLastSessionDuration(seconds: Long) {
        val totalTime = prefs.getLong(KEY_TOTAL_TIME, 0L) + seconds
        prefs.edit().putLong(KEY_TOTAL_TIME, totalTime).apply()
    }

    fun getLeaderboard(): List<Entry> {
        val arr = JSONArray(prefs.getString(KEY_LEADERBOARD, "[]"))
        val out = mutableListOf<Entry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Entry(
                    score = o.optInt("score", 0),
                    combo = o.optInt("combo", 0),
                    lanes = o.optInt("lanes", 4),
                    difficulty = o.optString("difficulty", "Normal"),
                    useBeat = o.optBoolean("useBeat", false),
                    bpm = o.optDouble("bpm", 120.0).toFloat(),
                    timestamp = o.optLong("ts", 0L)
                )
            )
        }
        return out
    }

    fun aggregates(): Aggregates {
        val runs = prefs.getInt(KEY_TOTAL_RUNS, 0)
        val totalTime = prefs.getLong(KEY_TOTAL_TIME, 0L)
        val best = prefs.getInt(KEY_BEST_SCORE, 0)
        val totalScore = prefs.getLong(KEY_TOTAL_SCORE, 0L)
        val avg = if (runs > 0) totalScore.toDouble() / runs else 0.0
        return Aggregates(runs, totalTime, avg, best)
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_LEADERBOARD)
            .remove(KEY_TOTAL_RUNS)
            .remove(KEY_TOTAL_TIME)
            .remove(KEY_TOTAL_SCORE)
            .remove(KEY_BEST_SCORE)
            .apply()
    }

    companion object {
        private const val TOP_N = 20
        private const val KEY_LEADERBOARD = "leaderboard"
        private const val KEY_TOTAL_RUNS = "total_runs"
        private const val KEY_TOTAL_TIME = "total_time"
        private const val KEY_TOTAL_SCORE = "total_score"
        private const val KEY_BEST_SCORE = "best_score"
    }
}
