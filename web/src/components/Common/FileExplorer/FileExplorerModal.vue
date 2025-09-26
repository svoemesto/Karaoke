<template>
  <transition name="modal-fade">
    <div class="fem-modal-backdrop">
      <div class="fem-area">

        <div class="fem-area-modal-header">
          File Explorer
        </div>

        <div class="fem-area-modal-body">
          <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
          <div class="fem-root-wrapper">

            <div class="fem-fileexplorer-bv-table-body-wrapper">
              <div class="fem-fileexplorer-bv-table-body">
                <b-table
                    :items="fileExplorerFiles"
                    :busy="isBusy"
                    :fields="fileExplorerFilesFields"
                    small
                    bordered
                    hover
                    @row-clicked="onRowClicked"
                >
                  <template #table-busy>
                    <div class="fem-text-center text-danger my-2">
                      <b-spinner class="fem-align-middle"></b-spinner>
                      <strong>Loading...</strong>
                    </div>
                  </template>
                  <template #cell(name)="data">
                    <div
                        class="fem-fld-fileexplorer-name"
                        v-text="data.value"
                        :style="{ backgroundColor: data.item.directory ? 'lightyellow' : 'white', color: fileExplorerCurrentPath === data.item.path ? 'blue' : 'black' }"
                    ></div>
                  </template>
                </b-table>
              </div>
            </div>
            <div class="fem-file-explorer-current-path">{{fileExplorerCurrentPath}}</div>

          </div>
        </div>

        <div class="fem-area-modal-footer">
          <button type="button" class="fem-btn-close" @click="ok">OK</button>
          <button type="button" class="fem-btn-close" @click="cancel">Отмена</button>
        </div>

      </div>
    </div>
  </transition>
</template>

<script>

import Vue from "vue";
import { TablePlugin } from 'bootstrap-vue'
import { SpinnerPlugin } from 'bootstrap-vue'
import CustomConfirm from "@/components/Common/CustomConfirm.vue";
import {promisedXMLHttpRequest} from "@/lib/utils";
Vue.use(TablePlugin)
Vue.use(SpinnerPlugin)
export default {
  name: "FileExplorerModal",
  components: {
    CustomConfirm
  },
  props: {
    path: {
      type: String,
      required: false,
      default: undefined
    },
    start: {
      type: String,
      required: true
    },
    extensions: {
      type: String,
      required: false,
      default: ''
    },
    directory: {
      type: Boolean,
      required: false,
      default: false
    },
    multichoice: {
      type: Boolean,
      required: false,
      default: false
    }
  },
  data() {
    return {
      fileExplorerCurrentPath: this.$store.getters.getFileExplorerCurrentPath,
      fileExplorerRootPath: this.$store.getters.getFileExplorerRootPath,
      fileExplorerFiles: this.$store.getters.getFileExplorerFiles,
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
      isBusy: false,
    }
  },
  mounted() {
    console.log('this.path', this.path);
    if (this.path !== undefined && this.path !== '' && this.path !== null) {
      this.fileExplorerCurrentPath = this.path;
    } else {
      this.fileExplorerCurrentPath = this.start;
    }
    if (this.fileExplorerCurrentPath === undefined ) this.fileExplorerCurrentPath = this.start;
    console.log('this.fileExplorerCurrentPath', this.fileExplorerCurrentPath);
    this.getFiles(this.fileExplorerCurrentPath);
  },
  computed: {
    fileExplorerFilesIsLoading() {
      return this.$store.getters.fileExplorerFilesIsLoading;
    },
    fileExplorerFilesFields() {
      return [
        {
          key: 'name',
          label: 'Имя файла',
          style: {
            minWidth: '580px',
            maxWidth: '580px',
            textAlign: 'left',
            fontSize: 'small'
          }
        }
      ]
    }
  },
  methods: {
    onRowClicked(item, index) {
      console.log(`Row '${index}' clicked: `, item.name);
      if (item.toRoot) {
        this.navigateToParent()
      } else {
        this.fileExplorerCurrentPath = item.path;
        if (item.directory) {
          this.getFiles(this.fileExplorerCurrentPath);
        } else {
          this.ok();
        }
      }
    },
    getFiles(path) {
      console.log('path', path);
      let request = { method: 'POST', url: "/apis/files", params: { path: path, extensions: this.extensions } };
      this.isBusy = true;
      promisedXMLHttpRequest(request).then(data => {
        let result = JSON.parse(data);
        console.log('result', result);
        console.log('this.directory', this.directory);
        if (this.directory) {
          result = result.filter(item => item.directory === true);
        }
        if (this.fileExplorerCurrentPath !== this.fileExplorerRootPath) {
          let toRoot = {
            name: "..",
            toRoot: true
          };
          result.unshift(toRoot);
        }
        this.fileExplorerFiles = result;
        this.isBusy = false;
      }).catch(error => {
        console.log(error);
        this.isBusy = false;
      });
    },
    navigateTo(path) {
      this.fileExplorerCurrentPath = path;
      this.getFiles(this.fileExplorerCurrentPath);
    },
    navigateToParent() {
      const parts = this.fileExplorerCurrentPath.split('/');
      parts.pop();
      const parentPath = parts.join('/') || '/';
      this.navigateTo(parentPath);
    },
    closeCustomConfirm() {
      this.isCustomConfirmVisible = false;
    },
    ok() {
      this.$emit('getpath', this.fileExplorerCurrentPath);
      this.$emit('close');
    },
    cancel() {
      this.$emit('close');
    }
  }
}
</script>

<style scoped>

.fem-modal-backdrop {
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

.fem-area {
  background: #FFFFFF;
  box-shadow: 2px 2px 20px 1px;
  overflow-x: auto;
  display: flex;
  flex-direction: column;
  position: relative;
  max-width: calc(100vw - 20px);
  max-height: calc(100vh - 20px);
}

.fem-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.fem-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}

.fem-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: center;
}

.fem-btn-close {
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

.fem-root-wrapper {
  display: flex;
  flex-direction: column;
}

.fem-smartcopy-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.fem-row-label {
  min-width: 140px;
  max-width: 140px;
  text-align: right;
  padding: 0 3px;
  font-size: small;
}
.fem-row-input {
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

.fem-input-field {
  border-radius: 5px;
  width: 385px;
}

.fem-input-field:hover {
  background-color: lightyellow;
}

.fem-input-field:focus {
  background-color: cyan;
}

.fem-button-clear-field {
  border: thin solid black;
  border-radius: 50%;
  font-size: x-small;
  height: 20px;
  width: 20px;
  margin-top: -4px;
  margin-left: -10px;
}

.fem-group-button {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: auto;
}
.fem-group-button-left-right {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: 75px;
  font-size: xx-large;
}
.fem-group-button-up-down {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: 100px;
  font-size: xx-large;
}
.fem-group-button-active {
  background-color: dodgerblue;
}

.fem-fileexplorer-bv-table-body-wrapper {
  display: block;
  width: 600px;
  height: 600px;
  overflow-y: scroll;
}

.fem-fileexplorer-bv-table-body {
  width: fit-content;
}

.fem-fld-fileexplorer-name {
  min-width: 580px;
  max-width: 580px;
  text-align: left;
  font-size: small;
  cursor: default;
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
}
.fem-fld-fileexplorer-name:hover {
  text-decoration: underline;
  cursor: pointer;
}
.fem-file-explorer-current-path {
  width: 580px;
  text-align: left;
  font-size: small;
  color: red;
  border: #2c3e50 thin solid;
  border-radius: 5px;
}
</style>