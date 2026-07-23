<template>
  <transition name="modal-fade">
    <div class="acm-modal-backdrop">
      <div class="acm-area">
        <div class="acm-header">
          <div class="acm-header-title">Обложка альбома</div>
        </div>
        <div class="acm-body">
          <!-- Шаг: стартовый экран с текущей картинкой -->
          <div v-if="step === 'view'" class="acm-view">
            <div class="acm-current-picture">
              <div v-if="loadingCurrent" class="acm-message">Загрузка...</div>
              <img
                v-else-if="currentImageUrl"
                class="acm-current-image"
                alt="Текущая обложка альбома"
                :src="currentImageUrl"
              />
              <div v-else class="acm-message">Картинки альбома пока нет</div>
            </div>
            <div v-if="searchError" class="acm-error">{{ searchError }}</div>
            <div class="acm-buttons-group">
              <button
                type="button"
                class="acm-button"
                :disabled="step === 'searching'"
                @click="searchInternet"
              >
                {{ step === 'searching' ? 'Поиск…' : 'Найти в интернете' }}
              </button>
              <button type="button" class="acm-button" @click="openFileExplorer">
                Загрузить с диска
              </button>
            </div>
          </div>

          <!-- Шаг: поиск в процессе -->
          <div v-else-if="step === 'searching'" class="acm-message">Ищем обложку альбома...</div>

          <!-- Шаг: результаты поиска -->
          <div v-else-if="step === 'results'" class="acm-results">
            <div v-if="searchMessage" class="acm-note">{{ searchMessage }}</div>
            <div class="acm-candidates-grid">
              <div
                v-for="(candidate, index) in candidates"
                :key="index"
                class="acm-candidate"
                @click="selectCandidate(candidate)"
              >
                <img
                  class="acm-candidate-image"
                  alt="Вариант обложки"
                  :src="candidate.url"
                  @error="onCandidateError(candidate)"
                />
                <div class="acm-candidate-source">
                  {{ candidate.source === 'YANDEX_MUSIC' ? 'Яндекс.Музыка' : 'Веб-поиск' }}
                </div>
              </div>
            </div>
            <div class="acm-buttons-group">
              <button type="button" class="acm-button" @click="searchInternet">
                Искать снова
              </button>
              <button type="button" class="acm-button" @click="openFileExplorer">
                Загрузить с диска
              </button>
              <button type="button" class="acm-button-secondary" @click="step = 'view'">
                Назад
              </button>
            </div>
          </div>

          <!-- Шаг: кадрирование -->
          <div v-else-if="step === 'cropping'" class="acm-cropping">
            <div v-if="lowResolutionWarning" class="acm-warning">
              Картинка небольшого разрешения — после масштабирования до 400×400 качество может
              быть низким.
            </div>
            <div class="acm-cropper-wrap">
              <Cropper
                ref="cropper"
                class="acm-cropper"
                :src="cropSourceUrl"
                :stencil-props="{ aspectRatio: 1 }"
                @ready="onCropperImageReady"
                @error="onCropperError"
              />
            </div>
            <div v-if="saveError" class="acm-error">{{ saveError }}</div>
            <div class="acm-buttons-group">
              <button
                type="button"
                class="acm-button"
                :disabled="isSaving"
                @click="saveCover"
              >
                {{ isSaving ? 'Сохранение…' : 'Сохранить' }}
              </button>
              <button type="button" class="acm-button-secondary" :disabled="isSaving" @click="backFromCropping">
                Назад
              </button>
            </div>
          </div>
        </div>
        <div class="acm-footer">
          <button type="button" class="acm-button-close" @click="close">Закрыть</button>
        </div>
      </div>

      <FileExplorerModal
        v-if="isFileExplorerVisible"
        :start="startFolder"
        extensions="png;jpg;jpeg;webp;gif;bmp"
        @close="closeFileExplorer"
        @getpath="onDiskFileSelected"
      />
    </div>
  </transition>
</template>

<script>
import { Cropper } from 'vue-advanced-cropper'
import 'vue-advanced-cropper/dist/style.css'
import FileExplorerModal from '../../Common/FileExplorer/FileExplorerModal.vue'

/**
 * Модальное окно для поиска/загрузки и сохранения картинки альбома (LogoAlbum.png).
 *
 * @see AGENTS.md
 */
export default {
  name: 'AlbumCoverModal',
  components: {
    Cropper,
    FileExplorerModal,
  },
  emits: ['close', 'saved'],
  data() {
    return {
      step: 'view', // 'view' | 'searching' | 'results' | 'cropping'
      loadingCurrent: true,
      currentImageUrl: '',
      candidates: [],
      searchMessage: '',
      searchError: '',
      cropSourceUrl: '',
      cropSourceFrom: 'search', // 'search' | 'disk' — куда возвращаться кнопкой "Назад"
      isFileExplorerVisible: false,
      isSaving: false,
      saveError: '',
      lowResolutionWarning: false,
    }
  },
  computed: {
    startFolder() {
      const song = this.$store.getters.getCurrentSong
      return (song && song.rootFolder) || ''
    },
  },
  async mounted() {
    await this.loadCurrentPicture()
  },
  methods: {
    close() {
      this.$emit('close')
    },
    async loadCurrentPicture() {
      this.loadingCurrent = true
      try {
        this.currentImageUrl = await this.$store.dispatch('getAlbumPictureBase64Promise')
      } finally {
        this.loadingCurrent = false
      }
    },
    async searchInternet() {
      this.step = 'searching'
      this.searchError = ''
      try {
        const data = await this.$store.dispatch('searchAlbumCoverPromise')
        const result = JSON.parse(data)
        if (result && result.candidates && result.candidates.length > 0) {
          this.candidates = result.candidates
          this.searchMessage = result.message || ''
          this.step = 'results'
        } else {
          this.searchError = (result && result.message) || 'Ничего не найдено'
          this.step = 'view'
        }
      } catch (e) {
        this.searchError = 'Ошибка поиска обложки'
        this.step = 'view'
      }
    },
    onCandidateError(candidate) {
      this.candidates = this.candidates.filter((c) => c !== candidate)
    },
    selectCandidate(candidate) {
      this.cropSourceUrl = candidate.url
      this.cropSourceFrom = 'search'
      this.lowResolutionWarning = false
      this.saveError = ''
      this.step = 'cropping'
    },
    openFileExplorer() {
      this.isFileExplorerVisible = true
    },
    closeFileExplorer() {
      this.isFileExplorerVisible = false
    },
    async onDiskFileSelected(path) {
      this.isFileExplorerVisible = false
      if (!path) return
      const base64 = await this.$store.getters.loadPictureFromDiskBase64(path)
      if (!base64) {
        this.searchError = 'Не удалось загрузить выбранный файл'
        this.step = 'view'
        return
      }
      this.cropSourceUrl = 'data:image/png;base64,' + base64
      this.cropSourceFrom = 'disk'
      this.lowResolutionWarning = false
      this.saveError = ''
      this.step = 'cropping'
    },
    onCropperImageReady() {
      const cropper = this.$refs.cropper
      if (!cropper) return
      const result = cropper.getResult()
      const w = result && result.image ? result.image.width : 0
      const h = result && result.image ? result.image.height : 0
      this.lowResolutionWarning = w > 0 && h > 0 && (w < 400 || h < 400)
    },
    onCropperError() {
      this.saveError = 'Не удалось загрузить картинку для кадрирования'
    },
    backFromCropping() {
      this.saveError = ''
      this.step = this.cropSourceFrom === 'search' && this.candidates.length > 0 ? 'results' : 'view'
    },
    // Кроппер отдаёт canvas произвольного размера (зависит от выбранной области) — досаживаем
    // до ровно 400x400, чтобы бэкенду не нужно было гадать/пересчитывать.
    resizeCanvasTo400(sourceCanvas) {
      const targetCanvas = document.createElement('canvas')
      targetCanvas.width = 400
      targetCanvas.height = 400
      const ctx = targetCanvas.getContext('2d')
      ctx.drawImage(sourceCanvas, 0, 0, 400, 400)
      return targetCanvas
    },
    async saveCover() {
      if (this.isSaving) return
      const cropper = this.$refs.cropper
      if (!cropper) return
      const result = cropper.getResult()
      if (!result || !result.canvas) {
        this.saveError = 'Не удалось получить кадрированное изображение'
        return
      }
      this.isSaving = true
      this.saveError = ''
      try {
        const finalCanvas = this.resizeCanvasTo400(result.canvas)
        const dataUrl = finalCanvas.toDataURL('image/png')
        const imageBase64 = dataUrl.split(',')[1]
        const url = await this.$store.dispatch('saveAlbumCoverPromise', { imageBase64 })
        if (url) {
          this.$emit('saved', url)
          this.close()
        } else {
          this.saveError = 'Не удалось сохранить картинку — проверьте папку альбома на диске'
        }
      } catch (e) {
        this.saveError = 'Ошибка сохранения картинки'
      } finally {
        this.isSaving = false
      }
    },
  },
}
</script>

<style scoped>
.acm-modal-fade-enter,
.acm-modal-fade-leave-active {
  opacity: 0;
}

.acm-modal-fade-enter-active,
.acm-modal-fade-leave-active {
  transition: opacity 0.5s ease;
}

.acm-modal-backdrop {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: rgba(0, 0, 0, 0.3);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1055;
}

.acm-area {
  background: #ffffff;
  box-shadow: 2px 2px 20px 1px;
  overflow-x: auto;
  display: flex;
  flex-direction: column;
  width: auto;
  height: auto;
  min-width: 500px;
  position: relative;
  max-width: 900px;
  max-height: 720px;
  font-family: Avenir, Helvetica, Arial, sans-serif;
  font-weight: 300;
}

.acm-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.acm-body {
  background-color: white;
  padding: 15px;
  color: black;
  overflow-y: auto;
  min-height: 200px;
}

.acm-message {
  padding: 20px;
  text-align: center;
  color: #555;
}

.acm-error {
  padding: 8px;
  text-align: center;
  color: #8a2f2f;
  background-color: #fdecec;
  border-radius: 6px;
  margin-bottom: 10px;
}

.acm-warning {
  padding: 8px;
  text-align: center;
  color: #8a6100;
  background-color: #fff8e6;
  border-radius: 6px;
  margin-bottom: 10px;
}

.acm-note {
  padding: 4px;
  text-align: center;
  color: #555;
  font-size: small;
  margin-bottom: 10px;
}

.acm-current-picture {
  background-color: black;
  width: 250px;
  height: 250px;
  margin: 0 auto 15px auto;
  display: flex;
  align-items: center;
  justify-content: center;
}

.acm-current-image {
  width: 250px;
  height: 250px;
}

.acm-buttons-group {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 10px;
}

.acm-button {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  padding: 8px 14px;
  cursor: pointer;
}

.acm-button:hover:not(:disabled) {
  background-color: lightyellow;
}

.acm-button:disabled {
  background-color: lightgray;
  cursor: default;
}

.acm-button-secondary {
  border: solid gray thin;
  border-radius: 5px;
  background-color: #f4f4f4;
  padding: 8px 14px;
  cursor: pointer;
}

.acm-button-secondary:hover:not(:disabled) {
  background-color: #e4e4e4;
}

.acm-candidates-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
  max-height: 420px;
  overflow-y: auto;
  padding: 4px;
}

.acm-candidate {
  cursor: pointer;
  border: 2px solid transparent;
  border-radius: 6px;
  padding: 4px;
  text-align: center;
}

.acm-candidate:hover {
  border-color: #4aae9b;
  background-color: #e8f4f2;
}

.acm-candidate-image {
  width: 100%;
  height: 140px;
  object-fit: cover;
  background-color: black;
  border-radius: 4px;
}

.acm-candidate-source {
  font-size: 11px;
  color: #555;
  margin-top: 4px;
}

.acm-cropper-wrap {
  width: 100%;
  height: 420px;
  background-color: #333;
}

.acm-cropper {
  width: 100%;
  height: 100%;
}

.acm-footer {
  background-color: darkslategray;
  padding: 10px;
  display: flex;
  justify-content: flex-end;
}

.acm-button-close {
  border: 1px solid white;
  border-radius: 10px;
  font-size: 16px;
  cursor: pointer;
  font-weight: bold;
  color: #4aae9b;
  background: transparent;
  width: 150px;
  height: auto;
}

.acm-button-close:hover {
  background: darkgreen;
}
</style>
