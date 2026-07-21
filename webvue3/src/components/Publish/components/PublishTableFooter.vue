<template>
  <div class="table-publish-footer">
    <button class="round-30-button" title="Обновить" @click="clickReload">
      <img alt="reload" class="icon-20" src="../../../assets/svg/icon_filter.svg" />
    </button>
    <button class="round-30-button" @click="clickPrevious">
      <img
        alt="previous"
        class="icon-20"
        title="["
        src="../../../assets/svg/icon_fast_backward.svg"
      />
    </button>
    <div class="wrapper-date">
      <input v-model="publishDays" class="input-date" />
    </div>
    <button class="round-30-button" @click="clickNext">
      <img
        alt="previous"
        class="icon-20"
        title="]"
        src="../../../assets/svg/icon_fast_forward.svg"
      />
    </button>
    <div class="wrapper-date">
      <input v-model="publishDateFrom" class="input-date" />
    </div>
    <div class="wrapper-date">
      <input v-model="publishDateTo" class="input-date" />
    </div>
    <button
      class="color-button"
      style="background-color: #7fffd4"
      title="Полностью готово"
      @click="clickColorButton('STATE_ALL_DONE')"
    />
    <button
      class="color-button"
      style="background-color: #bdb76b"
      title="Просрочено"
      @click="clickColorButton('STATE_OVERDUE')"
    />
    <button
      class="color-button"
      style="background-color: #ffff00"
      title="Сегодня"
      @click="clickColorButton('STATE_TODAY')"
    />
    <button
      class="color-button"
      style="background-color: #dcdcdc"
      title="Готово к публикации"
      @click="clickColorButton('STATE_ALL_UPLOADED')"
    />
    <button
      class="color-button"
      style="background-color: #87cefa"
      title="Нет TG"
      @click="clickColorButton('STATE_WO_TG')"
    />
    <button
      class="color-button"
      style="background-color: #ffdab9"
      title="Нет VK"
      @click="clickColorButton('STATE_WO_VK')"
    />
    <button
      class="color-button"
      style="background-color: #ff8000"
      title="Нет DZEN"
      @click="clickColorButton('STATE_WO_DZEN')"
    />
    <button
      class="color-button"
      style="background-color: #ffc880"
      title="Нет VKG"
      @click="clickColorButton('STATE_WO_VKG')"
    />
    <button
      class="color-button"
      style="background-color: #ffffff"
      title="Статус: NONE"
      @click="clickColorButton('STATUS_0')"
    />
    <button
      class="color-button"
      style="background-color: #dda0dd"
      title="Статус: Текст найден"
      @click="clickColorButton('STATUS_1')"
    />
    <button
      class="color-button"
      style="background-color: #ee82ee"
      title="Статус: Текст проверен"
      @click="clickColorButton('STATUS_2')"
    />
    <button
      class="color-button"
      style="background-color: #98fb98"
      title="Статус: Проект создан"
      @click="clickColorButton('STATUS_3')"
    />
    <button
      class="color-button"
      style="background-color: #00ff7f"
      title="Статус: Проект проверен"
      @click="clickColorButton('STATUS_4')"
    />
    <button
      class="color-button"
      style="background-color: #00ff00"
      title="Статус: Готово"
      @click="clickColorButton('STATUS_6')"
    />
    <button class="action-button" @click="clickActionButton('all')">С начала</button>
    <button class="action-button" @click="clickActionButton('fromtoday')">С сегодня</button>
    <button class="action-button" @click="clickActionButton('fromnotpublish')">
      С незавершенной
    </button>
    <button class="action-button" @click="clickActionButton('fromnotcheck')">
      С непроверенной
    </button>
    <button class="action-button" @click="clickActionButton('fromnotdone')">С неготовой</button>
    <button class="action-button" @click="clickActionButton('unpublish')">UNPUBLISH</button>
    <button class="action-button" @click="clickActionButton('skiped')">SKIPED</button>
  </div>
</template>

<script>
import { stringDDMMYYaddDays } from '../../../lib/utils'

/**
 * Компонент «Publish Table Footer».
 *
 * @see AGENTS.md
 */

export default {
  name: 'PublishTableFooter',
  computed: {
    publishDateFrom: {
      get() {
        return this.$store.getters.getPublishFilterDateFrom
      },
      set(value) {
        this.$store.dispatch('setPublishFilterDateFrom', { value })
      },
    },
    publishDateTo: {
      get() {
        return this.$store.getters.getPublishFilterDateTo
      },
      set(value) {
        this.$store.dispatch('setPublishFilterDateTo', { value })
      },
    },
    publishDays: {
      get() {
        return this.$store.getters.getPublishFilterDays
      },
      set(value) {
        this.$store.dispatch('setPublishFilterDays', { value })
      },
    },
  },
  async beforeMount() {
    this.$store.dispatch('setPublishFilterDateFrom', {
      value: await this.$store.getters.getWebvueProp('publishFilterDateFrom', ''),
    })
    this.$store.dispatch('setPublishFilterDateTo', {
      value: await this.$store.getters.getWebvueProp('publishFilterDateTo', ''),
    })
    this.$store.dispatch('setPublishFilterDays', {
      value: await this.$store.getters.getWebvueProp('publishFilterDays', 90),
    })
  },
  methods: {
    async clickColorButton(param) {
      this.publishDateFrom = await this.$store.dispatch('getPublicationsDateFrom', { param: param })
      this.setDateTo()
      this.$store.dispatch('loadPublishDigest', {
        filterDateFrom: this.publishDateFrom,
        filterDateTo: this.publishDateTo,
      })
    },
    clickActionButton(param) {
      this.$store.dispatch('loadPublishDigest', { filterCond: param })
    },
    setDateTo() {
      this.publishDateTo = stringDDMMYYaddDays(this.publishDateFrom, this.publishDays)
      // let parts =this.publishDateFrom.split('.');
      // let date = new Date('20' + parts[2], parts[1] - 1, parts[0]);
      // date.setDate(date.getDate() + this.publishDays);
      // this.publishDateTo = `${this.addZero(date.getDate())}.${this.addZero(date.getMonth() + 1)}.${date.getFullYear().toString().substring(2)}`
    },
    clickPrevious() {
      this.publishDateFrom = stringDDMMYYaddDays(this.publishDateFrom, -this.publishDays)
      this.setDateTo()
      this.clickReload()
      // let parts = this.publishDateFrom.split('.');
      // let date = new Date('20' + parts[2], parts[1] - 1, parts[0]);
      // date.setDate(date.getDate() - this.publishDays);
      // this.publishDateFrom = `${this.addZero(date.getDate())}.${this.addZero(date.getMonth() + 1)}.${date.getFullYear().toString().substring(2)}`
      // this.setDateTo()
      // this.clickReload()
    },
    clickNext() {
      this.publishDateFrom = stringDDMMYYaddDays(this.publishDateFrom, this.publishDays)
      this.setDateTo()
      this.clickReload()
      // let parts =this.publishDateFrom.split('.');
      // let date = new Date('20' + parts[2], parts[1] - 1, parts[0]);
      // date.setDate(date.getDate() + this.publishDays);
      // this.publishDateFrom = `${this.addZero(date.getDate())}.${this.addZero(date.getMonth() + 1)}.${date.getFullYear().toString().substring(2)}`
      // this.setDateTo()
      // this.clickReload()
    },
    clickReload() {
      this.$store.dispatch('loadPublishDigest', {
        filterDateFrom: this.publishDateFrom,
        filterDateTo: this.publishDateTo,
      })
    },
    addZero(num) {
      if (num >= 0 && num <= 9) {
        return '0' + num
      } else {
        return num
      }
    },
  },
}
</script>

<style scoped>
.wrapper-date {
}
.input-date {
  width: 80px;
}

.table-publish-footer {
  display: flex;
  flex-direction: row;
}
.color-button {
  border: solid 1px black;
  border-radius: 6px;
  width: 30px;
  height: 30px;
  margin-left: 2px;
}
.action-button {
  border: solid 1px black;
  border-radius: 6px;
  width: 150px;
  height: 30px;
  margin-left: 2px;
  background-color: antiquewhite;
}
.action-button:hover {
  background-color: lightpink;
}
.action-button:focus {
  background-color: darksalmon;
}
.action-button[disabled] {
  background-color: lightgray;
}
.round-30-button {
  border: solid 1px black;
  border-radius: 6px;
  width: 30px;
  height: 30px;
  margin-left: 2px;
  background-color: antiquewhite;
}
.round-30-button:hover {
  background-color: lightpink;
}
.round-30-button:focus {
  background-color: darksalmon;
}
.round-30-button[disabled] {
  background-color: lightgray;
}
.icon-20 {
  width: 20px;
  height: 20px;
}
</style>
