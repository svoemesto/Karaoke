package com.svoemesto.karaokeapp

import com.svoemesto.karaokeapp.model.StemJob
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Инвариант закрытости приватных объектов премиум-задания (спека 561, OpenProject #361,
 * аудит BR-258) — обеспечивается **программно при старте karaoke-app** (решение владельца №2,
 * FR-006): приложение само приводит свои бакеты к безопасному состоянию, поэтому закрытость
 * не зависит от ручных действий администратора в каждом развёртывании.
 *
 * Что делается с REMOTE-хранилищем (тем же клиентом, которым объекты задания туда попадают —
 * `StorageApiClient` → MinIO на прод-хосте):
 *
 * - бакет `stemjobs` — создать (если нет) и выставить **private** (пустая bucket policy):
 *   анонимный GET `/minio/stemjobs/<key>` получает 403 от самого MinIO;
 * - бакет `karaoke` — выставить **public** (`s3:GetObject` на весь бакет): публичная часть
 *   сайта (картинки/превью/mp3 песен) по контракту раздаётся анонимно (FR-002), и та же
 *   политика, что и была, восстанавливается после любого сбоя конфигурации.
 *
 * Fail-safe: ошибка связи с хранилищем **не роняет старт приложения** — она логируется
 * (`infra.stemjobs.storage`), приложение стартует и повторит попытку при следующем старте.
 * Иначе недоступность MinIO блокировала бы запуск всего admin-приложения.
 *
 * @see specs/561-impl-stem-bucket/research.md
 */
@Component
class StemJobStoragePolicyInitializer(
    private val storageApiClient: StorageApiClient,
) {
    private val log = LoggerFactory.getLogger("infra.stemjobs.storage")

    @EventListener(ApplicationReadyEvent::class)
    fun enforceBucketPolicies() {
        applyPrivate(StemJob.STORAGE_BUCKET)
        applyPublic(StemJob.PUBLIC_STORAGE_BUCKET)
    }

    private fun applyPrivate(bucket: String) {
        try {
            storageApiClient.setBucketPrivate(bucket).block()
            log.info("stemjobs:storage:policy bucket={} policy=private", bucket)
        } catch (e: Exception) {
            log.error(
                "stemjobs:storage:policy bucket={} policy=private failed reason={}",
                bucket,
                e.javaClass.simpleName,
            )
        }
    }

    private fun applyPublic(bucket: String) {
        try {
            storageApiClient.setBucketPublic(bucket).block()
            log.info("stemjobs:storage:policy bucket={} policy=public", bucket)
        } catch (e: Exception) {
            log.error(
                "stemjobs:storage:policy bucket={} policy=public failed reason={}",
                bucket,
                e.javaClass.simpleName,
            )
        }
    }
}
