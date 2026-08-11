<template>
  <div v-if="visible" class="km-share-modal-backdrop" @click.self="onBackdrop">
    <div class="km-share-modal">
      <button class="km-share-close" @click="close">×</button>

      <h2>Временный доступ к песне</h2>

      <div v-if="!currentLink && !creating && !created">
        <p class="km-share-lead">
          Создайте ссылку для друзей — по ней можно открыть эту песню в полном режиме онлайн-плеера
          без авторизации. Доступен одновременно максимум на двух устройствах.
        </p>
        <fieldset class="km-share-ttl">
          <legend>Срок</legend>
          <label> <input v-model="ttl" type="radio" :value="3600" /> 1 час </label>
          <label> <input v-model="ttl" type="radio" :value="86400" /> 24 часа </label>
        </fieldset>
        <button class="km-share-btn" @click="createLink">Создать и скопировать</button>
      </div>

      <div v-if="creating" class="km-share-loading">Создаём ссылку…</div>

      <div v-if="created">
        <p class="km-share-lead">
          Ссылка активна до {{ expiresLabel }}. Скопируйте её и перешлите — получатель откроет плеер
          в полном режиме.
        </p>
        <div class="km-share-url" @click="copyUrl">{{ shareUrl }}</div>
        <p v-if="copied" class="km-share-copied">Ссылка скопирована</p>
        <div class="km-share-actions">
          <button class="km-share-btn" @click="copyUrl">Скопировать снова</button>
          <button class="km-share-btn km-share-btn-warn" @click="reissue">Перевыпустить</button>
          <button class="km-share-btn km-share-btn-danger" @click="revoke">Отозвать</button>
        </div>
      </div>

      <div v-if="currentLink && !creating && !created">
        <p v-if="isExpired" class="km-share-lead">
          Срок ссылки истёк {{ expiresLabel ? `(${expiresLabel})` : '' }} — она больше не работает.
        </p>
        <p v-else class="km-share-lead">У этой песни уже есть активная ссылка.</p>
        <div v-if="currentLink.url && !isExpired" class="km-share-url" @click="copyUrl">
          {{ currentLink.url }}
        </div>
        <div v-else-if="currentLink.url && isExpired" class="km-share-url km-share-url-lost">
          {{ currentLink.url }}
        </div>
        <div v-else class="km-share-url km-share-url-lost">
          URL этой ссылки не сохранён в этом браузере — после перезапуска или в другом устройстве
          перевыпустите ссылку.
        </div>
        <p v-if="copied" class="km-share-copied">Ссылка скопирована</p>
        <p class="km-share-meta">
          <span v-if="expiresLabel">
            {{ isExpired ? 'Истекла' : 'Активна' }} до {{ expiresLabel }}.
          </span>
          <span v-if="currentLink.sessionsTotal > 0">
            Открытий: {{ currentLink.sessionsTotal }}.
          </span>
          <span v-if="currentLink.rejectedConcurrent > 0">
            Отказов по конкуренции: {{ currentLink.rejectedConcurrent }}.
          </span>
        </p>
        <div class="km-share-actions">
          <button class="km-share-btn" :disabled="!currentLink.url || isExpired" @click="copyUrl">
            Скопировать
          </button>
          <button class="km-share-btn km-share-btn-warn" @click="reissue">
            {{ isExpired ? 'Создать новую' : 'Перевыпустить' }}
          </button>
          <button v-if="!isExpired" class="km-share-btn km-share-btn-danger" @click="revoke">
            Отозвать
          </button>
        </div>
      </div>

      <div v-if="errorMessage" class="km-share-error">
        {{ errorMessage }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onUnmounted } from 'vue'
import { useAuth } from '../composables/useAuth'
import { createShareLink, getCurrentShareLink, revokeShareLink } from '../composables/useShareLink'

const props = defineProps({
  visible: Boolean,
  songId: { type: [Number, String], required: true },
})
const emit = defineEmits(['close'])

const { token } = useAuth()

// Секрет share-ссылки бэкенд отдаёт ровно один раз — при создании. Поэтому после рестарта
// браузера или новой сессии владельца у нас нет URL без перевыпуска. Сохраняем URL в
// localStorage сразу после успешного createLink/reissue, чтобы при следующем заходе
// можно было снова скопировать или переслать.
function storageKey(linkId) {
  return `karaoke-share-url:${linkId}`
}

function readSavedUrl(linkId) {
  try {
    return localStorage.getItem(storageKey(linkId)) || ''
  } catch (e) {
    return ''
  }
}

function saveUrl(linkId, url) {
  try {
    localStorage.setItem(storageKey(linkId), url)
  } catch (e) {
    /* ignore quota */
  }
}

function clearSavedUrl(linkId) {
  try {
    localStorage.removeItem(storageKey(linkId))
  } catch (e) {
    /* ignore */
  }
}

const ttl = ref(3600)
const creating = ref(false)
const created = ref(false)
const copied = ref(false)
const errorMessage = ref('')
const shareUrl = ref('')
const currentLink = ref(null)

const expiresLabel = computed(() => {
  const link = currentLink.value
  if (!link) return ''
  if (link.expiresAtLabel) return link.expiresAtLabel
  if (link.expiresAt) {
    try {
      return new Date(link.expiresAt).toLocaleString('ru-RU', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    } catch (e) {
      return new Date(link.expiresAt).toString()
    }
  }
  return ''
})

// Ссылка из БД приходит с active=true вплоть до прохода ShareLinkSweeper (раз в 60 сек),
// но фактически она уже просрочена, если expires_at < now(). Сравниваем с реальным
// epoch ms (expiresAtMs) — это поле вычисляется на бэке через
// `EXTRACT(EPOCH FROM (expires_at AT TIME ZONE 'Europe/Moscow'))`, чтобы не получать
// +3ч сдвиг «naive как UTC». См. SongShareLinkService.OwnerLinkView.expiresAtMs.
const isExpired = computed(() => {
  const link = currentLink.value
  if (!link) return false
  if (link.expiresAtMs && typeof link.expiresAtMs === 'number')
    return link.expiresAtMs <= Date.now()
  if (link.expiresAt && typeof link.expiresAt === 'number') {
    // Fallback для версий бэка без expiresAtMs: сдвигаем на -3ч (МСК).
    return link.expiresAt - 3 * 3600 * 1000 <= Date.now()
  }
  return false
})

async function loadCurrent() {
  errorMessage.value = ''
  if (!token.value) return
  try {
    const { status, body } = await getCurrentShareLink(props.songId, token.value)
    if (status === 200 && body && body.link) {
      const linkId = body.link.linkId
      const savedUrl = readSavedUrl(linkId)
      currentLink.value = { ...body.link, url: savedUrl }
    } else {
      currentLink.value = null
    }
  } catch (e) {
    errorMessage.value = 'Не удалось получить текущую ссылку'
  }
}

async function createLink() {
  errorMessage.value = ''
  creating.value = true
  try {
    const { status, body } = await createShareLink(props.songId, ttl.value, token.value)
    if (status === 200 && body && body.url) {
      shareUrl.value = body.url
      saveUrl(body.linkId, body.url)
      currentLink.value = {
        linkId: body.linkId,
        songId: props.songId,
        expiresAt: body.expiresAt,
        expiresAtMs: body.expiresAtMs ?? body.expiresAt,
        expiresAtLabel: body.expiresAtLabel,
        url: body.url,
        active: true,
      }
      created.value = true
      copied.value = false
      await copyToClipboard(body.url)
      copied.value = true
    } else {
      handleError(status, body)
    }
  } catch (e) {
    errorMessage.value = 'Сетевая ошибка'
  } finally {
    creating.value = false
  }
}

async function reissue() {
  // Для активной ссылки — переспрашиваем (отзыв старой — деструктивное действие).
  // Для просроченной — сразу создаём: пользователь явно видит «Срок ссылки истёк» и
  // подтверждение «старая перестанет работать» выглядит бессмысленно.
  if (!isExpired.value) {
    if (!confirm('Перевыпустить ссылку? Старая ссылка перестанет работать.')) return
  }
  await createLink()
}

async function revoke() {
  if (!confirm('Отозвать ссылку? Все активные сессии будут завершены.')) return
  try {
    const linkId = currentLink.value?.linkId
    await revokeShareLink(props.songId, 'manual', token.value)
    if (linkId) clearSavedUrl(linkId)
    currentLink.value = null
    created.value = false
    shareUrl.value = ''
  } catch (e) {
    errorMessage.value = 'Не удалось отозвать ссылку'
  }
}

async function copyUrl() {
  const url = shareUrl.value || currentLink.value?.url
  if (!url) return
  await copyToClipboard(url)
  copied.value = true
  setTimeout(() => {
    copied.value = false
  }, 2000)
}

async function copyToClipboard(text) {
  try {
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(text)
      return
    }
  } catch (e) {
    /* fall through */
  }
  // fallback
  const ta = document.createElement('textarea')
  ta.value = text
  document.body.appendChild(ta)
  ta.select()
  try {
    document.execCommand('copy')
  } catch (e) {
    /* ignore */
  }
  document.body.removeChild(ta)
}

function handleError(status, body) {
  const code = body && body.errorCode
  if (status === 403 && code === 'share.notOwner')
    errorMessage.value = 'Только премиум-пользователи могут создавать ссылки'
  else if (status === 409 && code === 'share.songUnavailable')
    errorMessage.value = 'Песня недоступна для share-ссылки'
  else if (status === 429 && code === 'share.linkAlreadyActive') {
    // Бэкенд сообщает reason + limit/actual — без жёстко прошитого текста.
    const reason = body.reason
    const limit = body.limit
    const actual = body.actual
    if (reason === 'maxActivePerUser')
      errorMessage.value = `Превышен лимит активных ссылок (${actual}/${limit}). Отзовите старые ссылки и попробуйте снова.`
    else if (reason === 'maxGenerationsPerDay')
      errorMessage.value = `Превышен дневной лимит созданий (${actual}/${limit}).`
    else if (reason === 'maxReissuesPerSongPerHour')
      errorMessage.value = `Превышен часовой лимит перевыпусков этой песни (${actual}/${limit}).`
    else errorMessage.value = 'Превышен лимит ссылок'
  } else errorMessage.value = 'Не удалось создать ссылку'
}

function close() {
  emit('close')
}

function onBackdrop() {
  close()
}

// US7 (FR-051): автообновление статуса активной ссылки каждые 30 сек, пока модалка открыта.
// Полезно, если владелец/админ параллельно отозвал ссылку — модалка покажет «Отозвана» без F5.
let pollTimer = null

watch(
  () => props.visible,
  (v) => {
    if (v) {
      created.value = false
      shareUrl.value = ''
      errorMessage.value = ''
      loadCurrent()
      if (pollTimer) clearInterval(pollTimer)
      pollTimer = setInterval(() => {
        if (props.visible) loadCurrent()
      }, 30000)
    } else {
      if (pollTimer) {
        clearInterval(pollTimer)
        pollTimer = null
      }
    }
  },
)

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<style scoped>
.km-share-modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 50;
}
.km-share-modal {
  background: var(--km-card, #ffffff);
  color: var(--km-text, #1a1a2e);
  border: 1px solid var(--km-border, #c8cadb);
  border-radius: 12px;
  padding: 24px 32px;
  max-width: 480px;
  width: calc(100% - 32px);
  position: relative;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}
.km-share-close {
  position: absolute;
  top: 8px;
  right: 12px;
  background: none;
  border: none;
  color: inherit;
  font-size: 24px;
  cursor: pointer;
}
.km-share-lead {
  color: var(--km-text2, #5a5a80);
}
.km-share-ttl {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border: 1px solid var(--km-border, #c8cadb);
  border-radius: 8px;
  padding: 12px 16px;
  margin: 16px 0;
}
.km-share-ttl legend {
  padding: 0 8px;
}
.km-share-btn {
  display: inline-block;
  margin: 4px;
  padding: 10px 20px;
  border-radius: 8px;
  background: var(--km-bg2, #e8eaf6);
  color: var(--km-text, #1a1a2e);
  border: 1px solid var(--km-border, #c8cadb);
  cursor: pointer;
  transition:
    filter 0.15s ease,
    box-shadow 0.15s ease,
    transform 0.05s ease;
}
.km-share-btn:hover {
  /* filter:brightness затемняет любой фон (и светлый, и тёмный), делая hover
     заметным в обеих темах. --km-hover в светлой теме (#eeeeff) практически
     неотличим от --km-bg2 (#e8eaf6) — поэтому не полагаемся на переменную. */
  filter: brightness(0.92);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
}
.km-share-btn:active {
  transform: translateY(1px);
}
.km-share-btn-warn {
  background: var(--km-accent2, #c47f00);
  color: #fff;
  border-color: var(--km-accent2, #c47f00);
}
.km-share-btn-warn:hover {
  filter: brightness(0.85);
  box-shadow: 0 2px 6px rgba(196, 127, 0, 0.4);
}
.km-share-btn-danger {
  background: #dc3545;
  color: #fff;
  border-color: #dc3545;
}
.km-share-btn-danger:hover {
  filter: brightness(0.88);
  box-shadow: 0 2px 6px rgba(220, 53, 69, 0.4);
}
.km-share-url {
  font-family: monospace;
  background: var(--km-input, #f8f9ff);
  color: var(--km-text, #1a1a2e);
  border: 1px solid var(--km-border, #c8cadb);
  padding: 12px;
  border-radius: 8px;
  word-break: break-all;
  cursor: pointer;
  user-select: all;
  margin: 16px 0;
}
.km-share-copied {
  color: #198754;
}
.km-share-meta {
  color: var(--km-text2, #5a5a80);
  font-size: 14px;
}
.km-share-url-lost {
  cursor: default;
  background: var(--km-bg2, #e8eaf6);
  color: var(--km-text2, #5a5a80);
  font-style: italic;
}
.km-share-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background: var(--km-bg2, #e8eaf6);
  color: var(--km-text2, #5a5a80);
}
.km-share-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.km-share-loading {
  padding: 24px;
  text-align: center;
}
.km-share-error {
  margin-top: 12px;
  padding: 8px 12px;
  background: #f8d7da;
  border: 1px solid #f1aeb5;
  border-radius: 6px;
  color: #842029;
}
</style>
