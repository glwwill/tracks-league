package com.tracksleague.engine

data class Snapshot(
    val masks: IntArray,
    val marks: BooleanArray
) {
    fun copy(): Snapshot = Snapshot(masks.clone(), marks.clone())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Snapshot) return false
        return masks.contentEquals(other.masks) && marks.contentEquals(other.marks)
    }

    override fun hashCode(): Int {
        var result = masks.contentHashCode()
        result = 31 * result + marks.contentHashCode()
        return result
    }
}

enum class EdgeMode {
    ADD,
    REMOVE,
    TOGGLE
}

fun popcount(m: Int): Int =
    (m and 1) + ((m shr 1) and 1) + ((m shr 2) and 1) + ((m shr 3) and 1)

fun neighbour(p: PuzzleSpec, i: Int, d: Int): Int {
    val r = (i / p.width) + DR[d]
    val c = (i % p.width) + DC[d]
    if (r < 0 || r >= p.height || c < 0 || c >= p.width) return -1
    return r * p.width + c
}

fun clueSet(p: PuzzleSpec): Set<Int> =
    p.clues.map { it.r * p.width + it.c }.toSet()

fun initialSnapshot(p: PuzzleSpec): Snapshot {
    val masks = IntArray(p.width * p.height)
    for (cl in p.clues) {
        val i = cl.r * p.width + cl.c
        masks[i] = cl.mask
        for (d in 0 until 4) {
            if (((cl.mask shr d) and 1) == 1) {
                val j = neighbour(p, i, d)
                if (j >= 0) {
                    masks[j] = masks[j] or (1 shl ((d + 2) and 3))
                }
            }
        }
    }
    masks[p.startRow * p.width] = masks[p.startRow * p.width] or (1 shl 3)
    masks[(p.height - 1) * p.width + p.endCol] = masks[(p.height - 1) * p.width + p.endCol] or (1 shl 2)
    return Snapshot(masks, BooleanArray(p.width * p.height))
}

fun changeEdge(
    p: PuzzleSpec,
    locked: Set<Int>,
    masks: IntArray,
    i: Int,
    d: Int,
    mode: EdgeMode
): IntArray? {
    val j = neighbour(p, i, d)
    if (j < 0 || i in locked || j in locked) return null
    val has = ((masks[i] shr d) and 1) == 1
    val action = when (mode) {
        EdgeMode.TOGGLE -> if (has) EdgeMode.REMOVE else EdgeMode.ADD
        else -> mode
    }
    val next = masks.clone()
    if (action == EdgeMode.ADD) {
        if (has || popcount(masks[i]) >= 2 || popcount(masks[j]) >= 2) return null
        next[i] = next[i] or (1 shl d)
        next[j] = next[j] or (1 shl ((d + 2) and 3))
    } else {
        if (!has) return null
        next[i] = next[i] and (1 shl d).inv()
        next[j] = next[j] and (1 shl ((d + 2) and 3)).inv()
    }
    return next
}

fun hasEdge(masks: IntArray, i: Int, d: Int): Boolean =
    ((masks[i] shr d) and 1) == 1
