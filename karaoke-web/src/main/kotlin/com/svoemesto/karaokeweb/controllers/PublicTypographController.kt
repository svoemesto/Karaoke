package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.replaceSymbolsInSong
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
 * Реализация — прямой вызов локальной [`replaceSymbolsInSong`](../TypographUtils.kt.html), а не
 * `com.svoemesto.karaokeapp.replaceSymbolsInSong`: последняя при первом обращении тянет за собой
 * `Constants.kt` из `karaoke-app`, который при class init собирает карту `ProducerType → Mko*`
 * (MLT) — JVM загружает все `Mko*`-классы, часть которых при инициализации обращается к
 * `APP_WORK_ON_SERVER`/`WORKING_DATABASE` для MLT, настроенным только в `karaoke-app`. На проде
 * `karaoke-app` не развёрнут — переменные не инициализированы — class init падает с
 * `NoClassDefFoundError: Could not initialize class com.svoemesto.karaokeapp.ConstantsKt`,
 * и POST возвращает 500 `Internal Server Error`. Локальная копия правил (в `TypographUtils.kt`)
 * отрезает karaoke-web от этого class loading, поведение идентично.
 *
 * Контракт и поведение идентичны эталонам в `karaoke-app`
 * (`specs/155-editor-typograph-button/contracts/replacesymbolsinsong.md`). Этот же endpoint в
 * `karaoke-app` остаётся рабочим для админки (`webvue3` → nginx → `karaoke-app:8898`),
 * дублирование намеренное.
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
    ): String = com.svoemesto.karaokeweb.replaceSymbolsInSong(txt) // fully-qualified: см. PR #207, иначе рекурсия
}
