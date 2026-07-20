<template>
  <div class="news-table">
    <div class="news-table-add">
      <div class="news-table-add-title">{{ editingId ? 'Изменить новость:' : 'Добавить новость:' }}</div>
      <div class="news-table-add-row">
        <input v-model="form.title" class="news-field news-field-title" placeholder="Заголовок"/>
        <select v-model="form.category" class="news-field news-field-category">
          <option v-for="opt in categoryOptions" :key="opt.value" :value="opt.value" v-text="opt.icon + ' ' + opt.label"/>
        </select>
        <input v-model="form.publishAt" class="news-field news-field-publishat" type="datetime-local" title="Дата и время публикации"/>
      </div>
      <div class="news-table-add-row">
        <textarea v-model="form.body" class="news-field news-field-body" placeholder="Текст новости" rows="2"/>
      </div>
      <div class="news-table-add-row">
        <input v-model="form.link" class="news-field news-field-link" placeholder="Ссылка (необязательно) — например /song?id=123"/>
        <button class="news-btn-round" :disabled="!canSubmit" @click="submit">{{ editingId ? 'Сохранить' : 'Добавить' }}</button>
        <button v-if="editingId" class="news-btn-round news-btn-cancel" @click="cancelEdit">Отмена</button>
      </div>
    </div>

    <div class="news-table-target">
      Целевая БД:
      <select v-model="newsTargetModel" class="news-field news-field-target">
        <option value="local">Локальная</option>
        <option value="remote">Сервер</option>
      </select>
    </div>

    <div class="news-table-body">
      <b-table
          :items="newsList"
          :busy="newsListIsLoading"
          :fields="newsFields"
          small
          bordered
          hover
      >
        <template #table-busy>
          <div class="text-center text-danger my-2">
            <b-spinner class="align-middle"/>
            <strong>Loading...</strong>
          </div>
        </template>

        <template #cell(id)="data">
          <div class="fld-news-id" v-text="data.value"/>
        </template>

        <template #cell(title)="data">
          <div class="fld-news-title" title="Изменить" @click.left="startEdit(data.item)" v-text="data.value"/>
        </template>

        <template #cell(category)="data">
          <div class="fld-news-category" v-text="categoryLabel(data.value)"/>
        </template>

        <template #cell(publishAt)="data">
          <div class="fld-news-publishat" v-text="formatDate(data.value)"/>
        </template>

        <template #cell(published)="data">
          <div class="fld-news-status" :class="data.value ? 'fld-news-status-published' : 'fld-news-status-scheduled'">
            {{ data.value ? 'Опубликовано' : 'Запланировано' }}
          </div>
        </template>

        <template #cell(actions)="data">
          <div class="fld-news-actions">
            <button class="news-btn-round-small" title="Удалить" @click="remove(data.item)">×</button>
          </div>
        </template>
      </b-table>
    </div>
  </div>
</template>

<script>
import { BSpinner, BTable } from 'bootstrap-vue-next'

const CATEGORY_OPTIONS = [
  { value: 'air', label: 'Эфир', icon: '📻' },
  { value: 'premium', label: 'Премиум', icon: '🪙' },
  { value: 'feature', label: 'Функционал', icon: '✨' },
  { value: 'general', label: 'Общее', icon: '📰' },
]

const emptyForm = () => ({ title: '', body: '', category: 'general', link: '', publishAt: '' })

export default {
  name: "NewsTable",
  components: { BSpinner, BTable },
  data() {
    return {
      form: emptyForm(),
      editingId: null,
      categoryOptions: CATEGORY_OPTIONS,
    }
  },
  computed: {
    newsList() { return this.$store.getters.getNewsList },
    newsListIsLoading() { return this.$store.getters.getNewsListIsLoading },
    canSubmit() { return this.form.title.trim() !== '' },
    newsTargetModel: {
      get() { return this.$store.getters.getNewsTarget },
      set(value) {
        this.$store.dispatch('setNewsTarget', value);
        this.$store.dispatch('loadNews');
      }
    },
    newsFields() {
      return [
        { key: 'id', label: 'ID', style: { minWidth: '50px', maxWidth: '50px', textAlign: 'center', fontSize: 'small' } },
        { key: 'title', label: 'Заголовок', style: { minWidth: '260px', maxWidth: '260px', textAlign: 'left', fontSize: 'small' } },
        { key: 'category', label: 'Категория', style: { minWidth: '120px', maxWidth: '120px', textAlign: 'left', fontSize: 'small' } },
        { key: 'publishAt', label: 'Публикация', style: { minWidth: '150px', maxWidth: '150px', textAlign: 'center', fontSize: 'small' } },
        { key: 'published', label: 'Статус', style: { minWidth: '120px', maxWidth: '120px', textAlign: 'center', fontSize: 'small' } },
        { key: 'actions', label: '', style: { minWidth: '50px', maxWidth: '50px', textAlign: 'center', fontSize: 'small' } },
      ]
    }
  },
  mounted() {
    this.$store.dispatch('loadNews');
  },
  methods: {
    categoryLabel(value) {
      const opt = this.categoryOptions.find(o => o.value === value);
      return opt ? `${opt.icon} ${opt.label}` : value;
    },
    // publishAt приходит как java.sql.Timestamp.toString() ("yyyy-MM-dd HH:mm:ss.SSS") — тот же
    // формат, что sponsrPremiumUntil в karaoke-public/AccountView.vue.
    formatDate(tsString) {
      if (!tsString) return '—';
      try {
        const d = new Date(tsString.replace(' ', 'T'));
        return d.toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit' });
      } catch (e) { return tsString }
    },
    // "yyyy-MM-dd HH:mm:ss.SSS" -> "yyyy-MM-ddTHH:mm" (значение для input[type=datetime-local]).
    toDatetimeLocal(tsString) {
      if (!tsString) return '';
      const s = tsString.replace(' ', 'T');
      return s.slice(0, 16);
    },
    submit() {
      if (!this.canSubmit) return;
      if (this.editingId) {
        this.saveEdit();
      } else {
        this.create();
      }
    },
    create() {
      const payload = { ...this.form, clearPublishAt: !this.form.publishAt };
      this.$store.dispatch('createNewsPromise', payload).then(() => {
        this.form = emptyForm();
        this.$store.dispatch('loadNews');
      }).catch(error => console.error("Ошибка при добавлении новости:", error));
    },
    startEdit(item) {
      this.editingId = item.id;
      this.form = {
        title: item.title,
        body: item.body,
        category: item.category,
        link: item.link,
        publishAt: this.toDatetimeLocal(item.publishAt),
      };
    },
    saveEdit() {
      const payload = { id: this.editingId, ...this.form, clearPublishAt: !this.form.publishAt };
      this.$store.dispatch('updateNewsPromise', payload).then(() => {
        this.cancelEdit();
        this.$store.dispatch('loadNews');
      }).catch(error => console.error("Ошибка при сохранении новости:", error));
    },
    cancelEdit() {
      this.editingId = null;
      this.form = emptyForm();
    },
    remove(item) {
      if (!confirm(`Удалить новость «${item.title}»?`)) return;
      this.$store.dispatch('deleteNewsPromise', item.id).then(() => {
        this.$store.commit('removeNewsItem', item.id);
      }).catch(error => console.error("Ошибка при удалении новости:", error));
    },
  }
}
</script>

<style scoped>
.news-table {
  padding: 0;
  margin: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  font-family: Avenir, Helvetica, Arial, sans-serif;
}

.news-table-add {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  max-width: 900px;
  margin-bottom: 10px;
  padding: 8px;
  border: thin dashed darkgray;
  border-radius: 8px;
  font-size: small;
}
.news-table-add-title { font-weight: bold; }
.news-table-add-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
}

.news-table-target {
  width: 100%;
  max-width: 900px;
  margin-bottom: 10px;
  font-size: small;
  text-align: left;
}

.news-table-body { width: fit-content; max-width: 100%; }

/* Инвариант DEVELOPMENT.md: select/input/textarea в одном ряду — общий явный width, appearance:none
   на select обязателен (иначе ОС-рамка/паддинг раздувает высоту и рвёт совпадение по бордеру). */
.news-field {
  box-sizing: border-box;
  border: 1px solid black;
  border-radius: 4px;
  padding: 4px 6px;
  font-size: small;
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  font-family: inherit;
}
.news-field-title { width: 320px; }
.news-field-category { width: 160px; }
.news-field-publishat { width: 220px; }
.news-field-body { width: 100%; resize: vertical; }
.news-field-link { flex: 1; }
.news-field-target { width: 140px; }

.news-btn-round {
  border: solid 1px black;
  border-radius: 6px;
  padding: 4px 12px;
  background-color: antiquewhite;
  cursor: pointer;
  white-space: nowrap;
}
.news-btn-round:hover { background-color: lightpink; }
.news-btn-round[disabled] { background-color: lightgray; cursor: default; }
.news-btn-cancel { background-color: #eee; }

.news-btn-round-small {
  border: solid 1px black;
  border-radius: 6px;
  width: 24px;
  height: 24px;
  background-color: #f4b6b6;
  cursor: pointer;
  line-height: 1;
}
.news-btn-round-small:hover { background-color: #e08a8a; }

.fld-news-id { min-width: 50px; max-width: 50px; text-align: center; font-size: small; }
.fld-news-title {
  min-width: 260px; max-width: 260px; text-align: left; font-size: small;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  cursor: pointer;
}
.fld-news-title:hover { text-decoration: underline; }
.fld-news-category { min-width: 120px; max-width: 120px; text-align: left; font-size: small; white-space: nowrap; }
.fld-news-publishat { min-width: 150px; max-width: 150px; text-align: center; font-size: small; white-space: nowrap; }
.fld-news-status { min-width: 120px; max-width: 120px; text-align: center; font-size: small; font-weight: bold; }
.fld-news-status-published { color: #2f8a3e; }
.fld-news-status-scheduled { color: #a06a00; }
.fld-news-actions { min-width: 50px; max-width: 50px; text-align: center; }
</style>
