<template>
  <div class="authors-bv-table">
<!--    <AuthorEditModal v-if="isAuthorEditVisible" @close="closeAuthorEdit"/>-->
    <AuthorsFilter v-if="isAuthorsFilterVisible" @close="closeAuthorsFilter"/>
    <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
    <div class="authors-bv-table-header">
      <b-pagination
          v-model="currentPage"
          :total-rows="countRows"
          :per-page="perPage"
          :limit="20"
          size="sm"
          pills
      ></b-pagination>
    </div>
    <div class="authors-bv-table-body">
      <b-table
          :items="authorsDigests"
          :busy="isBusy"
          :fields="authorDigestFields"
          :per-page="perPage"
          :current-page="currentPage"
          small
          bordered
          hover
          @row-clicked="onRowClicked"
      >
        <template #table-busy>
          <div class="text-center text-danger my-2">
            <b-spinner class="align-middle"></b-spinner>
            <strong>Loading...</strong>
          </div>
        </template>
        <template #table-colgroup="scope">
          <col
              v-for="field in scope.fields"
              :key="field.key"
              :style="field.style"
          >
        </template>

        <template #cell(id)="data">
          <div
              class="fld-author-id"
              v-text="data.value"
              :style="{ color: currentAuthorId === data.item.id ? 'blue' : 'black' }"
              @click.left="changeValue(data.item)"
          ></div>
        </template>

        <template #cell(author)="data">
          <div
              class="fld-author"
              v-text="data.value"
              :style="{ color: currentAuthorId === data.item.id ? 'blue' : 'black' }"
              @click.left="changeValue(data.item)"
          ></div>
        </template>

        <template #cell(ymId)="data">
          <div
              class="fld-ymId"
              v-text="data.value"
              :style="{ color: currentAuthorId === data.item.id ? 'blue' : 'black' }"
              @click.left="openYandexMusicAuthor(data.item)"
          ></div>
        </template>

        <template #cell(lastAlbumYm)="data">
          <div
              class="fld-lastAlbumYm"
              v-text="data.value"
              :style="{ color: currentAuthorId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>

        <template #cell(lastAlbumProcessed)="data">
          <div
              class="fld-lastAlbumProcessed"
              v-text="data.value"
              :style="{ color: currentAuthorId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>

        <template #cell(watched)="data">
          <div
              class="fld-watched"
              v-text="data.value"
              :style="{ color: currentAuthorId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>
        

      </b-table>
    </div>
    <div class="authors-bv-table-footer">
      <button class="btn-round-double" @click="isAuthorsFilterVisible=true" title="Фильтр">
        <img alt="filter" class="icon-40" src="../../assets/svg/icon_filter.svg">
      </button>
    </div>


  </div>
</template>

<script>

import Vue from "vue";
import { TablePlugin } from 'bootstrap-vue'
import { PaginationPlugin } from 'bootstrap-vue'
import { SpinnerPlugin } from 'bootstrap-vue'

// import AuthorEditModal from "@/components/Authors/edit/AuthorEditModal.vue";
import AuthorsFilter from "@/components/AuthorsFilter/AuthorsFilterModal.vue";
import CustomConfirm from "../Common/CustomConfirm.vue";
Vue.use(TablePlugin)
Vue.use(PaginationPlugin)
Vue.use(SpinnerPlugin)

export default {
  name: "AuthorsBvTable",
  components: {
    // AuthorEditModal,
    AuthorsFilter,
    CustomConfirm
  },
  data() {
    return {
      perPage: 50,
      currentPage: 1,
      isAuthorEditVisible: false,
      isAuthorsFilterVisible: false,
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
      isBusy: false,
      currentAuthorId: '',
      currentAuthor: undefined
    }
  },
  watch: {
    authorsDigestIsLoading: {
      handler () {
        this.isBusy = this.authorsDigestIsLoading;
      }
    }
  },
  mounted() {
    // this.$store.dispatch('loadAuthorsDigests', { filter_author: 'Павел Кашин'} )
  },
  computed: {
    authorsDigestIsLoading() {
      return this.$store.getters.getAuthorsDigestIsLoading;
    },
    authorsDigests() {
      return this.$store.getters.getAuthorsDigest;
    },
    countRows() {
      return this.authorsDigests.length;
    },
    authorDigestFields() {
      return [
        {
          key: 'id',
          label: 'ID',
          style: {
            minWidth: '50px',
            maxWidth: '50px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'author',
          label: 'Автор',
          style: {
            minWidth: '300px',
            maxWidth: '300px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'ymId',
          label: 'Yandex ID',
          style: {
            minWidth: '100px',
            maxWidth: '100px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'lastAlbumYm',
          label: 'Последний альбом (Yandex)',
          style: {
            minWidth: '300px',
            maxWidth: '300px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'lastAlbumProcessed',
          label: 'Последний альбом (DB)',
          style: {
            minWidth: '300px',
            maxWidth: '300px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'watched',
          label: 'Watch',
          style: {
            minWidth: '50px',
            maxWidth: '50px',
            textAlign: 'left',
            fontSize: 'small'
          }
        }
      ]
    }
  },
  methods: {

    openYandexMusicAuthor(item) {
      if (item.ymId) {
        const yandexMusicAuthorLink = 'https://music.yandex.ru/artist/' + item.ymId + '/albums';
        window.open(yandexMusicAuthorLink, '_blank');
      }
    },

    changeValue(item) {

      this.customConfirmParams = {
        header: 'Изменение Автора',
        body: `Автор ID = <strong>${item.id}</strong>`,
        callback: this.doChangeValue,
        fields: [
          {
            fldName: 'id',
            fldLabel: 'ID:',
            fldValue: item.id,
            disabled: true,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px'}
          },
          {
            fldName: 'author',
            fldLabel: 'Автор:',
            fldValue: item.author,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px'}
          },
          {
            fldName: 'ymId',
            fldLabel: 'Yandex ID:',
            fldValue: item.ymId,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px'}
          },
          {
            fldName: 'lastAlbumYm',
            fldLabel: 'Последний альбом (Yandex):',
            fldValue: item.lastAlbumYm,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px'}
          },
          {
            fldName: 'lastAlbumProcessed',
            fldLabel: 'Последний альбом (DB):',
            fldValue: item.lastAlbumProcessed,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px'}
          },
          {
            fldName: 'watched',
            fldLabel: 'Следить:',
            fldValue: item.watched,
            fldIsBoolean: true,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '300px', textAlign: 'center', borderRadius: '5px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },

    doChangeValue(author) {
      this.$store.dispatch('setAuthorValuePromise', author)
          .then(result => { // result - это целое число, возвращаемое промисом
            if (result !== 0) { // Проверяем, отлично ли оно от нуля
              this.$store.dispatch('loadOneRecord', result);
            }
          })
          .catch(error => {
            console.error("Ошибка при выполнении setAuthorValuePromise:", error);
          });
    },

    closeCustomConfirm() {
      this.isCustomConfirmVisible = false;
    },

    editAuthor(key) {
      this.$store.commit('setCurrentAuthorKey', key);
      this.isAuthorEditVisible = true;
    },
    closeAuthorEdit() {
      this.isAuthorEditVisible = false;
    },
    closeAuthorsFilter() {
      this.isAuthorsFilterVisible = false;
    },
    onRowClicked(item, index) {
      this.currentAuthor = item;
      this.currentAuthorId = item.id;
      console.log(`Row '${index}' clicked: `, item.id);
    },
    getCellStyle(data) {
      return {
        backgroundColor: data.item.color
      }
    }
  }
}
</script>

<style>

.authors-bv-table {
  padding: 0;
  margin: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  font-family: Avenir, Helvetica, Arial, sans-serif;
}

.authors-bv-table-header {
  width: fit-content;
}

.authors-bv-table-body {
  width: fit-content;
}

.authors-bv-table-footer {
  margin-top: auto;
  display: flex;
  flex-direction: row;
  align-items: center;
}

.fld-author-id {
  min-width: 50px;
  max-width: 50px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-author-id:hover {
  text-decoration: underline;
  cursor: pointer;
}
.fld-author {
  min-width: 300px;
  max-width: 300px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-author:hover {
  text-decoration: underline;
  cursor: pointer;
}

.fld-ymId {
  min-width: 100px;
  max-width: 100px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-ymId:hover {
  text-decoration: underline;
  cursor: pointer;
}

.fld-lastAlbumYm {
  min-width: 300px;
  max-width: 300px;
  text-align: left;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-lastAlbumProcessed {
  min-width: 300px;
  max-width: 300px;
  text-align: left;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-watched {
  min-width: 50px;
  max-width: 50px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
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
.icon-40 {
  width: 40px;
  height: 40px;
}

</style>