<template>
  <div class="home">
    <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
    <FileExplorerModal
        v-if="isFileExplorerVisible"
        @close="closeFileExplorer"
        :path="pathToFolder"
        start="/sm-karaoke/work"
        directory
        @getpath="getPath"
    />
    <div class="home-wrapper">
      <div class="home-controls">
      <datalist id="list_authors">
        <option v-for="author in songAuthors" :key="author" :value="author"/>
      </datalist>
      <datalist id="list_dicts">
        <option v-for="dict in dicts" :key="dict" :value="dict"/>
      </datalist>
      <div class="field-and-buttons-wrapper">
        <input class="input-folder" type="text" placeholder="Путь к папке" v-model="pathToFolder" @dblclick="isFileExplorerVisible=true">
        <button class="button-action" @click="addFilesFromFolder" :disabled="!pathToFolder" >Добавить файлы из папки</button>
        <button class="button-action" @click="createDzenPicturesForFolder" :disabled="!pathToFolder">Создать картинки плейлистов Dzen для папки</button>
      </div>
      <!-- <button class="button-action" @click="copyToStore">Обновить хранилище</button>
      <button class="button-action" @click="actualizeVKLinkPictureWeb">Актуализация VKLinkPictureWeb</button> -->
      <button class="button-action" @click="smartCopyPeriodByDay">Подготовить файлы для публикации</button>
      <!-- <button class="button-action" @click="checkLastAlbumYm">Поиск новых альбомов</button>
      <button class="button-action" @click="updateBpmAndKey">Обновить пустые BPM и KEY из фалов CSV</button>
      <button class="button-action" @click="updateBpmAndKeyLV">Обновить пустые BPM и KEY из фалов LV</button> -->
      <div class="field-and-buttons-wrapper">
        <input list="list_authors" class="input-author" type="text" placeholder="Автор" v-model="author">
        <!-- <button class="button-action" @click="markDublicates" :disabled="!author">Найти и обработать дубликаты песен автора</button> -->
        <button class="button-action" @click="autoAssignOriginalAll" :disabled="!author">Автопривязать оригинал по аудио (статус 1 → 2)</button>
      </div>
      <!-- <button class="button-action" @click="delDublicates">Удалить дубликаты</button>
      <button class="button-action" @click="clearPreDublicates">Очистить информацию о пре-дубликатах</button> -->
      <div class="field-and-buttons-wrapper">
        <div class="fields-line-wrapper">
          <input list="list_dicts" class="input-dict-type" type="text" placeholder="Словарь" v-model="dictType">
          <input class="input-dict-value" type="text" placeholder="Слово" v-model="dictValue">
        </div>
        <div class="fields-line-wrapper">
          <button class="button-action button-action-inline" @click="dictActionAdd" :disabled="!dictType || !dictValue">Добавить слово в словарь</button>
          <button class="button-action button-action-inline" @click="dictActionRemove" :disabled="!dictType || !dictValue">Удалить слово из словаря</button>
        </div>
      </div>
      <div class="fields-line-wrapper">
        <button class="button-action button-action-inline" @click="autorizeYMstart" :disabled="authYmInProgress">Auth YM 1</button>
        <button class="button-action button-action-inline" @click="autorizeYMstart2" :disabled="authYmInProgress">Auth YM 2</button>
        <button class="button-action button-action-inline" @click="autorizeYMstop" :disabled="!authYmInProgress">Auth YM: Stop</button>
      </div>
      <button class="button-action" @click="customFunction">Выполнить Custom Function</button>
      </div>
      <SyncTable />
    </div>
  </div>
</template>

<script>

import CustomConfirm from '../components/Common/CustomConfirm.vue';
import FileExplorerModal from "../components/Common/FileExplorer/FileExplorerModal.vue";
import SyncTable from '../components/Sync/SyncTable.vue';
// import { useFileDialog } from '@vueuse/core'
export default {
  name: 'HomeView',
  components: {
    CustomConfirm,
    FileExplorerModal,
    SyncTable
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
      dicts:[],
      authYmInProgress: false
    }
  },
  async mounted() {
    let songAuthors = await this.$store.getters.songAuthors;
    let dicts = await this.$store.getters.dicst;
    this.songAuthors = songAuthors;
    this.dicts = dicts;
  },
  methods: {
    getPath(path) {
      this.pathToFolder = path;
    },
    closeFileExplorer() {
      this.isFileExplorerVisible = false;
    },
    closeCustomConfirm() {
      this.isCustomConfirmVisible = false;
    },
    addFilesFromFolder() {
      this.customConfirmParams = {
        header: 'Добавление файлов из папки',
        body: `Добавить файлы из папки?<br>
               Файлы будут добавлены, если их ещё нет в базе данных<br>
               И имеют формат: <strong>YYYY (NN) [Автор] - Песня.flac</strong>
        `,
        timeout: 10,
        callback: this.doAddFilesFromFolder
      }
      this.isCustomConfirmVisible = true;
    },
    doAddFilesFromFolder() {
      this.$store.dispatch('createFromFolderPromise', {folder: this.pathToFolder}).then(data => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: data !== '0' ? 'info' : 'warning',
          header: 'Добавление файлов из папки',
          body: data !== '0' ? `Добавлено записей: <strong>${data}</strong>` : 'Ни одного файла не добавлено.',
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
    },
    createDzenPicturesForFolder() {
      this.customConfirmParams = {
        header: 'Создание картинок',
        body: `Создать картинки Dzen для папки?`,
        timeout: 10,
        callback: this.doCreateDzenPicturesForFolder
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateDzenPicturesForFolder() {
      this.$store.dispatch('createDzenPicturesForFolderPromise', {folder: this.pathToFolder}).then(() => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'info',
          header: 'Создание картинок',
          body: 'Готово.',
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
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
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          },
          {
            fldName: 'priorKaraoke',
            fldLabel: 'Приоритет Karaoke:',
            fldValue: 10,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          },
          {
            fldName: 'priorChords',
            fldLabel: 'Приоритет Chords:',
            fldValue: 10,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doCopyToStore(result) {
      this.$store.dispatch('collectStorePromise', {priorLyrics: result.priorLyrics, priorKaraoke: result.priorKaraoke, priorChords: result.priorChords}).then(data => {
        let result = JSON.parse(data);
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'info',
          header: 'Обновление хранилища',
          body: `Готово.<hr>
                Скопировано файлов: <strong>${result[0]}</strong><br>
                Создано задач на кодирование 720p: <strong>${result[1]}</strong>`,
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
    },
    actualizeVKLinkPictureWeb() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body: `Актуализировать VKLinkPictureWeb?`,
        timeout: 10,
        callback: () => { this.$store.dispatch('actualizeVKLinkPictureWebPromise') }
      }
      this.isCustomConfirmVisible = true;
    },
    smartCopyPeriodByDay() {
      const now = new Date();

      // Первый день следующего месяца: год, месяц+1, день 1
      const firstDay = new Date(now.getFullYear(), now.getMonth() + 1, 1);

      // Последний день следующего месяца: год, месяц+2, день 0
      const lastDay = new Date(now.getFullYear(), now.getMonth() + 2, 0);

      // Функция форматирования в dd.MM.yy
      const formatDate = (date) => {
          const dd = String(date.getDate()).padStart(2, '0');
          const mm = String(date.getMonth() + 1).padStart(2, '0');
          const yy = String(date.getFullYear()).slice(-2);
          return `${dd}.${mm}.${yy}`;
      };

      this.customConfirmParams = {
        header: 'Подготовка файлов к публикации',
        body: `Скопировать недостающие файлы в папки для публикации?`,
        callback: this.doSmartCopyPeriodByDay,
        fields: [
          {
            fldName: 'periodStart',
            fldLabel: 'Дата начала:',
            fldValue: formatDate(firstDay),
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '400px', textAlign: 'center', borderRadius: '10px'}
          },
          {
            fldName: 'periodEnd',
            fldLabel: 'Дата конца:',
            fldValue: formatDate(lastDay),
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '400px', textAlign: 'center', borderRadius: '10px'}
          },
          {
            fldName: 'smartCopyPathPrefix',
            fldLabel: 'Папка:',
            fldValue: '/sm-karaoke/work/ПУБЛИКАЦИИ',
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '400px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doSmartCopyPeriodByDay(result) {
      this.$store.dispatch('smartCopyPeriodByDayPromise', {periodStart: result.periodStart, periodEnd: result.periodEnd, smartCopyPathPrefix: result.smartCopyPathPrefix}).then(data => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'info',
          header: 'Подготовка файлов к публикации',
          body: `Готово`,
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
    },
    checkLastAlbumYm() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body: `Найти новые альбомы авторов?`,
        timeout: 10,
        callback: this.doCheckLastAlbumYm
      }
      this.isCustomConfirmVisible = true;
    },
    doCheckLastAlbumYm() {
      this.$store.dispatch('checkLastAlbumYmPromise')
    },
    updateBpmAndKey() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body: `Обновить пустые BPM и KEY из фалов CSV?`,
        timeout: 10,
        callback: this.doUpdateBpmAndKey
      }
      this.isCustomConfirmVisible = true;
    },
    doUpdateBpmAndKey() {
      this.$store.dispatch('updateBpmAndKeyPromise').then(data => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'info',
          header: 'Обновление BPM и KEY',
          body: `Готово.<hr>
                Обновлено файлов: <strong>${data}</strong>`,
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
    },
    updateBpmAndKeyLV() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body: `Обновить пустые BPM и KEY из фалов LV?`,
        timeout: 10,
        callback: this.doUpdateBpmAndKeyLV
      }
      this.isCustomConfirmVisible = true;
    },
    doUpdateBpmAndKeyLV() {
      this.$store.dispatch('updateBpmAndKeyLVPromise')
    },
    markDublicates() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body: `Найти и обработать дубликаты песен автора «<strong>${this.author}</strong>»?`,
        timeout: 10,
        callback: this.doMarkDublicates
      }
      this.isCustomConfirmVisible = true;
    },
    doMarkDublicates() {
      this.$store.dispatch('markDublicatesPromise', {author: this.author}).then(data => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: data !== '0' ? 'info' : 'warning',
          header: 'Поиск дубликатов',
          body: data !== '0' ? `Найдено дубликатов: <strong>${data}</strong>` : 'Ни одного дубликата не найдено.',
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
    },
    autoAssignOriginalAll() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body: `Автоматически привязать оригинал по аудио-сверке для песен автора «<strong>${this.author}</strong>» со статусом 1 и ненулевым root_id?<br>`
            + `Для каждой будет найден наиболее похожий по аудио вариант из «семьи» (порог 85%), скопированы текст/маркеры со сдвигом, песня сохранена и переведена в статус 2.<br>`
            + `<strong>Операция тяжёлая и идёт в фоне — итог придёт уведомлением.</strong>`,
        timeout: 15,
        callback: this.doAutoAssignOriginalAll
      }
      this.isCustomConfirmVisible = true;
    },
    doAutoAssignOriginalAll() {
      this.$store.dispatch('autoAssignOriginalAllPromise', {author: this.author}).then(() => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'info',
          header: 'Автопривязка оригинала',
          body: `Операция запущена в фоне.<br>Итог придёт уведомлением по завершении.`,
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
    },
    delDublicates() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body: `Удалить дубликаты?`,
        timeout: 10,
        callback: this.doDelDublicates
      }
      this.isCustomConfirmVisible = true;
    },
    doDelDublicates() {
      this.$store.dispatch('deleteDublicatesPromise').then(data => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: data !== '0' ? 'info' : 'warning',
          header: 'Удаление дубликатов',
          body: data !== '0' ? `Удалено дубликатов: <strong>${data}</strong>` : 'Ни одного дубликата не удалено.',
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
    },
    clearPreDublicates() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body: `Очистить пре-дубликаты?`,
        timeout: 10,
        callback: this.doClearPreDublicates
      }
      this.isCustomConfirmVisible = true;
    },
    doClearPreDublicates() {
      this.$store.dispatch('clearPreDublicatesPromise').then(data => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: data !== '0' ? 'info' : 'warning',
          header: 'Очистка пре-дубликатов',
          body: data !== '0' ? `Очищено пре-дубликатов: <strong>${data}</strong>` : 'Ни одного пре-дубликата не очищено.',
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
    },
    dictActionAdd() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body: `Добавить слово «<strong>${this.dictValue.toLowerCase()}</strong>» в словарь «<strong>${this.dictType}</strong>»?`,
        timeout: 10,
        callback: this.doDictActionAdd
      }
      this.isCustomConfirmVisible = true;
    },
    doDictActionAdd() {
      let params = {
        dictName: this.dictType,
        dictValue: this.dictValue.toLowerCase(),
        dictAction: 'add'
      }
      this.$store.getters.doTfd(params).then(() => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'info',
          header: 'Добавление слова в словарь',
          body: `Слово «<strong>${this.dictValue.toLowerCase()}</strong>» успешно добавлено в словарь «<strong>${this.dictType}</strong>»?`,
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
    },
    dictActionRemove() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body: `Удалить слово «<strong>${this.dictValue.toLowerCase()}</strong>» из словаря «<strong>${this.dictType}</strong>»?`,
        timeout: 10,
        callback: this.doDictActionRemove
      }
      this.isCustomConfirmVisible = true;
    },
    doDictActionRemove() {
      let params = {
        dictName: this.dictType,
        dictValue: this.dictValue.toLowerCase(),
        dictAction: 'remove'
      }
      this.$store.getters.doTfd(params).then(() => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'info',
          header: 'Удаление слова из словаря',
          body: `Слово «<strong>${this.dictValue.toLowerCase()}</strong>» успешно удалено из словаря «<strong>${this.dictType}</strong>»?`,
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
    },
    autorizeYMstart() {
      this.authYmInProgress = true;
      this.$store.dispatch('autorizeYMstartPromise');
    },
    autorizeYMstart2() {
      this.authYmInProgress = true;
      this.$store.dispatch('autorizeYMstart2Promise');
    },
    autorizeYMstop() {
      this.$store.dispatch('autorizeYMstopPromise').then(() => {
        this.authYmInProgress = false;
      });
    },
    customFunction() {
      this.customConfirmParams = {
        header: 'Подтвердите действие',
        body: `Запустить Custom Function?`,
        timeout: 10,
        callback: this.doCustomFunction
      }
      this.isCustomConfirmVisible = true;
    },
    doCustomFunction() {
      this.$store.dispatch('customFunctionPromise').then(() => {
        this.customConfirmParams = {
          isAlert: true,
          alertType: 'info',
          header: 'Запуск Custom Function',
          body: `Custom Function успешно запущена.`,
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
    },
  }
}
</script>

<style scoped>

.home {
  display: flex;
  flex-direction: column;
  max-width: 780px;
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

/* Верхние элементы управления держим узкой центрированной колонкой (~500px),
   чтобы кнопки не растягивались, а широкую таблицу синхронизации пускаем на всю ширину. */
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
  color: #FFFFFF;
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