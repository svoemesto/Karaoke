<template>
  <!-- РЕАЛЬНАЯ модалка: закрывается ТОЛЬКО явной кнопкой в футере («Закрыть»). НЕТ @click.self
       на фоне, потому что обёртка живая — показывается поверх SongEditorTable и любого
       контента; клик вне se-modal-tray должен идти кнопкам, а не неявному «закрыть». ESC
       тоже не закрывает — админу требуется явное решение по заданию. -->
  <div class="se-overlay" @click.stop>
    <div class="se-modal se-modal-wide" @click.stop>
      <template v-if="a">
        <h3 class="se-modal-title">
          {{ a.songName || 'Песня' }} <span class="se-dim">— {{ a.author }}</span>
        </h3>
        <div class="se-meta">
          <span
            >Исполнитель задания: <strong>{{ a.assigneeName || a.assigneeEmail }}</strong></span
          >
          <span>Голосов: {{ voiceCount }}</span>
          <span
            >Статус:
            <span class="se-badge" :class="`se-badge-${a.status}`">{{
              statusLabel(a.status)
            }}</span></span
          >
          <span>ID песни: {{ a.songId }}</span>
          <span v-if="songIdStatus !== null && songIdStatus >= 5"
            >idStatus песни:
            <span class="se-badge se-badge-approved">{{ idStatusLabel(songIdStatus) }}</span></span
          >
        </div>

        <div class="se-player-toggle">
          <button type="button" class="se-btn" @click="showPlayer = !showPlayer">
            {{ showPlayer ? 'Скрыть плеер' : '▶ Прослушать (черновик)' }}
          </button>
          <button
            type="button"
            class="se-btn se-btn-primary"
            @click="
              $emit('open-editor', {
                assignmentId: a.id,
                songId: a.songId,
                target: targetForEditor,
              })
            "
          >
            ▶ Открыть в редакторе
          </button>
        </div>
        <div v-if="showPlayer" ref="playerWrap" class="se-player-wrap">
          <iframe
            :src="playerSrc"
            :height="playerHeight"
            class="se-player-frame"
            allow="autoplay"
          />
        </div>

        <div v-if="voiceCount > 1" class="se-voice-tabs">
          <button
            v-for="i in voiceCount"
            :key="i"
            type="button"
            class="se-voice-tab"
            :class="{ 'se-voice-tab-active': currentVoiceIdx === i - 1 }"
            @click="currentVoiceIdx = i - 1"
          >
            Голос {{ i }}
          </button>
        </div>

        <div class="se-cols">
          <div class="se-col">
            <!-- Feature 263 Pass 247: слайдер font-size в модалке (по образцу
                 SongKaraokeEditorView.vue:287-289). Админ меняет шрифт прямо здесь —
                 watcher на textFontSize сохраняет в localStorage через saveEditorSettings(). -->
            <div class="se-col-title">Текст пользователя</div>
            <label class="se-font-slider">
              <span class="se-font-slider-label">Шрифт {{ textFontSize }}px</span>
              <input v-model.number="textFontSize" type="range" min="6" max="36" step="1" />
            </label>
            <!-- Feature 263 FR-004: font-size берётся из настроек редактора (textFontSize).
                 Стиль применяется через :style, чтобы не привязываться к классу .se-text -->
            <pre class="se-text" :style="{ fontSize: textFontSize + 'px' }">{{
              currentSourceText || '(пусто)'
            }}</pre>
          </div>
          <!-- Feature 263 FR-002: новый блок «Разметка» — HTML из formatText(parsedMarkers, -1).
               Палитра .ke-fx-* на чёрном фоне (см. стили .se-markup ниже) идентична
               правой колонке онлайн-редактора в karaoke-public (EditorWorkView.vue:1845). -->
          <div class="se-col">
            <div class="se-col-title">Разметка</div>
            <label class="se-font-slider">
              <span class="se-font-slider-label">Шрифт {{ previewFontSize }}px</span>
              <input v-model.number="previewFontSize" type="range" min="6" max="36" step="1" />
            </label>
            <div
              class="se-markup"
              :style="{ fontSize: previewFontSize + 'px' }"
              v-html="parsedMarkupHtml || '(пусто)'"
            />
          </div>
          <div class="se-col">
            <div class="se-col-title">Маркеры: {{ markerCount }}</div>
            <!-- Feature 263 FR-007: счётчики в одну строку через span + разделитель ·.
                 Логика markerStats не меняется, переформатирован ТОЛЬКО markup. -->
            <div class="se-marker-summary">
              <span
                >Слоги: <strong>{{ markerStats.syllables }}</strong></span
              >
              <span class="se-marker-sep">·</span>
              <span
                >Концы строк: <strong>{{ markerStats.endofline }}</strong></span
              >
              <span class="se-marker-sep">·</span>
              <span
                >Новые строки: <strong>{{ markerStats.newline }}</strong></span
              >
              <span class="se-marker-sep">·</span>
              <span
                >END: <strong>{{ markerStats.end ? 'есть' : 'нет' }}</strong></span
              >
            </div>
          </div>
        </div>

        <div v-if="a.reviewComment" class="se-prev-comment">
          Прошлый комментарий: {{ a.reviewComment }}
        </div>
        <div v-if="isRemoteView" class="se-remote-note">
          Запись открыта из серверной БД — «Одобрить»/«Отклонить» прочитают и обновят статус задания
          там же, на сервере (актуальные правки пользователя, если они ещё не подтянуты
          синхронизацией). Разметка применяется к самой песне всегда в локальной БД — только здесь
          есть локальный диск для рендера.
        </div>

        <label class="se-field">
          <span>Комментарий (при отклонении)</span>
          <textarea v-model="comment" rows="2" placeholder="Что нужно исправить…" />
        </label>

        <p v-if="message" class="se-msg" :class="{ 'se-msg-err': isError }">{{ message }}</p>

        <div v-if="canChooseIdStatus" class="se-idstatus-pick">
          <label class="se-idstatus-option">
            <input v-model="selectedIdStatus" type="radio" :value="5" />
            5 — Маркеры проверены
          </label>
          <label class="se-idstatus-option">
            <input v-model="selectedIdStatus" type="radio" :value="6" />
            6 — Готова
          </label>
        </div>

        <div class="se-modal-btns">
          <button class="se-btn" @click="$emit('close')">Закрыть</button>
          <button class="se-btn se-btn-warning" :disabled="busy" @click="doRevoke">Отозвать</button>
          <button class="se-btn se-btn-danger" :disabled="busy" @click="doReject">Отклонить</button>
          <button class="se-btn se-btn-primary" :disabled="busy" @click="doApprove">
            Одобрить и применить
          </button>
        </div>
      </template>
      <div v-else class="se-loading">Загрузка…</div>
    </div>
  </div>
</template>

<script>
// Feature 263: formatText + loadEditorSettings импортируются из локального fallback-файла,
// а не из karaoke-public/src/composables/useKaraokeEditor. Причина: Docker-сборка webvue3
// (deploy/karaoke-webvue3/Dockerfile) копирует ТОЛЬКО ./webvue3/ в /app/ — прямой импорт
// '../../../../karaoke-public/...' выходит за пределы контекста, и Rollup не может
// его разрешить (fail на `npm run build` внутри Docker-контейнера). Локальный fallback:
//  (а) идентичная логика (минимальная копия из karaoke-public, помечена @see);
//  (б) генерирует классы ke-fx-* (НЕ ske-fx-*), как требует Clarifications Q1 (2026-08-30);
//  (в) EDITOR_DEFAULTS с clamp'ом [6, 36] для совместимости с настройками редактора.
// @see ./useReviewModalFormat.js
// @see karaoke-public/src/composables/useKaraokeEditor.js (источник)
import { formatText, loadEditorSettings, saveEditorSettings } from './useReviewModalFormat'

const STATUS_LABELS = {
  assigned: 'Назначено',
  in_progress: 'В работе',
  submitted: 'На проверке',
  approved: 'Одобрено',
  rejected: 'Отклонено',
}

/**
 * Модальное окно для review.
 *
 * @emits close
 * @emits reviewed
 *
 * @see AGENTS.md
 */

export default {
  name: 'ReviewModal',
  emits: ['close', 'reviewed', 'open-editor'],
  data() {
    return {
      comment: '',
      busy: false,
      message: '',
      isError: false,
      showPlayer: false,
      currentVoiceIdx: 0,
      playerHeight: 0,
      // Feature 184: выбор финального статуса песни при апруве. 6 — backward-compatible
      // дефолт (текущее поведение), 5 — «Маркеры проверены» (без рендера DEMO / sync related).
      // Имя «Маркеры проверены» — канон из specs/022-song-status-lifecycle/data-model.md и
      // SongEdit.vue (title кнопки). Это шаг жизненного цикла СРАЗУ ПОСЛЕ проверки маркеров
      // редактором и ДО рендера караоке-видео.
      selectedIdStatus: 6,
      // Feature 263: размеры шрифта для блоков «Текст пользователя» и «Разметка» берутся из
      // loadEditorSettings() в mounted() — соответствуют настройкам онлайн-редактора
      // (EDITOR_DEFAULTS.textFontSize = 16, previewFontSize = 18, диапазон 6..36).
      // Fallback на дефолты нужен для корректного первого рендера до mounted() (Vue SSR-safe).
      // @see karaoke-public/src/composables/useKaraokeEditor.js:29-30
      textFontSize: 16,
      previewFontSize: 18,
    }
  },
  computed: {
    a() {
      return this.$store.getters.getAssignmentCurrent
    },
    // Только для информационного баннера — approve/reject читают И апрувят/отклоняют статус задания в
    // ОДНОЙ И ТОЙ ЖЕ БД (target); в LOCAL всегда применяется только сама разметка песни (см.
    // SongEditorController.approve).
    isRemoteView() {
      return this.$store.getters.getAssignmentsTarget === 'remote'
    },
    // Куда писать редактору: всё по тому же принципу, что и существующие target-aware действия
    // SongEditorController — в БД, где реально лежит задание.
    targetForEditor() {
      return this.$store.getters.getAssignmentsTarget || 'local'
    },
    // Превью неодобрённого черновика: /player/:id понимает assignmentId и подставляет edited_markers
    // ВСЕЙ песни (все голоса задания) вместо того, что уже сохранено в tbl_songs (см. ApiController.getSongPlayerData).
    // target — откуда реально читать задание/черновик (см. getAssignmentsTarget): реальный цикл
    // назначение→работа часто идёт целиком на remote, а local ещё не синкнут.
    playerSrc() {
      if (!this.a) return ''
      const target = this.$store.getters.getAssignmentsTarget
      return `/player/${this.a.songId}?assignmentId=${this.a.id}&target=${target}`
    },
    voiceCount() {
      return this.a ? Math.max(1, (this.a.draftMarkersPerVoice || []).length) : 0
    },
    currentSourceText() {
      return (
        (this.a && this.a.draftSourceTexts && this.a.draftSourceTexts[this.currentVoiceIdx]) || ''
      )
    },
    parsedMarkers() {
      return (
        (this.a &&
          this.a.draftMarkersPerVoice &&
          this.a.draftMarkersPerVoice[this.currentVoiceIdx]) ||
        []
      )
    },
    /**
     * HTML-представление разметки текущего голоса (для блока «Разметка»).
     * Идентично тому, что админ видит в правой колонке онлайн-редактора в karaoke-public —
     * использует ту же `formatText()` (HTML-классы `ke-fx-*`).
     * `curMarkerIndex = -1` означает «никакой слог не подсвечивать как текущий» — в модалке
     * ревью плеер не запущен, текущего времени нет.
     * @see https://github.com/svoemesto/Karaoke/blob/master/karaoke-public/src/views/EditorWorkView.vue#L1845-L1888 (стили `.ke-fx-*` на чёрном фоне)
     * @see https://github.com/svoemesto/Karaoke/blob/master/karaoke-public/src/composables/useKaraokeEditor.js#L447 (formatText)
     */
    parsedMarkupHtml() {
      return formatText(this.parsedMarkers, -1)
    },
    markerCount() {
      return this.parsedMarkers.length
    },
    markerStats() {
      const s = { syllables: 0, endofline: 0, newline: 0, end: false }
      for (const m of this.parsedMarkers) {
        if (m.markertype === 'syllables') s.syllables++
        else if (m.markertype === 'endofline') s.endofline++
        else if (m.markertype === 'newline') s.newline++
        else if (m.markertype === 'setting' && m.label === 'END') s.end = true
      }
      return s
    },
    // Feature 184: текущий id_status ПЕСНИ (не задания — `a.status` это SongAssignmentStatus).
    // Источник — поле `idStatus` в ответе POST /api/songeditor/byId (FR-011). null, если поле
    // отсутствует (старый бэкенд) — fallback на radio с дефолтом 6 (backward-compatible).
    songIdStatus() {
      return this.a && typeof this.a.idStatus === 'number' ? this.a.idStatus : null
    },
    // FR-007: радио «Финальный статус песни» (5 или 6) показываем ВСЕГДА, когда знаем
    // текущий статус песни. Админу нужна возможность выбора при каждом апруве — иначе
    // теряется смысл фичи 184 (выбор 5/6 при отложенном релизе).
    //
    // Скрытие radio для `songIdStatus >= 5` (Pass 51-3, US2 первой итерации) было ошибкой
    // UX: админ открывает задание с уже-готовой песней (например, после одобрения в 6)
    // и НЕ видит контрол выбора → воспринимает фичу как сломанную.
    //
    // Безопасность «случайного downgrade»: при `requestedIdStatus=5, current=6` бэкенд тихо
    // оставляет более высокий статус (downgrade-ignore, см. contracts/approve-endpoint.md,
    // Edge Cases spec.md) — никаких побочных эффектов, кроме информативного лога.
    canChooseIdStatus() {
      return this.songIdStatus !== null
    },
  },
  watch: {
    // При открытии плеера: ловим момент после рендера wrap'а (его v-if), ставим ResizeObserver
    // и инициируем первый расчёт. Без этого на первом кадре iframe получит height="0" или
    // высоту по умолчанию (~150px в Chrome), и нужно дополнительно дожидаться следующего тика.
    showPlayer(v) {
      if (v) {
        this.$nextTick(() => this.observeWrapAndFit())
      } else if (this._resizeObserver) {
        this._resizeObserver.disconnect()
        this._resizeObserver = null
      }
    },
    // Feature 263 Pass 247: watcher на размер шрифта — сохраняет изменение в localStorage,
    // чтобы оно пережило закрытие модалки и подхватывалось в редакторе (и наоборот).
    // Шаблон взят из SongKaraokeEditorView.vue:590-592 (там — тот же приём для слайдера).
    // try/catch — на случай если localStorage недоступен (приватный режим / квота).
    textFontSize(v) {
      try {
        saveEditorSettings({ textFontSize: v })
      } catch (e) {
        /* no-op */
      }
    },
    previewFontSize(v) {
      try {
        saveEditorSettings({ previewFontSize: v })
      } catch (e) {
        /* no-op */
      }
    },
    // Feature 184: сброс выбора статуса при смене задания. Без этого выбор «залипает» между
    // разными заданиями, если модалка переиспользуется (например, в SongsTable компонент может
    // оставаться смонтированным). Сравниваем по id, а не по ссылке — `a` всегда новый объект
    // после `setAssignmentCurrent` (см. store.js:117-121).
    a: {
      handler(newA, oldA) {
        if (newA && oldA && newA.id !== oldA.id) {
          this.selectedIdStatus = 6
        }
      },
      deep: false,
    },
  },
  async mounted() {
    // Feature 263: подхватываем размеры шрифта из localStorage админа (настройки онлайн-редактора),
    // чтобы блоки «Текст пользователя» и «Разметка» выглядели так же, как в онлайн-редакторе.
    // loadEditorSettings() безопасен при недоступном localStorage (приватный режим / квота) — возвращает
    // EDITOR_DEFAULTS. data-поля textFontSize/previewFontSize уже инициализированы дефолтами (16/18) для
    // SSR-safe первого рендера. Без live-watcher'a на storage — смена настроек в редакторе подхватывается
    // при следующем открытии модалки (достаточно).
    try {
      const s = loadEditorSettings()
      if (typeof s.textFontSize === 'number') this.textFontSize = s.textFontSize
      if (typeof s.previewFontSize === 'number') this.previewFontSize = s.previewFontSize
    } catch (e) {
      /* no-op: дефолты уже применены */
    }
    // Если плеер уже открыт на момент mounted (например, v-if стал true до lifecycle),
    // сразу ставим ResizeObserver и пересчитываем высоту.
    window.addEventListener('resize', this.fitPlayerTo16x9)
    if (this.showPlayer) {
      this.$nextTick(() => this.observeWrapAndFit())
    }
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.fitPlayerTo16x9)
    if (this._resizeObserver) {
      this._resizeObserver.disconnect()
      this._resizeObserver = null
    }
  },
  methods: {
    // Устанавливает ResizeObserver на wrap и запускает первый расчёт 16:9.
    // ResizeObserver гарантированно срабатывает при ЛЮБОМ изменении ширины родителя —
    // в том числе при ресайзе самой модалки (а не только окна браузера).
    observeWrapAndFit() {
      const wrap = this.$refs.playerWrap
      if (!wrap) return
      try {
        if (!this._resizeObserver) {
          this._resizeObserver = new ResizeObserver(() => this.fitPlayerTo16x9())
          this._resizeObserver.observe(wrap)
        }
      } catch (e) {
        /* no-op */
      }
      this.fitPlayerTo16x9()
    },
    // Пропорции 16:9 для iframe-плеера — единственный надёжный путь это ВСЕГДА вычислять
    // высоту в пикселях из clientWidth wrap'а и применять её:
    //   1) wrap.style.height — чтобы контейнер не схлопнулся и не «раздулся» по CSS;
    //   2) iframe height АТРИБУТ — проходит через iframe, в отличие от CSS height, который
    //      браузеры часто игнорируют (`<iframe>` специфически обрабатывают CSS height% /
    //      aspect-ratio родителя);
    //   3) iframe style.height — belt-and-suspenders для браузеров, которые игнорируют
    //      атрибут при inline-стилях.
    // Без этих трёх одновременно iframe мог оказаться 150px в Chrome / 0 в Firefox.
    fitPlayerTo16x9() {
      if (!this.showPlayer) return
      const wrap = this.$refs.playerWrap
      if (!wrap) return
      const w = wrap.clientWidth
      if (w <= 0) return
      // ВАЖНО: пропорция 16:9 — это для ЭКРАНА караоке (canvas+текст), а не для всего
      // iframe. Внутри iframe (PlayerView.vue → KaraokePlayer) viewport делится на canvas
      // (flex:1, рисует текст/звёзды) и controls (≈110 px: #kp-controls-volume с волновыми
      // формами и слайдерами громкости + #kp-controls-bottom с прогрессом/меню/иконкой скорости).
      // Если задать iframe height = w × 9/16, canvas внутри получит сплющенную высоту
      // (iframe_H − 110), и соотношение будет далеко от 16:9. Поэтому iframe H =
      // жел. canvas H (16:9) + controls H (≈110 px). Картинка в iframe станет 16:9.
      // Оценка 110 px занижена самой абстракции (waveform-виджеты по 40 px + padding +
      // border), но близка — на любом разумном размере модалки отклонение по высоте canvas
      // от 16:9 не превышает ~10%.
      const CONTROLS_APPROX_H = 110
      const canvasTargetH = Math.round((w * 9) / 16)
      const iframeH = canvasTargetH + CONTROLS_APPROX_H
      wrap.style.height = iframeH + 'px'
      wrap.style.flexShrink = '0'
      this.playerHeight = iframeH
      const iframe = wrap.querySelector('iframe')
      if (iframe) iframe.style.height = iframeH + 'px'
    },
    statusLabel(s) {
      return STATUS_LABELS[s] || s
    },
    // Лейбл статуса для read-only бейджа US2 (feature 184). Объявлен в methods:, а не в
    // computed:, потому что computed-свойства в Vue 2 — это геттеры без параметров; шаблон
    // вызывает idStatusLabel(songIdStatus) с аргументом, и в production-сборке `this.idStatusLabel`
    // вернёт строку (результат геттера при вызове без аргумента), а не функцию → TypeError.
    // Имена «Маркеры проверены» / «Готова» — канон из specs/022-song-status-lifecycle.
    idStatusLabel(s) {
      if (s === 5) return '5 (маркеры проверены)'
      if (s === 6) return '6 (готова)'
      return String(s)
    },
    async doApprove() {
      this.busy = true
      this.message = ''
      try {
        // Feature 184: передаём выбранный статус в виде объекта {id, idStatus} (FR-008). Бэкенд
        // либо применит выбранное значение, либо проигнорирует downgrade (если песня уже выше).
        // Возвращённый `idStatus` — ФАКТИЧЕСКИЙ после применения (FR-012), используем его в
        // сообщении (FR-009), чтобы admin видел, в каком статусе песня реально осталась.
        const res = await this.$store.dispatch('approveAssignment', {
          id: this.a.id,
          idStatus: this.selectedIdStatus,
        })
        if (res && res.ok && res.status === 'already_approved') {
          // Повторный/двойной клик по уже одобренному заданию (specs/094-fix-approve-news-failure,
          // FR-002/FR-006) — явное сообщение вместо тихого закрытия окна без результата.
          this.isError = false
          this.message = 'Задание уже одобрено'
          setTimeout(() => this.$emit('reviewed'), 900)
        } else if (res && res.ok) {
          // Явное сообщение об успехе (FR-001/FR-005) — раньше окно закрывалось молча, и
          // администратор не мог отличить реальный успех от «зависшего» запроса.
          this.isError = false
          this.message = 'Одобрено в статусе ' + (res.idStatus != null ? res.idStatus : '?')
          setTimeout(() => this.$emit('reviewed'), 900)
        } else {
          this.isError = true
          this.message = 'Не удалось одобрить: ' + ((res && res.error) || '')
        }
      } catch (e) {
        this.isError = true
        this.message = 'Ошибка запроса'
      } finally {
        this.busy = false
      }
    },
    async doReject() {
      this.busy = true
      this.message = ''
      try {
        const res = await this.$store.dispatch('rejectAssignment', {
          id: this.a.id,
          comment: this.comment,
        })
        if (res && res.ok) {
          this.$emit('reviewed')
        } else {
          this.isError = true
          this.message = 'Не удалось отклонить'
        }
      } catch (e) {
        this.isError = true
        this.message = 'Ошибка запроса'
      } finally {
        this.busy = false
      }
    },
    async doRevoke() {
      if (
        !confirm(
          'Отозвать назначение у редактора? Задание и его черновик будут удалены — пользователь больше не сможет его редактировать, и эту же песню сразу можно будет назначить другому редактору через селектор «Назначить…».',
        )
      )
        return
      this.busy = true
      this.message = ''
      try {
        const res = await this.$store.dispatch('revokeAssignment', this.a.id)
        if (res && res.ok) {
          this.$emit('reviewed')
        } else {
          this.isError = true
          this.message = 'Не удалось отозвать'
        }
      } catch (e) {
        this.isError = true
        this.message = 'Ошибка запроса'
      } finally {
        this.busy = false
      }
    },
  },
}
</script>

<style scoped>
.se-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.se-modal {
  background: #fff;
  border-radius: 12px;
  padding: 1.5rem;
  width: 420px;
  max-width: 92vw;
  /* Feature 263 Pass 246 UX fix (2026-08-30): при развёрнутом плеере (iframe 16:9 + ~110px
     controls) высота модалки увеличивается и на коротких экранах хедер (`.se-modal-title`)
     и футер (`.se-modal-btns`) уходят за границы viewport. Решение: `max-height: 90vh`
     + `overflow-y: auto` — модалка центрируется overlay-flex пока помещается, иначе
     обрезается до 90vh и появляется вертикальная прокрутка. Хедер/футер остаются в потоке
     документа, достижимы через скролл. */
  max-height: 90vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  font-family: Avenir, Helvetica, Arial, sans-serif;
  font-weight: 400;
  box-sizing: border-box;
}
.se-modal-wide {
  /* Feature 263 FR-008: расширено с 760px до min(96vw, 1100px), чтобы комфортно разместить
     три колонки на десктопе (≥1024px). На мобиле (<96vw) занимает почти всю ширину окна. */
  width: min(96vw, 1100px);
}
.se-player-toggle {
  display: flex;
}
/* Высота .se-player-wrap вычисляется и ставится JS-ом (fitPlayerTo16x9) — НЕЛЬЗЯ полагаться
   на CSS aspect-ratio + iframe % height (браузеры это игнорируют). Учитываем, что внутри
   iframe-плеера (PlayerView.vue → KaraokePlayer) viewport делится:
     • #kp-canvas-wrap (flex:1) — это то, для чего применяется 16:9;
     • #kp-controls-volume (~50px: волновые формы + слайдеры громкости);
     • #kp-controls-bottom (~60px: прогресс, play/пауза, меню).
   Поэтому iframe высотой wrapH = canvasH(16:9) + ~110 px (= CONTROLS_APPROX_H в JS).
   Без этого плеер «сплющивается» (canvas становится шире, чем 16:9). */
.se-player-wrap {
  width: 100%;
  border-radius: 8px;
  overflow: hidden;
  background: #000;
}
.se-player-frame {
  width: 100%;
  border: none;
  display: block;
}
.se-modal-title {
  margin: 0;
  font-size: 1.15rem;
}
.se-dim {
  color: #888;
  font-weight: 400;
}
.se-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem 1.2rem;
  font-size: 0.82rem;
  color: #555;
  font-weight: 400;
}
.se-voice-tabs {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}
.se-voice-tab {
  border: 1px solid #bbb;
  border-radius: 20px;
  padding: 0.3rem 0.9rem;
  background: #fff;
  cursor: pointer;
  font-size: 0.8rem;
  font-weight: 400;
}
.se-voice-tab:hover {
  background: #f5f5f5;
}
.se-voice-tab-active {
  background: #24803a;
  color: #fff;
  border-color: #24803a;
}
.se-cols {
  /* Feature 263 FR-008: адаптивная сетка 1/2/3 колонки.
       mobile-first: 1fr (всё вертикально);
       @media (min-width: 768px): 2 колонки, Маркеры — на всю ширину под Текст+Разметка;
       @media (min-width: 1024px): 3 колонки в одной строке. */
  display: grid;
  gap: 1rem;
  grid-template-columns: 1fr;
}
@media (min-width: 768px) {
  .se-cols {
    grid-template-columns: 1fr 1fr;
  }
  .se-cols .se-col:last-child {
    grid-column: 1 / -1;
  }
}
@media (min-width: 1024px) {
  .se-cols {
    grid-template-columns: 1fr 1fr 1fr;
  }
  .se-cols .se-col:last-child {
    grid-column: auto;
  }
}
.se-col-title {
  font-size: 0.72rem;
  text-transform: uppercase;
  color: #888;
  font-weight: 400;
  margin-bottom: 0.3rem;
}
/* Feature 263 Pass 247: слайдер font-size в модалке (по образцу
   SongKaraokeEditorView.vue:1853-1862 — .ske-font-slider). label обёртка для кликабельности,
   внутри — подпись «Шрифт Npx» и <input type="range">. v-model.number в template синхронизирует
   data-поля textFontSize/previewFontSize (см. watcher'ы в <script> — сохраняют в localStorage). */
.se-font-slider {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.72rem;
  color: #888;
  font-weight: 400;
  margin-bottom: 0.3rem;
  cursor: pointer;
}
.se-font-slider-label {
  white-space: nowrap;
}
.se-font-slider input[type='range'] {
  flex: 1;
  margin: 0;
  cursor: pointer;
  accent-color: #24803a;
}
.se-text {
  background: #f5f5f5;
  border-radius: 8px;
  padding: 0.6rem;
  /* Feature 263: font-size управляется через inline-style на <pre class="se-text"> (FR-004),
     значения берутся из textFontSize (= data.loadEditorSettings().textFontSize). Дефолт 16px. */
  max-height: 220px;
  overflow: auto;
  white-space: pre-wrap;
  margin: 0;
  font-weight: 400;
  /* Feature 263 FR-001: явное выравнивание по левому краю, чтобы текст не «прыгал» в
     зависимости от контекста (light/dark theme, ширина, родительские flex/grid). */
  text-align: left;
}
/* Feature 263 FR-002/FR-006: блок «Разметка» — HTML-представление разметки текущего голоса
   в формате karaoke-public (чёрный фон, моно-строчный с <br>, цветные span'ы групп голоса).
   По решению Clarifications 2026-08-30: используем ту же палитру `.ke-fx-*`, что и в
   karaoke-public (EditorWorkView.vue:1861-1888), с чёрным фоном — пиксель-в-пиксель
   идентично правой колонке онлайн-редактора.
   font-size — через :style (previewFontSize из настроек редактора). */
.se-markup {
  background: #000;
  border-radius: 8px;
  padding: 0.6rem;
  max-height: 220px;
  overflow: auto;
  white-space: pre-wrap;
  font-weight: 400;
  text-align: left;
}
/* Палитра `.ke-fx-*` — копия из karaoke-public/src/views/EditorWorkView.vue:1861-1888.
   Поскольку `.se-markup` рендерится через v-html (HTML генерируется formatText из
   karaoke-public), классы внутри — именно `ke-fx-*`. Через :deep() «пробрасываем» стили
   в дочерние элементы (Vue 3 SFC scoped CSS). */
.se-markup :deep(.ke-fx-cur) {
  color: #ff0000;
  font-weight: bolder;
}
.se-markup :deep(.ke-fx-group0) {
  color: #ffffff;
  font-weight: bolder;
}
.se-markup :deep(.ke-fx-group1) {
  color: #ffff00;
  font-style: italic;
  font-weight: bolder;
}
.se-markup :deep(.ke-fx-group2) {
  color: #00bfff;
  font-weight: bolder;
}
.se-markup :deep(.ke-fx-group3) {
  color: #00ff00;
  font-style: italic;
  font-weight: bolder;
}
.se-markup :deep(.ke-fx-comment) {
  color: #d2691e;
  font-size: 0.78em;
  font-style: italic;
  font-weight: bolder;
}
/* Feature 263 FR-007: блок «Маркеры» одной строкой — горизонтальный flex с переносом.
   Счётчики обёрнуты в <span>, между ними — разделитель `.se-marker-sep`. */
.se-marker-summary {
  background: #f5f5f5;
  border-radius: 8px;
  padding: 0.6rem;
  font-size: 0.85rem;
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 0.3rem 0.8rem;
  align-items: baseline;
  font-weight: 400;
}
.se-marker-sep {
  color: #aaa;
  font-weight: 400;
}
.se-prev-comment {
  font-size: 0.8rem;
  color: #a9500f;
  background: #fff3e8;
  border-radius: 8px;
  padding: 0.4rem 0.6rem;
  font-weight: 400;
}
.se-remote-note {
  font-size: 0.8rem;
  color: #8a6d0a;
  background: #fef8e3;
  border: 1px solid #f2dd9a;
  border-radius: 8px;
  padding: 0.5rem 0.6rem;
  font-weight: 400;
}
.se-field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.85rem;
  font-weight: 400;
}
.se-field textarea {
  padding: 0.45rem 0.6rem;
  border: 1px solid #ccc;
  border-radius: 8px;
  font-size: 0.9rem;
  resize: vertical;
  font-weight: 400;
}
.se-msg {
  font-size: 0.85rem;
  color: #2a7a3a;
  margin: 0;
  font-weight: 400;
}
.se-msg-err {
  color: #c0392b;
}
.se-modal-btns {
  display: flex;
  justify-content: flex-end;
  gap: 0.6rem;
  margin-top: 0.5rem;
}
.se-btn {
  border: 1px solid #bbb;
  border-radius: 8px;
  padding: 0.45rem 1rem;
  background: antiquewhite;
  cursor: pointer;
  font-size: 0.9rem;
  font-weight: 400;
}
.se-btn:hover {
  background: lightpink;
}
.se-btn-primary {
  background: #24803a;
  color: #fff;
  border: none;
}
.se-btn-primary:hover {
  opacity: 0.9;
  background: #24803a;
}
.se-btn-danger {
  background: #c0392b;
  color: #fff;
  border: none;
}
.se-btn-danger:hover {
  opacity: 0.9;
  background: #c0392b;
}
.se-btn-warning {
  background: #8e6d0f;
  color: #fff;
  border: none;
}
.se-btn-warning:hover {
  opacity: 0.9;
  background: #8e6d0f;
}
.se-btn:disabled {
  opacity: 0.5;
  cursor: default;
}
.se-loading {
  padding: 2rem;
  text-align: center;
  color: #888;
  font-weight: 400;
}
.se-badge {
  font-size: 0.7rem;
  font-weight: 700;
  border-radius: 20px;
  padding: 0.15rem 0.6rem;
}
.se-badge-assigned {
  background: #e2e6ea;
  color: #5a6570;
}
.se-badge-in_progress {
  background: #dbeafe;
  color: #1e5fbf;
}
.se-badge-submitted {
  background: #fef3c7;
  color: #92700a;
}
.se-badge-approved {
  background: #d1f5d8;
  color: #24803a;
}
.se-badge-rejected {
  background: #ffe0cc;
  color: #b8500f;
}
/* Feature 184: блок выбора финального статуса песни. Дизайн — в той же палитре, что и
   .se-prev-comment/.se-remote-note (antirquote-серый фон, чтобы не конкурировать визуально
   с основными кнопками). Нативные radio — без bootstrap-vue-next (модалка самодостаточна,
   свой <style scoped>, см. research D-7). */
.se-idstatus-pick {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  background: #f5f5f5;
  border-radius: 8px;
  padding: 0.6rem 0.7rem;
  font-size: 0.85rem;
  font-weight: 400;
}
.se-idstatus-option {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  cursor: pointer;
  font-size: 0.85rem;
  font-weight: 400;
}
.se-idstatus-option input[type='radio'] {
  margin: 0;
  cursor: pointer;
}
/* US2: read-only бейдж для песен в idStatus 5/6 — в той же палитре, что .se-prev-comment. */
.se-idstatus-readonly {
  background: #fff3e8;
  color: #a9500f;
  border-radius: 8px;
  padding: 0.45rem 0.6rem;
  font-size: 0.82rem;
  margin: 0;
  font-weight: 400;
}
</style>
