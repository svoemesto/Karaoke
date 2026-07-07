<template>
  <div class="sue-root">
    <div v-if="siteUserCurrent">
      <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
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
          <div class="sue-static">{{ siteUserCurrent.createdAt }}</div>
        </div>
        <div class="label-and-input">
          <div class="label">Последний вход:</div>
          <div class="sue-static">{{ siteUserCurrent.lastLoginAt }}</div>
        </div>
      </div>
      <div class="sue-footer">
        <button class="sue-btn" @click="save" :disabled="notChanged()">Сохранить</button>
        <button v-if="!siteUserCurrent.banned" class="sue-btn sue-btn-danger" @click="ban">Забанить</button>
        <button v-else class="sue-btn" @click="unban">Разбанить</button>
        <button class="sue-btn sue-btn-danger" @click="deleteUser">Удалить</button>
      </div>
    </div>
    <div v-else>Не выбран пользователь</div>
  </div>
</template>

<script>
import CustomConfirm from "../../Common/CustomConfirm.vue";

export default {
  name: "SiteUserEdit",
  components: { CustomConfirm },
  data() {
    return {
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
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
  },
  methods: {
    closeCustomConfirm() { this.isCustomConfirmVisible = false },
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
.sue-static { font-size: small; }
.sue-hint { color: gray; font-size: x-small; }
.sue-checkbox-label { display: flex; align-items: center; gap: 6px; font-size: small; cursor: pointer; }
.sue-footer { display: flex; gap: 6px; border: thin dashed darkgray; border-radius: 10px; padding: 8px; }
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
