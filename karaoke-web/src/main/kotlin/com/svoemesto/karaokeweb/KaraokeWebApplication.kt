package com.svoemesto.karaokeweb

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KaraokeWebApplication

fun main(args: Array<String>) {
    runApplication<KaraokeWebApplication>(*args)
}
