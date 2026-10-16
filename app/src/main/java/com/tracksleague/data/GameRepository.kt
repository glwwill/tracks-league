package com.tracksleague.data

import android.content.Context
import com.tracksleague.engine.Assignment
import com.tracksleague.engine.Difficulty
import com.tracksleague.engine.LADDER
import com.tracksleague.engine.Puzzle
import com.tracksleague.engine.assignmentFor
import com.tracksleague.engine.generatePuzzle
import com.tracksleague.engine.isValidSolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class User(
    val id: Int,
    val name: String,
    val gamesPlayed: Int,
    val totalScore: Int
)

data class LeaderRow(
    val rank: Int,
    val id: Int,
    val name: String,
    val score: Int,
    val games: Int,
    val isMe: Boolean = false
)

data class ActiveGame(
    val gameId: Int,
    val step: Int,
    val ladderSteps: Int,
    val inLadder: Boolean,
    val size: Int,
    val level: Difficulty,
    val points: Int,
    val puzzle: Puzzle
)

class GameRepository(context: Context) {
    private val prefs = context.getSharedPreferences("tracks_league_data", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow(loadUser())
    val currentUser: StateFlow<User> = _currentUser.asStateFlow()

    private val _currentGame = MutableStateFlow<ActiveGame?>(null)
    val currentGame: StateFlow<ActiveGame?> = _currentGame.asStateFlow()

    private val _leaderboard = MutableStateFlow<List<LeaderRow>>(emptyList())
    val leaderboard: StateFlow<List<LeaderRow>> = _leaderboard.asStateFlow()

    init {
        updateLeaderboard()
    }

    private fun loadUser(): User {
        val id = prefs.getInt("user_id", 101)
        val name = prefs.getString("user_name", "Player 1") ?: "Player 1"
        val gamesPlayed = prefs.getInt("user_games", 0)
        val totalScore = prefs.getInt("user_score", 0)
        return User(id = id, name = name, gamesPlayed = gamesPlayed, totalScore = totalScore)
    }

    fun updateUserName(newName: String) {
        val trimmed = newName.trim().ifEmpty { "Player 1" }
        val user = _currentUser.value.copy(name = trimmed)
        _currentUser.value = user
        prefs.edit().putString("user_name", trimmed).apply()
        updateLeaderboard()
    }

    suspend fun loadOrCreateGame(forceNew: Boolean = false): ActiveGame = withContext(Dispatchers.Default) {
        val user = _currentUser.value
        val step = user.gamesPlayed + 1
        val savedSeed = if (!forceNew) prefs.getInt("active_seed", -1) else -1
        val assignment = if (savedSeed > 0) {
            val sz = prefs.getInt("active_size", 5)
            val lvl = Difficulty.fromKey(prefs.getString("active_level", "easy") ?: "easy")
            Assignment(step = step, size = sz, level = lvl, seed = savedSeed, points = lvl.points)
        } else {
            assignmentFor(step)
        }

        val puzzle = generatePuzzle(
            width = assignment.size,
            height = assignment.size,
            seed = assignment.seed,
            level = assignment.level
        )

        val game = ActiveGame(
            gameId = assignment.seed xor 0x5F3759DF,
            step = assignment.step,
            ladderSteps = LADDER.size,
            inLadder = assignment.step <= LADDER.size,
            size = assignment.size,
            level = assignment.level,
            points = assignment.points,
            puzzle = puzzle
        )

        prefs.edit()
            .putInt("active_seed", assignment.seed)
            .putInt("active_size", assignment.size)
            .putString("active_level", assignment.level.key)
            .apply()

        _currentGame.value = game
        game
    }

    suspend fun skipGame(): ActiveGame = withContext(Dispatchers.Default) {
        val user = _currentUser.value
        val step = user.gamesPlayed + 1
        val assignment = assignmentFor(step)
        val puzzle = generatePuzzle(
            width = assignment.size,
            height = assignment.size,
            seed = assignment.seed,
            level = assignment.level
        )
        val game = ActiveGame(
            gameId = assignment.seed xor 0x5F3759DF,
            step = assignment.step,
            ladderSteps = LADDER.size,
            inLadder = assignment.step <= LADDER.size,
            size = assignment.size,
            level = assignment.level,
            points = assignment.points,
            puzzle = puzzle
        )
        prefs.edit()
            .putInt("active_seed", assignment.seed)
            .putInt("active_size", assignment.size)
            .putString("active_level", assignment.level.key)
            .apply()

        _currentGame.value = game
        game
    }

    suspend fun submitSolution(masks: IntArray): Pair<Boolean, Int> = withContext(Dispatchers.Default) {
        val game = _currentGame.value ?: return@withContext Pair(false, 0)
        val isValid = isValidSolution(game.puzzle.toSpec(), masks)
        if (!isValid) return@withContext Pair(false, 0)

        // Solved! Award points and increment games played
        val user = _currentUser.value
        val newGamesPlayed = user.gamesPlayed + 1
        val newScore = user.totalScore + game.points
        val updatedUser = user.copy(gamesPlayed = newGamesPlayed, totalScore = newScore)

        prefs.edit()
            .putInt("user_games", newGamesPlayed)
            .putInt("user_score", newScore)
            .remove("active_seed")
            .apply()

        _currentUser.value = updatedUser
        updateLeaderboard()
        Pair(true, game.points)
    }

    fun nextPuzzle() {
        // Will be loaded by caller triggering loadOrCreateGame(forceNew = true)
    }

    private fun updateLeaderboard() {
        val me = _currentUser.value
        val baseRivals = listOf(
            LeaderRow(0, 1, "Gary S.", 145, 6),
            LeaderRow(0, 2, "Sarah M.", 120, 5),
            LeaderRow(0, 3, "Alex K.", 85, 4),
            LeaderRow(0, 4, "Elena R.", 70, 3),
            LeaderRow(0, 5, "David L.", 45, 2),
            LeaderRow(0, 6, "Rachel T.", 35, 2),
            LeaderRow(0, 7, "Marcus B.", 10, 1)
        )

        val allEntries = (baseRivals + LeaderRow(
            rank = 0,
            id = me.id,
            name = me.name,
            score = me.totalScore,
            games = me.gamesPlayed,
            isMe = true
        )).distinctBy { it.id }

        // Sort by total score descending, then games played ascending, then id
        val sorted = allEntries.sortedWith(
            compareByDescending<LeaderRow> { it.score }
                .thenBy { it.games }
                .thenBy { it.id }
        )

        val ranked = sorted.mapIndexed { index, row ->
            row.copy(rank = index + 1)
        }

        _leaderboard.value = ranked
    }
}
