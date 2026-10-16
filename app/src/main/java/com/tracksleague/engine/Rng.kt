package com.tracksleague.engine

import kotlin.math.floor

interface Rng {
    fun next(): Double
    fun int(n: Int): Int
    fun <T> shuffle(arr: MutableList<T>): MutableList<T>
}

class Mulberry32(seed: Int) : Rng {
    private var a: UInt = seed.toUInt()

    override fun next(): Double {
        a += 0x6d2b79f5u
        var t = a.toInt()
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + ((t xor (t ushr 7)) * 61))
        val u = (t.toLong() and 0xFFFFFFFFL) ushr 14
        return u.toDouble() / 4294967296.0
    }

    override fun int(n: Int): Int {
        if (n <= 0) return 0
        return floor(next() * n).toInt().coerceIn(0, n - 1)
    }

    override fun <T> shuffle(arr: MutableList<T>): MutableList<T> {
        for (i in arr.size - 1 downTo 1) {
            val j = int(i + 1)
            val tmp = arr[i]
            arr[i] = arr[j]
            arr[j] = tmp
        }
        return arr
    }
}
