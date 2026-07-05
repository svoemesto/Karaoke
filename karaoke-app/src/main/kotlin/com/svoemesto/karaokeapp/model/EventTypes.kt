package com.svoemesto.karaokeapp.model

// Централизованные строковые значения tbl_events.event_type/link_type/rest_name. dbValue — не
// .name/ordinal, чтобы уже сохранённые в БД строки (250k+ строк) остались единственным
// источником истины без миграции данных; enum — только замена разбросанных по коду литералов.
enum class EventType(val dbValue: String) {
    CALL_REST("callRest"),
    CLICK_TO_LINK("clickToLink"),
    PLAY("play"),      // legacy: видео на странице песни, НЕ онлайн-плеер
    PLAYER("player"),  // онлайн-плеер: open/play/pause/seek/export/progress/ended, см. PlayerAction
    ENGAGEMENT("engagement"), // время на странице: rest_name = идентификатор страницы, link_name = секунды
    UI("ui");          // UI-действия: link_type = navigate|theme|scroll, link_name = деталь

    companion object {
        fun fromDb(value: String?): EventType? = entries.find { it.dbValue == value }
    }
}

// Подтипы для event_type = EventType.UI (значение link_type). Хранятся как строки в БД, отдельного
// enum-класса LinkType не заводим — значения свободные, меньше связанности.
enum class UiAction(val dbValue: String) {
    NAVIGATE("navigate"), THEME("theme"), SCROLL("scroll");

    companion object {
        fun fromDb(value: String?): UiAction? = entries.find { it.dbValue == value }
    }
}

enum class RestName(val dbValue: String) {
    MAIN("main"), ZAKROMA("zakroma"), FILTER("filter"), SONG("song");

    companion object {
        fun fromDb(value: String?): RestName? = entries.find { it.dbValue == value }
    }
}

enum class LinkType(val dbValue: String) {
    LINK_TO_SOCIAL_NETWORK("linkToSocialNetwork"),
    LINK_TO_SONG("linkToSong"),
    SONG_META("songMeta"); // жест разблокировки плеера — по-прежнему не пишется в tbl_events

    companion object {
        fun fromDb(value: String?): LinkType? = entries.find { it.dbValue == value }
    }
}

// Значение tbl_events.link_type, когда event_type = EventType.PLAYER. link_name при этом
// переиспользуется как деталь действия: ключ стема для EXPORT, позиция в секундах для SEEK.
enum class PlayerAction(val dbValue: String) {
    OPEN("open"), PLAY("play"), PAUSE("pause"), SEEK("seek"), EXPORT("export"),
    PROGRESS("progress"), // веха прослушивания: link_name = "25"|"50"|"75"
    ENDED("ended");       // трек доигран до конца

    companion object {
        fun fromDb(value: String?): PlayerAction? = entries.find { it.dbValue == value }
    }
}
