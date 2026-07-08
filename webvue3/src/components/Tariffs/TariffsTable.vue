<template>
  <div class="trf-wrapper">
    <div class="trf-toolbar">
      <label class="trf-toolbar-item">
        БД:
        <select v-model="target" @change="reload">
          <option value="local">Локальная</option>
          <option value="remote">Сервер</option>
        </select>
      </label>
      <button class="trf-btn" @click="reload">Обновить</button>
    </div>

    <div class="trf-add">
      <div class="trf-add-title">Новый тариф:</div>
      <select v-model="newItem.scope">
        <option value="SONG">Подписка на песню (бессрочная)</option>
        <option value="SITE">Подписка на сайт (период)</option>
      </select>
      <input class="trf-input" placeholder="Название" v-model="newItem.name">
      <input class="trf-input trf-num" type="number" min="0" step="0.01" placeholder="Цена, ₽" v-model.number="newItem.priceRub">
      <input v-if="newItem.scope === 'SITE'" class="trf-input trf-num" type="number" min="1" placeholder="Период, дней" v-model.number="newItem.periodDays">
      <button class="trf-btn" :disabled="!canCreate" @click="create">Добавить</button>
    </div>

    <div v-if="isLoading" class="trf-loading">Загрузка...</div>
    <div v-else-if="editable.length === 0" class="trf-empty">Тарифов пока нет</div>
    <table v-else class="trf-table">
      <thead>
        <tr>
          <th class="trf-th">Тип</th>
          <th class="trf-th">Название</th>
          <th class="trf-th">Цена, ₽</th>
          <th class="trf-th">Период, дн.</th>
          <th class="trf-th">Активен</th>
          <th class="trf-th">По умолчанию</th>
          <th class="trf-th">Порядок</th>
          <th class="trf-th"></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in editable" :key="item.id">
          <td>{{ item.scope === 'SONG' ? 'Песня' : 'Сайт' }}</td>
          <td><input class="trf-input" v-model="item.name"></td>
          <td><input class="trf-input trf-num" type="number" min="0" step="0.01" v-model.number="item.priceRub"></td>
          <td><input class="trf-input trf-num" type="number" min="0" v-model.number="item.periodDays" :disabled="item.scope === 'SONG'"></td>
          <td><input type="checkbox" v-model="item.isActive"></td>
          <td><input type="checkbox" v-model="item.isDefault"></td>
          <td><input class="trf-input trf-num" type="number" v-model.number="item.sortOrder"></td>
          <td class="trf-actions">
            <button class="trf-btn" :disabled="!isChanged(item)" @click="save(item)">Сохранить</button>
            <button class="trf-btn trf-btn-danger" @click="remove(item)">Удалить</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script>
export default {
  name: "TariffsTable",
  data() {
    return {
      editable: [],
      original: {},
      newItem: { scope: 'SONG', name: '', priceRub: 0, periodDays: 30 },
    }
  },
  computed: {
    isLoading() { return this.$store.getters.getTariffsIsLoading },
    list() { return this.$store.getters.getTariffsList },
    target: {
      get() { return this.$store.getters.getTariffsTarget },
      set(v) { this.$store.dispatch('setTariffsTarget', v) }
    },
    canCreate() { return this.newItem.name.trim() !== '' && this.newItem.priceRub >= 0 },
  },
  watch: {
    list: {
      immediate: true,
      handler(list) {
        this.editable = list.map(i => Object.assign({}, i));
        this.original = {};
        list.forEach(i => { this.original[i.id] = Object.assign({}, i) });
      }
    }
  },
  mounted() {
    this.reload();
  },
  methods: {
    reload() {
      this.$store.dispatch('loadTariffsList');
    },
    isChanged(item) {
      const orig = this.original[item.id];
      if (!orig) return false;
      return Object.keys(item).some(k => item[k] !== orig[k]);
    },
    async save(item) {
      await this.$store.dispatch('saveTariff', item);
    },
    async remove(item) {
      if (!confirm(`Удалить тариф «${item.name}»?`)) return;
      await this.$store.dispatch('deleteTariff', item.id);
    },
    async create() {
      await this.$store.dispatch('createTariff', this.newItem);
      this.newItem = { scope: 'SONG', name: '', priceRub: 0, periodDays: 30 };
    }
  }
}
</script>

<style scoped>
.trf-wrapper { width: 100%; max-width: 1000px; margin: 10px auto; font-family: Avenir, Helvetica, Arial, sans-serif; }
.trf-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; font-size: small; }
.trf-add { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; padding: 8px; border: thin dashed darkgray; border-radius: 8px; font-size: small; flex-wrap: wrap; }
.trf-add-title { font-weight: bold; }
.trf-btn { border: solid 1px black; border-radius: 6px; padding: 4px 12px; background-color: antiquewhite; cursor: pointer; }
.trf-btn:hover { background-color: lightpink; }
.trf-btn[disabled] { background-color: lightgray; cursor: default; }
.trf-btn-danger { background-color: #f4b6b6; }
.trf-btn-danger:hover { background-color: #e08a8a; }
.trf-loading, .trf-empty { font-size: small; color: gray; padding: 10px 0; }
.trf-table { width: 100%; border-collapse: collapse; font-size: small; }
.trf-th { text-align: left; border-bottom: 2px solid black; padding: 6px 8px; }
.trf-input { padding: 3px 6px; border: 1px solid black; border-radius: 4px; font-size: small; width: 140px; }
.trf-num { width: 90px; }
.trf-actions { display: flex; gap: 6px; white-space: nowrap; }
td { border-bottom: 1px solid lightgray; padding: 6px 8px; }
</style>
