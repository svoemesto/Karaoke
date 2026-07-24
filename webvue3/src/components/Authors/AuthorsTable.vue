<template>
  <div class="authors-bv-table">
    <PictureEditModal v-if="isPictureEditVisible" @close="closePictureEdit" />
    <AuthorsFilter v-if="isAuthorsFilterVisible" @close="closeAuthorsFilter" />
    <custom-confirm
      v-if="isCustomConfirmVisible"
      :params="customConfirmParams"
      @close="closeCustomConfirm"
    />
    <AuthorAliasesModal
      v-if="isAuthorAliasesVisible"
      :author-id="aliasesAuthorId"
      @close="closeAuthorAliases"
    />
    <div class="authors-bv-table-header">
      <b-pagination
        v-model="currentPage"
        :total-rows="countRows"
        :per-page="perPage"
        :limit="30"
        size="sm"
        pills
      />
    </div>
    <div class="authors-bv-table-body">
      <b-table
        v-model:sort-by="sortBy"
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
            <b-spinner class="align-middle" />
            <strong>Loading...</strong>
          </div>
        </template>
        <template #table-colgroup="scope">
          <col v-for="field in scope.fields" :key="field.key" :style="field.style" />
        </template>

        <template #cell(picturePreview)="data">
          <div
            class="fld-picture-preview"
            :style="{ color: currentAuthorId === data.item.id ? 'blue' : 'black' }"
            @click.left="editPicture(data.item.pictureId)"
          >
            <img
              v-if="data.item.picturePreviewUrl"
              :src="data.item.picturePreviewUrl"
              alt="Preview"
              class="preview-image"
            />
            <div v-else class="no-image-placeholder">Нет изображения</div>
          </div>
        </template>

        <template #cell(id)="data">
          <div
            class="fld-author-id"
            :style="{ color: currentAuthorId === data.item.id ? 'blue' : 'black' }"
            @click.left="changeValue(data.item)"
            v-text="data.value"
          />
        </template>

        <template #cell(author)="data">
          <div
            class="fld-author"
            :style="{ color: currentAuthorId === data.item.id ? 'blue' : 'black' }"
            @click.left="changeValue(data.item)"
            v-text="data.value"
          />
        </template>

        <template #cell(ymId)="data">
          <div
            class="fld-ymId"
            :style="{ color: currentAuthorId === data.item.id ? 'blue' : 'black' }"
            @click.left="openYandexMusicAuthor(data.item)"
            v-text="data.value"
          />
        </template>

        <template #cell(lastAlbumYm)="data">
          <div
            class="fld-lastAlbumYm"
            :style="{ color: currentAuthorId === data.item.id ? 'blue' : 'black' }"
            v-text="data.value"
          />
        </template>

        <template #cell(vkId)="data">
          <div
            class="fld-vkId"
            :style="{ color: currentAuthorId === data.item.id ? 'blue' : 'black' }"
            @click.left="openVKMusicAuthor(data.item)"
            v-text="data.value"
          />
        </template>

        <template #cell(lastAlbumVk)="data">
          <div
            class="fld-lastAlbumVk"
            :style="{ color: currentAuthorId === data.item.id ? 'blue' : 'black' }"
            v-text="data.value"
          />
        </template>

        <template #cell(lastAlbumProcessed)="data">
          <div
            class="fld-lastAlbumProcessed"
            :style="{ color: currentAuthorId === data.item.id ? 'blue' : 'black' }"
            v-text="data.value"
          />
        </template>

        <template #cell(watched)="data">
          <div
            class="fld-watched"
            :style="{ color: currentAuthorId === data.item.id ? 'blue' : 'black' }"
            v-text="data.value"
          />
        </template>

        <template #cell(skip)="data">
          <div
            class="fld-skip"
            :style="{ color: currentAuthorId === data.item.id ? 'blue' : 'black' }"
            v-text="data.value"
          />
        </template>

        <template #cell(haveNewAlbum)="data">
          <div
            class="fld-haveNewAlbum"
            :style="{ color: currentAuthorId === data.item.id ? 'blue' : 'black' }"
            v-text="data.value"
          />
        </template>

        <template #cell(isSpecialOrder)="data">
          <div
            class="fld-isSpecialOrder"
            :style="{ color: currentAuthorId === data.item.id ? 'blue' : 'black' }"
            v-text="data.value ? '🪙' : ''"
          />
        </template>

        <template #cell(aliases)="data">
          <div
            class="fld-aliases"
            @click.left="editAliases(data.item)"
            v-text="aliasesSummary(data.value)"
          />
        </template>
      </b-table>
    </div>
    <div class="authors-bv-table-footer">
      <button class="btn-round-double" title="Фильтр" @click="isAuthorsFilterVisible = true">
        <img alt="filter" class="icon-40" src="../../assets/svg/icon_filter.svg" />
      </button>
    </div>
  </div>
</template>

<script>
import { BPagination, BSpinner, BTable } from 'bootstrap-vue-next'
import AuthorsFilter from '../../components/Authors/filter/AuthorsFilterModal.vue'
import CustomConfirm from '../Common/CustomConfirm.vue'
import PictureEditModal from '../../components/Pictures/edit/PictureEditModal.vue'
import AuthorAliasesModal from './AuthorAliasesModal.vue'

/**
 * Таблица со списком authors с пагинацией, фильтрами и сортировкой.
 *
 * @see docs/features/dual-db-sync.md
 */

export default {
  name: 'AuthorsTable',
  components: {
    AuthorsFilter,
    PictureEditModal,
    AuthorAliasesModal,
    CustomConfirm,
    BPagination,
    BSpinner,
    BTable,
  },
  data() {
    return {
      perPage: 30,
      // Восстанавливаем последнюю страницу из store, чтобы при уходе с компонента и возврате таблица
      // открывалась на той же странице.
      currentPage: this.$store.getters.getAuthorsTableCurrentPage || 1,
      sortBy: [],
      isAuthorEditVisible: false,
      isPictureEditVisible: false,
      isAuthorsFilterVisible: false,
      isCustomConfirmVisible: false,
      isAuthorAliasesVisible: false,
      aliasesAuthorId: null,
      customConfirmParams: undefined,
      isBusy: false,
      currentAuthorId: '',
      currentAuthor: undefined,
    }
  },
  computed: {
    authorsDigestIsLoading() {
      return this.$store.getters.getAuthorsDigestIsLoading
    },
    authorsDigests() {
      return this.$store.getters.getAuthorsDigest
    },
    countRows() {
      return this.authorsDigests ? this.authorsDigests.length : 0
    },
    authorDigestFields() {
      return [
        {
          key: 'picturePreview',
          label: '(picture)',
          style: {
            minWidth: '125px',
            maxWidth: '125px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'id',
          sortable: true,
          label: 'ID',
          style: {
            minWidth: '50px',
            maxWidth: '50px',
            textAlign: 'center',
            fontSize: 'small',
          },
        },
        {
          key: 'author',
          sortable: true,
          label: 'Автор',
          style: {
            minWidth: '300px',
            maxWidth: '300px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'ymId',
          sortable: true,
          label: 'Yandex ID',
          style: {
            minWidth: '100px',
            maxWidth: '100px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'lastAlbumYm',
          sortable: true,
          label: 'Последний альбом (Yandex)',
          style: {
            minWidth: '300px',
            maxWidth: '300px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'vkId',
          sortable: true,
          label: 'VK ID',
          style: {
            minWidth: '100px',
            maxWidth: '100px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'lastAlbumVk',
          sortable: true,
          label: 'Последний альбом (VK)',
          style: {
            minWidth: '300px',
            maxWidth: '300px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'lastAlbumProcessed',
          sortable: true,
          label: 'Последний альбом (DB)',
          style: {
            minWidth: '300px',
            maxWidth: '300px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'watched',
          sortable: true,
          label: 'Watch',
          style: {
            minWidth: '50px',
            maxWidth: '50px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'skip',
          sortable: true,
          label: 'Skip',
          style: {
            minWidth: '50px',
            maxWidth: '50px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'haveNewAlbum',
          sortable: true,
          label: 'New Album',
          style: {
            minWidth: '50px',
            maxWidth: '50px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'isSpecialOrder',
          sortable: true,
          label: 'Спец',
          style: {
            minWidth: '50px',
            maxWidth: '50px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'aliases',
          sortable: true,
          label: 'Алиасы',
          style: {
            minWidth: '250px',
            maxWidth: '250px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
      ]
    },
  },
  watch: {
    authorsDigestIsLoading: {
      handler() {
        this.isBusy = this.authorsDigestIsLoading
      },
    },
    currentPage: {
      handler(newPage) {
        // Сохраняем страницу в store, чтобы она восстановилась после переключения на другой компонент.
        this.$store.commit('setAuthorsTableCurrentPage', newPage)
      },
    },
  },
  mounted() {
    // this.$store.dispatch('loadAuthorsDigests', { filterAuthor: 'Павел Кашин'} )
  },
  methods: {
    editPicture(id) {
      this.$store.commit('setPictureCurrentId', id)
      this.isPictureEditVisible = true
    },

    openYandexMusicAuthor(item) {
      if (item.ymId) {
        const yandexMusicAuthorLink = 'https://music.yandex.ru/artist/' + item.ymId + '/albums'
        window.open(yandexMusicAuthorLink, '_blank')
      }
    },

    openVKMusicAuthor(item) {
      if (item.ymId) {
        const vkMusicAuthorLink = 'https://vk.ru/artist/' + item.vkId + '/releases'
        window.open(vkMusicAuthorLink, '_blank')
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
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px' },
          },
          {
            fldName: 'author',
            fldLabel: 'Автор:',
            fldValue: item.author,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px' },
          },
          {
            fldName: 'ymId',
            fldLabel: 'Yandex ID:',
            fldValue: item.ymId,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px' },
          },
          {
            fldName: 'lastAlbumYm',
            fldLabel: 'Последний альбом (Yandex):',
            fldValue: item.lastAlbumYm,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px' },
          },
          {
            fldName: 'vkId',
            fldLabel: 'VK ID:',
            fldValue: item.vkId,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px' },
          },
          {
            fldName: 'lastAlbumVk',
            fldLabel: 'Последний альбом (VK):',
            fldValue: item.lastAlbumVk,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px' },
          },
          {
            fldName: 'lastAlbumProcessed',
            fldLabel: 'Последний альбом (DB):',
            fldValue: item.lastAlbumProcessed,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px' },
          },
          {
            fldName: 'watched',
            fldLabel: 'Следить?:',
            fldValue: item.watched,
            fldIsBoolean: true,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '300px', textAlign: 'center', borderRadius: '5px' },
          },
          {
            fldName: 'skip',
            fldLabel: 'Пропустить?:',
            fldValue: item.skip,
            fldIsBoolean: true,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '300px', textAlign: 'center', borderRadius: '5px' },
          },
          {
            fldName: 'haveNewAlbum',
            fldLabel: 'Новый альбом?:',
            fldValue: item.watched,
            fldIsBoolean: true,
            disabled: true,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '300px', textAlign: 'center', borderRadius: '5px' },
          },
          {
            fldName: 'isSpecialOrder',
            fldLabel: 'По спецзаказу?:',
            fldValue: item.isSpecialOrder || false,
            fldIsBoolean: true,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '300px', textAlign: 'center', borderRadius: '5px' },
          },
        ],
      }
      this.isCustomConfirmVisible = true
    },

    doChangeValue(author) {
      this.$store
        .dispatch('setAuthorValuePromise', author)
        .then((result) => {
          // result - это целое число, возвращаемое промисом
          if (result !== 0) {
            // Проверяем, отлично ли оно от нуля
            this.$store.dispatch('loadOneRecord', result)
          }
        })
        .catch((error) => {
          console.error('Ошибка при выполнении setAuthorValuePromise:', error)
        })
    },

    closeCustomConfirm() {
      this.isCustomConfirmVisible = false
    },

    aliasesSummary(aliases) {
      if (!aliases) return '—'
      return (
        aliases
          .split(';')
          .map((a) => a.trim())
          .filter((a) => a.length > 0)
          .join(', ') || '—'
      )
    },

    editAliases(item) {
      this.aliasesAuthorId = item.id
      this.isAuthorAliasesVisible = true
    },

    closeAuthorAliases() {
      this.isAuthorAliasesVisible = false
    },

    editAuthor(key) {
      this.$store.commit('setCurrentAuthorKey', key)
      this.isAuthorEditVisible = true
    },
    closeAuthorEdit() {
      this.isAuthorEditVisible = false
    },
    closeAuthorsFilter() {
      this.isAuthorsFilterVisible = false
    },
    closePictureEdit() {
      this.isPictureEditVisible = false
    },
    onRowClicked(item, index) {
      this.currentAuthor = item
      this.currentAuthorId = item.id
      console.log(`Row '${index}' clicked: `, item.id)
    },
    getCellStyle(data) {
      return {
        backgroundColor: data.item.color,
      }
    },
  },
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
.authors-bv-table-body th {
  position: relative;
}
.authors-bv-table-body th svg.bi {
  position: absolute;
  right: 2px;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0 !important;
  transition: opacity 0.15s ease;
  pointer-events: none;
}
.authors-bv-table-body th:hover svg.bi {
  opacity: 0.6 !important;
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

.fld-vkId {
  min-width: 100px;
  max-width: 100px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-vkId:hover {
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

.fld-lastAlbumVk {
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

.fld-skip {
  min-width: 50px;
  max-width: 50px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-haveNewAlbum {
  min-width: 50px;
  max-width: 50px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-aliases {
  min-width: 250px;
  max-width: 250px;
  text-align: left;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.fld-aliases:hover {
  text-decoration: underline;
  cursor: pointer;
}

.fld-picture-preview {
  min-width: 50px;
  max-width: 125px;
  text-align: center;
  font-size: small;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 54px; /* Примерная высота под изображение + отступы */
  overflow: hidden;
  background-color: black;
}
.fld-picture-preview:hover {
  cursor: pointer;
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
