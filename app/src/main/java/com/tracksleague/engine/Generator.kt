package com.tracksleague.engine

import kotlin.math.max
import kotlin.math.roundToInt

const val MIN_SIZE = 5
const val MAX_SIZE = 10

private data class LevelConfig(val floor: Double, val logic: Boolean)

private val LEVELS = mapOf(
    Difficulty.EASY to LevelConfig(floor = 0.2, logic = true),
    Difficulty.MEDIUM to LevelConfig(floor = 0.1, logic = true),
    Difficulty.HARD to LevelConfig(floor = 0.04, logic = false)
)

private const val SEARCH_NODES = 4000

private data class RoutePath(
    val startRow: Int,
    val endCol: Int,
    val cells: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RoutePath) return false
        return startRow == other.startRow && endCol == other.endCol && cells.contentEquals(other.cells)
    }

    override fun hashCode(): Int {
        var result = startRow
        result = 31 * result + endCol
        result = 31 * result + cells.contentHashCode()
        return result
    }
}

private fun randomRoute(w: Int, h: Int, rng: Rng): RoutePath? {
    val startRow = rng.int(h)
    val endCol = rng.int(w)
    val start = startRow * w
    val end = (h - 1) * w + endCol
    if (start == end) return null

    val minLen = max(w + h - 1, (w * h * 0.4).roundToInt())
    val maxLen = (w * h * 0.75).roundToInt()

    val visited = ByteArray(w * h)
    val path = ArrayList<Int>(w * h)
    var budget = 4000

    fun dfs(cell: Int, lastDir: Int): Boolean {
        if (budget-- <= 0) return false
        visited[cell] = 1
        path.add(cell)
        if (cell == end) {
            if (path.size >= minLen) return true
            visited[cell] = 0
            path.removeAt(path.size - 1)
            return false
        }
        if (path.size < maxLen) {
            val r = cell / w
            val c = cell % w
            val dirs = rng.shuffle(mutableListOf(0, 1, 2, 3))
            if (lastDir >= 0 && rng.next() < 0.35) {
                dirs.remove(lastDir)
                dirs.add(0, lastDir)
            }
            for (d in dirs) {
                val rr = r + DR[d]
                val cc = c + DC[d]
                if (rr < 0 || rr >= h || cc < 0 || cc >= w) continue
                val j = rr * w + cc
                if (visited[j].toInt() != 0) continue
                if (dfs(j, d)) return true
                if (budget <= 0) break
            }
        }
        visited[cell] = 0
        path.removeAt(path.size - 1)
        return false
    }

    if (!dfs(start, -1)) return null
    return RoutePath(startRow, endCol, path.toIntArray())
}

private fun routeToMasks(route: RoutePath, w: Int, h: Int): IntArray {
    val masks = IntArray(w * h)
    val cells = route.cells
    masks[cells[0]] = masks[cells[0]] or (1 shl 3) // entrance on left
    masks[cells[cells.size - 1]] = masks[cells[cells.size - 1]] or (1 shl 2) // exit on bottom
    for (k in 0 until cells.size - 1) {
        val a = cells[k]
        val b = cells[k + 1]
        val dr = (b / w) - (a / w)
        val dc = (b % w) - (a % w)
        val d = when {
            dr == -1 -> 0
            dc == 1 -> 1
            dr == 1 -> 2
            else -> 3
        }
        masks[a] = masks[a] or (1 shl d)
        masks[b] = masks[b] or (1 shl ((d + 2) and 3))
    }
    return masks
}

private fun buildSpec(
    w: Int,
    h: Int,
    route: RoutePath,
    masks: IntArray,
    clues: List<Clue>
): PuzzleSpec {
    val rowCounts = IntArray(h)
    val colCounts = IntArray(w)
    for (i in masks.indices) {
        if (masks[i] != 0) {
            rowCounts[i / w]++
            colCounts[i % w]++
        }
    }
    return PuzzleSpec(
        width = w,
        height = h,
        startRow = route.startRow,
        endCol = route.endCol,
        rowCounts = rowCounts,
        colCounts = colCounts,
        clues = clues
    )
}

fun generatePuzzle(
    width: Int,
    height: Int,
    seed: Int,
    level: Difficulty = Difficulty.MEDIUM
): Puzzle {
    val config = LEVELS[level] ?: LevelConfig(0.1, true)
    val w = width
    val h = height
    val rng = Mulberry32(seed)

    for (attempt in 0 until 200) {
        val route = randomRoute(w, h, rng) ?: continue
        val truth = routeToMasks(route, w, h)

        val floor = max(if (level == Difficulty.HARD) 1 else 2, (w * h * config.floor).roundToInt())
        val clues = mutableListOf<Clue>()
        val clued = mutableSetOf<Int>()

        val trackIndices = mutableListOf<Int>()
        for (i in truth.indices) {
            if (truth[i] != 0) trackIndices.add(i)
        }
        val trackCells = rng.shuffle(trackIndices)
        for (i in trackCells.take(floor)) {
            clued.add(i)
            clues.add(Clue(r = i / w, c = i % w, mask = truth[i]))
        }

        var guard = 0
        var failed = false
        while (true) {
            if (guard++ > w * h) {
                failed = true
                break
            }
            val spec = buildSpec(w, h, route, truth, clues)
            if (config.logic) {
                val d = deduce(spec)
                if (d.solved) break
                val open = d.undecided.filter { truth[it] != 0 && it !in clued }
                val pool = if (open.isNotEmpty()) open else truth.indices.filter { truth[it] != 0 && it !in clued }
                if (pool.isEmpty()) {
                    failed = true
                    break
                }
                val pick = pool[rng.int(pool.size)]
                clued.add(pick)
                clues.add(Clue(r = pick / w, c = pick % w, mask = truth[pick]))
                continue
            }

            val res = solve(spec, limit = 2, nodeLimit = SEARCH_NODES)
            if (res.complete && res.solutions.size == 1) break

            val other = res.solutions.firstOrNull { !it.contentEquals(truth) }
            val candidates = mutableListOf<Int>()
            if (other != null) {
                for (i in truth.indices) {
                    if (truth[i] != 0 && truth[i] != other[i] && i !in clued) candidates.add(i)
                }
            }
            if (candidates.isEmpty()) {
                for (i in truth.indices) {
                    if (truth[i] != 0 && i !in clued) candidates.add(i)
                }
            }
            if (candidates.isEmpty()) {
                failed = true
                break
            }
            val pick = candidates[rng.int(candidates.size)]
            clued.add(pick)
            clues.add(Clue(r = pick / w, c = pick % w, mask = truth[pick]))
        }
        if (failed) continue

        // Drop redundant clues in random order
        val cluesShuffled = rng.shuffle(clues.toMutableList())
        for (cl in cluesShuffled) {
            if (clues.size <= floor) break
            val without = clues.filter { it != cl }
            val trial = buildSpec(w, h, route, truth, without)
            val redundant = if (config.logic) deduce(trial).solved else isUnique(trial, SEARCH_NODES)
            if (redundant) {
                clues.clear()
                clues.addAll(without)
            }
        }

        val spec = buildSpec(w, h, route, truth, clues)
        return Puzzle(
            width = spec.width,
            height = spec.height,
            startRow = spec.startRow,
            endCol = spec.endCol,
            rowCounts = spec.rowCounts,
            colCounts = spec.colCounts,
            clues = spec.clues,
            seed = seed,
            solution = truth
        )
    }

    // Fallback: simpler puzzle if attempt limit reached
    return generatePuzzle(width, height, seed + 1, Difficulty.EASY)
}

fun isValidSolution(p: PuzzleSpec, masks: IntArray): Boolean {
    val w = p.width
    val h = p.height
    if (masks.size != w * h) return false
    val start = p.startRow * w
    val end = (h - 1) * w + p.endCol

    for (r in 0 until h) {
        var t = 0
        for (c in 0 until w) if (masks[r * w + c] != 0) t++
        if (t != p.rowCounts[r]) return false
    }
    for (c in 0 until w) {
        var t = 0
        for (r in 0 until h) if (masks[r * w + c] != 0) t++
        if (t != p.colCounts[c]) return false
    }
    for (cl in p.clues) {
        if (masks[cl.r * w + cl.c] != cl.mask) return false
    }

    val total = masks.count { it != 0 }
    if (total == 0) return false

    var cell = start
    var from = 3 // arrived from left edge
    var steps = 0
    val seen = HashSet<Int>()

    while (true) {
        if (cell in seen) return false
        seen.add(cell)
        steps++

        val m = masks[cell]
        if (m == 0 || (m and (1 shl from)) == 0) return false
        val rest = m and (1 shl from).inv()
        if (rest == 0 || (rest and (rest - 1)) != 0) return false // exactly one other exit direction
        val d = java.lang.Integer.numberOfTrailingZeros(rest)
        if (cell == end && d == 2) break

        val r = (cell / w) + DR[d]
        val c = (cell % w) + DC[d]
        if (r < 0 || r >= h || c < 0 || c >= w) return false
        cell = r * w + c
        from = (d + 2) and 3
    }

    return steps == total
}
