package com.tracksleague.engine

const val UP = 0
const val RIGHT = 1
const val DOWN = 2
const val LEFT = 3

val DR = intArrayOf(-1, 0, 1, 0)
val DC = intArrayOf(0, 1, 0, -1)

data class Clue(
    val r: Int,
    val c: Int,
    val mask: Int
)

data class PuzzleSpec(
    val width: Int,
    val height: Int,
    val startRow: Int,
    val endCol: Int,
    val rowCounts: IntArray,
    val colCounts: IntArray,
    val clues: List<Clue>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PuzzleSpec) return false
        return width == other.width &&
                height == other.height &&
                startRow == other.startRow &&
                endCol == other.endCol &&
                rowCounts.contentEquals(other.rowCounts) &&
                colCounts.contentEquals(other.colCounts) &&
                clues == other.clues
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + startRow
        result = 31 * result + endCol
        result = 31 * result + rowCounts.contentHashCode()
        result = 31 * result + colCounts.contentHashCode()
        result = 31 * result + clues.hashCode()
        return result
    }
}

data class Puzzle(
    val width: Int,
    val height: Int,
    val startRow: Int,
    val endCol: Int,
    val rowCounts: IntArray,
    val colCounts: IntArray,
    val clues: List<Clue>,
    val seed: Int,
    val solution: IntArray
) {
    fun toSpec(): PuzzleSpec = PuzzleSpec(width, height, startRow, endCol, rowCounts, colCounts, clues)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Puzzle) return false
        return width == other.width &&
                height == other.height &&
                startRow == other.startRow &&
                endCol == other.endCol &&
                rowCounts.contentEquals(other.rowCounts) &&
                colCounts.contentEquals(other.colCounts) &&
                clues == other.clues &&
                seed == other.seed &&
                solution.contentEquals(other.solution)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + startRow
        result = 31 * result + endCol
        result = 31 * result + rowCounts.contentHashCode()
        result = 31 * result + colCounts.contentHashCode()
        result = 31 * result + clues.hashCode()
        result = 31 * result + seed
        result = 31 * result + solution.contentHashCode()
        return result
    }
}

enum class Difficulty(val key: String, val label: String, val points: Int) {
    EASY("easy", "Easy", 10),
    MEDIUM("medium", "Medium", 25),
    HARD("hard", "Hard", 50);

    companion object {
        fun fromKey(key: String): Difficulty = entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}
