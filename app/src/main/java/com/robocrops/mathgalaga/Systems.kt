package com.robocrops.mathgalaga

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import kotlin.math.abs
import kotlin.random.Random

// Target frame time for delta scaling (60 FPS)
private const val TARGET_FRAME_TIME_MS = 1000.0 / 60.0

open class BaseSystem(protected val controller: GameController) {
    open fun update(delta: Double) {}
}

class InputSystem(controller: GameController) : BaseSystem(controller) {
    override fun update(delta: Double) {
        // Handled by handleTouch/handleJoystick
    }
}

class MovementSystem(controller: GameController) : BaseSystem(controller) {
    override fun update(delta: Double) {
        val deltaFactor = delta / TARGET_FRAME_TIME_MS
        // Alien movement
        controller.world["alien_movement"]?.forEach { (eid, amAny) ->
            val am = amAny as? AlienMovement ?: return@forEach
            val pos = controller.world["position"]?.get(eid) as? Position ?: return@forEach
            val size = controller.world["size"]?.get(eid) as? Size ?: return@forEach
            pos.x += (am.speed * am.direction * deltaFactor).toFloat()
            if (pos.x < 0 || pos.x + size.width > Config.ScreenSettings.WIDTH) {
                am.direction = -am.direction
                pos.y += (20 * deltaFactor).toFloat()
            }
        }
        // Bullet/velocity movement
        controller.world["velocity"]?.forEach { (eid, velAny) ->
            val vel = velAny as? Velocity ?: return@forEach
            val pos = controller.world["position"]?.get(eid) as? Position ?: return@forEach
            pos.x += (vel.vx * deltaFactor).toFloat()
            pos.y += (vel.vy * deltaFactor).toFloat()
        }
    }
}

class ShootingSystem(controller: GameController) : BaseSystem(controller) {
    override fun update(delta: Double) {
        val now = System.currentTimeMillis()
        val deltaFactor = delta / TARGET_FRAME_TIME_MS
        controller.world["shooter"]?.forEach { (eid, sAny) ->
            val s = sAny as? Shooter ?: return@forEach
            if (s.chance != null) {
                // Alien shooting - scale chance for frame-rate independence
                val scaledChance = s.chance!! * deltaFactor
                if (Random.nextFloat() < scaledChance
                    && now - s.lastShot > s.interval
                ) {
                    controller.createBullet(eid, s.bulletSpeed)
                    s.lastShot = now
                }
            }
            // Player shooting: Handled in input
        }
    }
}

class CollisionSystem(controller: GameController) : BaseSystem(controller) {
    override fun update(delta: Double) {
        val hits = mutableListOf<Pair<Int, Int>>()
        val bulletEids = controller.world["bullet"]?.keys?.toList() ?: emptyList()
        val alienEids = controller.world["alien"]?.keys?.toList() ?: emptyList()

        if (bulletEids.isEmpty() || alienEids.isEmpty()) return

        // Optimized: Sort by x-position for sweep-and-prune
        val sortedBullets = bulletEids.sortedBy { (controller.world["position"]?.get(it) as? Position)?.x ?: Float.MAX_VALUE }
        val sortedAliens = alienEids.sortedBy { (controller.world["position"]?.get(it) as? Position)?.x ?: Float.MAX_VALUE }

        var alienIndex = 0
        sortedBullets.forEach { bEid ->
            val bPos = controller.world["position"]?.get(bEid) as? Position ?: return@forEach
            val bSize = controller.world["size"]?.get(bEid) as? Size ?: return@forEach
            val bRect = controller.getRect(bEid)
            var hit = false

            // Advance alienIndex to potential overlaps
            while (alienIndex < sortedAliens.size) {
                val aEidTemp = sortedAliens[alienIndex]
                val aPos = controller.world["position"]?.get(aEidTemp) as? Position ?: break
                val aSize = controller.world["size"]?.get(aEidTemp) as? Size ?: break
                if (aPos.x + aSize.width >= bPos.x) break
                alienIndex++
            }

            // Check overlapping aliens
            for (j in alienIndex until sortedAliens.size) {
                val aEid = sortedAliens[j]
                if (controller.world["explosion"]?.containsKey(aEid) == true) continue
                val aPos = controller.world["position"]?.get(aEid) as? Position ?: continue
                controller.world["size"]?.get(aEid) as? Size ?: continue
                if (aPos.x > bPos.x + bSize.width) break // No more overlaps
                val aRect = controller.getRect(aEid)
                if (Utils.collides(bRect, aRect)) {
                    hits.add(Pair(bEid, aEid))
                    hit = true
                    break
                }
            }
            if (hit) return@forEach

            // Player checks (fewer, no sorting needed)
            controller.playerEids.forEach { pEid ->
                val p = controller.world["player"]?.get(pEid) as? Player ?: return@forEach
                if (p.state != "active") return@forEach
                val pRect = controller.getRect(pEid)
                if (Utils.collides(bRect, pRect)) {
                    hits.add(Pair(bEid, pEid))
                    return@forEach
                }
            }
        }
        // Process all hits
        hits.forEach { (bEid, targetEid) ->
            val ownerEid = (controller.world["bullet"]?.get(bEid) as? Bullet)?.ownerEid ?: return@forEach
            controller.removeEntity(bEid)
            if (controller.playerEids.contains(targetEid)) {
                // Alien bullet hit player
                if (controller.playerEids.contains(ownerEid)) return@forEach // Skip player bullet hitting player
                val p = controller.world["player"]?.get(targetEid) as? Player ?: return@forEach
                p.lives -= 1
                controller.view.playHitSound()
                createPlayerHitExplosion(targetEid)  // Added: Create explosion animation when player is hit by bullet
                if (p.lives > 0) {
                    p.state = "respawning"
                    p.respawnTime = System.currentTimeMillis()
                } else {
                    p.state = "dead"
                }
            } else {
                // Player bullet hit alien
                if (!controller.playerEids.contains(ownerEid)) return@forEach // Only process player bullets on aliens
                val p = controller.world["player"]?.get(ownerEid) as? Player ?: return@forEach
                val a = controller.world["alien"]?.get(targetEid) as? Alien ?: return@forEach
                var canKill = true
                if (a.number != null) {
                    if (a.number != (p.problem["answer"] as? Int) || p.clearedTop)
                        canKill = false
                }
                if (!canKill) return@forEach
                // Kill alien
                val now = System.currentTimeMillis()
                controller.world.getOrPut("explosion") { mutableMapOf() }[targetEid] = Explosion(now)
                controller.world.getOrPut("lifespan") { mutableMapOf() }[targetEid] = Lifespan(now, Config.ExplosionSettings.DURATION)
                controller.world["alien_movement"]?.remove(targetEid)
                controller.world["shooter"]?.remove(targetEid)
                controller.world["collider"]?.remove(targetEid)
                controller.world["render"]?.set(
                    targetEid, mapOf(
                        "type" to "explosion",
                        "color" to Config.ColorSettings.YELLOW,
                        "max_radius" to 30
                    )
                )
                controller.view.playExplosionSound()
                if (a.number == null) {
                    p.score += 1.0
                } else {
                    p.clearedTop = true
                    p.streak += 1
                    val bonus = 1 + 0.1 * p.streak
                    p.score += bonus
                    val pos = controller.world["position"]?.get(targetEid) as? Position ?: return@forEach
                    val comboEid = controller.newEntity()
                    controller.world["position"]?.set(comboEid, Position(pos.x, pos.y))
                    controller.world["render"]?.set(
                        comboEid, mapOf(
                            "type" to "combo_text",
                            "text" to "×${p.streak}",
                            "color" to Config.ColorSettings.YELLOW,
                            "font" to Config.FontSettings.COMBO
                        )
                    )
                    controller.world.getOrPut("lifespan") { mutableMapOf() }[comboEid] =
                        Lifespan(now, Config.ComboSettings.DURATION)
                    controller.world.getOrPut("float_up") { mutableMapOf() }[comboEid] =
                        FloatUp(Config.ComboSettings.FLOAT_SPEED)
                    p.dm.record(true)
                }
            }
        }

        // Added: Detect and handle player-alien crashes
        // Only check active players against non-exploding aliens
        val activePlayerEids = controller.playerEids.filter {
            (controller.world["player"]?.get(it) as? Player)?.state == "active"
        }
        if (activePlayerEids.isNotEmpty() && alienEids.isNotEmpty()) {
            val sortedPlayers = activePlayerEids.sortedBy { (controller.world["position"]?.get(it) as? Position)?.x ?: Float.MAX_VALUE }
            var playerIndex = 0
            sortedPlayers.forEach { pEid ->
                val pPos = controller.world["position"]?.get(pEid) as? Position ?: return@forEach
                val pSize = controller.world["size"]?.get(pEid) as? Size ?: return@forEach
                val pRect = controller.getRect(pEid)

                // Advance alienIndex to potential overlaps (reuse alienIndex but reset for each player)
                alienIndex = 0
                while (alienIndex < sortedAliens.size) {
                    val aEidTemp = sortedAliens[alienIndex]
                    val aPos = controller.world["position"]?.get(aEidTemp) as? Position ?: break
                    val aSize = controller.world["size"]?.get(aEidTemp) as? Size ?: break
                    if (aPos.x + aSize.width >= pPos.x) break
                    alienIndex++
                }

                // Check overlapping aliens
                for (j in alienIndex until sortedAliens.size) {
                    val aEid = sortedAliens[j]
                    if (controller.world["explosion"]?.containsKey(aEid) == true) continue
                    val aPos = controller.world["position"]?.get(aEid) as? Position ?: continue
                    val aSize = controller.world["size"]?.get(aEid) as? Size ?: continue
                    if (aPos.x > pPos.x + pSize.width) break // No more overlaps
                    val aRect = controller.getRect(aEid)
                    if (Utils.collides(pRect, aRect)) {
                        handlePlayerAlienCrash(pEid, aEid)
                        break
                    }
                }
            }
        }
    }

    // Added: Helper function to create explosion entity at player's position when hit
    private fun createPlayerHitExplosion(playerEid: Int) {
        val pos = controller.world["position"]?.get(playerEid) as? Position ?: return
        val size = controller.world["size"]?.get(playerEid) as? Size ?: return
        val now = System.currentTimeMillis()
        val expEid = controller.newEntity()
        controller.world.getOrPut("position") { mutableMapOf() }[expEid] = Position(pos.x, pos.y)
        controller.world.getOrPut("size") { mutableMapOf() }[expEid] = size
        controller.world.getOrPut("explosion") { mutableMapOf() }[expEid] = Explosion(now)
        controller.world.getOrPut("lifespan") { mutableMapOf() }[expEid] = Lifespan(now, Config.PlayerHitSettings.EXPLOSION_DURATION)
        controller.world.getOrPut("render") { mutableMapOf() }[expEid] = mapOf(
            "type" to "explosion",
            "color" to Config.PlayerHitSettings.EXPLOSION_COLOR,
            "max_radius" to Config.PlayerHitSettings.EXPLOSION_MAX_RADIUS
        )
        controller.view.playExplosionSound()
    }

    // Added: Helper function to handle player-alien crash: deduct player life, explode both
    private fun handlePlayerAlienCrash(playerEid: Int, alienEid: Int) {
        val p = controller.world["player"]?.get(playerEid) as? Player ?: return
        p.lives -= 1
        controller.view.playHitSound()
        createPlayerHitExplosion(playerEid)
        if (p.lives > 0) {
            p.state = "respawning"
            p.respawnTime = System.currentTimeMillis()
        } else {
            p.state = "dead"
        }

        // Explode the alien (similar to bullet hit, but no score or streak since it's a crash)
        val now = System.currentTimeMillis()
        controller.world.getOrPut("explosion") { mutableMapOf() }[alienEid] = Explosion(now)
        controller.world.getOrPut("lifespan") { mutableMapOf() }[alienEid] = Lifespan(now, Config.ExplosionSettings.DURATION)
        controller.world["alien_movement"]?.remove(alienEid)
        controller.world["shooter"]?.remove(alienEid)
        controller.world["collider"]?.remove(alienEid)
        controller.world["render"]?.set(
            alienEid, mapOf(
                "type" to "explosion",
                "color" to Config.ColorSettings.YELLOW,
                "max_radius" to 30
            )
        )
        controller.view.playExplosionSound()
    }
}

class LifespanSystem(controller: GameController) : BaseSystem(controller) {
    override fun update(delta: Double) {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<Int>()
        controller.world["lifespan"]?.forEach { (eid, lAny) ->
            val l = lAny as? Lifespan ?: return@forEach
            if (now - l.start > l.duration) {
                toRemove.add(eid)
            }
        }
        toRemove.forEach { controller.removeEntity(it) }
        // Respawn logic
        controller.world["player"]?.forEach { (eid, pAny) ->
            val p = pAny as? Player ?: return@forEach
            if (p.state == "respawning" && now - p.respawnTime > Config.PlayerSettings.RESPAWN_DURATION) {
                p.state = "active"
                val pos = controller.world["position"]?.get(eid) as? Position ?: return@forEach
                pos.x = p.startX
                // Added: Attach RespawnAura component to player after respawn for animation
                controller.world.getOrPut("respawn_aura") { mutableMapOf() }[eid] = RespawnAura(now)
            }
        }

        // Added: Remove RespawnAura after its duration
        controller.world["respawn_aura"]?.forEach { (eid, raAny) ->
            val ra = raAny as? RespawnAura ?: return@forEach
            if (now - ra.start > Config.RespawnAuraSettings.DURATION) {
                controller.world["respawn_aura"]?.remove(eid)
            }
        }
    }
}

class BoundsSystem(controller: GameController) : BaseSystem(controller) {
    override fun update(delta: Double) {
        val toRemove = mutableListOf<Int>()
        controller.world["bullet"]?.keys?.forEach { eid ->
            val pos = controller.world["position"]?.get(eid) as? Position ?: return@forEach
            val size = controller.world["size"]?.get(eid) as? Size ?: return@forEach
            if (pos.y < 0 || pos.y + size.height > Config.ScreenSettings.HEIGHT) {
                toRemove.add(eid)
            }
        }
        toRemove.forEach { controller.removeEntity(it) }
    }
}

class RenderingSystem(controller: GameController) : BaseSystem(controller) {
    override fun update(delta: Double) {}

    fun draw(canvas: Canvas) {
        // FIX: Iterate over copy of keys to avoid ConcurrentModificationException
        val renderKeys = controller.world["render"]?.keys?.toList() ?: emptyList()
        for (eid in renderKeys) {
            if (controller.playerEids.contains(eid) &&
                (controller.world["player"]?.get(eid) as? Player)?.state == "dead"
            ) continue
            val r = controller.world["render"]?.get(eid) as? Map<*, *> ?: continue
            val pos = controller.world["position"]?.get(eid) as? Position ?: continue
            val size = controller.world["size"]?.get(eid) as? Size ?: Size(0, 0)
            when (r["type"]) {
                "rect" -> {
                    val paint = Paint().apply { color = r["color"] as? Int ?: Config.ColorSettings.WHITE }
                    canvas.drawRect(controller.getRect(eid), paint)
                }
                "sprite" -> {
                    val image = r["image"] as? Bitmap ?: continue
                    if (image.isRecycled) {
                        Log.e("MathGalaga", "Recycled bitmap in sprite render, skipping")
                        continue
                    }
                    canvas.drawBitmap(image, pos.x, pos.y, null)
                    val text = r["text"] as? String
                    if (!text.isNullOrEmpty()) {
                        val paint = Config.FontSettings.MAIN
                        val bounds = Rect()
                        paint.getTextBounds(text, 0, text.length, bounds)
                        val centerX = pos.x + size.width / 2 - bounds.width() / 2
                        val centerY = pos.y + size.height / 2 + bounds.height() / 2
                        canvas.drawText(text, centerX, centerY, paint)
                    }
                }
                "explosion" -> {
                    val exp = controller.world["explosion"]?.get(eid) as? Explosion ?: continue
                    val elapsed = System.currentTimeMillis() - exp.start
                    val radius = ((elapsed.toFloat() / Config.ExplosionSettings.DURATION) * (r["max_radius"] as? Int
                        ?: 30)).toFloat()  // Changed: Use toFloat() for drawCircle
                    val centerX = pos.x + size.width / 2
                    val centerY = pos.y + size.height / 2
                    val paint = Paint().apply { color = r["color"] as? Int ?: Config.ColorSettings.YELLOW }
                    canvas.drawCircle(centerX, centerY, radius, paint)
                }
                "combo_text" -> {
                    val lifespan = controller.world["lifespan"]?.get(eid) as? Lifespan ?: continue
                    val elapsed = System.currentTimeMillis() - lifespan.start
                    val alpha = (255 * (1 - elapsed.toFloat() / lifespan.duration)).toInt().coerceIn(0, 255)
                    val paint = (r["font"] as? Paint ?: Config.FontSettings.COMBO)
                    paint.alpha = alpha

                    // Floating up effect
                    val floatUp = controller.world["float_up"]?.get(eid) as? FloatUp ?: continue
                    val ypos = pos.y - (elapsed.toFloat() / lifespan.duration) * floatUp.speed.toFloat()

                    // Draw the combo text
                    canvas.drawText(r["text"] as? String ?: "", pos.x, ypos, paint)
                }
            }

            // Added: If entity is a player with RespawnAura, draw the aura animation around it
            if (controller.playerEids.contains(eid) && controller.world["respawn_aura"]?.containsKey(eid) == true) {
                val ra = controller.world["respawn_aura"]?.get(eid) as? RespawnAura ?: continue
                val elapsed = System.currentTimeMillis() - ra.start
                // Calculate current color: flash between colors
                val colorIndex = ((elapsed / Config.RespawnAuraSettings.FLASH_INTERVAL) % Config.RespawnAuraSettings.COLORS.size).toInt()
                val auraColor = Config.RespawnAuraSettings.COLORS[colorIndex]
                val auraRadius = (size.width.coerceAtLeast(size.height) / 2f) * Config.RespawnAuraSettings.RADIUS_FACTOR
                val centerX = pos.x + size.width / 2
                val centerY = pos.y + size.height / 2
                val paint = Paint().apply {
                    color = auraColor
                    style = Paint.Style.STROKE
                    strokeWidth = 4f  // Thick stroke for visible aura
                }
                canvas.drawCircle(centerX, centerY, auraRadius, paint)
            }
        }
    }
}