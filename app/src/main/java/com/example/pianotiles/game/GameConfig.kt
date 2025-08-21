package com.example.pianotiles.game

data class GameConfig(
    val laneCount: Int = 4,
    val difficultyLabel: String = "Normal",
    val baseSpeed: Float = 600f,
    val maxSpeed: Float = 1600f,
    val speedIncrementPerSpawn: Float = 2f,
    val baseSpawnInterval: Float = 0.8f,
    val spawnDecrementPerSpawn: Float = 0.005f,
    val minSpawnInterval: Float = 0.30f,
    val hapticsEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val useBeat: Boolean = false,
    val bpm: Float = 120f,
    val usePattern: Boolean = false,
    val patternSeed: Long = 0L,
)
