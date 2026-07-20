<template>
  <div class="pst-wrapper">
    <div class="pst-toolbar">
      <label class="pst-toolbar-item">
        БД:
        <select v-model="target" @change="reload">
          <option value="local">Локальная</option>
          <option value="remote">Сервер</option>
        </select>
      </label>
      <button class="pst-btn" @click="reload">Обновить</button>
    </div>

    <div v-if="isLoading" class="pst-loading">Загрузка...</div>
    <div v-else-if="editable.length === 0" class="pst-empty">Нет настроек</div>
    <table v-else class="pst-table">
      <thead>
        <tr>
          <th class="pst-th">Ключ</th>
          <th class="pst-th">Значение</th>
          <th class="pst-th">Описание</th>
          <th class="pst-th" />
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in editable" :key="item.key">
          <td class="pst-key">{{ item.key }}</td>
          <td><input v-model="item.value" class="pst-input" /></td>
          <td class="pst-desc">{{ item.description }}</td>
          <td>
            <button
              class="pst-btn"
              :disabled="item.value === original[item.key]"
              @click="save(item)"
            >
              Сохранить
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script>
export default {
  name: 'PublicSettingsTable',
  data() {
    return {
      editable: [],
      original: {},
    }
  },
  computed: {
    isLoading() {
      return this.$store.getters.getPublicSettingsDigestIsLoading
    },
    digest() {
      return this.$store.getters.getPublicSettingsDigest
    },
    target: {
      get() {
        return this.$store.getters.getPublicSettingsTarget
      },
      set(v) {
        this.$store.dispatch('setPublicSettingsTarget', v)
      },
    },
  },
  watch: {
    digest: {
      immediate: true,
      handler(list) {
        this.editable = list.map((i) => Object.assign({}, i))
        this.original = {}
        list.forEach((i) => {
          this.original[i.key] = i.value
        })
      },
    },
  },
  mounted() {
    this.reload()
  },
  methods: {
    reload() {
      this.$store.dispatch('loadPublicSettingsDigest')
    },
    async save(item) {
      await this.$store.dispatch('savePublicSettingValue', { key: item.key, value: item.value })
      this.original[item.key] = item.value
    },
  },
}
</script>

<style scoped>
.pst-wrapper {
  width: 100%;
  max-width: 900px;
  margin: 10px auto;
  font-family: Avenir, Helvetica, Arial, sans-serif;
}
.pst-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  font-size: small;
}
.pst-btn {
  border: solid 1px black;
  border-radius: 6px;
  padding: 4px 12px;
  background-color: antiquewhite;
  cursor: pointer;
}
.pst-btn:hover {
  background-color: lightpink;
}
.pst-btn[disabled] {
  background-color: lightgray;
  cursor: default;
}
.pst-loading,
.pst-empty {
  font-size: small;
  color: gray;
  padding: 10px 0;
}
.pst-table {
  width: 100%;
  border-collapse: collapse;
  font-size: small;
}
.pst-th {
  text-align: left;
  border-bottom: 2px solid black;
  padding: 6px 8px;
}
.pst-key {
  font-family: monospace;
  padding: 6px 8px;
  border-bottom: 1px solid lightgray;
  white-space: nowrap;
}
.pst-desc {
  padding: 6px 8px;
  border-bottom: 1px solid lightgray;
  color: gray;
}
.pst-input {
  width: 100%;
  padding: 3px 6px;
  border: 1px solid black;
  border-radius: 4px;
  font-size: small;
}
td {
  border-bottom: 1px solid lightgray;
  padding: 6px 8px;
}
</style>
