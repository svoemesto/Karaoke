#!/usr/bin/env python3
"""
tools/auto-kdoc-quality.py

Заменяет базовый авто-KDoc на качественный для указанных классов.
Используется для KDoc Pass 4+ — заменяет KDoc по словарю CLASS_DESCRIPTIONS.

Логика:
1. Для каждого класса в CLASS_DESCRIPTIONS — найти файл (по имени класса).
2. Заменить базовый KDoc (одна строка `* Класс <Name>.` или `* Перечисление ...`) на
   качественный с описанием.
3. Если KDoc не найден — добавить перед class/object.
"""
import os
import re
import sys
from pathlib import Path

CLASS_DESCRIPTIONS = {
    # === Mko* (MLT Karaoke Object — визуальные слои karaoke-видео) ===
    'MkoAudio': 'Producer для аудио-слоя (vocals/accompaniment/mix/source) в karaoke-видео. Создаёт MLT-блоки для загрузки FLAC-стемов и их синхронизации с таймлайном.',
    'MkoVoices': 'Обёртка для всех голосов песни (multitrack). Создаёт родительский `<multitrack>`-узел с голосами как вложенными `<playlist>`.',
    'MkoMelodyNote': 'Producer для одной ноты мелодии (знак ноты на грифе + лейбл). Создаёт MLT-блоки для отображения позиции ноты в текущий момент времени.',
    'MkoCounters': 'Producer для счётчиков (1/2/3...) — показывает текущее слово/строку песни в углу кадра. Создаёт MLT-блоки для динамического текста со счётчиком.',
    'MkoWatermark': 'Producer для водяного знака («ДЕМО», «ПОДПИСКА», и т.п.) в углу видео. Создаёт полупрозрачный текстовый overlay с настраиваемой позицией.',
    'MkoSepar': 'Producer для разделителя между строками текста (горизонтальная линия). Создаёт MLT-блоки для визуального отделения куплетов/припевов.',
    'MkoSplashStart': 'Producer для заставки в начале видео (логотип, название, обложка). Создаёт MLT-блоки, видимые первые N секунд, затем исчезающие.',
    'MkoLine': 'Producer для одной строки текста песни. Содержит слоги с таймингами. Создаёт MLT-блоки для отображения текста с karaoke-подсветкой.',
    'MkoFingerboard': 'Producer для грифа гитары внизу кадра. Создаёт MLT-блоки для отображения позиций аккордов на грифе в текущий момент времени.',
    'MkoElement': 'Базовый элемент визуального слоя (родитель для текста, аккордов, нот). Содержит общие параметры: позиция, цвет, шрифт, transform-properties.',
    'MkoLineTrack': 'Трек для отображения всех строк текста песни. Создаёт MLT-`<tractor>` с несколькими `MkoLine` как вложенными элементами.',
    'MkoChordPictureElement': 'Элемент аккордной картинки (диаграмма грифа + название аккорда). Один аккорд в текущий момент времени.',
    'MkoFaderText': 'Producer для фейд-эффекта на тексте (появление/исчезновение). Создаёт MLT-блоки с alpha-анимацией.',
    'MkoFillcolorSongtexts': 'Producer для заливки цветом фона за текстом песни. Создаёт MLT-блоки с прямоугольником заданного цвета под текстом.',
    'MkoChordPictureLines': 'Набор строк аккордной картинки (вертикальные позиции грифа). Создаёт MLT-блоки с линиями-разделителями.',
    'MkoSongText': 'Producer для основного текста песни (все строки, синхронизированные по времени). Главный визуальный слой — отображает karaoke-текст с подсветкой текущего слога.',
    'MkoChordPictureLine': 'Одна линия аккордной картинки (горизонтальная позиция грифа). Часть аккордной диаграммы.',
    'MkoScrollerTrack': 'Трек-скроллер для прокрутки текста/аккордов. Создаёт MLT-блоки с translate-анимацией для плавной прокрутки длинного контента.',
    'MkoChordPictureImage': 'Изображение аккордной диаграммы (PNG/JPG из MinIO). Создаёт MLT-блоки с producer для рендера картинки аккорда.',
    'MkoLines': 'Набор строк текста с общей позицией/стилем. Содержит все MkoLine текущего голоса как вложенные элементы.',
    'MkoString': 'Одна струна грифа гитары. Создаёт MLT-блоки для горизонтальной линии струны на грифе.',
    'MkoVoice': 'Один голос песни (vocal/accompaniment/mix). Содержит настройки рендера для конкретного голоса (цвет, шрифт, отступ).',
    'MkoFlash': 'Producer для вспышки при смене строки/куплета. Создаёт MLT-блоки с короткой белой вспышкой (50-100ms).',
    'MkoProgress': 'Producer для прогресс-бара внизу видео. Создаёт MLT-блоки с динамической шириной progress-bar.',
    'MkoChordPictureFader': 'Producer для фейд-эффекта на аккордной картинке. Создаёт MLT-блоки с alpha-анимацией появления/исчезновения.',
    'MkoChordPictureLineTrack': 'Трек для отображения всех линий аккордной картинки. Создаёт MLT-`<tractor>` с несколькими MkoChordPictureLine.',

    # === Services ===
    'StorageApiClient': 'HTTP-клиент для удалённого MinIO через karaoke-web (proxy). Используется в karaoke-web, где нет прямого доступа к MinIO. Поддерживает upload/download с прогрессом через `CountingInputStream`.',

    # === LLM ===
    'SearchTool': 'Web-поисковик через SearXNG (`SEARXNG_BASE_URL`) — собирает 5-10 кандидатов-страниц по тексту запроса. Используется в `LyricsFinderService` для первичного отбора источников.',
    'Tools': 'Набор LangChain4j `@Tool`-методов для LLM-агентов (ScraperAgent, SearchAgent): парсинг HTML, извлечение текста/аккордов, нормализация строк. Используется в LLM-pipeline `llm-lyrics-search.md`.',
}


def find_file_for_class(class_name, root='karaoke-app/src/main/kotlin'):
    """Находит файл, содержащий объявление класса class_name."""
    for r, _, files in os.walk(root):
        for f in files:
            if not f.endswith('.kt'):
                continue
            path = os.path.join(r, f)
            content = open(path).read()
            # Ищем объявление класса
            if re.search(rf'^(class|object|interface|enum class|sealed class|data class|abstract class)\s+{re.escape(class_name)}\b', content, re.M):
                return path
    return None


def replace_kdoc(path, class_name, description):
    """Заменяет KDoc для класса на качественный."""
    with open(path) as f:
        content = f.read()
    lines = content.split('\n')

    # Находим объявление класса
    decl_pattern = re.compile(rf'^(public\s+)?(class|object|interface|enum class|sealed class|data class|abstract class)\s+{re.escape(class_name)}\b')
    decl_idx = None
    for i, line in enumerate(lines):
        if decl_pattern.match(line):
            decl_idx = i
            break
    if decl_idx is None:
        return False

    # Находим начало текущего KDoc
    kdoc_start = None
    for j in range(decl_idx - 1, max(-1, decl_idx - 10), -1):
        if j < 0:
            break
        line_j = lines[j]
        if line_j.strip().endswith('*/'):
            # Находим /** выше
            for k in range(j, max(-1, j - 10), -1):
                if k < 0:
                    break
                if lines[k].lstrip().startswith('/**'):
                    kdoc_start = k
                    break
            break
        if line_j.strip() and not line_j.strip().startswith('//') and not line_j.strip().startswith('*'):
            # Достигли не-KDoc строки — выходим
            break

    # Генерируем новый KDoc
    new_kdoc_lines = [
        '/**',
        f' * {description}',
        ' *',
        ' * @see docs/features/mlt-generator.md',
        ' */',
    ]

    if kdoc_start is not None:
        # Находим конец старого KDoc
        kdoc_end = None
        for j in range(kdoc_start, decl_idx):
            if lines[j].strip().endswith('*/'):
                kdoc_end = j
                break
        if kdoc_end is not None:
            # Заменяем
            new_lines = lines[:kdoc_start] + new_kdoc_lines + lines[kdoc_end + 1:]
            new_content = '\n'.join(new_lines)
            with open(path, 'w') as f:
                f.write(new_content)
            return True
    return False


def main():
    for class_name, description in CLASS_DESCRIPTIONS.items():
        path = find_file_for_class(class_name)
        if not path:
            print(f"  NOT FOUND: {class_name}")
            continue
        if replace_kdoc(path, class_name, description):
            print(f"  OK: {class_name} ({path})")
        else:
            print(f"  FAIL: {class_name} ({path})")


if __name__ == '__main__':
    main()
