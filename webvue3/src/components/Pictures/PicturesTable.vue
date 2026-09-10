<template>
  <div class="pictures-bv-table">
    <PictureEditModal v-if="isPictureEditVisible" @close="closePictureEdit" />
    <PicturesFilter v-if="isPicturesFilterVisible" @close="closePicturesFilter" />
    <custom-confirm
      v-if="isCustomConfirmVisible"
      :params="customConfirmParams"
      @close="closeCustomConfirm"
    />
    <div class="pictures-bv-table-header">
      <b-form-input
        :id="`rows-per-page-pictures`"
        type="number"
        min="1"
        max="1000"
        size="sm"
        style="width: 65px"
        :model-value="perPage"
        :disabled="isSavingRowsPerPage"
        @change="onPerPageChange($event)"
      />
      <b-spinner v-if="isSavingRowsPerPage" small />
      <b-pagination
        v-model="currentPage"
        :total-rows="countRows"
        :per-page="perPage"
        :limit="30"
        size="sm"
        pills
      />
    </div>
    <div class="pictures-bv-table-body">
      <b-table
        v-model:sort-by="sortBy"
        :items="picturesDigests"
        :busy="isBusy"
        :fields="pictureDigestFields"
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

        <template #cell(preview)="data">
          <div
            class="fld-picture-preview"
            :style="{ color: currentPictureId === data.item.id ? 'blue' : 'black' }"
            @click.left="editPicture(data.item.id)"
          >
            <img
              v-if="data.item.previewUrl"
              :src="data.item.previewUrl"
              alt="Preview"
              class="preview-image"
            />
            <div v-else class="no-image-placeholder">Нет изображения</div>
          </div>
        </template>

        <template #cell(id)="data">
          <div
            class="fld-picture-id"
            :style="{ color: currentPictureId === data.item.id ? 'blue' : 'black' }"
            @click.left="editPicture(data.item.id)"
            v-text="data.value"
          />
        </template>

        <template #cell(name)="data">
          <div
            class="fld-picture-name"
            :style="{ color: currentPictureId === data.item.id ? 'blue' : 'black' }"
            @click.left="editPicture(data.item.id)"
            v-text="data.value"
          />
        </template>
      </b-table>
    </div>
    <div class="pictures-bv-table-footer">
      <button class="btn-round-double" title="Фильтр" @click="isPicturesFilterVisible = true">
        <img alt="filter" class="icon-40" src="../../assets/svg/icon_filter.svg" />
      </button>
    </div>
  </div>
</template>

<script>
// import Vue from "vue";
// import { TablePlugin } from 'bootstrap-vue'
// import { PaginationPlugin } from 'bootstrap-vue'
// import { SpinnerPlugin } from 'bootstrap-vue'
import { BPagination, BSpinner, BTable, BFormInput } from 'bootstrap-vue-next'
import PictureEditModal from '../../components/Pictures/edit/PictureEditModal.vue'
import PicturesFilter from '../../components/Pictures/filter/PicturesFilterModal.vue'
import CustomConfirm from '../Common/CustomConfirm.vue'
// Vue.use(TablePlugin)
// Vue.use(PaginationPlugin)
// Vue.use(SpinnerPlugin)
/**
 * Таблица со списком pictures с пагинацией, фильтрами и сортировкой.
 *
 * @see archive/docs/features/dual-db-sync.md
 */

export default {
  name: 'PicturesTable',
  components: {
    PictureEditModal,
    PicturesFilter,
    CustomConfirm,
    BPagination,
    BSpinner,
    BTable,
    BFormInput,
  },
  data() {
    return {
      perPage: 30,
      // Восстанавливаем последнюю страницу из store, чтобы при уходе с компонента и возврате таблица
      // открывалась на той же странице.
      currentPage: this.$store.getters.getPicturesTableCurrentPage || 1,
      sortBy: [],
      isPictureEditVisible: false,
      isPicturesFilterVisible: false,
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
      isBusy: false,
      currentPictureId: '',
      currentPicture: undefined,
    }
  },
  computed: {
    // specs/358-rows-per-page: состояние saving для индикатора loading.
    isSavingRowsPerPage() {
      return this.$store.getters.isSavingRowsPerPage('pictures')
    },
    picturesDigestIsLoading() {
      return this.$store.getters.getPicturesDigestIsLoading
    },
    picturesDigests() {
      return this.$store.getters.getPicturesDigest
    },
    countRows() {
      return this.picturesDigests ? this.picturesDigests.length : 0
    },
    pictureDigestFields() {
      return [
        {
          key: 'preview',
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
          key: 'name',
          sortable: true,
          label: 'Имя',
          style: {
            minWidth: '500px',
            maxWidth: '500px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
      ]
    },
  },
  watch: {
    picturesDigestIsLoading: {
      handler() {
        this.isBusy = this.picturesDigestIsLoading
      },
    },
    // Сбрасываем currentPage на 1, если текущая страница вышла за пределы новой выборки
    // (например, после применения фильтра, сужающего число страниц). См. OpenProject #50.
    // Эталон правильной реализации — Songs/SongsTable.vue:998-1009.
    // @see docs/features/pagination-filter-admin-tables.md (FR-006)
    countRows: {
      handler(newCount) {
        const totalPages = Math.max(1, Math.ceil(newCount / this.perPage))
        if (this.currentPage > totalPages) {
          this.currentPage = 1
        }
      },
    },
    currentPage: {
      handler(newPage) {
        // Сохраняем страницу в store, чтобы она восстановилась после переключения на другой компонент.
        this.$store.commit('setPicturesTableCurrentPage', newPage)
      },
    },
  },
  async mounted() {
    // specs/358-rows-per-page: загрузить настройки таблиц (один раз при старте SPA,
    // защищён флагом  в store ).
    await this.$store.dispatch('loadTableSettings')
    this.perPage = this.$store.getters.getRowsPerPage('pictures')

    // this.$store.dispatch('loadPicturesDigests', { filterPicture: 'Павел Кашин'} )
  },
  methods: {
    /**
     * specs/358-rows-per-page: обработчик изменения поля «Строк на странице».
     * Парсит значение, валидирует диапазон, отправляет в backend, обновляет UI
     * только после успешного ответа (без оптимистичного обновления — см.
     * Clarifications Q3 spec.md).
     *
     * @param {string|number} newValue
     */
    async onPerPageChange(e) {
      // Bootstrap-vue-next `<b-form-input>` в нативном режиме передаёт в @change Event,
      // а не значение. Извлекаем value из target.
      const rawValue = e && e.target ? e.target.value : e
      const parsed = parseInt(rawValue, 10)
      if (isNaN(parsed) || parsed < 1 || parsed > 1000) {
        // eslint-disable-next-line no-console
        console.warn('[Pictures.onPerPageChange] invalid value', rawValue)
        return
      }
      if (parsed === this.perPage) return
      this.currentPage = 1 // ADR-0004: page reset
      if (this.$store.getters.getPicturesTableCurrentPage !== undefined) {
        this.$store.commit('setPicturesTableCurrentPage', 1)
      }
      const ok = await this.$store.dispatch('setRowsPerPage', {
        tableKey: 'pictures',
        value: parsed,
      })
      if (ok) {
        this.perPage = this.$store.getters.getRowsPerPage('pictures')
        await this.$store.dispatch('loadPicturesDigests', {
          page: this.currentPage,
          perPage: this.perPage,
          ...this.$store.getters.getPicturesFilter,
        })
      }
    },
    closeCustomConfirm() {
      this.isCustomConfirmVisible = false
    },

    editPicture(id) {
      this.$store.commit('setPictureCurrentId', id)
      this.isPictureEditVisible = true
    },
    closePictureEdit() {
      this.isPictureEditVisible = false
    },
    closePicturesFilter() {
      this.isPicturesFilterVisible = false
    },
    onRowClicked(item, index) {
      this.currentPicture = item
      this.currentPictureId = item.id
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
.pictures-bv-table {
  padding: 0;
  margin: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  font-family: Avenir, Helvetica, Arial, sans-serif;
}

.pictures-bv-table-header {
  width: fit-content;
  display: flex;
  align-items: center;
  gap: 10px;
}

.pictures-bv-table-body {
  width: fit-content;
}
.pictures-bv-table-body th {
  position: relative;
}
.pictures-bv-table-body th svg.bi {
  position: absolute;
  right: 2px;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0 !important;
  transition: opacity 0.15s ease;
  pointer-events: none;
}
.pictures-bv-table-body th:hover svg.bi {
  opacity: 0.6 !important;
}

.pictures-bv-table-footer {
  margin-top: auto;
  display: flex;
  flex-direction: row;
  align-items: center;
}

.fld-picture-id {
  min-width: 50px;
  max-width: 50px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-picture-id:hover {
  text-decoration: underline;
  cursor: pointer;
}
.fld-picture-name {
  min-width: 500px;
  max-width: 500px;
  text-align: left;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-picture-name:hover {
  text-decoration: underline;
  cursor: pointer;
}

/* Новые стили для ячейки с изображением */
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
.preview-image {
  width: auto; /* Позволяет высоте масштабироваться пропорционально */
  height: 50px; /* Установленная высота */
  object-fit: contain; /* Обеспечивает, что изображение полностью помещается в элемент, сохраняя пропорции */
  vertical-align: middle; /* Выравнивание по центру ячейки */
}

.no-image-placeholder {
  font-size: 0.7em;
  color: gray;
  text-align: center;
  padding: 5px;
  /* Можно добавить border или background для визуального обозначения */
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

/* specs/358-rows-per-page: поле ввода «Строк на странице» — выровнено по центру
   с кнопками пагинации. Bootstrap .form-control имеет min-height через padding
   + font-size, что смещает baseline относительно .btn-sm кнопок. */
#rows-per-page-pictures {
  padding: 0.25rem 0.5rem !important;
  line-height: 1.5 !important;
  height: 31px !important;
  font-size: 0.875rem !important;
  text-align: center;
  align-self: center;
}

/* specs/358-rows-per-page: убрать дефолтный margin-bottom у <b-pagination>
   внутри header-div, чтобы pagination был выровнен по центральной оси
   с input (без смещения baseline вниз). */
.pictures-bv-table-header .pagination,
.pictures-bv-table-header ul.pagination {
  margin-bottom: 0 !important;
}
</style>
