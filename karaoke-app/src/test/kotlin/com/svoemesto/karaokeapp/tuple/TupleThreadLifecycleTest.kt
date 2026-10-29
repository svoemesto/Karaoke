package com.svoemesto.karaokeapp.tuple

import com.svoemesto.karaokeapp.audit.TestRepoRoot
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 674 (FR-015/FR-018, ADR local-0009): поток кортежа одноразовый, соединение закрывается
 * один раз в `finally`; в `tuple/` нет сырых потоков; exit-code guard остаётся в worker.
 *
 * @see specs/674-kortege-execution/tasks.md T012
 */
class TupleThreadLifecycleTest {
    private val worker: String =
        TestRepoRoot
            .file("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt")
            .readText()

    @Test
    fun `поток кортежа закрывает соединение в finally после цикла`() {
        assertTrue(worker.contains("TupleExecutor("), "цикл кортежа должен идти через TupleExecutor")
        assertTrue(worker.contains("KaraokeProcess.firstRunnableStep") || worker.contains("JdbcTupleStore"))
        assertTrue(worker.contains("finally"), "закрытие соединения обязано быть в finally")
        assertTrue(worker.contains("closeThreadConnection()"))
    }

    @Test
    fun `сырых потоков в tuple-пакете нет`() {
        val dir =
            TestRepoRoot.file("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/tuple")
        assertTrue(dir.isDirectory)
        val rawThread = Regex("""(?<![\w])thread\s*\{|(?<![\w])Thread\s*\{""")
        dir.listFiles()!!.filter { it.extension == "kt" }.forEach { file ->
            assertFalse(
                rawThread.containsMatchIn(file.readText()),
                "OneShotThreadWiringTest: сырой поток в ${file.name}",
            )
        }
    }

    @Test
    fun `exit code проверяется до DONE`() {
        val await = worker.indexOf("awaitProcessExit(startedProcess")
        val failure = worker.indexOf("processExitFailure(args, exitCode")
        assertTrue(await > 0 && failure > await, "exit code обязан проверяться после ожидания")
        assertTrue(worker.contains("redirectErrorStream(true)"))
    }
}
