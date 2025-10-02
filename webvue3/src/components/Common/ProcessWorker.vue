<template>
  <div class="process_worker">
    <div class="button-with-text-count-waiting">
      <button
          class="btn-round-double"
          @click.left="clickStartStopWorkerButton"
          :disabled="disabled"
      >
        <img v-if="!isWork" alt="start" class="icon-40" src="../../assets/svg/icon_play.svg">
        <img v-else alt="stop" class="icon-40" src="../../assets/svg/icon_stop.svg">
      </button>
      <div class="text-count-waiting" v-text="countWaiting"></div>
    </div>
    <div class="wrapper">
      <div class="process-text" v-text="processName"></div>
      <div class="wrapper-bar">
        <div class="process-progress-bar"
             role="progressbar"
             v-text="processPercentage"
             :style="styleProgressBar"
        ></div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: "ProcessWorker",
  data() {
    return {
      // isWork: false,
      // stopAfterThreadIsDone: false,
      // process: undefined
    }
  },
  computed: {
    process() {
      return this.$store.getters.getWorkingProcess
    },
    isWork() {
      return this.$store.getters.getProcessIsWorking
    },
    stopAfterThreadIsDone() {
      return this.$store.getters.getProcessWillStopAfterThreadIsDone
    },
    countWaiting() {
      return this.$store.getters.getCountWaiting
    },
    disabled() {
      return this.isWork && this.stopAfterThreadIsDone;
    },
    processName() {
      const maxSymbols = 75;
      let name = this.process ? `${this.process.name} /${this.process.type}/ [${this.process.timeLeftStr}]` : '';
      return this.truncateString(name, maxSymbols);
    },
    processPercentage() { return this.process ? `${this.process.percentage}%` : ''},
    styleProgressBar() {
      return {
        fontSize: 'small',
        width: this.processPercentage,
        backgroundImage: 'linear-gradient(45deg, hsla(0, 0%, 100%, .15) 25%, transparent 0, transparent 50%, hsla(0, 0%, 100%, .15) 0, hsla(0, 0%, 100%, .15) 75%, transparent 0, transparent)',
        backgroundSize: '1rem 1rem',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        color: '#fff',
        justifyContent: 'center',
        textAlign: 'center',
        whiteSpace: 'nowrap',
        backgroundColor: '#007bff',
        transition: 'width .6s ease',
        animation: '1s linear infinite progress-bar-stripes',
      }
    }
  },
  methods: {
    clickStartStopWorkerButton() {
      this.$store.dispatch('startStopProcessWorker')
    },
    checkUpdateProcessesWorker() {
      this.$store.dispatch('getProcessesWorkerStatusPromise').then(data => {
        let status = JSON.parse(data);
        let isWork = status.isWork;
        let stopAfterThreadIsDone = status.stopAfterThreadIsDone;
        this.$store.dispatch("setProcessIsWorking", isWork);
        this.$store.dispatch("setProcessWillStopAfterThreadIsDone", stopAfterThreadIsDone);
      })
    },
    truncateString(name, maxSymbols) {
      if (name.length <= maxSymbols) {
        return name;
      }

      if (maxSymbols <= 3) {
        return '...';
      }

      const charsToShow = maxSymbols - 3; // 3 символа для троеточия
      const frontChars = Math.ceil(charsToShow / 2);
      const backChars = Math.floor(charsToShow / 2);

      const front = name.substring(0, frontChars);
      const back = name.substring(name.length - backChars);

      return front + '...' + back;
    }
  },
  mounted() {
    this.checkUpdateProcessesWorker();
  }
}
</script>

<style scoped>

.process_worker {
  display: flex;
  margin: 0 10px;
}
.worker-start-stop-button {

}
.icon-start {

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
  margin-left: -1px;
}

.wrapper {
  display: flex;
  flex-direction: column;
  margin-left: 10px;
}
.process-text {
  font-size: small;
}
.button-with-text-count-waiting {
  position: relative;
}
.text-count-waiting {
  font-size: x-small;
  color: white;
  position: absolute;
  pointer-events: none;
  top: 100%;
  left: 100%;
  transform: translate(-50%, -50%);
  padding: 0 4px;
  border-radius: 5px;
  background-color: gray;
}
.wrapper-bar {
  display: flex;
  height: 1rem;
  overflow: hidden;
  line-height: 0;
  font-size: .75rem;
  background-color: #e9ecef;
  border-radius: .25rem;
}
</style>