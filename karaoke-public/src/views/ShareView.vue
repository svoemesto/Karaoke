<template>
  <div class="km-share-landing">
    <div v-if="state === 'claiming'" class="km-card">
      <div class="km-spinner" />
      <p>Получаем доступ к песне…</p>
    </div>

    <div v-else-if="state === 'concurrent'" class="km-card km-card-warn">
      <h2>Эта ссылка уже используется</h2>
      <p>
        По этой временной ссылке одновременно может быть открыто не более двух устройств. Дождитесь,
        пока другое устройство закроет плеер, и попробуйте снова.
      </p>
      <button class="km-btn" @click="retry">Повторить</button>
    </div>

    <div v-else-if="state === 'notfound'" class="km-card km-card-error">
      <h2>Ссылка недоступна</h2>
      <p>Срок действия ссылки истёк, она была отозвана владельцем или песня снята с публикации.</p>
      <router-link to="/" class="km-btn">На главную</router-link>
    </div>

    <div v-else-if="state === 'ratelimited'" class="km-card km-card-error">
      <h2>Слишком много попыток</h2>
      <p>Подождите минуту и попробуйте снова.</p>
      <button class="km-btn" @click="retry">Повторить</button>
    </div>

    <div v-else-if="state === 'ready'" class="km-card km-card-share">
      <div v-if="albumImageUrl || artistImageUrl" class="km-share-cover">
        <img
          v-if="albumImageUrl"
          :src="albumImageUrl"
          alt="Обложка альбома"
          class="km-share-cover-album"
        />
        <img
          v-if="artistImageUrl"
          :src="artistImageUrl"
          alt="Фото исполнителя"
          class="km-share-cover-artist"
        />
      </div>
      <h2 class="km-share-title">{{ songName || 'Песня' }}</h2>
      <p v-if="author" class="km-share-author">{{ author }}</p>
      <p v-if="album" class="km-share-album">
        <span>{{ album }}</span>
        <span v-if="year"> · {{ year }}</span>
      </p>
      <p v-if="expiresAtLabel" class="km-share-badge">Доступно до {{ expiresAtLabel }}</p>
      <p v-if="isExpired" class="km-share-error">
        Срок действия этой ссылки истёк. Попросите владельца прислать новую.
      </p>
      <p v-else class="km-share-note">
        Вам открыли временный доступ к этой песне в режиме онлайн-плеера.
      </p>
      <button v-if="!isExpired" class="km-btn km-btn-primary" @click="openPlayer">
        Открыть плеер
      </button>
      <button v-if="!isExpired" class="km-btn km-btn-secondary" @click="copyLink">
        Скопировать ссылку
      </button>
    </div>

    <div v-else class="km-card km-card-error">
      <h2>Не удалось открыть ссылку</h2>
      <p>Попробуйте позже.</p>
      <router-link to="/" class="km-btn">На главную</router-link>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { claimShare } from '../services/songShareLink'
import { formatDate } from '../utils/dateFormat.js'

const route = useRoute()
const router = useRouter()

const state = ref('claiming')
const sessionTokenHash = ref('')
const songId = ref(0)
const expiresAt = ref(0)
const songName = ref('')
const author = ref('')
const album = ref('')
const year = ref(0)
const albumImageUrl = ref('')
const artistImageUrl = ref('')

const expiresAtLabel = computed(() => formatDate(expiresAt.value))

async function doClaim() {
  state.value = 'claiming'
  try {
    const { status, body } = await claimShare(route.params.secret)
    if (status === 200 && body && body.sessionTokenHash) {
      // Бэкенд возвращает оба id: linkId (id записи в tbl_song_share_links) и songId
      // (id песни). Для /player/{id} используем songId — иначе попадаем на чью-то
      // песню и playerdata отдаёт 404 (см. инцидент 2026-08-10).
      songId.value = body.songId ?? body.linkId
      sessionTokenHash.value = body.sessionTokenHash
      songName.value = body.songName || ''
      author.value = body.author || ''
      album.value = body.album || ''
      year.value = Number(body.year) || 0
      albumImageUrl.value = body.albumImageUrl || ''
      artistImageUrl.value = body.artistImageUrl || ''
      // expiresAt — реальный epoch ms (момент окончания lease). Бэк отдаёт как часть
      // /claim response с T023. Метка «Доступно до …» форматируется на клиенте
      // через dateFormat.formatDate в TZ устройства (FR-011).
      expiresAt.value = Number(body.expiresAt) || 0
      state.value = 'ready'
      return
    }
    const code = body && body.errorCode
    if (code === 'share.concurrentLimit') state.value = 'concurrent'
    else if (code === 'share.rateLimited') state.value = 'ratelimited'
    else state.value = 'notfound'
  } catch (_e) {
    state.value = 'error'
  }
}

const isExpired = computed(() => expiresAt.value > 0 && expiresAt.value <= Date.now())

const shareUrl = computed(() => {
  if (!songId.value || !sessionTokenHash.value) return ''
  const base = window.location.origin
  return `${base}/player/${songId.value}?share=1&session=${encodeURIComponent(sessionTokenHash.value)}`
})

function retry() {
  doClaim()
}

function openPlayer() {
  if (!songId.value || !sessionTokenHash.value) return
  router.replace({
    path: `/player/${songId.value}`,
    query: { share: '1', session: sessionTokenHash.value },
  })
}

async function copyLink() {
  if (!shareUrl.value) return
  try {
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(shareUrl.value)
      return
    }
  } catch (e) {
    /* fall through */
  }
  // fallback
  const ta = document.createElement('textarea')
  ta.value = shareUrl.value
  document.body.appendChild(ta)
  ta.select()
  try {
    document.execCommand('copy')
  } catch (e) {
    /* ignore */
  }
  document.body.removeChild(ta)
}

onMounted(() => {
  doClaim()
})
</script>

<style scoped>
.km-share-landing {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 80vh;
  padding: 24px;
}
.km-card {
  max-width: 480px;
  background: var(--km-bg-card, #1c1c1c);
  color: var(--km-fg, #eee);
  border-radius: 12px;
  padding: 32px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.3);
  text-align: center;
}
.km-card-warn {
  border-left: 4px solid #f80;
}
.km-card-error {
  border-left: 4px solid #e44;
}
.km-card h2 {
  margin-top: 0;
}
.km-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #555;
  border-top-color: #f80;
  border-radius: 50%;
  margin: 0 auto 16px;
  animation: km-spin 0.8s linear infinite;
}
@keyframes km-spin {
  to {
    transform: rotate(360deg);
  }
}
.km-btn {
  display: inline-block;
  margin-top: 16px;
  padding: 10px 20px;
  border-radius: 8px;
  background: #333;
  color: #fff;
  border: none;
  cursor: pointer;
  text-decoration: none;
}
.km-btn-primary {
  background: #f80;
  color: #1c1c1c;
  font-weight: 600;
}
.km-btn-secondary {
  background: #444;
  color: #fff;
}
.km-share-badge {
  display: inline-block;
  margin: 0 0 12px;
  padding: 6px 12px;
  background: rgba(255, 136, 0, 0.15);
  border: 1px solid #f80;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  color: #f80;
}
.km-share-error {
  margin: 16px 0 0;
  padding: 12px;
  background: rgba(228, 68, 68, 0.1);
  border-left: 3px solid #e44;
  border-radius: 4px;
  color: #faa;
  font-size: 14px;
}

/* Лендинг share-ссылки — расширенный вариант с превью обложки/автора и подписью песни.
   Дизайн: обложка альбома 400×400 слева + превью автора 1000×400 справа (баннер), ниже
   название крупно, автор средне, альбом+год мельче. Общая ширина карточки 560px, чтобы
   картинки влезли без переноса на мобильных (на узких экранах схлопываются в столбик). */
.km-card-share {
  max-width: 560px;
  text-align: left;
}
.km-share-cover {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  align-items: stretch;
}
.km-share-cover-album {
  width: 160px;
  height: 160px;
  object-fit: cover;
  border-radius: 8px;
  flex-shrink: 0;
  background: #2a2a2a;
}
.km-share-cover-artist {
  flex: 1;
  min-width: 0;
  height: 160px;
  object-fit: cover;
  border-radius: 8px;
  background: #2a2a2a;
}
.km-share-title {
  margin: 0 0 8px;
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
}
.km-share-author {
  margin: 0 0 4px;
  font-size: 17px;
  font-weight: 500;
  color: #ddd;
}
.km-share-album {
  margin: 0 0 16px;
  font-size: 14px;
  color: #aaa;
}
.km-share-note {
  margin: 16px 0 0;
  font-size: 14px;
  line-height: 1.5;
  color: #ccc;
}
.km-card-share .km-btn-primary {
  display: block;
  margin: 24px auto 0;
  width: max-content;
  min-width: 200px;
}
@media (max-width: 520px) {
  .km-card-share {
    max-width: 100%;
  }
  .km-share-cover {
    flex-direction: column;
  }
  .km-share-cover-artist {
    width: 100%;
  }
}
</style>
