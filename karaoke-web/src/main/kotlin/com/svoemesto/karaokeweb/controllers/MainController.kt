package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.TEXT_FILE_DICTS
import com.svoemesto.karaokeapp.WORKING_DATABASE
import com.svoemesto.karaokeapp.model.Settings
import org.springframework.stereotype.Controller
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class MainController(private val webSocket: SimpMessagingTemplate) {

    @GetMapping("/")
    fun main(model: Model): String {
        model.addAttribute("authors", Settings.loadListAuthors(WORKING_DATABASE))
        model.addAttribute("dicts", TEXT_FILE_DICTS.keys.toMutableList().sorted().toList())
        return "main"
    }

}