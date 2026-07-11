<template>
  <div class="dictionaries-bv-table">
    <DictionariesFilter v-if="isDictionariesFilterVisible" @close="closeDictionariesFilter"/>
    <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />

    <div class="dictionaries-bv-table-add">
      <div class="dictionaries-bv-table-add-title">Добавить:</div>
      <select class="dct-field" v-model="newItem.dictName">
        <option value="" disabled>(словарь)</option>
        <option v-for="name in dictNames" :key="name" :value="name" v-text="name"></option>
      </select>
      <input class="dct-field" placeholder="Значение" v-model="newItem.dictValue">
      <button class="dct-btn-round" :disabled="!canCreate" @click="create">Добавить</button>
    </div>

    <div class="dictionaries-bv-table-header">
      <b-pagination
          v-model="currentPage"
          :total-rows="countRows"
          :per-page="perPage"
          :limit="20"
          size="sm"
          pills
      ></b-pagination>
    </div>
    <div class="dictionaries-bv-table-body">
      <b-table
          :items="dictionariesDigest"
          :busy="isBusy"
          :fields="dictionaryDigestFields"
          :per-page="perPage"
          :current-page="currentPage"
          v-model:sort-by="sortBy"
          small
          bordered
          hover
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
          <div class="fld-dict-id" v-text="data.value"></div>
        </template>

        <template #cell(dictName)="data">
          <div class="fld-dict-name" v-text="data.value" @click.left="changeValue(data.item)"></div>
        </template>

        <template #cell(dictValue)="data">
          <div class="fld-dict-value" v-text="data.value" @click.left="changeValue(data.item)"></div>
        </template>

        <template #cell(actions)="data">
          <div class="fld-dict-actions">
            <button class="dct-btn-round-small" @click="remove(data.item)" title="Удалить">×</button>
          </div>
        </template>

      </b-table>
    </div>
    <div class="dictionaries-bv-table-footer">
      <button class="dct-btn-round-double" @click="isDictionariesFilterVisible=true" title="Фильтр">
        <img alt="filter" class="dct-icon-40" src="../../assets/svg/icon_filter.svg">
      </button>
    </div>

  </div>
</template>

<script>

import { BPagination, BSpinner, BTable } from 'bootstrap-vue-next'
import DictionariesFilter from "./filter/DictionariesFilterModal.vue";
import CustomConfirm from "../Common/CustomConfirm.vue";

export default {
  name: "DictionariesTable",
  components: {
    DictionariesFilter,
    CustomConfirm,
    BPagination,
    BSpinner,
    BTable
  },
  data() {
    return {
      perPage: 19,
      currentPage: 1,
      sortBy: [],
      isDictionariesFilterVisible: false,
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
      isBusy: false,
      newItem: { dictName: '', dictValue: '' }
    }
  },
  watch: {
    dictionariesDigestIsLoading: {
      handler () {
        this.isBusy = this.dictionariesDigestIsLoading;
      }
    }
  },
  mounted() {
    this.$store.dispatch('loadDictNames');
    this.$store.dispatch('loadDictionariesDigests', {});
  },
  computed: {
    dictionariesDigestIsLoading() {
      return this.$store.getters.getDictionariesDigestIsLoading;
    },
    dictionariesDigest() {
      return this.$store.getters.getDictionariesDigest;
    },
    dictNames() {
      return this.$store.getters.getDictNames;
    },
    countRows() {
      return this.dictionariesDigest ? this.dictionariesDigest.length : 0;
    },
    canCreate() {
      return !!this.newItem.dictName && this.newItem.dictValue.trim() !== '';
    },
    dictionaryDigestFields() {
      return [
        {
          key: 'id',
          sortable: true,
          label: 'ID',
          style: { minWidth: '60px', maxWidth: '60px', textAlign: 'center', fontSize: 'small' }
        },
        {
          key: 'dictName',
          sortable: true,
          label: 'Словарь',
          style: { minWidth: '250px', maxWidth: '250px', textAlign: 'left', fontSize: 'small' }
        },
        {
          key: 'dictValue',
          sortable: true,
          label: 'Значение',
          style: { minWidth: '400px', maxWidth: '400px', textAlign: 'left', fontSize: 'small' }
        },
        {
          key: 'actions',
          label: '',
          style: { minWidth: '50px', maxWidth: '50px', textAlign: 'center', fontSize: 'small' }
        }
      ]
    }
  },
  methods: {

    create() {
      if (!this.canCreate) return;
      // Пара (dictName, dictValue) уникальна на уровне БД (uq_tbl_dictionaries_name_value);
      // бэкенд возвращает created:false, если такая пара уже существовала (createNewDictionaryItem
      // идемпотентен) — предупреждаем пользователя явно, а не просто молчим.
      this.$store.dispatch('createDictionaryItemPromise', { dictName: this.newItem.dictName, dictValue: this.newItem.dictValue })
          .then(result => {
            this.$store.dispatch('loadDictionariesDigests', {});
            if (!result.created) {
              alert(`Значение «${this.newItem.dictValue}» уже есть в словаре «${this.newItem.dictName}»`);
            }
            this.newItem.dictValue = '';
          })
          .catch(error => {
            console.error("Ошибка при добавлении значения словаря:", error);
          });
    },

    remove(item) {
      if (!confirm(`Удалить значение «${item.dictValue}» из словаря «${item.dictName}»?`)) return;
      this.$store.dispatch('deleteDictionaryItemPromise', item.id)
          .then(() => {
            this.$store.commit('removeDictionariesDigest', item.id);
          })
          .catch(error => {
            console.error("Ошибка при удалении значения словаря:", error);
          });
    },

    changeValue(item) {

      this.customConfirmParams = {
        header: 'Изменение значения словаря',
        body: `Запись ID = <strong>${item.id}</strong>`,
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
            fldName: 'dictName',
            fldLabel: 'Словарь:',
            fldValue: item.dictName,
            fldIsSelect: true,
            fldOptions: this.dictNames,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px'}
          },
          {
            fldName: 'dictValue',
            fldLabel: 'Значение:',
            fldValue: item.dictValue,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },

    doChangeValue(payload) {
      this.$store.dispatch('saveDictionaryItemPromise', payload)
          .then(result => {
            if (result !== 0) {
              this.$store.dispatch('loadOneRecord', result);
            } else {
              // save() на бэкенде поймал конфликт uq_tbl_dictionaries_name_value (такая пара
              // dictName+dictValue уже есть у другой записи) и вернул 0 вместо id.
              alert('Не удалось сохранить: такая пара «словарь + значение» уже существует.');
            }
          })
          .catch(error => {
            console.error("Ошибка при сохранении значения словаря:", error);
          });
    },

    closeCustomConfirm() {
      this.isCustomConfirmVisible = false;
    },
    closeDictionariesFilter() {
      this.isDictionariesFilterVisible = false;
    },
  }
}
</script>

<style>

.dictionaries-bv-table {
  padding: 0;
  margin: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  font-family: Avenir, Helvetica, Arial, sans-serif;
}

.dictionaries-bv-table-add {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  padding: 8px;
  border: thin dashed darkgray;
  border-radius: 8px;
  font-size: small;
}
.dictionaries-bv-table-add-title {
  font-weight: bold;
}

.dictionaries-bv-table-header {
  width: fit-content;
}

.dictionaries-bv-table-body {
  width: fit-content;
}
.dictionaries-bv-table-body th {
  position: relative;
}
.dictionaries-bv-table-body th svg.bi {
  position: absolute;
  right: 2px;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0 !important;
  transition: opacity 0.15s ease;
  pointer-events: none;
}
.dictionaries-bv-table-body th:hover svg.bi {
  opacity: 0.6 !important;
}

.dictionaries-bv-table-footer {
  margin-top: auto;
  display: flex;
  flex-direction: row;
  align-items: center;
}

.fld-dict-id {
  min-width: 60px;
  max-width: 60px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-dict-name {
  min-width: 250px;
  max-width: 250px;
  text-align: left;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.fld-dict-name:hover {
  text-decoration: underline;
  cursor: pointer;
}

.fld-dict-value {
  min-width: 400px;
  max-width: 400px;
  text-align: left;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.fld-dict-value:hover {
  text-decoration: underline;
  cursor: pointer;
}

.fld-dict-actions {
  min-width: 50px;
  max-width: 50px;
  text-align: center;
  font-size: small;
}

/* Инвариант DEVELOPMENT.md: select и соседний input в одном ряду добавления — один и тот же явный
   width общим классом (.dct-field) сразу на оба элемента, appearance:none на select обязателен
   (иначе ОС-рамка/паддинг раздувает высоту ряда и рвёт совпадение по бордеру с input). */
.dct-field {
  width: 220px;
  box-sizing: border-box;
  border: 1px solid black;
  border-radius: 4px;
  padding: 4px 6px;
  font-size: small;
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
}

.dct-btn-round {
  border: solid 1px black;
  border-radius: 6px;
  padding: 4px 12px;
  background-color: antiquewhite;
  cursor: pointer;
}
.dct-btn-round:hover {
  background-color: lightpink;
}
.dct-btn-round[disabled] {
  background-color: lightgray;
  cursor: default;
}

.dct-btn-round-small {
  border: solid 1px black;
  border-radius: 6px;
  width: 24px;
  height: 24px;
  background-color: #f4b6b6;
  cursor: pointer;
  line-height: 1;
}
.dct-btn-round-small:hover {
  background-color: #e08a8a;
}

.dct-btn-round-double {
  border: solid 1px black;
  border-radius: 6px;
  width: 50px;
  height: 50px;
  margin-left: 2px;
  background-color: antiquewhite;
}
.dct-btn-round-double:hover {
  background-color: lightpink;
}
.dct-btn-round-double:focus {
  background-color: darksalmon;
}
.dct-btn-round-double[disabled] {
  background-color: lightgray;
}
.dct-icon-40 {
  width: 40px;
  height: 40px;
}

</style>
