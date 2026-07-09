package com.svoemesto.karaokeapp.monitor

/**
 * Одно системное сообщение мониторинга - аналог HealthReport (canResolve/problemText/solutionText/
 * solutionActions), но НЕ привязанное к конкретной песне Settings: это проверки состояния проекта
 * в целом (очередь рендера, доступность прод-сервера, горизонт публикаций и т.п.).
 *
 * key должен быть стабильным между прогонами одной и той же проверки - по нему связывается
 * состояние "прочитано" (см. MonitoringService.dismissed) и ре-деривация resolveAction при вызове
 * "Решить проблему" (лямбда живёт только в свежем снапшоте, DTO её не переносит - как и
 * HealthReport.getHealthReport(...).executeSolutionActions()).
 *
 * detail - изменчивая часть текста для отображения (например "недоступен уже N мин") - сознательно
 * НЕ входит в contentHash(), иначе сообщение "мигало" бы read/unread на каждом тике планировщика.
 */
data class MonitorAlert(
    val key: String,
    val severity: MonitorSeverity,
    val title: String,
    val body: String,
    val category: String,
    val detail: String? = null,
    val recommendations: String? = null,
    val resolveAction: (() -> Unit)? = null
) {
    val canResolve: Boolean get() = resolveAction != null

    fun contentHash(): String =
        Integer.toHexString((severity.name + "|" + title + "|" + body).hashCode())

    fun executeResolve() {
        resolveAction?.invoke()
    }

    fun toDto(read: Boolean): MonitorAlertDto = MonitorAlertDto(
        key = key,
        severityName = severity.name,
        color = severity.color,
        title = title,
        body = body,
        category = category,
        detail = detail,
        recommendations = recommendations,
        canResolve = canResolve,
        contentHash = contentHash(),
        read = read
    )
}
