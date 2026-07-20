<template>
  <div class="set-table">
    <AssignModal v-if="isAssignVisible" @close="isAssignVisible = false" @assigned="onAssigned" />
    <ReviewModal
      v-if="isReviewVisible"
      @close="isReviewVisible = false"
      @reviewed="onReviewed"
      @open-editor="onOpenEditor"
    />
    <SongKaraokeEditorModal
      v-if="isKaraokeEditorOpen"
      :id="karaokeEditorAssignmentId"
      mode="assignment"
      :target="karaokeEditorTarget"
      @close="isKaraokeEditorOpen = false"
    />

    <div class="set-toolbar">
      <label class="set-toolbar-item">
        БД:
        <select v-model="target" @change="reload">
          <option value="local">Локальная</option>
          <option value="remote">Сервер</option>
        </select>
      </label>
      <select v-model="filterStatus" class="set-toolbar-item" @change="reload">
        <option value="">Все статусы</option>
        <option value="assigned">Назначено</option>
        <option value="in_progress">В работе</option>
        <option value="submitted">На проверке</option>
        <option value="approved">Одобрено</option>
        <option value="rejected">Отклонено</option>
      </select>
      <select v-model="filterAssigneeId" class="set-toolbar-item" @change="reload">
        <option value="">Все исполнители</option>
        <option v-for="u in editorSiteUsers" :key="u.id" :value="u.id">{{ u.displayName || u.email }}</option>
      </select>
      <input v-model="filterAuthor" class="set-toolbar-item" type="text" placeholder="Автор" list="set-authors-list" @change="reload"/>
      <datalist id="set-authors-list">
        <option v-for="a in dictAuthors" :key="a" :value="a"/>
      </datalist>
      <button class="set-toolbar-item set-btn" @click="reload">Обновить</button>
      <button class="set-toolbar-item set-btn set-btn-add" @click="isAssignVisible = true">+ Назначить песню</button>
    </div>

    <div v-if="isRemoteView" class="set-remote-note">
      Просмотр серверной БД. Назначить/одобрить/отклонить/удалить — работают и отсюда (id совпадает в
      обеих БД); новое назначение создастся сразу на сервере (реальный цикл работы часто идёт целиком
      на PROD). Изменение самой песни при апруве в любом случае применяется в локальной БД.
    </div>

    <div class="set-table-body">
      <b-table
          v-model:sort-by="sortBy"
          :items="sortedDigest"
          :busy="isBusy"
          :fields="fields"
          small bordered hover
          @row-clicked="onRowClicked"
      >
        <template #table-busy>
          <div class="text-center text-danger my-2"><b-spinner class="align-middle"/><strong> Загрузка...</strong></div>
        </template>
        <template #table-colgroup="scope">
          <col v-for="field in scope.fields" :key="field.key" :style="field.style"/>
        </template>
        <template #cell(status)="data">
          <span class="set-badge" :class="`set-badge-${data.value}`">{{ statusLabel(data.value) }}</span>
        </template>
        <template #cell(actions)="data">
          <button class="set-mini-btn" @click.stop="openReview(data.item.id)">Просмотр</button>
          <button class="set-mini-btn set-mini-revoke" @click.stop="onRevoke(data.item)">Отозвать</button>
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
import SongKaraokeEditorModal from './SongKaraokeEditorModal.vue'

const STATUS_LABELS = {
  assigned: 'Назначено', in_progress: 'В работе', submitted: 'На проверке',
  approved: 'Одобрено', rejected: 'Отклонено',
};

const STATUS_ORDER = {
  submitted: 0, in_progress: 1, assigned: 2, approved: 3, rejected: 4,
};

export default {
  name: "SongEditorTable",
  components: { AssignModal, ReviewModal, SongKaraokeEditorModal, BSpinner, BTable },
  data() {
    return {
      isBusy: false, sortBy: [], filterStatus: '', filterAssigneeId: '', filterAuthor: '',
      isAssignVisible: false, isReviewVisible: false, dictAuthors: [],
      // Состояние модалки онлайн-редактора, открываемой из ReviewModal (mode='assignment').
      isKaraokeEditorOpen: false,
      karaokeEditorAssignmentId: 0,
      karaokeEditorTarget: 'local',
    }
  },
  computed: {
    digest() { return this.$store.getters.getAssignmentsDigest || [] },
    sortedDigest() {
      return [...this.digest].sort((a, b) => (STATUS_ORDER[a.status] ?? 99) - (STATUS_ORDER[b.status] ?? 99));
    },
    editorSiteUsers() { return this.$store.getters.getEditorSiteUsers || [] },
    target: {
      get() { return this.$store.getters.getAssignmentsTarget },
      set(v) { this.$store.dispatch('setAssignmentsTarget', v) }
    },
    // Только для информационного баннера — assign/approve/reject/delete работают одинаково в обоих
    // видах (id совпадает в LOCAL/REMOTE; songassignments синкается SERVER_TO_LOCAL, реальный цикл
    // работы часто идёт целиком на PROD). Единственное, что всегда LOCAL — само изменение песни при
    // апруве (см. SongEditorController.approve).
    isRemoteView() { return this.target === 'remote' },
    fields() {
      return [
        { key: 'id', sortable: true, label: 'ID', style: { minWidth: '50px', maxWidth: '50px', textAlign: 'center', fontSize: 'small' } },
        { key: 'author', sortable: true, label: 'Автор', style: { minWidth: '160px', textAlign: 'left', fontSize: 'small' } },
        { key: 'year', sortable: true, label: 'Год', style: { minWidth: '60px', maxWidth: '60px', textAlign: 'center', fontSize: 'small' } },
        { key: 'album', sortable: true, label: 'Альбом', style: { minWidth: '160px', textAlign: 'left', fontSize: 'small' } },
        { key: 'songName', sortable: true, label: 'Песня', style: { minWidth: '220px', textAlign: 'left', fontSize: 'small' } },
        { key: 'assigneeName', sortable: true, label: 'Редактор', style: { minWidth: '160px', textAlign: 'center', fontSize: 'small' } },
        { key: 'status', sortable: true, label: 'Статус', style: { minWidth: '110px', maxWidth: '110px', textAlign: 'center', fontSize: 'small' } },
        { key: 'actions', label: '', style: { minWidth: '160px', maxWidth: '160px', textAlign: 'center', fontSize: 'small' } },
      ]
    }
  },
  watch: {
    assignmentsIsLoading() { this.isBusy = this.$store.getters.getAssignmentsIsLoading }
  },
  async mounted() {
    // defaultTarget (KaraokeProperty editorAssignmentDefaultTarget) сеет и стартовое значение "БД" —
    // ждём его ДО первого reload(), иначе первая загрузка ушла бы в старый дефолт 'local'.
    await this.$store.dispatch('loadEditorDefaultTarget');
    this.reload();
    this.$store.dispatch('loadEditorSiteUsers');
    this.$store.getters.songAuthorsPromise.then(data => {
      this.dictAuthors = JSON.parse(data).authors || [];
    }).catch(() => {});
  },
  methods: {
    statusLabel(s) { return STATUS_LABELS[s] || s },
    reload() {
      this.isBusy = true;
      const params = {};
      if (this.filterStatus) params.filterStatus = this.filterStatus;
      // filterAssigneeId — Long? на бэкенде: пустая строка не биндится, поэтому передаём ключ только
      // когда реально выбран исполнитель (тот же паттерн, что searchCandidateSongs ниже).
      if (this.filterAssigneeId) params.filterAssigneeId = this.filterAssigneeId;
      if (this.filterAuthor) params.filterAuthor = this.filterAuthor;
      this.$store.dispatch('loadAssignmentsDigest', params)
        .finally(() => { this.isBusy = false; });
    },
    async openReview(id) {
      await this.$store.dispatch('loadAssignmentById', id);
      this.isReviewVisible = true;
    },
    onRowClicked(item) { this.openReview(item.id); },
    onOpenEditor(payload) {
      // ReviewModal просит открыть редактор — поднимаем модалку редактора ПОВЕРХ карточки
      // проверки (не закрывая её). z-index editor'а (1080) > z-index ReviewModal (1000), так что
      // editor просто перекрывает Review; после закрытия editor'а ReviewModal всё ещё видна и
      // пользователь возвращается к проверке задания. isReviewVisible выставляется в false только
      // явным @close от ReviewModal (кликом по её фону или кнопкой «Закрыть»).
      this.karaokeEditorAssignmentId = payload.assignmentId;
      this.karaokeEditorTarget = payload.target || 'local';
      this.isKaraokeEditorOpen = true;
    },
    onAssigned() { this.isAssignVisible = false; this.reload(); },
    onReviewed() { this.isReviewVisible = false; this.reload(); },
    async onDelete(id) {
      if (!confirm('Снять назначение (удалить задание)?')) return;
      await this.$store.dispatch('deleteAssignment', id);
      this.reload();
    },
    async onRevoke(item) {
      if (!confirm(`Отозвать назначение у «${item.assigneeName || item.assigneeEmail}» на песню «${item.songName}»? Задание и его черновик будут удалены — редактор больше не сможет его править, и эту же песню сразу можно будет назначить другому через селектор «Назначить…» в таблице песен.`)) return;
      await this.$store.dispatch('revokeAssignment', item.id);
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
.set-btn:disabled, .set-mini-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.set-remote-note {
  width: 100%; max-width: 900px; font-size: 0.8rem; color: #8a6d0a; background: #fef8e3;
  border: 1px solid #f2dd9a; border-radius: 8px; padding: 0.5rem 0.75rem; margin-bottom: 8px;
}
.set-table-body { width: fit-content; }
.set-table-body :deep(th) { position: relative; }
.set-table-body :deep(th svg.bi) {
  position: absolute;
  right: 2px;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0 !important;
  transition: opacity 0.15s ease;
  pointer-events: none;
}
.set-table-body :deep(th:hover svg.bi) { opacity: 0.6 !important; }
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
.set-mini-revoke:hover { background: #e5d8f0; }
</style>
