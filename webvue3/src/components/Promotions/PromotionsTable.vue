<template>
  <div class="prm-wrapper">
    <div class="prm-toolbar">
      <label class="prm-toolbar-item">
        БД:
        <select v-model="target" @change="reload">
          <option value="local">Локальная</option>
          <option value="remote">Сервер</option>
        </select>
      </label>
      <button class="prm-btn" @click="reload">Обновить</button>
    </div>

    <div class="prm-hint">
      Параметры (params) — JSON, зависит от типа:
      <ul>
        <li><b>NEW_USER_PERCENT</b>: {"percent":10,"hoursAfterRegistration":24}</li>
        <li><b>NTH_FREE</b>: {"n":5} — каждая 5-я оплаченная подписка бесплатна</li>
        <li><b>HAPPY_HOUR</b>: {"percent":15,"hours":[20,21,22],"daysOfWeek":[5,6,7]} (часы 0-23, дни ISO 1=Пн..7=Вс)</li>
        <li><b>FLAT_PERCENT</b>: {"percent":10}</li>
        <li><b>CART_BULK_PERCENT</b>: {"minQty":10,"percent":30} — скидка на ВЕСЬ заказ «Корзины», если
          товаров в нём ≥ minQty (применяется к SONG, количество считается строго внутри одного заказа,
          в отличие от NTH_FREE, который считает по всем покупкам пользователя за всё время)</li>
      </ul>
    </div>

    <div class="prm-add">
      <div class="prm-add-title">Новая акция:</div>
      <input v-model="newItem.name" class="prm-input" placeholder="Название"/>
      <select v-model="newItem.type">
        <option value="FLAT_PERCENT">FLAT_PERCENT</option>
        <option value="NEW_USER_PERCENT">NEW_USER_PERCENT</option>
        <option value="NTH_FREE">NTH_FREE</option>
        <option value="HAPPY_HOUR">HAPPY_HOUR</option>
        <option value="CART_BULK_PERCENT">CART_BULK_PERCENT</option>
      </select>
      <select v-model="newItem.appliesTo">
        <option value="BOTH">Песня + Сайт</option>
        <option value="SONG">Только песня</option>
        <option value="SITE">Только сайт</option>
      </select>
      <input v-model="newItem.paramsJson" class="prm-input prm-json" placeholder='{"percent":10}'/>
      <button class="prm-btn" :disabled="!canCreate" @click="create">Добавить</button>
    </div>

    <div v-if="isLoading" class="prm-loading">Загрузка...</div>
    <div v-else-if="editable.length === 0" class="prm-empty">Акций пока нет</div>
    <table v-else class="prm-table">
      <thead>
        <tr>
          <th class="prm-th">Название</th>
          <th class="prm-th">Тип</th>
          <th class="prm-th">Применяется к</th>
          <th class="prm-th">Параметры (JSON)</th>
          <th class="prm-th">Приоритет</th>
          <th class="prm-th">Активна</th>
          <th class="prm-th"/>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in editable" :key="item.id">
          <td><input v-model="item.name" class="prm-input"/></td>
          <td>
            <select v-model="item.type">
              <option value="FLAT_PERCENT">FLAT_PERCENT</option>
              <option value="NEW_USER_PERCENT">NEW_USER_PERCENT</option>
              <option value="NTH_FREE">NTH_FREE</option>
              <option value="HAPPY_HOUR">HAPPY_HOUR</option>
              <option value="CART_BULK_PERCENT">CART_BULK_PERCENT</option>
            </select>
          </td>
          <td>
            <select v-model="item.appliesTo">
              <option value="BOTH">Песня + Сайт</option>
              <option value="SONG">Только песня</option>
              <option value="SITE">Только сайт</option>
            </select>
          </td>
          <td><input v-model="item.paramsJson" class="prm-input prm-json"/></td>
          <td><input v-model.number="item.priority" class="prm-input prm-num" type="number"/></td>
          <td><input v-model="item.isActive" type="checkbox"/></td>
          <td class="prm-actions">
            <button class="prm-btn" :disabled="!isChanged(item)" @click="save(item)">Сохранить</button>
            <button class="prm-btn prm-btn-danger" @click="remove(item)">Удалить</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script>
export default {
  name: "PromotionsTable",
  data() {
    return {
      editable: [],
      original: {},
      newItem: { name: '', type: 'FLAT_PERCENT', appliesTo: 'BOTH', paramsJson: '{"percent":10}' },
    }
  },
  computed: {
    isLoading() { return this.$store.getters.getPromoRulesIsLoading },
    list() { return this.$store.getters.getPromoRulesList },
    target: {
      get() { return this.$store.getters.getPromoRulesTarget },
      set(v) { this.$store.dispatch('setPromoRulesTarget', v) }
    },
    canCreate() { return this.newItem.name.trim() !== '' && this.isValidJson(this.newItem.paramsJson) },
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
      this.$store.dispatch('loadPromoRulesList');
    },
    isValidJson(str) {
      try { JSON.parse(str); return true } catch (e) { return false }
    },
    isChanged(item) {
      const orig = this.original[item.id];
      if (!orig) return false;
      return Object.keys(item).some(k => item[k] !== orig[k]);
    },
    async save(item) {
      if (!this.isValidJson(item.paramsJson)) { alert('Некорректный JSON в параметрах'); return; }
      await this.$store.dispatch('savePromoRule', item);
    },
    async remove(item) {
      if (!confirm(`Удалить акцию «${item.name}»?`)) return;
      await this.$store.dispatch('deletePromoRule', item.id);
    },
    async create() {
      await this.$store.dispatch('createPromoRule', this.newItem);
      this.newItem = { name: '', type: 'FLAT_PERCENT', appliesTo: 'BOTH', paramsJson: '{"percent":10}' };
    }
  }
}
</script>

<style scoped>
.prm-wrapper { width: 100%; max-width: 1100px; margin: 10px auto; font-family: Avenir, Helvetica, Arial, sans-serif; }
.prm-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; font-size: small; }
.prm-hint { font-size: x-small; color: gray; margin-bottom: 10px; }
.prm-hint ul { margin: 4px 0; padding-left: 18px; }
.prm-add { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; padding: 8px; border: thin dashed darkgray; border-radius: 8px; font-size: small; flex-wrap: wrap; }
.prm-add-title { font-weight: bold; }
.prm-btn { border: solid 1px black; border-radius: 6px; padding: 4px 12px; background-color: antiquewhite; cursor: pointer; }
.prm-btn:hover { background-color: lightpink; }
.prm-btn[disabled] { background-color: lightgray; cursor: default; }
.prm-btn-danger { background-color: #f4b6b6; }
.prm-btn-danger:hover { background-color: #e08a8a; }
.prm-loading, .prm-empty { font-size: small; color: gray; padding: 10px 0; }
.prm-table { width: 100%; border-collapse: collapse; font-size: small; }
.prm-th { text-align: left; border-bottom: 2px solid black; padding: 6px 8px; }
.prm-input { padding: 3px 6px; border: 1px solid black; border-radius: 4px; font-size: small; width: 130px; }
.prm-json { width: 220px; font-family: monospace; }
.prm-num { width: 70px; }
.prm-actions { display: flex; gap: 6px; white-space: nowrap; }
td { border-bottom: 1px solid lightgray; padding: 6px 8px; }
</style>
