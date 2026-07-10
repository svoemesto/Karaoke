<template>
  <div class="sue-root">
    <div v-if="siteUserCurrent">
      <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
      <UserEventsModal
          v-if="isEventsVisible"
          :user="{ siteUserId: siteUserCurrent.id, displayName: siteUserCurrent.displayName, email: siteUserCurrent.email }"
          :events="userEvents"
          :total-count="userEventsTotalCount"
          :is-loading="userEventsIsLoading"
          :cap="2000"
          @close="isEventsVisible=false"
      />
      <UserPlaylistsModal
          v-if="isPlaylistsVisible"
          :site-user-id="siteUserCurrent.id"
          :user-label="siteUserCurrent.displayName || siteUserCurrent.email"
          :target="siteUsersTarget"
          @close="isPlaylistsVisible=false"
      />
      <UserSubscriptionsModal
          v-if="isSubscriptionsVisible"
          :site-user-id="siteUserCurrent.id"
          :user-label="siteUserCurrent.displayName || siteUserCurrent.email"
          :target="siteUsersTarget"
          @close="isSubscriptionsVisible=false"
      />
      <div class="sue-header">
        <div>ID = {{ siteUserCurrent.id }}</div>
        <div>{{ siteUserCurrent.email }}</div>
      </div>
      <div class="sue-body">
        <div class="label-and-input">
          <div class="label">Имя:</div>
          <input class="input-field" v-model="siteUserCurrent.displayName">
        </div>
        <div class="label-and-input">
          <div class="label">Sponsr UID:</div>
          <input class="input-field" v-model="siteUserCurrent.sponsrUid">
        </div>
        <div class="label-and-input">
          <div class="label">Премиум:</div>
          <label class="sue-checkbox-label">
            <input type="checkbox" v-model="siteUserCurrent.premium">
            <span class="sue-hint">(вручную, до реализации автоматической Sponsr-сверки)</span>
          </label>
        </div>
        <div class="label-and-input">
          <div class="label">Постоянный премиум:</div>
          <label class="sue-checkbox-label">
            <input type="checkbox" v-model="siteUserCurrent.permanentPremium">
            <span class="sue-hint">(если включено — премиум действует всегда, независимо от чекбокса выше)</span>
          </label>
        </div>
        <div class="label-and-input">
          <div class="label">Премиум по Sponsr до:</div>
          <input class="input-field sue-datetime" type="datetime-local" v-model="sponsrPremiumUntilLocal">
          <button v-if="sponsrPremiumUntilLocal" class="sue-clear-btn" title="Очистить" @click="sponsrPremiumUntilLocal=''">×</button>
          <span class="sue-hint">(в норме — Sponsr-синхронизация; можно выдать/отозвать вручную)</span>
        </div>
        <div class="label-and-input">
          <div class="label">Подписка на сайт до:</div>
          <input class="input-field sue-datetime" type="datetime-local" v-model="sitePremiumUntilLocal">
          <button v-if="sitePremiumUntilLocal" class="sue-clear-btn" title="Очистить" @click="sitePremiumUntilLocal=''">×</button>
          <span class="sue-hint">(в норме — оплаченная подписка на сайте; можно выдать/отозвать вручную)</span>
        </div>
        <div class="label-and-input">
          <div class="label">Приветствие отправлено:</div>
          <label class="sue-checkbox-label">
            <input type="checkbox" v-model="siteUserCurrent.welcomeMessageSent">
            <span class="sue-hint">(разовая отправка при первом премиуме; сброс — отправить повторно при следующем)</span>
          </label>
        </div>
        <div class="label-and-input">
          <div class="label">Постоянная скидка:</div>
          <input class="input-field sue-num" type="number" min="0" max="100" step="0.01" v-model.number="siteUserCurrent.personalDiscountPercent">
          <span class="sue-hint">%, суммируется поверх любой акции; 0 = нет скидки</span>
        </div>
        <div class="label-and-input">
          <div class="label">Редактор караоке:</div>
          <label class="sue-checkbox-label">
            <input type="checkbox" v-model="siteUserCurrent.editor">
            <span class="sue-hint">(доступ к онлайн-редактору разметки на публичном сайте)</span>
          </label>
        </div>
        <div class="label-and-input">
          <div class="label">Лимит избранного:</div>
          <input class="input-field sue-num" type="number" min="0" v-model.number="siteUserCurrent.maxFavorites">
          <span class="sue-hint">0 = дефолт (100)</span>
        </div>
        <div class="label-and-input">
          <div class="label">Лимит плейлистов:</div>
          <input class="input-field sue-num" type="number" min="0" v-model.number="siteUserCurrent.maxPlaylists">
          <span class="sue-hint">0 = дефолт (50)</span>
        </div>
        <div class="label-and-input">
          <div class="label">Лимит песен в плейлисте:</div>
          <input class="input-field sue-num" type="number" min="0" v-model.number="siteUserCurrent.maxPlaylistItems">
          <span class="sue-hint">0 = дефолт (500)</span>
        </div>
        <div class="label-and-input">
          <div class="label">Статус:</div>
          <div class="sue-static" :style="{ color: siteUserCurrent.banned ? 'darkred' : 'darkgreen' }">
            {{ siteUserCurrent.banned ? `Забанен: ${siteUserCurrent.banReason}` : 'Активен' }}
          </div>
        </div>
        <div class="label-and-input">
          <div class="label">Создан:</div>
          <input class="input-field sue-datetime" type="datetime-local" v-model="createdAtLocal">
        </div>
        <div class="label-and-input">
          <div class="label">Последний вход:</div>
          <input class="input-field sue-datetime" type="datetime-local" v-model="lastLoginAtLocal">
        </div>
      </div>
      <div class="sue-footer">
        <button class="sue-btn" @click="save" :disabled="notChanged()">Сохранить</button>
        <button v-if="!siteUserCurrent.banned" class="sue-btn sue-btn-danger" @click="ban">Забанить</button>
        <button v-else class="sue-btn" @click="unban">Разбанить</button>
        <button class="sue-btn sue-btn-danger" @click="deleteUser">Удалить</button>
        <span class="sue-footer-spacer"></span>
        <button class="sue-btn" @click="openChat">Чат</button>
        <button class="sue-btn" @click="openEvents">События</button>
        <button class="sue-btn" @click="openPlaylists">Плейлисты</button>
        <button class="sue-btn" @click="openSubscriptions">Подписки/покупки</button>
      </div>
    </div>
    <div v-else>Не выбран пользователь</div>
  </div>
</template>

<script>
import CustomConfirm from "../../Common/CustomConfirm.vue";
import UserEventsModal from "../../Stats/UserEventsModal.vue";
import UserPlaylistsModal from "../UserPlaylistsModal.vue";
import UserSubscriptionsModal from "../UserSubscriptionsModal.vue";

export default {
  name: "SiteUserEdit",
  components: { CustomConfirm, UserEventsModal, UserPlaylistsModal, UserSubscriptionsModal },
  data() {
    return {
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
      isEventsVisible: false,
      isPlaylistsVisible: false,
      isSubscriptionsVisible: false,
    }
  },
  mounted() {
    let item = this.$store.getters.getSiteUserCurrent;
    this.$store.dispatch('setSiteUserCurrent', item);
    this.$store.dispatch('setSiteUserSnapshot', item);
  },
  computed: {
    siteUserCurrent() { return this.$store.getters.getSiteUserCurrent },
    siteUserSnapshot() { return this.$store.getters.getSiteUserSnapshot },
    siteUserDiff() { return this.$store.getters.getSiteUserDiff },
    siteUsersTarget() { return this.$store.getters.getSiteUsersTarget },
    userEvents() { return this.$store.getters.getStatsUserEvents },
    userEventsTotalCount() { return this.$store.getters.getStatsUserEventsTotalCount },
    userEventsIsLoading() { return this.$store.getters.getStatsUserEventsIsLoading },
    // sponsrPremiumUntil/sitePremiumUntil — nullable-колонки, очистка поля (пустой <input>) должна
    // дойти до бэкенда как null. createdAt/lastLoginAt — NOT NULL, поэтому очистка там просто
    // отменяется (оставляем прежнее значение) вместо отправки мусорного значения на сервер.
    sponsrPremiumUntilLocal: {
      get() { return this.toLocalInput(this.siteUserCurrent.sponsrPremiumUntil) },
      set(value) { this.siteUserCurrent.sponsrPremiumUntil = this.fromLocalInputNullable(value) }
    },
    sitePremiumUntilLocal: {
      get() { return this.toLocalInput(this.siteUserCurrent.sitePremiumUntil) },
      set(value) { this.siteUserCurrent.sitePremiumUntil = this.fromLocalInputNullable(value) }
    },
    createdAtLocal: {
      get() { return this.toLocalInput(this.siteUserCurrent.createdAt) },
      set(value) { this.siteUserCurrent.createdAt = this.fromLocalInputRequired(value, this.siteUserCurrent.createdAt) }
    },
    lastLoginAtLocal: {
      get() { return this.toLocalInput(this.siteUserCurrent.lastLoginAt) },
      set(value) { this.siteUserCurrent.lastLoginAt = this.fromLocalInputRequired(value, this.siteUserCurrent.lastLoginAt) }
    },
  },
  methods: {
    closeCustomConfirm() { this.isCustomConfirmVisible = false },
    // "yyyy-MM-dd HH:mm:ss[.f...]" (JDBC Timestamp.toString()) <-> "yyyy-MM-ddTHH:mm" (<input
    // type="datetime-local">) — секунды/доли всегда обнуляются при редактировании через это поле.
    toLocalInput(ts) {
      if (!ts) return '';
      return ts.replace(' ', 'T').slice(0, 16);
    },
    fromLocalInputNullable(value) {
      return value ? `${value.replace('T', ' ')}:00` : null;
    },
    fromLocalInputRequired(value, previous) {
      return value ? `${value.replace('T', ' ')}:00` : previous;
    },
    // Открывает раздел «Чат» (webvue3) уже на переписке с этим пользователем — target чата
    // подстраивается под текущий target раздела «Пользователи сайта» (local/remote), иначе id
    // пользователя из одной БД мог бы открыть чужую/несуществующую переписку в другой.
    async openChat() {
      await this.$store.dispatch('setChatTarget', this.siteUsersTarget);
      await this.$store.dispatch('openChatThread', this.siteUserCurrent.id);
      this.$emit('close');
      this.$router.push({ name: 'chat' });
    },
    async openEvents() {
      await this.$store.dispatch('setStatsTarget', this.siteUsersTarget);
      this.$store.dispatch('loadStatsUserEvents', { siteUserId: this.siteUserCurrent.id, page: 1, pageSize: 2000 });
      this.isEventsVisible = true;
    },
    openPlaylists() { this.isPlaylistsVisible = true },
    openSubscriptions() { this.isSubscriptionsVisible = true },
    notChanged() { return this.siteUserDiff.length === 0 },
    async save() {
      let diffs = {};
      for (let diff of this.siteUserDiff) diffs[diff.name] = diff.new;
      await this.$store.dispatch('saveSiteUser', diffs);
    },
    async refreshCurrentFromDigest() {
      const id = this.siteUserCurrent.id;
      const fresh = this.$store.getters.getSiteUsersDigest.find(u => u.id === id);
      if (fresh) {
        this.$store.dispatch('setSiteUserCurrent', fresh);
        this.$store.dispatch('setSiteUserSnapshot', fresh);
      }
    },
    ban() {
      let item = { reason: '' };
      this.customConfirmParams = {
        header: 'Подтвердите бан пользователя',
        body: `Забанить пользователя <strong>«${this.siteUserCurrent.email}»</strong>?`,
        callback: this.doBan,
        fields: [
          {
            fldName: 'reason',
            fldLabel: 'Причина:',
            fldValue: item.reason,
            fldLabelStyle: { width: '150px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px' }
          }
        ]
      };
      this.isCustomConfirmVisible = true;
    },
    async doBan(item) {
      await this.$store.dispatch('banSiteUserCurrent', item.reason);
      await this.refreshCurrentFromDigest();
      this.isCustomConfirmVisible = false;
    },
    unban() {
      this.customConfirmParams = {
        header: 'Подтвердите разбан пользователя',
        body: `Снять бан с пользователя <strong>«${this.siteUserCurrent.email}»</strong>?`,
        callback: this.doUnban,
      };
      this.isCustomConfirmVisible = true;
    },
    async doUnban() {
      await this.$store.dispatch('unbanSiteUserCurrent');
      await this.refreshCurrentFromDigest();
      this.isCustomConfirmVisible = false;
    },
    deleteUser() {
      this.customConfirmParams = {
        header: 'Подтвердите удаление пользователя',
        body: `Удалить пользователя <strong>«${this.siteUserCurrent.email}»</strong>? Это действие необратимо.`,
        timeout: 10,
        callback: this.doDeleteUser,
      };
      this.isCustomConfirmVisible = true;
    },
    async doDeleteUser() {
      await this.$store.dispatch('deleteSiteUserCurrent');
      this.$emit('close');
    }
  }
}
</script>

<style scoped>
.sue-root {
  padding: 0;
  margin: 0;
  font-family: Avenir, Helvetica, Arial, sans-serif;
}
.sue-header {
  display: flex;
  justify-content: space-between;
  border: thin dashed darkgray;
  border-radius: 10px;
  padding: 5px 10px;
  font-size: small;
}
.sue-body { margin: 10px 0; display: flex; flex-direction: column; }
.label-and-input { display: flex; align-items: center; margin-bottom: 4px; }
.label { font-size: small; text-align: right; width: 130px; padding-right: 6px; }
.input-field { padding: 2px 5px; width: 300px; font-size: small; border-radius: 5px; border: thin solid black; }
.sue-num { width: 90px; margin-right: 8px; }
.sue-datetime { width: 210px; margin-right: 8px; }
.sue-clear-btn {
  border: thin solid black;
  border-radius: 50%;
  width: 20px;
  height: 20px;
  font-size: x-small;
  line-height: 1;
  cursor: pointer;
  margin-right: 8px;
  background-color: antiquewhite;
}
.sue-clear-btn:hover { background-color: lightpink; }
.sue-static { font-size: small; }
.sue-hint { color: gray; font-size: x-small; }
.sue-checkbox-label { display: flex; align-items: center; gap: 6px; font-size: small; cursor: pointer; }
.sue-footer { display: flex; align-items: center; gap: 6px; border: thin dashed darkgray; border-radius: 10px; padding: 8px; }
.sue-footer-spacer { flex: 1; }
.sue-btn {
  border: solid 1px black;
  border-radius: 6px;
  padding: 6px 14px;
  background-color: antiquewhite;
  cursor: pointer;
}
.sue-btn:hover { background-color: lightpink; }
.sue-btn[disabled] { background-color: lightgray; cursor: default; }
.sue-btn-danger { background-color: #f4b6b6; }
.sue-btn-danger:hover { background-color: #e08a8a; }
</style>
