<template>
  <transition name="modal-fade">
    <div class="modal-backdrop">
      <div class="area">
        <header class="modal-header" id="modalTitle">
          <div class="header-song-name">«{{song.songName}}»</div>
          <div class="header-song-description">«{{song.author}}», альбом: «{{song.album}}», год: {{song.year}}</div>
          <button type="button" class="btn-close" @click="close">x</button>
        </header>
        <body>

          <div class="area">
            <div class="header">
              <div class="voice">
                <label for="select-voice" class="label-for-check">Голос:</label>
                <select id="select-voice" v-model="currentVoice" style="width: 100px;">
                  <option v-for="value in lstVoices" :key="value.value" :value="value.value" :label="value.text"/>
                </select>
              </div>
              <div class="sound">
                <label for="sound" class="label-for-sound">Звук:</label>
                <button class="group-button" :class="soundButtonClass('voice')" name="button-sound" type="button" value="voice" @click="setSound('voice')">Голос</button>
                <button class="group-button" :class="soundButtonClass('song')" name="button-sound" type="button" value="song" @click="setSound('song')">Песня</button>
                <button class="group-button" :class="soundButtonClass('minus')" name="button-sound" type="button" value="minus" @click="setSound('minus')">Минус</button>
              </div>
            </div>
            <div class="body">
              <div id="waveform"></div>
              <div id="wave-timeline"></div>
              <div style="width: 100%; height: 100%">
                {{text}}
              </div>

            </div>
            <div class="footer">
            </div>
          </div>

        </body>
        <footer class="modal-footer">
          I'm the default footer!
          <button type="button" class="btn-green" @click="close">Close me!</button>
        </footer>
      </div>
    </div>
  </transition>
</template>

<script>

// import ws from '../assets/js/wavesurfer/wavesurfer'
// import wsMarkers from '../assets/js/wavesurfer/wavesurfer.markers'
// import wsRegions from '../assets/js/wavesurfer/wavesurfer.regions'
// import wsTimeline from '../assets/js/wavesurfer/wavesurfer.timeline'
// import wsCursor from '../assets/js/wavesurfer/wavesurfer.cursor'

export default {
  name: "SubsEdit",
  data() {
    return {
      currentVoice: 0,
      dataVoices: this.voices,
      sound: 'voice',
      // wavesurfer: ws.create({
      //   container: '#waveform',
      //   waveColor: 'violet',
      //   progressColor: 'purple',
      //   backend: 'MediaElement',
      //   autoCenterImmediately: true,
      //   barHeight: 1,
      //   height: 256,
      //   barWidth: 2,
      //   barRadius: 2,
      //   skipLength: 0.016666666666
      // }),
      // pluginCursor: wsCursor.WaveSurfer.cursor.create({
      //   showTime: true,
      //   opacity: 1,
      //   customShowTimeStyle: {
      //     'background-color': '#000',
      //     color: '#fff',
      //     padding: '2px',
      //     'font-size': '10px'
      //   }
      // }),
      // pluginMarkers: wsMarkers.WaveSurfer.markers.create({
      //   markers: []
      // }),
      // pluginRegion: wsRegions.WaveSurfer.regions.create({
      //   regions: []
      // }),
      // pluginTimeline: wsTimeline.WaveSurfer.timeline.create({
      //   container: "#wave-timeline"
      // })
    }
  },
  props: {
    voices: {
      type: Array,
      required: true,
      default: () => []
    },
    song: {
      type: Object,
      required: true
    }
  },
  watch: {
    currentVoice: {
      handler () {
        if (this.currentVoice === this.dataVoices.length) {
          this.dataVoices.push({
            text: '',
            markers: [],
            syllables: []
          })
        }
      }
    }

  },
  computed: {
    voice() {
      return this.dataVoices.length ? this.dataVoices[this.currentVoice] : [];
    },
    text() {
      return this.voice ? this.voice.text : '';
    },
    markers() {
      return this.voice ? this.voice.markers : [];
    },
    syllables() {
      return this.voice ? this.voice.syllables : [];
    },
    lstVoices() {
      let result = [];
      if (this.dataVoices.length > 0) {
        for (let i = 0; i < this.dataVoices.length; i++) {
          result.push( { value: i, text: i + 1 } );
        }
        result.push( { value: this.dataVoices.length, text: "Добавить" } );
      }
      return result;
    }
  },
  methods: {
    soundButtonClass(sound) {
      return sound === this.sound ? 'group-button-active' : ''
    },
    setSound(sound) {
      this.sound = sound;
    },
    close() {
      this.$emit('close');
    }
  }
}
</script>

<style scoped>
.group-button {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
}
.group-button-active {
  background-color: dodgerblue;
}
.area {
  background-color: #4AAE9B;
  display: flex;
  flex-direction: column;
  width: 1280px;
  /*height: 720px;*/
}
.header {
  background-color: lightskyblue;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100px;
}
.header-song-name {
  text-align: center;
  font-size: 24pt;
  line-height: 75%;
}
.header-song-description {
  text-align: center;
  font-size: 16pt;
}
.body {
  background-color: #eeeeee;
  width: 1280px;
  height: 500px;
}
.footer {
  background-color: #2c3e50;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 50px;
}

.modal-backdrop {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: rgba(0, 0, 0, 0.3);
  display: flex;
  justify-content: center;
  align-items: center;
}

.area {
  background: #FFFFFF;
  box-shadow: 2px 2px 20px 1px;
  overflow-x: auto;
  display: flex;
  flex-direction: column;
  width: auto;
  height: auto;
  position: relative;
}

.modal-header,
.modal-footer {
  padding: 15px;
  display: flex;
}

.modal-header {
  border-bottom: 1px solid #eeeeee;
  color: #4AAE9B;
  justify-content: space-between;
}

.modal-footer {
  border-top: 1px solid #eeeeee;
  justify-content: flex-end;
}

.modal-body {
  position: relative;
  padding: 20px 10px;
}

.btn-close {
  border: none;
  font-size: 20px;
  padding: 20px;
  cursor: pointer;
  font-weight: bold;
  color: #4AAE9B;
  background: transparent;
}

.btn-green {
  color: white;
  background: #4AAE9B;
  border: 1px solid #4AAE9B;
  border-radius: 2px;
}

.modal-fade-enter,
.modal-fade-leave-active {
  opacity: 0;
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity .5s ease
}

</style>