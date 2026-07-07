<template>
  <div class="km-page ke-page">
    <!-- Шапка -->
    <header class="km-header ke-sticky-top">
      <div class="ke-header-inner">
        <RouterLink to="/account/editor" class="km-back">← Мои задания</RouterLink>
        <div class="ke-header-title">
          <template v-if="task">
            <span class="ke-h-song">{{ task.songName }}</span>
            <span class="ke-h-author">{{ task.author }}</span>
          </template>
        </div>
        <span v-if="task" class="ke-badge" :class="`ke-badge-${status}`">{{ statusLabel }}</span>
      </div>
    </header>

    <div v-if="loading" class="ke-empty">Загрузка задания…</div>
    <div v-else-if="loadError" class="ke-empty">{{ loadError }}</div>

    <div v-else class="ke-work">
      <!-- Баннер отклонения -->
      <div v-if="status === 'rejected' && reviewComment" class="ke-reject-banner">
        <strong>Возвращено на доработку:</strong> {{ reviewComment }}
      </div>
      <div v-else-if="status === 'submitted'" class="ke-info-banner">
        Задание отправлено на проверку. Пока админ его не рассмотрит, редактирование недоступно.
      </div>
      <div v-else-if="status === 'approved'" class="ke-ok-banner">
        Разметка одобрена и применена. Спасибо! Песня доступна в онлайн-плеере.
      </div>

      <!-- Бегущая строка -->
      <div class="ke-tail-card">
        <div class="ke-tail-line">
          <span class="ke-tail-begin">{{ tail.begin }}</span>
          <span class="ke-tail-curr">{{ tail.curr }}</span>
          <span class="ke-tail-next">{{ tail.next }}</span>
          <span class="ke-tail-end">{{ tail.end }}</span>
        </div>
      </div>

      <!-- Волновая форма -->
      <div class="ke-wave-card">
        <div ref="waveform" class="ke-waveform"></div>
        <div class="ke-time">{{ fmtTime(currentTime) }} / {{ fmtTime(duration) }}</div>
      </div>

      <!-- Транспорт -->
      <div class="ke-transport">
        <button class="ke-tbtn" title="Назад 1с (←)" @click="step(-1)">⏮</button>
        <button class="ke-tbtn ke-tbtn-play" :title="isPlaying ? 'Пауза (Space)' : 'Играть (Space)'" @click="playPause">
          {{ isPlaying ? '⏸' : '▶' }}
        </button>
        <button class="ke-tbtn" title="Вперёд 1с (→)" @click="step(1)">⏭</button>

        <div class="ke-sliders">
          <label class="ke-slider">
            <span>Скорость {{ playbackRate.toFixed(2) }}×</span>
            <input type="range" min="0.3" max="1" step="0.05" v-model.number="playbackRate" @input="applyRate" />
          </label>
          <label class="ke-slider">
            <span>Масштаб</span>
            <input type="range" min="20" max="400" step="10" v-model.number="zoom" @input="applyZoom" />
          </label>
          <label class="ke-slider">
            <span>Голос</span>
            <input type="range" min="0" max="1" step="0.05" v-model.number="volumeVoice" @input="applyVolumes" />
          </label>
          <label class="ke-slider">
            <span>Минус</span>
            <input type="range" min="0" max="1" step="0.05" v-model.number="volumeMinus" @input="applyVolumes" />
          </label>
        </div>
      </div>

      <!-- Кнопки разметки -->
      <div v-if="canEdit" class="ke-markup">
        <button class="ke-mbtn ke-mbtn-syl" title="Слог (S)" @click="mark('syllables')">
          <span class="ke-mbtn-ico">◆</span> Слог <kbd>S</kbd>
        </button>
        <button class="ke-mbtn ke-mbtn-eol" title="Конец строки (Q)" @click="mark('endofline')">
          <span class="ke-mbtn-ico">⏎</span> Конец строки <kbd>Q</kbd>
        </button>
        <button class="ke-mbtn ke-mbtn-nl" title="Новая строка / пауза (W)" @click="mark('newline')">
          <span class="ke-mbtn-ico">␊</span> Новая строка <kbd>W</kbd>
        </button>
        <button class="ke-mbtn ke-mbtn-del" title="Удалить маркер (D)" @click="removeMarker">
          <span class="ke-mbtn-ico">🗑</span> Удалить <kbd>D</kbd>
        </button>
      </div>

      <!-- Текст + превью -->
      <div class="ke-texts">
        <div class="ke-text-col">
          <div class="ke-col-title">Текст песни</div>
          <textarea
            class="ke-textarea"
            v-model="sourceText"
            :disabled="!canEdit"
            placeholder="Вставьте сюда текст песни — он автоматически разобьётся на слоги."
            @input="onTextInput"
          ></textarea>
        </div>
        <div class="ke-text-col">
          <div class="ke-col-title">Разметка</div>
          <div class="ke-preview" v-html="formattedTextHtml"></div>
        </div>
      </div>
    </div>

    <!-- Нижняя панель -->
    <footer v-if="!loading && !loadError && canEdit" class="ke-footer ke-sticky-bottom">
      <span class="ke-save-state" :class="`ke-save-${saveState}`">{{ saveStateLabel }}</span>
      <div class="ke-footer-btns">
        <button class="ke-btn ke-btn-ghost" :disabled="saveState === 'saving'" @click="saveNow">Сохранить</button>
        <button class="ke-btn ke-btn-primary" :disabled="submitting || markers.length === 0" @click="submit">
          Отправить на проверку
        </button>
      </div>
    </footer>
  </div>
</template>

<script>
import { fetchTask, saveTask, submitTask } from '../services/songEditorApi'
import { useAuth } from '../composables/useAuth'
import { STATUS_LABELS } from '../composables/editorStatus'
import {
  splitSyllables, sortMarkers, relabelSyllables, currentSyllableIndex, currentMarkerIndex,
  addMarker, deleteMarkerAtTime, ensureEndMarker, formatText, buildTail, markersToSave, markersFromServer,
} from '../composables/useKaraokeEditor'

export default {
  name: 'EditorWorkView',
  setup() {
    const { token, user, fetchMe } = useAuth()
    return { token, user, fetchMe }
  },
  data() {
    return {
      task: null,
      loading: true,
      loadError: '',
      status: 'assigned',
      canEdit: false,
      reviewComment: '',
      sourceText: '',
      markers: [],
      syllables: [],
      // Аудио / waveform
      ws: null,
      wsRegions: null,
      minusAudio: null,
      currentTime: 0,
      duration: 0,
      isPlaying: false,
      playbackRate: 1,
      zoom: 100,
      volumeVoice: 1,
      volumeMinus: 0.7,
      // Сохранение
      saveState: 'idle', // idle | saving | saved | error
      saveTimer: null,
      submitting: false,
      redrawScheduled: false,
    }
  },
  computed: {
    statusLabel() { return STATUS_LABELS[this.status] || this.status },
    saveStateLabel() {
      return { idle: '', saving: 'Сохранение…', saved: 'Сохранено ✓', error: 'Ошибка сохранения' }[this.saveState] || ''
    },
    curSyllableIndex() { return currentSyllableIndex(this.markers, this.currentTime) },
    curMarkerIndex() { return currentMarkerIndex(this.markers, this.currentTime) },
    formattedTextHtml() { return formatText(this.markers, this.curMarkerIndex) },
    tail() { return buildTail(this.syllables, this.curSyllableIndex) },
  },
  async mounted() {
    await this.fetchMe()
    if (!this.token) {
      this.$router.push({ path: '/login', query: { redirect: `/account/editor/${this.$route.params.id}` } })
      return
    }
    if (!this.user || !this.user.editor) {
      this.$router.push('/account')
      return
    }
    await this.load()
    window.addEventListener('keydown', this.onKeyDown)
  },
  beforeUnmount() {
    window.removeEventListener('keydown', this.onKeyDown)
    if (this.saveTimer) clearTimeout(this.saveTimer)
    try { this.minusAudio && this.minusAudio.pause() } catch (e) { /* noop */ }
    try { this.ws && this.ws.destroy() } catch (e) { /* noop */ }
  },
  methods: {
    fmtTime(s) {
      if (!s || s < 0) s = 0
      const m = Math.floor(s / 60)
      const sec = Math.floor(s % 60)
      return `${m}:${sec.toString().padStart(2, '0')}`
    },
    async load() {
      this.loading = true
      this.loadError = ''
      const { status, body } = await fetchTask(this.$route.params.id)
      if (status !== 200 || !body) {
        this.loadError = 'Задание не найдено или недоступно.'
        this.loading = false
        return
      }
      this.task = body
      this.status = body.status
      this.canEdit = !!body.canEdit
      this.reviewComment = body.reviewComment || ''
      this.sourceText = body.sourceText || ''
      this.markers = markersFromServer(body.markers)
      this.syllables = splitSyllables(this.sourceText)
      relabelSyllables(this.markers, this.syllables)
      this.loading = false
      await this.$nextTick()
      await this.initWaveSurfer(body.audioVocalsUrl, body.audioAccompanimentUrl)
    },
    async initWaveSurfer(voiceUrl, minusUrl) {
      if (!this.$refs.waveform) return
      const { default: WaveSurfer } = await import('wavesurfer.js')
      const { default: RegionsPlugin } = await import('wavesurfer.js/dist/plugins/regions.esm.js')
      const styles = getComputedStyle(document.documentElement)
      const accent = (styles.getPropertyValue('--km-accent') || '#3b82f6').trim()

      this.ws = WaveSurfer.create({
        container: this.$refs.waveform,
        height: 140,
        waveColor: '#9db4d6',
        progressColor: accent,
        cursorColor: '#ff5252',
        minPxPerSec: this.zoom,
        autoScroll: true,
        autoCenter: true,
        normalize: true,
      })
      this.wsRegions = this.ws.registerPlugin(RegionsPlugin.create())

      if (minusUrl) {
        this.minusAudio = new Audio(minusUrl)
        this.minusAudio.preload = 'auto'
        this.minusAudio.volume = this.volumeMinus
      }

      this.ws.on('decode', () => {
        this.duration = this.ws.getDuration()
        this.ws.setVolume(this.volumeVoice)
        this.redrawRegions()
      })
      this.ws.on('timeupdate', (t) => {
        this.currentTime = t
        // Мягкая синхронизация минуса (коррекция только при заметном дрейфе).
        if (this.minusAudio && Math.abs(this.minusAudio.currentTime - t) > 0.25) {
          try { this.minusAudio.currentTime = t } catch (e) { /* noop */ }
        }
      })
      this.ws.on('play', () => {
        this.isPlaying = true
        if (this.minusAudio) { try { this.minusAudio.currentTime = this.ws.getCurrentTime(); this.minusAudio.play() } catch (e) { /* noop */ } }
      })
      this.ws.on('pause', () => {
        this.isPlaying = false
        if (this.minusAudio) { try { this.minusAudio.pause() } catch (e) { /* noop */ } }
      })
      // Перетаскивание маркера пользователем.
      this.wsRegions.on('region-updated', (region) => {
        const marker = this.markers.find(m => m.uid === region.id)
        if (!marker) return
        marker.time = region.start
        sortMarkers(this.markers)
        relabelSyllables(this.markers, this.syllables)
        this.scheduleRedraw()
        this.scheduleAutosave()
      })

      this.ws.load(voiceUrl)
    },
    regionContentEl(marker) {
      const el = document.createElement('div')
      el.className = 'ke-region'
      if (marker.markertype === 'syllables') {
        el.classList.add('ke-region-syl')
        el.textContent = (marker.label || '').replaceAll('_', ' ').trim() || '·'
      } else if (marker.markertype === 'endofline') {
        el.classList.add('ke-region-eol')
        el.textContent = '⏎'
      } else if (marker.markertype === 'newline') {
        el.classList.add('ke-region-nl')
        el.textContent = '␊'
      } else if (marker.markertype === 'setting' && marker.label === 'END') {
        el.classList.add('ke-region-end')
        el.textContent = 'END'
      } else {
        el.textContent = ''
      }
      return el
    },
    redrawRegions() {
      if (!this.wsRegions) return
      this.wsRegions.clearRegions()
      for (const m of this.markers) {
        this.wsRegions.addRegion({
          id: m.uid,
          start: m.time,
          content: this.regionContentEl(m),
          color: this.hexToRgba(m.color, 0.35),
          drag: this.canEdit,
          resize: false,
        })
      }
    },
    scheduleRedraw() {
      if (this.redrawScheduled) return
      this.redrawScheduled = true
      setTimeout(() => { this.redrawScheduled = false; this.redrawRegions() }, 0)
    },
    hexToRgba(hex, a) {
      if (!hex || hex[0] !== '#') return `rgba(210,105,30,${a})`
      const h = hex.slice(1)
      const r = parseInt(h.substring(0, 2), 16)
      const g = parseInt(h.substring(2, 4), 16)
      const b = parseInt(h.substring(4, 6), 16)
      return `rgba(${r},${g},${b},${a})`
    },
    // --- Транспорт ---
    playPause() { if (this.ws) this.ws.playPause() },
    step(sec) { if (this.ws) this.ws.setTime(Math.max(0, Math.min(this.duration, this.ws.getCurrentTime() + sec))) },
    applyRate() {
      if (this.ws) this.ws.setPlaybackRate(this.playbackRate)
      if (this.minusAudio) this.minusAudio.playbackRate = this.playbackRate
    },
    applyZoom() { if (this.ws) { try { this.ws.zoom(this.zoom) } catch (e) { /* noop */ } } },
    applyVolumes() {
      if (this.ws) this.ws.setVolume(this.volumeVoice)
      if (this.minusAudio) this.minusAudio.volume = this.volumeMinus
    },
    // --- Разметка ---
    mark(type) {
      if (!this.canEdit || !this.ws) return
      addMarker(this.markers, this.syllables, type, this.ws.getCurrentTime())
      this.redrawRegions()
      this.scheduleAutosave()
    },
    removeMarker() {
      if (!this.canEdit || !this.ws) return
      deleteMarkerAtTime(this.markers, this.syllables, this.ws.getCurrentTime())
      this.redrawRegions()
      this.scheduleAutosave()
    },
    onTextInput() {
      this.syllables = splitSyllables(this.sourceText)
      relabelSyllables(this.markers, this.syllables)
      this.scheduleRedraw()
      this.scheduleAutosave()
    },
    // --- Сохранение ---
    scheduleAutosave() {
      if (!this.canEdit) return
      this.saveState = 'idle'
      if (this.saveTimer) clearTimeout(this.saveTimer)
      this.saveTimer = setTimeout(() => this.saveNow(), 3000)
    },
    async saveNow() {
      if (!this.canEdit) return
      if (this.saveTimer) { clearTimeout(this.saveTimer); this.saveTimer = null }
      this.saveState = 'saving'
      const payload = JSON.stringify(markersToSave(this.markers))
      const { status, body } = await saveTask(this.$route.params.id, this.sourceText, payload)
      if (status === 200) {
        this.saveState = 'saved'
        if (body && body.status) this.status = body.status
      } else {
        this.saveState = 'error'
      }
    },
    async submit() {
      if (!this.canEdit) return
      this.submitting = true
      // Гарантируем END-маркер на длительности трека и сохраняем перед отправкой.
      ensureEndMarker(this.markers, this.duration)
      this.redrawRegions()
      await this.saveNow()
      const { status, body } = await submitTask(this.$route.params.id)
      this.submitting = false
      if (status === 200) {
        this.status = (body && body.status) || 'submitted'
        this.canEdit = false
      } else {
        this.saveState = 'error'
      }
    },
    // --- Клавиатура ---
    onKeyDown(e) {
      const tag = (e.target && e.target.tagName) || ''
      if (tag === 'TEXTAREA' || tag === 'INPUT') return
      if (!this.canEdit && e.code !== 'Space') {
        if (e.code === 'Space') { e.preventDefault(); this.playPause() }
        return
      }
      switch (e.code) {
        case 'Space': e.preventDefault(); this.playPause(); break
        case 'ArrowLeft': e.preventDefault(); this.step(-1); break
        case 'ArrowRight': e.preventDefault(); this.step(1); break
        case 'KeyS': e.preventDefault(); this.mark('syllables'); break
        case 'KeyQ': e.preventDefault(); this.mark('endofline'); break
        case 'KeyW': e.preventDefault(); this.mark('newline'); break
        case 'KeyD': e.preventDefault(); this.removeMarker(); break
        default: break
      }
    },
  }
}
</script>

<style scoped>
.km-page { min-height: 100vh; background: var(--km-bg); color: var(--km-text); padding-bottom: 5rem; }
.km-header { background: var(--km-header); border-bottom: 1px solid var(--km-border); padding: 0.5rem 1rem; }
.ke-sticky-top { position: sticky; top: 0; z-index: 20; }
.ke-header-inner { max-width: 900px; margin: 0 auto; display: flex; align-items: center; gap: 1rem; }
.km-back { color: var(--km-accent); text-decoration: none; font-size: 0.85rem; white-space: nowrap; }
.km-back:hover { text-decoration: underline; }
.ke-header-title { flex: 1; min-width: 0; display: flex; flex-direction: column; align-items: center; text-align: center; }
.ke-h-song { font-weight: 700; font-size: 1rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100%; }
.ke-h-author { color: var(--km-text2); font-size: 0.8rem; }
.ke-empty { max-width: 900px; margin: 0 auto; padding: 3rem 1rem; text-align: center; color: var(--km-text2); }

.ke-badge { font-size: 0.72rem; font-weight: 700; border-radius: 20px; padding: 0.22rem 0.7rem; white-space: nowrap; }
.ke-badge-assigned { background: #e2e6ea; color: #5a6570; }
.ke-badge-in_progress { background: #dbeafe; color: #1e5fbf; }
.ke-badge-submitted { background: #fef3c7; color: #92700a; }
.ke-badge-approved { background: #d1f5d8; color: #24803a; }
.ke-badge-rejected { background: #ffe0cc; color: #b8500f; }

.ke-work { max-width: 900px; margin: 0 auto; padding: 1rem; display: flex; flex-direction: column; gap: 1rem; }

.ke-reject-banner { background: #fff2e8; border: 1px solid #ffcfa8; color: #a9500f; border-radius: 12px; padding: 0.75rem 1rem; font-size: 0.9rem; }
.ke-info-banner { background: #fef8e3; border: 1px solid #f2dd9a; color: #8a6d0a; border-radius: 12px; padding: 0.75rem 1rem; font-size: 0.9rem; }
.ke-ok-banner { background: #e9faee; border: 1px solid #b6e6c2; color: #1f7a37; border-radius: 12px; padding: 0.75rem 1rem; font-size: 0.9rem; }

/* Бегущая строка */
.ke-tail-card {
  background: var(--km-card); border: 1px solid var(--km-border); border-radius: 16px;
  min-height: 96px; display: flex; align-items: center; justify-content: center; padding: 1rem;
  overflow: hidden;
}
.ke-tail-line { display: flex; align-items: baseline; gap: 0.5rem; flex-wrap: nowrap; white-space: nowrap; max-width: 100%; overflow: hidden; }
.ke-tail-begin, .ke-tail-end { color: var(--km-text2); font-size: 1.1rem; opacity: 0.7; }
.ke-tail-curr { color: #2563eb; font-size: 2.6rem; font-weight: 700; }
.ke-tail-next { color: #ef4444; font-size: 2.6rem; font-weight: 700; }

/* Waveform */
.ke-wave-card { background: var(--km-card); border: 1px solid var(--km-border); border-radius: 16px; padding: 0.75rem; }
.ke-waveform { width: 100%; }
.ke-time { text-align: right; color: var(--km-text2); font-size: 0.8rem; margin-top: 0.35rem; font-variant-numeric: tabular-nums; }

/* Транспорт */
.ke-transport { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; justify-content: center; }
.ke-tbtn {
  width: 52px; height: 52px; border-radius: 50%; border: 1px solid var(--km-border);
  background: var(--km-card); color: var(--km-text); font-size: 1.2rem; cursor: pointer;
  display: flex; align-items: center; justify-content: center; transition: background 0.15s;
}
.ke-tbtn:hover { background: var(--km-hover); }
.ke-tbtn-play { width: 64px; height: 64px; background: var(--km-accent); color: #fff; border: none; font-size: 1.5rem; }
.ke-tbtn-play:hover { opacity: 0.9; background: var(--km-accent); }
.ke-sliders { display: flex; gap: 1rem; flex-wrap: wrap; }
.ke-slider { display: flex; flex-direction: column; font-size: 0.72rem; color: var(--km-text2); gap: 0.2rem; min-width: 120px; }
.ke-slider input { width: 120px; }

/* Разметка */
.ke-markup { display: flex; gap: 0.75rem; flex-wrap: wrap; justify-content: center; }
.ke-mbtn {
  display: inline-flex; align-items: center; gap: 0.5rem; padding: 0.7rem 1.2rem;
  border-radius: 12px; border: 1px solid var(--km-border); background: var(--km-card);
  color: var(--km-text); font-size: 0.95rem; font-weight: 600; cursor: pointer; transition: all 0.15s;
}
.ke-mbtn:hover { transform: translateY(-1px); }
.ke-mbtn-ico { font-size: 1.1rem; }
.ke-mbtn kbd { font-size: 0.7rem; background: var(--km-hover); border: 1px solid var(--km-border); border-radius: 4px; padding: 0.05rem 0.35rem; color: var(--km-text2); }
.ke-mbtn-syl { border-color: #d2691e; }
.ke-mbtn-syl:hover { background: rgba(210,105,30,0.12); }
.ke-mbtn-eol { border-color: #ef4444; }
.ke-mbtn-eol:hover { background: rgba(239,68,68,0.1); }
.ke-mbtn-nl { border-color: #ef4444; }
.ke-mbtn-nl:hover { background: rgba(239,68,68,0.1); }
.ke-mbtn-del:hover { background: rgba(120,120,120,0.12); }

/* Текст */
.ke-texts { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
.ke-text-col { display: flex; flex-direction: column; gap: 0.4rem; }
.ke-col-title { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--km-text2); font-weight: 700; }
.ke-textarea {
  background: var(--km-input); color: var(--km-text); border: 1px solid var(--km-border);
  border-radius: 12px; padding: 0.75rem; font-size: 0.95rem; min-height: 240px; resize: vertical;
  line-height: 1.5; width: 100%;
}
.ke-textarea:focus { outline: none; border-color: var(--km-accent); }
.ke-preview {
  background: var(--km-input); border: 1px solid var(--km-border); border-radius: 12px;
  padding: 0.75rem; min-height: 240px; line-height: 1.7; font-size: 1rem; overflow-y: auto;
}
.ke-preview :deep(.ke-fx-cur) { color: #ef4444; font-weight: 800; }
.ke-preview :deep(.ke-fx-norm) { color: var(--km-text); }

/* Регионы на waveform */
:deep(.ke-region) { font-size: 11px; font-weight: 700; padding: 1px 3px; color: #222; }
:deep(.ke-region-eol), :deep(.ke-region-nl) { color: #b91c1c; }
:deep(.ke-region-end) { color: #1e3a8a; }

/* Футер */
.ke-footer {
  background: var(--km-header); border-top: 1px solid var(--km-border); padding: 0.6rem 1rem;
  display: flex; align-items: center; justify-content: space-between; gap: 1rem;
}
.ke-sticky-bottom { position: sticky; bottom: 0; z-index: 20; }
.ke-save-state { font-size: 0.82rem; }
.ke-save-saving { color: var(--km-text2); }
.ke-save-saved { color: #24803a; }
.ke-save-error { color: #d64545; }
.ke-footer-btns { display: flex; gap: 0.75rem; margin-left: auto; }
.ke-btn { border-radius: 10px; padding: 0.55rem 1.2rem; font-size: 0.9rem; font-weight: 600; cursor: pointer; border: 1px solid var(--km-border); }
.ke-btn-ghost { background: var(--km-card); color: var(--km-text); }
.ke-btn-ghost:hover { background: var(--km-hover); }
.ke-btn-primary { background: var(--km-accent); color: #fff; border: none; }
.ke-btn-primary:hover { opacity: 0.9; }
.ke-btn:disabled { opacity: 0.5; cursor: default; }

@media (max-width: 720px) {
  .ke-texts { grid-template-columns: 1fr; }
  .ke-tail-curr, .ke-tail-next { font-size: 2rem; }
  .ke-sliders { justify-content: center; }
}
</style>
