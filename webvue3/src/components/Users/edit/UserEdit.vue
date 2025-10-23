<template>
  <div :style="styleRoot">
    <div v-if="userCurrent">
      <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
      <div class="header">
        <div class="header-user-id">ID = {{userCurrent.id}}</div>
      </div>
      <div class="body">
        <div class="column-1">
          <div class="label-and-input">
            <div class="label">Login:</div>
            <input class="input-field" v-model="userCurrent.login">
            <button class="btn-round" @click="undoField('login')" :disabled="notChanged('login')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(userCurrent.login, 'login')" :disabled="!userCurrent.login"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('login')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Email:</div>
            <input class="input-field" v-model="userCurrent.email">
            <button class="btn-round" @click="undoField('email')" :disabled="notChanged('email')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(userCurrent.email, 'email')" :disabled="!userCurrent.email"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('email')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Имя:</div>
            <input class="input-field" v-model="userCurrent.firstName">
            <button class="btn-round" @click="undoField('firstName')" :disabled="notChanged('firstName')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(userCurrent.firstName, 'firstName')" :disabled="!userCurrent.firstName"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('firstName')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Фамилия:</div>
            <input class="input-field" v-model="userCurrent.lastName">
            <button class="btn-round" @click="undoField('lastName')" :disabled="notChanged('lastName')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(userCurrent.lastName, 'lastName')" :disabled="!userCurrent.lastName"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('lastName')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Группы:</div>
            <input class="input-field" v-model="userCurrent.groups">
            <button class="btn-round" @click="undoField('groups')" :disabled="notChanged('groups')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(userCurrent.groups, 'groups')" :disabled="!userCurrent.groups"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('groups')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="column2-buttons-group ">
            <button class="group-button" @click="checkPassword" title="Проверить пароль">Проверить пароль</button>
            <button class="group-button" @click="resetPassword" title="Сбросить пароль">Сбросить пароль</button>
            <button class="group-button" @click="changePassword" title="Изменить пароль">Изменить пароль</button>
          </div>
        </div>
      </div>
      <div class="footer">
        <button class="btn-round-save-double" @click="save" :disabled="notChanged()" title="Сохранить"><img alt="saveUser" class="icon-save-double" src="../../../assets/svg/icon_save.svg"></button>
        <button class="btn-round-double" @click="deleteUser" title="Удалить пользователя"><img alt="delete" class="icon-40" src="../../../assets/svg/icon_delete.svg"></button>
      </div>
    </div>
    <div v-else>
      Не выбран пользователь
    </div>
  </div>
</template>

<script>

import CustomConfirm from "../../Common/CustomConfirm.vue";
import { useToast } from "bootstrap-vue-next";
import { h } from 'vue';
export default {
  name: "UserEdit",
  components: {
    CustomConfirm
  },
  data () {
    return {
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
      userAutoSave: true,
      userAutoSaveDelayMs: 1000,
      userSaveTimer: undefined,
      createToast: () => {}
    };
  },
  async mounted() {
    const { create } = useToast();
    this.createToast = create;
    let image = this.$store.getters.getUserCurrent;
    this.$store.dispatch('setUserCurrent', image);
    this.$store.dispatch('setUserSnapshot', image);
    this.userAutoSave = await this.propAutoSave();
    this.userAutoSaveDelayMs = Number(await this.propAutoSaveDelayMs());
  },
  watch: {
    userDiff: {
      async handler () {
        if (this.userDiff.length !== 0 && this.userAutoSave) {
          clearTimeout(this.userSaveTimer);
          this.userSaveTimer = setTimeout(this.save, this.userAutoSaveDelayMs);
        }
      }
    }
  },
  computed: {
    styleRoot() {
      return {
        padding: 0,
        margin: 0,
        width: 'auto',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        fontFamily: 'Avenir, Helvetica, Arial, sans-serif'
        // backgroundColor: 'lightyellow'
      }
    },
    userCurrent() { return this.$store.getters.getUserCurrent },
    userSnapshot() { return this.$store.getters.getUserSnapshot },
    userDiff() { return this.$store.getters.getUserDiff },
  },
  methods: {
    async propAutoSave() {
      const propValue = await this.$store.getters.getPropValue('autoSave');
      return propValue === 'true'
    },
    async propAutoSaveDelayMs() { return await this.$store.getters.getPropValue('autoSaveDelayMs') },
    closeCustomConfirm() { this.isCustomConfirmVisible = false },
    undoField(name) {
      return this.$store.dispatch('setUserCurrentField', {name: name, value: this.userSnapshot[name]})
    },
    async copyToClipboard(value, fieldName) {
      await navigator.clipboard.writeText(value);
      this.showCopyToClipboardToast(fieldName, value);
    },
    async pasteFromClipboard(name) {
      await navigator.clipboard.readText().then(data => {
        return this.$store.dispatch('setUserCurrentField', {name: name, value: data})
      });
    },
    showCopyToClipboardToast(fieldName, fieldValue) {
      // Use a shorter name for this.$createElement
      // const h = this.$createElement

      // Функция для преобразования текста с \n в массив VNodes с <br>
      const createTextWithLineBreaks = (text) => {
        if (typeof text !== 'string') {
          return [String(text)];
        }

        const lines = text.split('\n');
        const vnodes = [];

        lines.forEach((line, index) => {
          // Добавляем текст строки
          vnodes.push(line);
          // Если это не последняя строка, добавляем <br>
          if (index < lines.length - 1) {
            vnodes.push(h('br'));
          }
        });

        return vnodes;
      };

      // Создаем сообщение с возможными переносами строк
      const vNodesMsg = h('div', [
        h('div', { style: { display: 'flex', flexDirection: 'row', flexWrap: 'wrap' } }, [
          h('div', { style: { fontFamily: 'sans-serif', fontSize: 'small', textAlign: 'left', paddingRight: '5px' } }, [`Значение поля `]),
          h('div', { style: { fontFamily: 'monospace', fontSize: 'small', textAlign: 'left' , fontWeight: 'bold', paddingRight: '5px', color: 'darkred'} }, [fieldName]),
          h('div', { style: { fontFamily: 'sans-serif', fontSize: 'small', textAlign: 'left' } }, [` скопировано в буфер обмена:`]),
        ]),
        h('br'),
        h('div', { style: { fontFamily: 'monospace', fontSize: 'x-small', textAlign: 'left' } }, createTextWithLineBreaks(fieldValue))
      ]);

      this.createToast({
        slots: { default: () => [vNodesMsg] },
        title: 'COPY',
        autoHideDelay: 3000,
        bodyClass: 'toast-body-copytoclipboard',
        headerClass: 'toast-header-copytoclipboard',
        appendToast: false,
        position: 'top-start',
        // modelValue: true
      })
    },
    notChanged(name) {
      if (name) {
        return this.userSnapshot ? this.userCurrent[name] === this.userSnapshot[name] : true;
      } else {
        return this.userDiff.length === 0;
      }
    },
    save() {
      clearTimeout(this.userSaveTimer);
      let diffs = {};
      for (let diff of this.userDiff) {
        diffs[diff.name] = diff.new;
      }
      return this.$store.dispatch('saveUser', diffs)
    },
    deleteUser() {
      this.customConfirmParams = {
        header: 'Подтвердите удаление пользователя',
        body: `Удалить пользователя <strong>«${this.userCurrent.login}»</strong>?`,
        timeout: 10,
        callback: this.doDeleteUser
      }
      this.isCustomConfirmVisible = true;
    },
    async doDeleteUser() {
      const result = await this.$store.dispatch('deleteUserCurrent');
      this.showBooleanToast(result, 'Удаление пользователя', `Пользователь удалён`, `Пользователь не удалён`);
      this.$emit('close');
    },
    resetPassword() {
      this.customConfirmParams = {
        header: 'Подтвердите сброса пароля',
        body: `Сбросить пароль для пользователя <strong>«${this.userCurrent.login}»</strong>?`,
        timeout: 10,
        callback: this.doResetPassword
      }
      this.isCustomConfirmVisible = true;
    },
    async doResetPassword() {
      const result = await this.$store.dispatch('resetPasswordUserCurrent');
      this.showBooleanToast(result, 'Сброс пароля', `Пароль сброшен`, `Пароль не сброшен`);
      this.isCustomConfirmVisible = false;
    },
    checkPassword() {
      let item = { password: ''};
      this.customConfirmParams = {
        header: 'Проверка пароля',
        body: `Проверка пароля`,
        callback: this.doCheckPassword,
        fields: [
          {
            fldName: 'password',
            fldLabel: 'Пароль:',
            fldValue: item.password,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px'}
          },
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    async doCheckPassword(item) {
      const result = await this.$store.dispatch('checkPasswordUserCurrent', item.password);
      console.log('doCheckPassword result', result);
      this.showBooleanToast(result === 'true', 'Проверка пароля', `Проверка пароля пройдена`, `Проверка пароля не пройдена`);
      this.isCustomConfirmVisible = false;
    },
    changePassword() {
      let item = { newPassword: '', oldPassword: ''};
      this.customConfirmParams = {
        header: 'Смена пароля',
        body: `Смена пароля`,
        callback: this.doChangePassword,
        fields: [
          {
            fldName: 'newPassword',
            fldLabel: 'Новый пароль:',
            fldValue: item.password,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px'}
          },
          {
            fldName: 'oldPassword',
            fldLabel: 'Старый пароль:',
            fldValue: item.password,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px'}
          },
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    async doChangePassword(item) {
      const result = await this.$store.dispatch('changePasswordUserCurrent', item.newPassword, item.oldPassword);
      this.showBooleanToast(result, 'Смена пароля', `Пароль изменён`, `Пароль не изменён`);
      this.isCustomConfirmVisible = false;
    },
    showBooleanToast(value, textHeader, textBodyTrue, textBodyFalse) {
      if (value) {
        const vNodesMsg = h('div', [
          h('div', { style: { display: 'flex', flexDirection: 'row', flexWrap: 'wrap' } }, [
            h('div', { style: { fontFamily: 'sans-serif', fontSize: 'small', textAlign: 'left', paddingRight: '5px' } }, [`${textBodyTrue}`]),
          ])
        ]);

        this.createToast({
          slots: { default: () => [vNodesMsg] },
          title: textHeader,
          autoHideDelay: 3000,
          bodyClass: 'toast-body-servermessage',
          headerClass: 'toast-header-servermessage',
          appendToast: false,
          position: 'top-start',
          // modelValue: true
        })
      } else {
        const vNodesMsg = h('div', [
          h('div', { style: { display: 'flex', flexDirection: 'row', flexWrap: 'wrap' } }, [
            h('div', { style: { fontFamily: 'sans-serif', fontSize: 'small', textAlign: 'left', paddingRight: '5px' } }, [`${textBodyFalse}`]),
          ])
        ]);

        this.createToast({
          slots: { default: () => [vNodesMsg] },
          title: textHeader,
          autoHideDelay: 3000,
          bodyClass: 'toast-body-servererror',
          headerClass: 'toast-header-servererror',
          appendToast: false,
          position: 'top-start',
          // modelValue: true
        })
      }

    }

  }
}
</script>

<style scoped>

.header {
  border: thin dashed darkgray;
  border-radius: 10px;
  padding: 5px 0;
  background-color: transparent;
}
.body {
  margin: 0;
  display: flex;
  flex-direction: row;
  height: max-content;
  background-color: transparent;
  z-index: 100;
}
.footer {
  display: flex;
  flex-direction: row;
  justify-content: center;
  border: thin dashed darkgray;
  border-radius: 10px;
  padding: 5px 0;
  background-color: transparent;
}
.header-user-id {
  text-align: center;
  font-size: 12pt;
  margin: 0 auto;
}
.column-1 {
  width: max-content;
  height: max-content;
  /*background-color: white;*/
  margin: 5px 5px 5px 0;
  display: flex;
  flex-direction: column;
  /*align-items: flex-end;*/
}
.column-2 {
  width: max-content;
  height: max-content;
  margin: 5px 5px 5px 5px;
}
.label-and-input {
  display: flex;
}
.label {
  font-size: small;
  text-align: right;
  width: 110px;
  padding-right: 2px;
  padding-top: 2px;
}
.input-field {
  display: block;
  padding-bottom: 3px;
  width: 310px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}
.input-field:hover {
  background-color: lightyellow;
}
.input-field:focus {
  background-color: cyan;
}
.btn-round {
  border: solid 1px black;
  border-radius: 25%;
  width: 24px;
  height: 24px;
  margin-left: 2px;
  background-color: antiquewhite;
}
.btn-round:hover {
  background-color: lightpink;
}
.btn-round:focus {
  background-color: darksalmon;
}
.btn-round[disabled] {
  background-color: lightgray;
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
.btn-round-save-double {
  border: solid 1px black;
  border-radius: 6px;
  width: 50px;
  height: 50px;
  margin-left: 2px;
  background-color: red;
}
.btn-round-save-double:hover {
  background-color: darkred;
}
.btn-round-save-double:focus {
  background-color: greenyellow;
}
.btn-round-save-double[disabled] {
  background-color: lightgray;
}
.icon-undo {
  width: 18px;
  height: 18px;
  margin-left: -4px;
  margin-top: -10px;
}
.icon-copy {
  width: 24px;
  height: 24px;
  margin-left: -6px;
  margin-top: -10px;
}
.icon-paste {
  width: 24px;
  height: 24px;
  margin-left: -6px;
  margin-top: -10px;
}
.user-full {
  background-color: black;
  width: auto;
  height: 400px;
}
.image-full {
  width: auto;
  height: 400px;
}
.column2-buttons-group {
  font-size: small;
  display: flex;
  flex-direction: column;
}
.group-button {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: auto;
}
</style>