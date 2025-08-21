package com.example.pianotiles.game

import android.content.Context
import android.content.SharedPreferences

class ScoreManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("piano_tiles_score", Context.MODE_PRIVATE)

    var score = 0
        private set
    var combo = 0
        private set
    var maxCombo = 0
        private set
    var best = prefs.getInt("best", 0)
        private set

    fun hit(j: GameEngine.Judgement = GameEngine.Judgement.GOOD) {
    combo += 1
    if (combo > maxCombo) maxCombo = combo
        val base = when (j) {
            GameEngine.Judgement.PERFECT -> 20
            GameEngine.Judgement.GREAT -> 15
            GameEngine.Judgement.GOOD -> 10
            else -> 0
        }
        val bonus = (combo / 10) * 2
        score += base + bonus
        if (score > best) {
            best = score
            prefs.edit().putInt("best", best).apply()
        }
    }

    fun resetComboOnMiss() { combo = 0 }

    fun resetAll() { score = 0; combo = 0; maxCombo = 0 }
}