package com.tracksleague.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tracksleague.engine.EdgeMode
import com.tracksleague.engine.PuzzleSpec
import com.tracksleague.engine.Snapshot
import com.tracksleague.engine.changeEdge
import com.tracksleague.engine.clueSet
import com.tracksleague.engine.hasEdge
import com.tracksleague.engine.neighbour
import com.tracksleague.sound.SoundManager
import com.tracksleague.ui.theme.TracksTheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TrackBoard(
    puzzle: PuzzleSpec,
    state: Snapshot,
    solved: Boolean,
    soundManager: SoundManager,
    onSnapshotChange: (Snapshot) -> Unit,
    onBeginGesture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TracksTheme.colors
    val w = puzzle.width
    val h = puzzle.height
    val locked = remember(puzzle) { clueSet(puzzle) }

    val currentSnapshot by rememberUpdatedState(state)
    val currentSolved by rememberUpdatedState(solved)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio((w + 2f) / (h + 2f))
            .testTag("track_board")
    ) {
        val totalCols = w + 2f
        val cellSize = constraints.maxWidth.toFloat() / totalCols

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio((w + 2f) / (h + 2f))
                .pointerInput(puzzle, solved) {
                    if (solved) return@pointerInput

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var moved = false
                        var lastCell = getCellIndex(down.position, cellSize, w, h)
                        var gestureBegun = false
                        var mode: EdgeMode? = null
                        var dragPlacementCount = 0

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                val currCell = getCellIndex(change.position, cellSize, w, h)
                                if (currCell >= 0 && lastCell >= 0 && currCell != lastCell) {
                                    moved = true
                                    val r1 = lastCell / w
                                    val c1 = lastCell % w
                                    val r2 = currCell / w
                                    val c2 = currCell % w
                                    val dr = r2 - r1
                                    val dc = c2 - c1

                                    val d = when {
                                        dr == -1 && dc == 0 -> 0
                                        dr == 0 && dc == 1 -> 1
                                        dr == 1 && dc == 0 -> 2
                                        dr == 0 && dc == -1 -> 3
                                        else -> -1
                                    }

                                    val neighborCell = if (d >= 0) neighbour(puzzle, lastCell, d) else -1
                                    if (d >= 0 && neighborCell >= 0 && lastCell !in locked && neighborCell !in locked) {
                                        val cur = currentSnapshot
                                        if (mode == null) {
                                            mode = if (hasEdge(cur.masks, lastCell, d)) EdgeMode.REMOVE else EdgeMode.ADD
                                        }

                                        val nextMasks = changeEdge(puzzle, locked, cur.masks, lastCell, d, mode)
                                        if (nextMasks != null) {
                                            if (!gestureBegun) {
                                                gestureBegun = true
                                                onBeginGesture()
                                            }
                                            val marks = cur.marks.clone()
                                            marks[lastCell] = false
                                            marks[currCell] = false
                                            val nextSnap = Snapshot(nextMasks, marks)
                                            dragPlacementCount++
                                            if (mode == EdgeMode.ADD) {
                                                soundManager.place(dragPlacementCount)
                                            } else {
                                                soundManager.erase()
                                            }
                                            onSnapshotChange(nextSnap)
                                        }
                                    }
                                    lastCell = currCell
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // Tap handling
                        if (!moved && !currentSolved && lastCell >= 0) {
                            val cur = currentSnapshot
                            if (lastCell !in locked && cur.masks[lastCell] == 0) {
                                onBeginGesture()
                                val marks = cur.marks.clone()
                                marks[lastCell] = !marks[lastCell]
                                if (marks[lastCell]) {
                                    soundManager.mark()
                                } else {
                                    soundManager.unmark()
                                }
                                onSnapshotChange(Snapshot(cur.masks, marks))
                            }
                        }
                    }
                }
        ) {
            val s = cellSize
            val railDist = s * (6f / 56f)
            val tieLen = s * (10f / 56f)
            val railStroke = (s * (3f / 56f)).coerceAtLeast(2.5f)
            val tieStroke = (s * (4f / 56f)).coerceAtLeast(3f)

            // 1. Draw Cell Backgrounds
            for (i in 0 until (w * h)) {
                val r = i / w
                val c = i % w
                val x = s + c * s
                val y = s + r * s
                val isClue = i in locked
                val fill = if (isClue) colors.clue else colors.cell

                drawRect(
                    color = fill,
                    topLeft = Offset(x, y),
                    size = Size(s, s)
                )
                drawRect(
                    color = colors.line,
                    topLeft = Offset(x, y),
                    size = Size(s, s),
                    style = Stroke(width = 1f)
                )
            }

            // 2. Outer Border Frame
            drawRect(
                color = if (solved) colors.good else colors.ink,
                topLeft = Offset(s, s),
                size = Size(w * s, h * s),
                style = Stroke(width = (s * (2.5f / 56f)).coerceAtLeast(2.5f))
            )

            // 3. Entrance & Exit Outside Track Stubs
            drawPiece(
                mask = 1 shl 1,
                ox = 0f,
                oy = s + puzzle.startRow * s,
                s = s,
                railDist = railDist,
                tieLen = tieLen,
                railStroke = railStroke,
                tieStroke = tieStroke,
                railColor = colors.clueRail,
                tieColor = colors.clueTie
            )
            drawPiece(
                mask = 1 shl 0,
                ox = s + puzzle.endCol * s,
                oy = s + h * s,
                s = s,
                railDist = railDist,
                tieLen = tieLen,
                railStroke = railStroke,
                tieStroke = tieStroke,
                railColor = colors.clueRail,
                tieColor = colors.clueTie
            )

            // 4. Tracks on the Grid
            for (i in 0 until (w * h)) {
                val m = state.masks[i]
                if (m != 0) {
                    val r = i / w
                    val c = i % w
                    val isClue = i in locked
                    drawPiece(
                        mask = m,
                        ox = s + c * s,
                        oy = s + r * s,
                        s = s,
                        railDist = railDist,
                        tieLen = tieLen,
                        railStroke = railStroke,
                        tieStroke = tieStroke,
                        railColor = if (isClue) colors.clueRail else colors.rail,
                        tieColor = if (isClue) colors.clueTie else colors.tie
                    )
                }
            }

            // 5. X Marks
            for (i in 0 until (w * h)) {
                if (state.marks[i]) {
                    val r = i / w
                    val c = i % w
                    val padding = s * 0.32f
                    val x1 = s + c * s + padding
                    val y1 = s + r * s + padding
                    val x2 = s + (c + 1) * s - padding
                    val y2 = s + (r + 1) * s - padding

                    drawLine(
                        color = colors.muted,
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = (s * (3f / 56f)).coerceAtLeast(2f),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = colors.muted,
                        start = Offset(x2, y1),
                        end = Offset(x1, y2),
                        strokeWidth = (s * (3f / 56f)).coerceAtLeast(2f),
                        cap = StrokeCap.Round
                    )
                }
            }

            // 6. Badges 'A' (entrance) and 'B' (exit)
            val badgeRadius = s * 0.32f
            val textPaint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = s * 0.35f
                color = colors.bg.toArgb()
                isFakeBoldText = true
            }

            // A badge
            val ax = s / 2f
            val ay = s + puzzle.startRow * s + s / 2f
            drawCircle(color = colors.ink, radius = badgeRadius, center = Offset(ax, ay))
            drawContext.canvas.nativeCanvas.drawText("A", ax, ay + textPaint.textSize * 0.35f, textPaint)

            // B badge
            val bx = s + puzzle.endCol * s + s / 2f
            val by = s + h * s + s / 2f
            drawCircle(color = colors.ink, radius = badgeRadius, center = Offset(bx, by))
            drawContext.canvas.nativeCanvas.drawText("B", bx, by + textPaint.textSize * 0.35f, textPaint)

            // 7. Clue Numbers (Col on top, Row on right)
            val countPaint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = s * 0.44f
                isFakeBoldText = true
            }

            // Column counts on top
            for (c in 0 until w) {
                var now = 0
                for (r in 0 until h) {
                    if (state.masks[r * w + c] != 0) now++
                }
                val target = puzzle.colCounts[c]
                countPaint.color = when {
                    now > target -> colors.bad.toArgb()
                    now == target -> colors.good.toArgb()
                    else -> colors.muted.toArgb()
                }
                val cx = s + c * s + s / 2f
                val cy = s / 2f + countPaint.textSize * 0.35f
                drawContext.canvas.nativeCanvas.drawText(target.toString(), cx, cy, countPaint)
            }

            // Row counts on right
            for (r in 0 until h) {
                var now = 0
                for (c in 0 until w) {
                    if (state.masks[r * w + c] != 0) now++
                }
                val target = puzzle.rowCounts[r]
                countPaint.color = when {
                    now > target -> colors.bad.toArgb()
                    now == target -> colors.good.toArgb()
                    else -> colors.muted.toArgb()
                }
                val cx = s + w * s + s / 2f
                val cy = s + r * s + s / 2f + countPaint.textSize * 0.35f
                drawContext.canvas.nativeCanvas.drawText(target.toString(), cx, cy, countPaint)
            }
        }
    }
}

private fun getCellIndex(pos: Offset, s: Float, w: Int, h: Int): Int {
    val x = pos.x - s
    val y = pos.y - s
    val c = (x / s).toInt()
    val r = (y / s).toInt()
    if (r !in 0 until h || c !in 0 until w) return -1
    return r * w + c
}

private fun DrawScope.drawPiece(
    mask: Int,
    ox: Float,
    oy: Float,
    s: Float,
    railDist: Float,
    tieLen: Float,
    railStroke: Float,
    tieStroke: Float,
    railColor: Color,
    tieColor: Color
) {
    val cx = ox + s / 2f
    val cy = oy + s / 2f
    val dirs = (0 until 4).filter { ((mask shr it) and 1) == 1 }

    val midD = arrayOf(
        Offset(0f, -s / 2f),
        Offset(s / 2f, 0f),
        Offset(0f, s / 2f),
        Offset(-s / 2f, 0f)
    )

    if (dirs.size == 1) {
        val d = dirs[0]
        val mx = midD[d].x
        val my = midD[d].y
        val isHorizontal = d == 1 || d == 3
        val ex = cx + mx
        val ey = cy + my

        if (isHorizontal) {
            drawLine(railColor, Offset(cx, cy - railDist), Offset(ex, cy - railDist), strokeWidth = railStroke)
            drawLine(railColor, Offset(cx, cy + railDist), Offset(ex, cy + railDist), strokeWidth = railStroke)
            for (k in 0 until 2) {
                val tx = cx + (mx * (k + 0.5f)) / 2f
                drawLine(tieColor, Offset(tx, cy - tieLen), Offset(tx, cy + tieLen), strokeWidth = tieStroke)
            }
        } else {
            drawLine(railColor, Offset(cx - railDist, cy), Offset(cx - railDist, ey), strokeWidth = railStroke)
            drawLine(railColor, Offset(cx + railDist, cy), Offset(cx + railDist, ey), strokeWidth = railStroke)
            for (k in 0 until 2) {
                val ty = cy + (my * (k + 0.5f)) / 2f
                drawLine(tieColor, Offset(cx - tieLen, ty), Offset(cx + tieLen, ty), strokeWidth = tieStroke)
            }
        }
    } else if (dirs.size >= 2) {
        val a = dirs[0]
        val b = dirs[1]

        if (b - a == 2) {
            // Straight across
            if (a == 0) {
                // Vertical
                drawLine(railColor, Offset(cx - railDist, oy), Offset(cx - railDist, oy + s), strokeWidth = railStroke)
                drawLine(railColor, Offset(cx + railDist, oy), Offset(cx + railDist, oy + s), strokeWidth = railStroke)
                for (k in 0 until 4) {
                    val ty = oy + ((k + 0.5f) * s) / 4f
                    drawLine(tieColor, Offset(cx - tieLen, ty), Offset(cx + tieLen, ty), strokeWidth = tieStroke)
                }
            } else {
                // Horizontal
                drawLine(railColor, Offset(ox, cy - railDist), Offset(ox + s, cy - railDist), strokeWidth = railStroke)
                drawLine(railColor, Offset(ox, cy + railDist), Offset(ox + s, cy + railDist), strokeWidth = railStroke)
                for (k in 0 until 4) {
                    val tx = ox + ((k + 0.5f) * s) / 4f
                    drawLine(tieColor, Offset(tx, cy - tieLen), Offset(tx, cy + tieLen), strokeWidth = tieStroke)
                }
            }
        } else {
            // Curve: arc is centered on the corner shared by the two edges
            val kx = ox + (if (1 in dirs) s else 0f)
            val ky = oy + (if (2 in dirs) s else 0f)

            val m1x = cx + midD[a].x
            val m1y = cy + midD[a].y
            val m2x = cx + midD[b].x
            val m2y = cy + midD[b].y

            val t1 = atan2(m1y - ky, m1x - kx)
            var delta = atan2(m2y - ky, m2x - kx) - t1
            while (delta > PI) delta -= (2.0 * PI).toFloat()
            while (delta < -PI) delta += (2.0 * PI).toFloat()

            // Draw curved ties
            for (k in 0 until 3) {
                val th = t1 + (delta * (k + 0.5f)) / 3f
                val rInner = s / 2f - tieLen
                val rOuter = s / 2f + tieLen
                val x1 = kx + rInner * cos(th)
                val y1 = ky + rInner * sin(th)
                val x2 = kx + rOuter * cos(th)
                val y2 = ky + rOuter * sin(th)
                drawLine(tieColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = tieStroke)
            }

            // Draw curved rails
            val radii = floatArrayOf(s / 2f - railDist, s / 2f + railDist)
            for (r in radii) {
                val path = Path()
                val steps = 16
                for (step in 0..steps) {
                    val th = t1 + delta * (step.toFloat() / steps)
                    val px = kx + r * cos(th)
                    val py = ky + r * sin(th)
                    if (step == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, railColor, style = Stroke(width = railStroke))
            }
        }
    }
}
