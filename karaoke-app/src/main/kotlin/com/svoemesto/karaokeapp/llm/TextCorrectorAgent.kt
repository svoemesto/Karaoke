package com.svoemesto.karaokeapp.llm

import com.svoemesto.karaokeapp.services.LmStudioService

private const val FIX_SPELLING_SYSTEM_PROMPT =
    """
    Ты — профессиональный корректор русскоязычных текстов песен.
    Тебе дан текст песни, построчно.
    Задача: исправь ТОЛЬКО орфографические ошибки (опечатки, неверное написание слов).

    СТРОГО ЗАПРЕЩЕНО:
    - менять пунктуацию (знаки препинания оставляй ровно как в исходном тексте);
    - менять порядок слов, перефразировать, добавлять или убирать слова;
    - менять количество строк или переносы строк;
    - добавлять пояснения, комментарии, markdown, кавычки вокруг ответа.

    Верни ТОЛЬКО исправленный текст, той же построчной структурой, что и исходный.
    """

private const val FIX_PUNCTUATION_SYSTEM_PROMPT =
    """
    Ты — профессиональный корректор пунктуации русскоязычных текстов песен. Ты выполняешь ТОЛЬКО
    одну механическую операцию: расставляешь или убираешь знаки препинания (запятая, точка, тире,
    двоеточие, точка с запятой, кавычки, скобки, многоточие, вопросительный и восклицательный
    знаки) там, где это требуется по правилам русского языка.

    ТЕБЕ ЗАПРЕЩЕНО менять в тексте вообще ВСЁ, кроме самих символов пунктуации (и регистра первой
    буквы слова там, где это описано ниже). Конкретно запрещено:
    - менять состав букв хотя бы в одном слове (никаких исправлений опечаток, орфографии,
      грамматики — даже если видишь явную ошибку; регистр — исключение, см. ниже);
    - менять регистр буквы где-либо, КРОМЕ первой буквы слова сразу после расставленного тобой
      знака конца предложения (точка/!/?) или в начале строки — там разрешено сделать первую букву
      заглавной, если по исходному тексту она была строчной;
    - менять порядок слов, перефразировать, заменять слова синонимами;
    - добавлять или убирать слова;
    - объединять, разделять, менять порядок строк или удалять переносы строк — количество строк и
      разбивка по строкам должны остаться АБСОЛЮТНО такими же, как в исходном тексте;
    - добавлять пояснения, комментарии, markdown, кавычки вокруг ответа.

    Единственное, что тебе разрешено — вставить, удалить или заменить символ пунктуации между
    буквами/словами, не трогая сами буквы и слова.

    Верни ТОЛЬКО исправленный текст, той же построчной структурой, что и исходный.
    """

private val WORD_TOKEN_RE = Regex("[0-9A-Za-zА-Яа-яЁё]+")

// Пословная реконструкция строки после LLM: маленькие модели на практике всё равно меняют
// регистр и/или "исправляют" опечатки при просьбе поправить только пунктуацию (промпт-инжиниринг
// в FIX_PUNCTUATION_SYSTEM_PROMPT это не убирает полностью). Идём по словам построчно и ПОЗИЦИОННО
// сверяем с исходной строкой без учёта регистра: слово совпало без учёта регистра - берём версию
// LLM (регистр меняться МОГ, буквы - нет); не совпало (LLM поменял буквы слова, а не только
// регистр) - откатываем именно это слово к исходному. Все пробелы/пунктуация между словами и по
// краям строки берутся из ответа LLM целиком, как есть - так сохраняются его правки пунктуации,
// даже на строке, где часть слов пришлось откатить.
internal fun reconcilePunctuationLine(
    origLine: String,
    correctedLine: String,
): String {
    val origWords = WORD_TOKEN_RE.findAll(origLine).map { it.value }.toList()
    val correctedMatches = WORD_TOKEN_RE.findAll(correctedLine).toList()
    // Число слов не совпало (LLM добавил/убрал/склеил слово) - строке в целом доверять нельзя.
    if (origWords.size != correctedMatches.size) return origLine

    val sb = StringBuilder()
    var cursor = 0
    correctedMatches.forEachIndexed { i, match ->
        sb.append(correctedLine, cursor, match.range.first)
        val origWord = origWords[i]
        val correctedWord = match.value
        sb.append(if (correctedWord.equals(origWord, ignoreCase = true)) correctedWord else origWord)
        cursor = match.range.last + 1
    }
    sb.append(correctedLine, cursor, correctedLine.length)
    return sb.toString()
}

// Число строк LLM должно сохранять один в один (см. промпт) - строка без пары в ответе LLM
// откатывается к исходной целиком, дальше построчно работает reconcilePunctuationLine.
internal fun enforcePunctuationOnly(
    original: String,
    corrected: String,
): String {
    val originalLines = original.split("\n")
    val correctedLines = corrected.split("\n")
    return originalLines
        .mapIndexed { i, origLine ->
            val correctedLine = correctedLines.getOrNull(i) ?: return@mapIndexed origLine
            reconcilePunctuationLine(origLine, correctedLine)
        }.joinToString("\n")
}

/**
 * LLM-корректор текста песни (AI-редактор текста в SubsEdit.vue) — через LM Studio (см.
 * [LmStudioService]), тот же сервер и клиент, что и у [ScraperAgent] для поиска текстов песен.
 */
object TextCorrectorAgent {
    fun fixSpelling(text: String): String? = LmStudioService.chat(FIX_SPELLING_SYSTEM_PROMPT, text)

    fun fixPunctuation(text: String): String? =
        LmStudioService.chat(FIX_PUNCTUATION_SYSTEM_PROMPT, text)?.let { enforcePunctuationOnly(text, it) }
}
