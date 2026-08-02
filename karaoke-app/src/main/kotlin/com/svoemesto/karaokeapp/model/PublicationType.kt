package com.svoemesto.karaokeapp.model

/**
 * Тип публикации в группе ВКонтакте (specs/121-vk-news-auto-publish, FR-027 —
 * расширяемо).
 *
 * Определяет, какой шаблон текста поста использовать
 * (`vkTemplateAir` / `vkTemplatePremium`, см. `VkTemplateService.templateFor`)
 * и откуда брать текст. Не хранится в БД — передаётся как параметр в
 * [com.svoemesto.karaokeapp.services.VkAutoPublishService.publishToVk].
 *
 * Идемпотентность — общая по [Song.idVk] (один пост на песню, независимо от
 * типа, FR-007/FR-016/FR-026). Будущие типы (FR-027) добавляются новыми
 * значениями enum + новыми ключами `vkTemplate<Name>` в KaraokeProperties,
 * без структурных изменений.
 *
 * @see docs/features/vk-news-auto-publish.md
 */
enum class PublicationType(
    val code: String,
) {
    /** Авто, по `tbl_news.category='air'` + `publish_at <= now()` (FR-001). Шаблон: `vkTemplateAir`. */
    AIR("air"),

    /** Ручной, кнопка в карточке песни (FR-026). Шаблон: `vkTemplatePremium`. */
    PREMIUM("premium"),

    ;

    companion object {
        /** Возвращает тип по коду или `null`, если код не сопоставлен ни одному значению. */
        fun fromCode(code: String?): PublicationType? = entries.firstOrNull { it.code == code }
    }
}
