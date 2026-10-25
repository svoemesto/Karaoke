package com.svoemesto.karaokeapp.audit

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Регрессия пакета `PKG-S3-25` (аудит #336, OpenProject #397) по записям,
 * закрытым РАНЕЕ (другими пакетами) — регрессионные проверки, а не повторная правка.
 *
 * Перечень (по `registry.csv`, `status=исправлено`, `work_package` 361/375/383/407):
 * BR-234, BR-235, BR-237, BR-244, BR-247, BR-249, BR-251, BR-258, BR-266.
 *
 * Правки чужих пакетов НЕ переписывались — здесь только «якорь», который упадёт,
 * если правка будет отменена.
 *
 * @see specs/397-pkg-s3-25/report.md
 */
class PkgS325AlreadyClosedRegressionTest {
    private fun repoFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile ?: error("не найден корень репозитория (settings.gradle.kts)")
        }
        val file = File(dir, relativePath)
        assertTrue(file.isFile, "файл не найден: $relativePath")
        return file
    }

    private fun readCode(relativePath: String): String =
        repoFile(relativePath)
            .readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")

    /**
     * BR-247 (права файла с секретами, #407): `Karaoke.properties` и его `.bak`
     * должны оставаться `600` — в файле лежат токены VK/Telegram/Whisper.
     */
    @Test
    fun `BR-247 файл с секретами остаётся 600`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt")
        assertTrue(
            source.contains("SECRET_FILE_PERMISSIONS"),
            "BR-247: набор прав для файла с секретами должен быть объявлен",
        )
        val secretPermissions = source.substringAfter("SECRET_FILE_PERMISSIONS").substringBefore(")")
        assertTrue(
            secretPermissions.contains("OWNER_READ") && secretPermissions.contains("OWNER_WRITE"),
            "BR-247: у файла с секретами должны быть права владельца",
        )
        assertFalse(
            secretPermissions.contains("GROUP_READ") || secretPermissions.contains("OTHERS_READ"),
            "BR-247: файл с секретами не должен быть доступен на чтение другим",
        )
        assertTrue(
            source.contains("fun setSecretFilePermissions"),
            "BR-247: публичная функция выставления прав должна существовать",
        )
    }

    /**
     * BR-244 (гонка записи файла настроек): запись сериализована одним
     * `ReentrantLock`, а временный файл — на уникальном пути.
     */
    @Test
    fun `BR-244 запись файла настроек сериализована`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt")
        assertTrue(
            source.contains("ReentrantLock"),
            "BR-244: запись файла свойств должна быть под блокировкой",
        )
        assertTrue(
            source.contains("writePropertiesFileAtomic"),
            "BR-244: запись должна быть атомарной",
        )
    }

    /**
     * BR-251 (результат синхронизации игнорировался) — по реестру закрыт пакетом
     * #375: результат `postChangeRecords` проверяется, а неподтверждённые чанки не
     * удаляются из источника.
     */
    @Test
    fun `BR-251 результат отправки чанка синхронизации проверяется`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt")
        assertTrue(
            source.contains("postChunksConfirming"),
            "BR-251: чанки должны отправляться с проверкой подтверждения",
        )
        val helper = source.substringAfter("internal fun postChunksConfirming").substringBefore("private fun")
        assertTrue(
            helper.contains("if (!send(requestBody)) break"),
            "BR-251: отказ цели должен прерывать отправку чанков",
        )
    }

    /**
     * BR-266 (список авторов) — по реестру закрыт пакетом #361: рекурсия заменена
     * на один проход по круговому списку.
     */
    @Test
    fun `BR-266 список авторов обходится одним проходом без рекурсии`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt")
        assertTrue(
            source.contains("authorRequestOrder"),
            "BR-266: обход списка авторов должен идти через authorRequestOrder",
        )
        val order = source.substringAfter("internal fun authorRequestOrder").substringBefore("fun getAuthorForRequest")
        assertFalse(
            order.contains("authorRequestOrder("),
            "BR-266: рекурсивный вызов в authorRequestOrder должен отсутствовать",
        )
    }

    /**
     * BR-234 (collect-store — no-op) — по реестру закрыт пакетом #375: сбор
     * хранилища больше не «пустой успех».
     */
    @Test
    fun `BR-234 сбор хранилища не является пустым успехом`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt")
        val collectStore = source.substringAfter("collectstore").substringBefore("fun ")
        assertFalse(
            collectStore.contains("return true"),
            "BR-234: сбор хранилища не должен безусловно отвечать успехом",
        )
    }

    /**
     * BR-235 (OOM на индексе голоса) — по реестру закрыт пакетом #375: индекс
     * голоса проверяется ДО использования.
     */
    @Test
    fun `BR-235 индекс голоса проверяется до использования`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt")
        assertTrue(
            source.contains("isValidVoiceIndex"),
            "BR-235: индекс голоса должен проверяться функцией isValidVoiceIndex",
        )
    }

    /**
     * BR-237 (SQL-инъекция в списке альбомов) — по реестру закрыт пакетом #383:
     * значения подставляются параметрами, а не склейкой.
     */
    @Test
    fun `BR-237 альбомы читаются без склейки значений в SQL`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt")
        val loadList = source.substringAfter("fun loadAlbumTilesWithCounts").substringBefore("private fun")
        assertFalse(
            loadList.contains("\"WHERE author_id = '\" +"),
            "BR-237: author_id должен подставляться параметром, а не склейкой",
        )
        assertTrue(
            loadList.contains("ps.setLong(1, authorId)"),
            "BR-237: значение должно передаваться через setLong (параметр запроса)",
        )
    }

    /**
     * BR-258 (публичный бакет со стемами) — по реестру закрыт пакетом #361:
     * приватные объекты задания лежат в отдельном бакете `stemjobs`.
     */
    @Test
    fun `BR-258 приватные объекты задания лежат в отдельном бакете`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/StemJob.kt")
        assertTrue(
            source.contains("STORAGE_BUCKET"),
            "BR-258: у задания должен быть приватный бакет STORAGE_BUCKET",
        )
        val storageBucket = source.substringAfter("STORAGE_BUCKET").substringBefore(")")
        assertTrue(
            storageBucket.contains("stemjobs"),
            "BR-258: приватный бакет стемов должен отличаться от публичного «karaoke»",
        )
    }

    /**
     * BR-249 (результат записи файла настроек) — в реестре числится TODO за этим
     * пакетом, но часть правки (наблюдаемость записи) пересекается с уже
     * сделанной ранее работой по правам файла (BR-247). Проверяем, что обе
     * части на месте.
     */
    @Test
    fun `BR-249 результат записи наблюдаем и файл с секретами защищён`() {
        val source = readCode("karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt")
        assertTrue(
            source.contains("fun savePropertiesMap(): Boolean"),
            "BR-249: результат записи файла свойств должен быть наблюдаем",
        )
        assertTrue(
            source.contains("setSecretFilePermissions"),
            "BR-249: запись файла свойств обязана ставить права 600 (BR-247)",
        )
    }
}
