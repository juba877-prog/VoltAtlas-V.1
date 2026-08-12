package com.example.pdf

import java.io.OutputStream

class CountingOutputStream(private val out: OutputStream) : OutputStream() {
    var count: Long = 0
        private set
    override fun write(b: Int) {
        out.write(b)
        count++
    }
    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        count += len
    }
    override fun close() {
        out.close()
    }
    override fun flush() {
        out.flush()
    }
}
