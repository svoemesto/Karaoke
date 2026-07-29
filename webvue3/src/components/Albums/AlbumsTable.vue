<template>
  <div class="albums-bv-table">
    <custom-confirm
      v-if="isCustomConfirmVisible"
      :params="customConfirmParams"
      @close="closeCustomConfirm"
    />
    <AlbumsFilter v-if="isAlbumsFilterVisible" @close="closeAlbumsFilter" />
    <PictureEditModal v-if="isPictureEditVisible" @close="closePictureEdit" />
    <AlbumCoverModal
      v-if="isAlbumCoverModalVisible"
      @saved="onAlbumCoverSaved"
      @close="closeAlbumCoverModal"
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

        <template #cell(authorPicture)="data">
          <div
            class="fld-picture-preview"
            :title="data.item.authorName"
            @click.left="editPicture(data.item.authorPictureId)"
          >
            <img
              v-if="data.item.authorPicturePreviewUrl"
              :src="data.item.authorPicturePreviewUrl"
              alt="Author preview"
              class="preview-image"
            />
            <div v-else class="no-image-placeholder">Нет изображения</div>
          </div>
        </template>

        <template #cell(albumPicture)="data">
          <div
            class="fld-picture-preview"
            :class="{ 'is-clickable': canEditCover(data.item) }"
            :title="
              canEditCover(data.item) ? data.item.name : 'У альбома нет песен — обложка недоступна'
            "
            @click.left="canEditCover(data.item) && openAlbumCoverModal(data.item)"
          >
            <img
              v-if="data.item.albumPicturePreviewUrl"
              :src="data.item.albumPicturePreviewUrl"
              alt="Album preview"
              class="preview-image"
            />
            <div v-else class="no-image-placeholder">Нет изображения</div>
          </div>
        </template>

        <template #cell(id)="data">
          <div class="fld-album-id" @click.left="changeValue(data.item)" v-text="data.value" />
        </template>

        <template #cell(authorId)="data">
          <div
            class="fld-album-author"
            @click.left="changeValue(data.item)"
            v-text="data.item.authorName || `#${data.value}`"
          />
        </template>

        <template #cell(year)="data">
          <div class="fld-album-year" @click.left="changeValue(data.item)" v-text="data.value" />
        </template>

        <template #cell(name)="data">
          <div
            class="fld-album-name"
            :class="{ 'is-clickable': canEditCover(data.item) }"
            :title="
              canEditCover(data.item)
                ? 'Изменить обложку альбома'
                : 'У альбома нет песен — обложка недоступна'
            "
            @click.left="
              canEditCover(data.item) ? openAlbumCoverModal(data.item) : changeValue(data.item)
            "
            v-text="data.value"
          />
        </template>

        <template #cell(albumType)="data">
          <select
            class="form-select form-select-sm fld-album-type-select"
            :value="data.value"
            :title="albumTypeLabel(data.value)"
            @change="onAlbumTypeChange(data.item, $event)"
          >
            <option
              v-for="opt in albumTypeOptions"
              :key="opt.value"
              :value="opt.value"
              v-text="opt.label"
            />
          </select>
        </template>

        <template #cell(songsCount)="data">
          <div class="fld-album-songs-count" v-text="data.value" />
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
      <div v-if="showEmptyHint" class="albums-empty-hint">
        Список пуст. Нажмите кнопку
        <img alt="filter" class="icon-20" src="../../assets/svg/icon_filter.svg" />
        и задайте фильтр, чтобы загрузить альбомы.
      </div>
    </div>
    <div class="albums-bv-table-footer">
      <button class="btn-round-double" title="Фильтр" @click="isAlbumsFilterVisible = true">
        <img alt="filter" class="icon-40" src="../../assets/svg/icon_filter.svg" />
      </button>
    </div>
  </div>
</template>

<script>
import { BPagination, BSpinner, BTable } from 'bootstrap-vue-next'
import CustomConfirm from '../Common/CustomConfirm.vue'
import AlbumsFilter from './filter/AlbumsFilterModal.vue'
import PictureEditModal from '../Pictures/edit/PictureEditModal.vue'
import AlbumCoverModal from '../Songs/edit/AlbumCoverModal.vue'

// Значения соответствуют AlbumType.dbValue (karaoke-app/model/AlbumType.kt) — не менять без
// синхронной правки бэкенда.
const ALBUM_TYPE_OPTIONS = ['studio', 'live', 'compilation', 'bootleg', 'single', 'archive']
const ALBUM_TYPE_LABELS = {
  studio: 'Студийный',
  live: 'Концертный',
  compilation: 'Сборник',
  bootleg: 'Бутлег',
  single: 'Сингл',
  archive: 'Архивный',
}

/**
 * Таблица со списком albums с пагинацией, фильтром, превью картинок автора/альбома,
 * inline-<select> для типа альбома и колонкой «Песен» (денормализованный счётчик из
 * AlbumDTO.songsCount). По образцу AuthorsTable.vue.
 *
 * Загрузка по фильтру: в `mounted()` НЕ дёргаем `loadAlbumsDigests` — таблица содержит 5k+
 * альбомов с превью картинок, без фильтра она заметно тормозит. Данные подгружаются только
 * по нажатию «Применить фильтр» в AlbumsFilterModal (см. ok() там) и при мутациях
 * create/delete (если таблица уже не пустая — обновляем, иначе оставляем пустой до явного
 * фильтра).
 *
 * @see specs/011-album-song-rename/contracts/api.md
 */
export default {
  name: 'AlbumsTable',
  components: {
    CustomConfirm,
    AlbumsFilter,
    PictureEditModal,
    AlbumCoverModal,
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
      isAlbumsFilterVisible: false,
      isPictureEditVisible: false,
      isAlbumCoverModalVisible: false,
      // prevCurrentSongId запоминается при открытии модалки AlbumCoverModal из AlbumsTable
      // и восстанавливается при её закрытии — чтобы не терять рабочий контекст администратора,
      // если он до этого был в /Songs. Подробнее — research.md Decision 2.
      prevCurrentSongId: null,
      // id альбома, для которого сейчас открыта модалка обложки (нужен для loadOneRecord
      // после @saved, чтобы обновить preview в строке таблицы).
      currentAlbumCoverAlbumId: null,
      customConfirmParams: undefined,
      isBusy: false,
      // Объединённый список опций типа альбома (value + label) для inline-<select>
      // в ячейке и для модалок create/change. Источник истины — AlbumType.dbValue на бэке
      // (karaoke-app/model/AlbumType.kt), см. комментарий к ALBUM_TYPE_OPTIONS ниже.
      albumTypeOptions: ALBUM_TYPE_OPTIONS.map((v) => ({ value: v, label: ALBUM_TYPE_LABELS[v] })),
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
    // Показывать подсказку «Задайте фильтр», если таблица пуста и загрузка не идёт.
    showEmptyHint() {
      return !this.albumsDigestIsLoading && this.countRows === 0
    },
    // Можно ли открыть модалку обложки альбома — true только если у альбома есть хотя бы
    // одна песня (songsCount > 0). Без песен у альбома нет rootFolder/LogoAlbum.png —
    // AlbumCoverModal работать не сможет (см. research.md Decision 1 + INV-1 в data-model.md).
    canEditCover() {
      return (item) => item && item.songsCount > 0
    },
    albumDigestFields() {
      return [
        {
          key: 'authorPicture',
          label: '(автор)',
          style: { minWidth: '125px', maxWidth: '125px', textAlign: 'center', fontSize: 'small' },
        },
        {
          key: 'albumPicture',
          label: '(альбом)',
          style: { minWidth: '125px', maxWidth: '125px', textAlign: 'center', fontSize: 'small' },
        },
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
          style: { minWidth: '140px', maxWidth: '140px', textAlign: 'left', fontSize: 'small' },
        },
        {
          key: 'songsCount',
          sortable: true,
          label: 'Песен',
          style: { minWidth: '60px', maxWidth: '60px', textAlign: 'center', fontSize: 'small' },
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
    // Намеренно НЕ грузим альбомы при входе — таблица содержит 5k+ записей с превью картинок,
    // загрузка занимает заметное время. Альбомы подгружаются ТОЛЬКО по фильтру (см. ok() в
    // AlbumsFilterModal и явные мутации — create/delete). Если таблица пустая, пользователь
    // увидит «Задайте фильтр» ниже. loadAuthorsDigests тоже не дёргаем: длинный список
    // подсказок в AlbumsFilterModal догружает себя сам в beforeMount при открытии.
  },
  methods: {
    albumTypeLabel(value) {
      return ALBUM_TYPE_LABELS[value] || value
    },
    fieldStyle() {
      return { width: '300px', textAlign: 'right', paddingRight: '5px' }
    },
    valueStyle() {
      return { width: '300px', textAlign: 'left', borderRadius: '5px' }
    },
    editPicture(id) {
      this.$store.commit('setPictureCurrentId', id)
      this.isPictureEditVisible = true
    },
    closePictureEdit() {
      this.isPictureEditVisible = false
    },
    closeAlbumsFilter() {
      this.isAlbumsFilterVisible = false
    },
    // Открывает модалку AlbumCoverModal для смены обложки альбома (та же модалка, что в
    // SongEdit.vue по кнопке «Изменить обложку альбома»). Перед открытием:
    //   1) запоминаем prevCurrentSongId (если SongsStore.currentSongId был установлен — мы
    //      могли прийти сюда из /Songs);
    //   2) узнаём id «репрезентативной» песни альбома через Albums/getFirstSongIdByAlbumIdPromise
    //      (см. research.md Decision 1, MIN(id) — единственная песня альбома подойдёт);
    //   3) дожидаемся полной загрузки SongsStore.currentSong через setCurrentSongId (async) —
    //      это тот же action, что использует SongEdit.vue: после него `getCurrentSong`
    //      возвращает полноценный объект песни с author/album, и AlbumCoverModal.defaultSearchQuery
    //      правильно подставляет «{author} {album} обложка альбома» в поле «Поисковый запрос».
    //      Без этого шага (если бы использовался setCurrentSongIdOnly) модалка в mounted()
    //      читала бы старую/null песню и поле было бы пустым.
    // На @close / @saved — closeAlbumCoverModal восстанавливает prevCurrentSongId (синхронно,
    // модалка уже не нуждается в currentSong).
    async openAlbumCoverModal(item) {
      if (!this.canEditCover(item)) return
      this.prevCurrentSongId = this.$store.getters.getCurrentSongId
      this.currentAlbumCoverAlbumId = item.id
      this.isBusy = true
      try {
        const firstSongId = await this.$store.dispatch('getFirstSongIdByAlbumIdPromise', item.id)
        if (!firstSongId) {
          // Бэк вернул 0 — песен нет (race condition с UI-блокировкой songsCount === 0)
          console.warn('Альбом без песен, модалка не открыта:', item.id)
          this.currentAlbumCoverAlbumId = null
          return
        }
        // Дожидаемся полной загрузки currentSong (тот же action, что в SongEdit.vue).
        // После этого defaultSearchQuery в модалке подставит корректную строку.
        await this.$store.dispatch('setCurrentSongId', firstSongId)
        this.isAlbumCoverModalVisible = true
      } catch (e) {
        console.error('Ошибка при открытии модалки обложки альбома', item.id, e)
        this.currentAlbumCoverAlbumId = null
      } finally {
        this.isBusy = false
      }
    },
    closeAlbumCoverModal() {
      this.isAlbumCoverModalVisible = false
      // Восстанавливаем прежнее значение currentSongId (либо null, если до клика не было).
      // Подробнее — research.md Decision 2 + US3/SC-004 в spec.md.
      this.$store.commit('setCurrentSongIdOnly', this.prevCurrentSongId || null)
      this.prevCurrentSongId = null
    },
    onAlbumCoverSaved() {
      // Модалка эмитнула saved → close (внутри себя) → наш @close уже сработал
      // и currentSongId восстановлен. Здесь только обновляем превью в строке таблицы.
      const albumId = this.currentAlbumCoverAlbumId
      this.currentAlbumCoverAlbumId = null
      if (albumId) {
        this.$store.dispatch('loadOneRecord', albumId)
      }
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
          {
            fldName: 'shortDescription',
            fldLabel: 'Короткое описание:',
            fldValue: item.shortDescription || '',
            fldLabelStyle: this.fieldStyle(),
            fldValueStyle: this.valueStyle(),
          },
          {
            fldName: 'warning',
            fldLabel: 'Предупреждение:',
            fldValue: item.warning || '',
            fldLabelStyle: this.fieldStyle(),
            fldValueStyle: this.valueStyle(),
          },
          {
            fldName: 'description',
            fldLabel: 'Описание:',
            fldValue: item.description || '',
            fldIsTextarea: true,
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
          {
            fldName: 'shortDescription',
            fldLabel: 'Короткое описание:',
            fldValue: '',
            fldLabelStyle: this.fieldStyle(),
            fldValueStyle: this.valueStyle(),
          },
          {
            fldName: 'warning',
            fldLabel: 'Предупреждение:',
            fldValue: '',
            fldLabelStyle: this.fieldStyle(),
            fldValueStyle: this.valueStyle(),
          },
          {
            fldName: 'description',
            fldLabel: 'Описание:',
            fldValue: '',
            fldIsTextarea: true,
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
    // Inline-смена типа альбома прямо в строке таблицы: шлёт минимальный payload
    // (id + только изменённое поле albumType) и патчит локальный digest без лишнего GET.
    // Бэкенд (apisUpdateAlbum) требует authorId/year/name/sortOrder — берём их из текущей
    // строки, чтобы не открывать модалку.
    onAlbumTypeChange(item, event) {
      const newAlbumType = event.target.value
      if (newAlbumType === item.albumType) return
      const payload = {
        id: item.id,
        authorId: item.authorId,
        year: item.year,
        name: item.name,
        albumType: newAlbumType,
        sortOrder: item.sortOrder,
      }
      this.$store
        .dispatch('setAlbumValuePromise', payload)
        .then((result) => {
          if (result !== 0 && result !== '0') {
            this.$store.commit('updateAlbumsDigests', [{ ...item, albumType: newAlbumType }])
          } else {
            // Бэк вернул 0 — альбом не найден, откатываем UI к прежнему значению.
            event.target.value = item.albumType
          }
        })
        .catch((error) => {
          console.error('Ошибка при смене типа альбома:', error)
          event.target.value = item.albumType
        })
    },
    doCreateAlbum(album) {
      this.$store
        .dispatch('createAlbumPromise', album)
        .then(() => {
          // Не дёргаем сервер без фильтра — см. mounted(). Если таблица уже загружена
          // (фильтр применён), обновим, чтобы новый альбом попал в видимое подмножество.
          if (this.countRows > 0) {
            this.$store.dispatch('loadAlbumsDigests', {})
          }
        })
        .catch((error) => {
          console.error('Ошибка при выполнении createAlbumPromise:', error)
        })
    },
    deleteAlbum(item) {
      this.$store
        .dispatch('deleteAlbumPromise', item.id)
        .then(() => {
          if (this.countRows > 0) {
            this.$store.dispatch('loadAlbumsDigests', {})
          }
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
.albums-bv-table-footer {
  margin-top: auto;
  display: flex;
  flex-direction: row;
  align-items: center;
}
.fld-album-id,
.fld-album-author,
.fld-album-year,
.fld-album-name,
.fld-album-type,
.fld-album-songs-count,
.fld-album-sort-order {
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-album-type-select {
  font-size: small;
  padding: 1px 4px;
  width: 100%;
  min-width: 130px;
}
.fld-album-songs-count {
  text-align: center;
  color: #444;
  font-weight: 500;
}
.albums-empty-hint {
  margin-top: 12px;
  padding: 8px 14px;
  font-size: small;
  color: #555;
  background-color: lightyellow;
  border: 1px dashed #c0a060;
  border-radius: 6px;
}
.albums-empty-hint .icon-20 {
  width: 18px;
  height: 18px;
  vertical-align: middle;
  margin: 0 2px;
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
/* Кликабельные ячейки открытия обложки альбома (preview `(альбом)` и `Название`) — см.
   specs/014-album-cell-album-cover-modal. Класс .is-clickable добавляется computed'ом
   canEditCover только если songsCount > 0; без песен — клик заблокирован (INV-1). */
.fld-picture-preview.is-clickable,
.fld-album-name.is-clickable {
  cursor: pointer;
}
.fld-picture-preview.is-clickable:hover {
  background-color: #2a2a2a;
}
.fld-album-name.is-clickable:hover {
  text-decoration: underline;
}
.fld-picture-preview {
  min-width: 50px;
  max-width: 125px;
  text-align: center;
  font-size: small;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 54px;
  overflow: hidden;
  background-color: black;
}
.fld-picture-preview:hover {
  cursor: pointer;
}
.preview-image {
  width: auto;
  height: 50px;
  object-fit: contain;
  vertical-align: middle;
}
.no-image-placeholder {
  font-size: 0.7em;
  color: white;
  text-align: center;
  padding: 5px;
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
.icon-40 {
  width: 40px;
  height: 40px;
}
</style>
