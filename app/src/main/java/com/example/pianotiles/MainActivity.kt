package com.example.pianotiles

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.pianotiles.game.GameConfig
import com.example.pianotiles.ui.GameView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    enableImmersive()
        setContentView(R.layout.activity_main)
        val lanes = intent.getIntExtra("lanes", 4)
        val diff = intent.getStringExtra("difficulty") ?: "Normal"
        val haptics = intent.getBooleanExtra("haptics", true)
        val sound = intent.getBooleanExtra("sound", false)
        val useBeat = intent.getBooleanExtra("useBeat", false)
        val bpm = intent.getFloatExtra("bpm", 120f)
        val usePattern = intent.getBooleanExtra("usePattern", false)
        val seed = intent.getLongExtra("seed", 0L)
        val config = when (diff) {
            "Easy" -> GameConfig(laneCount = lanes, difficultyLabel = diff, baseSpeed = 500f, baseSpawnInterval = 0.9f, minSpawnInterval = 0.35f, hapticsEnabled = haptics, soundEnabled = sound, useBeat = useBeat, bpm = bpm, usePattern = usePattern, patternSeed = seed)
            "Hard" -> GameConfig(laneCount = lanes, difficultyLabel = diff, baseSpeed = 700f, baseSpawnInterval = 0.7f, minSpawnInterval = 0.28f, hapticsEnabled = haptics, soundEnabled = sound, useBeat = useBeat, bpm = bpm, usePattern = usePattern, patternSeed = seed)
            else -> GameConfig(laneCount = lanes, difficultyLabel = diff, hapticsEnabled = haptics, soundEnabled = sound, useBeat = useBeat, bpm = bpm, usePattern = usePattern, patternSeed = seed)
        }
        val gameView: GameView = findViewById(R.id.gameView)
        gameView.applyConfig(config)

        val btn: android.widget.ImageButton = findViewById(R.id.btnPause)
        fun updateIcon() {
            btn.setImageResource(if (gameView.isPaused()) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
        }
        updateIcon()
        btn.setOnClickListener {
            if (gameView.isPaused()) gameView.resumeGame() else gameView.pauseGame()
            updateIcon()
        }
    }

    private fun enableImmersive() {
        // Edge-to-edge布局并隐藏系统栏，白条/黑杠进入沉浸
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