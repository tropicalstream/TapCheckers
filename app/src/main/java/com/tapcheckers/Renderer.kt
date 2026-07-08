package com.tapcheckers

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.tapcheckers.engine.Difficulty
import com.tapcheckers.engine.GameEngine
import com.tapcheckers.engine.GameState
import com.tapcheckers.engine.file
import com.tapcheckers.engine.rank
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** All drawing in 640x480 logical space on pure black (waveguide = black transparent). */
class Renderer(private val engine: GameEngine, private val store: SettingsStore) {

    private val W = 640f
    private val H = 480f
    private val BX = GameEngine.BOARD_X
    private val BY = GameEngine.BOARD_Y
    private val SQ = GameEngine.SQ

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val rf = RectF()
    private val crownPath = Path()

    private val playableSq = 0xFF1B2740.toInt()
    private val voidSq = 0xFF33456A.toInt()
    private val whiteFill = 0xFFE8ECF4.toInt()
    private val whiteRim = 0xFF9AA8C0.toInt()
    private val blackFill = 0xFF2A3247.toInt()
    private val blackRim = 0xFF6E7C96.toInt()
    private val gold = 0xFFFFD24A.toInt()

    fun draw(c: Canvas, w: Int, h: Int) {
        c.drawColor(Color.BLACK)
        if (w <= 0 || h <= 0) return
        val s = min(w / W, h / H)
        c.save()
        c.translate((w - W * s) / 2f, (h - H * s) / 2f)
        c.scale(s, s)

        if (engine.state == GameState.MENU) drawMenu(c)
        else {
            drawBoard(c)
            drawPieces(c)
            drawOppTrail(c)
            drawHud(c)
            drawParticles(c)
            if (engine.state == GameState.OVER) drawOver(c)
            drawInvalid(c)
        }
        if (engine.settingsOpen) drawSettings(c)
        c.restore()
    }

    // ------------------------------------------------------------- board

    private fun drawBoard(c: Canvas) {
        rf.set(BX - 8f, BY - 8f, BX + 8 * SQ + 8f, BY + 8 * SQ + 8f)
        stroke.strokeWidth = 6f; stroke.color = Color.argb(60, 100, 150, 220)
        c.drawRoundRect(rf, 8f, 8f, stroke)
        stroke.strokeWidth = 2f; stroke.color = Color.argb(200, 140, 190, 245)
        c.drawRoundRect(rf, 8f, 8f, stroke)

        for (sqi in 0 until 64) {
            val col = engine.col(sqi); val row = engine.row(sqi)
            val x = BX + col * SQ; val y = BY + row * SQ
            val playable = (file(sqi) + rank(sqi)) % 2 == 0
            fill.shader = null
            fill.color = if (playable) playableSq else voidSq
            c.drawRect(x, y, x + SQ, y + SQ, fill)
        }
        // Crown rows: where each side is crowned (faint gold hint).
        tintRow(c, 7)
        tintRow(c, 0)

        if (engine.lastFrom >= 0) {
            highlightSquare(c, engine.lastFrom, Color.argb(70, 255, 220, 90))
            highlightSquare(c, engine.lastTo, Color.argb(90, 255, 220, 90))
        }
        if (engine.selected >= 0) {
            highlightSquare(c, engine.selected, Color.argb(110, 90, 230, 120))
            if (store.showLegal) {
                for (t in engine.targets) {
                    val cx = engine.sqCenterX(t); val cy = engine.sqCenterY(t)
                    fill.shader = null; fill.color = Color.argb(150, 90, 230, 120)
                    c.drawCircle(cx, cy, 6f, fill)
                }
            }
        }
        if (store.showCoords) {
            for (i in 0 until 8) {
                val fileChar = ('a' + if (!engine.flip) i else 7 - i)
                text(c, fileChar.toString(), BX + i * SQ + SQ - 7f, BY + 8 * SQ + 11f, 9f, Color.argb(150, 150, 180, 220))
                val rankChar = (if (!engine.flip) 8 - i else i + 1).toString()
                text(c, rankChar, BX - 9f, BY + i * SQ + 13f, 9f, Color.argb(150, 150, 180, 220))
            }
        }
        // Cursor + dwell ring.
        val cx = BX + engine.col(engine.cursor) * SQ
        val cy = BY + engine.row(engine.cursor) * SQ
        val pulse = (180 + 75 * sin(engine.time * 5f)).toInt().coerceIn(80, 255)
        rf.set(cx + 2f, cy + 2f, cx + SQ - 2f, cy + SQ - 2f)
        stroke.strokeWidth = 3f; stroke.color = Color.argb(pulse, 255, 255, 255)
        c.drawRoundRect(rf, 6f, 6f, stroke)
        if (engine.selected >= 0 && engine.cursor in engine.targets) {
            val ccx = cx + SQ / 2; val ccy = cy + SQ / 2
            rf.set(ccx - SQ * 0.46f, ccy - SQ * 0.46f, ccx + SQ * 0.46f, ccy + SQ * 0.46f)
            stroke.strokeWidth = 4f
            if (engine.moveArmed) {
                val pz = (200 + 55 * sin(engine.time * 6f)).toInt().coerceIn(120, 255)
                stroke.color = Color.argb(pz, 120, 240, 150)
                c.drawArc(rf, -90f, 360f, false, stroke)
            } else {
                stroke.color = Color.argb(90, 255, 220, 140)
                c.drawArc(rf, -90f, 360f, false, stroke)
                stroke.color = Color.argb(230, 255, 210, 120)
                c.drawArc(rf, -90f, 360f * engine.dwellProgress, false, stroke)
            }
        }
    }

    private fun tintRow(c: Canvas, boardRank: Int) {
        fill.shader = null
        fill.color = Color.argb(22, 255, 210, 74)
        for (f in 0 until 8) {
            val sqi = boardRank * 8 + f
            if ((f + boardRank) % 2 != 0) continue
            val x = BX + engine.col(sqi) * SQ; val y = BY + engine.row(sqi) * SQ
            c.drawRect(x, y, x + SQ, y + SQ, fill)
        }
    }

    private fun highlightSquare(c: Canvas, sqi: Int, color: Int) {
        val x = BX + engine.col(sqi) * SQ; val y = BY + engine.row(sqi) * SQ
        fill.shader = null; fill.color = color
        c.drawRect(x, y, x + SQ, y + SQ, fill)
    }

    private fun drawPieces(c: Canvas) {
        for (sqi in 0 until 64) {
            val p = engine.board.sq[sqi]
            if (p == 0) continue
            drawPiece(c, engine.sqCenterX(sqi), engine.sqCenterY(sqi), SQ * 0.82f, p > 0, abs(p) == 2)
        }
    }

    /** A checker disc; kings wear a gold crown. */
    private fun drawPiece(c: Canvas, cx: Float, cy: Float, size: Float, white: Boolean, king: Boolean) {
        val r = size * 0.42f
        val body = if (white) whiteFill else blackFill
        val rim = if (white) whiteRim else blackRim
        fill.shader = null
        fill.color = rim; c.drawCircle(cx, cy, r, fill)
        fill.color = body; c.drawCircle(cx, cy, r * 0.86f, fill)
        // Groove ring for a stacked-disc look.
        stroke.strokeWidth = 1.6f; stroke.color = rim
        c.drawCircle(cx, cy, r * 0.62f, stroke)
        fill.color = Color.argb(60, 255, 255, 255)
        c.drawCircle(cx - r * 0.26f, cy - r * 0.28f, r * 0.26f, fill)
        if (king) drawCrown(c, cx, cy, r * 0.78f)
    }

    private fun drawCrown(c: Canvas, cx: Float, cy: Float, s: Float) {
        crownPath.reset()
        crownPath.moveTo(cx - 0.5f * s, cy + 0.28f * s)
        crownPath.lineTo(cx - 0.5f * s, cy - 0.30f * s)
        crownPath.lineTo(cx - 0.22f * s, cy + 0.04f * s)
        crownPath.lineTo(cx, cy - 0.34f * s)
        crownPath.lineTo(cx + 0.22f * s, cy + 0.04f * s)
        crownPath.lineTo(cx + 0.5f * s, cy - 0.30f * s)
        crownPath.lineTo(cx + 0.5f * s, cy + 0.28f * s)
        crownPath.close()
        fill.shader = null; fill.color = gold
        c.drawPath(crownPath, fill)
        stroke.strokeWidth = 1.4f; stroke.color = 0xFF7A5A00.toInt()
        c.drawPath(crownPath, stroke)
    }

    private fun drawOppTrail(c: Canvas) {
        val path = engine.oppPath
        if (path.size < 2) return
        fill.shader = null
        for (seg in 0 until path.size - 1) {
            val x1 = engine.sqCenterX(path[seg]); val y1 = engine.sqCenterY(path[seg])
            val x2 = engine.sqCenterX(path[seg + 1]); val y2 = engine.sqCenterY(path[seg + 1])
            val dx = x2 - x1; val dy = y2 - y1
            val n = (hypot(dx, dy) / 13f).toInt().coerceAtLeast(2)
            for (i in 1 until n) {
                val t = i.toFloat() / n
                val tw = sin(engine.time * 4f - (seg * n + i) * 0.5f) * 0.5f + 0.5f
                fill.color = Color.argb((110 + 120 * tw).toInt().coerceIn(60, 255), 255, 158, 96)
                c.drawCircle(x1 + dx * t, y1 + dy * t, 2.4f + tw * 1.4f, fill)
            }
        }
        val a = path[path.size - 2]; val b = path[path.size - 1]
        val ang = atan2(engine.sqCenterY(b) - engine.sqCenterY(a), engine.sqCenterX(b) - engine.sqCenterX(a))
        val bx = engine.sqCenterX(b); val by = engine.sqCenterY(b); val ah = 9f
        crownPath.reset()
        crownPath.moveTo(bx, by)
        crownPath.lineTo(bx - ah * cos(ang - 0.42f), by - ah * sin(ang - 0.42f))
        crownPath.lineTo(bx - ah * cos(ang + 0.42f), by - ah * sin(ang + 0.42f))
        crownPath.close()
        fill.color = Color.argb(235, 255, 172, 104)
        c.drawPath(crownPath, fill)
    }

    // --------------------------------------------------------------- HUD

    private fun drawHud(c: Canvas) {
        val turnText: String; val turnColor: Int
        when {
            engine.state == GameState.THINKING -> {
                val dots = ".".repeat(1 + ((engine.time * 2).toInt() % 3))
                turnText = "${engine.difficulty.label} thinking$dots"
                turnColor = Color.argb(255, 255, 200, 100)
            }
            engine.jumpChain -> { turnText = "KEEP JUMPING!"; turnColor = Color.argb(255, 255, 210, 90) }
            engine.statusMsg.startsWith("Jump") -> { turnText = "JUMP — YOU MUST TAKE IT"; turnColor = Color.argb(255, 255, 170, 90) }
            else -> { turnText = "Your move"; turnColor = Color.argb(255, 150, 235, 170) }
        }
        if (engine.state != GameState.OVER) text(c, turnText, W / 2f, 34f, 16f, turnColor, glow = Color.argb(90, 60, 120, 200))

        text(c, "CPU", 570f, 82f, 13f, Color.argb(255, 255, 150, 150))
        text(c, engine.difficulty.label.substringAfter("· ").ifEmpty { engine.difficulty.label }, 570f, 100f, 11f, Color.argb(220, 220, 200, 200))
        if (engine.speedOn) drawClock(c, 570f, 126f, if (engine.humanWhite) engine.blackMs else engine.whiteMs, engine.state == GameState.THINKING)
        drawCount(c, 150f, !engine.humanWhite)

        text(c, "YOU", 570f, 330f, 13f, Color.argb(255, 150, 235, 170))
        text(c, if (engine.humanWhite) "White" else "Black", 570f, 348f, 11f, Color.argb(220, 200, 224, 235))
        if (engine.speedOn) drawClock(c, 570f, 374f, if (engine.humanWhite) engine.whiteMs else engine.blackMs, engine.state == GameState.PLAYING)
        drawCount(c, 400f, engine.humanWhite)

        text(c, "W ${store.wins}  L ${store.losses}", 72f, 250f, 11f, Color.argb(200, 160, 185, 220))
    }

    /** Draw remaining-piece tally (men + a crown per king) for a side. */
    private fun drawCount(c: Canvas, y: Float, white: Boolean) {
        var men = 0; var kings = 0
        val side = if (white) 1 else -1
        for (i in 0 until 64) {
            val p = engine.board.sq[i]
            if (p == 0 || (if (p > 0) 1 else -1) != side) continue
            if (abs(p) == 2) kings++ else men++
        }
        drawPiece(c, 524f, y, 20f, white, false)
        text(c, "×$men", 540f, y + 5f, 14f, Color.WHITE, Paint.Align.LEFT)
        drawPiece(c, 578f, y, 20f, white, true)
        text(c, "×$kings", 594f, y + 5f, 14f, Color.WHITE, Paint.Align.LEFT)
    }

    private fun drawClock(c: Canvas, cx: Float, y: Float, ms: Long, active: Boolean) {
        val totalSec = (ms / 1000).toInt(); val mm = totalSec / 60; val ss = totalSec % 60
        val low = ms in 1..10000
        val col = when {
            low -> Color.argb((150 + 100 * sin(engine.time * 8f)).toInt().coerceIn(60, 255), 255, 80, 80)
            active -> Color.argb(255, 255, 240, 180)
            else -> Color.argb(200, 170, 195, 230)
        }
        rf.set(cx - 42f, y - 15f, cx + 42f, y + 9f)
        fill.shader = null
        fill.color = if (active) Color.argb(140, 40, 60, 100) else Color.argb(80, 24, 36, 60)
        c.drawRoundRect(rf, 6f, 6f, fill)
        text(c, "%d:%02d".format(mm, ss), cx, y + 2f, 16f, col)
    }

    // -------------------------------------------------------- overlays

    private fun drawInvalid(c: Canvas) {
        val msg = engine.invalidMsg ?: return
        val a = (engine.invalidT / 2.6f).coerceIn(0f, 1f)
        val alpha = (min(1f, a * 3f) * 255).toInt()
        val hint = engine.invalidIsHint
        rf.set(150f, 436f, 490f, 466f)
        fill.shader = null
        fill.color = if (hint) Color.argb((alpha * 0.85f).toInt(), 54, 44, 16) else Color.argb((alpha * 0.85f).toInt(), 60, 20, 24)
        c.drawRoundRect(rf, 12f, 12f, fill)
        stroke.strokeWidth = 2f
        stroke.color = if (hint) Color.argb((alpha * 0.9f).toInt(), 255, 200, 110) else Color.argb((alpha * 0.9f).toInt(), 255, 110, 110)
        c.drawRoundRect(rf, 12f, 12f, stroke)
        text(c, msg, 320f, 456f, 12.5f, if (hint) Color.argb(alpha, 255, 230, 180) else Color.argb(alpha, 255, 220, 210))
    }

    private fun drawOver(c: Canvas) {
        dim(c, 150)
        panel(c, 130f, 170f, 510f, 320f)
        val win = engine.resultMsg.contains("win", true)
        val col = if (win) Color.argb(255, 150, 235, 170) else Color.argb(255, 255, 120, 120)
        text(c, if (win) "YOU WIN!" else "DEFEAT", 320f, 218f, 30f, col, glow = Color.argb(140, 40, 90, 150))
        text(c, engine.resultMsg, 320f, 254f, 13f, Color.argb(255, 210, 224, 245))
        text(c, "TAP FOR MENU", 320f, 296f, 15f, Color.argb((170 + 85 * sin(engine.time * 4f)).toInt().coerceIn(60, 255), 200, 230, 255))
    }

    private fun drawMenu(c: Canvas) {
        // Motif: a stack of checkers topped by a king.
        drawPiece(c, 300f, 158f, 44f, false, false)
        drawPiece(c, 340f, 158f, 44f, true, false)
        drawPiece(c, 320f, 120f, 48f, true, true)

        textP.setShadowLayer(16f, 0f, 0f, Color.argb(180, 60, 150, 255))
        text(c, "TAPCHECKERS", 320f, 232f, 42f, 0xFFEAF3FF.toInt())
        textP.clearShadowLayer()
        text(c, "jump, chain, and crown your kings", 320f, 258f, 12f, Color.argb(220, 150, 190, 235))

        val d = Difficulty.from(engine.menuDiff)
        text(c, "‹  ${d.label}  ›", 320f, 306f, 22f, Color.WHITE, glow = Color.argb(140, 80, 150, 255))
        text(c, "playing as ${if (store.playerWhite) "White" else "Black"} · speed ${store.speedLabel}", 320f, 332f, 12f, Color.argb(255, 255, 224, 120))
        text(c, "record   W ${store.wins}   L ${store.losses}", 320f, 356f, 12f, Color.argb(220, 156, 255, 176))

        text(c, "swipe ↔ difficulty   •   tap to play", 320f, 400f, 13f, Color.argb((170 + 85 * sin(engine.time * 3f)).toInt().coerceIn(60, 255), 200, 230, 255))
        text(c, "double-tap for settings", 320f, 422f, 11f, Color.argb(160, 150, 175, 210))
    }

    // --------------------------------------------------------- settings

    private fun drawSettings(c: Canvas) {
        dim(c, 188)
        panel(c, 138f, 34f, 502f, 446f)
        text(c, "SETTINGS", 320f, 64f, 20f, Color.WHITE, glow = Color.argb(160, 80, 150, 255))
        val menu = engine.settingsMenu
        val visible = 10
        val start = (menu.selected - visible / 2).coerceIn(0, (menu.items.size - visible).coerceAtLeast(0))
        var y = 96f
        for (i in start until min(start + visible, menu.items.size)) {
            val item = menu.items[i]
            val sel = i == menu.selected
            if (sel) {
                fill.shader = null; fill.color = Color.argb(210, 36, 64, 106)
                rf.set(150f, y - 15f, 490f, y + 8f)
                c.drawRoundRect(rf, 8f, 8f, fill)
            }
            text(c, item.label, 166f, y, 13f, if (sel) Color.WHITE else Color.argb(255, 159, 180, 208), Paint.Align.LEFT)
            val v = item.value()
            if (v.isNotEmpty()) {
                val shown = if (sel && item.adjust != null) "‹ $v ›" else v
                text(c, shown, 474f, y, 13f, if (sel) Color.argb(255, 255, 224, 128) else Color.argb(255, 120, 144, 176), Paint.Align.RIGHT)
            }
            y += 33f
        }
        if (start > 0) text(c, "▲", 320f, 86f, 10f, Color.argb(180, 150, 180, 220))
        if (start + visible < menu.items.size) text(c, "▼", 320f, 430f, 10f, Color.argb(180, 150, 180, 220))
        text(c, "swipe ↕ select   ↔ adjust   tap OK   double-tap close", 320f, 462f, 10.5f, Color.argb(200, 150, 175, 210))
    }

    // ------------------------------------------------------ fx & helpers

    private fun drawParticles(c: Canvas) {
        for (pt in engine.particles.list) {
            val k = (pt.life / pt.maxLife).coerceIn(0f, 1f)
            val alpha = (k * 255).toInt()
            if (pt.ring) {
                stroke.strokeWidth = 1.5f + 3f * k; stroke.color = pt.color; stroke.alpha = alpha
                c.drawCircle(pt.x, pt.y, pt.size * (1f + (1f - k) * 2f), stroke)
            } else {
                fill.shader = null; fill.color = pt.color; fill.alpha = alpha
                c.drawCircle(pt.x, pt.y, pt.size * (0.4f + 0.6f * k), fill)
            }
        }
        stroke.alpha = 255; fill.alpha = 255
    }

    private fun dim(c: Canvas, a: Int) {
        fill.shader = null; fill.color = Color.argb(a, 0, 0, 0)
        c.drawRect(0f, 0f, W, H, fill)
    }

    private fun panel(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        rf.set(l, t, r, b)
        fill.shader = null; fill.color = Color.argb(236, 12, 22, 42)
        c.drawRoundRect(rf, 16f, 16f, fill)
        stroke.strokeWidth = 2f; stroke.color = Color.argb(200, 95, 134, 200)
        c.drawRoundRect(rf, 16f, 16f, stroke)
    }

    private fun text(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
        align: Paint.Align = Paint.Align.CENTER, glow: Int = 0,
    ) {
        textP.textSize = size
        textP.textAlign = align
        textP.color = color
        if (glow != 0) textP.setShadowLayer(size * 0.4f, 0f, 0f, glow) else textP.clearShadowLayer()
        c.drawText(s, x, y, textP)
        textP.clearShadowLayer()
    }
}
