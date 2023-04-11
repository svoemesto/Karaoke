package model

import java.io.Serializable
import kotlin.math.absoluteValue

enum class MusicChord(val text: String, val names: List<String>, val intervals: List<MusicInterval>) : Serializable {
    X (text = "мажорный аккорд (мажорное трезвучие)",
        names = listOf("","M","maj"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.PERFECT_FIFTH
        )
    ),
    Xm (text = "минорный аккорд (минорное трезвучие)",
        names = listOf("m","-","min"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MINOR_THIRD,
            MusicInterval.PERFECT_FIFTH
        )
    ),
    Xaug (text = "увеличенный аккорд (увеличенное трезвучие)",
        names = listOf("+","aug"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.MINOR_SIXTH
        )
    ),
    Xdim (text = "уменьшенный аккорд (уменьшенное трезвучие)",
        names = listOf("dim"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MINOR_THIRD,
            MusicInterval.AUGMENTED_FOURTH,
        )
    ),
    X7 (text = "доминантсептаккорд",
        names = listOf("7","dom7"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MINOR_SEVENTH
        )
    ),
    X7a5 (text = "доминантсептаккорд с повышенной квинтой",
        names = listOf("7+5","7#5","7/5#","7/5+","75#","75+","aug7"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.MINOR_SIXTH,
            MusicInterval.MINOR_SEVENTH
        )
    ),
    Xm7 (text = "малый минорный септаккорд",
        names = listOf("m7","-7"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MINOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MINOR_SEVENTH
        )
    ),
    Xm7a (text = "большой минорный септаккорд",
        names = listOf("m7+","-maj7","m#7","m+7","m/maj7","min/maj/","mmaj7"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MINOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MAJOR_SEVENTH
        )
    ),
    Xm7d5 (text = "полууменьшенный септаккорд",
        names = listOf("m7-5","m7/5-","m75-","m75b","m7b5"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MINOR_THIRD,
            MusicInterval.AUGMENTED_FOURTH,
            MusicInterval.MINOR_SEVENTH
        )
    ),
    Xdim7 (text = "уменьшенный септаккорд",
        names = listOf("dim7","m6/5-","o7"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MINOR_THIRD,
            MusicInterval.AUGMENTED_FOURTH,
            MusicInterval.MAJOR_SIXTH
        )
    ),
    X6 (text = "мажорный аккорд (мажорное трезвучие) с большой секстой",
        names = listOf("6"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MAJOR_SIXTH
        )
    ),
    Xm6 (text = "минорный аккорд (минорное трезвучие) с большой секстой",
        names = listOf("m6"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MINOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MAJOR_SIXTH
        )
    ),
    X7d5 (text = "доминантсептаккорд с пониженной квинтой",
        names = listOf("7-5","7/5-","7/5b","75-","75b"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.AUGMENTED_FOURTH,
            MusicInterval.MINOR_SEVENTH
        )
    ),
    X69 (text = "мажорный аккорд (мажорное трезвучие) с секстой и ноной",
        names = listOf("6/9"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MAJOR_SIXTH,
            MusicInterval.MAJOR_NINTH
        )
    ),
    X9 (text = "большой нонаккорд",
        names = listOf("9"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MINOR_SEVENTH,
            MusicInterval.MAJOR_NINTH
        )
    ),
    X7d9 (text = "малый нонаккорд",
        names = listOf("7-9","-9","9-"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MINOR_SEVENTH,
            MusicInterval.MINOR_NINTH
        )
    ),
    Xmaj9 (text = "нонаккорд на основе большого мажорного септаккорда",
        names = listOf("maj9"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MAJOR_SEVENTH,
            MusicInterval.MAJOR_NINTH
        )
    ),
    X11 (text = "ундецимаккорд на основе доминантсептаккорда",
        names = listOf("11"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MINOR_SEVENTH,
            MusicInterval.MAJOR_NINTH,
            MusicInterval.PERFECT_ELEVENTH
        )
    ),
    Xm11 (text = "ундецимаккорд на основе малого минорного септаккорда",
        names = listOf("m11"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MINOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MINOR_SEVENTH,
            MusicInterval.MAJOR_NINTH,
            MusicInterval.PERFECT_ELEVENTH
        )
    ),
    X13 (text = "терцдецимаккорд на основе доминантсептаккорда",
        names = listOf("13"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MINOR_SEVENTH,
            MusicInterval.MAJOR_NINTH,
            MusicInterval.MINOR_FOURTEENTH
        )
    ),
    Xmaj13 (text = "терцдецимаккорд на основе большого мажорного септаккорда",
        names = listOf("maj13"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MAJOR_SEVENTH,
            MusicInterval.MAJOR_NINTH,
            MusicInterval.MINOR_FOURTEENTH
        )
    ),
    Xsus4 (text = "мажорное трезвучие с чистой квартой вместо терции",
        names = listOf("sus4","+3","4","m4","sus"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.PERFECT_FOURTH,
            MusicInterval.PERFECT_FIFTH
        )
    ),
    Xm69 (text = "минорный аккорд (минорное трезвучие) с секстой и ноной",
        names = listOf("m6/9"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MINOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MAJOR_SIXTH,
            MusicInterval.MAJOR_NINTH
        )
    ),
    X5 (text = "квинт аккорд (power-аккорд)",
        names = listOf("5"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.PERFECT_FIFTH
        )
    ),
    X7a9 (text = "нонаккорд на основе доминантсептаккорда с повышенной ноной",
        names = listOf("7+9"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MINOR_SEVENTH,
            MusicInterval.MINOR_TENTH
        )
    ),
    X7a11 (text = "доминантсептаккорд с повышенной ундецимой",
        names = listOf("7+11","7-5","7/5-","7/5b","75-","75b"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MINOR_SEVENTH,
            MusicInterval.PERFECT_TWELFTH
        )
    ),
    X7d5a9 (text = "большой нонаккорд с пониженной квинтой и увеличенной ноной",
        names = listOf("7-5(+9)"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.AUGMENTED_FOURTH,
            MusicInterval.MINOR_SEVENTH,
            MusicInterval.MINOR_TENTH
        )
    ),
    X7sus4 (text = "доминантсептаккорд с квартой вместо терции",
        names = listOf("7sus4","11","7/4","m7/4"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.PERFECT_FOURTH,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MINOR_SEVENTH
        )
    ),
    X9d5 (text = "большой нонаккорд с пониженной квинтой",
        names = listOf("9-5"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.AUGMENTED_FOURTH,
            MusicInterval.MINOR_SEVENTH,
            MusicInterval.MAJOR_NINTH,
        )
    ),
    X9a11 (text = "большой нонаккорд с повышенной ундецимой",
        names = listOf("9+11"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MINOR_SEVENTH,
            MusicInterval.MAJOR_NINTH,
            MusicInterval.PERFECT_TWELFTH
        )
    ),
    X9sus4 (text = "большой нонаккорд с квартой вместо терции",
        names = listOf("9sus4"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.PERFECT_FOURTH,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MINOR_SEVENTH,
            MusicInterval.MAJOR_NINTH
        )
    ),
    X13d9 (text = "терцдецимаккорд на основе доминантсептаккорда с малой ноной",
        names = listOf("13-9"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MINOR_SEVENTH,
            MusicInterval.MINOR_NINTH,
            MusicInterval.MINOR_FOURTEENTH
        )
    ),
    X13sus4 (text = "терцдецимаккорд на основе доминантсептаккорда с квартой вместо терции",
        names = listOf("13sus4"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.PERFECT_FOURTH,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MAJOR_SIXTH,
            MusicInterval.MAJOR_NINTH,
            MusicInterval.MINOR_FOURTEENTH
        )
    ),
    Xadd9 (text = "мажорный аккорд с большой ноной",
        names = listOf("add9"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MAJOR_NINTH
        )
    ),
    Xmd6 (text = "минорный аккорд (минорное трезвучие) с малой секстой",
        names = listOf("m-6"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MINOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MINOR_SIXTH
        )
    ),
    Xm9 (text = "нонаккорд на основе малого минорного септаккорда",
        names = listOf("m9"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MINOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MINOR_SEVENTH,
            MusicInterval.MAJOR_NINTH
        )
    ),
    Xm9d5 (text = "нонаккорд на основе полууменьшенного септаккорда",
        names = listOf("m9-5"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MINOR_THIRD,
            MusicInterval.AUGMENTED_FOURTH,
            MusicInterval.MINOR_SEVENTH,
            MusicInterval.MAJOR_NINTH
        )
    ),
    Xm9a7 (text = "нонаккорд на основе большого минорного септаккорда",
        names = listOf("m9+7"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MINOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MAJOR_SEVENTH,
            MusicInterval.MAJOR_NINTH
        )
    ),
    Xm13 (text = "терцдецимаккорд на основе большого минорного септаккорда",
        names = listOf("m13"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MINOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MINOR_SEVENTH,
            MusicInterval.MAJOR_NINTH,
            MusicInterval.MINOR_FOURTEENTH
        )
    ),
    Xmadd9 (text = "минорный аккорд с большой ноной",
        names = listOf("m(add9)"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MINOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MAJOR_NINTH,
        )
    ),
    Xmaj7 (text = "большой мажорный септаккорд",
        names = listOf("maj7","+7","7+","M7","maj7+"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MAJOR_SEVENTH
        )
    ),
    Xmaj7d5 (text = "большой мажорный септаккорд с пониженной квинтой",
        names = listOf("maj7-5"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.AUGMENTED_FOURTH,
            MusicInterval.MAJOR_SEVENTH
        )
    ),
    Xmaj7a11 (text = "большой мажорный септаккорд с повышенной ундецимой",
        names = listOf("maj7+11"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.PERFECT_FIFTH,
            MusicInterval.MAJOR_SEVENTH,
            MusicInterval.PERFECT_TWELFTH
        )
    ),
    Xsus2 (text = "минорное трезвучие с большой секундой вместо терции",
        names = listOf("sus2"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_SECOND,
            MusicInterval.PERFECT_FIFTH
        )
    ),
    Xa7a9 (text = "нонаккорд с повышенной квинтой и повышенной ноной",
        names = listOf("+7+9"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.MINOR_SIXTH,
            MusicInterval.MINOR_SEVENTH,
            MusicInterval.MINOR_TENTH
        )
    ),
    Xa7d9 (text = "малый нонаккорд с повышенной квинтой",
        names = listOf("+7-9"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.MINOR_SIXTH,
            MusicInterval.MINOR_SEVENTH,
            MusicInterval.MINOR_NINTH
        )
    ),
    Xa9 (text = "большой нонаккорд с повышенной квинтой",
        names = listOf("+9"),
        intervals = listOf(
            MusicInterval.UNISON,
            MusicInterval.MAJOR_THIRD,
            MusicInterval.MINOR_SIXTH,
            MusicInterval.MINOR_SEVENTH,
            MusicInterval.MAJOR_NINTH
        )
    );

    fun getNotes(rootNote: MusicNote): List<Pair<MusicNote, Int>> =
        this.intervals.map { it.getMusicNote(rootNote) }

    // Струна - лад - номер ноты в списке нот аккорда
    fun getFingerboard(rootNote: MusicNote, initFret: Int = 0, capo: Int = 0, withSmallBarre: Boolean = true): List<Fingerboard> {

//        var newIndexNote = MusicNote.values().indexOf(startRootNote) - capo
//        if (newIndexNote < 0) newIndexNote = MusicNote.values().size + newIndexNote
//        val rootNote = MusicNote.values()[newIndexNote]
//        var initFret = rootNote.defaultRootFret
//        if (initFret < 0) initFret = 0

        val preResult: MutableList<Fingerboard> = mutableListOf() // Предварительный результат
        var result: MutableList<Fingerboard> = mutableListOf() // Результат
        val notes = getNotes(rootNote).map { it.first } // Список нот аккорда, начиная с тоники
        val maxDiffBetweenFrets = 3 + if (initFret == 0) 1 else 0 // Максимальное расстояние между ладами (4 для 0-го лада, 3 для остальных)
        val guitarStrings = GuitarString.values() // Гитарные струны

        guitarStrings.forEach { guitarString -> // Цикл по струнам
            var foundNoteInString = false // Найдена ли нота на струне
            var fretForNote = 0 // Лад для ноты
            var indexNoteInChord = 0 // Индекс ноты в аккорде
            for (fret in initFret until Integer.min(guitarString.fretNextString, maxDiffBetweenFrets) + initFret) { // Цикл от начального лада до лада с нотой следующей струны (не включая)
                val (noteInFret, _, _) = guitarString.getNote(fret) // Получаем ноту текущего лада цикла
                if (noteInFret in notes) { // Если эта нота есть среди нот аккорда
                    foundNoteInString = true // Считаем что ноту на струне нашли
                    fretForNote = fret // Запоминаем лад, на котором нашли эту ноту
                    indexNoteInChord = notes.indexOf(noteInFret) // Запоминаем индекс этой ноты в массиве нот аккорда

                    if (initFret != 0) {
                        preResult.add(Fingerboard(
                            guitarString = guitarString,
                            rootFret = initFret,
                            fret = fretForNote,
                            finger = 0,
                            indexNote = indexNoteInChord
                        ))
                    } else {
                        break
                    }


//                    break // Выходим из цикла
                    // Тут проблема. Мы выходим при нахождении первой ноты на струне, а там могут быть еще.
                }
            }
            if (initFret == 0 && foundNoteInString) { // Если после прохода цикла нота найдена - добавляем запись в массив предварительного результата
                preResult.add(Fingerboard(
                    guitarString = guitarString,
                    rootFret = initFret,
                    fret = fretForNote,
                    finger = 0,
                    indexNote = indexNoteInChord
                ))
            }
        }

        var fretMap = preResult.groupBy { it.fret }.toList()
        fretMap = fretMap.sortedBy { it.first }
        var wasSmallBarre = false
        var currFinger = 1

        if (fretMap[0].first != initFret) return emptyList()

        fretMap.forEach { (fret, fingerboards) ->
            if (fret == initFret) {
                fingerboards.forEach {
                    it.barre = true
                    it.finger = if (initFret == 0) 0 else 1
                }
                currFinger += if (initFret == 0) 0 else 1
                result.addAll(fingerboards)
            } else {
                if (fret - initFret > maxDiffBetweenFrets) {
                    fingerboards.forEach {
                        it.muted = true
                    }
                    result.addAll(fingerboards)
                } else {
                    if (fingerboards.isNotEmpty()) {
                        val sortedFingerboards = fingerboards.sortedByDescending { it.guitarString.number }
                        if (withSmallBarre && sortedFingerboards.size > 1) {
                            var barre = true
                            for (indexFingerboard in 0 until sortedFingerboards.size-1) {
                                if ((sortedFingerboards[indexFingerboard+1].guitarString.number - sortedFingerboards[indexFingerboard].guitarString.number).absoluteValue != 1) {
                                    barre = false
                                    break
                                }
                            }
                            if (barre) {
                                sortedFingerboards.forEach { fingerboard ->
                                    fingerboard.finger = currFinger
                                    fingerboard.barre = true
                                }
                                wasSmallBarre = true
                                currFinger += 1
                            } else {
                                sortedFingerboards.forEach { fingerboard ->
                                    if (initFret == 0 && fingerboard.guitarString.number == 6 && fingerboard.fret <3) {
                                        fingerboard.muted = true
                                    } else {
                                        fingerboard.finger = currFinger
                                        currFinger += 1
                                    }

                                }
                            }
                        } else {
                            sortedFingerboards.forEach { fingerboard ->
                                fingerboard.finger = currFinger
                                currFinger += 1
                            }
                        }
                        result.addAll(sortedFingerboards)
                    }
                }

            }
        }

        if (currFinger > 5 && result.first { it.guitarString == GuitarString.GS6 }.fret > initFret) {
            result.first { it.guitarString == GuitarString.GS6 }.fret = initFret
            result.first { it.guitarString == GuitarString.GS6 }.muted = true
            val f6 = result.first { it.guitarString == GuitarString.GS6 }.finger - 1
            result.first { it.guitarString == GuitarString.GS6 }.finger = 1
            result.forEach { if (it.finger > f6) it.finger = it.finger - 1 }
            currFinger -= 1
        }

        if (currFinger == 6 && result.first { it.guitarString == GuitarString.GS5 }.fret > initFret) {
            result.first { it.guitarString == GuitarString.GS5 }.fret = initFret
            result.first { it.guitarString == GuitarString.GS5 }.muted = true
            val f5 = result.first { it.guitarString == GuitarString.GS5 }.finger
            if (f5 < 5) {
                result.first { it.finger == 5 }.finger = f5
            }
            result.first { it.guitarString == GuitarString.GS5 }.finger = 1
            currFinger -= 1
        }

        notes.forEachIndexed{ indexNote, _ ->
            if (indexNote !in result.map { it.indexNote }.toList()) return emptyList()
        }

        GuitarString.values().forEach { gs ->
            if (gs !in result.map { it.guitarString }) {
                result.add(
                    Fingerboard(
                        guitarString = gs,
                        rootFret = initFret,
                        fret = initFret,
                        finger = 0,
                        indexNote = -1,
                        muted = true
                    )
                )
            }
        }

        // На данном этапе надо посмотреть, есть ли задвоение струн
        // Если есть - посмотреть, какие ноты аккорда зажаты на этой задвоенной струне
        // Посмотреть, есть ли такие же ноты на остальных струнах
        // И оставить ту задвоенную струну, на которой уникальная нота

        // Отфильтровываем задвоенные струны
        val groupResult = result.groupBy { it.guitarString }.filter { it.value.size > 1 }
        groupResult.forEach { doubleResult ->
            doubleResult.value.forEach { doubleFingerboard ->
                val foundDouble = result.any { it.guitarString != doubleResult.key && it.indexNote == doubleFingerboard.indexNote }
                if (foundDouble) {
                    result.remove(result.first { it.guitarString == doubleResult.key && it.indexNote == doubleFingerboard.indexNote })
                }
            }
        }

        val finalResult: MutableList<Fingerboard> = mutableListOf()
        GuitarString.values().forEach { gs ->
            result.filter { it.guitarString == gs }.maxByOrNull { it.fret }?.let { finalResult.add(it) }
        }

        if (finalResult.size != 6) return emptyList()

        result = finalResult.sortedBy { it.guitarString.number }.toMutableList()
        for (i in 1..4) {
            if (result[i].muted && !result[i-1].muted && !result[i+1].muted) return emptyList()
        }

        // Если мы на нулевом ладу, две струны на первом и 2 на 3 - отказать
        if (initFret == 0) {
            if (
                (result.filter { it.fret == 1 }.size > 0 &&
                result.filter { it.fret == 2 }.size == 0 &&
                result.filter { it.fret == 3 }.size > 1) ||
                (result.filter { it.fret == 1 }.size > 1 &&
                result.filter { it.fret == 2 }.size == 0 &&
                result.filter { it.fret == 3 }.size > 0) ||
                (result.filter { it.fret == 1 }.size > 1 &&
                result.filter { it.fret == 2 }.size == 1 &&
                result.filter { it.fret == 3 }.size == 0)
            ) return emptyList()
        }


        if (currFinger == 4 && withSmallBarre && wasSmallBarre) {
            return getFingerboard(rootNote, initFret, capo,false)
        } else {
            if (currFinger > 5) {
                return emptyList()
            } else {
                return result
            }
        }

    }

    companion object {

        fun getChordNote(chordName: String): Pair<MusicChord?,MusicNote?> {
            val noteName: String = if (chordName.isNotEmpty()) chordName[0].toString() + if (chordName.length > 1 && chordName[1] in "♭♯#b") chordName[1] else "" else ""
            val chordNameInNames = chordName.substring(noteName.length)
            return Pair(
                MusicChord.values().firstOrNull { it.names.contains(chordNameInNames) },
                MusicNote.getNote(noteName)
            )
        }

    }

}

data class Fingerboard(
    val guitarString: GuitarString,
    val rootFret: Int,
    var fret: Int,
    var finger: Int,
    val indexNote: Int,
    var barre: Boolean = false,
    var muted: Boolean = false
) : Serializable {
    override fun toString(): String {
        var result = "струна ${guitarString.number} лад ${rootFret} "
        for (i in 0 until 4) {
            var str = ""
            if (i == 0) {
                if (muted) {
                    str ="|-x-"
                } else {
                    str ="|-${if (rootFret == 0) ":" else "1"}-"
                }
            } else {
                if (i == fret - rootFret) {
                    if (!muted) {
                        str ="|-${finger}-"
                    } else {
                        str ="|---"
                    }
                } else {
                    str ="|---"
                }
            }
            result += str
        }
        return result
    }
}