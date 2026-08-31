<template>
  <div class="home">
    <custom-confirm
      v-if="isCustomConfirmVisible"
      :params="customConfirmParams"
      @close="closeCustomConfirm"
    />
    <FileExplorerModal
      v-if="isFileExplorerVisible"
      :path="pathToFolder"
      start="/sm-karaoke/work"
      directory
      @close="closeFileExplorer"
      @getpath="getPath"
    />
    <div class="home-wrapper">
      <div class="home-controls">
        <datalist id="list_authors">
          <option v-for="author in songAuthors" :key="author" :value="author" />
        </datalist>
        <datalist id="list_dicts">
          <option v-for="dict in dicts" :key="dict" :value="dict" />
        </datalist>
        <div class="field-and-buttons-wrapper">
          <input
            v-model="pathToFolder"
            class="input-folder"
            type="text"
            placeholder="Путь к папке"
            @dblclick="isFileExplorerVisible = true"
          />
          <button class="button-action" :disabled="!pathToFolder" @click="addFilesFromFolder">
            Добавить файлы из папки
          </button>
          <button
            class="button-action"
            :disabled="!pathToFolder"
            @click="createDzenPicturesForFolder"
          >
            Создать картинки плейлистов Dzen для папки
          </button>
        </div>
        <!-- <button class="button-action" @click="copyToStore">Обновить хранилище</button>
      <button class="button-action" @click="actualizeVKLinkPictureWeb">Актуализация VKLinkPictureWeb</button> -->
        <button class="button-action" @click="smartCopyPeriodByDay">
          Подготовить файлы для публикации
        </button>
        <!-- <button class="button-action" @click="checkLastAlbumYm">Поиск новых альбомов</button>
      <button class="button-action" @click="updateBpmAndKey">Обновить пустые BPM и KEY из фалов CSV</button>
      <button class="button-action" @click="updateBpmAndKeyLV">Обновить пустые BPM и KEY из фалов LV</button> -->
        <div class="field-and-buttons-wrapper">
          <input
            v-model="author"
            list="list_authors"
            class="input-author"
            type="text"
            placeholder="Автор"
          />
          <!-- <button class="button-action" @click="markDublicates" :disabled="!author">Найти и обработать дубликаты песен автора</button> -->
          <button class="button-action" :disabled="!author" @click="findParentForAuthor">
            Поиск родителя
          </button>
          <button class="button-action" :disabled="!author" @click="findAudioParentForAuthor">
            Найти аудио-родителя
          </button>
          <button class="button-action" :disabled="!author" @click="autoAssignOriginalAll">
            Автопривязать оригинал по аудио (статус 1 → 2)
          </button>
          <button class="button-action" @click="recalcPlayerReadiness">
            Пересчитать готовность плеера{{ author ? '' : ' (все авторы)' }}
          </button>
          <button class="button-action" @click="deleteSearchResultsForReadySongs">
            Удалить результаты поиска готовых песен
          </button>
          <button class="button-action" @click="backfillPublishFlags">
            Backfill флагов публикации (LOCAL)
          </button>
        </div>
        <div class="field-and-buttons-wrapper">
          <button class="button-action" @click="backfillAlbumsFromSongs">
            Заполнить альбомы из песен
          </button>
          <button class="button-action" @click="normalizeAlbumSortOrder">
            Нормализовать сквозную сортировку альбомов
          </button>
        </div>
        <!-- <button class="button-action" @click="delDublicates">Удалить дубликаты</button>
      <button class="button-action" @click="clearPreDublicates">Очистить информацию о пре-дубликатах</button> -->
        <div class="field-and-buttons-wrapper">
          <div class="fields-line-wrapper">
            <input
              v-model="dictType"
              list="list_dicts"
              class="input-dict-type"
              type="text"
              placeholder="Словарь"
            />
            <input v-model="dictValue" class="input-dict-value" type="text" placeholder="Слово" />
          </div>
          <div class="fields-line-wrapper">
            <button
              class="button-action button-action-inline"
              :disabled="!dictType || !dictValue"
              @click="dictActionAdd"
            >
              Добавить слово в словарь
            </button>
            <button
              class="button-action button-action-inline"
              :disabled="!dictType || !dictValue"
              @click="dictActionRemove"
            >
              Удалить слово из словаря
            </button>
          </div>
        </div>
        <div class="fields-line-wrapper">
          <button
            class="button-action button-action-inline"
            :disabled="authYmInProgress"
            @click="autorizeYMstart"
          >
            Auth YM 1
          </button>
          <button
            class="button-action button-action-inline"
            :disabled="authYmInProgress"
            @click="autorizeYMstart2"
          >
            Auth YM 2
          </button>
          <button
            class="button-action button-action-inline"
            :disabled="!authYmInProgress"
            @click="autorizeYMstop"
          >
            Auth YM: Stop
          </button>
        </div>
        <button
          class="button-action"
          title="Custom Function: поиск родителей и аудио-родителей для песен без родителя (root_id=0, статус &lt; 3)"
          @click="customFunction"
        >
          Поиск родителей и аудио-родителей (Custom Function)
        </button>
        <!-- specs/277-song-name-censored: фоновый реckan tbl_songs.song_name_censored по словарю «Censored».
             Операция тяжёлая (≈18k строк), перезаписывает ВСЕ цензурированные названия — включая
             ручные правки в SongEdit. Идёт в фоне, итог приходит SSE-уведомлением. -->
        <button
          class="button-action"
          title="Пересканировать цензурированные названия ВСЕХ песен по актуальному словарю «Censored»"
          @click="rescanAllCensoredNames"
        >
          Пересканировать цензурированные названия песен
        </button>
        <button
          class="button-action"
          title="Экспорт манифеста (текст + тайминг слогов + путь к вокальному стему) по всем песням с готовой разметкой — для дообучения forced-alignment модели (alignment-ml/)"
          @click="exportAlignmentDataset"
        >
          Экспорт датасета для forced-alignment
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import CustomConfirm from '../components/Common/CustomConfirm.vue'
import FileExplorerModal from '../components/Common/FileExplorer/FileExplorerModal.vue'
// import { useFileDialog } from '@vueuse/core'
/**
 * Главная страница админ-SPA: «добавить файлы из папки».
 *
 * Функционал:
 * - **Поле пути**: `pathToFolder` (ввод + `FileExplorerModal` для выбора).
 * - **Добавление файлов**: «Добавить файлы из папки» — POST
 *   `/api/song/addFilesFromFolder` с путём.
 * - **Создание Dzen-картинок**: «Создать картинки плейлистов Dzen для папки» —
 *   рендер PNG-картинок альбома для публикации в Dzen (см. `Pictures.kt`).
 * - **Подсказки**: `<datalist>` для автодополнения имён авторов/словарей
 *   (берутся из Song/Dictionaries через `songAuthors` / `dicts` computed).
 *
 * Использует `CustomConfirm` и `FileExplorerModal`.
 *
 * @see AGENTS.md
 */
export default {
  name: 'HomeView',
  components: {
    CustomConfirm,
    FileExplorerModal,
  },
  data() {
    return {
      isCustomConfirmVisible: false,
      isFileExplorerVisible: false,
      customConfirmParams: undefined,
      pathToFolder: '',
      author: '',
      dictType: '',
      dictValue: '',
      songAuthors: [],
      dicts: [],
      authYmInProgress: false,
    }
  },
  async mounted() {
    let songAuthors = await this.$store.getters.songAuthors
    let dicts = await this.$store.getters.dicst
    this.songAuthors = songAuthors
    this.dicts = dicts
  },
  methods: {
    getPath(path) {
      this.pathToFolder = path
    },
    closeFileExplorer() {
      this.isFileExplorerVisible = false
    },
    closeCustomConfirm() {
      this.isCustomConfirmVisible = false
    },
    addFilesFromFolder() {
      this.customConfirmParams = {
        header: 'Добавление файлов из папки',
        body: `Добавить файлы из папки?<br>
               Файлы будут добавлены, если их ещё нет в базе данных<br>
               И имеют формат: <strong>YYYY (NN) [Автор] - Песня.flac</strong>
        `,
        timeout: 10,
        callback: this.doAddFilesFromFolder,
      }
      this.isCustomConfirmVisible = true
    },
    doAddFilesFromFolder() {
      this.$store
        .dispatch('createFromFolderPromise', { folder: this.pathToFolder })
        .then((data) => {
          this.customConfirmParams = {
            isAlert: true,
            alertType: data !== '0' ? 'info' : 'warning',
            header: 'Добавление файлов из папки',
            body:
              data !== '0'
                ? `Добавлено записей: <strong>${data}</strong>`
                : 'Ни одного файла не добавлено.',
            timeout: 10,
          }
          this.isCustomConfirmVisible = true
        })
    },
    createDzenPicturesForFolder() {
      this.customConfirmParams = {
        header: 'Создание картинок',
        body: `Создать картинки Dzen для папки?`,
        timeout: 10,
        callback: this.doCreateDzenPicturesForFolder,
      }
      this.isCustomConfirmVisible = true
    },
    doCreateDzenPicturesForFolder() {
      this.$store
        .dispatch('createDzenPicturesForFolderPromise', { folder: this.pathToFolder })
        .then(() => {
          this.customConfirmParams = {
            isAlert: true,
            alertType: 'info',
            header: 'Создание картинок',
            body: 'Готово.',
            timeout: 10,
          }
          this.isCustomConfirmVisible = true
        })
    },
    copyToStore() {
      this.customConfirmParams = {
        header: 'Обновление хранилища',
        body: `Скопировать недостающие файлы в хранилище<br>и создать задачи на кодирование недостающих 720p?`,
        callback: this.doCopyToStore,
        fields: [
          {
            fldName: 'priorLyrics',
            fldLabel: 'Приоритет Lyrics:',
            fldValue: 10,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px' },
          },
          {
            fldName: 'priorKaraoke',
            fldLabel: 'Приоритет Karaoke:',
            fldValue: 10,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px' },
          },
          {
            fldName: 'priorChords',
            fldLabel: 'Приоритет Chords:',
            fldValue: 10,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px' },
          },
        ],
      }
      this.isCustomConfirmVisible = true
    },
    doCopyToStore(result) {
      this.$store
        .dispatch('collectStorePromise', {
          priorLyrics: result.priorLyrics,
          priorKaraoke: result.priorKaraoke,
          priorChords: result.priorChords,
        })
        .then((data) => {
          let result = JSON.parse(data)
          this.customConfirmParams = {
            isAlert: true,
            alertType: 'info',
            header: 'Обновление хранилища',
            body: `Готово.<hr>
                Скопировано файлов: <strong>${result[0]}</strong><br>
                Создано задач на кодирование 720p: <strong>${result[1]}</strong>`,
            timeout: 10,
          }
          this.isCustomConfirmVisible = true
        })
    },
    actualizeVKLinkPictureWeb() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body: `Актуализировать VKLinkPictureWeb?`,
        timeout: 10,
        callback: () => {
          this.$store.dispatch('actualizeVKLinkPictureWebPromise')
        },
      }
      this.isCustomConfirmVisible = true
    },
    smartCopyPeriodByDay() {
      const now = new Date()

      // Первый день следующего месяца: год, месяц+1, день 1
      const firstDay = new Date(now.getFullYear(), now.getMonth() + 1, 1)

      // Последний день следующего месяца: год, месяц+2, день 0
      const lastDay = new Date(now.getFullYear(), now.getMonth() + 2, 0)

      // Функция форматирования в dd.MM.yy
      const formatDate = (date) => {
        const dd = String(date.getDate()).padStart(2, '0')
        const mm = String(date.getMonth() + 1).padStart(2, '0')
        const yy = String(date.getFullYear()).slice(-2)
        return `${dd}.${mm}.${yy}`
      }

      this.customConfirmParams = {
        header: 'Подготовка файлов к публикации',
        body: `Скопировать недостающие файлы в папки для публикации?`,
        callback: this.doSmartCopyPeriodByDay,
        fields: [
          {
            fldName: 'periodStart',
            fldLabel: 'Дата начала:',
            fldValue: formatDate(firstDay),
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '400px', textAlign: 'center', borderRadius: '10px' },
          },
          {
            fldName: 'periodEnd',
            fldLabel: 'Дата конца:',
            fldValue: formatDate(lastDay),
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '400px', textAlign: 'center', borderRadius: '10px' },
          },
          {
            fldName: 'smartCopyPathPrefix',
            fldLabel: 'Папка:',
            fldValue: '/sm-karaoke/work/ПУБЛИКАЦИИ',
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '400px', textAlign: 'center', borderRadius: '10px' },
          },
        ],
      }
      this.isCustomConfirmVisible = true
    },
    doSmartCopyPeriodByDay(result) {
      this.$store
        .dispatch('smartCopyPeriodByDayPromise', {
          periodStart: result.periodStart,
          periodEnd: result.periodEnd,
          smartCopyPathPrefix: result.smartCopyPathPrefix,
        })
        .then((_data) => {
          this.customConfirmParams = {
            isAlert: true,
            alertType: 'info',
            header: 'Подготовка файлов к публикации',
            body: `Готово`,
            timeout: 10,
          }
          this.isCustomConfirmVisible = true
        })
    },
    checkLastAlbumYm() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body: `Найти новые альбомы авторов?`,
        timeout: 10,
        callback: this.doCheckLastAlbumYm,
      }
      this.isCustomConfirmVisible = true
    },
    doCheckLastAlbumYm() {
      this.$store.dispatch('checkLastAlbumYmPromise')
    },
    updateBpmAndKey() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body: `Обновить пустые BPM и KEY из фалов CSV?`,
        timeout: 10,
        callback: this.doUpdateBpmAndKey,
      }
      this.isCustomConfirmVisible = true
    },
    doUpdateBpmAndKey() {
      this.$store.dispatch('updateBpmAndKeyPromise').then((data) => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'info',
          header: 'Обновление BPM и KEY',
          body: `Готово.<hr>
                Обновлено файлов: <strong>${data}</strong>`,
          timeout: 10,
        }
        this.isCustomConfirmVisible = true
      })
    },
    updateBpmAndKeyLV() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body: `Обновить пустые BPM и KEY из фалов LV?`,
        timeout: 10,
        callback: this.doUpdateBpmAndKeyLV,
      }
      this.isCustomConfirmVisible = true
    },
    doUpdateBpmAndKeyLV() {
      this.$store.dispatch('updateBpmAndKeyLVPromise')
    },
    markDublicates() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body: `Найти и обработать дубликаты песен автора «<strong>${this.author}</strong>»?`,
        timeout: 10,
        callback: this.doMarkDublicates,
      }
      this.isCustomConfirmVisible = true
    },
    doMarkDublicates() {
      this.$store.dispatch('markDublicatesPromise', { author: this.author }).then((data) => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: data !== '0' ? 'info' : 'warning',
          header: 'Поиск дубликатов',
          body:
            data !== '0'
              ? `Найдено дубликатов: <strong>${data}</strong>`
              : 'Ни одного дубликата не найдено.',
          timeout: 10,
        }
        this.isCustomConfirmVisible = true
      })
    },
    autoAssignOriginalAll() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body:
          `Автоматически привязать оригинал по аудио-сверке для песен автора «<strong>${this.author}</strong>» со статусом 1 и ненулевым root_id?<br>` +
          `Для каждой будет найден наиболее похожий по аудио вариант из «семьи» (порог 85%), скопированы текст/маркеры со сдвигом, песня сохранена и переведена в статус 2.<br>` +
          `<strong>Операция тяжёлая и идёт в фоне — итог придёт уведомлением.</strong>`,
        timeout: 15,
        callback: this.doAutoAssignOriginalAll,
      }
      this.isCustomConfirmVisible = true
    },
    doAutoAssignOriginalAll() {
      this.$store.dispatch('autoAssignOriginalAllPromise', { author: this.author }).then(() => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'info',
          header: 'Автопривязка оригинала',
          body: `Операция запущена в фоне.<br>Итог придёт уведомлением по завершении.`,
          timeout: 10,
        }
        this.isCustomConfirmVisible = true
      })
    },
    // specs/283-admin-find-parent: точечный поиск только текстового родителя (без аудио-фазы)
    // для всех песен выбранного автора с root_id=0. По умолчанию (crossAuthor=false) подбор
    // родителя идёт только среди песен того же автора; при crossAuthor=true — среди всех.
    findParentForAuthor() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body:
          `Запустить поиск родителя для всех песен автора «<strong>${this.author}</strong>» с root_id=0?<br>` +
          `Для каждой такой песни будет выполнен поиск родителя по точному совпадению нормализованного названия.<br>` +
          `Если родитель найден и у песни ещё нет проверенного текста — root_id будет проставлен.<br>` +
          `<strong>Операция тяжёлая и идёт в фоне — итог придёт уведомлением.</strong>`,
        fields: [
          {
            fldName: 'crossAuthor',
            fldLabel: 'Искать среди песен других авторов:',
            fldValue: false,
            fldIsBoolean: true,
            fldLabelStyle: { width: '320px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { flex: '1' },
          },
        ],
        timeout: 15,
        callback: this.doFindParentForAuthor,
      }
      this.isCustomConfirmVisible = true
    },
    doFindParentForAuthor(result) {
      const crossAuthor = result.crossAuthor === 'true' || result.crossAuthor === true
      this.$store
        .dispatch('findParentForAuthorPromise', { author: this.author, crossAuthor: crossAuthor })
        .then((response) => {
          this.customConfirmParams = {
            isAlert: true,
            alertType: response === 'ALREADY_RUNNING' ? 'warning' : 'info',
            header: 'Поиск родителя',
            body:
              response === 'ALREADY_RUNNING'
                ? `Уже запущено — дождитесь завершения текущего прогона.`
                : `Операция запущена в фоне.<br>Итог придёт уведомлением по завершении.`,
            timeout: 10,
          }
          this.isCustomConfirmVisible = true
        })
    },
    // specs/283-admin-find-parent: фоновый поиск аудио-родителя (только в семье, по root_id
    // транзитивно) для всех песен выбранного автора с root_id <> 0 И у которых ещё НЕ найден
    // audio_parent_id (=0). Не меняет текст/маркеры/статус — только пишет audio_parent_id.
    findAudioParentForAuthor() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body:
          `Запустить поиск аудио-родителя среди всех претендентов в семье для песен автора «<strong>${this.author}</strong>» с root_id ≠ 0, у которых ещё не найден audio_parent_id?<br>` +
          `Для каждой такой песни будет выполнена акустическая сверка (WaveformCompare) с другими песнями в её семье — и при совпадении ≥ 95% будет записан audio_parent_id.<br>` +
          `Текст/маркеры/статус песни НЕ изменяются.<br>` +
          `<strong>Операция очень тяжёлая (ffmpeg-декод на каждого кандидата) и идёт в фоне — итог придёт уведомлением.</strong>`,
        timeout: 15,
        callback: this.doFindAudioParentForAuthor,
      }
      this.isCustomConfirmVisible = true
    },
    doFindAudioParentForAuthor() {
      this.$store
        .dispatch('findAudioParentForAuthorPromise', { author: this.author })
        .then((response) => {
          this.customConfirmParams = {
            isAlert: true,
            alertType: response === 'ALREADY_RUNNING' ? 'warning' : 'info',
            header: 'Поиск аудио-родителя',
            body:
              response === 'ALREADY_RUNNING'
                ? `Уже запущено — дождитесь завершения текущего прогона.`
                : `Операция запущена в фоне.<br>Итог придёт уведомлением по завершении.`,
            timeout: 10,
          }
          this.isCustomConfirmVisible = true
        })
    },
    recalcPlayerReadiness() {
      const scope = this.author
        ? `песен автора «<strong>${this.author}</strong>»`
        : `<strong>ВСЕХ</strong> песен всех авторов`
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body:
          `Пересчитать и сохранить флаги готовности онлайн-плеера (стемы/картинки) для ${scope}?<br>` +
          `Разовая сверка нужна после введения персистентных флагов готовности — иначе уже готовые песни могут временно не показываться как готовые.<br>` +
          `<strong>Операция тяжёлая и идёт в фоне — итог придёт уведомлением.</strong>`,
        timeout: 10,
        callback: this.doRecalcPlayerReadiness,
      }
      this.isCustomConfirmVisible = true
    },
    doRecalcPlayerReadiness() {
      this.$store.dispatch('recalcPlayerReadinessPromise', { author: this.author }).then(() => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'info',
          header: 'Пересчёт готовности плеера',
          body: `Операция запущена.<br>Итог придёт уведомлением по завершении.`,
          timeout: 10,
        }
        this.isCustomConfirmVisible = true
      })
    },
    // specs/015-search-engine-selection: backfill очистки результатов поиска текста для песен,
    // ставших готовыми (статус ≥3) ДО появления автоочистки в Song.saveToDb(). Затрагивает ВСЕ
    // готовые песни всех авторов — фильтра по автору у этой кнопки нет (в отличие от
    // recalcPlayerReadiness), т.к. цель — просто освободить место в таблицах результатов поиска.
    deleteSearchResultsForReadySongs() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body:
          `Удалить сохранённые результаты поиска текста для ВСЕХ готовых песен (статус ≥3)?<br>` +
          `Песни со статусом &lt;3 не затрагиваются.<br>` +
          `<strong>Операция может занять время и идёт в фоне — итог придёт уведомлением.</strong>`,
        timeout: 10,
        callback: this.doDeleteSearchResultsForReadySongs,
      }
      this.isCustomConfirmVisible = true
    },
    doDeleteSearchResultsForReadySongs() {
      this.$store.dispatch('deleteSearchResultsForReadySongsPromise').then(() => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'info',
          header: 'Удаление результатов поиска готовых песен',
          body: `Операция запущена.<br>Итог придёт уведомлением по завершении.`,
          timeout: 10,
        }
        this.isCustomConfirmVisible = true
      })
    },
    // specs/124-news-flags-backfill: одноразовый backfill ПОЛНОГО complete-набора флагов публикации
    // (newsAvailableAnnounced=true, premiumAutoPublishState="COMPLETE" и др.) для уже готовых песен
    // на LOCAL. Без этого первый же save() старой песни после развёртывания feature 122 триггерит
    // автопубликацию в TG+VK для 15000 песен (лавина). Действует только на LOCAL — флаги на PROD
    // попадают через обычную синхронизацию при активном kill-switch'е.
    backfillPublishFlags() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body:
          `Проставить готовым песням на LOCAL полный complete-набор флагов публикации ` +
          `(newsAvailableAnnounced=true, premiumAutoPublishState="COMPLETE", newsPremium*Sent=true)?<br>` +
          `Песни с активной автопубликацией в TG/VK (rendering/publishing) будут пропущены.<br>` +
          `Запуск ТОЛЬКО на LOCAL — на PROD флаги попадают через обычную синхронизацию с активным ` +
          `kill-switch'ом newsAutoPublishKillSwitch.<br>` +
          `<strong>Операция может занять время и идёт в фоне — итог придёт уведомлением ` +
          `с разбивкой по категориям.</strong>`,
        timeout: 15,
        callback: this.doBackfillPublishFlags,
      }
      this.isCustomConfirmVisible = true
    },
    doBackfillPublishFlags() {
      this.$store.dispatch('backfillPublishFlagsPromise').then(() => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'info',
          header: 'Backfill флагов публикации (LOCAL)',
          body: `Операция запущена в фоне.<br>Итог придёт уведомлением с разбивкой по категориям.`,
          timeout: 10,
        }
        this.isCustomConfirmVisible = true
      })
    },
    // specs/011-album-song-rename: разовый (идемпотентный) бэкфилл Album из song_author/
    // song_year/song_album существующих песен. Только LOCAL — на SERVER результат попадает
    // через обычную синхронизацию (RUNBOOK.md §2.2), не повторным запуском этой кнопки.
    backfillAlbumsFromSongs() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body:
          `Создать альбомы из уже заполненных у песен автора/года/альбома и связать с ними песни?<br>` +
          `Обрабатывает только ещё не привязанные песни — можно запускать повторно, дубли не создаются.<br>` +
          `Действует только на LOCAL. Чтобы новые альбомы попали на сервер — запустите синхронизацию ` +
          `«Альбомы» и «Песни» после завершения.<br>` +
          `<strong>Операция может занять время и идёт в фоне — итог придёт уведомлением.</strong>`,
        timeout: 15,
        callback: this.doBackfillAlbumsFromSongs,
      }
      this.isCustomConfirmVisible = true
    },
    doBackfillAlbumsFromSongs() {
      this.$store.dispatch('backfillAlbumsFromSongsPromise').then(() => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'info',
          header: 'Заполнение альбомов из песен',
          body: `Операция запущена в фоне.<br>Итог придёт уведомлением по завершении.`,
          timeout: 10,
        }
        this.isCustomConfirmVisible = true
      })
    },
    // Одноразовая миграция: раньше sortOrder нумеровался внутри (автор, год), теперь — сквозной
    // по автору (управляется драг-дропом в модалке "Альбомы автора"). Сохраняет текущий видимый
    // порядок, просто переводя его в сквозную нумерацию — можно перезапускать без риска.
    normalizeAlbumSortOrder() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body:
          `Перевести порядок отображения альбомов с "внутри года" на сквозной по автору?<br>` +
          `Текущий видимый порядок альбомов сохранится — просто станет сквозной нумерацией, ` +
          `дальше её можно менять драг-дропом в карточке автора.<br>` +
          `Действует только на LOCAL. Идемпотентно — можно запускать повторно.<br>` +
          `<strong>Операция может занять время и идёт в фоне — итог придёт уведомлением.</strong>`,
        timeout: 15,
        callback: this.doNormalizeAlbumSortOrder,
      }
      this.isCustomConfirmVisible = true
    },
    doNormalizeAlbumSortOrder() {
      this.$store.dispatch('normalizeAlbumSortOrderPromise').then(() => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'info',
          header: 'Нормализация порядка альбомов',
          body: `Операция запущена в фоне.<br>Итог придёт уведомлением по завершении.`,
          timeout: 10,
        }
        this.isCustomConfirmVisible = true
      })
    },
    delDublicates() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body: `Удалить дубликаты?`,
        timeout: 10,
        callback: this.doDelDublicates,
      }
      this.isCustomConfirmVisible = true
    },
    doDelDublicates() {
      this.$store.dispatch('deleteDublicatesPromise').then((data) => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: data !== '0' ? 'info' : 'warning',
          header: 'Удаление дубликатов',
          body:
            data !== '0'
              ? `Удалено дубликатов: <strong>${data}</strong>`
              : 'Ни одного дубликата не удалено.',
          timeout: 10,
        }
        this.isCustomConfirmVisible = true
      })
    },
    clearPreDublicates() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body: `Очистить пре-дубликаты?`,
        timeout: 10,
        callback: this.doClearPreDublicates,
      }
      this.isCustomConfirmVisible = true
    },
    doClearPreDublicates() {
      this.$store.dispatch('clearPreDublicatesPromise').then((data) => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: data !== '0' ? 'info' : 'warning',
          header: 'Очистка пре-дубликатов',
          body:
            data !== '0'
              ? `Очищено пре-дубликатов: <strong>${data}</strong>`
              : 'Ни одного пре-дубликата не очищено.',
          timeout: 10,
        }
        this.isCustomConfirmVisible = true
      })
    },
    dictActionAdd() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body: `Добавить слово «<strong>${this.dictValue.toLowerCase()}</strong>» в словарь «<strong>${this.dictType}</strong>»?`,
        timeout: 10,
        callback: this.doDictActionAdd,
      }
      this.isCustomConfirmVisible = true
    },
    doDictActionAdd() {
      let params = {
        dictName: this.dictType,
        dictValue: this.dictValue.toLowerCase(),
        dictAction: 'add',
      }
      this.$store.getters.doTfd(params).then(() => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'info',
          header: 'Добавление слова в словарь',
          body: `Слово «<strong>${this.dictValue.toLowerCase()}</strong>» успешно добавлено в словарь «<strong>${this.dictType}</strong>»?`,
          timeout: 10,
        }
        this.isCustomConfirmVisible = true
      })
    },
    dictActionRemove() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body: `Удалить слово «<strong>${this.dictValue.toLowerCase()}</strong>» из словаря «<strong>${this.dictType}</strong>»?`,
        timeout: 10,
        callback: this.doDictActionRemove,
      }
      this.isCustomConfirmVisible = true
    },
    doDictActionRemove() {
      let params = {
        dictName: this.dictType,
        dictValue: this.dictValue.toLowerCase(),
        dictAction: 'remove',
      }
      this.$store.getters.doTfd(params).then(() => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'info',
          header: 'Удаление слова из словаря',
          body: `Слово «<strong>${this.dictValue.toLowerCase()}</strong>» успешно удалено из словаря «<strong>${this.dictType}</strong>»?`,
          timeout: 10,
        }
        this.isCustomConfirmVisible = true
      })
    },
    autorizeYMstart() {
      this.authYmInProgress = true
      this.$store.dispatch('autorizeYMstartPromise')
    },
    autorizeYMstart2() {
      this.authYmInProgress = true
      this.$store.dispatch('autorizeYMstart2Promise')
    },
    autorizeYMstop() {
      this.$store.dispatch('autorizeYMstopPromise').then(() => {
        this.authYmInProgress = false
      })
    },
    customFunction() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body:
          `Запустить поиск родителей и аудио-родителей для песен с root_id=0 и статусом &lt; 3?<br>` +
          `Для каждой такой песни сначала будет выполнен поиск родителя по точному совпадению названия (текст/маркеры переписываются, кроме случая, когда у песни уже есть проверенный текст — статус ≥ TEXT_CHECK); затем — только для тех, кому родитель найден — поиск аудио-родителя по акустическому сходству (порог 85%, поле не пересекается с родителем).<br>` +
          `<strong>Операция тяжёлая и идёт в фоне — итог придёт уведомлением. Можно запускать повторно, чтобы подхватить новые песни.</strong>`,
        timeout: 15,
        callback: this.doCustomFunction,
      }
      this.isCustomConfirmVisible = true
    },
    doCustomFunction() {
      this.$store.dispatch('customFunctionPromise').then(() => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'info',
          header: 'Поиск родителей и аудио-родителей',
          body: `Операция запущена в фоне.<br>Итог придёт уведомлением по завершении.`,
          timeout: 10,
        }
        this.isCustomConfirmVisible = true
      })
    },
    // specs/277-song-name-censored: подтверждение + запуск фонового реckana
    // tbl_songs.song_name_censored по словарю «Censored».
    rescanAllCensoredNames() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body:
          `Пересканировать цензурированные названия ВСЕХ песен (≈18k строк) по актуальному словарю «Censored»?<br>` +
          `<strong>Операция перезапишет ВСЕ цензурированные названия, включая ручные правки в SongEdit.</strong><br>` +
          `Идёт в фоне, итог придёт SSE-уведомлением по завершении.`,
        timeout: 15,
        callback: this.doRescanAllCensoredNames,
      }
      this.isCustomConfirmVisible = true
    },
    doRescanAllCensoredNames() {
      this.$store.dispatch('rescanAllCensoredNamesPromise').then((response) => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: response === 'ALREADY_RUNNING' ? 'warning' : 'info',
          header: 'Пересканирование цензурированных названий',
          body:
            response === 'ALREADY_RUNNING'
              ? `Уже запущено — дождитесь завершения текущего прогона.`
              : `Операция запущена в фоне.<br>Итог придёт уведомлением по завершении.`,
          timeout: 10,
        }
        this.isCustomConfirmVisible = true
      })
    },
    exportAlignmentDataset() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body:
          `Собрать манифест (текст + тайминг слогов + путь к вокальному стему) по всем песням с готовой разметкой (статус ≥ PROJECT_CREATE) для дообучения forced-alignment модели?<br>` +
          `Аудио никуда не копируется — в манифест пишутся только пути к уже существующим файлам.<br>` +
          `<strong>Операция идёт в фоне — итог придёт уведомлением. Можно запускать повторно, чтобы обновить манифест.</strong>`,
        timeout: 15,
        callback: this.doExportAlignmentDataset,
      }
      this.isCustomConfirmVisible = true
    },
    doExportAlignmentDataset() {
      this.$store.dispatch('exportAlignmentDatasetPromise').then(() => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'info',
          header: 'Экспорт датасета для forced-alignment',
          body: `Операция запущена в фоне.<br>Итог придёт уведомлением по завершении.`,
          timeout: 10,
        }
        this.isCustomConfirmVisible = true
      })
    },
  },
}
</script>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  max-width: 500px;
  min-height: calc(100vh - 85px);
  margin: 0 auto;
  justify-content: center;
}

.home-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 15px;
  border: 1px dashed darksalmon;
  border-radius: 35px;
  background-color: beige;
}

/* Верхние элементы управления держим центрированной колонкой (~500px), чтобы кнопки не растягивались. */
.home-controls {
  display: flex;
  flex-direction: column;
  width: 500px;
  max-width: 100%;
}

.field-and-buttons-wrapper {
  display: flex;
  flex-direction: column;
  padding: 10px 15px;
  border: 1px dashed darksalmon;
  border-radius: 35px;
  background-color: beige;
}

.fields-line-wrapper {
  display: flex;
  flex-direction: row;
}

.button-action {
  width: 100%;
  height: 50px;
  margin: 5px auto;
  border: none;
  border-radius: 20px;
  background-color: royalblue;
  color: #ffffff;
  font-weight: bolder;
}

.button-action-inline {
  flex: 1;
}

.button-action:hover {
  background-color: dodgerblue;
  border: 1px solid black;
}
.button-action[disabled] {
  background-color: lightgray;
}
.button-action[disabled]:hover {
  border: none;
  background-color: lightgray;
}

.input-folder {
  border: thin solid black;
  border-radius: 5px;
  width: 100%;
  height: auto;
}

.input-author {
  border: thin solid black;
  border-radius: 5px;
  width: 100%;
  height: auto;
}

.input-dict-type {
  border: thin solid black;
  border-radius: 5px;
  width: 100%;
  height: auto;
  flex: 1;
}

.input-dict-value {
  border: thin solid black;
  border-radius: 5px;
  width: 100%;
  height: auto;
  flex: 1;
}
</style>
