<template>
  <div class="km-page">
    <header class="km-header">
      <div class="km-header-inner">
        <div class="km-header-left">
          <RouterLink to="/account" class="km-back">← Личный кабинет</RouterLink>
        </div>
      </div>
    </header>

    <div class="km-content">
      <h1 class="km-title">Создать минусовку из аудио</h1>

      <div v-if="!isPremium" class="km-form-card">
        <p class="km-hint-text">Эта функция доступна только премиум-подписчикам.</p>
        <RouterLink to="/premium" class="km-submit-btn km-link-btn">Оформить подписку →</RouterLink>
      </div>

      <div v-else class="km-form-card">
        <h2 class="km-subtitle">Новое задание</h2>
        <div class="km-field">
          <label class="km-label">Режим разделения</label>
          <select v-model="createForm.mode" class="km-input">
            <option value="DEMUCS2">Музыка + голос (2 дорожки)</option>
            <option value="DEMUCS5">Музыка + голос + бас + ударные (5 дорожек)</option>
          </select>
        </div>
        <div class="km-field">
          <label class="km-label">Аудиофайл</label>
          <input
            ref="fileInput"
            type="file"
            :accept="acceptExtensions"
            class="km-input"
            @change="onFileChange"
          />
          <span class="km-hint-text km-limits-hint">
            До {{ maxFileSizeMb }} МБ, до {{ maxDurationMin }} мин. Форматы:
            {{ allowedExtensionsText }}.
          </span>
        </div>
        <p class="km-hint-text">В очереди: {{ activeCount }} / {{ maxActiveJobs }}</p>
        <p v-if="createMessage" :class="['km-message', createError ? 'km-error' : 'km-success']">
          {{ createMessage }}
        </p>
        <div v-if="uploading" class="km-progress-track">
          <div class="km-progress-fill" :style="{ width: uploadProgress + '%' }" />
        </div>
        <button
          class="km-submit-btn"
          :disabled="uploading || !selectedFile || activeCount >= maxActiveJobs"
          @click="onCreate"
        >
          {{ uploading ? `Загрузка... ${uploadProgress}%` : 'Создать минусовку' }}
        </button>
      </div>

      <h2 class="km-subtitle km-jobs-title">Мои задания</h2>
      <div v-if="loading" class="km-hint">Загрузка...</div>
      <div v-else-if="jobs.length === 0" class="km-empty">Заданий пока нет.</div>
      <div v-else class="km-job-list">
        <div v-for="job in jobs" :key="job.id" class="km-job-card">
          <div class="km-job-main">
            <div class="km-job-title">
              {{ job.originalFileName || 'Задание #' + job.id }}
              <span class="km-job-mode">{{ modeText(job.mode) }}</span>
            </div>
            <div class="km-job-meta">
              <span
                v-if="job.status === 'WAITING' || job.status === 'WORKING'"
                class="km-status-badge km-status-working"
              >
                <span class="km-status-spinner" />
                {{ statusText(job.status) }}
              </span>
              <span v-else :class="['km-status-badge', statusClass(job.status)]">{{
                statusText(job.status)
              }}</span>
              <span v-if="job.status === 'DONE'">
                · осталось {{ timeLeftText(job.expiresAt) }}</span
              >
              <span v-if="job.status === 'ERROR' && job.errorMessage">
                · {{ job.errorMessage }}</span
              >
            </div>
            <div v-if="job.status === 'DONE'" class="km-stem-links">
              <button
                class="km-stem-btn"
                :class="stemBtnClass(job, 'original')"
                :disabled="isStemBusy(job, 'original')"
                @click="downloadStem(job, 'original', 'оригинал')"
              >
                <span
                  v-if="stemProgress(job, 'original') > 0"
                  class="km-stem-btn-progress"
                  :style="{ width: stemProgress(job, 'original') + '%' }"
                />
                <span class="km-stem-btn-label">
                  <span v-if="stemState(job, 'original') === 'loading'" class="km-mini-spinner" />
                  <span v-else-if="stemState(job, 'original') === 'done'">✓</span>
                  <span v-else>⬇</span>
                  Оригинал
                  <template v-if="stemProgress(job, 'original') > 0">
                    {{ stemProgress(job, 'original') }}%
                  </template>
                </span>
              </button>
              <button
                v-for="stem in job.availableStems"
                :key="stem"
                class="km-stem-btn"
                :class="stemBtnClass(job, stem)"
                :disabled="isStemBusy(job, stem)"
                @click="downloadStem(job, stem, stemLabel(stem))"
              >
                <span
                  v-if="stemProgress(job, stem) > 0"
                  class="km-stem-btn-progress"
                  :style="{ width: stemProgress(job, stem) + '%' }"
                />
                <span class="km-stem-btn-label">
                  <span v-if="stemState(job, stem) === 'loading'" class="km-mini-spinner" />
                  <span v-else-if="stemState(job, stem) === 'done'">✓</span>
                  <span v-else>⬇</span>
                  {{ stemLabel(stem) }}
                  <template v-if="stemProgress(job, stem) > 0">
                    {{ stemProgress(job, stem) }}%
                  </template>
                </span>
              </button>
            </div>
          </div>
          <div class="km-job-actions">
            <button
              class="km-btn km-btn-danger"
              :disabled="job.deleteRequested"
              @click="onDelete(job)"
            >
              {{ job.deleteRequested ? 'Удаляется...' : 'Удалить' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { authGet, authPost, authUpload } from '../services/authApi'
import { useAuth } from '../composables/useAuth'

const STEM_LABELS = {
  accompaniment: 'Музыка',
  vocals: 'Голос',
  drums: 'Ударные',
  bass: 'Бас',
  other: 'Остальное',
}

const MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024
const MAX_DURATION_SECONDS = 60 * 60
const MAX_ACTIVE_JOBS = 5
const ALLOWED_EXTENSIONS = ['mp3', 'wav', 'flac', 'ogg', 'm4a', 'aac', 'wma', 'opus', 'aiff']

let pollTimer = null

/**
 * View-страница «Stem Jobs» — основной layout и data-fetching.
 *
 * @see AGENTS.md
 */

export default {
  name: 'StemJobsView',
  setup() {
    const { token, user } = useAuth()
    return { token, user }
  },
  data() {
    return {
      loading: true,
      jobs: [],
      createForm: { mode: 'DEMUCS2' },
      selectedFile: null,
      uploading: false,
      uploadProgress: 0,
      createMessage: '',
      createError: false,
      maxFileSizeMb: MAX_FILE_SIZE_BYTES / (1024 * 1024),
      maxDurationMin: MAX_DURATION_SECONDS / 60,
      maxActiveJobs: MAX_ACTIVE_JOBS,
      allowedExtensionsText: ALLOWED_EXTENSIONS.join(', '),
      acceptExtensions: ALLOWED_EXTENSIONS.map((e) => '.' + e).join(','),
      stemDownloadState: {},
      stemDownloadProgress: {},
    }
  },
  computed: {
    isPremium() {
      return !!(this.user && this.user.effectivePremium)
    },
    activeCount() {
      return this.jobs.filter((j) => j.status === 'WAITING' || j.status === 'WORKING').length
    },
  },
  async mounted() {
    if (!this.token) {
      this.$router.push({ path: '/login', query: { redirect: '/account/stemjobs' } })
      return
    }
    await this.load()
    pollTimer = setInterval(() => {
      if (this.activeCount > 0) this.load(true)
    }, 7000)
  },
  beforeUnmount() {
    if (pollTimer) clearInterval(pollTimer)
  },
  methods: {
    modeText(mode) {
      return mode === 'DEMUCS5' ? 'Музыка + голос + бас + ударные' : 'Музыка + голос'
    },
    stemLabel(stem) {
      return STEM_LABELS[stem] || stem
    },
    statusText(status) {
      return (
        { WAITING: 'В работе', WORKING: 'В работе', DONE: 'Готово', ERROR: 'Ошибка' }[status] ||
        status
      )
    },
    statusClass(status) {
      return (
        {
          WAITING: 'km-status-working',
          WORKING: 'km-status-working',
          DONE: 'km-status-done',
          ERROR: 'km-status-error',
        }[status] || ''
      )
    },
    timeLeftText(expiresAtString) {
      if (!expiresAtString) return ''
      try {
        const expires = new Date(expiresAtString.replace(' ', 'T')).getTime()
        const diffMs = expires - Date.now()
        if (diffMs <= 0) return 'меньше минуты'
        const hours = Math.floor(diffMs / 3600000)
        const minutes = Math.floor((diffMs % 3600000) / 60000)
        return hours > 0 ? `${hours} ч ${minutes} мин` : `${minutes} мин`
      } catch (e) {
        return ''
      }
    },
    onFileChange(e) {
      const file = e.target.files && e.target.files[0]
      this.createMessage = ''
      if (!file) {
        this.selectedFile = null
        return
      }
      if (file.size > MAX_FILE_SIZE_BYTES) {
        this.createMessage = `Файл больше ${this.maxFileSizeMb} МБ`
        this.createError = true
        this.selectedFile = null
        e.target.value = ''
        return
      }
      const ext = (file.name.split('.').pop() || '').toLowerCase()
      if (!ALLOWED_EXTENSIONS.includes(ext)) {
        this.createMessage = 'Неподдерживаемый формат файла'
        this.createError = true
        this.selectedFile = null
        e.target.value = ''
        return
      }
      this.selectedFile = file
    },
    async load(silent) {
      if (!silent) this.loading = true
      try {
        const { status, body } = await authGet('/api/public/account/stemjobs/list', this.token)
        if (status === 200 && Array.isArray(body)) this.jobs = body
      } catch (e) {
        /* оставляем прежний список */
      }
      this.loading = false
    },
    async onCreate() {
      if (!this.selectedFile) return
      this.createMessage = ''
      this.uploading = true
      this.uploadProgress = 0
      try {
        const { status, body } = await authUpload(
          '/api/public/account/stemjobs/create',
          this.selectedFile,
          { mode: this.createForm.mode },
          this.token,
          (pct) => {
            this.uploadProgress = pct
          },
        )
        if (status === 200 && body) {
          this.createMessage = 'Задание создано'
          this.createError = false
          this.selectedFile = null
          if (this.$refs.fileInput) this.$refs.fileInput.value = ''
          await this.load(true)
        } else {
          this.createMessage = this.createErrorText(body && body.error)
          this.createError = true
        }
      } catch (e) {
        this.createMessage = 'Не удалось связаться с сервером'
        this.createError = true
      } finally {
        this.uploading = false
      }
    },
    createErrorText(error) {
      return (
        {
          premium_required: 'Нужна премиум-подписка',
          invalid_mode: 'Неверный режим',
          file_required: 'Выберите файл',
          file_too_large: `Файл больше ${this.maxFileSizeMb} МБ`,
          unsupported_format: 'Неподдерживаемый формат файла',
          queue_limit_reached: `Достигнут лимит очереди (${this.maxActiveJobs})`,
        }[error] || 'Не удалось создать задание'
      )
    },
    async onDelete(job) {
      if (!confirm('Удалить задание? Файлы будут удалены из хранилища.')) return
      job.deleteRequested = true
      await authPost(`/api/public/account/stemjobs/${job.id}/delete`, {}, this.token)
      await this.load(true)
    },
    stemKey(job, stem) {
      return `${job.id}:${stem}`
    },
    stemState(job, stem) {
      return this.stemDownloadState[this.stemKey(job, stem)] || 'idle'
    },
    isStemBusy(job, stem) {
      return this.stemState(job, stem) === 'loading'
    },
    stemBtnClass(job, stem) {
      const state = this.stemState(job, stem)
      if (state === 'loading') return 'km-stem-btn-loading'
      if (state === 'done') return 'km-stem-btn-done'
      return ''
    },
    stemProgress(job, stem) {
      return this.stemDownloadProgress[this.stemKey(job, stem)] || 0
    },
    setStemState(key, state) {
      const next = { ...this.stemDownloadState }
      if (state) next[key] = state
      else delete next[key]
      this.stemDownloadState = next
    },
    setStemProgress(key, pct) {
      const next = { ...this.stemDownloadProgress }
      if (pct === null || pct === undefined) delete next[key]
      else next[key] = pct
      this.stemDownloadProgress = next
    },
    async readResponseWithProgress(res, onProgress) {
      const total = parseInt(res.headers.get('content-length') || '0', 10)
      if (!res.body || !total) return res.blob()
      const reader = res.body.getReader()
      const chunks = []
      let loaded = 0
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        chunks.push(value)
        loaded += value.length
        onProgress(Math.min(100, Math.round((loaded / total) * 100)))
      }
      return new Blob(chunks, { type: res.headers.get('content-type') || undefined })
    },
    async downloadStem(job, stem, label) {
      const key = this.stemKey(job, stem)
      if (this.isStemBusy(job, stem)) return
      this.setStemState(key, 'loading')
      this.setStemProgress(key, 0)
      try {
        const res = await fetch(
          `/api/public/account/stemjobs/${job.id}/download?stem=${encodeURIComponent(stem)}`,
          {
            headers: { Authorization: `Bearer ${this.token}` },
          },
        )
        if (!res.ok) {
          alert('Не удалось скачать файл — возможно, срок хранения истёк')
          this.setStemState(key, null)
          this.setStemProgress(key, null)
          return
        }
        const blob = await this.readResponseWithProgress(res, (pct) => this.setStemProgress(key, pct))
        const url = URL.createObjectURL(blob)
        const baseName = (job.originalFileName || 'stem').replace(/\.[^.]+$/, '')
        const ext = stem === 'original' ? job.originalExt || 'bin' : 'mp3'
        const a = document.createElement('a')
        a.href = url
        a.download = `${baseName} - ${label}.${ext}`
        document.body.appendChild(a)
        a.click()
        a.remove()
        URL.revokeObjectURL(url)
        this.setStemState(key, 'done')
        this.setStemProgress(key, null)
        setTimeout(() => this.setStemState(key, null), 1500)
      } catch (e) {
        alert('Не удалось скачать файл')
        this.setStemState(key, null)
        this.setStemProgress(key, null)
      }
    },
  },
}
</script>

<style scoped>
.km-page {
  min-height: 100vh;
  background: var(--km-bg);
  color: var(--km-text);
}
.km-header {
  background: var(--km-header);
  border-bottom: 1px solid var(--km-border);
  padding: 0.5rem 1rem;
}
.km-header-inner {
  max-width: 700px;
  margin: 0 auto;
}
.km-back {
  color: var(--km-accent);
  text-decoration: none;
  font-size: 0.85rem;
}
.km-content {
  max-width: 700px;
  margin: 0 auto;
  padding: 2rem 1rem;
}
.km-title {
  font-size: 1.4rem;
  margin: 0 0 1.25rem;
}
.km-subtitle {
  font-size: 1rem;
  margin: 0 0 1rem;
  color: var(--km-text);
}
.km-jobs-title {
  margin-top: 1.5rem;
}
.km-hint,
.km-empty {
  font-size: 0.9rem;
  color: var(--km-text2);
}
.km-hint-text {
  font-size: 0.85rem;
  color: var(--km-text2);
  margin: 0 0 1rem;
}
.km-limits-hint {
  display: block;
  margin-top: 0.35rem;
}
.km-form-card {
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 14px;
  padding: 1.5rem;
  margin-bottom: 1.25rem;
}
.km-field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-bottom: 0.9rem;
}
.km-label {
  font-size: 0.75rem;
  color: var(--km-text2);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.km-input {
  background: var(--km-input);
  color: var(--km-text);
  border: 1px solid var(--km-border);
  border-radius: 8px;
  padding: 0.5rem 0.75rem;
  font-size: 0.95rem;
  width: 100%;
}
.km-input:focus {
  outline: none;
  border-color: var(--km-accent);
}
.km-message {
  font-size: 0.85rem;
  margin: 0.5rem 0;
}
.km-error {
  color: #e05555;
}
.km-success {
  color: #3fae5b;
}
.km-submit-btn {
  background: var(--km-accent);
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 0.5rem 1.25rem;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
}
.km-submit-btn:hover {
  opacity: 0.88;
}
.km-submit-btn:disabled {
  opacity: 0.6;
  cursor: default;
}
.km-link-btn {
  display: inline-block;
  text-decoration: none;
  text-align: center;
}
.km-progress-track {
  background: var(--km-border);
  border-radius: 6px;
  height: 8px;
  margin-bottom: 0.75rem;
  overflow: hidden;
}
.km-progress-fill {
  background: var(--km-accent);
  height: 100%;
  transition: width 0.2s;
}

.km-job-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.km-job-card {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  background: var(--km-card);
  border: 1px solid var(--km-border);
  border-radius: 12px;
  padding: 1rem 1.25rem;
}
.km-job-main {
  flex: 1;
  min-width: 0;
}
.km-job-title {
  font-weight: 600;
  font-size: 0.95rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}
.km-job-mode {
  font-size: 0.75rem;
  font-weight: 400;
  color: var(--km-text2);
}
.km-job-meta {
  font-size: 0.8rem;
  color: var(--km-text2);
  margin-top: 0.25rem;
}
.km-status-badge {
  display: inline-block;
  border-radius: 20px;
  padding: 0.1rem 0.6rem;
  font-weight: 600;
  font-size: 0.75rem;
}
.km-status-working {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  background: rgba(212, 175, 55, 0.18);
  color: #a67c00;
  animation: km-badge-pulse 1.6s ease-in-out infinite;
}
.km-status-spinner {
  width: 9px;
  height: 9px;
  flex-shrink: 0;
  border-radius: 50%;
  border: 2px solid rgba(166, 124, 0, 0.35);
  border-top-color: #a67c00;
  animation: km-spin 0.7s linear infinite;
}
@keyframes km-spin {
  to {
    transform: rotate(360deg);
  }
}
@keyframes km-badge-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}
.km-status-done {
  background: rgba(63, 174, 91, 0.18);
  color: #3fae5b;
}
.km-status-error {
  background: rgba(224, 85, 85, 0.18);
  color: #e05555;
}
.km-stem-links {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-top: 0.6rem;
}
.km-stem-btn {
  position: relative;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  background: transparent;
  color: var(--km-accent);
  border: 1px solid var(--km-accent);
  border-radius: 8px;
  padding: 0.3rem 0.7rem;
  font-size: 0.78rem;
  cursor: pointer;
  white-space: nowrap;
}
.km-stem-btn:hover {
  background: var(--km-hover);
}
.km-stem-btn:disabled {
  cursor: default;
}
.km-stem-btn-loading {
  opacity: 0.9;
}
.km-stem-btn-done {
  color: #3fae5b;
  border-color: #3fae5b;
}
.km-stem-btn-progress {
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  background: var(--km-accent);
  opacity: 0.22;
  transition: width 0.15s linear;
  z-index: 0;
}
.km-stem-btn-label {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}
.km-mini-spinner {
  width: 9px;
  height: 9px;
  flex-shrink: 0;
  border-radius: 50%;
  border: 2px solid rgba(212, 175, 55, 0.35);
  border-top-color: var(--km-accent);
  animation: km-spin 0.7s linear infinite;
}
.km-job-actions {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  flex-shrink: 0;
}
.km-btn {
  display: inline-block;
  border-radius: 8px;
  padding: 0.4rem 0.9rem;
  font-weight: 600;
  cursor: pointer;
  border: none;
  font-size: 0.82rem;
  white-space: nowrap;
}
.km-btn-danger {
  background: transparent;
  color: #e05555;
  border: 1px solid #e05555;
}
.km-btn-danger:hover {
  background: rgba(224, 85, 85, 0.1);
}
.km-btn-danger:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
