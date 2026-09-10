package com.svoemesto.karaokeweb.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Request DTO для bulk-fetch membership (избранное + не-избранные плейлисты)
 * песен пользователя. Используется в `POST /api/public/account/playlists/membership`
 * (см. [specs/361-playlists-membership-uri-length/spec.md](../../specs/361-playlists-membership-uri-length/spec.md)).
 *
 * Заменяет CSV в query-string GET-эндпоинта, который ломался на крупных авторах
 * (~2500+ песен, URL > 8 КБ → HTTP 414 Request-URI Too Large, см. OpenProject #77).
 *
 * @see docs/features/playlist-membership.md
 * @see specs/361-playlists-membership-uri-length/contracts/api-public-account-playlists-membership.md
 */
data class MembershipRequest(
    @JsonProperty("ids")
    val ids: List<Long>,
)
