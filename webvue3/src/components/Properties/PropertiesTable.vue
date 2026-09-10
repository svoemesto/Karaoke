<template>
  <div class="properties-bv-table">
    <!--    <PropertyEditModal v-if="isPropertyEditVisible" @close="closePropertyEdit"/>-->
    <PropertiesFilter v-if="isPropertiesFilterVisible" @close="closePropertiesFilter" />
    <custom-confirm
      v-if="isCustomConfirmVisible"
      :params="customConfirmParams"
      @close="closeCustomConfirm"
    />
    <div class="properties-bv-table-header">
      <b-form-input
        :id="`rows-per-page-properties`"
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
    <div class="properties-bv-table-body">
      <b-table
        v-model:sort-by="sortBy"
        :items="propertiesDigests"
        :busy="isBusy"
        :fields="propertyDigestFields"
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

        <template #cell(key)="data">
          <div
            class="fld-key"
            :style="{
              backgroundColor: data.item.color,
              color: currentPropertyKey === data.item.key ? 'blue' : 'black',
            }"
            @click.left="changeValue(data.item)"
            v-text="data.value"
          />
        </template>

        <template #cell(value)="data">
          <div
            class="fld-value"
            :style="{
              backgroundColor: data.item.color,
              color: currentPropertyKey === data.item.key ? 'blue' : 'black',
            }"
            v-text="data.value"
          />
        </template>

        <template #cell(defaultValue)="data">
          <div
            class="fld-defaultValue"
            :style="{
              backgroundColor: data.item.color,
              color: currentPropertyKey === data.item.key ? 'blue' : 'black',
            }"
            v-text="data.value"
          />
        </template>

        <template #cell(description)="data">
          <div
            class="fld-description"
            :style="{
              backgroundColor: data.item.color,
              color: currentPropertyKey === data.item.key ? 'blue' : 'black',
            }"
            v-text="data.value"
          />
        </template>

        <template #cell(type)="data">
          <div
            class="fld-type"
            :style="{
              backgroundColor: data.item.color,
              color: currentPropertyKey === data.item.key ? 'blue' : 'black',
            }"
            v-text="data.value"
          />
        </template>
      </b-table>
    </div>
    <div class="properties-bv-table-footer">
      <button class="btn-round-double" title="Фильтр" @click="isPropertiesFilterVisible = true">
        <img alt="filter" class="icon-40" src="../../assets/svg/icon_filter.svg" />
      </button>
    </div>
  </div>
</template>

<script>
import { BPagination, BSpinner, BTable, BFormInput } from 'bootstrap-vue-next'
import PropertiesFilter from '../../components/Properties/filter/PropertiesFilterModal.vue'
import CustomConfirm from '../Common/CustomConfirm.vue'

/**
 * Таблица со списком properties с пагинацией, фильтрами и сортировкой.
 *
 * @see archive/docs/features/mlt-generator.md
 */

export default {
  name: 'PropertiesTable',
  components: {
    // PropertyEditModal,
    PropertiesFilter,
    CustomConfirm,
    BPagination,
    BSpinner,
    BTable,
    BFormInput,
  },
  data() {
    return {
      perPage: 50,
      // Восстанавливаем последнюю страницу из store, чтобы при уходе с компонента и возврате таблица
      // открывалась на той же странице.
      currentPage: this.$store.getters.getPropertiesTableCurrentPage || 1,
      sortBy: [],
      isPropertyEditVisible: false,
      isPropertiesFilterVisible: false,
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
      isBusy: false,
      currentPropertyKey: '',
      currentProperty: undefined,
    }
  },
  computed: {
    // specs/358-rows-per-page: состояние saving для индикатора loading.
    isSavingRowsPerPage() {
      return this.$store.getters.isSavingRowsPerPage('properties')
    },
    propertiesDigestIsLoading() {
      return this.$store.getters.getPropertiesDigestIsLoading
    },
    propertiesDigests() {
      return this.$store.getters.getPropertiesDigest
    },
    countRows() {
      return this.propertiesDigests ? this.propertiesDigests.length : 0
    },
    propertyDigestFields() {
      return [
        {
          key: 'key',
          sortable: true,
          label: 'KEY',
          style: {
            minWidth: '400px',
            maxWidth: '400px',
            textAlign: 'center',
            fontSize: 'small',
          },
        },
        {
          key: 'value',
          sortable: true,
          label: 'Значение',
          style: {
            minWidth: '300px',
            maxWidth: '300px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'defaultValue',
          sortable: true,
          label: 'Значение по-умолчанию',
          style: {
            minWidth: '300px',
            maxWidth: '300px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'description',
          sortable: true,
          label: 'Описание',
          style: {
            minWidth: '500px',
            maxWidth: '500px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
        {
          key: 'type',
          sortable: true,
          label: 'Тип',
          style: {
            minWidth: '120px',
            maxWidth: '120px',
            textAlign: 'left',
            fontSize: 'small',
          },
        },
      ]
    },
  },
  watch: {
    propertiesDigestIsLoading: {
      handler() {
        this.isBusy = this.propertiesDigestIsLoading
      },
    },
    currentPage: {
      handler(newPage) {
        // Сохраняем страницу в store, чтобы она восстановилась после переключения на другой компонент.
        this.$store.commit('setPropertiesTableCurrentPage', newPage)
      },
    },
  },
  async mounted() {
    // specs/358-rows-per-page: загрузить настройки таблиц (один раз при старте SPA,
    // защищён флагом  в store ).
    await this.$store.dispatch('loadTableSettings')
    this.perPage = this.$store.getters.getRowsPerPage('properties')

    // this.$store.dispatch('loadPropertiesDigests', { filterAuthor: 'Павел Кашин'} )
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
        console.warn('[Properties.onPerPageChange] invalid value', rawValue)
        return
      }
      if (parsed === this.perPage) return
      this.currentPage = 1 // ADR-0004: page reset
      if (this.$store.getters.getPropertiesTableCurrentPage !== undefined) {
        this.$store.commit('setPropertiesTableCurrentPage', 1)
      }
      const ok = await this.$store.dispatch('setRowsPerPage', {
        tableKey: 'properties',
        value: parsed,
      })
      if (ok) {
        this.perPage = this.$store.getters.getRowsPerPage('properties')
        await this.$store.dispatch('loadPropertiesDigests', {
          page: this.currentPage,
          perPage: this.perPage,
          ...this.$store.getters.getPropertiesFilter,
        })
      }
    },
    changeValue(item) {
      this.customConfirmParams = {
        header: 'Изменение значения настройки',
        body: `Значение настройки <strong>${item.key}.</strong>`,
        callback: this.doChangeValue,
        fields: [
          {
            fldName: 'propertyValue',
            fldLabel: 'Значение:',
            fldValue: item.value,
            fldIsBoolean: item.type === 'Boolean',
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '300px', textAlign: 'center', borderRadius: '10px' },
          },
        ],
      }
      this.isCustomConfirmVisible = true
    },

    doChangeValue(result) {
      this.$store
        .dispatch('setPropertyValuePromise', {
          propertyKey: this.currentProperty.key,
          propertyValue: result.propertyValue,
        })
        .then((data) => {
          // data - это объект, возвращаемый промисом
          if (data) {
            let result = JSON.parse(data)
            this.$store.dispatch('updateOneProperty', result.property)
          }
        })
        .catch((error) => {
          console.error('Ошибка при выполнении setPropertyValuePromise:', error)
        })
    },

    closeCustomConfirm() {
      this.isCustomConfirmVisible = false
    },

    editProperty(key) {
      this.$store.commit('setCurrentPropertyKey', key)
      this.isPropertyEditVisible = true
    },
    closePropertyEdit() {
      this.isPropertyEditVisible = false
    },
    closePropertiesFilter() {
      this.isPropertiesFilterVisible = false
    },
    onRowClicked(item, index) {
      this.currentProperty = item
      this.currentPropertyKey = item.key
      console.log(`Row '${index}' clicked: `, item.key)
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
.properties-bv-table {
  padding: 0;
  margin: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  font-family: Avenir, Helvetica, Arial, sans-serif;
}

.properties-bv-table-header {
  width: fit-content;
  display: flex;
  align-items: center;
  gap: 10px;
}

.properties-bv-table-body {
  width: fit-content;
}
.properties-bv-table-body th {
  position: relative;
}
.properties-bv-table-body th svg.bi {
  position: absolute;
  right: 2px;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0 !important;
  transition: opacity 0.15s ease;
  pointer-events: none;
}
.properties-bv-table-body th:hover svg.bi {
  opacity: 0.6 !important;
}

.properties-bv-table-footer {
  margin-top: auto;
  display: flex;
  flex-direction: row;
  align-items: center;
}

.fld-key {
  min-width: 400px;
  max-width: 400px;
  text-align: left;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-key:hover {
  text-decoration: underline;
  cursor: pointer;
}
.fld-value {
  min-width: 300px;
  max-width: 300px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-defaultValue {
  min-width: 300px;
  max-width: 300px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-description {
  min-width: 500px;
  max-width: 500px;
  text-align: left;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-type {
  min-width: 120px;
  max-width: 120px;
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

/* specs/358-rows-per-page: поле ввода «Строк на странице» — выровнено по центру
   с кнопками пагинации. Bootstrap .form-control имеет min-height через padding
   + font-size, что смещает baseline относительно .btn-sm кнопок. */
#rows-per-page-properties {
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
.properties-bv-table-header .pagination,
.properties-bv-table-header ul.pagination {
  margin-bottom: 0 !important;
}
</style>
