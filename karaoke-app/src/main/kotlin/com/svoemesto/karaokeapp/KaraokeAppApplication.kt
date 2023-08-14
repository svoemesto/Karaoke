package com.svoemesto.karaokeapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class KaraokeAppApplication

fun main(args: Array<String>) {
    runApplication<KaraokeAppApplication>(*args)
}
