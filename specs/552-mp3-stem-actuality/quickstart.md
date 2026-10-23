# Quickstart: проверка фикса

## Предусловия

- JDK 17, Gradle wrapper, `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle`.
- Локальный `karaoke-app` для ручной проверки (опционально): контейнер `karaoke-app` на `localhost:8898`.

## 1. Юнит-тесты

```bash
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests '*HealthReportMp3ActualityTest*'
```

Ожидание: тест зелёный; падает, если storage-only тип снова начнёт считать дисковую копию эталоном.

## 2. Компиляция и линтер

```bash
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:ktlintCheck
```

## 3. Ручной сценарий (после деплоя на admin-машину)

1. Взять песню со статусом 6, у которой mp3-стемы есть в LOCAL и REMOTE и совпадают по размеру.
2. Убедиться, что дисковой копии нет (или удалить её).
3. Открыть плеер песни в админке (создастся дисковая копия VBR).
4. Пересчитать HR (`POST /api/song/healthReportList?id=<id>`).
5. Ожидание: для `MP3_ACCOMPANIMENT`/`MP3_VOCAL` (и других mp3) нет записей об актуальности.

## 4. Негативная проверка

1. Взять песню, где LOCAL и REMOTE объекты одного типа различаются по размеру (или подменить размер в кеше/объекте на стенде).
2. Пересчитать HR.
3. Ожидание: WARNING «Размеры файлов в локальном и удалённом хранилищах различаются», без действий.

## 5. Проверка отсутствия регрессии для обычных типов

Типы с `LOCAL_FILESYSTEM` (например, `AUDIO_SONG`, `PICTURE_ALBUM`) должны по-прежнему сравнивать диск с хранилищем; это покрыто тестом `diskCopyIsAuthoritative`.
