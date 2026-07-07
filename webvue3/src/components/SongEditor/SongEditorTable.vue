<template>
  <div class="set-table">
    <AssignModal v-if="isAssignVisible" @close="isAssignVisible = false" @assigned="onAssigned" />
    <ReviewModal v-if="isReviewVisible" @close="isReviewVisible = false" @reviewed="onReviewed" />

    <div class="set-toolbar">
      <label class="set-toolbar-item">
        БД:
        <select v-model="target" @change="reload">
          <option value="local">Локальная</option>
          <option value="remote">Сервер</option>
        </select>
      </label>
      <select class="set-toolbar-item" v-model="filterStatus" @change="reload">
        <option value="">Все статусы</option>
        <option value="assigned">Назначено</option>
        <option value="in_progress">В работе</option>
        <option value="submitted">На проверке</option>
        <option value="approved">Одобрено</option>
        <option value="rejected">Отклонено</option>
      </select>
      <button class="set-toolbar-item set-btn" @click="reload">Обновить</button>
      <button class="set-toolbar-item set-btn set-btn-add" @click="isAssignVisible = true">+ Назначить песню</button>
    </div>

    <div class="set-table-body">
      <b-table
          :items="digest"
          :busy="isBusy"
          :fields="fields"
          small bordered hover
          @row-clicked="onRowClicked"
      >
        <template #table-busy>
          <div class="text-center text-danger my-2"><b-spinner class="align-middle"></b-spinner><strong> Загрузка...</strong></div>
        </template>
        <template #table-colgroup="scope">
          <col v-for="field in scope.fields" :key="field.key" :style="field.style">
        </template>
        <template #cell(status)="data">
          <span class="set-badge" :class="`set-badge-${data.value}`">{{ statusLabel(data.value) }}</span>
        </template>
        <template #cell(actions)="data">
          <button class="set-mini-btn" @click.stop="openReview(data.item.id)">Просмотр</button>
          <button class="set-mini-btn set-mini-del" @click.stop="onDelete(data.item.id)">Удалить</button>
        </template>
      </b-table>
    </div>

    <div class="set-table-footer">Всего: {{ digest.length }}</div>
  </div>
</template>

<script>
import { BSpinner, BTable } from 'bootstrap-vue-next'
import AssignModal from './AssignModal.vue'
import ReviewModal from './ReviewModal.vue'

const STATUS_LABELS = {
  assigned: 'Назначено', in_progress: 'В работе', submitted: 'На проверке',
  approved: 'Одобрено', rejected: 'Отклонено',
};

export default {
  name: "SongEditorTable",
  components: { AssignModal, ReviewModal, BSpinner, BTable },
  data() {
    return { isBusy: false, filterStatus: '', isAssignVisible: false, isReviewVisible: false }
  },
  computed: {
    digest() { return this.$store.getters.getAssignmentsDigest || [] },
    target: {
      get() { return this.$store.getters.getAssignmentsTarget },
      set(v) { this.$store.dispatch('setAssignmentsTarget', v) }
    },
    fields() {
      return [
        { key: 'id', label: 'ID', style: { minWidth: '50px', maxWidth: '50px', textAlign: 'center', fontSize: 'small' } },
        { key: 'songName', label: 'Песня', style: { minWidth: '220px', textAlign: 'left', fontSize: 'small' } },
        { key: 'author', label: 'Автор', style: { minWidth: '160px', textAlign: 'left', fontSize: 'small' } },
        { key: 'assigneeName', label: 'Исполнитель', style: { minWidth: '160px', textAlign: 'left', fontSize: 'small' } },
        { key: 'voice', label: 'Голос', style: { minWidth: '55px', maxWidth: '55px', textAlign: 'center', fontSize: 'small' } },
        { key: 'status', label: 'Статус', style: { minWidth: '110px', maxWidth: '110px', textAlign: 'center', fontSize: 'small' } },
        { key: 'actions', label: '', style: { minWidth: '160px', maxWidth: '160px', textAlign: 'center', fontSize: 'small' } },
      ]
    }
  },
  watch: {
    assignmentsIsLoading() { this.isBusy = this.$store.getters.getAssignmentsIsLoading }
  },
  mounted() { this.reload(); },
  methods: {
    statusLabel(s) { return STATUS_LABELS[s] || s },
    reload() {
      this.isBusy = true;
      this.$store.dispatch('loadAssignmentsDigest', { filterStatus: this.filterStatus })
        .finally(() => { this.isBusy = false; });
    },
    async openReview(id) {
      await this.$store.dispatch('loadAssignmentById', id);
      this.isReviewVisible = true;
    },
    onRowClicked(item) { this.openReview(item.id); },
    onAssigned() { this.isAssignVisible = false; this.reload(); },
    onReviewed() { this.isReviewVisible = false; this.reload(); },
    async onDelete(id) {
      if (!confirm('Снять назначение (удалить задание)?')) return;
      await this.$store.dispatch('deleteAssignment', id);
      this.reload();
    }
  }
}
</script>

<style scoped>
.set-table { padding: 0; margin: 0; width: 100%; display: flex; flex-direction: column; align-items: center; font-family: Avenir, Helvetica, Arial, sans-serif; }
.set-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; font-size: small; }
.set-toolbar-item { font-size: small; }
.set-btn { border: solid 1px black; border-radius: 6px; padding: 4px 10px; background-color: antiquewhite; cursor: pointer; }
.set-btn:hover { background-color: lightpink; }
.set-btn-add { background-color: #d1f5d8; }
.set-table-body { width: fit-content; }
.set-table-footer { margin-top: 6px; font-size: small; color: gray; }
.set-badge { font-size: 0.72rem; font-weight: 700; border-radius: 20px; padding: 0.12rem 0.55rem; }
.set-badge-assigned { background: #e2e6ea; color: #5a6570; }
.set-badge-in_progress { background: #dbeafe; color: #1e5fbf; }
.set-badge-submitted { background: #fef3c7; color: #92700a; }
.set-badge-approved { background: #d1f5d8; color: #24803a; }
.set-badge-rejected { background: #ffe0cc; color: #b8500f; }
.set-mini-btn { font-size: 0.72rem; border: 1px solid #bbb; border-radius: 6px; background: #f5f5f5; padding: 2px 8px; cursor: pointer; margin: 0 2px; }
.set-mini-btn:hover { background: #e6e6e6; }
.set-mini-del:hover { background: #ffd9d0; }
</style>
