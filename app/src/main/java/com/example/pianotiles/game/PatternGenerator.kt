package com.example.pianotiles.game

import kotlin.random.Random

class PatternGenerator(private val laneCount: Int, seed: Long) {
    private val rng = if (seed == 0L) Random.Default else Random(seed)
    private var lastLane = -1

    fun nextLane(): Int {
        val jump = rng.nextInt(0, 5) == 0
        val lane = if (jump) rng.nextInt(0, laneCount) else {
            var v: Int
            do { v = rng.nextInt(0, laneCount) } while (v == lastLane)
            v
        }
        lastLane = lane
        return lane
    }
}
