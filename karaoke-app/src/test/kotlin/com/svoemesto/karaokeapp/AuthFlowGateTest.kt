package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Регрессия BR-113 (аудит #336, PKG-S2-11): auth-flow имеет конечное ожидание и
 * не переприсваивает глобальный latch.
 *
 * До правки `authLatch.await()` блокировал HTTP-поток Tomcat без таймаута, а
 * `runAuthFlow` переприсваивал глобальный `authLatch` после CAS — `completeAuth()`
 * мог отсчитать «не тот» объект. Тест проверяет контракт [AuthFlowGate]:
 * ровно один flow, `complete()` отпускает текущий latch, ожидание завершается
 * по таймауту, после `finish()` можно запустить следующий flow.
 */
class AuthFlowGateTest {
    @Test
    fun `only one flow at a time and complete releases current latch`() {
        val gate = AuthFlowGate()
        val first = gate.tryStart()
        assertNotNull(first)
        assertNull(gate.tryStart(), "второй flow не должен стартовать параллельно")

        gate.complete()
        assertTrue(gate.await(first!!), "complete() должен отпустить latch текущего flow")

        gate.finish()
        val second = gate.tryStart()
        assertNotNull(second, "после finish() следующий flow должен запускаться")
        gate.complete()
        assertTrue(gate.await(second!!))
    }

    @Test
    fun `await returns false on timeout instead of hanging forever`() {
        val gate = AuthFlowGate(timeoutMs = 30)
        val latch = gate.tryStart()!!
        assertFalse(gate.await(latch), "по таймауту ожидание обязано вернуть false (BR-113)")
    }
}
