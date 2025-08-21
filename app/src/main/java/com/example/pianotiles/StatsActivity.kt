package com.example.pianotiles

import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.pianotiles.stats.StatsRepository

class StatsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    enableImmersive()
        setContentView(R.layout.activity_stats)

        val repo = StatsRepository(this)
        val header: TextView = findViewById(R.id.tv_header)
        val lv: ListView = findViewById(R.id.lv_leaderboard)
        val btnClear: android.widget.Button = findViewById(R.id.btn_clear)

        fun refresh() {
            val ag = repo.aggregates()
            val playMin = ag.totalPlayTimeSec / 60
            header.text = getString(R.string.stats_header, ag.totalRuns, ag.bestScore, ag.avgScore, playMin)
            val diffKeys = resources.getStringArray(R.array.difficulty_keys)
            val diffLabels = resources.getStringArray(R.array.difficulty_labels)
            val items = repo.getLeaderboard().mapIndexed { idx, e ->
                val mode = if (e.useBeat) getString(R.string.beat_mode_compact, e.bpm.toInt()) else run {
                    val i = diffKeys.indexOf(e.difficulty)
                    if (i in diffLabels.indices) diffLabels[i] else e.difficulty
                }
                getString(R.string.leaderboard_item, idx + 1, e.score, e.combo, e.lanes, mode)
            }
            lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        }
        refresh()

        btnClear.setOnClickListener {
            repo.clear()
            refresh()
        }
    }

    private fun enableImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersive()
    }

    override fun onResume() {
        super.onResume()
        enableImmersive()
    }
}
