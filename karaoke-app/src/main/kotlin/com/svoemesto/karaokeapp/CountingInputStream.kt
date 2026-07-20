package com.svoemesto.karaokeapp

import java.io.FilterInputStream
import java.io.InputStream

class CountingInputStream(
    input: InputStream,
    private val onBytesRead: (bytesRead: Long) -> Unit,
) : FilterInputStream(input) {
    private var total = 0L

    override fun read(): Int {
        val b = super.read()
        if (b != -1) {
            total++
            onBytesRead(total)
        }
        return b
    }

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        val n = super.read(b, off, len)
        if (n != -1) {
            total += n
            onBytesRead(total)
        }
        return n
    }
}
