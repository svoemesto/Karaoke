<template>
  <transition name="modal-fade">
    <div class="sufm-modal-backdrop">
      <div class="sufm-area">

        <div class="sufm-area-modal-header">
          Фильтр пользователей сайта
        </div>

        <div class="sufm-area-modal-body">
          <div class="sufm-root-wrapper">

            <div class="sufm-filter-row">
              <div class="sufm-row-label"><div v-text="'ID:'"/></div>
              <div class="sufm-row-input">
                <input v-model="siteUsersFilterId" class="sufm-input-field"/>
              </div>
              <button :disabled="!siteUsersFilterId" class="sufm-button-clear-field" @click.left="siteUsersFilterId=''" v-text="'X'"/>
            </div>

            <div class="sufm-filter-row">
              <div class="sufm-row-label"><div v-text="'Email:'"/></div>
              <div class="sufm-row-input">
                <input v-model="siteUsersFilterEmail" class="sufm-input-field"/>
              </div>
              <button :disabled="!siteUsersFilterEmail" class="sufm-button-clear-field" @click.left="siteUsersFilterEmail=''" v-text="'X'"/>
            </div>

            <div class="sufm-filter-row">
              <div class="sufm-row-label"><div v-text="'Имя:'"/></div>
              <div class="sufm-row-input">
                <input v-model="siteUsersFilterDisplayName" class="sufm-input-field"/>
              </div>
              <button :disabled="!siteUsersFilterDisplayName" class="sufm-button-clear-field" @click.left="siteUsersFilterDisplayName=''" v-text="'X'"/>
            </div>

            <div class="sufm-filter-row">
              <div class="sufm-row-label"><div v-text="'Sponsr UID:'"/></div>
              <div class="sufm-row-input">
                <input v-model="siteUsersFilterSponsrUid" class="sufm-input-field"/>
              </div>
              <button :disabled="!siteUsersFilterSponsrUid" class="sufm-button-clear-field" @click.left="siteUsersFilterSponsrUid=''" v-text="'X'"/>
            </div>

            <div class="sufm-filter-row">
              <div class="sufm-row-label"><div v-text="'Премиум:'"/></div>
              <div class="sufm-row-input">
                <select v-model="siteUsersFilterIsPremium" class="sufm-input-field">
                  <option value="">Все</option>
                  <option value="+">Да</option>
                  <option value="-">Нет</option>
                </select>
              </div>
              <button :disabled="!siteUsersFilterIsPremium" class="sufm-button-clear-field" @click.left="siteUsersFilterIsPremium=''" v-text="'X'"/>
            </div>

            <div class="sufm-filter-row">
              <div class="sufm-row-label"><div v-text="'Постоянный премиум:'"/></div>
              <div class="sufm-row-input">
                <select v-model="siteUsersFilterIsPermanentPremium" class="sufm-input-field">
                  <option value="">Все</option>
                  <option value="+">Да</option>
                  <option value="-">Нет</option>
                </select>
              </div>
              <button :disabled="!siteUsersFilterIsPermanentPremium" class="sufm-button-clear-field" @click.left="siteUsersFilterIsPermanentPremium=''" v-text="'X'"/>
            </div>

            <div class="sufm-filter-row">
              <div class="sufm-row-label"><div v-text="'Активный премиум:'"/></div>
              <div class="sufm-row-input">
                <select v-model="siteUsersFilterIsEffectivePremium" class="sufm-input-field">
                  <option value="">Все</option>
                  <option value="+">Да</option>
                  <option value="-">Нет</option>
                </select>
              </div>
              <button :disabled="!siteUsersFilterIsEffectivePremium" class="sufm-button-clear-field" @click.left="siteUsersFilterIsEffectivePremium=''" v-text="'X'"/>
            </div>

            <div class="sufm-filter-row">
              <div class="sufm-row-label"><div v-text="'Редактор:'"/></div>
              <div class="sufm-row-input">
                <select v-model="siteUsersFilterIsEditor" class="sufm-input-field">
                  <option value="">Все</option>
                  <option value="+">Да</option>
                  <option value="-">Нет</option>
                </select>
              </div>
              <button :disabled="!siteUsersFilterIsEditor" class="sufm-button-clear-field" @click.left="siteUsersFilterIsEditor=''" v-text="'X'"/>
            </div>

            <div class="sufm-filter-row">
              <div class="sufm-row-label"><div v-text="'Статус:'"/></div>
              <div class="sufm-row-input">
                <select v-model="siteUsersFilterIsBanned" class="sufm-input-field">
                  <option value="">Все</option>
                  <option value="+">Забанен</option>
                  <option value="-">Активен</option>
                </select>
              </div>
              <button :disabled="!siteUsersFilterIsBanned" class="sufm-button-clear-field" @click.left="siteUsersFilterIsBanned=''" v-text="'X'"/>
            </div>

          </div>
        </div>

        <div class="sufm-area-modal-footer">
          <button type="button" class="sufm-btn-close" @click="ok">Применить фильтр</button>
          <button type="button" class="sufm-btn-close" @click="cancel">Отмена</button>
        </div>

      </div>
    </div>
  </transition>
</template>

<script>

export default {
  name: "SiteUsersFilterModal",
  computed: {
    siteUsersFilterId: {
      get() { return this.$store.getters.getSiteUsersFilterId; },
      set(value) { this.$store.dispatch('setSiteUsersFilterId', { value: value }); }
    },
    siteUsersFilterEmail: {
      get() { return this.$store.getters.getSiteUsersFilterEmail; },
      set(value) { this.$store.dispatch('setSiteUsersFilterEmail', { value: value }); }
    },
    siteUsersFilterDisplayName: {
      get() { return this.$store.getters.getSiteUsersFilterDisplayName; },
      set(value) { this.$store.dispatch('setSiteUsersFilterDisplayName', { value: value }); }
    },
    siteUsersFilterSponsrUid: {
      get() { return this.$store.getters.getSiteUsersFilterSponsrUid; },
      set(value) { this.$store.dispatch('setSiteUsersFilterSponsrUid', { value: value }); }
    },
    siteUsersFilterIsPremium: {
      get() { return this.$store.getters.getSiteUsersFilterIsPremium; },
      set(value) { this.$store.dispatch('setSiteUsersFilterIsPremium', { value: value }); }
    },
    siteUsersFilterIsPermanentPremium: {
      get() { return this.$store.getters.getSiteUsersFilterIsPermanentPremium; },
      set(value) { this.$store.dispatch('setSiteUsersFilterIsPermanentPremium', { value: value }); }
    },
    siteUsersFilterIsEffectivePremium: {
      get() { return this.$store.getters.getSiteUsersFilterIsEffectivePremium; },
      set(value) { this.$store.dispatch('setSiteUsersFilterIsEffectivePremium', { value: value }); }
    },
    siteUsersFilterIsEditor: {
      get() { return this.$store.getters.getSiteUsersFilterIsEditor; },
      set(value) { this.$store.dispatch('setSiteUsersFilterIsEditor', { value: value }); }
    },
    siteUsersFilterIsBanned: {
      get() { return this.$store.getters.getSiteUsersFilterIsBanned; },
      set(value) { this.$store.dispatch('setSiteUsersFilterIsBanned', { value: value }); }
    },
  },
  async beforeMount() {
    // SiteUsersTable уже вызывает hydrateSiteUsersFilter на своём mounted() — здесь только
    // подстраховка (модалка может быть открыта уже после того, как таблица закешировала значения,
    // повторный вызов идемпотентен).
    await this.$store.dispatch('hydrateSiteUsersFilter');
  },
  methods: {
    ok() {
      let params = {};
      if (this.siteUsersFilterId) params.filterId = this.siteUsersFilterId;
      if (this.siteUsersFilterEmail) params.filterEmail = this.siteUsersFilterEmail;
      if (this.siteUsersFilterDisplayName) params.filterDisplayName = this.siteUsersFilterDisplayName;
      if (this.siteUsersFilterSponsrUid) params.filterSponsrUid = this.siteUsersFilterSponsrUid;
      if (this.siteUsersFilterIsPremium) params.filterIsPremium = this.siteUsersFilterIsPremium;
      if (this.siteUsersFilterIsPermanentPremium) params.filterIsPermanentPremium = this.siteUsersFilterIsPermanentPremium;
      if (this.siteUsersFilterIsEffectivePremium) params.filterIsEffectivePremium = this.siteUsersFilterIsEffectivePremium;
      if (this.siteUsersFilterIsEditor) params.filterIsEditor = this.siteUsersFilterIsEditor;
      if (this.siteUsersFilterIsBanned) params.filterIsBanned = this.siteUsersFilterIsBanned;
      this.$store.dispatch('loadSiteUsersDigest', params);

      this.$emit('close');
    },
    cancel() {
      this.$emit('close');
    }
  }
}
</script>

<style scoped>

.sufm-modal-fade-enter,
.sufm-modal-fade-leave-active { opacity: 0; }

.sufm-modal-fade-enter-active,
.sufm-modal-fade-leave-active { transition: opacity .5s ease }

.sufm-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.sufm-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}

.sufm-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: center;
}

.sufm-modal-backdrop {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: rgba(0, 0, 0, 0.3);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1055;
}

.sufm-area {
  background: #FFFFFF;
  box-shadow: 2px 2px 20px 1px;
  overflow-x: auto;
  display: flex;
  flex-direction: column;
  width: auto;
  height: auto;
  position: relative;
  max-width: calc(100vw - 20px);
  max-height: calc(100vh - 20px);
}

.sufm-btn-close {
  border: 1px solid white;
  border-radius: 10px;
  cursor: pointer;
  font-weight: bold;
  color: white;
  background: transparent;
  width: 150px;
  height: auto;
  font-size: small;
}

.sufm-root-wrapper {
  display: flex;
  flex-direction: column;
}
.sufm-button-clear-field {
  border: thin solid black;
  border-radius: 50%;
  font-size: x-small;
  height: 20px;
  width: 20px;
  margin-top: -4px;
  margin-left: -10px;
}
.sufm-filter-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.sufm-row-label {
  min-width: 170px;
  max-width: 170px;
  text-align: right;
  padding: 0 3px;
  font-size: small;
}
.sufm-row-input {
  display: block;
  padding-bottom: 3px;
  width: 200px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}

/* Ширина/паддинг заданы явно на общем классе (см. инвариант в DEVELOPMENT.md — select/input width
   matching): поле сужено на 18px под сдвиг круглой кнопки очистки, как в AuthorsFilterModal. */
.sufm-input-field {
  box-sizing: border-box;
  border: 1px solid #767676;
  border-radius: 5px;
  padding: 1px 4px;
  font: inherit;
  background-color: white;
  width: calc(100% - 18px);
}

.sufm-input-field:hover {
  background-color: lightyellow;
}
.sufm-input-field:focus {
  background-color: cyan;
}

select.sufm-input-field {
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  cursor: pointer;
}

</style>
