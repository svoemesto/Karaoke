<template>
  <div class="ske-page">
    <!-- Заголовок -->
    <div class="ske-header">
      <div class="ske-header-inner">
        <span class="ske-h-song" :title="songName">{{ songName }}</span>
        <span class="ske-h-author">{{ author }}</span>
      </div>
      <div class="ske-header-right">
        <span class="ske-badge" :class="`ske-badge-${statusKind}`">{{ statusLabel }}</span>
      </div>
    </div>

    <!-- Баннер отклонения (только в режиме assignment) -->
    <div v-if="showRejectBanner" class="ske-reject-banner">
      <strong>Возвращено на доработку:</strong> {{ reviewComment }}
    </div>

    <!-- Превью в настоящем плеере (через KaraokePlayer — наш webvue3-плеер с inline-данными) -->
    <div class="ske-player-toggle">
      <button class="ske-btn ske-btn-ghost" :disabled="playerLoading" @click="togglePlayer">
        {{ playerLoading ? 'Подготовка…' : (showPlayer ? 'Скрыть плеер' : '▶ Прослушать в плеере') }}
      </button>
    </div>
    <div v-if="showPlayer" class="ske-player-wrap">
      <div ref="playerContainer" class="ske-player-container"></div>
    </div>

    <!-- Голоса: задание покрывает всю песню — переключение/добавление/удаление голосов -->
    <div class="ske-voice-tabs">
      <button
          v-for="(v, i) in voices" :key="i" type="button" class="ske-voice-tab"
          :class="{ 'ske-voice-tab-active': currentVoiceIdx === i }"
          @click="setCurrentVoice(i)"
      >Голос {{ i + 1 }}</button>
      <button v-if="canEdit" type="button" class="ske-voice-tab ske-voice-tab-add" @click="addVoice">+ Голос</button>
      <button v-if="canEdit && voices.length > 1" type="button" class="ske-voice-tab ske-voice-tab-remove" @click="removeLastVoice">
        ✕ Удалить голос {{ voices.length }}
      </button>
    </div>

    <!-- Вейвформа -->
    <div class="ske-wave-card">
      <div ref="waveform" class="ske-waveform"></div>
      <div class="ske-time">{{ fmtTime(currentTime) }} / {{ fmtTime(duration) }}</div>
    </div>

    <!-- Бегущая строка -->
    <div class="ske-tail-card">
      <div class="ske-tail-line">
        <span class="ske-tail-begin">{{ tail.begin }}</span>
        <span class="ske-tail-curr">{{ tail.curr }}</span>
        <span class="ske-tail-next">{{ tail.next }}</span>
        <span class="ske-tail-end">{{ tail.end }}</span>
      </div>
    </div>

    <!-- Транспорт -->
    <div class="ske-transport">
      <button class="ske-tbtn" title="Назад 1с (←)" @click="step(-1)">⏮</button>
      <button class="ske-tbtn ske-tbtn-play" :title="isPlaying ? 'Пауза (Space/X)' : 'Играть (Space/X)'" @click="playPause">
        {{ isPlaying ? '⏸' : '▶' }}
      </button>
      <button class="ske-tbtn" title="Вперёд 1с (→)" @click="step(1)">⏭</button>

      <div class="ske-sliders">
        <label class="ske-slider">
          <span>Скорость {{ playbackRate.toFixed(2) }}×</span>
          <input type="range" min="0.3" max="1" step="0.05" v-model.number="playbackRate" @input="applyRate" />
        </label>
        <label class="ske-slider">
          <span>Масштаб</span>
          <input type="range" min="20" max="400" step="10" v-model.number="zoom" @input="applyZoom" />
        </label>
        <div class="ske-slider ske-sound-toggle">
          <span>Стем</span>
          <div class="ske-sound-btns">
            <button type="button" class="ske-sound-btn" :class="{ 'ske-sound-btn-active': activeSound === 'voice' }" @click="setActiveSound('voice')">Голос</button>
            <button type="button" class="ske-sound-btn" :class="{ 'ske-sound-btn-active': activeSound === 'music' }" @click="setActiveSound('music')">Музыка</button>
          </div>
        </div>
        <label class="ske-slider">
          <span>Громкость {{ Math.round(volume * 100) }}%</span>
          <input type="range" min="0" max="1" step="0.05" v-model.number="volume" @input="applyVolume" />
        </label>
      </div>
    </div>

    <!-- Тулбар клавиатуры-подсказки -->
    <div v-if="canEdit" class="ske-kb-toolbar">
      <button type="button" class="ske-btn ske-btn-ghost ske-kb-toggle" @click="showKeyboard = !showKeyboard">
        {{ showKeyboard ? 'Скрыть клавиатуру' : '⌨ Показать клавиатуру' }}
      </button>
      <button type="button" class="ske-btn ske-btn-ghost" @click="clearMarkers">Очистить маркеры</button>
    </div>
    <div v-if="canEdit && showKeyboard" class="ske-keyboard">
      <div class="ske-kb-grid">
        <div v-for="(row, ri) in keyboardRows" :key="ri" class="ske-kb-row">
          <button
              v-for="(k, ki) in row.keys" :key="k.code || ('blank-' + ri + '-' + ki)"
              type="button" class="ske-kb-key"
              :style="{ flexBasis: (k.w * 56 + (k.w - 1) * 6) + 'px' }"
              :class="{ 'ske-kb-key-active': k.code && heldKeys[k.code], 'ske-kb-key-inactive': !k.code, 'ske-kb-key-spacer': k.spacer }"
              @mousedown.prevent="pressKey(k.code)" @mouseup.prevent="releaseKey(k.code)"
              @mouseleave="releaseKey(k.code)"
          >
            <span class="ske-kb-key-label">{{ k.label }}</span>
            <span v-if="k.caption" class="ske-kb-key-caption">{{ k.caption }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- Текст + превью -->
    <div class="ske-texts">
      <div class="ske-text-col">
        <div class="ske-col-header">
          <div class="ske-col-title">Текст песни</div>
          <label class="ske-font-slider">
            <span>Шрифт {{ textFontSize }}px</span>
            <input type="range" min="6" max="36" step="1" v-model.number="textFontSize" />
          </label>
        </div>
        <textarea
          class="ske-textarea"
          :style="{ fontSize: textFontSize + 'px' }"
          v-model="sourceText"
          :placeholder="canEdit ? 'Вставьте сюда текст песни — он автоматически разобьётся на слоги.' : ''"
          @input="onTextInput"
        ></textarea>
      </div>
      <div class="ske-text-col">
        <div class="ske-col-header">
          <div class="ske-col-title">Разметка</div>
          <label class="ske-font-slider">
            <span>Шрифт {{ previewFontSize }}px</span>
            <input type="range" min="6" max="36" step="1" v-model.number="previewFontSize" />
          </label>
        </div>
        <div class="ske-preview" :style="{ fontSize: previewFontSize + 'px' }" v-html="formattedTextHtml"></div>
      </div>
    </div>
  </div>
</template>

<script>
import {
  splitSyllables, sortMarkers, relabelSyllables, currentSyllableIndex, currentMarkerIndex,
  addMarker, deleteMarkerAtTime, ensureEndMarker, formatText, buildTail, markersToSave, markersFromServer,
  adjacentMarkerTime, buildInlinePlayerData,
  loadEditorSettings, saveEditorSettings,
} from '../../composables/useKaraokeEditor'
import { STATUS_LABELS } from '../../composables/editorStatus'
import KaraokePlayer from '../../player/KaraokePlayer.js'

// Хоткеи 1:1 с karaoke-public / SubsEdit.vue. Digit6/Digit0 не перенесены (нет нот/аккордов и
// mute-регионов в упрощённом редакторе).
const WIRED_KEYS = new Set([
  'Space', 'KeyX', 'KeyQ', 'KeyE', 'KeyA', 'KeyD', 'KeyZ', 'KeyC',
  'BracketLeft', 'BracketRight', 'Comma', 'Period',
  'KeyS', 'KeyW', 'Digit1', 'Digit2', 'Digit3', 'Digit4', 'Digit5',
  'KeyT', 'KeyY', 'KeyU', 'KeyI', 'KeyP', 'KeyO',
])
const ALWAYS_ALLOWED_KEYS = new Set([
  'Space', 'KeyX', 'KeyQ', 'KeyE', 'KeyA', 'KeyD', 'KeyZ', 'KeyC',
  'BracketLeft', 'BracketRight', 'Comma', 'Period',
])

export default {
  name: 'SongKaraokeEditorView',
  props: {
    mode: { type: String, required: true, validator: v => v === 'song' || v === 'assignment' },
    songId: { type: Number, required: true },
    assignmentId: { type: Number, default: null },
    songName: { type: String, default: '' },
    author: { type: String, default: '' },
    album: { type: String, default: '' },
    year: { type: [Number, null], default: null },
    track: { type: [Number, null], default: null },
    // ВАЖНО: prop НЕ должен называться `key`, потому что в HTML-шаблоне родителя `:key="x"`
    // интерпретируется Vue как vnode-директива (управление пересозданием компонента), а не как
    // передача props. Передаём под именем `tonality` и внутри маппим на `data.key` для KaraokePlayer.
    tonality: { type: String, default: null },
    bpm: { type: [Number, null], default: null },
    target: { type: String, default: 'local' },
    sourceTexts: { type: Array, default: () => [] },
    markersPerVoice: { type: Array, default: () => [] },
    audioVocalsUrl: { type: String, default: '' },
    audioAccompanimentUrl: { type: String, default: '' },
    audioBassUrl: { type: String, default: null },
    audioDrumsUrl: { type: String, default: null },
    albumImageUrl: { type: String, default: null },
    artistImageUrl: { type: String, default: null },
    exportBaseName: { type: String, default: '' },
    canEdit: { type: Boolean, default: true },
    reviewComment: { type: String, default: '' },
    status: { type: String, default: 'song' },
  },
  data() {
    return {
      voices: [],
      currentVoiceIdx: 0,
      ws: null,
      wsRegions: null,
      voiceUrl: '',
      minusUrl: '',
      currentTime: 0,
      duration: 0,
      isPlaying: false,
      // Настройки, персистентные между сессиями (см. loadEditorSettings/saveEditorSettings в
      // useKaraokeEditor.js) — всё, что юзер подкручивает ползунками, должно сохраняться и
      // подхватываться при следующем открытии редактора (как _loadPersistedSettings в плеере).
      ...loadEditorSettings(),
      heldKeys: {},
      scrubTimers: {},
      editSpeed: 0.75,
      saveState: 'idle',
      saveTimer: null,
      kpPlayer: null,
      showPlayer: false,
      playerLoading: false,
      redrawScheduled: false,
    }
  },
  computed: {
    statusLabel() {
      if (this.status === 'song' || !this.status) return STATUS_LABELS.song
      return STATUS_LABELS[this.status] || this.status
    },
    statusKind() { return this.status || 'song' },
    showRejectBanner() {
      return this.mode === 'assignment' && this.status === 'rejected' && this.reviewComment
    },
    currentVoiceData() { return this.voices[this.currentVoiceIdx] || { sourceText: '', markers: [], syllables: [] } },
    sourceText: {
      get() { return this.currentVoiceData.sourceText },
      set(v) { if (this.voices[this.currentVoiceIdx]) this.voices[this.currentVoiceIdx].sourceText = v },
    },
    markers: {
      get() { return this.currentVoiceData.markers },
      set(v) { if (this.voices[this.currentVoiceIdx]) this.voices[this.currentVoiceIdx].markers = v },
    },
    syllables: {
      get() { return this.currentVoiceData.syllables },
      set(v) { if (this.voices[this.currentVoiceIdx]) this.voices[this.currentVoiceIdx].syllables = v },
    },
    hasAnyMarkers() { return this.voices.some(v => v.markers.length > 0) },
    curSyllableIndex() { return currentSyllableIndex(this.markers, this.currentTime) },
    curMarkerIndex() { return currentMarkerIndex(this.markers, this.currentTime) },
    formattedTextHtml() { return formatText(this.markers, this.curMarkerIndex) },
    tail() { return buildTail(this.syllables, this.curSyllableIndex) },
    keyboardRows() {
      const K = (code, label, caption) => ({ code, label, caption, w: 1, spacer: false })
      const D = (label) => ({ code: null, label, caption: '', w: 1, spacer: false })
      const M = (label, w) => ({ code: null, label, caption: '', w, spacer: true })
      return [
        {
          keys: [
            K('Digit1', '1', 'конец слога'),
            K('Digit2', '2', 'конец строки'),
            K('Digit3', '3', 'стр.+слог'),
            K('Digit4', '4', 'нов. строка'),
            K('Digit5', '5', 'стр.+нов.+слог'),
            D('6'), D('7'), D('8'), D('9'), D('0'),
          ],
        },
        {
          keys: [
            M('⇥', 0.5),
            K('KeyQ', 'Q', '−0.01с'),
            K('KeyW', 'W', 'слог'),
            K('KeyE', 'E', '+0.01с'),
            D('R'),
            K('KeyT', 'T', 'группа 0'),
            K('KeyY', 'Y', 'группа 1'),
            K('KeyU', 'U', 'группа 2'),
            K('KeyI', 'I', 'группа 3'),
            K('KeyO', 'O', 'коммент'),
            K('KeyP', 'P', 'группа 4'),
            K('BracketLeft', '[', 'пред. маркер'),
            K('BracketRight', ']', 'след. маркер'),
          ],
        },
        {
          keys: [
            M('⇪', 1),
            K('KeyA', 'A', '◀◀ медл.'),
            K('KeyS', 'S', 'удалить'),
            K('KeyD', 'D', '▶ медл.'),
            D('F'), D('G'), D('H'), D('J'), D('K'), D('L'),
          ],
        },
        {
          keys: [
            M('⇧', 1.5),
            K('KeyZ', 'Z', '◀◀ быстро'),
            K('KeyX', 'X', 'play/pause'),
            K('KeyC', 'C', '▶▶ быстро'),
            D('V'), D('B'), D('N'), D('M'),
            K('Comma', ',', 'пред. слог'),
            K('Period', '.', 'след. слог'),
          ],
        },
      ]
    },
  },
  watch: {
    // Каждое изменение ползунка / настройки — сразу сохраняем в localStorage, чтобы при
    // следующем открытии редактора значения подхватились (см. loadEditorSettings).
    textFontSize(v) { saveEditorSettings({ textFontSize: v }) },
    previewFontSize(v) { saveEditorSettings({ previewFontSize: v }) },
    volume(v) { saveEditorSettings({ volume: v }) },
    playbackRate(v) { saveEditorSettings({ playbackRate: v }) },
    zoom(v) { saveEditorSettings({ zoom: v }) },
    activeSound(v) { saveEditorSettings({ activeSound: v }) },
    showKeyboard(v) { saveEditorSettings({ showKeyboard: v }) },
  },
  async mounted() {
    this.voiceUrl = this.audioVocalsUrl
    this.minusUrl = this.audioAccompanimentUrl
    this.loadVoicesFromProps()
    await this.$nextTick()
    await this.initWaveSurfer()
    window.addEventListener('keydown', this.onKeyDown)
    window.addEventListener('keyup', this.onKeyUp)
    window.addEventListener('resize', this.onWindowResizePlayer)
  },
  beforeUnmount() {
    window.removeEventListener('keydown', this.onKeyDown)
    window.removeEventListener('keyup', this.onKeyUp)
    window.removeEventListener('resize', this.onWindowResizePlayer)
    for (const code of Object.keys(this.scrubTimers)) clearInterval(this.scrubTimers[code])
    if (this.saveTimer) clearTimeout(this.saveTimer)
    try { this.ws && this.ws.destroy() } catch (e) { /* noop */ }
    try { this.kpPlayer && this.kpPlayer.destroy() } catch (e) { /* noop */ }
  },
  methods: {
    fmtTime(s) {
      if (!s || s < 0) s = 0
      const m = Math.floor(s / 60)
      const sec = Math.floor(s % 60)
      return `${m}:${sec.toString().padStart(2, '0')}`
    },
    loadVoicesFromProps() {
      // Задание покрывает ВСЮ песню — массив sourceTexts[]/markersPerVoice[], индекс = номер голоса.
      // Длины МОГУТ разойтись (см. Settings.kt: setSourceText/setSourceMarkers — два отдельных
      // вызова, не атомарны) — берём максимум, недостающее считаем пустым.
      const rawTexts = (this.sourceTexts && this.sourceTexts.length) ? this.sourceTexts : ['']
      const rawMarkers = (this.markersPerVoice && this.markersPerVoice.length) ? this.markersPerVoice : [[]]
      const voiceCount = Math.max(rawTexts.length, rawMarkers.length)
      this.voices = []
      for (let i = 0; i < voiceCount; i++) {
        const text = rawTexts[i] || ''
        const markers = markersFromServer(rawMarkers[i] || [])
        const syllables = splitSyllables(text)
        relabelSyllables(markers, syllables)
        this.voices.push({ sourceText: text, markers, syllables })
      }
      this.currentVoiceIdx = 0
    },
    async initWaveSurfer() {
      if (!this.$refs.waveform) return
      const { default: WaveSurfer } = await import('wavesurfer.js')
      const { default: RegionsPlugin } = await import('wavesurfer.js/dist/plugins/regions.esm.js')
      const { default: Minimap } = await import('wavesurfer.js/dist/plugins/minimap.esm.js')
      const styles = getComputedStyle(document.documentElement)
      const accent = (styles.getPropertyValue('--bs-primary') || styles.getPropertyValue('--km-accent') || '#3b82f6').trim()

      this.ws = WaveSurfer.create({
        container: this.$refs.waveform,
        height: 140,
        waveColor: '#9db4d6',
        progressColor: accent,
        cursorColor: '#ff5252',
        cursorWidth: 2,
        minPxPerSec: this.zoom,
        autoScroll: true,
        autoCenter: true,
        normalize: true,
        plugins: [
          Minimap.create({
            height: 20,
            waveColor: 'rgb(255, 0, 0)',
            progressColor: 'rgb(100, 0, 100)',
          }),
        ],
      })
      this.wsRegions = this.ws.registerPlugin(RegionsPlugin.create())

      this.ws.on('decode', () => {
        this.duration = this.ws.getDuration()
        this.ws.setVolume(this.volume)
        this.ws.setPlaybackRate(this.playbackRate)
        this.redrawRegions()
      })
      this.ws.on('timeupdate', (t) => { this.currentTime = t })
      this.ws.on('play', () => { this.isPlaying = true })
      this.ws.on('pause', () => { this.isPlaying = false })
      this.wsRegions.on('region-updated', (region) => {
        const marker = this.markers.find(m => m.uid === region.id)
        if (!marker) return
        marker.time = region.start
        sortMarkers(this.markers)
        relabelSyllables(this.markers, this.syllables)
        this.scheduleRedraw()
        this.$emit('change')
      })

      this.ws.load(this.activeSound === 'voice' ? this.voiceUrl : this.minusUrl)
    },
    regionContentEl(marker) {
      const el = document.createElement('div')
      el.style.fontSize = '9px'
      el.style.fontWeight = '700'
      el.style.padding = '1px 3px'
      el.style.color = '#222'
      if (marker.markertype === 'syllables') {
        const label = document.createElement('span')
        label.style.backgroundColor = 'beige'
        label.style.display = 'inline-block'
        label.style.padding = '0 2px'
        label.style.whiteSpace = 'nowrap'
        label.textContent = (marker.label || '').replaceAll('_', ' ').trim() || '·'
        el.appendChild(label)
      } else if (marker.markertype === 'endofline') {
        el.style.color = '#b91c1c'
        el.textContent = '⏎'
      } else if (marker.markertype === 'newline') {
        el.style.color = '#b91c1c'
        el.textContent = '␊'
      } else if (marker.markertype === 'setting' && marker.label === 'END') {
        el.style.color = '#1e3a8a'
        el.textContent = 'END'
      } else if (marker.markertype === 'setting' && marker.label && marker.label.startsWith('GROUP|')) {
        el.style.color = '#000080'
        el.textContent = 'G' + marker.label.split('|')[1]
      } else if (marker.markertype === 'setting' && marker.label && marker.label.startsWith('COMMENT|')) {
        el.style.color = '#000080'
        el.textContent = '💬'
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
    centerOnTime(t) {
      if (!this.ws || !this.$refs.waveform || !this.zoom) return
      const visibleSeconds = this.$refs.waveform.clientWidth / this.zoom
      this.ws.setScrollTime(Math.max(0, t - visibleSeconds / 2))
    },
    playPause() { if (this.ws) this.ws.playPause() },
    step(sec) { if (this.ws) this.ws.setTime(Math.max(0, Math.min(this.duration, this.ws.getCurrentTime() + sec))) },
    applyRate() { if (this.ws) this.ws.setPlaybackRate(this.playbackRate) },
    applyZoom() { if (this.ws) { try { this.ws.zoom(this.zoom) } catch (e) { /* noop */ } } },
    applyVolume() { if (this.ws) this.ws.setVolume(this.volume) },
    async setActiveSound(sound) {
      if (this.activeSound === sound || !this.ws) { this.activeSound = sound; return }
      const wasPlaying = this.ws.isPlaying()
      const t = this.ws.getCurrentTime()
      this.activeSound = sound
      const url = sound === 'voice' ? this.voiceUrl : this.minusUrl
      if (!url) return
      await this.ws.load(url)
      this.ws.setTime(t)
      if (wasPlaying) this.ws.play()
    },
    setCurrentVoice(idx) {
      if (idx < 0 || idx >= this.voices.length || idx === this.currentVoiceIdx) return
      if (this.wsRegions) this.wsRegions.clearRegions()
      this.currentVoiceIdx = idx
      this.redrawRegions()
    },
    addVoice() {
      if (!this.canEdit) return
      this.voices.push({ sourceText: '', markers: [], syllables: [] })
      this.setCurrentVoice(this.voices.length - 1)
      this.$emit('change')
    },
    removeLastVoice() {
      if (!this.canEdit || this.voices.length <= 1) return
      if (!window.confirm(`Удалить голос ${this.voices.length}? Весь его текст и маркеры будут потеряны.`)) return
      this.voices.pop()
      if (this.currentVoiceIdx >= this.voices.length) this.currentVoiceIdx = this.voices.length - 1
      if (this.wsRegions) this.wsRegions.clearRegions()
      this.redrawRegions()
      this.$emit('change')
    },
    clearMarkers() {
      if (!this.canEdit) return
      if (!window.confirm('Удалить все маркеры разметки? Отменить это действие будет нельзя.')) return
      this.markers = []
      this.redrawRegions()
      this.$emit('change')
    },
    mark(type, notDelete = false, label = '') {
      if (!this.canEdit || !this.ws) return
      addMarker(this.markers, this.syllables, type, this.ws.getCurrentTime(), notDelete, label)
      this.redrawRegions()
      this.$emit('change')
    },
    addComment() {
      if (!this.canEdit) return
      const text = window.prompt('Текст комментария (отобразится курсивом в разметке):', '')
      if (!text || !text.trim()) return
      this.mark('setting', false, 'COMMENT|' + text.trim())
    },
    removeMarker() {
      if (!this.canEdit || !this.ws) return
      const idx = deleteMarkerAtTime(this.markers, this.syllables, this.ws.getCurrentTime())
      this.redrawRegions()
      this.$emit('change')
      if (idx >= 0) {
        let t = null
        if (idx < this.markers.length) t = this.markers[idx].time
        else if (this.markers.length > 0) t = this.markers[idx - 1].time
        if (t !== null) { this.ws.setTime(t); this.centerOnTime(t) }
      }
    },
    onTextInput() {
      this.syllables = splitSyllables(this.sourceText)
      relabelSyllables(this.markers, this.syllables)
      this.scheduleRedraw()
      this.$emit('change')
    },
    currentSnapshot() {
      const sourceTexts = this.voices.map(v => v.sourceText)
      const markersPerVoice = this.voices.map(v => markersToSave(v.markers))
      return JSON.stringify({ sourceTexts, markersPerVoice })
    },
    async togglePlayer() {
      if (this.showPlayer) { this.showPlayer = false; return }
      this.playerLoading = true
      try {
        // Используем inlineData — превью сразу показывает текущий черновик в памяти, без
        // промежуточного HTTP-запроса /api/song/{id}/playerdata.
        //
        // ВАЖНО: порядок шагов здесь критичен.
        //   1) сначала this.showPlayer = true — иначе контейнер <div ref="playerContainer">
        //      не отрендерен, $refs.playerContainer === null, и previous-версия уходила в
        //      ранний return. Ошибка выглядела как «ничего не происходит при клике».
        //   2) await $nextTick() — дать Vue материализовать ref в DOM.
        // Каждый показ — новый инстанс плеера (KaraokePlayer крепко привязывается к DOM
        // контейнеру при init() и крашит любые попытки пере-attachить его).
        this.showPlayer = true
        await this.$nextTick()
        const inlineData = buildInlinePlayerData({
          songId: this.songId,
          songName: this.songName,
          author: this.author,
          album: this.album || '',
          year: this.year != null ? this.year : null,
          track: this.track != null ? this.track : null,
          key: this.tonality || null,
          bpm: this.bpm != null ? this.bpm : null,
          sourceTexts: this.voices.map(v => v.sourceText),
          markersPerVoice: this.voices.map(v => markersToSave(v.markers)),
          audioVocalsUrl: this.voiceUrl,
          audioAccompanimentUrl: this.minusUrl,
          audioBassUrl: this.audioBassUrl || null,
          audioDrumsUrl: this.audioDrumsUrl || null,
          albumImageUrl: this.albumImageUrl || null,
          artistImageUrl: this.artistImageUrl || null,
          exportBaseName: this.exportBaseName || '',
        })
        if (this.kpPlayer) { try { this.kpPlayer.destroy() } catch (e) {} this.kpPlayer = null }
        if (!this.$refs.playerContainer) return
        // Пропорции экрана плеера должны быть 16:9 — фактически canvas (kp-canvas-wrap), а не
        // весь root-div плеера. В KaraokePlayer root-div высотой X делит её между kp-canvas-wrap
        // (flex:1) и блоками controls (#kp-controls-volume + #kp-controls-bottom с wave-формами,
        // прогрессом, play/меню) ≈ 100-110 px. Передаём containerHeight с запасом под controls,
        // а после init калибруем по факту — это единственный надёжный способ (хардкод высоты
        // controls хрупкий, реальная высота видна только в DOM после инициализации).
        const wrap = this.$refs.playerContainer.parentElement
        const w = wrap.clientWidth
        if (w <= 0) return
        const targetCanvasH = Math.round(w * 9 / 16)
        // Прикидка высоты controls — для корректной первой инициализации canvas (иначе до
        // калибровки canvas получится на ~controlsH короче, чем нужно). ~110 px обычно (volume+
        // bottom), но при ресайзе вниз может сжиматься; для оценочной инициализации хватает.
        const ESTIMATED_CONTROLS_H = 110
        const initialH = targetCanvasH + ESTIMATED_CONTROLS_H
        wrap.style.height = initialH + 'px'
        this.kpPlayer = new KaraokePlayer(this.$refs.playerContainer, { inlineData, containerHeight: initialH + 'px' }, '/api')
        // init() async и без return-check'а внутри тянет FontFace'ы, аудио и т.п. — без await
        // ошибка уходит в KaraokePlayer'овский console.error и пользователь снова ничего не
        // видит. Логируем наружу, кнопка после init() остаётся разблокированной.
        try {
          await this.kpPlayer.init()
        } catch (e) {
          console.error('KaraokePlayer.init() failed:', e)
        }
        // Калибруем: измеряем фактическую высоту kp-canvas-wrap и общую высоту плеера
        // (она = rootDiv.clientHeight). controlsH = rootDiv - canvasWrap (реальная высота controls
        // после рендера). Если фактическая canvas высота не равна targetCanvasH 16:9 —
        // пересчитываем rootDiv высоту и обновляем layout.
        const canvasWrap = this.$refs.playerContainer.querySelector('#kp-canvas-wrap')
        const rootDiv = canvasWrap ? canvasWrap.parentElement : null
        if (canvasWrap && rootDiv) {
          const actualCanvasH = canvasWrap.clientHeight
          const actualRootH = rootDiv.clientHeight
          const actualControlsH = actualRootH - actualCanvasH
          const targetRootH = targetCanvasH + actualControlsH
          if (Math.abs(actualRootH - targetRootH) > 1) {
            wrap.style.height = targetRootH + 'px'
            rootDiv.style.height = targetRootH + 'px'
            if (typeof this.kpPlayer._resizeCanvas === 'function') this.kpPlayer._resizeCanvas()
          }
        }
      } finally {
        this.playerLoading = false
      }
    },
    // Желаемая высота плеера = желаемая высота canvas (16:9) + фактическая высота controls.
    // Конкретный случай — handle window.resize: clientWidth wrap'а изменился, надо сохранить
    // пропорцию canvas 16:9 и при этом сохранить текущую высоту controls (она зависит от layout,
    // а не от containerHeight). Чтобы узнать актуальную высоту controls, читаем rootDiv (первый
    // дочерний div внутри container). Минимальный fallback — 110 px, если по какой-то причине
    // не удалось измерить.
    computePlayerHeight16x9(wrap) {
      if (!wrap) return 0
      const w = wrap.clientWidth
      if (w <= 0) return 0
      const canvasWrap = this.$refs.playerContainer && this.$refs.playerContainer.querySelector('#kp-canvas-wrap')
      const rootDiv = canvasWrap ? canvasWrap.parentElement : null
      let controlsH = 110
      if (rootDiv && canvasWrap && rootDiv.clientHeight > 0 && canvasWrap.clientHeight > 0) {
        controlsH = Math.max(0, rootDiv.clientHeight - canvasWrap.clientHeight)
      }
      const targetCanvasH = Math.round(w * 9 / 16)
      return targetCanvasH + controlsH
    },
    onWindowResizePlayer() {
      if (!this.showPlayer) return
      const wrap = this.$refs.playerContainer && this.$refs.playerContainer.parentElement
      const h = this.computePlayerHeight16x9(wrap)
      if (h <= 0 || !wrap) return
      wrap.style.height = h + 'px'
      if (this.kpPlayer) {
        // Внутренний div плеера (rootDiv) был зафиксирован по containerHeight при init(); без
        // явного обновления canvas будет иметь старую высоту и текст «выползет» / обрежется.
        const rootDiv = this.$refs.playerContainer.querySelector(':scope > div')
        if (rootDiv) rootDiv.style.height = h + 'px'
        if (typeof this.kpPlayer._resizeCanvas === 'function') this.kpPlayer._resizeCanvas()
      }
    },
    onKeyDown(e) {
      const tag = (e.target && e.target.tagName) || ''
      if (tag === 'TEXTAREA' || tag === 'INPUT') return
      if (e.code === 'ArrowLeft') { e.preventDefault(); this.step(-1); return }
      if (e.code === 'ArrowRight') { e.preventDefault(); this.step(1); return }
      if (!WIRED_KEYS.has(e.code)) return
      if (!this.canEdit && !ALWAYS_ALLOWED_KEYS.has(e.code)) return
      if (e.repeat) return
      e.preventDefault()
      this.pressKey(e.code)
    },
    onKeyUp(e) {
      if (!WIRED_KEYS.has(e.code)) return
      this.releaseKey(e.code)
    },
    pressKey(code) {
      if (!code || this.heldKeys[code]) return
      this.heldKeys[code] = true
      this.dispatchKeyAction(code)
    },
    releaseKey(code) {
      if (!code) return
      this.heldKeys[code] = false
      this.stopScrub(code)
    },
    dispatchKeyAction(code) {
      switch (code) {
        case 'Space': case 'KeyX': this.playPause(); break
        case 'KeyQ': if (this.ws) this.ws.skip(-0.01); break
        case 'KeyE': if (this.ws) this.ws.skip(0.01); break
        case 'KeyA': this.startScrub('KeyA', -1, false); break
        case 'KeyD': this.startScrub('KeyD', 1, false); break
        case 'KeyZ': this.startScrub('KeyZ', -1, true); break
        case 'KeyC': this.startScrub('KeyC', 1, true); break
        case 'BracketLeft': this.jumpMarker(-1, null); this.startNavRepeat('BracketLeft', -1, null); break
        case 'BracketRight': this.jumpMarker(1, null); this.startNavRepeat('BracketRight', 1, null); break
        case 'Comma': this.jumpMarker(-1, 'syllables'); this.startNavRepeat('Comma', -1, 'syllables'); break
        case 'Period': this.jumpMarker(1, 'syllables'); this.startNavRepeat('Period', 1, 'syllables'); break
        case 'KeyS': if (this.canEdit) this.removeMarker(); break
        case 'KeyW': if (this.canEdit) this.mark('syllables'); break
        case 'Digit1': if (this.canEdit) this.mark('endofsyllable'); break
        case 'Digit2': if (this.canEdit) this.mark('endofline'); break
        case 'Digit3': if (this.canEdit) { this.mark('endofline'); this.mark('syllables', true) }; break
        case 'Digit4': if (this.canEdit) this.mark('newline'); break
        case 'Digit5': if (this.canEdit) { this.mark('endofline'); this.mark('newline', true); this.mark('syllables', true) }; break
        case 'KeyT': if (this.canEdit) this.mark('setting', false, 'GROUP|0'); break
        case 'KeyY': if (this.canEdit) this.mark('setting', false, 'GROUP|1'); break
        case 'KeyU': if (this.canEdit) this.mark('setting', false, 'GROUP|2'); break
        case 'KeyI': if (this.canEdit) this.mark('setting', false, 'GROUP|3'); break
        case 'KeyP': if (this.canEdit) this.mark('setting', false, 'GROUP|4'); break
        case 'KeyO': this.addComment(); break
        default: break
      }
    },
    startScrub(code, direction, fast) {
      if (this.scrubTimers[code] || !this.ws) return
      if (fast) {
        this.scrubTimers[code] = setInterval(() => this.ws.skip(direction * 0.1), 50)
      } else if (direction < 0) {
        this.ws.setPlaybackRate(this.editSpeed)
        this.scrubTimers[code] = setInterval(() => this.ws.skip(-this.editSpeed * 0.01), 10)
      } else {
        this.ws.setPlaybackRate(this.editSpeed)
        this.scrubTimers[code] = setInterval(() => { if (!this.ws.isPlaying()) this.ws.play() }, 16)
      }
    },
    startNavRepeat(code, direction, filterType) {
      if (this.scrubTimers[code]) return
      this.scrubTimers[code] = setInterval(() => this.jumpMarker(direction, filterType), 100)
    },
    stopScrub(code) {
      if (this.scrubTimers[code]) { clearInterval(this.scrubTimers[code]); delete this.scrubTimers[code] }
      if ((code === 'KeyA' || code === 'KeyD') && this.ws) {
        this.ws.setPlaybackRate(this.playbackRate)
        if (this.ws.isPlaying()) this.ws.pause()
      }
    },
    jumpMarker(direction, filterType) {
      if (!this.ws) return
      const t = adjacentMarkerTime(this.markers, this.ws.getCurrentTime(), direction, filterType)
      if (t !== null) { this.ws.setTime(t); this.centerOnTime(t) }
    },
  },
}
</script>

<style scoped>
/* Стили админского онлайн-редактора караоке-разметки (SongKaraokeEditorView) — зеркало 1:1 с
   public-редактором (karaoke-public/src/views/EditorWorkView.vue). Все классы имеют префикс ske-
   вместо ke-, а CSS-переменные --km-* из public-light-палитры (karaoke-public/src/style.css:16-29)
   захардкожены в конкретные значения, чтобы не зависеть от глобальной темы webvue3.

   Отличия от public:
   • position: sticky НЕ применён — обёртка модальная (.skm-body { overflow-y: auto } в
     SongKaraokeEditorModal.vue), sticky наружу модалки не работает и не нужен.
   • .ske-page — наш flex-контейнер (в public это .km-page как обёртка всей страницы editor).
   • Локальный бейдж .ske-badge-song дополнительно к стандартным статусам задания — используется
     в mode='song' (см. статус «Песня» в editorStatus.js). */

.ske-page {
  background: #f4f6fb;
  color: #1a1a2e;
  font-family: Avenir, Helvetica, Arial, sans-serif;
  font-weight: 400;
  font-size: 14px;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 0 0 0 0;
}

/* Заголовок внутри редактора. Sticky-вариант из public не применяется — обёртка модальная. */
.ske-header {
  background: #ffffff;
  border-bottom: 1px solid #c8cadb;
  padding: 0.5rem 1rem;
}
.ske-header-inner { display: flex; align-items: center; gap: 1rem; }
.ske-h-song { font-weight: 700; font-size: 1rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ske-h-author { color: #5a5a80; font-size: 0.8rem; }
.ske-header-right { margin-left: auto; flex-shrink: 0; }

.ske-empty { padding: 3rem 1rem; text-align: center; color: #5a5a80; }

/* Бейджи статуса. Те же цвета, что в public-light, плюс дополнительный .ske-badge-song. */
.ske-badge { font-size: 0.72rem; font-weight: 700; border-radius: 20px; padding: 0.22rem 0.7rem; white-space: nowrap; }
.ske-badge-assigned { background: #e2e6ea; color: #5a6570; }
.ske-badge-in_progress { background: #dbeafe; color: #1e5fbf; }
.ske-badge-submitted { background: #fef3c7; color: #92700a; }
.ske-badge-approved { background: #d1f5d8; color: #1f7a37; }
.ske-badge-rejected { background: #ffe0cc; color: #b8500f; }
.ske-badge-song { background: #ecf5ee; color: #24803a; }

.ske-reject-banner { background: #fff2e8; border: 1px solid #ffcfa8; color: #a9500f; border-radius: 12px; padding: 0.75rem 1rem; font-size: 0.9rem; }

/* Превью в плеере — обёртка под KaraokePlayer (admin) либо iframe (если когда-то потребуется). */
.ske-player-toggle { display: flex; justify-content: center; }
/* Пропорции экрана плеера — 16:9. aspect-ratio даёт точную высоту от ширины родителя, а на
   ресайз окна — пересчёт в методе onWindowResizePlayer (handler на window.resize) с передачей
   новой высоты в KaraokePlayer через containerHeight-перезапись внутреннего div. */
.ske-player-wrap { width: 100%; aspect-ratio: 16 / 9; border-radius: 16px; overflow: hidden; background: #000; }
.ske-player-container { width: 100%; height: 100%; }
.ske-player-frame { width: 100%; height: 100%; border: none; display: block; }

/* Голоса. */
.ske-voice-tabs { display: flex; gap: 0.5rem; flex-wrap: wrap; justify-content: center; }
.ske-voice-tab {
  border: 1px solid #c8cadb; border-radius: 20px; padding: 0.35rem 1rem; background: #ffffff;
  color: #1a1a2e; cursor: pointer; font-size: 0.82rem; font-weight: 600;
}
.ske-voice-tab:hover { background: #eeeeff; }
.ske-voice-tab-active { background: #5c4de0; color: #fff; border-color: #5c4de0; }
.ske-voice-tab-add { border-color: #24803a; color: #24803a; background: transparent; }
.ske-voice-tab-add:hover { background: rgba(36,128,58,0.1); }
.ske-voice-tab-remove { border-color: #c0392b; color: #c0392b; background: transparent; }
.ske-voice-tab-remove:hover { background: rgba(192,57,43,0.1); }

/* Бегущая строка. */
.ske-tail-card {
  background: #ffffff; border: 1px solid #c8cadb; border-radius: 16px;
  min-height: 96px; display: flex; align-items: center; justify-content: center; padding: 1rem;
  overflow: hidden;
}
.ske-tail-line { display: flex; align-items: baseline; gap: 0.5rem; flex-wrap: nowrap; white-space: nowrap; max-width: 100%; overflow: hidden; }
.ske-tail-begin, .ske-tail-end { color: #5a5a80; font-size: 1.1rem; opacity: 0.7; }
.ske-tail-curr { color: #2563eb; font-size: 2.6rem; font-weight: 700; }
.ske-tail-next { color: #ef4444; font-size: 2.6rem; font-weight: 700; }

/* Вейвформа. */
.ske-wave-card { background: #ffffff; border: 1px solid #c8cadb; border-radius: 16px; padding: 0.75rem; }
.ske-waveform { width: 100%; }
.ske-time { text-align: right; color: #5a5a80; font-size: 0.8rem; margin-top: 0.35rem; font-variant-numeric: tabular-nums; }

/* Транспорт. */
.ske-transport { display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap; justify-content: center; }
.ske-tbtn {
  width: 52px; height: 52px; border-radius: 50%; border: 1px solid #c8cadb;
  background: #ffffff; color: #1a1a2e; font-size: 1.2rem; cursor: pointer;
  display: flex; align-items: center; justify-content: center; transition: background 0.15s;
}
.ske-tbtn:hover { background: #eeeeff; }
.ske-tbtn-play { width: 64px; height: 64px; background: #5c4de0; color: #fff; border: none; font-size: 1.5rem; }
.ske-tbtn-play:hover { opacity: 0.9; background: #5c4de0; }
.ske-sliders { display: flex; gap: 1rem; flex-wrap: wrap; }
.ske-slider { display: flex; flex-direction: column; font-size: 0.72rem; color: #5a5a80; gap: 0.2rem; min-width: 120px; }
.ske-slider input { width: 120px; }
.ske-sound-toggle { min-width: unset; }
.ske-sound-btns { display: flex; gap: 0.3rem; }
.ske-sound-btn {
  border: 1px solid #c8cadb; background: #ffffff; color: #1a1a2e;
  border-radius: 8px; padding: 0.25rem 0.6rem; font-size: 0.78rem; cursor: pointer;
}
.ske-sound-btn:hover { background: #eeeeff; }
.ske-sound-btn-active { background: #5c4de0; color: #fff; border-color: #5c4de0; }

/* Нарисованная клавиатура-подсказка. Центрируется вся сетка, ряды прижаты к общему левому
   краю относительно друг друга (align-items: flex-start), иначе сдвиги между рядами развалятся. */
.ske-kb-toolbar { display: flex; justify-content: center; gap: 0.75rem; flex-wrap: wrap; }
.ske-keyboard { display: flex; justify-content: center; }
.ske-kb-grid { display: flex; flex-direction: column; gap: 0.4rem; align-items: flex-start; }
.ske-kb-row { display: flex; gap: 0.4rem; }
.ske-kb-key {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 0.15rem;
  flex: 0 0 auto; height: 52px; padding: 0.2rem 0.4rem; border-radius: 8px;
  border: 1px solid #c8cadb; background: #ffffff; color: #1a1a2e;
  cursor: pointer; transition: background 0.1s, transform 0.05s; user-select: none;
}
.ske-kb-key:hover { background: #eeeeff; }
.ske-kb-key-label { font-size: 1rem; font-weight: 700; line-height: 1; }
.ske-kb-key-caption { font-size: 0.6rem; color: #5a5a80; line-height: 1; white-space: nowrap; }
.ske-kb-key-active {
  background: #5c4de0; border-color: #5c4de0; transform: translateY(1px);
}
.ske-kb-key-active .ske-kb-key-label, .ske-kb-key-active .ske-kb-key-caption { color: #fff; }
.ske-kb-key-inactive { opacity: 0.32; cursor: default; }
.ske-kb-key-inactive:hover { background: #ffffff; }
.ske-kb-key-spacer { padding: 0.2rem 0.05rem; }
.ske-kb-key-spacer .ske-kb-key-label { font-size: 0.8rem; }

/* Кнопки. */
.ske-btn { border-radius: 10px; padding: 0.55rem 1.2rem; font-size: 0.9rem; font-weight: 600; cursor: pointer; border: 1px solid #c8cadb; }
.ske-btn-ghost { background: #ffffff; color: #1a1a2e; }
.ske-btn-ghost:hover { background: #eeeeff; }
.ske-btn-primary { background: #5c4de0; color: #fff; border: none; }
.ske-btn-primary:hover { opacity: 0.9; }
.ske-btn:disabled { opacity: 0.5; cursor: default; }

/* Текст + превью. font-size у preview наследуется от .ske-preview (регулируется слайдером).
   Базовый шрифт всего редактора — Avenir (как у всего admin), font-weight 400 явно зафиксирован
   на .ske-page и textarea/preview, чтобы KaraokePlayer-овский Roboto Black (900), подгружаемый
   локально, не прокидывал свой вес на текстовые блоки редактора (раньше .ske-page наследовал
   300 → fallback на 500/700 при отсутствии нужного веса → визуально жирный). */
.ske-texts { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
.ske-text-col { display: flex; flex-direction: column; gap: 0.4rem; }
.ske-col-header { display: flex; align-items: center; justify-content: space-between; gap: 0.75rem; flex-wrap: wrap; }
.ske-col-title { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.04em; color: #5a5a80; font-weight: 700; }
.ske-font-slider { display: flex; align-items: center; gap: 0.4rem; font-size: 0.72rem; color: #5a5a80; }
.ske-font-slider input { width: 110px; }
.ske-textarea {
  background: #f8f9ff; color: #1a1a2e; border: 1px solid #c8cadb;
  border-radius: 12px; padding: 0.75rem; font-size: 0.95rem; min-height: 520px; resize: vertical;
  line-height: 1.5; width: 100%; font-weight: 400;
}
.ske-textarea:focus { outline: none; border-color: #5c4de0; }
/* Превью разметки — чёрный фон, многоколоночная вёрстка (1:1 с public SubsEdit.vue).
   font-weight: 400 явно — обычный текст стихи, иначе относительно «bolder» в группах/слогах
   базовый текст зависит от web-шрифта и выглядит по-разному. Сам current syllable подсвечиваем
   red + bold; группы различаем только цветом/italic (без font-weight) — иначе вся preview выглядит
   жирной. */
.ske-preview {
  background: #000; border: 1px solid #c8cadb; border-radius: 12px;
  padding: 0.75rem; min-height: 520px; max-height: 620px; line-height: 1.6; overflow-y: auto;
  column-width: 160px; column-fill: auto; text-align: left; font-weight: 400; color: #FFFFFF;
}
.ske-preview :deep(.ske-fx-cur) { color: #FF0000; font-weight: 700; }
.ske-preview :deep(.ske-fx-group0) { color: #FFFFFF; }
.ske-preview :deep(.ske-fx-group1) { color: #FFFF00; font-style: italic; }
.ske-preview :deep(.ske-fx-group2) { color: #00BFFF; }
.ske-preview :deep(.ske-fx-group3) { color: #00FF00; font-style: italic; }
.ske-preview :deep(.ske-fx-comment) { color: #D2691E; font-size: 0.78em; font-style: italic; }

@media (max-width: 720px) {
  .ske-texts { grid-template-columns: 1fr; }
  .ske-tail-curr, .ske-tail-next { font-size: 2rem; }
  .ske-sliders { justify-content: center; }
  .ske-kb-row { flex-wrap: wrap; justify-content: center; }
  .ske-kb-key { min-width: 46px; height: 44px; }
  .ske-kb-row { margin-left: 0 !important; }
  .ske-kb-key-inactive { display: none; }
  .ske-textarea, .ske-preview { min-height: 320px; }
}
</style>
