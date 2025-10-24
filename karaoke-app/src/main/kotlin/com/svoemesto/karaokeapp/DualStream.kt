package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.SseNotification
import com.svoemesto.karaokeapp.services.SNS
import java.io.PrintStream


class DualStream(@Suppress("unused") private val out1: PrintStream) : PrintStream(out1) {

    override fun write(buf: ByteArray, off: Int, len: Int) {
        try {
            super.write(buf, off, len)
            val buffToPrint: ByteArray = buf.copyOfRange(off, off + len)
//            if (!(len == 1 && buf[0].toInt() == 10)) {
                val txt = buffToPrint.toString(Charsets.UTF_8)
                SNS.send(SseNotification.log(txt))
//            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}