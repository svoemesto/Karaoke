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
      style="background-color: #ccffcc"
      title="DONE — готово и расписание в прошлом / не сегодня"
      @click="clickColorButton('STATE_DONE')"
    />
    <button
      class="color-button"
      style="background-color: #ffff00"
      title="TODAY — эфир сегодня в будущем"
      @click="clickColorButton('STATE_TODAY')"
    />
    <button
      class="color-button"
      style="background-color: #33ff33"
      title="ON_AIR — постоянно бесплатна или внутри бесплатного окна"
      @click="clickColorButton('STATE_ON_AIR')"
    />
    <button
      class="color-button"
      style="background-color: #99ccff"
      title="EXCLUSIVE — нет действительного расписания"
      @click="clickColorButton('STATE_EXCLUSIVE')"
    />
    <button
      class="color-button"
      title="IN_WORK — idStatus < 6"
      @click="clickColorButton('STATE_IN_WORK')"
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
 * Компонент «Publish Table Footer»: легенда из пяти состояний песни (DONE / TODAY / ON_AIR /
 * EXCLUSIVE / IN_WORK), управление окном публикаций (дата/диапазон) и командные кнопки
 * (С начала / С сегодня / … / UNPUBLISH / SKIPED). Цвета кнопок синхронизированы с backend —
 * `Song.state.color`, см. `specs/155-song-state-colors/contracts/song-state-color.md` и
 * `archive/docs/features/song-state-colors.md`.
 *
 * @see archive/docs/features/song-state-colors.md
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
      const date = await this.$store.dispatch('getPublicationsDateFrom', { param: param })
      // Эндпоинт возвращает пустую строку для состояний без «живой» даты (EXCLUSIVE,
      // IN_WORK) — сбрасываем фильтр в пустую дату вместо того, чтобы записывать "" в
      // setDateTo() и ломать арифметику.
      this.publishDateFrom = date || ''
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
