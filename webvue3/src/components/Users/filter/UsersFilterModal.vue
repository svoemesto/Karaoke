<template>
  <transition name="modal-fade">
    <div class="ufm-modal-backdrop">
      <div class="ufm-area">

        <div class="ufm-area-modal-header">
          Фильтр для пользователей
        </div>

        <div class="ufm-area-modal-body">
          <div class="ufm-root-wrapper">

            <div class="ufm-filter-row">
              <div class="ufm-row-label">
                <div v-text="'ID:'"></div>
              </div>
              <div class="ufm-row-input">
                <input class="ufm-input-field" v-model="usersFilterId">
              </div>
              <button :disabled="!usersFilterId" class="ufm-button-clear-field" @click.left="usersFilterId=''" v-text="'X'"></button>
            </div>

            <div class="ufm-filter-row">
              <div class="ufm-row-label">
                <div v-text="'Login:'"></div>
              </div>
              <div class="ufm-row-input">
                <input class="ufm-input-field" v-model="usersFilterLogin">
              </div>
              <button :disabled="!usersFilterLogin" class="ufm-button-clear-field" @click.left="usersFilterLogin=''" v-text="'X'"></button>
            </div>

            <div class="ufm-filter-row">
              <div class="ufm-row-label">
                <div v-text="'Email:'"></div>
              </div>
              <div class="ufm-row-input">
                <input class="ufm-input-field" v-model="usersFilterEmail">
              </div>
              <button :disabled="!usersFilterEmail" class="ufm-button-clear-field" @click.left="usersFilterEmail=''" v-text="'X'"></button>
            </div>

            <div class="ufm-filter-row">
              <div class="ufm-row-label">
                <div v-text="'Имя:'"></div>
              </div>
              <div class="ufm-row-input">
                <input class="ufm-input-field" v-model="usersFilterFirstName">
              </div>
              <button :disabled="!usersFilterFirstName" class="ufm-button-clear-field" @click.left="usersFilterFirstName=''" v-text="'X'"></button>
            </div>

            <div class="ufm-filter-row">
              <div class="ufm-row-label">
                <div v-text="'Фамилия:'"></div>
              </div>
              <div class="ufm-row-input">
                <input class="ufm-input-field" v-model="usersFilterLastName">
              </div>
              <button :disabled="!usersFilterLastName" class="ufm-button-clear-field" @click.left="usersFilterLastName=''" v-text="'X'"></button>
            </div>
            
            <div class="ufm-filter-row">
              <div class="ufm-row-label">
                <div v-text="'Группы:'"></div>
              </div>
              <div class="ufm-row-input">
                <input class="ufm-input-field" v-model="usersFilterGroups">
              </div>
              <button :disabled="!usersFilterGroups" class="ufm-button-clear-field" @click.left="usersFilterGroups=''" v-text="'X'"></button>
            </div>

          </div>
        </div>

        <div class="ufm-area-modal-footer">
          <button type="button" class="ufm-btn-close" @click="ok">Применить фильтр</button>
          <button type="button" class="ufm-btn-close" @click="cancel">Отмена</button>
        </div>

      </div>
    </div>
  </transition>
</template>

<script>

export default {
  name: "UsersFilterModal",
  async beforeMount() {
    this.$store.dispatch('setUsersFilterId', { value: await this.$store.getters.getWebvueProp('usersFilterId', '') });
    this.$store.dispatch('setUsersFilterLogin', { value: await this.$store.getters.getWebvueProp('usersFilterLogin', '') });
    this.$store.dispatch('setUsersFilterEmail', { value: await this.$store.getters.getWebvueProp('usersFilterEmail', '') });
    this.$store.dispatch('setUsersFilterFirstName', { value: await this.$store.getters.getWebvueProp('usersFilterFirstName', '') });
    this.$store.dispatch('setUsersFilterLastName', { value: await this.$store.getters.getWebvueProp('usersFilterLastName', '') });
    this.$store.dispatch('setUsersFilterGroups', { value: await this.$store.getters.getWebvueProp('usersFilterGroups', '') });
  },
  computed: {
    usersFilterId: {
      get() { return this.$store.getters.getUsersFilterId; },
      set(value) { this.$store.dispatch('setUsersFilterId', { value: value }); }
    },
    usersFilterLogin: {
      get() { return this.$store.getters.getUsersFilterLogin; },
      set(value) { this.$store.dispatch('setUsersFilterLogin', { value: value }); }
    },
    usersFilterEmail: {
      get() { return this.$store.getters.getUsersFilterEmail; },
      set(value) { this.$store.dispatch('setUsersFilterEmail', { value: value }); }
    },
    usersFilterFirstName: {
      get() { return this.$store.getters.getUsersFilterFirstName; },
      set(value) { this.$store.dispatch('setUsersFilterFirstName', { value: value }); }
    },
    usersFilterLastName: {
      get() { return this.$store.getters.getUsersFilterLastName; },
      set(value) { this.$store.dispatch('setUsersFilterLastName', { value: value }); }
    },
    usersFilterGroups: {
      get() { return this.$store.getters.getUsersFilterGroups; },
      set(value) { this.$store.dispatch('setUsersFilterGroups', { value: value }); }
    }
  },
  methods: {
    ok() {
      this.$store.dispatch('setUsersFilterId', { value: this.usersFilterId });
      this.$store.dispatch('setUsersFilterLogin', { value: this.usersFilterLogin });
      this.$store.dispatch('setUsersFilterEmail', { value: this.usersFilterEmail });
      this.$store.dispatch('setUsersFilterFirstName', { value: this.usersFilterFirstName });
      this.$store.dispatch('setUsersFilterLastName', { value: this.usersFilterLastName });
      this.$store.dispatch('setUsersFilterGroups', { value: this.usersFilterGroups });

      let params = {};
      if (this.usersFilterId) params.filter_id = this.usersFilterId;
      if (this.usersFilterLogin) params.filter_user = this.usersFilterLogin;
      if (this.usersFilterEmail) params.filter_ym_id = this.usersFilterEmail;
      if (this.usersFilterFirstName) params.filter_last_album_ym = this.usersFilterFirstName;
      if (this.usersFilterLastName) params.filter_last_album_processed = this.usersFilterLastName;
      if (this.usersFilterGroups) params.filter_groups = this.usersFilterGroups;
      this.$store.dispatch('loadUsersDigest', params )

      this.$emit('close');
    },
    cancel() {
      this.$emit('close');
    }
  }
}
</script>

<style scoped>

.ufm-modal-fade-enter,
.ufm-modal-fade-leave-active {
  opacity: 0;
}

.ufm-modal-fade-enter-active,
.ufm-modal-fade-leave-active {
  transition: opacity .5s ease
}

.ufm-area-modal-header {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
}

.ufm-area-modal-body {
  background-color: white;
  padding: 10px;
  color: black;
  font-size: larger;
  font-weight: 300;
}

.ufm-area-modal-footer {
  background-color: darkslategray;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: center;
}

.ufm-modal-backdrop {
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

.ufm-area {
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

.ufm-btn-close {
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

.ufm-root-wrapper {
  display: flex;
  flex-direction: column;
}
.ufm-button-clear-field {
  border: thin solid black;
  border-radius: 50%;
  font-size: x-small;
  height: 20px;
  width: 20px;
  margin-top: -4px;
  margin-left: -10px;
}
.ufm-filter-row {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.ufm-row-label {
  min-width: 140px;
  max-width: 140px;
  text-align: right;
  padding: 0 3px;
  font-size: small;
}
.ufm-row-input {
  display: block;
  padding-bottom: 3px;
  width: 200px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}

.ufm-input-field {
  border-radius: 5px;
  width: fit-content;
}

.ufm-input-field:hover {
  background-color: lightyellow;
}
.ufm-input-field:focus {
  background-color: cyan;
}

</style>