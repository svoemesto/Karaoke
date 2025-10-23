<template>
  <div class="users-bv-table">
    <UserEditModal v-if="isUserEditVisible" @close="closeUserEdit"/>
    <UsersFilter v-if="isUsersFilterVisible" @close="closeUsersFilter"/>
    <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
    <div class="users-bv-table-header">
      <b-pagination
          v-model="currentPage"
          :total-rows="countRows"
          :per-page="perPage"
          :limit="20"
          size="sm"
          pills
      ></b-pagination>
    </div>
    <div class="users-bv-table-body">
      <b-table
          :items="usersDigest"
          :busy="isBusy"
          :fields="userDigestFields"
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

        <template #cell(id)="data">
          <div
              class="fld-user-id"
              v-text="data.value"
              :style="{ color: currentUserId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>

        <template #cell(login)="data">
          <div
              class="fld-login"
              v-text="data.value"
              :style="{ color: currentUserId === data.item.id ? 'blue' : 'black' }"
              @click.left="editUser(data.item.id, data.item)"
          ></div>
        </template>

        <template #cell(email)="data">
          <div
              class="fld-email"
              v-text="data.value"
              :style="{ color: currentUserId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>

        <template #cell(firstName)="data">
          <div
              class="fld-firstName"
              v-text="data.value"
              :style="{ color: currentUserId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>

        <template #cell(lastName)="data">
          <div
              class="fld-lastName"
              v-text="data.value"
              :style="{ color: currentUserId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>

        <template #cell(groups)="data">
          <div
              class="fld-groups"
              v-text="data.value"
              :style="{ color: currentUserId === data.item.id ? 'blue' : 'black' }"
          ></div>
        </template>

      </b-table>
    </div>
    <div class="users-bv-table-footer">
      <button class="btn-round-double" @click="isUsersFilterVisible=true" title="Фильтр">
        <img alt="filter" class="icon-40" src="../../assets/svg/icon_filter.svg">
      </button>
      <button class="btn-round-double" @click="addUser" title="Добавить пользователя">
        <img alt="filter" class="icon-40" src="../../assets/svg/icon_add_user.svg">
      </button>
    </div>


  </div>
</template>

<script>


import { BPagination, BSpinner, BTable } from 'bootstrap-vue-next'
import UsersFilter from "../../components/Users/filter/UsersFilterModal.vue";
import CustomConfirm from "../Common/CustomConfirm.vue";
import UserEditModal from "../../components/Users/edit/UserEditModal.vue";

export default {
  name: "UsersTable",
  components: {
    UsersFilter,
    UserEditModal,
    CustomConfirm,
    BPagination,
    BSpinner,
    BTable
  },
  data() {
    return {
      perPage: 19,
      currentPage: 1,
      isUserEditVisible: false,
      isUsersFilterVisible: false,
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
      isBusy: false,
      currentUserId: '',
      currentUser: undefined
    }
  },
  watch: {
    usersDigestIsLoading: {
      handler () {
        this.isBusy = this.usersDigestIsLoading;
      }
    }
  },
  computed: {
    usersDigestIsLoading() {
      return this.$store.getters.getUsersDigestIsLoading;
    },
    usersDigest() {
      return this.$store.getters.getUsersDigest;
    },
    countRows() {
      return this.usersDigest ? this.usersDigest.length : 0;
    },
    userDigestFields() {
      return [
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
          key: 'login',
          label: 'Login',
          style: {
            minWidth: '300px',
            maxWidth: '300px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'email',
          label: 'E-mail',
          style: {
            minWidth: '300px',
            maxWidth: '300px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'firstName',
          label: 'Имя',
          style: {
            minWidth: '300px',
            maxWidth: '300px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'lastName',
          label: 'Фамилия',
          style: {
            minWidth: '300px',
            maxWidth: '300px',
            textAlign: 'left',
            fontSize: 'small'
          }
        },
        {
          key: 'groups',
          label: 'Группы',
          style: {
            minWidth: '300px',
            maxWidth: '300px',
            textAlign: 'left',
            fontSize: 'small'
          }
        }
      ]
    }
  },
  methods: {

    addUser() {
      let item = {
        login: '',
        password: '',
        passwordConfirm: '',
        email: '',
        firstName: '',
        lastName: '',
      };
      this.customConfirmParams = {
        header: 'Создание пользователя',
        body: `Создание нового пользователя`,
        callback: this.doAddUser,
        fields: [
          {
            fldName: 'login',
            fldLabel: 'Login:',
            fldValue: item.login,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px'}
          },
          {
            fldName: 'password',
            fldLabel: 'Password:',
            fldValue: item.password,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px'}
          },
          {
            fldName: 'passwordConfirm',
            fldLabel: 'Password confirm:',
            fldValue: item.passwordConfirm,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px'}
          },
          {
            fldName: 'email',
            fldLabel: 'Email:',
            fldValue: item.email,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px'}
          },
          {
            fldName: 'firstName',
            fldLabel: 'Имя:',
            fldValue: item.firstName,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px'}
          },
          {
            fldName: 'lastName',
            fldLabel: 'Фамилия:',
            fldValue: item.lastName,
            fldLabelStyle: { width: '300px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '300px', textAlign: 'left', borderRadius: '5px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doAddUser(user) {
      this.$store.dispatch('createUserValuePromise', user)
          .then(result => { // result - это целое число, возвращаемое промисом
            if (result !== 0) { // Проверяем, отлично ли оно от нуля
              this.$store.dispatch('loadOneRecord', result);
            }
          })
          .catch(error => {
            console.error("Ошибка при выполнении setUserValuePromise:", error);
          });
    },

    closeCustomConfirm() {
      this.isCustomConfirmVisible = false;
    },

    editUser(id, user) {
      this.$store.commit('setUserCurrentId', id);
      this.$store.commit('setUserCurrent', user);
      console.log('user', user);
      this.isUserEditVisible = true;
    },
    closeUserEdit() {
      this.isUserEditVisible = false;
    },
    closeUsersFilter() {
      this.isUsersFilterVisible = false;
    },
    onRowClicked(item, index) {
      this.currentUser = item;
      this.currentUserId = item.id;
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

.users-bv-table {
  padding: 0;
  margin: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  font-family: Avenir, Helvetica, Arial, sans-serif;
}

.users-bv-table-header {
  width: fit-content;
}

.users-bv-table-body {
  width: fit-content;
}

.users-bv-table-footer {
  margin-top: auto;
  display: flex;
  flex-direction: row;
  align-items: center;
}

.fld-user-id {
  min-width: 50px;
  max-width: 50px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-login {
  min-width: 300px;
  max-width: 300px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}
.fld-login:hover {
  text-decoration: underline;
  cursor: pointer;
}

.fld-email {
  min-width: 300px;
  max-width: 300px;
  text-align: center;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-firstName {
  min-width: 300px;
  max-width: 300px;
  text-align: left;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-lastName {
  min-width: 300px;
  max-width: 300px;
  text-align: left;
  font-size: small;
  white-space: nowrap;
  overflow: hidden;
}

.fld-groups {
  min-width: 300px;
  max-width: 300px;
  text-align: left;
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