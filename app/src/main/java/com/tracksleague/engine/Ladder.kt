package com.tracksleague.engine

import kotlin.random.Random

data class LadderRung(val size: Int, val level: Difficulty)

val LADDER = listOf(
    LadderRung(5, Difficulty.EASY),
    LadderRung(6, Difficulty.EASY),
    LadderRung(7, Difficulty.MEDIUM),
    LadderRung(8, Difficulty.MEDIUM),
    LadderRung(9, Difficulty.HARD),
    LadderRung(10, Difficulty.HARD)
)

data class Assignment(
    val step: Int,
    val size: Int,
    val level: Difficulty,
    val seed: Int,
    val points: Int
)

fun assignmentFor(step: Int, seedOverride: Int? = null): Assignment {
    val rung = if (step in 1..LADDER.size) LADDER[step - 1] else null
    val level = rung?.level ?: Difficulty.entries.random()
    val size = rung?.size ?: Random.nextInt(MIN_SIZE, MAX_SIZE + 1)
    val seed = seedOverride ?: Random.nextInt(1, 2_000_000_000)
    return Assignment(
        step = step,
        size = size,
        level = level,
        seed = seed,
        points = level.points
    )
}
