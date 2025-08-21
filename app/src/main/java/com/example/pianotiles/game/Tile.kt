package com.example.pianotiles.game

data class Tile(
    var lane: Int,
    var y: Float,
    var pressed: Boolean = false
)