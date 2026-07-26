<template>
  <div class="albums-bv-table">
    <custom-confirm
      v-if="isCustomConfirmVisible"
      :params="customConfirmParams"
      @close="closeCustomConfirm"
    />
    <div class="albums-bv-table-header">
      <b-pagination
        v-model="currentPage"
        :total-rows="countRows"
        :per-page="perPage"
        :limit="30"
        size="sm"
        pills
      />
      <button class="btn-round-double" title="Создать альбом" @click="createAlbum">+</button>
    </div>
    <div class="albums-bv-table-body">
      <b-table
        v-model:sort-by="sortBy"
        :items="albumsDigests"
        :busy="isBusy"
        :fields="albumDigestFields"
        :per-page="perPage"
        :current-page="currentPage"
        small
        bordered
        hover
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

        <template #cell(id)="data">
          <div class="fld-album-id" @click.left="changeValue(data.item)" v-text="data.value" />
        </template>

        <template #cell(authorId)="data">
          <div
            class="fld-album-author"
            @click.left="changeValue(data.item)"
            v-text="authorName(data.value)"
          />
        </template>

        <template #cell(year)="data">
          <div class="fld-album-year" @click.left="changeValue(data.item)" v-text="data.value" />
        </template>

        <template #cell(name)="data">
          <div class="fld-album-name" @click.left="changeValue(data.item)" v-text="data.value" />
        </template>

        <template #cell(albumType)="data">
          <div
            class="fld-album-type"
            @click.left="changeValue(data.item)"
            v-text="albumTypeLabel(data.value)"
          />
        </template>

        <template #cell(sortOrder)="data">
          <div
            class="fld-album-sort-order"
            @click.left="changeValue(data.item)"
            v-text="data.value"
          />
        </template>

        <template #cell(actions)="data">
          <button class="btn-round-double-small" title="Удалить" @click="deleteAlbum(data.item)">
            ×
          </button>
        </template>
      </b-table>
    </div>
  </div>
</template>

<script>
import { BPagination, BSpinner, BTable } from 'bootstrap-vue-next'
import CustomConfirm from '../Common/CustomConfirm.vue'

// Значения соответствуют AlbumType.dbValue (karaoke-app/model/AlbumType.kt) — не менять без
// синхронной правки бэкенда.
const ALBUM_TYPE_OPTIONS = ['studio', 'live', 'compilation', 'bootleg']
const ALBUM_TYPE_LABELS = {
  studio: 'Студийный',
  live: 'Концертный',
  compilation: 'Сборник',
  bootleg: 'Бутлег',
}

/**
 * Таблица со списком albums с пагинацией, сортировкой и inline-редактированием — по образцу
 * AuthorsTable.vue.
 *
 * @see specs/011-album-song-rename/contracts/api.md
 */
export default {
  name: 'AlbumsTable',
  components: {
    CustomConfirm,
    BPagination,
    BSpinner,
    BTable,
  },
  data() {
    return {
      perPage: 30,
      currentPage: this.$store.getters.getAlbumsTableCurrentPage || 1,
      sortBy: [],
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
      isBusy: false,
    }
  },
  computed: {
    albumsDigestIsLoading() {
      return this.$store.getters.getAlbumsDigestIsLoading
    },
    albumsDigests() {
      return this.$store.getters.getAlbumsDigest
    },
    authorsDigests() {
      return this.$store.getters.getAuthorsDigest
    },
    countRows() {
      return this.albumsDigests ? this.albumsDigests.length : 0
    },
    albumDigestFields() {
      return [
        {
          key: 'id',
          sortable: true,
          label: 'ID',
          style: { minWidth: '50px', maxWidth: '50px', textAlign: 'center', fontSize: 'small' },
        },
        {
          key: 'authorId',
          sortable: true,
          label: 'Автор',
          style: { minWidth: '250px', maxWidth: '250px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'year',
          sortable: true,
          label: 'Год',
          style: { minWidth: '70px', maxWidth: '70px', textAlign: 'center', fontSize: 'small' },
        },
        {
          key: 'name',
          sortable: true,
          label: 'Название',
          style: { minWidth: '300px', maxWidth: '300px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'albumType',
          sortable: true,
          label: 'Тип',
          style: { minWidth: '120px', maxWidth: '120px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'sortOrder',
          sortable: true,
          label: 'Порядок',
          style: { minWidth: '80px', maxWidth: '80px', textAlign: 'center', fontSize: 'small' },
        },
        {
          key: 'actions',
          label: '',
          style: { minWidth: '40px', maxWidth: '40px', textAlign: 'center', fontSize: 'small' },
        },
      ]
    },
  },
  watch: {
    albumsDigestIsLoading: {
      handler() {
        this.isBusy = this.albumsDigestIsLoading
      },
    },
    currentPage: {
      handler(newPage) {
        this.$store.commit('setAlbumsTableCurrentPage', newPage)
      },
    },
  },
  mounted() {
    this.$store.dispatch('loadAlbumsDigests', {})
    if (!this.authorsDigests || this.authorsDigests.length === 0) {
      this.$store.dispatch('loadAuthorsDigests', {})
    }
  },
  methods: {
    authorName(authorId) {
      const author = (this.authorsDigests || []).find((a) => a.id === authorId)
      return author ? author.author : `#${authorId}`
    },
    albumTypeLabel(value) {
      return ALBUM_TYPE_LABELS[value] || value
    },
    fieldStyle() {
      return { width: '300px', textAlign: 'right', paddingRight: '5px' }
    },
    valueStyle() {
      return { width: '300px', textAlign: 'left', borderRadius: '5px' }
    },
    changeValue(item) {
      this.customConfirmParams = {
        header: 'Изменение альбома',
        body: `Альбом ID = <strong>${item.id}</strong>`,
        callback: this.doChangeValue,
        fields: [
          {
            fldName: 'id',
            fldLabel: 'ID:',
            fldValue: item.id,
            disabled: true,
            fldLabelStyle: this.fieldStyle(),
            fldValueStyle: this.valueStyle(),
          },
          {
            fldName: 'authorId',
            fldLabel: 'ID автора:',
            fldValue: item.authorId,
            fldLabelStyle: this.fieldStyle(),
            fldValueStyle: this.valueStyle(),
          },
          {
            fldName: 'year',
            fldLabel: 'Год:',
            fldValue: item.year,
            fldLabelStyle: this.fieldStyle(),
            fldValueStyle: this.valueStyle(),
          },
          {
            fldName: 'name',
            fldLabel: 'Название:',
            fldValue: item.name,
            fldLabelStyle: this.fieldStyle(),
            fldValueStyle: this.valueStyle(),
          },
          {
            fldName: 'albumType',
            fldLabel: 'Тип:',
            fldValue: item.albumType,
            fldIsSelect: true,
            fldOptions: ALBUM_TYPE_OPTIONS,
            fldLabelStyle: this.fieldStyle(),
            fldValueStyle: this.valueStyle(),
          },
          {
            fldName: 'sortOrder',
            fldLabel: 'Порядок отображения:',
            fldValue: item.sortOrder,
            fldLabelStyle: this.fieldStyle(),
            fldValueStyle: this.valueStyle(),
          },
        ],
      }
      this.isCustomConfirmVisible = true
    },
    createAlbum() {
      this.customConfirmParams = {
        header: 'Новый альбом',
        body: '',
        callback: this.doCreateAlbum,
        fields: [
          {
            fldName: 'authorId',
            fldLabel: 'ID автора:',
            fldValue: '',
            fldLabelStyle: this.fieldStyle(),
            fldValueStyle: this.valueStyle(),
          },
          {
            fldName: 'year',
            fldLabel: 'Год:',
            fldValue: new Date().getFullYear(),
            fldLabelStyle: this.fieldStyle(),
            fldValueStyle: this.valueStyle(),
          },
          {
            fldName: 'name',
            fldLabel: 'Название:',
            fldValue: '',
            fldLabelStyle: this.fieldStyle(),
            fldValueStyle: this.valueStyle(),
          },
          {
            fldName: 'albumType',
            fldLabel: 'Тип:',
            fldValue: 'studio',
            fldIsSelect: true,
            fldOptions: ALBUM_TYPE_OPTIONS,
            fldLabelStyle: this.fieldStyle(),
            fldValueStyle: this.valueStyle(),
          },
        ],
      }
      this.isCustomConfirmVisible = true
    },
    doChangeValue(album) {
      this.$store
        .dispatch('setAlbumValuePromise', album)
        .then((result) => {
          if (result !== 0) {
            this.$store.dispatch('loadOneRecord', result)
          }
        })
        .catch((error) => {
          console.error('Ошибка при выполнении setAlbumValuePromise:', error)
        })
    },
    doCreateAlbum(album) {
      this.$store
        .dispatch('createAlbumPromise', album)
        .then(() => {
          this.$store.dispatch('loadAlbumsDigests', {})
        })
        .catch((error) => {
          console.error('Ошибка при выполнении createAlbumPromise:', error)
        })
    },
    deleteAlbum(item) {
      this.$store
        .dispatch('deleteAlbumPromise', item.id)
        .then(() => {
          this.$store.dispatch('loadAlbumsDigests', {})
        })
        .catch((error) => {
          console.error('Ошибка при выполнении deleteAlbumPromise:', error)
        })
    },
    closeCustomConfirm() {
      this.isCustomConfirmVisible = false
    },
  },
}
</script>

<style scoped>
.albums-bv-table {
  padding: 0;
  margin: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  font-family: Avenir, Helvetica, Arial, sans-serif;
}
.albums-bv-table-header {
  width: fit-content;
  display: flex;
  align-items: center;
  gap: 10px;
}
.albums-bv-table-body {
  width: fit-content;
}
.fld-album-id,
.fld-album-author,
.fld-album-year,
.fld-album-name,
.fld-album-type,
.fld-album-sort-order {
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-album-id:hover,
.fld-album-author:hover,
.fld-album-year:hover,
.fld-album-name:hover,
.fld-album-type:hover,
.fld-album-sort-order:hover {
  text-decoration: underline;
  cursor: pointer;
}
.btn-round-double {
  border: solid 1px black;
  border-radius: 6px;
  width: 40px;
  height: 40px;
  background-color: antiquewhite;
}
.btn-round-double:hover {
  background-color: lightpink;
}
.btn-round-double-small {
  border: solid 1px black;
  border-radius: 4px;
  width: 24px;
  height: 24px;
  background-color: antiquewhite;
}
.btn-round-double-small:hover {
  background-color: lightpink;
}
</style>
