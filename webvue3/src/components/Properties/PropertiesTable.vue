<template>
  <div class="properties-bv-table">
<!--    <PropertyEditModal v-if="isPropertyEditVisible" @close="closePropertyEdit"/>-->
    <PropertiesFilter v-if="isPropertiesFilterVisible" @close="closePropertiesFilter"/>
    <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
    <div class="properties-bv-table-header">
      <b-pagination
          v-model="currentPage"
          :total-rows="countRows"
          :per-page="perPage"
          :limit="20"
          size="sm"
          pills
      ></b-pagination>
    </div>
    <div class="properties-bv-table-body">
      <b-table
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

        <template #cell(key)="data">
          <div
              class="fld-key"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentPropertyKey === data.item.key ? 'blue' : 'black' }"
              @click.left="changeValue(data.item)"
          ></div>
        </template>

        <template #cell(value)="data">
          <div
              class="fld-value"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentPropertyKey === data.item.key ? 'blue' : 'black' }"
          ></div>
        </template>

        <template #cell(defaultValue)="data">
          <div
              class="fld-defaultValue"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentPropertyKey === data.item.key ? 'blue' : 'black' }"
          ></div>
        </template>

        <template #cell(description)="data">
          <div
              class="fld-description"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentPropertyKey === data.item.key ? 'blue' : 'black' }"
          ></div>
        </template>

        <template #cell(type)="data">
          <div
              class="fld-type"
              v-text="data.value"
              :style="{ backgroundColor: data.item.color, color: currentPropertyKey === data.item.key ? 'blue' : 'black' }"
          ></div>
        </template>
        

      </b-table>
    </div>
    <div class="properties-bv-table-footer">
      <button class="btn-round-double" @click="isPropertiesFilterVisible=true" title="Фильтр">
        <img alt="filter" class="icon-40" src="../../assets/svg/icon_filter.svg">
      </button>
    </div>


  </div>
</template>

<script>


import { BPagination, BSpinner, BTable } from 'bootstrap-vue-next'
import PropertiesFilter from "../../components/Properties/filter/PropertiesFilterModal.vue";
import CustomConfirm from "../Common/CustomConfirm.vue";

export default {
  name: "PropertiesTable",
  components: {
    // PropertyEditModal,
    PropertiesFilter,
    CustomConfirm,
    BPagination,
    BSpinner,
    BTable
  },
  data() {
    return {
      perPage: 50,
      currentPage: 1,
      isPropertyEditVisible: false,
      isPropertiesFilterVisible: false,
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
      isBusy: false,
      currentPropertyKey: '',
      currentProperty: undefined
    }
  },
  watch: {
    propertiesDigestIsLoading: {
      handler () {
        this.isBusy = this.propertiesDigestIsLoading;
      }
    }
  },
  mounted() {
    // this.$store.dispatch('loadPropertiesDigests', { filterAuthor: 'Павел Кашин'} )
  },
  computed: {
    propertiesDigestIsLoading() {
      return this.$store.getters.getPropertiesDigestIsLoading;
    },
    propertiesDigests() {
      return this.$store.getters.getPropertiesDigest;
    },
    countRows() {
      return this.propertiesDigests ? this.propertiesDigests.length : 0;
    },
    propertyDigestFields() {
      return [
        {
          key: 'key',
          label: 'KEY',
          style: {
            minWidth: '400px',
            maxWidth: '400px',
            textAlign: 'center',
            fontSize: 'small'
          }
        },
        {
          key: 'value',
          label: 'Значение',
          style: {
            minWidth: '300px',
            maxWidth: '300px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'defaultValue',
          label: 'Значение по-умолчанию',
          style: {
            minWidth: '300px',
            maxWidth: '300px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'description',
          label: 'Описание',
          style: {
            minWidth: '500px',
            maxWidth: '500px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'type',
          label: 'Тип',
          style: {
            minWidth: '120px',
            maxWidth: '120px',
            textAlign: 'left',
            fontSize: 'small'
          }
        }
      ]
    }
  },
  methods: {

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
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '300px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },

    doChangeValue(result) {
      this.$store.dispatch('setPropertyValuePromise', {propertyKey: this.currentProperty.key, propertyValue: result.propertyValue}).then(data => { // data - это объект, возвращаемый промисом
        if (data) {
          let result = JSON.parse(data);
          this.$store.dispatch('updateOneProperty', result.property);
        }
        })
          .catch(error => {
            console.error("Ошибка при выполнении setPropertyValuePromise:", error);
        });
    },

    closeCustomConfirm() {
      this.isCustomConfirmVisible = false;
    },

    editProperty(key) {
      this.$store.commit('setCurrentPropertyKey', key);
      this.isPropertyEditVisible = true;
    },
    closePropertyEdit() {
      this.isPropertyEditVisible = false;
    },
    closePropertiesFilter() {
      this.isPropertiesFilterVisible = false;
    },
    onRowClicked(item, index) {
      this.currentProperty = item;
      this.currentPropertyKey = item.key;
      console.log(`Row '${index}' clicked: `, item.key);
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
}

.properties-bv-table-body {
  width: fit-content;
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

</style>