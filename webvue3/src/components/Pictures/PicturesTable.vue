<template>
  <div class="pictures-bv-table">
    <PictureEditModal v-if="isPictureEditVisible" @close="closePictureEdit"/>
    <PicturesFilter v-if="isPicturesFilterVisible" @close="closePicturesFilter"/>
    <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
    <div class="pictures-bv-table-header">
      <b-pagination
          v-model="currentPage"
          :total-rows="countRows"
          :per-page="perPage"
          :limit="20"
          size="sm"
          pills
      ></b-pagination>
    </div>
    <div class="pictures-bv-table-body">
      <b-table
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

        <template #cell(preview)="data">
          <div
              class="fld-picture-preview"
              :style="{ color: currentPictureId === data.item.id ? 'blue' : 'black' }"
              @click.left="editPicture(data.item.id)"
          >
            <img
                v-if="data.value && typeof data.value === 'string'"
                :src="'data:image/jpg;base64,' + data.value"
                alt="Preview"
                class="preview-image"
            >
            <div v-else class="no-image-placeholder">Нет изображения</div>
          </div>
        </template>

        <template #cell(id)="data">
          <div
              class="fld-picture-id"
              v-text="data.value"
              :style="{ color: currentPictureId === data.item.id ? 'blue' : 'black' }"
              @click.left="editPicture(data.item.id)"
          ></div>
        </template>

        <template #cell(name)="data">
          <div
              class="fld-picture-name"
              v-text="data.value"
              :style="{ color: currentPictureId === data.item.id ? 'blue' : 'black' }"
              @click.left="editPicture(data.item.id)"
          ></div>
        </template>

      </b-table>
    </div>
    <div class="pictures-bv-table-footer">
      <button class="btn-round-double" @click="isPicturesFilterVisible=true" title="Фильтр">
        <img alt="filter" class="icon-40" src="../../assets/svg/icon_filter.svg">
      </button>
    </div>


  </div>
</template>

<script>

// import Vue from "vue";
// import { TablePlugin } from 'bootstrap-vue'
// import { PaginationPlugin } from 'bootstrap-vue'
// import { SpinnerPlugin } from 'bootstrap-vue'
import { BPagination, BSpinner, BTable } from 'bootstrap-vue-next'
import PictureEditModal from "../../components/Pictures/edit/PictureEditModal.vue";
import PicturesFilter from "../../components/Pictures/filter/PicturesFilterModal.vue";
import CustomConfirm from "../Common/CustomConfirm.vue";
// Vue.use(TablePlugin)
// Vue.use(PaginationPlugin)
// Vue.use(SpinnerPlugin)

export default {
  name: "PicturesTable",
  components: {
    PictureEditModal,
    PicturesFilter,
    CustomConfirm,
    BPagination,
    BSpinner,
    BTable
  },
  data() {
    return {
      perPage: 19,
      currentPage: 1,
      isPictureEditVisible: false,
      isPicturesFilterVisible: false,
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
      isBusy: false,
      currentPictureId: '',
      currentPicture: undefined
    }
  },
  watch: {
    picturesDigestIsLoading: {
      handler () {
        this.isBusy = this.picturesDigestIsLoading;
      }
    }
  },
  mounted() {
    // this.$store.dispatch('loadPicturesDigests', { filter_picture: 'Павел Кашин'} )
  },
  computed: {
    picturesDigestIsLoading() {
      return this.$store.getters.getPicturesDigestIsLoading;
    },
    picturesDigests() {
      return this.$store.getters.getPicturesDigest;
    },
    countRows() {
      return this.picturesDigests ? this.picturesDigests.length : 0;
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
            fontSize: 'small'
          }
        },
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
          key: 'name',
          label: 'Имя',
          style: {
            minWidth: '500px',
            maxWidth: '500px',
            textAlign: 'left',
            fontSize: 'small'
          }
        }
      ]
    }
  },
  methods: {

    closeCustomConfirm() {
      this.isCustomConfirmVisible = false;
    },

    editPicture(id) {
      this.$store.commit('setPictureCurrentId', id);
      this.isPictureEditVisible = true;
    },
    closePictureEdit() {
      this.isPictureEditVisible = false;
    },
    closePicturesFilter() {
      this.isPicturesFilterVisible = false;
    },
    onRowClicked(item, index) {
      this.currentPicture = item;
      this.currentPictureId = item.id;
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
}

.pictures-bv-table-body {
  width: fit-content;
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

</style>