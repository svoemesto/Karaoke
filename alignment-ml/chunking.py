"""
Разбивка (текст + слоги-с-таймингом) на короткие сегменты для обучения (train.py) - вместо целой
песни (2-4 минуты, многовато для fine-tuning CTC-модели: риск OOM/медленных шагов на 1B-параметров
модели при 16GB VRAM) режем по паузам МЕЖДУ слогами.

В манифесте нет явных маркеров конца строки/куплета (ExportAlignmentDataset.kt экспортирует только
markertype=syllables) - но для чанкинга это и не нужно: граница между строками/куплетами - это
просто разрыв по времени между СОСЕДНИМИ слогами в общем списке. Если разрыв больше
SILENCE_THRESHOLD_MS - считаем это паузой между строками, режем тут и выбрасываем саму паузу (не
тащим в чанк ни хвост тишины перед следующим слогом, ни от текущего).

Если после такой "умной" резки кусок всё равно длиннее MAX_CHUNK_MS (в песне долго нет пауз -
например быстрый безостановочный текст) - дорезаем его по самому большому из ВНУТРЕННИХ разрывов
(даже если он меньше порога "тишины"), рекурсивно, пока куски не уложатся в лимит - это просто
защита от слишком длинных чанков, не "умная" резка по смыслу.
"""

from __future__ import annotations

from dataclasses import dataclass

from syllables import split_text_into_words

DEFAULT_SILENCE_THRESHOLD_MS = 3000
DEFAULT_MAX_CHUNK_MS = 20_000  # снижено с 30с - меньше пиковая память на самых длинных чанках (16GB VRAM впритык)
DEFAULT_MIN_CHUNK_MS = 1000
DEFAULT_LEAD_PAD_MS = 200
DEFAULT_TAIL_PAD_MS = 800


@dataclass
class Chunk:
    start_ms: int
    end_ms: int
    text: str


def _words_for_syllables(text: str, syllable_count: int) -> list[list[str]] | None:
    """Восстанавливает группировку слогов по словам из исходного текста (та же слоговая разбивка,
    что использовалась при генерации маркеров, см. WhisperMarkerAligner.kt/frontend getSyllables) -
    в манифесте она не сохранена, только плоский список {label, timeMs}. None при расхождении числа
    слогов - как и evaluate.py, в этом случае строку лучше пропустить целиком, а не гадать."""
    words = split_text_into_words(text)
    if sum(len(w) for w in words) != syllable_count:
        return None
    return words


def _split_by_gaps(indices: list[int], times_ms: list[int], threshold_ms: int) -> list[list[int]]:
    if not indices:
        return []
    groups = [[indices[0]]]
    for idx in indices[1:]:
        if times_ms[idx] - times_ms[groups[-1][-1]] > threshold_ms:
            groups.append([idx])
        else:
            groups[-1].append(idx)
    return groups


def _enforce_max_length(group: list[int], times_ms: list[int], max_ms: int) -> list[list[int]]:
    """Жадно копит подряд идущие слоги, режет, как только длительность от начала текущего куска
    превышает max_ms. НЕ пытается искать "лучшее" место реза (это не "умная" резка по паузам, а
    механический потолок длины) - важно, что это просто и не вырождается: попытка резать по
    "самому большому внутреннему разрыву" рекурсивно ломается на равномерных/близких интервалах
    (например, безостановочный речитатив) - при tie-break на первом найденном максимуме рекурсия
    отрезает по одному слогу за раз вместо деления пополам."""
    if not group:
        return []
    result: list[list[int]] = []
    current = [group[0]]
    for idx in group[1:]:
        if times_ms[idx] - times_ms[current[0]] > max_ms:
            result.append(current)
            current = [idx]
        else:
            current.append(idx)
    result.append(current)
    return result


def _merge_short_chunks(
    groups: list[list[int]],
    times_ms: list[int],
    min_ms: int,
    silence_threshold_ms: int,
) -> list[list[int]]:
    """Вырожденно короткие куски (одно слово/восклицание между двумя длинными паузами) почти не
    несут сигнала для обучения - но клеить их с соседом стоит, ТОЛЬКО если между ними нет настоящей
    паузы (граница появилась исключительно из-за принудительной нарезки по max_chunk_ms на
    непрерывном пении, а не из-за тишины между строками/куплетами) - иначе слияние вернуло бы в
    чанк ровно ту длинную тишину, которую вся эта нарезка должна была убрать (см. _split_by_gaps).
    Короткий кусок, у которого оба соседа отделены настоящей паузой (например, одиночное восклицание
    между двумя длинными фрагментами тишины), остаётся коротким - клеить его попросту не с чем."""
    if len(groups) <= 1:
        return groups

    result: list[list[int]] = []
    for group in groups:
        if result:
            gap_ms = times_ms[group[0]] - times_ms[result[-1][-1]]
            prev_duration = times_ms[result[-1][-1]] - times_ms[result[-1][0]]
            if gap_ms <= silence_threshold_ms and prev_duration < min_ms:
                result[-1] = result[-1] + group
                continue
        result.append(group)

    if len(result) > 1:
        gap_ms = times_ms[result[-1][0]] - times_ms[result[-2][-1]]
        last_duration = times_ms[result[-1][-1]] - times_ms[result[-1][0]]
        if gap_ms <= silence_threshold_ms and last_duration < min_ms:
            tail = result.pop()
            result[-1] = result[-1] + tail
    return result


def build_chunks(
    text: str,
    syllables: list,  # manifest.Syllable - объекты с .label и .time_ms, отсортированы по времени
    duration_ms: int,
    silence_threshold_ms: int = DEFAULT_SILENCE_THRESHOLD_MS,
    max_chunk_ms: int = DEFAULT_MAX_CHUNK_MS,
    min_chunk_ms: int = DEFAULT_MIN_CHUNK_MS,
    lead_pad_ms: int = DEFAULT_LEAD_PAD_MS,
    tail_pad_ms: int = DEFAULT_TAIL_PAD_MS,
) -> list[Chunk]:
    if not syllables:
        return []

    words = _words_for_syllables(text, len(syllables))
    if words is None:
        return []

    word_of_syllable = [w_idx for w_idx, word in enumerate(words) for _ in word]
    times_ms = [s.time_ms for s in syllables]
    if any(times_ms[i] > times_ms[i + 1] for i in range(len(times_ms) - 1)):
        # Не монотонно по времени - весь остальной чанкинг молча предполагает порядок = время
        # (см. докстринг модуля). На практике встречается, когда WhisperMarkerAligner.kt подобрал
        # вставке (hasGroundTruth=false) тайминг, "выбивающийся" из её текстовой позиции (известное
        # ограничение NW-выравнивания, см. комментарий у alignWords в WhisperMarkerAligner.kt) - без
        # этой проверки такая строка давала бы чанк с отрицательной длительностью (end_ms < start_ms,
        # см. end_ms = min(duration_ms, ...) ниже), что дальше либо портит обучающий пример пустым
        # аудио, либо роняет CTC loss (T < S). Пропускаем строку целиком - как и при расхождении
        # числа слогов (_words_for_syllables выше), надёжнее не гадать про порядок.
        return []
    indices = list(range(len(syllables)))

    groups = _split_by_gaps(indices, times_ms, silence_threshold_ms)
    groups = [g for group in groups for g in _enforce_max_length(group, times_ms, max_chunk_ms)]
    groups = _merge_short_chunks(groups, times_ms, min_chunk_ms, silence_threshold_ms)

    chunks = []
    for group in groups:
        start_ms = max(0, times_ms[group[0]] - lead_pad_ms)
        # duration_ms - тоже данные из манифеста (см. вызывающую сторону) - на случай, если оно
        # само окажется меньше времени последнего слога группы (не должно происходить в норме, но
        # это отдельное поле, а не производное от times_ms), не допускаем end_ms < start_ms.
        end_ms = max(start_ms, min(duration_ms, times_ms[group[-1]] + tail_pad_ms))
        word_indices = sorted({word_of_syllable[i] for i in group})
        text_chunk = " ".join("".join(words[w]) for w in word_indices)
        chunks.append(Chunk(start_ms=start_ms, end_ms=end_ms, text=text_chunk))
    return chunks
