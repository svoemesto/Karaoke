import Vue from 'vue'
import App from './App.vue'
import store from './store'
import router from './router'
import 'bootstrap'
import 'bootstrap/dist/css/bootstrap.min.css'
// import VueSSE from 'vue-sse';

// import SockJS from './lib/sockjs-client/dist/sockjs'
// import SockJS from './assets/js/sockjs'
// import stomp from './assets/js/stomp'
// import StompJs from './assets/js/stompjs'

Vue.config.productionTip = false

new Vue({
  store,
  router,
  // VueSSE,
  render: h => h(App),
  data: {
    stompClientSongs: null
  },
  methods: {
    connect() {

      // this.stompClientSongs = new StompJs.Client({
      //   brokerURL: 'ws://10.0.1.7:8899/apis/message'
      //   // transports: ["websocket"]
      // });
      //
      // this.stompClientSongs.onConnect = (frame) => {
      //   console.log('Connected: ' + frame);
      //   this.stompClientSongs.subscribe('/apis/messages/recordchange', (greeting) => {
      //     this.updateSongsFromMessage(JSON.parse(greeting.body));
      //   });
      // };
      //
      // this.stompClientSongs.onWebSocketError = (error) => {
      //   console.error('Error with websocket', error.message);
      //   console.error('Error with websocket', error.description);
      //   console.error('Error with websocket', error.context);
      // };
      //
      // this.stompClientSongs.onStompError = (frame) => {
      //   console.error('Broker reported error: ' + frame.headers['message']);
      //   console.error('Additional details: ' + frame.body);
      // };
      //
      // this.stompClientSongs.activate();


      // let socketSongs = new SockJS('/apis/message');
      // console.log('socketSongs.transportName: ', socketSongs.transportName);

      // this.stompClientSongs = stomp.Stomp.over(socketSongs);
      // this.stompClientSongs.connect({}, function (frame) {
      //   console.log('Connected: ' + frame);
      //   this.stompClientSongs.subscribe('/apis/messages/recordchange', function (recordchange) {
      //     console.log("Получено сообщение...");
      //     this.updateSongsFromMessage(JSON.parse(recordchange.body));
      //   });
      // });

    },
    updateSongsFromMessage(songs) {
      console.log('updateSongsFromMessage: ', songs)
    },
    loadSongs(params) {
      return this.$store.dispatch('loadSongsAndDictionaries', params)
    },
    async checkUpdateSongs() {
      // Получаем с бэка список айдишников песен, измененных с момента последней проверки
      let ids = JSON.parse(await this.$store.getters.getSongsIdsForUpdate);
      if (ids.length > 0) {
        console.log('ids changed: ', ids);
        // Если список получен, то нужно проверить, есть ли эти песни в pages и для тех что есть
        // сформировать структуру с индексами страниц и песен
        let songsIds = this.$store.getters.getSongsIds.filter(item => ids.includes(item.songId))
        if (songsIds.length > 0) {
          let idsForUpdate = songsIds.map(function(item) {return item.songId})
          let songsForUpdate = JSON.parse(await this.$store.getters.getSongsForUpdateByIds(idsForUpdate));
          let songsAndIndexesForUpdate = songsForUpdate.map(function (song) {
            let indexes = songsIds.find(item => item.songId === song.id)
            return { song: song, songIndex: indexes.songIndex, songId: indexes.id, pageIndex: indexes.pageIndex }
          })
          await this.$store.dispatch('updateSongsByIds', {songsAndIndexesForUpdate: songsAndIndexesForUpdate});
        }

      }
      await this.$store.dispatch('setLastUpdate', {lastUpdate: Date.now()});
    }

  },
  async mounted () {
    // this.connect();
    await this.loadSongs({ filter_author: 'Ундервуд'});
    setInterval(this.checkUpdateSongs, 10_000);
  }
}).$mount('#app')
