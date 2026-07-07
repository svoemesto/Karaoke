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

      <!-- Превью в настоящем плеере -->
      <div class="ke-player-toggle">
        <button class="ke-btn ke-btn-ghost" :disabled="playerLoading" @click="togglePlayer">
          {{ playerLoading ? 'Сохраняем…' : (showPlayer ? 'Скрыть плеер' : '▶ Прослушать в плеере') }}
        </button>
      </div>
      <div v-if="showPlayer" class="ke-player-wrap">
        <iframe :src="playerSrc" class="ke-player-frame" allow="autoplay"></iframe>
      </div>

      <!-- Голоса: задание покрывает всю песню — переключение/добавление/удаление голоса. -->
      <div class="ke-voice-tabs">
        <button
            v-for="(v, i) in voices" :key="i" type="button" class="ke-voice-tab"
            :class="{ 'ke-voice-tab-active': currentVoiceIdx === i }"
            @click="setCurrentVoice(i)"
        >Голос {{ i + 1 }}</button>
        <button v-if="canEdit" type="button" class="ke-voice-tab ke-voice-tab-add" @click="addVoice">+ Голос</button>
        <button v-if="canEdit && voices.length > 1" type="button" class="ke-voice-tab ke-voice-tab-remove" @click="removeLastVoice">
          ✕ Удалить голос {{ voices.length }}
        </button>
      </div>

      <!-- Волновая форма -->
      <div class="ke-wave-card">
        <div ref="waveform" class="ke-waveform"></div>
        <div class="ke-time">{{ fmtTime(currentTime) }} / {{ fmtTime(duration) }}</div>
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

      <!-- Транспорт -->
      <div class="ke-transport">
        <button class="ke-tbtn" title="Назад 1с (←)" @click="step(-1)">⏮</button>
        <button class="ke-tbtn ke-tbtn-play" :title="isPlaying ? 'Пауза (Space/X)' : 'Играть (Space/X)'" @click="playPause">
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
          <div class="ke-slider ke-sound-toggle">
            <span>Стем</span>
            <div class="ke-sound-btns">
              <button type="button" class="ke-sound-btn" :class="{ 'ke-sound-btn-active': activeSound === 'voice' }" @click="setActiveSound('voice')">Голос</button>
              <button type="button" class="ke-sound-btn" :class="{ 'ke-sound-btn-active': activeSound === 'music' }" @click="setActiveSound('music')">Музыка</button>
            </div>
          </div>
          <label class="ke-slider">
            <span>Громкость {{ Math.round(volume * 100) }}%</span>
            <input type="range" min="0" max="1" step="0.05" v-model.number="volume" @input="applyVolume" />
          </label>
        </div>
      </div>

      <!-- Нарисованная клавиатура: подсветка нажатой клавиши + подсказки действий (как в
           полновесном редакторе SubsEdit.vue, но в виде клавиатуры, а не ряда кнопок). Хоткеи
           работают независимо от того, показана ли клавиатура — сворачивается только подсказка. -->
      <div v-if="canEdit" class="ke-kb-toolbar">
        <button type="button" class="ke-btn ke-btn-ghost ke-kb-toggle" @click="showKeyboard = !showKeyboard">
          {{ showKeyboard ? 'Скрыть клавиатуру' : '⌨ Показать клавиатуру' }}
        </button>
        <button type="button" class="ke-btn ke-btn-ghost" @click="clearMarkers">Очистить маркеры</button>
      </div>
      <div v-if="canEdit && showKeyboard" class="ke-keyboard">
        <div class="ke-kb-grid">
          <div v-for="(row, ri) in keyboardRows" :key="ri" class="ke-kb-row">
            <button
                v-for="(k, ki) in row.keys" :key="k.code || ('blank-' + ri + '-' + ki)"
                type="button" class="ke-kb-key"
                :style="{ flexBasis: (k.w * 56 + (k.w - 1) * 6) + 'px' }"
                :class="{ 'ke-kb-key-active': k.code && heldKeys[k.code], 'ke-kb-key-inactive': !k.code, 'ke-kb-key-spacer': k.spacer }"
                @mousedown.prevent="pressKey(k.code)" @mouseup.prevent="releaseKey(k.code)"
                @mouseleave="releaseKey(k.code)"
            >
              <span class="ke-kb-key-label">{{ k.label }}</span>
              <span v-if="k.caption" class="ke-kb-key-caption">{{ k.caption }}</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Текст + превью -->
      <div class="ke-texts">
        <div class="ke-text-col">
          <div class="ke-col-header">
            <div class="ke-col-title">Текст песни</div>
            <label class="ke-font-slider">
              <span>Шрифт {{ textFontSize }}px</span>
              <input type="range" min="6" max="36" step="1" v-model.number="textFontSize" />
            </label>
          </div>
          <textarea
            class="ke-textarea"
            :style="{ fontSize: textFontSize + 'px' }"
            v-model="sourceText"
            :disabled="!canEdit"
            placeholder="Вставьте сюда текст песни — он автоматически разобьётся на слоги."
            @input="onTextInput"
          ></textarea>
        </div>
        <div class="ke-text-col">
          <div class="ke-col-header">
            <div class="ke-col-title">Разметка</div>
            <label class="ke-font-slider">
              <span>Шрифт {{ previewFontSize }}px</span>
              <input type="range" min="6" max="36" step="1" v-model.number="previewFontSize" />
            </label>
          </div>
          <div class="ke-preview" :style="{ fontSize: previewFontSize + 'px' }" v-html="formattedTextHtml"></div>
        </div>
      </div>
    </div>

    <!-- Нижняя панель -->
    <footer v-if="!loading && !loadError && canEdit" class="ke-footer ke-sticky-bottom">
      <span class="ke-save-state" :class="`ke-save-${saveState}`">{{ saveStateLabel }}</span>
      <div class="ke-footer-btns">
        <button class="ke-btn ke-btn-ghost" :disabled="saveState === 'saving'" @click="saveNow">Сохранить</button>
        <button class="ke-btn ke-btn-primary" :disabled="submitting || !hasAnyMarkers" @click="submit">
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
  adjacentMarkerTime,
} from '../composables/useKaraokeEditor'

// Хоткеи 1:1 с полновесным редактором (SubsEdit.vue, webvue3). Digit6 (аккорд) и Digit0
// (приглушение) НЕ перенесены — в упрощённом редакторе нет ни режима нот/аккордов, ни механизма
// mute-регионов, добавлять клавишу без единственной осмысленной функции было бы бессмысленно.
const WIRED_KEYS = new Set([
  'Space', 'KeyX', 'KeyQ', 'KeyE', 'KeyA', 'KeyD', 'KeyZ', 'KeyC',
  'BracketLeft', 'BracketRight', 'Comma', 'Period',
  'KeyS', 'KeyW', 'Digit1', 'Digit2', 'Digit3', 'Digit4', 'Digit5',
  'KeyT', 'KeyY', 'KeyU', 'KeyI', 'KeyP', 'KeyO',
])
// Клавиши, которые работают даже когда canEdit=false (просмотр уже отправленного/одобренного
// задания) — воспроизведение и навигация всегда доступны, разметку менять нельзя.
const ALWAYS_ALLOWED_KEYS = new Set([
  'Space', 'KeyX', 'KeyQ', 'KeyE', 'KeyA', 'KeyD', 'KeyZ', 'KeyC', 'BracketLeft', 'BracketRight', 'Comma', 'Period',
])

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
      // Задание покрывает ВСЮ песню — voices[i] = { sourceText, markers, syllables } одного голоса.
      // this.sourceText/markers/syllables (ниже, computed) — прозрачный прокси на voices[currentVoiceIdx],
      // поэтому весь остальной код (mark/removeMarker/onTextInput/redrawRegions/...) не меняется.
      voices: [],
      currentVoiceIdx: 0,
      // Аудио / waveform — ОДИН загруженный трек за раз (как в SubsEdit.vue: переключатель
      // Голос/Музыка подменяет файл в WaveSurfer, а не смешивает два одновременно играющих трека).
      ws: null,
      wsRegions: null,
      voiceUrl: '',
      minusUrl: '',
      currentTime: 0,
      duration: 0,
      isPlaying: false,
      playbackRate: 0.75,
      zoom: 100,
      volume: 1, // единый уровень громкости — общий и для голоса, и для музыки
      activeSound: 'voice', // 'voice' | 'music' — какой стем сейчас загружен в вейвформ
      // Хоткеи (см. SubsEdit.vue): heldKeys — состояние "зажатости" для подсветки на клавиатуре
      // и для hold-repeat действий; scrubTimers — интервалы перемотки/навигации, чтобы чистить их
      // по keyup/mouseup и при размонтировании.
      heldKeys: {},
      scrubTimers: {},
      editSpeed: 0.75, // скорость медленной перемотки (A/D) — фиксированная, как дефолт в admin
      showKeyboard: false, // клавиатура-подсказка занимает много места — по умолчанию свёрнута
      textFontSize: 16, // размер шрифта текстового поля (регулируется слайдером над полем)
      previewFontSize: 18, // размер шрифта панели разметки (совпадает с исходным admin-размером)
      // Сохранение
      saveState: 'idle', // idle | saving | saved | error
      saveTimer: null,
      submitting: false,
      redrawScheduled: false,
      // Превью в настоящем плеере (см. playerToken из fetchTask — привязан к этому заданию,
      // playerdata на бэкенде подставляет вместо опубликованных именно наши edited_markers).
      showPlayer: false,
      playerLoading: false,
      playerToken: '',
    }
  },
  computed: {
    statusLabel() { return STATUS_LABELS[this.status] || this.status },
    playerSrc() { return this.task ? `/player/${this.task.songId}` : '' },
    saveStateLabel() {
      return { idle: '', saving: 'Сохранение…', saved: 'Сохранено ✓', error: 'Ошибка сохранения' }[this.saveState] || ''
    },
    currentVoiceData() { return this.voices[this.currentVoiceIdx] || { sourceText: '', markers: [], syllables: [] } },
    // Прокси на текущий голос (voices[currentVoiceIdx]) — весь остальной код читает/пишет
    // this.sourceText/markers/syllables как раньше, не подозревая о многоголосье.
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
    // Раскладка мини-клавиатуры повторяет ГЕОМЕТРИЮ настоящей клавиатуры: каждый следующий буквенный
    // ряд сдвинут ровно на ПОЛОВИНУ ширины клавиши относительно предыдущего — поэтому A оказывается
    // ровно между Q и W, а Z — ровно между A и S (классическая «лесенка» QWERTY). Сдвиг создаётся не
    // margin-left, а самими widening-клавишами слева (Tab=0.5, Caps=1.0, Shift=1.5 условных единиц
    // ширины — нарастают на 0.5 каждый ряд), которые тоже рисуются (M(), неактивные, с символом).
    // Включает положение [ ] (правее P) и , . (после M) — они физически там и находятся. Рисуются
    // ВСЕ реальные клавиши ряда (не только задействованные) — иначе незанятые места выглядят как
    // дыры; недействующие показаны приглушённо (D() — тот же слот, но code=null, без действия).
    // Полный перечень забинженных в SubsEdit.vue клавиш (проверено по коду, listenerKeyDown/Up):
    // X A D Q E Z C [ ] , . W S 1 2 3 4 5 6 0 T Y U I O P — 25 штук. Здесь нет только Digit6
    // (аккорд) и Digit0 (заглушение) — в упрощённом редакторе нет ни режима нот/аккордов, ни
    // механизма mute-регионов, добавлять клавишу без единственной осмысленной функции бессмысленно.
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
    window.addEventListener('keyup', this.onKeyUp)
  },
  beforeUnmount() {
    window.removeEventListener('keydown', this.onKeyDown)
    window.removeEventListener('keyup', this.onKeyUp)
    for (const code of Object.keys(this.scrubTimers)) clearInterval(this.scrubTimers[code])
    if (this.saveTimer) clearTimeout(this.saveTimer)
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
      this.playerToken = body.playerToken || ''

      // Задание покрывает ВСЮ песню — sourceTexts/markersPerVoice приходят массивами (индекс =
      // номер голоса). Длины МОГУТ разойтись (см. Settings.kt: setSourceText/setSourceMarkers —
      // два отдельных вызова, не атомарны) — берём максимум, недостающее считаем пустым.
      const rawTexts = (body.sourceTexts && body.sourceTexts.length) ? body.sourceTexts : ['']
      const rawMarkersPerVoice = (body.markersPerVoice && body.markersPerVoice.length) ? body.markersPerVoice : [[]]
      const voiceCount = Math.max(rawTexts.length, rawMarkersPerVoice.length)
      this.voices = []
      for (let i = 0; i < voiceCount; i++) {
        const text = rawTexts[i] || ''
        const markers = markersFromServer(rawMarkersPerVoice[i] || [])
        const syllables = splitSyllables(text)
        relabelSyllables(markers, syllables)
        this.voices.push({ sourceText: text, markers, syllables })
      }
      this.currentVoiceIdx = 0

      this.voiceUrl = body.audioVocalsUrl
      this.minusUrl = body.audioAccompanimentUrl
      this.loading = false
      await this.$nextTick()
      await this.initWaveSurfer()
    },
    async initWaveSurfer() {
      if (!this.$refs.waveform) return
      const { default: WaveSurfer } = await import('wavesurfer.js')
      const { default: RegionsPlugin } = await import('wavesurfer.js/dist/plugins/regions.esm.js')
      const { default: Minimap } = await import('wavesurfer.js/dist/plugins/minimap.esm.js')
      const styles = getComputedStyle(document.documentElement)
      const accent = (styles.getPropertyValue('--km-accent') || '#3b82f6').trim()

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
        // Маленькая превьюшка всей дорожки под основной вейвформой (как в SubsEdit.vue).
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

      this.ws.load(this.activeSound === 'voice' ? this.voiceUrl : this.minusUrl)
    },
    // ВАЖНО: WaveSurfer рендерит регионы внутри собственного Shadow DOM (renderer.js:
    // div.attachShadow({mode:'open'})) — обычные CSS-классы компонента (даже через :deep()) туда
    // физически не проникают. Поэтому, как и в SubsEdit.vue (getRegionContentFromMarker), стиль
    // задаём ТОЛЬКО инлайново через el.style — единственное, что реально применяется.
    regionContentEl(marker) {
      const el = document.createElement('div')
      el.style.fontSize = '9px'
      el.style.fontWeight = '700'
      el.style.padding = '1px 3px'
      el.style.color = '#222'
      if (marker.markertype === 'syllables') {
        // Тот же стиль, что в SubsEdit.vue: подпись слога на своей подложке (background-color:
        // beige), а не голым текстом поверх региона.
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
    // Центрирует вьюпорт вейвформы на времени t. В SubsEdit.vue это происходит «бесплатно» через
    // внутренний scrollIntoView WaveSurfer, но он центрирует только когда позиция УЖЕ играет
    // (isPlaying) — на паузе (например, сразу после удаления маркера) сам WaveSurfer не центрирует,
    // поэтому считаем видимый диапазон по текущему зуму и центрируем вручную через setScrollTime().
    centerOnTime(t) {
      if (!this.ws || !this.$refs.waveform || !this.zoom) return
      const visibleSeconds = this.$refs.waveform.clientWidth / this.zoom
      this.ws.setScrollTime(Math.max(0, t - visibleSeconds / 2))
    },
    // --- Транспорт ---
    playPause() { if (this.ws) this.ws.playPause() },
    step(sec) { if (this.ws) this.ws.setTime(Math.max(0, Math.min(this.duration, this.ws.getCurrentTime() + sec))) },
    applyRate() { if (this.ws) this.ws.setPlaybackRate(this.playbackRate) },
    applyZoom() { if (this.ws) { try { this.ws.zoom(this.zoom) } catch (e) { /* noop */ } } },
    applyVolume() { if (this.ws) this.ws.setVolume(this.volume) },
    // Переключатель Голос/Музыка — подменяет ЗАГРУЖЕННЫЙ в вейвформ трек (как loadSong() в
    // SubsEdit.vue), сохраняя текущую позицию/состояние воспроизведения; регионы (маркеры)
    // перерисуются сами по событию 'decode' после загрузки нового файла.
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
    // --- Голоса ---
    // Переключение голоса: регионы чистим БЕЗУСЛОВНО, ДО переключения индекса — в отличие от
    // SubsEdit.vue (admin), где clearRegions() был обёрнут в "если у НОВОГО голоса есть маркеры",
    // из-за чего при переключении на голос без разметки старые регионы оставались висеть на
    // вейвформе (этот баг явно просили не повторять). Аудио НЕ перезагружаем — голоса делят одну
    // и ту же дорожку, различается только наложенная разметка.
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
      this.scheduleAutosave()
    },
    // Разрешено удалять только ПОСЛЕДНИЙ голос — иначе пришлось бы переиндексировать все
    // последующие голоса (а Settings.kt такого не умеет в принципе, только truncate с конца).
    removeLastVoice() {
      if (!this.canEdit || this.voices.length <= 1) return
      if (!confirm(`Удалить голос ${this.voices.length}? Весь его текст и маркеры будут потеряны.`)) return
      this.voices.pop()
      if (this.currentVoiceIdx >= this.voices.length) this.currentVoiceIdx = this.voices.length - 1
      if (this.wsRegions) this.wsRegions.clearRegions()
      this.redrawRegions()
      this.scheduleAutosave()
    },
    // --- Разметка ---
    clearMarkers() {
      if (!this.canEdit) return
      if (!confirm('Удалить все маркеры разметки? Отменить это действие будет нельзя.')) return
      this.markers = []
      this.redrawRegions()
      this.scheduleAutosave()
    },
    // notDelete=true — для комбо-хоткеев (Digit3/5): не даёт последующему addMarker в той же точке
    // времени стереть маркер, добавленный предыдущим вызовом (см. комментарий в useKaraokeEditor.js).
    // label — только для type='setting' (группа голоса 'GROUP|N' или 'COMMENT|текст').
    mark(type, notDelete = false, label = '') {
      if (!this.canEdit || !this.ws) return
      addMarker(this.markers, this.syllables, type, this.ws.getCurrentTime(), notDelete, label)
      this.redrawRegions()
      this.scheduleAutosave()
    },
    // Клавиша O в admin открывает форму «тип+значение»; здесь — просто текстовый комментарий
    // (самый частый практический случай), без общего конструктора произвольных setting-маркеров.
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
      this.scheduleAutosave()
      // Как в SubsEdit.vue: после удаления позиция переходит на маркер, «съехавший» на освободившийся
      // индекс, а если удалён был последний — на предыдущий (а не остаётся висеть в пустом месте).
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
      // Сохраняем ВСЕ голоса разом (не только текущий) — задание покрывает всю песню.
      const sourceTexts = JSON.stringify(this.voices.map(v => v.sourceText))
      const markersPerVoice = JSON.stringify(this.voices.map(v => markersToSave(v.markers)))
      const { status, body } = await saveTask(this.$route.params.id, sourceTexts, markersPerVoice)
      if (status === 200) {
        this.saveState = 'saved'
        if (body && body.status) this.status = body.status
      } else {
        this.saveState = 'error'
      }
    },
    // --- Превью в плеере ---
    async togglePlayer() {
      if (this.showPlayer) { this.showPlayer = false; return }
      // Форсируем сохранение — иначе превью показало бы устаревший (домоментный) черновик:
      // playerdata на бэкенде читает edited_markers из БД, а не то, что ещё только в памяти.
      this.playerLoading = true
      try {
        await this.saveNow()
        sessionStorage.setItem(`kp_token_${this.task.songId}`, this.playerToken)
        this.showPlayer = true
      } finally {
        this.playerLoading = false
      }
    },
    async submit() {
      if (!this.canEdit) return
      this.submitting = true
      // Гарантируем END-маркер на длительности трека для КАЖДОГО голоса и сохраняем перед отправкой.
      for (const v of this.voices) ensureEndMarker(v.markers, this.duration)
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
    // --- Клавиатура (хоткеи 1:1 с SubsEdit.vue — см. WIRED_KEYS выше) ---
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
    // Общая точка входа и для клавиатуры, и для клика по нарисованной клавише — оба пути ведут
    // к одному и тому же состоянию heldKeys (подсветка) и одному и тому же действию.
    pressKey(code) {
      if (!code || this.heldKeys[code]) return // null — незадействованная клавиша-заглушка на клавиатуре
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
    // A/D — медленная перемотка назад/вперёд, пока клавиша зажата (WaveSurfer не умеет играть
    // назад, поэтому назад — частые мелкие skip(); вперёд — реальное замедленное воспроизведение).
    // Z/C — быстрая перемотка (просто более крупный шаг skip(), без изменения playbackRate/паузы).
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

/* Голоса */
.ke-voice-tabs { display: flex; gap: 0.5rem; flex-wrap: wrap; justify-content: center; }
.ke-voice-tab {
  border: 1px solid var(--km-border); border-radius: 20px; padding: 0.35rem 1rem; background: var(--km-card);
  color: var(--km-text); cursor: pointer; font-size: 0.82rem; font-weight: 600;
}
.ke-voice-tab:hover { background: var(--km-hover); }
.ke-voice-tab-active { background: var(--km-accent); color: #fff; border-color: var(--km-accent); }
.ke-voice-tab-add { border-color: #24803a; color: #24803a; background: transparent; }
.ke-voice-tab-add:hover { background: rgba(36,128,58,0.1); }
.ke-voice-tab-remove { border-color: #c0392b; color: #c0392b; background: transparent; }
.ke-voice-tab-remove:hover { background: rgba(192,57,43,0.1); }

/* Превью в плеере */
.ke-player-toggle { display: flex; justify-content: center; }
.ke-player-wrap { width: 100%; height: 440px; border-radius: 16px; overflow: hidden; background: #000; }
.ke-player-frame { width: 100%; height: 100%; border: none; display: block; }

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
.ke-sound-toggle { min-width: unset; }
.ke-sound-btns { display: flex; gap: 0.3rem; }
.ke-sound-btn {
  border: 1px solid var(--km-border); background: var(--km-card); color: var(--km-text);
  border-radius: 8px; padding: 0.25rem 0.6rem; font-size: 0.78rem; cursor: pointer;
}
.ke-sound-btn:hover { background: var(--km-hover); }
.ke-sound-btn-active { background: var(--km-accent); color: #fff; border-color: var(--km-accent); }

/* Нарисованная клавиатура */
.ke-kb-toolbar { display: flex; justify-content: center; gap: 0.75rem; flex-wrap: wrap; }
/* .ke-keyboard центрирует ВЕСЬ блок клавиатуры (.ke-kb-grid) как единое целое по ширине страницы.
   .ke-kb-grid — align-items: flex-start ОБЯЗАТЕЛЬНО (не center): при center каждый ряд (у них разная
   суммарная ширина из-за spacer-клавиш) центрировался бы САМ ПО СЕБЕ, разваливая раскладку сдвигов
   между рядами (Q между 1/2, A между Q/W, Z между A/S) — все ряды должны быть прижаты к одному
   общему левому краю ОТНОСИТЕЛЬНО ДРУГ ДРУГА, а центрируется только весь блок целиком. */
.ke-keyboard { display: flex; justify-content: center; }
.ke-kb-grid { display: flex; flex-direction: column; gap: 0.4rem; align-items: flex-start; }
.ke-kb-row { display: flex; gap: 0.4rem; }
.ke-kb-key {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 0.15rem;
  flex: 0 0 auto; height: 52px; padding: 0.2rem 0.4rem; border-radius: 8px;
  border: 1px solid var(--km-border); background: var(--km-card); color: var(--km-text);
  cursor: pointer; transition: background 0.1s, transform 0.05s; user-select: none;
}
.ke-kb-key:hover { background: var(--km-hover); }
.ke-kb-key-label { font-size: 1rem; font-weight: 700; line-height: 1; }
.ke-kb-key-caption { font-size: 0.6rem; color: var(--km-text2); line-height: 1; white-space: nowrap; }
.ke-kb-key-active {
  background: var(--km-accent); border-color: var(--km-accent); transform: translateY(1px);
}
.ke-kb-key-active .ke-kb-key-label, .ke-kb-key-active .ke-kb-key-caption { color: #fff; }
/* Реальная, но незадействованная в этом редакторе клавиша — видна (клавиатура выглядит цельной,
   без «дыр»), но приглушена и некликабельна. */
.ke-kb-key-inactive { opacity: 0.32; cursor: default; }
.ke-kb-key-inactive:hover { background: var(--km-card); }
/* Узкие клавиши-разделители (Tab/Caps/Shift, ширина < 1 клавиши) — меньше паддинг и шрифт, иначе
   символ не влезает. */
.ke-kb-key-spacer { padding: 0.2rem 0.05rem; }
.ke-kb-key-spacer .ke-kb-key-label { font-size: 0.8rem; }

/* Текст */
.ke-texts { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
.ke-text-col { display: flex; flex-direction: column; gap: 0.4rem; }
.ke-col-header { display: flex; align-items: center; justify-content: space-between; gap: 0.75rem; flex-wrap: wrap; }
.ke-col-title { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--km-text2); font-weight: 700; }
.ke-font-slider { display: flex; align-items: center; gap: 0.4rem; font-size: 0.72rem; color: var(--km-text2); }
.ke-font-slider input { width: 110px; }
.ke-textarea {
  background: var(--km-input); color: var(--km-text); border: 1px solid var(--km-border);
  border-radius: 12px; padding: 0.75rem; font-size: 0.95rem; min-height: 520px; resize: vertical;
  line-height: 1.5; width: 100%;
}
.ke-textarea:focus { outline: none; border-color: var(--km-accent); }
/* Тот же стиль, что у панели разметки в полновесном редакторе (SubsEdit.vue, .se-grid-item-text):
   чёрный фон, многоколоночная вёрстка, белый жирный текст 18px, текущий слог — красный. */
.ke-preview {
  background: #000; border: 1px solid var(--km-border); border-radius: 12px;
  padding: 0.75rem; min-height: 520px; max-height: 620px; line-height: 1.6; overflow-y: auto;
  column-width: 160px; column-fill: auto; text-align: left;
}
/* font-size намеренно не задан — наследуется от .ke-preview (регулируется слайдером над панелью). */
.ke-preview :deep(.ke-fx-cur) { color: #FF0000; font-weight: bolder; }
.ke-preview :deep(.ke-fx-group0) { color: #FFFFFF; font-weight: bolder; }
.ke-preview :deep(.ke-fx-group1) { color: #FFFF00; font-style: italic; font-weight: bolder; }
.ke-preview :deep(.ke-fx-group2) { color: #00BFFF; font-weight: bolder; }
.ke-preview :deep(.ke-fx-group3) { color: #00FF00; font-style: italic; font-weight: bolder; }
.ke-preview :deep(.ke-fx-comment) { color: #D2691E; font-size: 0.78em; font-style: italic; font-weight: bolder; }

/* Стиль содержимого регионов waveform задаётся инлайново в JS (regionContentEl) — CSS сюда не
   доходит, регионы WaveSurfer рендерятся внутри его собственного Shadow DOM. */

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
  .ke-kb-row { flex-wrap: wrap; justify-content: center; }
  .ke-kb-key { min-width: 46px; height: 44px; }
  .ke-kb-row { margin-left: 0 !important; }
  .ke-kb-key-inactive { display: none; }
  .ke-textarea, .ke-preview { min-height: 320px; }
}
</style>
