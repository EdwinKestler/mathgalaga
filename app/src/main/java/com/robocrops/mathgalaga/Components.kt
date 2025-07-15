package com.robocrops.mathgalaga

import android.graphics.Color

data class Position(var x: Float, var y: Float)
data class Size(val width: Int, val height: Int)
data class Velocity(val vx: Int, val vy: Int)
data class AlienMovement(var speed: Int, var direction: Int)
data class Shooter(var interval: Long, var lastShot: Long, var chance: Double?, var bulletSpeed: Int)
data class Player(var color: Int, var dm: DifficultyManager, var lives: Int, var score: Double, var problem: Map<String, Any>, var streak: Int, var state: String, var respawnTime: Long, var clearedTop: Boolean, var startX: Float)
data class Alien(val number: Int?, val shape: String)
data class Bullet(val ownerEid: Int)
data class Explosion(val start: Long)
data class Lifespan(val start: Long, val duration: Long)
data class FloatUp(val speed: Int)