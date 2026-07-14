<template>
  <div class="sut-table">
    <SiteUserEditModal v-if="isUserEditVisible" @close="closeUserEdit" />
    <SiteUsersFilterModal v-if="isFilterVisible" @close="closeFilter" />

    <div class="sut-toolbar">
      <label class="sut-toolbar-item">
        БД:
        <select v-model="target" @change="onTargetChange">
          <option value="local">Локальная</option>
          <option value="remote">Сервер</option>
        </select>
      </label>
      <button class="sut-toolbar-item sut-btn" @click="isFilterVisible=true">Фильтр{{ activeFilterCount > 0 ? ` (${activeFilterCount})` : '' }}</button>
      <button class="sut-toolbar-item sut-btn" @click="reload">Обновить</button>
    </div>

    <div class="sut-table-header">
      <b-pagination
          v-model="currentPage"
          :total-rows="countRows"
          :per-page="perPage"
          :limit="20"
          size="sm"
          pills
      ></b-pagination>
    </div>
    <div class="sut-table-body">
      <b-table
          :items="siteUsersDigest"
          :busy="isBusy"
          :fields="siteUserDigestFields"
          :per-page="perPage"
          :current-page="currentPage"
          v-model:sort-by="sortBy"
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
          <col v-for="field in scope.fields" :key="field.key" :style="field.style">
        </template>

        <template #cell(effectivePremium)="data">
          <div
              style="text-align: center"
              :title="data.value ? 'Премиум активен сейчас' : 'Премиума нет'"
          >{{ data.value ? '🪙' : '' }}</div>
        </template>

        <template #cell(email)="data">
          <div class="fld-email" v-text="data.value" @click.left="editUser(data.item.id, data.item)"></div>
        </template>

        <template #cell(banned)="data">
          <div :style="{ color: data.value ? 'darkred' : 'darkgreen', textAlign: 'center' }">
            {{ data.value ? 'Забанен' : 'Активен' }}
          </div>
        </template>

        <template #cell(banReason)="data">
          <div class="fld-ellipsis" :title="data.value">{{ data.value }}</div>
        </template>

        <template #cell(premium)="data">
          <div style="text-align: center">{{ data.value ? 'Да' : '' }}</div>
        </template>

        <template #cell(permanentPremium)="data">
          <div style="text-align: center">{{ data.value ? 'Да' : '' }}</div>
        </template>

        <template #cell(editor)="data">
          <div style="text-align: center">{{ data.value ? 'Да' : '' }}</div>
        </template>

        <template #cell(sponsrPremiumUntil)="data">
          <div style="text-align: center">{{ formatDate(data.value) }}</div>
        </template>

        <template #cell(sitePremiumUntil)="data">
          <div style="text-align: center">{{ formatDate(data.value) }}</div>
        </template>

        <template #cell(personalDiscountPercent)="data">
          <div style="text-align: center">{{ data.value > 0 ? `${data.value}%` : '' }}</div>
        </template>

        <template #cell(maxFavorites)="data">
          <div style="text-align: center">{{ data.value > 0 ? data.value : '—' }}</div>
        </template>

        <template #cell(maxPlaylists)="data">
          <div style="text-align: center">{{ data.value > 0 ? data.value : '—' }}</div>
        </template>

        <template #cell(maxPlaylistItems)="data">
          <div style="text-align: center">{{ data.value > 0 ? data.value : '—' }}</div>
        </template>
      </b-table>
    </div>

    <div class="sut-table-footer">
      <span class="sut-count">Всего: {{ countRows }}</span>
    </div>
  </div>
</template>

<script>
import { BPagination, BSpinner, BTable } from 'bootstrap-vue-next'
import SiteUserEditModal from "./edit/SiteUserEditModal.vue";
import SiteUsersFilterModal from "./filter/SiteUsersFilterModal.vue";

export default {
  name: "SiteUsersTable",
  components: { SiteUserEditModal, SiteUsersFilterModal, BPagination, BSpinner, BTable },
  data() {
    return {
      perPage: 19,
      // Восстанавливаем последнюю страницу из store, чтобы при уходе с компонента и возврате таблица
      // открывалась на той же странице.
      currentPage: this.$store.getters.getSiteUsersTableCurrentPage || 1,
      sortBy: [],
      isUserEditVisible: false,
      isFilterVisible: false,
      isBusy: false,
    }
  },
  watch: {
    siteUsersDigestIsLoading: {
      handler() { this.isBusy = this.siteUsersDigestIsLoading }
    },
    currentPage: {
      handler(newPage) {
        // Сохраняем страницу в store, чтобы она восстановилась после переключения на другой компонент.
        this.$store.commit('setSiteUsersTableCurrentPage', newPage);
      }
    }
  },
  computed: {
    siteUsersDigestIsLoading() { return this.$store.getters.getSiteUsersDigestIsLoading },
    siteUsersDigest() { return this.$store.getters.getSiteUsersDigest },
    countRows() { return this.siteUsersDigest ? this.siteUsersDigest.length : 0 },
    target: {
      get() { return this.$store.getters.getSiteUsersTarget },
      set(value) { this.$store.dispatch('setSiteUsersTarget', value) }
    },
    activeFilterCount() {
      return [
        this.$store.getters.getSiteUsersFilterId,
        this.$store.getters.getSiteUsersFilterEmail,
        this.$store.getters.getSiteUsersFilterDisplayName,
        this.$store.getters.getSiteUsersFilterSponsrUid,
        this.$store.getters.getSiteUsersFilterIsPremium,
        this.$store.getters.getSiteUsersFilterIsPermanentPremium,
        this.$store.getters.getSiteUsersFilterIsEffectivePremium,
        this.$store.getters.getSiteUsersFilterIsEditor,
        this.$store.getters.getSiteUsersFilterIsBanned,
      ].filter(v => v !== '' && v !== undefined && v !== null).length;
    },
    siteUserDigestFields() {
      return [
        { key: 'id', label: 'ID', sortable: true, style: { minWidth: '50px', maxWidth: '50px', textAlign: 'center', fontSize: 'small' } },
        { key: 'effectivePremium', label: 'Активный премиум', sortable: true, style: { minWidth: '90px', maxWidth: '90px', textAlign: 'center', fontSize: 'small' } },
        { key: 'email', label: 'Email', sortable: true, style: { minWidth: '260px', maxWidth: '260px', textAlign: 'left', fontSize: 'small' } },
        { key: 'displayName', label: 'Имя', sortable: true, style: { minWidth: '200px', maxWidth: '200px', textAlign: 'left', fontSize: 'small' } },
        { key: 'sponsrUid', label: 'Sponsr UID', sortable: true, style: { minWidth: '110px', maxWidth: '110px', textAlign: 'center', fontSize: 'small' } },
        { key: 'premium', label: 'Премиум', sortable: true, style: { minWidth: '90px', maxWidth: '90px', textAlign: 'center', fontSize: 'small' } },
        { key: 'permanentPremium', label: 'Постоянный премиум', sortable: true, style: { minWidth: '90px', maxWidth: '90px', textAlign: 'center', fontSize: 'small' } },
        { key: 'sponsrPremiumUntil', label: 'Sponsr до', sortable: true, style: { minWidth: '110px', maxWidth: '110px', textAlign: 'center', fontSize: 'small' } },
        { key: 'sitePremiumUntil', label: 'Премиум сайта до', sortable: true, style: { minWidth: '110px', maxWidth: '110px', textAlign: 'center', fontSize: 'small' } },
        { key: 'personalDiscountPercent', label: 'Скидка', sortable: true, style: { minWidth: '70px', maxWidth: '70px', textAlign: 'center', fontSize: 'small' } },
        { key: 'editor', label: 'Редактор', sortable: true, style: { minWidth: '80px', maxWidth: '80px', textAlign: 'center', fontSize: 'small' } },
        { key: 'banned', label: 'Статус', sortable: true, style: { minWidth: '100px', maxWidth: '100px', textAlign: 'center', fontSize: 'small' } },
        { key: 'banReason', label: 'Причина бана', sortable: true, style: { minWidth: '160px', maxWidth: '160px', textAlign: 'left', fontSize: 'small' } },
        { key: 'maxFavorites', label: 'Лимит Избр.', sortable: true, style: { minWidth: '70px', maxWidth: '70px', textAlign: 'center', fontSize: 'small' } },
        { key: 'maxPlaylists', label: 'Лимит Плейл.', sortable: true, style: { minWidth: '70px', maxWidth: '70px', textAlign: 'center', fontSize: 'small' } },
        { key: 'maxPlaylistItems', label: 'Лимит Тр/Пл', sortable: true, style: { minWidth: '70px', maxWidth: '70px', textAlign: 'center', fontSize: 'small' } },
        { key: 'createdAt', label: 'Регистрация', sortable: true, style: { minWidth: '160px', maxWidth: '160px', textAlign: 'center', fontSize: 'small' } },
        { key: 'lastLoginAt', label: 'Последний вход', sortable: true, style: { minWidth: '160px', maxWidth: '160px', textAlign: 'center', fontSize: 'small' } },
      ]
    }
  },
  async mounted() {
    await this.$store.dispatch('hydrateSiteUsersFilter');
    this.reload();
  },
  methods: {
    reload() {
      const params = {};
      if (this.$store.getters.getSiteUsersFilterId) params.filterId = this.$store.getters.getSiteUsersFilterId;
      if (this.$store.getters.getSiteUsersFilterEmail) params.filterEmail = this.$store.getters.getSiteUsersFilterEmail;
      if (this.$store.getters.getSiteUsersFilterDisplayName) params.filterDisplayName = this.$store.getters.getSiteUsersFilterDisplayName;
      if (this.$store.getters.getSiteUsersFilterSponsrUid) params.filterSponsrUid = this.$store.getters.getSiteUsersFilterSponsrUid;
      if (this.$store.getters.getSiteUsersFilterIsPremium) params.filterIsPremium = this.$store.getters.getSiteUsersFilterIsPremium;
      if (this.$store.getters.getSiteUsersFilterIsPermanentPremium) params.filterIsPermanentPremium = this.$store.getters.getSiteUsersFilterIsPermanentPremium;
      if (this.$store.getters.getSiteUsersFilterIsEffectivePremium) params.filterIsEffectivePremium = this.$store.getters.getSiteUsersFilterIsEffectivePremium;
      if (this.$store.getters.getSiteUsersFilterIsEditor) params.filterIsEditor = this.$store.getters.getSiteUsersFilterIsEditor;
      if (this.$store.getters.getSiteUsersFilterIsBanned) params.filterIsBanned = this.$store.getters.getSiteUsersFilterIsBanned;
      this.$store.dispatch('loadSiteUsersDigest', params);
    },
    onTargetChange() {
      this.currentPage = 1;
      this.reload();
    },
    closeFilter() {
      this.isFilterVisible = false;
    },
    editUser(id, user) {
      this.$store.commit('setSiteUserCurrentId', id);
      this.$store.commit('setSiteUserCurrent', user);
      this.isUserEditVisible = true;
    },
    closeUserEdit() {
      this.isUserEditVisible = false;
      this.reload();
    },
    onRowClicked(item) {
      this.editUser(item.id, item);
    },
    formatDate(ts) {
      if (!ts) return '—';
      return new Date(ts.replace(' ', 'T')).toLocaleString('ru-RU', {
        timeZone: 'Europe/Moscow', day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit'
      });
    }
  }
}
</script>

<style scoped>
.sut-table {
  padding: 0;
  margin: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  font-family: Avenir, Helvetica, Arial, sans-serif;
}
.sut-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  font-size: small;
}
.sut-toolbar-item { font-size: small; }
.sut-btn {
  border: solid 1px black;
  border-radius: 6px;
  padding: 4px 10px;
  background-color: antiquewhite;
  cursor: pointer;
}
.sut-btn:hover { background-color: lightpink; }
.sut-table-header { width: fit-content; }
.sut-table-body { width: fit-content; max-width: 100%; overflow-x: auto; }
.sut-table-body :deep(th) { position: relative; }
.sut-table-body :deep(th svg.bi) {
  position: absolute;
  right: 2px;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0 !important;
  transition: opacity 0.15s ease;
  pointer-events: none;
}
.sut-table-body :deep(th:hover svg.bi) { opacity: 0.6 !important; }
.sut-table-footer { margin-top: 6px; font-size: small; color: gray; }
.fld-email {
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.fld-email:hover { text-decoration: underline; cursor: pointer; }
.fld-ellipsis {
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
