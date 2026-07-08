package com.svoemesto.karaokeapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class KaraokeAppApplication

fun main(args: Array<String>) {
    runApplication<KaraokeAppApplication>(*args)
}
