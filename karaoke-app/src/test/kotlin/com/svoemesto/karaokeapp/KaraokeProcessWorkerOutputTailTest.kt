package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ИНЦИДЕНТ 2026-11-03 (песня 30428): контейнер demucs при занятом GPU печатает
 * `torch.cuda.OutOfMemoryError` и **выходит с кодом 0** — шаг выглядел «успешным», и
 * оператор не видел причину. Хвост вывода docker-шага должен попадать в описание
 * задания (его видно в админке).
 */
class KaraokeProcessWorkerOutputTailTest {
    @Test
    fun `хвост вывода дописывается в описание шага`() {
        val result =
            KaraokeProcessWorker.appendOutputTail(
                "Демукс 2",
                "torch.cuda.OutOfMemoryError: CUDA out of memory",
            )

        assertTrue(result.contains("Демукс 2"), "исходное описание сохранено")
        assertTrue(result.contains("OutOfMemoryError"), "хвост вывода в описании")
    }

    @Test
    fun `пустой вывод не меняет описание`() {
        assertEquals("Демукс 2", KaraokeProcessWorker.appendOutputTail("Демукс 2", ""))
        assertEquals("", KaraokeProcessWorker.appendOutputTail(null, ""))
    }

    @Test
    fun `повторный вызов не размножает блок вывода`() {
        val first = KaraokeProcessWorker.appendOutputTail("Демукс 2", "ошибка 1")
        val second = KaraokeProcessWorker.appendOutputTail(first, "ошибка 2")

        assertEquals(1, second.split("[вывод]:").size - 1, "блок вывода один")
        assertTrue(second.contains("ошибка 2"), "новый хвост виден")
    }
}
