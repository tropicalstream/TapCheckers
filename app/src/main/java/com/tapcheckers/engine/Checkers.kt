package com.tapcheckers.engine

import kotlin.math.abs

/**
 * Checkers / English draughts on an 8x8 board (American rules):
 *  - men move one square diagonally forward; kings move one diagonally any way,
 *  - capture by jumping an adjacent enemy into the empty square beyond,
 *  - captures are FORCED, and a jump must continue with the same piece until it
 *    can't jump again (maximal multi-jump),
 *  - a man reaching the far row becomes a king (and that ends the turn),
 *  - you lose when you have no legal move (all captured or fully blocked).
 */
object P {
    const val EMPTY = 0
    const val WM = 1    // white man
    const val WK = 2    // white king
    const val BM = -1   // black man
    const val BK = -2   // black king
    const val WHITE = 1
    const val BLACK = -1
}

/** A complete turn (one slide, or a maximal jump chain). `path` includes `from`. */
data class Move(
    val from: Int, val to: Int,
    val captures: List<Int>, val promotes: Boolean, val path: List<Int>,
)

/** A single hop used by the interactive (human) path. cap = -1 for a slide. */
data class Step(val from: Int, val to: Int, val cap: Int)

fun file(sq: Int) = sq and 7
fun rank(sq: Int) = sq shr 3
fun sqOf(f: Int, r: Int) = r * 8 + f
fun onBoard(f: Int, r: Int) = f in 0..7 && r in 0..7
fun sideOf(p: Int) = if (p > 0) P.WHITE else P.BLACK

private val WM_DIRS = arrayOf(intArrayOf(1, 1), intArrayOf(-1, 1))
private val BM_DIRS = arrayOf(intArrayOf(1, -1), intArrayOf(-1, -1))
private val K_DIRS = arrayOf(intArrayOf(1, 1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(-1, -1))

class Board {
    val sq = IntArray(64)
    var side = P.WHITE

    fun clone(): Board {
        val b = Board()
        System.arraycopy(sq, 0, b.sq, 0, 64)
        b.side = side
        return b
    }

    fun whiteToMove() = side == P.WHITE
    fun playable(s: Int) = (file(s) + rank(s)) % 2 == 0
    fun onKingRow(s: Int, white: Boolean) = if (white) rank(s) == 7 else rank(s) == 0
    private fun dirsFor(p: Int) = when {
        abs(p) == 2 -> K_DIRS
        p > 0 -> WM_DIRS
        else -> BM_DIRS
    }

    fun count(white: Boolean): Int {
        var n = 0
        for (i in 0 until 64) if (sq[i] != 0 && sideOf(sq[i]) == (if (white) P.WHITE else P.BLACK)) n++
        return n
    }

    // ------------------------------------------------- single-step (human)

    fun slideStepsFrom(s: Int): List<Step> {
        val p = sq[s]
        if (p == 0 || sideOf(p) != side) return emptyList()
        val out = ArrayList<Step>(4)
        for (d in dirsFor(p)) {
            val f = file(s) + d[0]; val r = rank(s) + d[1]
            if (onBoard(f, r) && sq[sqOf(f, r)] == 0) out.add(Step(s, sqOf(f, r), -1))
        }
        return out
    }

    fun jumpStepsFrom(s: Int): List<Step> {
        val p = sq[s]
        if (p == 0 || sideOf(p) != side) return emptyList()
        val out = ArrayList<Step>(4)
        for (d in dirsFor(p)) {
            val f1 = file(s) + d[0]; val r1 = rank(s) + d[1]
            val f2 = file(s) + 2 * d[0]; val r2 = rank(s) + 2 * d[1]
            if (!onBoard(f2, r2)) continue
            val mid = sqOf(f1, r1); val land = sqOf(f2, r2)
            if (sq[land] != 0) continue
            val m = sq[mid]
            if (m != 0 && sideOf(m) != sideOf(p)) out.add(Step(s, land, mid))
        }
        return out
    }

    fun sideHasJump(): Boolean {
        for (i in 0 until 64) if (sq[i] != 0 && sideOf(sq[i]) == side && jumpStepsFrom(i).isNotEmpty()) return true
        return false
    }

    /** Forced-capture aware: jumps if any exist for the side, else slides. */
    fun legalStepsFrom(s: Int): List<Step> =
        if (sideHasJump()) jumpStepsFrom(s) else slideStepsFrom(s)

    /** Apply one hop; does NOT flip side (the caller decides when the turn ends). */
    fun stepApplied(from: Int, to: Int, cap: Int, promote: Boolean): Board {
        val b = clone()
        val piece = b.sq[from]
        b.sq[from] = 0
        if (cap >= 0) b.sq[cap] = 0
        b.sq[to] = if (promote) (if (piece > 0) P.WK else P.BK) else piece
        return b
    }

    // ------------------------------------------------- full turns (AI/rules)

    fun generateMoves(): List<Move> {
        val jumps = ArrayList<Move>()
        for (i in 0 until 64) {
            val p = sq[i]
            if (p == 0 || sideOf(p) != side) continue
            collectJumps(i, this, i, listOf(i), emptyList(), p, jumps)
        }
        if (jumps.isNotEmpty()) return jumps
        val slides = ArrayList<Move>()
        for (i in 0 until 64) {
            val p = sq[i]
            if (p == 0 || sideOf(p) != side) continue
            for (st in slideStepsFrom(i)) {
                val promo = abs(p) == 1 && onKingRow(st.to, p > 0)
                slides.add(Move(i, st.to, emptyList(), promo, listOf(i, st.to)))
            }
        }
        return slides
    }

    private fun collectJumps(
        cur: Int, work: Board, start: Int,
        path: List<Int>, caps: List<Int>, piece: Int, out: MutableList<Move>,
    ) {
        for (d in dirsFor(piece)) {
            val f1 = file(cur) + d[0]; val r1 = rank(cur) + d[1]
            val f2 = file(cur) + 2 * d[0]; val r2 = rank(cur) + 2 * d[1]
            if (!onBoard(f2, r2)) continue
            val mid = sqOf(f1, r1); val land = sqOf(f2, r2)
            if (work.sq[land] != 0) continue
            val m = work.sq[mid]
            if (m == 0 || sideOf(m) == sideOf(piece)) continue
            val nb = work.clone()
            nb.sq[land] = nb.sq[cur]; nb.sq[cur] = 0; nb.sq[mid] = 0
            val promo = abs(piece) == 1 && onKingRow(land, piece > 0)
            val np = path + land; val nc = caps + mid
            if (promo) {
                nb.sq[land] = if (piece > 0) P.WK else P.BK
                out.add(Move(start, land, nc, true, np)) // promotion ends the turn
            } else {
                val before = out.size
                collectJumps(land, nb, start, np, nc, piece, out)
                if (out.size == before) out.add(Move(start, land, nc, false, np)) // maximal
            }
        }
    }

    fun applied(m: Move): Board {
        val b = clone()
        val piece = b.sq[m.from]
        b.sq[m.from] = 0
        for (c in m.captures) b.sq[c] = 0
        b.sq[m.to] = if (m.promotes) (if (piece > 0) P.WK else P.BK) else piece
        b.side = -b.side
        return b
    }

    fun hasAnyMove(): Boolean = generateMoves().isNotEmpty()

    /** Most-advanced piece for a side (kings count as fully advanced); -1 if none. */
    fun focusSquare(white: Boolean): Int {
        val target = if (white) P.WHITE else P.BLACK
        var best = -1; var bestAdv = -2
        for (i in 0 until 64) {
            val p = sq[i]
            if (p == 0 || sideOf(p) != target) continue
            val adv = if (abs(p) == 2) 8 else if (white) rank(i) else 7 - rank(i)
            if (adv > bestAdv) { bestAdv = adv; best = i }
        }
        return best
    }

    // ------------------------------------------------------ explain a move

    fun explain(from: Int, to: Int): String {
        val p = sq[from]
        if (p == 0) return "There's no piece there."
        if (sideOf(p) != side) return "That's not your piece to move."
        if (!playable(to)) return "Play only on the dark squares."
        if (sq[to] != 0) return "That square is already taken."
        if (sideHasJump()) return "A jump is available — you must take it."
        val df = file(to) - file(from); val dr = rank(to) - rank(from)
        if (abs(df) != abs(dr)) return "Pieces move diagonally."
        if (abs(df) > 1) return "Move one square — jumps happen over a piece onto an empty square."
        if (abs(p) == 1) {
            val fwd = if (p > 0) 1 else -1
            if (dr != fwd) return "A man moves only forward — reach the far row to be crowned."
        }
        return "That move isn't allowed."
    }

    companion object {
        fun initial(): Board {
            val b = Board()
            for (r in 0..2) for (f in 0..7) if ((f + r) % 2 == 0) b.sq[sqOf(f, r)] = P.WM
            for (r in 5..7) for (f in 0..7) if ((f + r) % 2 == 0) b.sq[sqOf(f, r)] = P.BM
            return b
        }
    }
}
