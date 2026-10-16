package com.tracksleague.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tracksleague.data.ActiveGame
import com.tracksleague.data.GameRepository
import com.tracksleague.engine.Snapshot
import com.tracksleague.engine.initialSnapshot
import com.tracksleague.engine.isValidSolution
import com.tracksleague.sound.SoundManager
import com.tracksleague.ui.components.TrackBoard
import com.tracksleague.ui.theme.TracksTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameScreen(
    game: ActiveGame?,
    repository: GameRepository,
    soundManager: SoundManager,
    modifier: Modifier = Modifier
) {
    val colors = TracksTheme.colors
    val scope = rememberCoroutineScope()

    var snap by remember(game?.gameId) {
        mutableStateOf(game?.let { initialSnapshot(it.puzzle.toSpec()) })
    }
    val past = remember(game?.gameId) { mutableStateListOf<Snapshot>() }
    var earnedPoints by remember(game?.gameId) { mutableStateOf<Int?>(null) }
    var isChecking by remember(game?.gameId) { mutableStateOf(false) }
    var howToPlayExpanded by remember { mutableStateOf(false) }

    // Auto-check solution when valid
    LaunchedEffect(snap, game, earnedPoints, isChecking) {
        val currentSnap = snap
        val currentGame = game
        if (currentSnap == null || currentGame == null || earnedPoints != null || isChecking) return@LaunchedEffect

        if (isValidSolution(currentGame.puzzle.toSpec(), currentSnap.masks)) {
            isChecking = true
            val (ok, pts) = repository.submitSolution(currentSnap.masks)
            isChecking = false
            if (ok) {
                earnedPoints = pts
                soundManager.solve()
            }
        }
    }

    if (game == null || snap == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = colors.accent)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Generating puzzle...", color = colors.muted)
            }
        }
        return
    }

    val solved = earnedPoints != null
    val currentSnap = snap!!

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Chips header row
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val stepLabel = if (game.inLadder) {
                "Ladder ${game.step} of ${game.ladderSteps}"
            } else {
                "Bonus puzzle ${game.step - game.ladderSteps}"
            }
            Chip(text = stepLabel)
            Chip(text = "${game.size}×${game.size}")
            Chip(text = game.level.label)
            Chip(text = "${game.points} pts", isAccent = true)
        }

        // Board Canvas
        TrackBoard(
            puzzle = game.puzzle.toSpec(),
            state = currentSnap,
            solved = solved,
            soundManager = soundManager,
            onSnapshotChange = { next -> snap = next },
            onBeginGesture = {
                past.add(currentSnap.copy())
                if (past.size > 100) past.removeAt(0)
            }
        )

        // Status feedback text
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (earnedPoints != null) {
                Text(
                    text = "Solved! +$earnedPoints points.",
                    color = colors.good,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            } else if (isChecking) {
                Text(
                    text = "Checking solution...",
                    color = colors.muted,
                    fontSize = 15.sp
                )
            } else {
                Text(
                    text = "Connect entrance A to exit B using track count clues.",
                    color = colors.muted,
                    fontSize = 14.sp
                )
            }
        }

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!solved) {
                OutlinedButton(
                    onClick = {
                        if (past.isNotEmpty()) {
                            soundManager.click()
                            snap = past.removeAt(past.size - 1)
                        }
                    },
                    enabled = past.isNotEmpty(),
                    modifier = Modifier.weight(1f).testTag("undo_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Undo, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Undo")
                }

                OutlinedButton(
                    onClick = {
                        soundManager.thunk()
                        past.add(currentSnap.copy())
                        snap = initialSnapshot(game.puzzle.toSpec())
                    },
                    modifier = Modifier.weight(1f).testTag("reset_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Reset")
                }

                OutlinedButton(
                    onClick = {
                        soundManager.thunk()
                        scope.launch {
                            repository.skipGame()
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("skip_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Skip")
                }
            } else {
                Button(
                    onClick = {
                        soundManager.click()
                        scope.launch {
                            repository.loadOrCreateGame(forceNew = true)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("next_puzzle_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.panel
                    )
                ) {
                    Text("Next Puzzle", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }

        // Expandable How to Play Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.panel)
                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { howToPlayExpanded = !howToPlayExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, tint = colors.accent)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "How to play",
                        fontWeight = FontWeight.SemiBold,
                        color = colors.ink,
                        fontSize = 15.sp
                    )
                }
                Icon(
                    imageVector = if (howToPlayExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = colors.muted
                )
            }

            AnimatedVisibility(visible = howToPlayExpanded) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InstructionBullet("Drag across squares to lay track. Drag over existing track to erase it.")
                    InstructionBullet("Tap an empty square to mark it with an X (signaling no track can go there).")
                    InstructionBullet("Build one continuous track from A to B. It never branches or crosses itself.")
                    InstructionBullet("Numbers at the edge show how many squares in each row and column the track uses.")
                    InstructionBullet("Shaded blue squares are fixed clues. Points: Easy 10, Medium 25, Hard 50.")
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, isAccent: Boolean = false) {
    val colors = TracksTheme.colors
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.panel)
            .border(1.dp, if (isAccent) colors.accent else colors.line, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (isAccent) FontWeight.Bold else FontWeight.Normal,
            color = if (isAccent) colors.accent else colors.ink
        )
    }
}

@Composable
private fun InstructionBullet(text: String) {
    val colors = TracksTheme.colors
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("• ", color = colors.accent, fontWeight = FontWeight.Bold)
        Text(text = text, color = colors.muted, fontSize = 13.5.sp, lineHeight = 19.sp)
    }
}
