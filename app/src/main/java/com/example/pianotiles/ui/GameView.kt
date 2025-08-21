package com.example.pianotiles.ui

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.example.pianotiles.game.GameConfig
import com.example.pianotiles.game.GameEngine
import com.example.pianotiles.game.GameEngine.Judgement

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private val engine = GameEngine(context)
    @Volatile private var running = false
    private var gameThread: Thread? = null

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    fun isPaused(): Boolean = engine.state == GameEngine.GameState.PAUSED
    fun pauseGame() = engine.pause()
    fun resumeGame() = engine.resume()

    fun applyConfig(cfg: GameConfig) {
    engine.applyConfig(cfg)
    hapticsEnabled = cfg.hapticsEnabled
    soundEnabled = cfg.soundEnabled
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        gameThread = Thread {
            var lastNs = System.nanoTime()
            val targetFps = 60.0
            val frameNs = (1_000_000_000.0 / targetFps).toLong()

            while (running) {
                val now = System.nanoTime()
                val dt = (now - lastNs) / 1_000_000_000.0f
                lastNs = now

                try {
                    engine.update(dt, width, height)
                } catch (_: Throwable) { /* swallow to avoid crash; next frame continues */ }

                var canvas: Canvas? = null
                try {
                    canvas = holder.lockCanvas()
                    if (canvas != null) {
                        engine.draw(canvas)
                    }
                } catch (_: Throwable) {
                    // ignore rendering glitches
                } finally {
                    try { if (canvas != null) holder.unlockCanvasAndPost(canvas) } catch (_: Throwable) {}
                }

                val elapsed = System.nanoTime() - now
                val sleep = (frameNs - elapsed) / 1_000_000
                if (sleep > 0) {
                    try { Thread.sleep(sleep) } catch (_: InterruptedException) {}
                }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        engine.onSizeChanged(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try { gameThread?.interrupt() } catch (_: Throwable) {}
        try { gameThread?.join(800) } catch (_: Throwable) {}
        gameThread = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
    if (event.action == MotionEvent.ACTION_DOWN) {
            // GameOver：左半屏分享，右半屏重开
            if (engine.state == GameEngine.GameState.GAME_OVER) {
                if (event.x < width * 0.5f) shareScore() else engine.reset()
                return true
            }
            // 暂停按钮改由 Activity 控制，不再用区域触控
            val j = try { engine.onTap(event.x, event.y) } catch (_: Throwable) { Judgement.NONE }
            if (j != Judgement.NONE) {
                performHaptic(j)
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    private var hapticsEnabled: Boolean = true
    private var soundEnabled: Boolean = false

    private fun performHaptic(j: Judgement) {
        if (!hapticsEnabled) return
        val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        val dur = when (j) {
            Judgement.PERFECT -> 10L
            Judgement.GREAT -> 16L
            Judgement.GOOD -> 24L
            Judgement.MISS -> 32L
            else -> 0L
        }
        if (dur <= 0) return
        try {
            if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") vib.vibrate(dur)
        } catch (_: Throwable) { }
    }

    private fun shareScore() {
        // 简单分享文字：应用名称与分数/最佳成绩（本地化）
        val app = context.getString(com.example.pianotiles.R.string.app_name)
        val scoreText = context.getString(com.example.pianotiles.R.string.score, engine.currentScore())
        val bestText = context.getString(com.example.pianotiles.R.string.high_score, engine.bestScore())
        val text = "$app — $scoreText, $bestText"
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(android.content.Intent.EXTRA_TEXT, text)
        try {
            context.startActivity(android.content.Intent.createChooser(intent, context.getString(com.example.pianotiles.R.string.share_via)))
        } catch (_: Throwable) { /* no activity to handle share */ }
    }
}