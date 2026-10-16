package com.tracksleague.engine

data class SolveResult(
    val solutions: List<IntArray>,
    val complete: Boolean
)

data class DeduceResult(
    val solved: Boolean,
    val undecided: List<Int>
)

private class Ctx(
    val w: Int,
    val h: Int,
    val n: Int,
    val nb: ShortArray,
    val start: Int,
    val end: Int,
    val rowCounts: IntArray,
    val colCounts: IntArray
)

private class State(
    val C: ByteArray,
    val E: ByteArray
) {
    fun copy(): State = State(C.clone(), E.clone())
}

private fun buildCtx(p: PuzzleSpec): Ctx {
    val w = p.width
    val h = p.height
    val n = w * h
    val nb = ShortArray(n * 4) { -1 }
    for (r in 0 until h) {
        for (c in 0 until w) {
            for (d in 0 until 4) {
                val rr = r + DR[d]
                val cc = c + DC[d]
                if (rr in 0 until h && cc in 0 until w) {
                    nb[(r * w + c) * 4 + d] = (rr * w + cc).toShort()
                }
            }
        }
    }
    return Ctx(
        w = w,
        h = h,
        n = n,
        nb = nb,
        start = p.startRow * w,
        end = (h - 1) * w + p.endCol,
        rowCounts = p.rowCounts,
        colCounts = p.colCounts
    )
}

private fun setE(ctx: Ctx, st: State, i: Int, d: Int, v: Int): Int {
    val k = i * 4 + d
    val cur = st.E[k].toInt()
    if (cur == v) return 0
    if (cur != -1) return -1
    st.E[k] = v.toByte()
    val j = ctx.nb[k].toInt()
    if (j >= 0) {
        st.E[j * 4 + ((d + 2) and 3)] = v.toByte()
    }
    return 1
}

private fun propagate(ctx: Ctx, st: State): Boolean {
    val n = ctx.n
    val w = ctx.w
    val h = ctx.h
    val nb = ctx.nb
    val C = st.C
    val E = st.E

    while (true) {
        var changed = true
        while (changed) {
            changed = false

            for (i in 0 until n) {
                var o = 0
                var u = 0
                val b = i * 4
                for (d in 0 until 4) {
                    val e = E[b + d].toInt()
                    if (e == 1) o++
                    else if (e == -1) u++
                }
                var c = C[i].toInt()
                if (c == 0) {
                    if (o > 0) return false
                    if (u > 0) {
                        for (d in 0 until 4) {
                            if (E[b + d].toInt() == -1) setE(ctx, st, i, d, 0)
                        }
                        changed = true
                    }
                    continue
                }
                if (o > 2) return false
                if (c == -1) {
                    if (o >= 1) {
                        C[i] = 1
                        c = 1
                        changed = true
                    } else if (u < 2) {
                        C[i] = 0
                        for (d in 0 until 4) {
                            if (E[b + d].toInt() == -1) setE(ctx, st, i, d, 0)
                        }
                        changed = true
                        continue
                    }
                }
                if (c == 1) {
                    if (o + u < 2) return false
                    if (o == 2) {
                        if (u > 0) {
                            for (d in 0 until 4) {
                                if (E[b + d].toInt() == -1) setE(ctx, st, i, d, 0)
                            }
                            changed = true
                        }
                    } else if (o + u == 2 && u > 0) {
                        for (d in 0 until 4) {
                            if (E[b + d].toInt() == -1) setE(ctx, st, i, d, 1)
                        }
                        changed = true
                    }
                }
            }

            for (r in 0 until h) {
                var t = 0
                var u = 0
                for (c in 0 until w) {
                    val v = C[r * w + c].toInt()
                    if (v == 1) t++
                    else if (v == -1) u++
                }
                val T = ctx.rowCounts[r]
                if (t > T || t + u < T) return false
                if (u > 0 && (t == T || t + u == T)) {
                    val valueToSet = if (t == T) 0.toByte() else 1.toByte()
                    for (c in 0 until w) {
                        if (C[r * w + c].toInt() == -1) C[r * w + c] = valueToSet
                    }
                    changed = true
                }
            }

            for (c in 0 until w) {
                var t = 0
                var u = 0
                for (r in 0 until h) {
                    val v = C[r * w + c].toInt()
                    if (v == 1) t++
                    else if (v == -1) u++
                }
                val T = ctx.colCounts[c]
                if (t > T || t + u < T) return false
                if (u > 0 && (t == T || t + u == T)) {
                    val valueToSet = if (t == T) 0.toByte() else 1.toByte()
                    for (r in 0 until h) {
                        if (C[r * w + c].toInt() == -1) C[r * w + c] = valueToSet
                    }
                    changed = true
                }
            }
        }

        // Loop and completeness checks on the connected pieces so far
        val comp = ShortArray(n) { -1 }
        var nComp = 0
        val cellsIn = mutableListOf<Int>()
        val edgesIn = mutableListOf<Int>()
        val stack = IntArray(n)
        var stackTop = 0

        for (s in 0 until n) {
            if (comp[s].toInt() != -1 || C[s].toInt() != 1) continue
            var cells = 0
            var ends = 0
            comp[s] = nComp.toShort()
            stack[stackTop++] = s
            while (stackTop > 0) {
                val i = stack[--stackTop]
                cells++
                for (d in 0 until 4) {
                    if (E[i * 4 + d].toInt() != 1) continue
                    val j = nb[i * 4 + d].toInt()
                    if (j < 0) continue
                    ends++
                    if (comp[j].toInt() == -1) {
                        comp[j] = nComp.toShort()
                        stack[stackTop++] = j
                    }
                }
            }
            cellsIn.add(cells)
            edgesIn.add(ends / 2)
            nComp++
        }

        for (k in 0 until nComp) {
            if (edgesIn[k] >= cellsIn[k]) return false // a closed loop
        }

        val cs = comp[ctx.start].toInt()
        val ce = comp[ctx.end].toInt()
        if (cs != -1 && cs == ce) {
            var any = false
            for (i in 0 until n) {
                if (comp[i].toInt() == cs) continue
                if (C[i].toInt() == 1) return false
                if (C[i].toInt() == -1) {
                    C[i] = 0
                    any = true
                }
            }
            if (any) continue
        }
        return true
    }
}

private data class InitStateResult(
    val ctx: Ctx,
    val root: State,
    val ok: Boolean
)

private fun initState(p: PuzzleSpec): InitStateResult {
    val ctx = buildCtx(p)
    val n = ctx.n
    val w = ctx.w
    val h = ctx.h
    val root = State(ByteArray(n) { -1 }, ByteArray(n * 4) { -1 })
    var ok = true

    for (r in 0 until h) {
        if (!ok) break
        for (c in 0 until w) {
            if (!ok) break
            val i = r * w + c
            for (d in 0 until 4) {
                if (ctx.nb[i * 4 + d].toInt() != -1) continue
                var v = 0
                if (i == ctx.start && d == 3) v = 1
                if (i == ctx.end && d == 2) v = 1
                if (setE(ctx, root, i, d, v) < 0) ok = false
            }
        }
    }

    for (cl in p.clues) {
        val i = cl.r * w + cl.c
        root.C[i] = 1
        for (d in 0 until 4) {
            if (setE(ctx, root, i, d, (cl.mask shr d) and 1) < 0) ok = false
        }
    }
    return InitStateResult(ctx, root, ok)
}

fun deduce(p: PuzzleSpec): DeduceResult {
    val init = initState(p)
    if (!init.ok || !propagate(init.ctx, init.root)) return DeduceResult(false, emptyList())
    val undecided = mutableListOf<Int>()
    var open = false
    for (i in 0 until init.ctx.n) {
        if (init.root.C[i].toInt() == -1) undecided.add(i)
        for (d in 0 until 4) {
            if (init.root.E[i * 4 + d].toInt() == -1) open = true
        }
    }
    return DeduceResult(undecided.isEmpty() && !open, undecided)
}

fun solve(p: PuzzleSpec, limit: Int = 2, nodeLimit: Int = 200000): SolveResult {
    val init = initState(p)
    val solutions = mutableListOf<IntArray>()
    if (!init.ok) return SolveResult(solutions, complete = true)

    val ctx = init.ctx
    val n = ctx.n
    var nodes = 0
    var aborted = false

    fun recurse(st: State) {
        if (aborted || solutions.size >= limit) return
        if (++nodes > nodeLimit) {
            aborted = true
            return
        }
        if (!propagate(ctx, st)) return

        val C = st.C
        val E = st.E

        var bi = -1
        var bd = -1
        var bestU = 9
        for (i in 0 until n) {
            if (C[i].toInt() != 1) continue
            var u = 0
            var fd = -1
            for (d in 0 until 4) {
                if (E[i * 4 + d].toInt() == -1) {
                    u++
                    if (fd < 0) fd = d
                }
            }
            if (u in 1 until bestU) {
                bestU = u
                bi = i
                bd = fd
            }
        }

        if (bi < 0) {
            loopOuter@ for (i in 0 until n) {
                for (d in 0 until 4) {
                    if (E[i * 4 + d].toInt() == -1) {
                        bi = i
                        bd = d
                        break@loopOuter
                    }
                }
            }
        }

        if (bi < 0) {
            val masks = IntArray(n)
            for (i in 0 until n) {
                var m = 0
                for (d in 0 until 4) {
                    if (E[i * 4 + d].toInt() == 1) m = m or (1 shl d)
                }
                masks[i] = m
            }
            solutions.add(masks)
            return
        }

        for (v in intArrayOf(1, 0)) {
            val next = st.copy()
            if (setE(ctx, next, bi, bd, v) < 0) continue
            recurse(next)
            if (aborted || solutions.size >= limit) return
        }
    }

    recurse(init.root)
    return SolveResult(solutions, complete = !aborted)
}

fun isUnique(p: PuzzleSpec, nodeLimit: Int = 200000): Boolean {
    val res = solve(p, limit = 2, nodeLimit = nodeLimit)
    return res.complete && res.solutions.size == 1
}
