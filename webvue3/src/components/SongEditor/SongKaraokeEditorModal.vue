<template>
  <transition name="modal-fade">
    <!-- РЕАЛЬНАЯ модалка: закрывается ТОЛЬКО явными кнопками (× в шапке, «Отмена»/«Сохранить» в
         футере). НЕТ @click.self на фоне, потому что editor часто показывается ПОВЕРХУ другой
         модалки (`ReviewModal` из задания редактора): клик «за пределами» editor здесь — это
         клик в области родительской ReviewModal, и её @click.self закрыл бы и её тоже после
         закрытия editor (так как editor лежит на одном уровне DOM, не внутри Review). То же
         поведение у настоящих Bootstrap BModal в `SongEdit.vue` — клик по фону не закрывает. -->
    <div class="skm-overlay" @click.stop>
      <div class="skm-modal" @click.stop>
        <div class="skm-header">
          <span class="skm-header-title">{{ headerTitle }}</span>
          <span class="skm-header-meta">{{ headerMeta }}</span>
          <button class="skm-close-x" title="Закрыть" @click="$emit('close')">✕</button>
        </div>
        <div class="skm-body">
          <div v-if="loading" class="skm-status">Загрузка песни…</div>
          <div v-else-if="loadError" class="skm-status skm-status-error">{{ loadError }}</div>
          <song-karaoke-editor-view
            v-else
            ref="editor"
            :mode="mode"
            :song-id="loadedSongId"
            :assignment-id="assignmentId"
            :song-name="loadedSongName"
            :author="loadedAuthor"
            :album="loadedAlbum"
            :year="loadedYear"
            :track="loadedTrack"
            :tonality="loadedTonality"
            :bpm="loadedBpm"
            :target="target"
            :source-texts="loadedSourceTexts"
            :markers-per-voice="loadedMarkersPerVoice"
            :audio-vocals-url="loadedAudioVocalsUrl"
            :audio-accompaniment-url="loadedAudioAccompanimentUrl"
            :audio-bass-url="loadedAudioBassUrl"
            :audio-drums-url="loadedAudioDrumsUrl"
            :album-image-url="loadedAlbumImageUrl"
            :artist-image-url="loadedArtistImageUrl"
            :export-base-name="loadedExportBaseName"
            :can-edit="true"
            :review-comment="loadedReviewComment"
            :status="loadedStatus"
            @change="onEditorChange"
          />
        </div>
        <div class="skm-footer">
          <span class="skm-save-state" :class="`skm-save-${saveState}`">{{ saveStateLabel }}</span>
          <div class="skm-footer-btns">
            <button class="skm-btn skm-btn-ghost" :disabled="saving" @click="$emit('close')">
              Отмена
            </button>
            <button
              class="skm-btn skm-btn-primary"
              :disabled="saving || !hasAnyMarkers"
              @click="saveNow"
            >
              {{ saving ? 'Сохранение…' : 'Сохранить' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
import { promisedXMLHttpRequest } from '../../lib/utils'
import SongKaraokeEditorView from './SongKaraokeEditorView.vue'

// Модалка админского онлайн-редактора караоке-разметки (webvue3). Один и тот же режим редактора
// работает в двух сценариях:
//   - mode='song'        — id это songId; правки пишутся напрямую в Settings (tbl_settings).
//   - mode='assignment'  — id это assignmentId; правки пишутся в tbl_song_assignment_drafts.
// В обоих режимах canEdit=true всегда (для админа нет блокировки по статусу, как было в karaoke-public
// для конечных редакторов сайта).
//
// Футер содержит кнопки «Отмена» (закрыть без нового сохранения — автосохранение продолжает
// работать в фоне) и «Сохранить» (принудительный flush текущего черновика).
//
// Автосохранение через 3 секунды молчания (как в karaoke-public) — удобство без сюрприза.
export default {
  name: 'SongKaraokeEditorModal',
  components: { SongKaraokeEditorView },
  props: {
    mode: { type: String, required: true, validator: (v) => v === 'song' || v === 'assignment' },
    id: { type: Number, required: true },
    target: { type: String, default: 'local' },
  },
  data() {
    return {
      loading: true,
      loadError: '',
      loadedSongId: 0,
      loadedSongName: '',
      loadedAuthor: '',
      loadedAlbum: '',
      loadedYear: null,
      loadedTrack: null,
      loadedTonality: null,
      loadedBpm: null,
      loadedAssignmentId: null,
      loadedSourceTexts: [],
      loadedMarkersPerVoice: [],
      loadedAudioVocalsUrl: '',
      loadedAudioAccompanimentUrl: '',
      loadedAudioBassUrl: null,
      loadedAudioDrumsUrl: null,
      loadedAlbumImageUrl: null,
      loadedArtistImageUrl: null,
      loadedExportBaseName: '',
      loadedReviewComment: '',
      loadedStatus: 'song',
      saveState: 'idle',
      saveTimer: null,
      saving: false,
    }
  },
  computed: {
    assignmentId() {
      return this.mode === 'assignment' ? this.id : null
    },
    headerTitle() {
      if (!this.loadedSongName) return this.mode === 'assignment' ? 'Задание редактора' : 'Песня'
      return this.loadedSongName
    },
    headerMeta() {
      const parts = []
      if (this.loadedAuthor) parts.push(this.loadedAuthor)
      parts.push(this.mode === 'song' ? 'редактирование песни' : 'редактирование задания')
      return parts.join(' · ')
    },
    hasAnyMarkers() {
      return (this.loadedMarkersPerVoice || []).some((v) => (v || []).length > 0)
    },
    saveStateLabel() {
      return (
        { idle: '', saving: 'Сохранение…', saved: 'Сохранено ✓', error: 'Ошибка сохранения' }[
          this.saveState
        ] || ''
      )
    },
  },
  async mounted() {
    await this.load()
  },
  beforeUnmount() {
    if (this.saveTimer) clearTimeout(this.saveTimer)
  },
  methods: {
    async load() {
      this.loading = true
      this.loadError = ''
      try {
        const _data = new URLSearchParams({
          id: this.id,
          mode: this.mode,
          target: this.target,
        }).toString()
        const respText = await promisedXMLHttpRequest({
          method: 'POST',
          url: '/api/songeditor/edit/byId',
          params: { id: this.id, mode: this.mode, target: this.target },
        })
        const body = respText ? JSON.parse(respText) : null
        if (!body || !body.found) {
          this.loadError = 'Не удалось загрузить данные для редактирования.'
          this.loading = false
          return
        }
        this.loadedSongId = body.songId
        this.loadedSongName = body.songName
        this.loadedAuthor = body.author
        this.loadedAlbum = body.album || ''
        this.loadedYear = body.year || null
        this.loadedTrack = body.track || null
        this.loadedTonality = body.key || null
        this.loadedBpm = body.bpm != null ? body.bpm : null
        this.loadedAssignmentId = body.assignmentId || null
        this.loadedSourceTexts = body.sourceTexts || []
        this.loadedMarkersPerVoice = body.markersPerVoice || []
        this.loadedAudioVocalsUrl = body.audioVocalsUrl || ''
        this.loadedAudioAccompanimentUrl = body.audioAccompanimentUrl || ''
        this.loadedAudioBassUrl = body.audioBassUrl || null
        this.loadedAudioDrumsUrl = body.audioDrumsUrl || null
        this.loadedAlbumImageUrl = body.albumImageUrl || null
        this.loadedArtistImageUrl = body.artistImageUrl || null
        this.loadedExportBaseName = body.exportBaseName || ''
        this.loadedReviewComment = body.reviewComment || ''
        this.loadedStatus = body.status || (this.mode === 'song' ? 'song' : 'assigned')
        this.loading = false
      } catch (e) {
        this.loadError = 'Ошибка загрузки: ' + (e && e.message ? e.message : e)
        this.loading = false
      }
    },
    onEditorChange() {
      // Редактор изменил текст/маркеры — перепланируем автосохранение через 3 секунды (как в
      // karaoke-public). Сохраняем ВСЕ голоса разом.
      if (this.saveTimer) clearTimeout(this.saveTimer)
      this.saveState = 'idle'
      this.saveTimer = setTimeout(() => this.saveNow(), 3000)
    },
    async saveNow() {
      if (this.saving) return
      if (this.saveTimer) {
        clearTimeout(this.saveTimer)
        this.saveTimer = null
      }
      const editor = this.$refs.editor
      if (!editor) return
      this.saving = true
      this.saveState = 'saving'
      try {
        const sourceTexts = editor.voices.map((v) => v.sourceText)
        // markersToSave приводит ts в формат сервера (без uid, c нотой/аккордом = '').
        const markersPerVoice = editor.voices.map((v) => {
          const out = []
          for (const m of v.markers) {
            out.push({
              time: m.time,
              label: m.label || '',
              note: '',
              chord: '',
              stringLad: '',
              locklad: '',
              color: m.color || '',
              position: m.position || 'bottom',
              markertype: m.markertype,
            })
          }
          return out
        })
        const params = {
          id: this.id,
          mode: this.mode,
          target: this.target,
          sourceTexts: JSON.stringify(sourceTexts),
          markersPerVoice: JSON.stringify(markersPerVoice),
        }
        await promisedXMLHttpRequest({ method: 'POST', url: '/api/songeditor/edit/save', params })
        this.saveState = 'saved'
      } catch (e) {
        this.saveState = 'error'
      } finally {
        this.saving = false
      }
    },
  },
}
</script>

<style scoped>
.skm-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: stretch;
  justify-content: center;
  z-index: 1080;
  padding: 1.5rem;
}
.skm-modal {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.35);
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 1100px;
  max-height: 100%;
  overflow: hidden;
}
.skm-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  border-bottom: 1px solid #e5e5e5;
  padding: 0.6rem 1rem;
  background: #fafafa;
}
.skm-header-title {
  font-weight: 700;
  font-size: 1.05rem;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.skm-header-meta {
  color: #666;
  font-size: 0.85rem;
}
.skm-close-x {
  margin-left: auto;
  border: 1px solid #ccc;
  background: #fff;
  border-radius: 8px;
  width: 32px;
  height: 32px;
  font-size: 1rem;
  cursor: pointer;
}
.skm-close-x:hover {
  background: #f0f0f0;
}

.skm-body {
  flex: 1 1 auto;
  overflow-y: auto;
  padding: 0.5rem 1rem 0.75rem 1rem;
  background: #fff;
}
.skm-status {
  padding: 2.5rem;
  text-align: center;
  color: #666;
  font-size: 1rem;
}
.skm-status-error {
  color: #c0392b;
}

.skm-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid #e5e5e5;
  background: #fafafa;
  padding: 0.6rem 1rem;
  gap: 0.5rem;
  flex-shrink: 0;
}
.skm-save-state {
  font-size: 0.85rem;
  color: #666;
}
.skm-save-saving {
  color: #1e5fbf;
}
.skm-save-saved {
  color: #24803a;
}
.skm-save-error {
  color: #c0392b;
}
.skm-footer-btns {
  display: flex;
  gap: 0.5rem;
}

.skm-btn {
  border: 1px solid #bbb;
  border-radius: 8px;
  padding: 0.45rem 1.1rem;
  background: antiquewhite;
  cursor: pointer;
  font-size: 0.92rem;
  transition: background 0.15s;
}
.skm-btn:hover {
  background: lightpink;
}
.skm-btn-ghost {
  background: transparent;
  border: 1px solid #ccc;
  color: #333;
}
.skm-btn-ghost:hover {
  background: #f0f0f0;
}
.skm-btn-primary {
  background: #24803a;
  color: #fff;
  border: none;
}
.skm-btn-primary:hover {
  opacity: 0.9;
  background: #24803a;
}
.skm-btn:disabled {
  opacity: 0.5;
  cursor: default;
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.2s ease;
}
.modal-fade-enter,
.modal-fade-leave-to {
  opacity: 0;
}

@media (max-width: 720px) {
  .skm-overlay {
    padding: 0.25rem;
  }
  .ske-transport {
    gap: 0.5rem;
  }
  .ske-texts {
    grid-template-columns: 1fr;
  }
}
</style>
