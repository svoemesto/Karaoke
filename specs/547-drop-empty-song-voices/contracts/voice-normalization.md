# Контракт: чистая функция нормализации голосов

**Файл**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/VoiceNormalization.kt`
**Спека**: [../spec.md](../spec.md) (FR-001…FR-006, FR-009, FR-012)
**Модель**: [../data-model.md](../data-model.md)

Контракт описывает **поведение**, имена приведены как соглашение для плана; сигнатуры
могут уточняться при реализации без изменения семантики.

## 1. Функция нормализации

```kotlin
fun normalizeVoices(
    texts: List<String>,
    markers: List<List<SourceMarker>>,
): VoiceNormalization?
```

| Параметр | Тип | Смысл |
|---|---|---|
| `texts` | `List<String>` | тексты по голосам (индекс = номер голоса) |
| `markers` | `List<List<SourceMarker>>` | маркеры по голосам |

| Возврат | Смысл |
|---|---|
| `VoiceNormalization` | нормализованный набор + `removedIndices` + `changed` |
| `null` | данные не пригодны для нормализации — вызывающий ничего не меняет (FR-012) |

Функция **чистая**: не читает БД, не пишет в лог, не меняет аргументы.

## 2. Семантика

1. Диапазон индексов: `0 .. max(texts.size, markers.size) - 1`.
2. `empty(i) = texts.getOrNull(i).orEmpty().isBlank() && markers.getOrNull(i).orEmpty().isEmpty()`.
3. Индекс `0` сохраняется всегда (FR-004).
4. Индекс `i > 0` с `empty(i) = true` удаляется (FR-001).
5. Порядок оставшихся сохраняется; результат нумеруется `0..N-1` (FR-003).
6. `changed = true` ⇔ `removedIndices.isNotEmpty()`.
7. Если `changed = true`, `texts.size == markers.size` в результате (дополнение `""` /
   `emptyList()`); если `changed = false`, результат эквивалентен входу (I5).
8. `mapVoice(old)` — старый индекс → новый; `null`, если голос удалён; для индекса вне
   диапазона — `null`.

## 3. Парсеры полей песни

```kotlin
fun parseVoiceTexts(raw: String?): List<String>?          // null = не разобрать
fun parseVoiceMarkers(raw: String?): List<List<SourceMarker>>?  // null = не разобрать
```

| Вход | Результат |
|---|---|
| `null`, `""` | `emptyList()` (данных нет) |
| JSON-массив строк | `List<String>` |
| JSON-массив массивов `SourceMarker` | `List<List<SourceMarker>>` |
| плоский массив `SourceMarker` (легаси) | `listOf(<этот массив>)` — один голос |
| иной/битый JSON | `null` — нормализация пропускается |

Парсеры **не** повторяют сайд-эффект `Song.sourceMarkersList` (запись поля + `saveToDb`
при ошибке) — это осознанное отличие (research R2).

## 4. Примеры (обязательные тест-кейсы)

| # | Вход | Ожидание |
|---|---|---|
| 1 | `["A"], [[]]` (один пустой голос) | `changed=false`, без изменений |
| 2 | `[""], [[]]` | `changed=false` (первый не удаляется) |
| 3 | `["A",""], [[M],[]]` | `changed=true`, `texts=["A"]`, `removedIndices=[1]`, `mapVoice(1)=null` |
| 4 | `["A","", "B"], [[M],[],[M2]]` | `texts=["A","B"]`, `removedIndices=[1]`, `mapVoice(2)=1` |
| 5 | `["A","   \n\t", "B"]` | второй пуст по пробелам → удалён |
| 6 | `["A","text", ""], [[M],[],[M2]]` | второй НЕ пуст (есть текст) → `changed=false` |
| 7 | `["A",""],[ [M], [marker] ]` | второй голос с маркером, текста нет → не пуст |
| 8 | `["A","", "B",""], [[M],[],[M2],[]]` | удалены 1 и 3 → `texts=["A","B"]` |
| 9 | `["A","B"], [[M]]` (маркеров меньше) | второй не пуст (текст) → `changed=false` |
| 10 | `["A"], [[M],[marker]]` (маркеров больше) | второй с маркером → не пуст |
| 11 | нормализованный результат повторно | `changed=false` (идемпотентность, I4) |
| 12 | битый JSON в `parseVoiceMarkers` | `null` |

## 5. Гарантии

- Не бросает исключений на любых входных данных (в т.ч. битых) — только `null`.
- Сложность `O(total_markers + voices)`.
- Не зависит от `Song`, БД, окружения → тестируется юнит-тестом.
