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
      <button class="button-action" @click="copyToStore">Обновить хранилище</button>
      <button class="button-action" @click="actualizeVKLinkPictureWeb">Актуализация VKLinkPictureWeb</button>
      <button class="button-action" @click="checkLastAlbumYm">Поиск новых альбомов</button>
      <button class="button-action" @click="updateBpmAndKey">Обновить пустые BPM и KEY из фалов CSV</button>
      <button class="button-action" @click="updateBpmAndKeyLV">Обновить пустые BPM и KEY из фалов LV</button>
      <div class="field-and-buttons-wrapper">
        <input list="list_authors" class="input-author" type="text" placeholder="Автор" v-model="author">
        <button class="button-action" @click="markDublicates" :disabled="!author">Найти и пометить дубликаты песен автора</button>
      </div>
      <button class="button-action" @click="delDublicates">Удалить дубликаты</button>
      <button class="button-action" @click="clearPreDublicates">Очистить информацию о пре-дубликатах</button>
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
      <button class="button-action" @click="customFunction">Выполнить Custom Function</button>
      <button class="button-action" @click="updateRemoteSettings" :disabled="!allowUpdateRemote">Обновить REMOTE Database SETTINGS</button>
      <button class="button-action" @click="updateRemotePictures" :disabled="!allowUpdateRemote">Обновить REMOTE Database PICTURES</button>
      <button class="button-action" @click="updateLocalSettings" :disabled="!allowUpdateLocal">Обновить LOCAL Database SETTINGS</button>
      <button class="button-action" @click="updateLocalPictures" :disabled="!allowUpdateLocal">Обновить LOCAL Database PICTURES</button>
    </div>
  </div>
</template>

<script>

import CustomConfirm from '../components/Common/CustomConfirm.vue';
import FileExplorerModal from "../components/Common/FileExplorer/FileExplorerModal.vue";
// import { useFileDialog } from '@vueuse/core'
export default {
  name: 'HomeView',
  components: {
    CustomConfirm,
    FileExplorerModal
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
      allowUpdateRemote: false,
      allowUpdateLocal: false
    }
  },
  async mounted() {
    let songAuthors = await this.$store.getters.songAuthors;
    let dicts = await this.$store.getters.dicst;
    this.songAuthors = songAuthors;
    this.dicts = dicts;
    this.allowUpdateRemote = await this.propAllowUpdateRemote();
    this.allowUpdateLocal = await this.propAllowUpdateLocal();
  },
  methods: {
    async propAllowUpdateRemote() {
      const propValue = await this.$store.getters.getPropValue('allowUpdateRemote');
      return propValue === 'true'
    },
    async propAllowUpdateLocal() {
      const propValue = await this.$store.getters.getPropValue('allowUpdateLocal');
      return propValue === 'true'
    },
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
        body: `Найти и пометить дубликаты песен автора «<strong>${this.author}</strong>»?`,
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
    updateRemoteSettings() {
      this.customConfirmParams = {
        header: 'Обновление серверной БД',
        body: `Обновить таблицу песен на сервере данными из локальной базы данных?`,
        timeout: 10,
        callback: () => { this.$store.dispatch('updateRemoteSettingsPromise') }
      }
      this.isCustomConfirmVisible = true;
    },
    updateRemotePictures() {
      this.customConfirmParams = {
        header: 'Обновление серверной БД',
        body: `Обновить таблицу изображений на сервере данными из локальной базы данных?`,
        timeout: 10,
        callback: () => { this.$store.dispatch('updateRemotePicturesPromise') }
      }
      this.isCustomConfirmVisible = true;
    },
    updateLocalSettings() {
      this.customConfirmParams = {
        header: 'Обновление локальной БД',
        body: `Обновить таблицу песен в локальной базе данных данными с сервера?`,
        timeout: 10,
        callback: () => { this.$store.dispatch('updateLocalSettingsPromise') }
      }
      this.isCustomConfirmVisible = true;
    },
    updateLocalPictures() {
      this.customConfirmParams = {
        header: 'Обновление локальной БД',
        body: `Обновить таблицу изображений в локальной базе данных данными с сервера?`,
        timeout: 10,
        callback: () => { this.$store.dispatch('updateLocalPicturesPromise') }
      }
      this.isCustomConfirmVisible = true;
    }
  }
}
</script>

<style scoped>

.home {
  display: flex;
  flex-direction: column;
  max-width: 500px;
  min-height: calc(100vh - 25px);
  margin: 0 auto;
  justify-content: center;
}

.home-wrapper {
  display: flex;
  flex-direction: column;
  padding: 10px 15px;
  border: 1px dashed darksalmon;
  border-radius: 35px;
  background-color: beige;
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