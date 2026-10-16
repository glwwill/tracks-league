package com.tracksleague

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tracksleague.data.GameRepository
import com.tracksleague.sound.SoundManager
import com.tracksleague.ui.components.ProfileDialog
import com.tracksleague.ui.screens.GameScreen
import com.tracksleague.ui.screens.LeaderboardScreen
import com.tracksleague.ui.theme.TracksLeagueTheme
import com.tracksleague.ui.theme.TracksTheme

enum class MainTab {
    PLAY,
    LEADERBOARD
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = GameRepository(applicationContext)
        val soundManager = SoundManager(applicationContext)

        setContent {
            TracksLeagueTheme {
                MainAppShell(
                    repository = repository,
                    soundManager = soundManager
                )
            }
        }
    }
}

@Composable
fun MainAppShell(
    repository: GameRepository,
    soundManager: SoundManager
) {
    val colors = TracksTheme.colors
    val user by repository.currentUser.collectAsState()
    val game by repository.currentGame.collectAsState()
    val leaderboard by repository.leaderboard.collectAsState()
    val soundOn by soundManager.soundEnabled.collectAsState()

    var currentTab by remember { mutableStateOf(MainTab.PLAY) }
    var showProfileDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.loadOrCreateGame()
    }

    BackHandler(enabled = currentTab != MainTab.PLAY) {
        currentTab = MainTab.PLAY
    }

    if (showProfileDialog) {
        ProfileDialog(
            user = user,
            onDismiss = { showProfileDialog = false },
            onSaveName = { newName -> repository.updateUserName(newName) }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.bg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
            ) {
                // Topbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tracks League",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showProfileDialog = true }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Profile",
                            tint = colors.accent
                        )
                        Text(
                            text = user.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.ink
                        )
                        Text(
                            text = "${user.totalScore} pts",
                            fontSize = 13.sp,
                            color = colors.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Navigation Pills + Sound Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TabPill(
                            selected = currentTab == MainTab.PLAY,
                            label = "Play",
                            icon = Icons.Default.PlayArrow,
                            onClick = { currentTab = MainTab.PLAY },
                            testTag = "tab_play"
                        )
                        TabPill(
                            selected = currentTab == MainTab.LEADERBOARD,
                            label = "Leaderboard",
                            icon = Icons.Default.Leaderboard,
                            onClick = { currentTab = MainTab.LEADERBOARD },
                            testTag = "tab_leaderboard"
                        )
                    }

                    // Sound Toggle Button
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.panel)
                            .border(1.dp, colors.line, CircleShape)
                            .clickable { soundManager.toggleSound() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("sound_toggle_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (soundOn) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                            contentDescription = if (soundOn) "Sound on" else "Sound off",
                            tint = if (soundOn) colors.accent else colors.muted
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (soundOn) "Sound on" else "Sound off",
                            fontSize = 12.5.sp,
                            color = if (soundOn) colors.ink else colors.muted
                        )
                    }
                }

                // Tab Content
                when (currentTab) {
                    MainTab.PLAY -> GameScreen(
                        game = game,
                        repository = repository,
                        soundManager = soundManager,
                        modifier = Modifier.weight(1f)
                    )
                    MainTab.LEADERBOARD -> LeaderboardScreen(
                        leaderboard = leaderboard,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TabPill(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    val colors = TracksTheme.colors
    val bg = if (selected) colors.ink else colors.panel
    val contentColor = if (selected) colors.bg else colors.ink
    val borderColor = if (selected) colors.ink else colors.line

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = contentColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}
