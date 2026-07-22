"""
Слоговая разбивка текста - порт того же алгоритма, что использует frontend (SubsEdit.vue
getSyllables computed) и backend (WhisperMarkerAligner.kt, karaoke-app), которым была построена
слоговая разметка во всех 15k песнях датасета. Должна давать ИДЕНТИЧНУЮ разбивку - иначе тайминги,
предсказанные align.py, нельзя будет сравнивать 1:1 со слогами ground truth из manifest.jsonl.

Два прохода (как в оригинале, не независимо по слову!):
1. Разбить строку на слоги ПОСЛОВНО обычным regex.
2. ОДИН ОБЩИЙ проход по всей строке: слог без гласной (частицы вроде "в", "с", "к") приклеивается
   к следующему слогу - в том числе через границу слова. См. комментарий в WhisperMarkerAligner.kt
   про исходный баг Kotlin getSyllables(text) (обрабатывал только первое слово при многословном
   входе) - здесь эта ошибка не воспроизводится, слова берём через finditer (все, не только первое).
"""

import re

_SYLLABLE_RE = re.compile(
    r"[ЙЦКНГШЩЗХЪФВПРЛДЖЧСМТЬБQWRTYPSDFGHJKLZXCVBNM-]*"
    r"[ЁУЕЫАОЭЯИЮEUIOAїієѣ]"
    r"[ЙЦКНГШЩЗХЪФВПРЛДЖЧСМТЬБQWRTYPSDFGHJKLZXCVBNM-]*?"
    r"(?=[ЦКНГШЩЗХФВПРЛДЖЧСМТБQWRTYPSDFGHJKLZXCVBNM-]?[ЁУЕЫАОЭЯИЮEUIOAїієѣ]|[ЙYy][АИУЕОEUIOAїієѣ])",
    re.IGNORECASE,
)
_WORD_RE = re.compile(r"\S+")
_VOWELS = set("ЁУЕЫАОЭЯИЮёуеыаоэяиюEUIOAeuioaїієѣ")

# ВАЖНО: здесь НЕЛЬЗЯ чистить пунктуацию (запятые, тире-разделители и т.п.) до разбивки на слоги -
# frontend (SubsEdit.vue getSyllables), которым реально была построена ground-truth разметка во всех
# 15k песнях, тоже её не чистит, и результат (число/содержимое слогов) от этого зависит. Пробовали
# чистить здесь ради устойчивости forced-align (align.py падал на висящей запятой) - это СЛОМАЛО
# соответствие количества слогов с ground truth (см. evaluate.py "расхождение числа слогов" на
# текстах с дефисами). Устойчивость к "мусорным" символам при выравнивании обеспечивается в другом
# месте - align.py:_sanitize_for_vocab фильтрует именно то, что реально идёт в CTC-токенизатор, не
# трогая эту разбивку и её результат.


def _has_vowel(s: str) -> bool:
    return any(c in _VOWELS for c in s)


def _split_word_raw(word: str) -> list[str]:
    parts = _SYLLABLE_RE.sub(lambda m: m.group(0) + " ", word).split(" ")
    parts = [p for p in parts if p != ""]
    return parts if parts else [word]


def split_line_into_words(line: str) -> list[list[str]]:
    """Возвращает список слов, каждое - список слогов (после общего по строке слияния безгласных
    слогов). Одна запись верхнего уровня = одно "целевое слово" для сопоставления с распознаванием."""
    flat: list[list] = []  # [text, word_index]
    for word_index, match in enumerate(_WORD_RE.finditer(line)):
        for syl in _split_word_raw(match.group(0)):
            flat.append([syl, word_index])

    # Условие идентично frontend getSyllables (SubsEdit.vue): последний элемент строки сливается
    # НАЗАД всегда, если без гласной (через ИЛИ, не только буквальный "-") - раньше здесь стояло
    # "И" (только буквальный "-"), это расходилось с frontend и оставляло, например, повисшую
    # запятую в конце строки как самостоятельный "слог" (см. тот же фикс в WhisperMarkerAligner.kt).
    i = 0
    while i < len(flat):
        piece_text, piece_word_index = flat[i]
        if not _has_vowel(piece_text):
            if i != 0 and (i == len(flat) - 1 or piece_text == "-"):
                flat[i - 1][0] += piece_text
                del flat[i]
                i -= 1
            elif i < len(flat) - 2:
                flat[i + 1][0] = piece_text + flat[i + 1][0]
                del flat[i]
                i -= 1
        i += 1

    words: dict[int, list[str]] = {}
    for text, word_index in flat:
        words.setdefault(word_index, []).append(text)
    return [words[k] for k in sorted(words.keys())]


def split_text_into_words(text: str) -> list[list[str]]:
    """Как split_line_into_words, но по многострочному тексту (перенос строки - естественная
    граница, слоги через "\\n" никогда не сливаются - как и в оригинале)."""
    result: list[list[str]] = []
    for line in text.replace("\r\n", "\n").split("\n"):
        if line.strip() == "":
            continue
        result.extend(split_line_into_words(line))
    return result
