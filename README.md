# Tracks League (Android)

Train Tracks puzzle game rewritten as a native Android application using Kotlin and Jetpack Compose. Includes progressive ladder challenges, bonus levels, point scoring, dynamic leaderboards, and synthesized sound effects.

## Features

- **Train Tracks Puzzle Engine**: Full native Kotlin implementation of the Grand Tour / Train Tracks puzzle generation and deductive/backtracking solver.
- **Progressive Ladder**:
  - Steps 1 & 2: Easy (5×5, 6×6) — 10 points
  - Steps 3 & 4: Medium (7×7, 8×8) — 25 points
  - Steps 5 & 6: Hard (9×9, 10×10) — 50 points
  - Step 7+: Infinite bonus puzzles with random sizes and difficulty levels
- **Interactive Board**:
  - Custom Canvas rendering with authentic rails, ties, curved tracks, and entrance/exit badges (A to B)
  - Drag from square to square to lay or erase track
  - Tap empty squares to mark with an X (cell eliminated)
  - Real-time row and column track counts that highlight green when matched and red when exceeded
  - Full Undo and Reset support
- **Audio Synthesizer**: Built-in sound effects (pentatonic ascending scale during track placement, erase whoosh, tap marks, and victory fanfare) synthesized in real time via Android `AudioTrack` with no external media assets required.
- **League Standings**: Local leaderboard ranked by total score and fewest puzzles solved, with customizable player profile.
- **Material Design 3**: Linen/parchment theme with dark ink accents, responsive layout, and dark mode support.

## Project Structure

```
app/
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/tracksleague/
│   │   ├── MainActivity.kt               # Main app shell, edge-to-edge, navigation tabs
│   │   ├── data/
│   │   │   └── GameRepository.kt         # Persistence, progression, and leaderboard data
│   │   ├── engine/
│   │   │   ├── Types.kt                  # Directions, Clue, PuzzleSpec, Difficulty
│   │   │   ├── Rng.kt                    # Deterministic Mulberry32 seeded RNG
│   │   │   ├── Solver.kt                 # Deductive logic and backtracking solver
│   │   │   ├── Generator.kt              # Unique puzzle generator & solution validator
│   │   │   ├── GameEngine.kt             # Board state, edge manipulation, snapshots
│   │   │   └── Ladder.kt                 # Ladder sequence & assignment generation
│   │   ├── sound/
│   │   │   └── SoundManager.kt           # Real-time PCM audio synthesis
│   │   └── ui/
│   │       ├── components/
│   │       │   ├── TrackBoard.kt         # Interactive Canvas board with gestures
│   │       │   └── ProfileDialog.kt      # Player name customization dialog
│   │       ├── screens/
│   │       │   ├── GameScreen.kt         # Gameplay screen with controls & instructions
│   │       │   └── LeaderboardScreen.kt  # League rankings table
│   │       └── theme/
│   │           ├── Color.kt              # Custom palette (linen, rail, tie, accent)
│   │           └── Theme.kt              # Material 3 theme & dynamic insets
│   └── res/
│       ├── drawable/                     # Adaptive launcher icon & assets
│       ├── mipmap-*/                     # Multi-density raster icons
│       └── values/                       # strings.xml, themes.xml
```

## How to Play

1. Drag across squares to lay track. Drag over existing track to erase it.
2. Tap an empty square to mark it with an X (indicating no track can pass through).
3. Connect entrance **A** to exit **B** in one single path without branching or crossing.
4. The numbers on the top and right edges show exactly how many track segments belong in each column and row.
