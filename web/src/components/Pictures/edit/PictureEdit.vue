<template>
  <div :style="styleRoot">
    <div v-if="pictureCurrent">
      <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
      <FileExplorerModal
          v-if="isFileExplorerVisible"
          @close="closeFileExplorer"
          :start="pictureCurrent.pathToFolder"
          extensions='png'
          @getpath="getPathToPictureFile"
      />
      <div class="header">
        <div class="header-picture-id">ID = {{pictureCurrent.id}}</div>
      </div>
      <div class="body">
        <div class="column-1">
          <div class="label-and-input">
            <div class="label">Имя:</div>
            <input class="input-field" v-model="pictureCurrent.name">
            <button class="btn-round" @click="undoField('name')" :disabled="notChanged('name')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(pictureCurrent.name)" :disabled="!pictureCurrent.name"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('name')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="picture-full">
            <img class="image-full" alt="image" :src="'data:image/jpg;base64,' + pictureFullBase64">
          </div>
          <div class="column2-buttons-group ">
            <button class="group-button" @click="loadNewPicture" title="Загрузить новую картинку с диска">Загрузить новую картинку с диска</button>
            <button class="group-button" @click="savePictureToDB" title="Сохранить картинку в базу данных">Сохранить картинку в базу данных</button>
            <button class="group-button" @click="savePictureToDisk" title="Сохранить картинку на диске">Сохранить картинку на диске</button>
          </div>
        </div>
      </div>
      <div class="footer">
        <button class="btn-round-save-double" @click="save" :disabled="notChanged()" title="Сохранить"><img alt="savePicture" class="icon-save-double" src="../../../assets/svg/icon_save.svg"></button>
        <button class="btn-round-double" @click="deletePicture" title="Удалить картинку"><img alt="delete" class="icon-40" src="../../../assets/svg/icon_delete.svg"></button>
      </div>
    </div>
    <div v-else>
      Не выбрана картинка
    </div>
  </div>
</template>

<script>

import CustomConfirm from "../../Common/CustomConfirm.vue";
import FileExplorerModal from "@/components/Common/FileExplorer/FileExplorerModal.vue";
export default {
  name: "PictureEdit",
  components: {
    CustomConfirm,
    FileExplorerModal
  },
  data () {
    return {
      isCustomConfirmVisible: false,
      isFileExplorerVisible: false,
      customConfirmParams: undefined,
      pictureAutoSave: true,
      pictureAutoSaveDelayMs: 1000,
      pictureSaveTimer: undefined,
      pictureFullBase64: ''
    };
  },
  async mounted() {
    this.$store.getters.getPictureValuePromise.then( data => {
      let image = JSON.parse(data);
      this.$store.dispatch('setPictureCurrent', image);
      this.$store.dispatch('setPictureSnapshot', image);
      this.pictureFullBase64 = image.full;
    });
    this.pictureAutoSave = await this.propAutoSave();
    this.pictureAutoSaveDelayMs = Number(await this.propAutoSaveDelayMs());
  },
  watch: {
    pictureDiff: {
      async handler () {
        if (this.pictureDiff.length !== 0 && this.pictureAutoSave) {
          clearTimeout(this.pictureSaveTimer);
          this.pictureSaveTimer = setTimeout(this.save, this.pictureAutoSaveDelayMs);
        }
      }
    }
  },
  computed: {
    styleRoot() {
      return {
        padding: 0,
        margin: 0,
        width: 'auto',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        fontFamily: 'Avenir, Helvetica, Arial, sans-serif'
        // backgroundColor: 'lightyellow'
      }
    },
    pictureCurrent() { return this.$store.getters.getPictureCurrent },
    pictureSnapshot() { return this.$store.getters.getPictureSnapshot },
    pictureDiff() { return this.$store.getters.getPictureDiff },
  },
  methods: {
    async getPathToPictureFile(path) {
      this.pictureFullBase64 = await this.$store.getters.loadPictureFromDiskBase64(path);
    },
    async propAutoSave() {
      const propValue = await this.$store.getters.getPropValue('autoSave');
      return propValue === 'true'
    },
    async propAutoSaveDelayMs() { return await this.$store.getters.getPropValue('autoSaveDelayMs') },
    closeCustomConfirm() { this.isCustomConfirmVisible = false },
    closeFileExplorer() { this.isFileExplorerVisible = false },
    undoField(name) {
      return this.$store.dispatch('setPictureCurrentField', {name: name, value: this.pictureSnapshot[name]})
    },
    async copyToClipboard(value) {
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('', value);
    },
    async pasteFromClipboard(name) {
      await navigator.clipboard.readText().then(data => {
        return this.$store.dispatch('setPictureCurrentField', {name: name, value: data})
      });
    },
    showCopyToClipboardToast(fieldName, fieldValue) {
      // Use a shorter name for this.$createElement
      const h = this.$createElement

      // Функция для преобразования текста с \n в массив VNodes с <br>
      const createTextWithLineBreaks = (text) => {
        if (typeof text !== 'string') {
          return [String(text)];
        }

        const lines = text.split('\n');
        const vnodes = [];

        lines.forEach((line, index) => {
          // Добавляем текст строки
          vnodes.push(line);
          // Если это не последняя строка, добавляем <br>
          if (index < lines.length - 1) {
            vnodes.push(h('br'));
          }
        });

        return vnodes;
      };

      // Создаем сообщение с возможными переносами строк
      const vNodesMsg = h('div', [
        `Значение поля `,
        h('strong', fieldName),
        ` скопировано в буфер обмена:`,
        h('br'),
        h('div', { style: { fontFamily: 'monospace', fontSize: 'x-small' } }, createTextWithLineBreaks(fieldValue))
      ]);

      this.$bvToast.toast([vNodesMsg], {
        title: 'COPY',
        autoHideDelay: 3000,
        toaster: 'b-toaster-top-left',
        bodyClass: 'toast-body-info',
        headerClass: 'toast-header-info',
        appendToast: false,
        // noAutoHide: true
      })
    },
    notChanged(name) {
      if (name) {
        return this.pictureCurrent[name] === this.pictureSnapshot[name];
      } else {
        return this.pictureDiff.length === 0;
      }
    },
    save() {
      clearTimeout(this.pictureSaveTimer);
      let diffs = {};
      for (let diff of this.pictureDiff) {
        diffs[diff.name] = diff.new;
      }
      return this.$store.dispatch('savePicture', diffs)
    },
    deletePicture() {
      this.customConfirmParams = {
        header: 'Подтвердите удаление картинки',
        body: `Удалить картинку <strong>«${this.pictureCurrent.name}»</strong>?`,
        timeout: 10,
        callback: this.doDeletePicture
      }
      this.isCustomConfirmVisible = true;
    },
    doDeletePicture() {
      this.$store.commit('deletePictureCurrent');
      this.$emit('close');
    },
    loadNewPicture() {
      this.isFileExplorerVisible = true;
    },
    savePictureToDB() {
      this.pictureCurrent.full = this.pictureFullBase64;
    },
    savePictureToDisk() {
      this.$store.commit('savePictureCurrentToDisk');
    }
  }
}
</script>

<style scoped>

.header {
  border: thin dashed darkgray;
  border-radius: 10px;
  padding: 5px 0;
  background-color: transparent;
}
.body {
  margin: 0;
  display: flex;
  flex-direction: row;
  height: max-content;
  background-color: transparent;
  z-index: 100;
}
.footer {
  display: flex;
  flex-direction: row;
  justify-content: center;
  border: thin dashed darkgray;
  border-radius: 10px;
  padding: 5px 0;
  background-color: transparent;
}
.header-picture-id {
  text-align: center;
  font-size: 12pt;
  margin: 0 auto;
}
.column-1 {
  width: max-content;
  height: max-content;
  /*background-color: white;*/
  margin: 5px 5px 5px 0;
  display: flex;
  flex-direction: column;
  /*align-items: flex-end;*/
}
.column-2 {
  width: max-content;
  height: max-content;
  margin: 5px 5px 5px 5px;
}
.label-and-input {
  display: flex;
}
.label {
  font-size: small;
  text-align: right;
  width: 110px;
  padding-right: 2px;
  padding-top: 2px;
}
.input-field {
  display: block;
  padding-bottom: 3px;
  width: 310px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}
.input-field:hover {
  background-color: lightyellow;
}
.input-field:focus {
  background-color: cyan;
}
.btn-round {
  border: solid 1px black;
  border-radius: 25%;
  width: 24px;
  height: 24px;
  margin-left: 2px;
  background-color: antiquewhite;
}
.btn-round:hover {
  background-color: lightpink;
}
.btn-round:focus {
  background-color: darksalmon;
}
.btn-round[disabled] {
  background-color: lightgray;
}
.btn-round-double {
  border: solid 1px black;
  border-radius: 6px;
  width: 50px;
  height: 50px;
  margin-left: 2px;
  background-color: antiquewhite;
}
.btn-round-double:hover {
  background-color: lightpink;
}
.btn-round-double:focus {
  background-color: darksalmon;
}
.btn-round-double[disabled] {
  background-color: lightgray;
}
.btn-round-save-double {
  border: solid 1px black;
  border-radius: 6px;
  width: 50px;
  height: 50px;
  margin-left: 2px;
  background-color: red;
}
.btn-round-save-double:hover {
  background-color: darkred;
}
.btn-round-save-double:focus {
  background-color: greenyellow;
}
.btn-round-save-double[disabled] {
  background-color: lightgray;
}
.icon-undo {
  width: 18px;
  height: 18px;
  margin-left: -4px;
  margin-top: -10px;
}
.icon-copy {
  width: 24px;
  height: 24px;
  margin-left: -6px;
  margin-top: -10px;
}
.icon-paste {
  width: 24px;
  height: 24px;
  margin-left: -6px;
  margin-top: -10px;
}
.picture-full {
  background-color: black;
  width: auto;
  height: 400px;
}
.image-full {
  width: auto;
  height: 400px;
}
.column2-buttons-group {
  font-size: small;
  display: flex;
  flex-direction: column;
}
.group-button {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: auto;
}
</style>