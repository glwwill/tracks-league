package com.tracksleague.ui.theme

import androidx.compose.ui.graphics.Color

val LightBg = Color(0xFFF6F4EF)
val LightPanel = Color(0xFFFFFFFF)
val LightInk = Color(0xFF26231F)
val LightMuted = Color(0xFF7A746A)
val LightLine = Color(0xFFD9D4C8)
val LightCell = Color(0xFFFBFAF6)
val LightClue = Color(0xFFE6EEFB)
val LightRail = Color(0xFF3B3A39)
val LightTie = Color(0xFFA2764A)
val LightClueRail = Color(0xFF1F4F9A)
val LightClueTie = Color(0xFF5A86C6)
val LightAccent = Color(0xFF1F6F5B)
val LightGood = Color(0xFF1F8A4C)
val LightBad = Color(0xFFC0392B)
val LightWrong = Color(0xFFF6C9C3)

val DarkBg = Color(0xFF1B1A18)
val DarkPanel = Color(0xFF25231F)
val DarkInk = Color(0xFFECE8DF)
val DarkMuted = Color(0xFFA39D90)
val DarkLine = Color(0xFF3D3A34)
val DarkCell = Color(0xFF2A2824)
val DarkClue = Color(0xFF23324A)
val DarkRail = Color(0xFFD7D3CA)
val DarkTie = Color(0xFFB38A5C)
val DarkClueRail = Color(0xFF8DB4F0)
val DarkClueTie = Color(0xFF5D86C4)
val DarkAccent = Color(0xFF49B394)
val DarkGood = Color(0xFF55C27F)
val DarkBad = Color(0xFFEE7A6C)
val DarkWrong = Color(0xFF5A2D29)

data class TracksColors(
    val bg: Color,
    val panel: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val cell: Color,
    val clue: Color,
    val rail: Color,
    val tie: Color,
    val clueRail: Color,
    val clueTie: Color,
    val accent: Color,
    val good: Color,
    val bad: Color,
    val wrong: Color
)

val LocalLightTracksColors = TracksColors(
    bg = LightBg,
    panel = LightPanel,
    ink = LightInk,
    muted = LightMuted,
    line = LightLine,
    cell = LightCell,
    clue = LightClue,
    rail = LightRail,
    tie = LightTie,
    clueRail = LightClueRail,
    clueTie = LightClueTie,
    accent = LightAccent,
    good = LightGood,
    bad = LightBad,
    wrong = LightWrong
)

val LocalDarkTracksColors = TracksColors(
    bg = DarkBg,
    panel = DarkPanel,
    ink = DarkInk,
    muted = DarkMuted,
    line = DarkLine,
    cell = DarkCell,
    clue = DarkClue,
    rail = DarkRail,
    tie = DarkTie,
    clueRail = DarkClueRail,
    clueTie = DarkClueTie,
    accent = DarkAccent,
    good = DarkGood,
    bad = DarkBad,
    wrong = DarkWrong
)
