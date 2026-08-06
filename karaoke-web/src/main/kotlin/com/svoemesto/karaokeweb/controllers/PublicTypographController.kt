package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeapp.replaceSymbolsInSong
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

/**
 * Контроллер дубля `POST /api/replacesymbolsinsong` для публичного сайта (`karaoke-public`).
 *
 * Зачем существует в `karaoke-web`, а не в `karaoke-app`: однотипный endpoint уже есть в
 * `karaoke-app` (`ApiController.kt:5052` и `MainController.kt:970`), но `karaoke-app` на проде
 * **не разворачивается** — nginx `karaoke-public` проксирует `/api/` на `karaoke-web` (порт 8897),
 * а не на `karaoke-app` (порт 8898). Без этого контроллера кнопка «Типограф» в
 * `karaoke-public/src/views/EditorWorkView.vue` получает 405 (`Method Not Allowed`) от Spring,
 * потому что в `karaoke-web` нет соответствия для POST `/api/replacesymbolsinsong`.
 *
 * Реализация — прямой вызов `Utils.replaceSymbolsInSong(txt)` из `karaoke-app` (зависимость
 * `karaoke-web → karaoke-app` уже есть, см. `karaoke-web/build.gradle.kts:24`). Никаких БД,
 * сессий, Spring-бинов karaoke-app — функция чистая, детерминированная, без побочных эффектов.
 *
 * Контракт и поведение идентичны эталонам в `karaoke-app` (`specs/155-editor-typograph-button/
 * contracts/replacesymbolsinsong.md`). Этот же endpoint в `karaoke-app` остаётся рабочим для
 * админки (`webvue3` → nginx → `karaoke-app:8898`), дублирование намеренное.
 *
 * @see docs/features/editor-tasks.md
 */
@Controller
class PublicTypographController {
    /**
     * Применить набор типографских правил (Ё-словарь, кавычки-«ёлочки», нормализация тире/дефисов,
     * пробелов вокруг запятой/двоеточия, авто-переносы строк по заглавным буквам, удаление строк
     * из одних только «аккордовых» символов, транслитерация похожих латинских букв в кириллицу
     * при наличии русских букв в тексте) к произвольной строке `txt`.
     *
     * @param txt исходный текст (обязательный).
     * @return исправленный текст как сырая строка (НЕ JSON).
     */
    @PostMapping("/api/replacesymbolsinsong")
    @ResponseBody
    fun replaceSymbolsInSong(
        @RequestParam(required = true) txt: String,
    ): String = com.svoemesto.karaokeapp.replaceSymbolsInSong(txt)
}
