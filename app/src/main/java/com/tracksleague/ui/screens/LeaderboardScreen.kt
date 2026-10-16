package com.tracksleague.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tracksleague.data.LeaderRow
import com.tracksleague.ui.theme.TracksTheme

@Composable
fun LeaderboardScreen(
    leaderboard: List<LeaderRow>,
    modifier: Modifier = Modifier
) {
    val colors = TracksTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("leaderboard_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = colors.accent)
            Text(
                text = "League Standings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.ink
            )
        }

        Text(
            text = "Ranked by total score, then fewer puzzles solved. Solve ladder and bonus puzzles to climb the ranks!",
            color = colors.muted,
            fontSize = 13.5.sp
        )

        // Leaderboard Table Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.panel)
                .border(1.dp, colors.line, RoundedCornerShape(14.dp))
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.cell)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#",
                            fontWeight = FontWeight.Bold,
                            color = colors.ink,
                            modifier = Modifier.weight(0.7f),
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Player",
                            fontWeight = FontWeight.Bold,
                            color = colors.ink,
                            modifier = Modifier.weight(2.5f),
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Score",
                            fontWeight = FontWeight.Bold,
                            color = colors.ink,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.2f),
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Solved",
                            fontWeight = FontWeight.Bold,
                            color = colors.ink,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.2f),
                            fontSize = 13.sp
                        )
                    }
                    HorizontalDivider(color = colors.line, thickness = 1.dp)
                }

                items(leaderboard) { row ->
                    val bg = if (row.isMe) colors.clue else colors.panel
                    val fw = if (row.isMe) FontWeight.Bold else FontWeight.Normal

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = row.rank.toString(),
                            fontWeight = fw,
                            color = if (row.isMe) colors.accent else colors.ink,
                            modifier = Modifier.weight(0.7f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (row.isMe) "${row.name} (You)" else row.name,
                            fontWeight = fw,
                            color = colors.ink,
                            modifier = Modifier.weight(2.5f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${row.score}",
                            fontWeight = fw,
                            color = if (row.isMe) colors.accent else colors.ink,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.2f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${row.games}",
                            fontWeight = fw,
                            color = colors.muted,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.2f),
                            fontSize = 14.sp
                        )
                    }
                    HorizontalDivider(color = colors.line.copy(alpha = 0.5f), thickness = 0.5.dp)
                }
            }
        }
    }
}
