<template>
  <div class="table-publish-footer">
    <button class="round-30-button" @click="clickReload" title="Обновить">
      <img alt="reload" class="icon-20" src="../../../assets/svg/icon_filter.svg">
    </button>
    <button class="round-30-button" @click="clickPrevious">
      <img alt="previous" class="icon-20" title="[" src="../../../assets/svg/icon_fast_backward.svg">
    </button>
    <div class="wrapper-date">
      <input class="input-date" v-model="publishDays">
    </div>
    <button class="round-30-button" @click="clickNext">
      <img alt="previous" class="icon-20" title="]" src="../../../assets/svg/icon_fast_forward.svg">
    </button>
    <div class="wrapper-date">
      <input class="input-date" v-model="publishDateFrom">
    </div>
    <div class="wrapper-date">
      <input class="input-date" v-model="publishDateTo">
    </div>
    <button class="color-button" style="background-color: #7FFFD4" @click="clickColorButton('STATE_ALL_DONE')" title="Полностью готово" />
    <button class="color-button" style="background-color: #BDB76B;" @click="clickColorButton('STATE_OVERDUE')" title="Просрочено" />
    <button class="color-button" style="background-color: #FFFF00;" @click="clickColorButton('STATE_TODAY')" title="Сегодня" />
    <button class="color-button" style="background-color: #DCDCDC;" @click="clickColorButton('STATE_ALL_UPLOADED')" title="Готово к публикации" />
    <button class="color-button" style="background-color: #87CEFA;" @click="clickColorButton('STATE_WO_TG')" title="Нет TG" />
    <button class="color-button" style="background-color: #FFDAB9;" @click="clickColorButton('STATE_WO_VK')" title="Нет VK" />
    <button class="color-button" style="background-color: #FF8000;" @click="clickColorButton('STATE_WO_DZEN')" title="Нет DZEN" />
    <button class="color-button" style="background-color: #FFC880;" @click="clickColorButton('STATE_WO_VKG')" title="Нет VKG" />
    <button class="color-button" style="background-color: #FFFFFF;" @click="clickColorButton('STATUS_0')" title="Статус: NONE" />
    <button class="color-button" style="background-color: #DDA0DD;" @click="clickColorButton('STATUS_1')" title="Статус: Текст найден" />
    <button class="color-button" style="background-color: #EE82EE;" @click="clickColorButton('STATUS_2')" title="Статус: Текст проверен" />
    <button class="color-button" style="background-color: #98FB98;" @click="clickColorButton('STATUS_3')" title="Статус: Проект создан" />
    <button class="color-button" style="background-color: #00FF7F;" @click="clickColorButton('STATUS_4')" title="Статус: Проект проверен" />
    <button class="color-button" style="background-color: #00FF00;" @click="clickColorButton('STATUS_6')" title="Статус: Готово" />
    <button class="action-button" @click="clickActionButton('all')">С начала</button>
    <button class="action-button" @click="clickActionButton('fromtoday')">С сегодня</button>
    <button class="action-button" @click="clickActionButton('fromnotpublish')">С незавершенной</button>
    <button class="action-button" @click="clickActionButton('fromnotcheck')">С непроверенной</button>
    <button class="action-button" @click="clickActionButton('fromnotdone')">С неготовой</button>
    <button class="action-button" @click="clickActionButton('unpublish')">UNPUBLISH</button>
    <button class="action-button" @click="clickActionButton('skiped')">SKIPED</button>
  </div>
</template>

<script>
import {stringDDMMYYaddDays} from "../../../lib/utils";

export default {
  name: "PublishTableFooter",
  data() {
    return {
      publishDateFrom: '',
      publishDateTo: '',
      publishDays: 90
    }
  },
  methods: {
    async clickColorButton(param) {
      this.publishDateFrom = await this.$store.dispatch('getPublicationsDateFrom', { param: param})
      this.setDateTo();
      this.$store.dispatch('loadPublishDigest', { filterDateFrom: this.publishDateFrom, filterDateTo: this.publishDateTo})
    },
    clickActionButton(param) {
      this.$store.dispatch('loadPublishDigest', { filterCond: param})
    },
    setDateTo() {
      this.publishDateTo = stringDDMMYYaddDays(this.publishDateFrom, this.publishDays);
      // let parts =this.publishDateFrom.split('.');
      // let date = new Date('20' + parts[2], parts[1] - 1, parts[0]);
      // date.setDate(date.getDate() + this.publishDays);
      // this.publishDateTo = `${this.addZero(date.getDate())}.${this.addZero(date.getMonth() + 1)}.${date.getFullYear().toString().substring(2)}`
    },
    clickPrevious() {
      this.publishDateFrom = stringDDMMYYaddDays(this.publishDateFrom, -this.publishDays);
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
      this.publishDateFrom = stringDDMMYYaddDays(this.publishDateFrom, this.publishDays);
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
      this.$store.dispatch('loadPublishDigest', { filterDateFrom: this.publishDateFrom, filterDateTo: this.publishDateTo})
    },
    addZero(num) {
      if (num >= 0 && num <= 9) {
        return '0' + num;
      } else {
        return num;
      }
    }
  }
}
</script>

<style scoped>

.wrapper-date {

}
.input-date{
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