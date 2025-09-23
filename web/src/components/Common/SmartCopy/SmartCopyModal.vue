<template>
  <transition name="modal-fade">
    <div class="scm-modal-backdrop">
      <div class="scm-area">

        <div class="scm-area-modal-header">
          Smart Copy
        </div>

        <div class="scm-area-modal-body">
          <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
          <FileExplorerModal
              v-if="isFileExplorerVisible"
              @close="closeFileExplorer"
              :path="smartCopyPath"
              start="/clouds/Yandex.Disk/Karaoke"
              directory
              @getpath="getPath"
          />
          <div class="scm-root-wrapper">

            <div class="scm-smartcopy-row">
              <div class="scm-row-label">
                <div v-text="'Версия:'"></div>
              </div>
              <div class="scm-row-input">
                <button class="scm-group-button" :class="scm-smartCopySongVersionButtonClass('KARAOKE')" type="button" value="KARAOKE" @click="setSmartCopySongVersion('KARAOKE')">KARAOKE</button>
                <button class="scm-group-button" :class="scm-smartCopySongVersionButtonClass('LYRICS')" type="button" value="LYRICS" @click="setSmartCopySongVersion('LYRICS')">LYRICS</button>
                <button class="scm-group-button" :class="scm-smartCopySongVersionButtonClass('CHORDS')" type="button" value="CHORDS" @click="setSmartCopySongVersion('CHORDS')">CHORDS</button>
                <button class="scm-group-button" :class="scm-smartCopySongVersionButtonClass('TABS')" type="button" value="TABS" @click="setSmartCopySongVersion('TABS')">TABS</button>
              </div>
            </div>

            <div class="scm-smartcopy-row">
              <div class="scm-row-label">
                <div v-text="'Качество:'"></div>
              </div>
              <div class="scm-row-input">
                <button class="scm-group-button" :class="scm-smartCopySongResolutionButtonClass('1080p')" type="button" value="1080p" @click="setSmartCopySongResolution('1080p')">1080p 60fps</button>
                <button class="scm-group-button" :class="scm-smartCopySongResolutionButtonClass('720p')" type="button" value="720p" @click="setSmartCopySongResolution('720p')">720p 30fps</button>
              </div>
            </div>

            <div class="scm-smartcopy-row">
              <div class="scm-row-label">
                <div v-text="'Папки:'"></div>
              </div>
              <div class="scm-row-input">
                <button class="scm-group-button" :class="scm-smartCopyCreateSubfoldersAuthorsButtonClass(false)" type="button" value="false" @click="setSmartCopyCreateSubfoldersAuthors(false)">Не создавать</button>
                <button class="scm-group-button" :class="scm-smartCopyCreateSubfoldersAuthorsButtonClass(true)" type="button" value="true" @click="setSmartCopyCreateSubfoldersAuthors(true)">Создавать для авторов</button>
              </div>
            </div>

            <div class="scm-smartcopy-row">
              <div class="scm-row-label">
                <div v-text="'Шаблон:'"></div>
              </div>
              <div class="scm-row-input">
                <input class="scm-input-field" v-model="smartCopyRenameTemplate">
              </div>
              <button :disabled="!smartCopyRenameTemplate" class="scm-button-clear-field" @click.left="smartCopyRenameTemplate=''" v-text="'X'"></button>
            </div>

            <div class="scm-smartcopy-row">
              <div class="scm-row-label">
                <div v-text="'Путь:'"></div>
              </div>
              <div class="scm-row-input">
                <input class="scm-input-field" v-model="smartCopyPath" @dblclick="isFileExplorerVisible=true">
              </div>
              <button :disabled="!smartCopyPath" class="scm-button-clear-field" @click.left="smartCopyPath=''" v-text="'X'"></button>
            </div>

          </div>
        </div>

        <div class="scm-area-modal-footer">
          <button type="button" class="scm-btn-close" @click="smartCopy">Smart Copy</button>
          <button type="button" class="scm-btn-close" @click="cancel">Отмена</button>
        </div>

      </div>
    </div>
  </transition>
</template>

<script>

import CustomConfirm from "@/components/Common/CustomConfirm.vue";
import FileExplorerModal from "@/components/Common/FileExplorer/FileExplorerModal.vue";

export default {
  name: "SmartCopyModal",
  components: {
    CustomConfirm,
    FileExplorerModal
  },
  props: {
    ids: {
      type: Array,
      required: true
    }
  },
  data() {
    return {
      smartCopySongVersion: this.$store.getters.getSmartCopySongVersion,
      smartCopySongResolution: this.$store.getters.getSmartCopySongResolution,
      smartCopyCreateSubfoldersAuthors: this.$store.getters.getSmartCopyCreateSubfoldersAuthors,
      smartCopyRenameTemplate: this.$store.getters.getSmartCopyRenameTemplate,
      smartCopyPath: this.$store.getters.getSmartCopyPath,
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
      isFileExplorerVisible: false,
      file: null
    }
  },
  computed: {},
  methods: {
    getPath(path) {
      this.smartCopyPath = path;
    },
    closeCustomConfirm() {
      this.isCustomConfirmVisible = false;
    },
    closeFileExplorer() {
      this.isFileExplorerVisible = false;
    },
    smartCopySongVersionButtonClass(smartCopySongVersion) {
      return smartCopySongVersion === this.smartCopySongVersion ? 'group-button-active' : ''
    },
    smartCopySongResolutionButtonClass(smartCopySongResolution) {
      return smartCopySongResolution === this.smartCopySongResolution ? 'group-button-active' : ''
    },
    smartCopyCreateSubfoldersAuthorsButtonClass(smartCopyCreateSubfoldersAuthors) {
      return smartCopyCreateSubfoldersAuthors === this.smartCopyCreateSubfoldersAuthors ? 'group-button-active' : ''
    },
    setSmartCopySongVersion(smartCopySongVersion) {
      this.smartCopySongVersion = smartCopySongVersion;
    },
    setSmartCopySongResolution(smartCopySongResolution) {
      this.smartCopySongResolution = smartCopySongResolution;
    },
    setSmartCopyCreateSubfoldersAuthors(smartCopyCreateSubfoldersAuthors) {
      this.smartCopyCreateSubfoldersAuthors = smartCopyCreateSubfoldersAuthors;
    },
    smartCopy() {
      let body = `Для выбранных песен (<strong>${this.ids.length}</strong> шт.) произвести Smart Copy с параметрами:<br>`
      body = body + `Версия: <strong>${this.smartCopySongVersion}</strong><br>`
      body = body + `Качество: <strong>${this.smartCopySongResolution}</strong><br>`
      if (this.smartCopyCreateSubfoldersAuthors === true) {
        body = body + `<strong>Создавать под-папки для каждого автора</strong><br>`
      } else {
        body = body + `<strong>Не создавать под-папки</strong><br>`
      }
      if (this.smartCopyRenameTemplate !== '') {
        body = body + `Шаблон переименования: <strong>${this.smartCopyRenameTemplate}</strong><br>`
      }
      body = body + `Папка для копирования: <strong>${this.smartCopyPath}</strong><br>`
      body = body + `<br>Произвести Smart Copy для всех песен?`

      this.customConfirmParams = {
        header: 'Подтвердите Smart Copy',
        body: body,
        callback: this.doSmartCopy,
        fields: [
          {
            fldName: 'prior',
            fldLabel: 'Приоритет:',
            fldValue: this.$store.getters.getLastPriorSmartCopy,
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '40px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doSmartCopy(result) {
      this.$store.dispatch('setLastPriorSmartCopy', {value: result.prior});

      this.$store.dispatch('setSmartCopySongVersion', { smartCopySongVersion: this.smartCopySongVersion });
      this.$store.dispatch('setSmartCopySongResolution', { smartCopySongResolution: this.smartCopySongResolution });
      this.$store.dispatch('setSmartCopyCreateSubfoldersAuthors', { smartCopyCreateSubfoldersAuthors: this.smartCopyCreateSubfoldersAuthors });
      this.$store.dispatch('setSmartCopyRenameTemplate', { smartCopyRenameTemplate: this.smartCopyRenameTemplate });
      this.$store.dispatch('setSmartCopyPath', { smartCopyPath: this.smartCopyPath });

      let params = {};
      if (this.smartCopySongVersion) params.smartCopySongVersion = this.smartCopySongVersion;
      if (this.smartCopySongResolution) params.smartCopySongResolution = this.smartCopySongResolution;
      if (this.smartCopyCreateSubfoldersAuthors) params.smartCopyCreateSubfoldersAuthors = this.smartCopyCreateSubfoldersAuthors;
      if (this.smartCopyRenameTemplate) params.smartCopyRenameTemplate = this.smartCopyRenameTemplate;
      if (this.smartCopyPath) params.smartCopyPath = this.smartCopyPath;
      params.songsIds = this.ids.join(';');
      params.prior = result.prior;

      this.$store.dispatch('createSmartCopyForAllPromise', params).then(data => {
        let response = JSON.parse(data);
        this.customConfirmParams = {
          isAlert: true,
          alertType: response ? 'info' : 'error',
          header: 'Создание Smart Copy',
          body: `Создание Smart Copy для всех песен прошло успешно.`,
          timeout: 10
        }
        this.isCustomConfirmVisible = true;
      })
      this.$emit('close');
    },
    cancel() {
      this.$emit('close');
    }
  }
}
</script>

<style scoped>

.scm-modal-backdrop {
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

.scm-area {
  background: #FFFFFF;
  box-shadow: 2px 2px 20px 1px;
  overflow-x: auto;
  display: flex;
  flex-direction: column;
  width: auto;
  height: auto;
  position: relative;
  max-width: calc(100vw - 20px);
  max-height: calc(100vh - 20px);
}

.scm-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.scm-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}

.scm-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: center;
}

.scm-btn-close {
  border: 1px solid white;
  border-radius: 10px;
  cursor: pointer;
  font-weight: bold;
  color: white;
  background: transparent;
  width: 150px;
  height: auto;
  font-size: small;
}

.scm-root-wrapper {
  display: flex;
  flex-direction: column;
}

.scm-smartcopy-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.scm-row-label {
  min-width: 140px;
  max-width: 140px;
  text-align: right;
  padding: 0 3px;
  font-size: small;
}
.scm-row-input {
  display: flex;
  flex-direction: column;
  padding-bottom: 3px;
  width: 400px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}

.scm-input-field {
  border-radius: 5px;
  width: 385px;
}

.scm-input-field:hover {
  background-color: lightyellow;
}

.scm-input-field:focus {
  background-color: cyan;
}

.scm-button-clear-field {
  border: thin solid black;
  border-radius: 50%;
  font-size: x-small;
  height: 20px;
  width: 20px;
  margin-top: -4px;
  margin-left: -10px;
}

.scm-group-button {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: auto;
}
.scm-group-button-left-right {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: 75px;
  font-size: xx-large;
}
.scm-group-button-up-down {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: 100px;
  font-size: xx-large;
}
.scm-group-button-active {
  background-color: dodgerblue;
}

</style>