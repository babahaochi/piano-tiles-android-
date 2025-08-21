package com.example.pianotiles.game

import android.content.Context
import android.graphics.*
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.example.pianotiles.R
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random
import com.example.pianotiles.stats.StatsRepository

class GameEngine(private val context: Context) {

    enum class GameState { RUNNING, PAUSED, GAME_OVER }
    enum class Judgement { PERFECT, GREAT, GOOD, MISS, NONE }

    private val bgPaint = Paint().apply { color = ContextCompat.getColor(context, R.color.bg) }
    private val lanePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.tile_round)
        setShadowLayer(dp(6f), 0f, dp(2f), Color.argb(90, 0, 0, 0))
    }
    private val tilePressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.tile_round_pressed)
        setShadowLayer(dp(6f), 0f, dp(2f), Color.argb(110, 0, 0, 0))
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(21f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val overlayPaint = Paint().apply { color = Color.argb(140, 0, 0, 0) }
    private val overlayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(28f)
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val lock = Any()
    private var widthPx = 0
    private var heightPx = 0
    private var lanes = emptyList<Lane>()
    private var laneCount = 4
    private val tiles = mutableListOf<Tile>()
    private var spawnTimer = 0f
    private var spawnInterval = 0.8f
    private var speed = 600f
    private var maxSpeed = 1600f
    private var speedIncrementPerSpawn = 2f
    private var spawnDecrementPerSpawn = 0.005f
    private var useBeat = false
    private var beatInterval = 0.5f // 120 BPM
    private var usePattern = false
    private var patternSeed: Long = 0
    private var pattern: PatternGenerator? = null
    private val tileHeight get() = dp(120f)
    private val targetY get() = heightPx - tileHeight / 2f
    private val hitWindowPerfect get() = dp(20f)
    private val hitWindowGreat get() = dp(50f)
    private val hitWindowGood get() = dp(90f)

    private val score = ScoreManager(context)
    private val tmpRect = RectF()
    private var flashTimer = 0f
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent)
        alpha = 0
    }
    private var lastFlashLane: Int = -1
    private val flashBandHeight get() = dp(44f)
    private val flashMaxAlpha = 140
    private val pool = ArrayDeque<Tile>(64)
    private var sound: SoundManager? = null
    private data class FloatText(var text: String, var x: Float, var y: Float, var life: Float)
    private val floatTexts = mutableListOf<FloatText>()
    private data class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float)
    private val sparks = mutableListOf<Spark>()
    private val sparkPaint = Paint().apply { color = Color.WHITE; strokeWidth = dp(2f) }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        color = ContextCompat.getColor(context, R.color.accent)
    }
    private val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 0, 0, 0) }
    private val stats by lazy { StatsRepository(context) }
    private var sessionStartMs: Long = System.currentTimeMillis()
    private var lastConfig: GameConfig = GameConfig()

    // Extra FX
    private data class Ripple(var x: Float, var y: Float, var r: Float, var vr: Float, var alpha: Int, var life: Float)
    private val ripples = mutableListOf<Ripple>()
    private var comboPulse: Float = 0f
    private var shakeTime: Float = 0f
    private var shakeAmp: Float = 0f

    // HUD helpers
    private fun localizeDifficulty(key: String): String {
        return try {
            val keys = context.resources.getStringArray(R.array.difficulty_keys)
            val labels = context.resources.getStringArray(R.array.difficulty_labels)
            val i = keys.indexOf(key)
            if (i >= 0 && i < labels.size) labels[i] else key
        } catch (_: Throwable) { key }
    }
    private fun languageDisplay(): String {
        return try {
            val prefs = context.getSharedPreferences("menu_prefs", Context.MODE_PRIVATE)
            val k = prefs.getString("language", "system") ?: "system"
            when (k) {
                "zh" -> "中文"
                "en" -> "EN"
                else -> {
                    val lang = context.resources.configuration.locales[0].language
                    if (lang.startsWith("zh", true)) "中文" else "EN"
                }
            }
        } catch (_: Throwable) { "EN" }
    }

    var state: GameState = GameState.RUNNING
        private set

    fun currentScore(): Int = score.score
    fun bestScore(): Int = score.best

    fun onSizeChanged(w: Int, h: Int) {
        synchronized(lock) {
            widthPx = w
            heightPx = h
            lanes = (0 until laneCount).map { i ->
                val laneWidth = widthPx / laneCount.toFloat()
                val left = i * laneWidth
                Lane(index = i, left = left, right = left + laneWidth)
            }
        }
    }

    fun update(dt: Float, w: Int, h: Int) {
        if (w != widthPx || h != heightPx) onSizeChanged(w, h)
        if (widthPx == 0 || heightPx == 0) return
        if (state != GameState.RUNNING) return

        spawnTimer += dt
        val interval = if (useBeat) beatInterval else spawnInterval
        if (spawnTimer >= interval) {
            spawnTimer -= interval
            spawnTile()
            if (!useBeat) {
                // 难度渐进提升（节拍模式不按生成次数加速）
                speed = (speed + speedIncrementPerSpawn).coerceAtMost(maxSpeed)
                spawnInterval = (spawnInterval - spawnDecrementPerSpawn).coerceAtLeast(0.30f)
            }
        }

    var shouldGameOver = false
    synchronized(lock) {
            val iter = tiles.listIterator()
            while (iter.hasNext()) {
                val t = iter.next()
                t.y += speed * dt
                if (t.y - tileHeight > heightPx) {
                    // 未命中方块落出屏幕 -> 游戏结束
            iter.remove(); pool.addLast(t)
            shouldGameOver = true
                    break
                }
            }

            if (flashTimer > 0f) {
                flashTimer -= dt
                flashPaint.alpha = (flashMaxAlpha * (flashTimer / 0.08f)).toInt().coerceIn(0, flashMaxAlpha)
                if (flashPaint.alpha <= 0) lastFlashLane = -1
            } else {
                flashPaint.alpha = 0
                lastFlashLane = -1
            }

            // 浮标与粒子
            val itF = floatTexts.listIterator()
            while (itF.hasNext()) {
                val f = itF.next()
                f.y -= dp(40f) * dt
                f.life -= dt
                if (f.life <= 0f) itF.remove()
            }
            val itS = sparks.listIterator()
            while (itS.hasNext()) {
                val s = itS.next()
                s.x += s.vx * dt
                s.y += s.vy * dt
                s.vy += dp(400f) * dt // gravity
                s.life -= dt
                if (s.life <= 0f) itS.remove()
            }
            val itR = ripples.listIterator()
            while (itR.hasNext()) {
                val r = itR.next()
                r.r += r.vr * dt
                r.life -= dt
                r.alpha = (r.life / 0.35f * 160).toInt().coerceIn(0, 160)
                if (r.life <= 0f) itR.remove()
            }
    }
        // HUD pulse & screen shake decay
        if (comboPulse > 0f) comboPulse = (comboPulse - dt).coerceAtLeast(0f)
        if (shakeTime > 0f) {
            shakeTime -= dt
            if (shakeTime < 0f) shakeTime = 0f
        }
        if (shouldGameOver) gameOver()
    }

    fun draw(canvas: Canvas) {
    // 背景（渐变）+ 轻微屏幕抖动（MISS 时）
    val sx = if (shakeTime > 0f) ((Math.random() - 0.5) * dp(6f)).toFloat() else 0f
    val sy = if (shakeTime > 0f) ((Math.random() - 0.5) * dp(6f)).toFloat() else 0f
    canvas.save()
    canvas.translate(sx, sy)
    canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), bgPaint)
        // 车道线
        lanes.forEach { lane ->
            canvas.drawLine(lane.right, 0f, lane.right, heightPx.toFloat(), lanePaint)
        }
    // 方块（圆角 + 阴影 + 简易尾迹）
        synchronized(lock) {
            tiles.forEach { t ->
                val laneIdx = if (lanes.isNotEmpty()) t.lane.coerceIn(0, lanes.size - 1) else 0
                val lane = lanes[laneIdx]
        val left = lane.left + dp(10f)
        val right = lane.right - dp(10f)
        // trail bands
        val trailPaint1 = Paint(tilePaint).apply { alpha = 36 }
        val trailPaint2 = Paint(tilePaint).apply { alpha = 18 }
        tmpRect.set(left, (t.y - dp(36f)).coerceAtLeast(0f), right, t.y)
        canvas.drawRoundRect(tmpRect, dp(10f), dp(10f), trailPaint1)
        tmpRect.set(left + dp(4f), (t.y - dp(60f)).coerceAtLeast(0f), right - dp(4f), t.y - dp(20f))
        canvas.drawRoundRect(tmpRect, dp(8f), dp(8f), trailPaint2)
        // tile body
        tmpRect.set(left, t.y, right, t.y + tileHeight)
        canvas.drawRoundRect(tmpRect, dp(12f), dp(12f), if (t.pressed) tilePressedPaint else tilePaint)
            }
        }
        // 判定线
        val y = targetY
        canvas.drawLine(0f, y, widthPx.toFloat(), y, lanePaint)
        // HUD（增加当前 轨道/难度/语言 + 连击轻微脉冲）
        val scoreText = context.getString(R.string.score, score.score)
        val bestText = context.getString(R.string.high_score, score.best)
        val comboText = context.getString(R.string.combo, score.combo)
        val infoText = "${context.getString(R.string.lanes)}: ${lastConfig.laneCount} | " +
            "${context.getString(R.string.difficulty)}: ${localizeDifficulty(lastConfig.difficultyLabel)} | " +
            "${context.getString(R.string.language)}: ${languageDisplay()}"

        // 背景条，增强对比度
        val lines = arrayOf(scoreText, bestText, comboText, infoText)
        var maxW = 0f
        for (t in lines) maxW = maxOf(maxW, textPaint.measureText(t))
        val padH = dp(8f)
        val padV = dp(8f)
        val lineH = dp(24f)
        val top = dp(8f)
        val left = dp(8f)
        val bottom = top + lineH * lines.size + padV
        val right = left + maxW + padH * 2
        tmpRect.set(left, top, right, bottom)
        canvas.drawRoundRect(tmpRect, dp(10f), dp(10f), hudBgPaint)

        // 文本
        canvas.drawText(scoreText, left + padH, top + dp(20f), textPaint)
        canvas.drawText(bestText, left + padH, top + dp(44f), textPaint)
        val cx = left + padH
        val cy = top + dp(68f)
            if (comboPulse > 0f) {
                val k = 1f + 0.12f * (comboPulse / 0.2f).coerceIn(0f, 1f)
                canvas.save()
                canvas.translate(cx, cy)
                canvas.scale(k, k)
                canvas.drawText(comboText, 0f, 0f, textPaint)
                canvas.restore()
            } else {
                canvas.drawText(comboText, cx, cy, textPaint)
            }
        canvas.drawText(infoText, left + padH, top + dp(92f), textPaint)

        // 命中闪光（更柔和：仅在命中的车道上画一条小圆角色带）
        if (flashPaint.alpha > 0 && lastFlashLane >= 0 && lastFlashLane < lanes.size) {
            val lane = lanes[lastFlashLane]
            val left = lane.left + dp(6f)
            val right = lane.right - dp(6f)
            val top = y - flashBandHeight / 2f
            val bottom = y + flashBandHeight / 2f
            tmpRect.set(left, top, right, bottom)
            canvas.drawRoundRect(tmpRect, dp(12f), dp(12f), flashPaint)
        }

        // 浮标 & 扩散圆环
        val ftPaint = Paint(textPaint).apply { textSize = sp(22f) }
        synchronized(lock) {
            for (f in floatTexts) {
                canvas.drawText(f.text, f.x, f.y, ftPaint)
            }
            // 火花
            for (s in sparks) {
                canvas.drawPoint(s.x, s.y, sparkPaint)
            }
            // rings
            for (r in ripples) {
                ringPaint.alpha = r.alpha
                canvas.drawCircle(r.x, r.y, r.r, ringPaint)
            }
        }

        if (state != GameState.RUNNING) {
            canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), overlayPaint)
            val centerX = widthPx / 2f
            val centerY = heightPx / 2f
            val title = if (state == GameState.PAUSED) context.getString(R.string.paused) else context.getString(R.string.game_over)
            canvas.drawText(title, centerX, centerY - dp(10f), overlayTextPaint)
            val sub = if (state == GameState.PAUSED) context.getString(R.string.tap_resume) else context.getString(R.string.tap_restart)
            canvas.drawText(sub, centerX, centerY + dp(24f), overlayTextPaint.apply { textSize = sp(18f) })
            // reset size
            overlayTextPaint.textSize = sp(28f)
    }
    canvas.restore()
    }

    fun onTap(x: Float, y: Float): Judgement {
        if (state == GameState.PAUSED) return Judgement.NONE
        if (state == GameState.GAME_OVER) return Judgement.NONE
    if (lanes.isEmpty()) return Judgement.NONE
    if (widthPx <= 0 || heightPx <= 0) return Judgement.NONE

    val laneWidth = widthPx / laneCount.toFloat()
    val index = floor(x / laneWidth).toInt().coerceIn(0, laneCount - 1)

        // 1) 优先：如果手指位置正好落在该车道的某个方块矩形内，则视为命中并加分
        val tileUnderFinger = synchronized(lock) {
            tiles
                .asSequence()
                .filter { it.lane == index && y >= it.y && y <= it.y + tileHeight }
                // 选择更靠近判定线（y 更大的）方块，以避免误选到上方远处的方块
                .maxByOrNull { it.y }
        }

        if (tileUnderFinger != null) {
            // 即使未到判定线，也应加分；但为了保持手感，接近判定线时给予更高评价
            val center = tileUnderFinger.y + tileHeight / 2f
            val diff = abs(center - targetY)
            val judgement = when {
                diff <= hitWindowPerfect -> Judgement.PERFECT
                diff <= hitWindowGreat -> Judgement.GREAT
                else -> Judgement.GOOD // 其余情况统一按 GOOD 计分，确保“点到就加分”
            }
            synchronized(lock) {
                tileUnderFinger.pressed = true
                score.hit(judgement)
                tiles.remove(tileUnderFinger); pool.addLast(tileUnderFinger)
                lastFlashLane = index
                flashTimer = 0.08f
            }
            comboPulse = 0.2f
            sound?.playNote(index, judgement)
            addHitEffects(index, judgement)
            return judgement
        }

        // 2) 否则，沿用原“靠近判定线窗口”的判定：容许玩家稍早/稍晚在判定线附近命中
        val candidate = synchronized(lock) {
            tiles
                .filter { it.lane == index }
                .minByOrNull { abs((it.y + tileHeight / 2f) - targetY) }
        }

        if (candidate != null) {
            val center = candidate.y + tileHeight / 2f
            val diff = abs(center - targetY)
            val judgement = when {
                diff <= hitWindowPerfect -> Judgement.PERFECT
                diff <= hitWindowGreat -> Judgement.GREAT
                diff <= hitWindowGood -> Judgement.GOOD
                else -> Judgement.MISS
            }
            if (judgement != Judgement.MISS) {
                synchronized(lock) {
                    candidate.pressed = true
                    score.hit(judgement)
                    tiles.remove(candidate); pool.addLast(candidate)
                    lastFlashLane = index
                    flashTimer = 0.08f
        }
        comboPulse = 0.2f
                sound?.playNote(index, judgement)
                addHitEffects(index, judgement)
                return judgement
            }
        }
        // 空点：轻微失误，不结束游戏
    score.resetComboOnMiss()
    // subtle shake on miss
    shakeTime = 0.08f
    sound?.play(Judgement.MISS)
    addHitEffects(index, Judgement.MISS)
        return Judgement.MISS
    }

    fun pause() { if (state == GameState.RUNNING) { state = GameState.PAUSED; sound?.stopBackgroundSoft() } }
    fun resume() { if (state == GameState.PAUSED) { state = GameState.RUNNING; sound?.startBackgroundSoft() } }

    fun reset() {
        synchronized(lock) {
            pool.addAll(tiles)
            tiles.clear()
            flashTimer = 0f
        }
        spawnTimer = 0f
        speed = 600f
        spawnInterval = 0.8f
        score.resetAll()
        state = GameState.RUNNING
        sessionStartMs = System.currentTimeMillis()
    }

    private fun gameOver() {
        if (state == GameState.GAME_OVER) return
        state = GameState.GAME_OVER
        score.resetComboOnMiss()
        // persist stats
        val durationSec = ((System.currentTimeMillis() - sessionStartMs) / 1000L).coerceAtLeast(0L)
        try { stats.setLastSessionDuration(durationSec) } catch (_: Throwable) {}
        try {
            stats.addEntry(
                StatsRepository.Entry(
                    score = score.score,
                    combo = score.maxCombo,
                    lanes = laneCount,
                    difficulty = lastConfig.difficultyLabel,
                    useBeat = lastConfig.useBeat,
                    bpm = lastConfig.bpm,
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (_: Throwable) {}
    }

    private fun spawnTile() {
        val lane = pattern?.nextLane() ?: Random.nextInt(0, laneCount)
        synchronized(lock) {
            val t = if (pool.isNotEmpty()) pool.removeFirst().apply { this.lane = lane; y = -tileHeight; pressed = false } else Tile(lane = lane, y = -tileHeight)
            tiles.add(t)
        }
    }

    fun applyConfig(cfg: GameConfig) {
        val prevLaneCount = laneCount
        laneCount = cfg.laneCount.coerceIn(3, 6)
        speed = cfg.baseSpeed
        maxSpeed = cfg.maxSpeed
        speedIncrementPerSpawn = cfg.speedIncrementPerSpawn
        spawnInterval = cfg.baseSpawnInterval
        spawnDecrementPerSpawn = cfg.spawnDecrementPerSpawn
        useBeat = cfg.useBeat
        beatInterval = (60f / cfg.bpm).coerceIn(0.2f, 2.0f)
    if (cfg.soundEnabled && sound == null) sound = SoundManager(context, true) else if (!cfg.soundEnabled) { sound?.release(); sound = null }
        usePattern = cfg.usePattern
        patternSeed = cfg.patternSeed
        pattern = if (usePattern) PatternGenerator(laneCount, patternSeed) else null
        lastConfig = cfg
    sessionStartMs = System.currentTimeMillis()
    // start soft background when running
    if (state == GameState.RUNNING) sound?.startBackgroundSoft() else sound?.stopBackgroundSoft()
        // 如果车道数变化，需重建车道并清空当前方块以避免越界
        if (prevLaneCount != laneCount) {
            synchronized(lock) {
                tiles.clear()
                pool.clear()
            }
            if (widthPx > 0 && heightPx > 0) {
                onSizeChanged(widthPx, heightPx)
            }
        }
    }

    private fun addHitEffects(laneIndex: Int, judgement: Judgement) {
        synchronized(lock) {
            if (lanes.isEmpty()) return
            val idx = if (lanes.isNotEmpty()) laneIndex.coerceIn(0, lanes.size - 1) else 0
            val lane = lanes[idx]
            val cx = (lane.left + lane.right) / 2f
            val cy = targetY
            val text = when (judgement) {
                Judgement.PERFECT -> context.getString(R.string.perfect)
                Judgement.GREAT -> context.getString(R.string.great)
                Judgement.GOOD -> context.getString(R.string.good)
                else -> context.getString(R.string.miss)
            }
            floatTexts.add(FloatText(text, cx, cy - dp(8f), 0.6f))
            repeat(10) {
                val angle = (it / 10f) * (Math.PI * 2).toFloat()
                val speed = dp(200f) + it * 6
                sparks.add(Spark(cx, cy, kotlin.math.cos(angle) * speed, kotlin.math.sin(angle) * speed, 0.25f))
            }
            // ripple ring
            val baseLife = 0.35f
            ripples.add(Ripple(cx, cy, dp(8f), dp(480f), 160, baseLife))
        }
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, context.resources.displayMetrics)
}

data class Lane(val index: Int, val left: Float, val right: Float)