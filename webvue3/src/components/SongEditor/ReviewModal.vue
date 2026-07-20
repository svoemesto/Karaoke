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
            <div class="se-col-title">Текст пользователя</div>
            <pre class="se-text">{{ currentSourceText || '(пусто)' }}</pre>
          </div>
          <div class="se-col">
            <div class="se-col-title">Маркеры: {{ markerCount }}</div>
            <div class="se-marker-summary">
              <div>Слоги: {{ markerStats.syllables }}</div>
              <div>Концы строк: {{ markerStats.endofline }}</div>
              <div>Новые строки: {{ markerStats.newline }}</div>
              <div>END: {{ markerStats.end ? 'есть' : 'нет' }}</div>
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
const STATUS_LABELS = {
  assigned: 'Назначено',
  in_progress: 'В работе',
  submitted: 'На проверке',
  approved: 'Одобрено',
  rejected: 'Отклонено',
}

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
    // ВСЕЙ песни (все голоса задания) вместо того, что уже сохранено в tbl_settings (см. ApiController.getSongPlayerData).
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
  },
  async mounted() {
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
    async doApprove() {
      this.busy = true
      this.message = ''
      try {
        const res = await this.$store.dispatch('approveAssignment', this.a.id)
        if (res && res.ok) {
          this.$emit('reviewed')
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
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  font-family: Avenir, Helvetica, Arial, sans-serif;
  font-weight: 400;
  box-sizing: border-box;
}
.se-modal-wide {
  width: 760px;
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
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}
.se-col-title {
  font-size: 0.72rem;
  text-transform: uppercase;
  color: #888;
  font-weight: 400;
  margin-bottom: 0.3rem;
}
.se-text {
  background: #f5f5f5;
  border-radius: 8px;
  padding: 0.6rem;
  font-size: 0.82rem;
  max-height: 220px;
  overflow: auto;
  white-space: pre-wrap;
  margin: 0;
  font-weight: 400;
}
.se-marker-summary {
  background: #f5f5f5;
  border-radius: 8px;
  padding: 0.6rem;
  font-size: 0.85rem;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
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
</style>
