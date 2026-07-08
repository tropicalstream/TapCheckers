package com.tapcheckers.engine

import kotlin.math.abs
import kotlin.random.Random

/** Difficulty tiers. Depth sets strength; `blunder` lets easy tiers slip. */
enum class Difficulty(
    val label: String, val depth: Int, val blunderChance: Float,
    val poolMargin: Int, val budgetMs: Long,
) {
    NOVICE("1 · Novice", 3, 0.55f, 240, 800),
    AMATEUR("2 · Amateur", 5, 0.28f, 130, 1200),
    SKILLED("3 · Skilled", 7, 0.10f, 70, 1800),
    EXPERT("4 · Expert", 9, 0.0f, 0, 2400),
    MASTER("5 · Master", 11, 0.0f, 0, 2900);

    companion object {
        fun from(i: Int) = entries[i.coerceIn(0, entries.size - 1)]
    }
}

class AI {
    @Volatile private var deadline = 0L
    @Volatile private var aborted = false
    private val rng = Random(System.nanoTime())

    fun bestMove(board: Board, diff: Difficulty): Move? {
        val legal = board.generateMoves()
        if (legal.isEmpty()) return null
        if (legal.size == 1) return legal[0]
        deadline = System.currentTimeMillis() + diff.budgetMs
        aborted = false

        var scored = legal.map { it to 0 }
        for (d in 1..diff.depth) {
            val ordered = scored.sortedByDescending { it.second }.map { it.first }
            val res = ArrayList<Pair<Move, Int>>(ordered.size)
            for (m in ordered) {
                if (System.currentTimeMillis() > deadline) { aborted = true; break }
                res.add(m to -search(board.applied(m), d - 1, -INF, INF, 1))
            }
            if (!aborted && res.isNotEmpty()) scored = res
            if (aborted) break
            if (scored.any { abs(it.second) > MATE - 100 }) break
        }

        val best = scored.maxByOrNull { it.second } ?: return legal.random(rng)
        if (diff.blunderChance > 0f && rng.nextFloat() < diff.blunderChance) {
            val pool = scored.filter { best.second - it.second <= diff.poolMargin }
            return (if (pool.isNotEmpty()) pool else scored).random(rng).first
        }
        return scored.filter { it.second == best.second }.random(rng).first
    }

    private fun search(board: Board, depth: Int, a0: Int, beta: Int, ply: Int): Int {
        if (aborted || System.currentTimeMillis() > deadline) { aborted = true; return 0 }
        val moves = board.generateMoves()
        if (moves.isEmpty()) return -(MATE - ply) // no move = loss
        if (depth == 0) return evalNega(board)
        order(moves)
        var alpha = a0
        var best = -INF
        for (m in moves) {
            val s = -search(board.applied(m), depth - 1, -beta, -alpha, ply + 1)
            if (s > best) best = s
            if (best > alpha) alpha = best
            if (alpha >= beta) break
        }
        return best
    }

    private fun order(moves: List<Move>) {
        (moves as? ArrayList<Move>)?.sortByDescending { it.captures.size * 10 + if (it.promotes) 5 else 0 }
    }

    /** Static eval from the side-to-move's perspective (negamax). */
    private fun evalNega(board: Board): Int {
        var white = 0
        var black = 0
        val s = board.sq
        for (i in 0 until 64) {
            val p = s[i]
            if (p == 0) continue
            val f = file(i); val r = rank(i)
            val king = abs(p) == 2
            val centre = if (f in 2..5) CENTER else 0
            if (p > 0) {
                white += if (king) KING + centre else MAN + ADV * r + centre + (if (r == 0) BACK else 0)
            } else {
                black += if (king) KING + centre else MAN + ADV * (7 - r) + centre + (if (r == 7) BACK else 0)
            }
        }
        return (white - black) * board.side
    }

    companion object {
        private const val INF = 1_000_000
        private const val MATE = 30_000
        private const val MAN = 100
        private const val KING = 175
        private const val ADV = 5
        private const val BACK = 6
        private const val CENTER = 3
    }
}
