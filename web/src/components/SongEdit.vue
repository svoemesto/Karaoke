<template>
  <div :style="styleRoot">
    <div v-if="song">
      <subs-edit :voices="voices" v-if="isSubsEditVisible" @close="closeSubsEdit"/>
      <div class="header">
        <div class="header-song-name">«{{song.songName}}»</div>
        <div class="header-song-description">«{{song.author}}», альбом: «{{song.album}}», год: {{song.year}}</div>
      </div>
      <div class="body">
        <div class="column-1">
          <div class="label-and-input">
            <div class="label">Композиция:</div>
            <input class="input-field" v-model="song.songName">
            <button class="btn-round" @click="undoField('songName')" :disabled="notChanged('songName')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.songName)" :disabled="!song.songName"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('songName')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Исполнитель:</div>
            <input class="input-field" v-model="song.author">
            <button class="btn-round" @click="undoField('author')" :disabled="notChanged('author')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.author)" :disabled="!song.author"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('author')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Год:</div>
            <input class="input-field" v-model="song.year">
            <button class="btn-round" @click="undoField('year')" :disabled="notChanged('year')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.year)" :disabled="!song.year"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('year')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Альбом:</div>
            <input class="input-field" v-model="song.album">
            <button class="btn-round" @click="undoField('album')" :disabled="notChanged('album')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.album)" :disabled="!song.album"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('album')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">№ трека:</div>
            <input class="input-field" v-model="song.track">
            <button class="btn-round" @click="undoField('track')" :disabled="notChanged('track')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.track)" :disabled="!song.track"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('track')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Тональность:</div>
            <input class="input-field" v-model="song.key">
            <button class="btn-round" @click="undoField('key')" :disabled="notChanged('key')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.key)" :disabled="!song.key"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('key')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Темп:</div>
            <input class="input-field" v-model="song.bpm">
            <button class="btn-round" @click="undoField('bpm')" :disabled="notChanged('bpm')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.bpm)" :disabled="!song.bpm"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('bpm')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Тэги:</div>
            <input class="input-field" v-model="song.tags">
            <button class="btn-round" @click="undoField('tags')" :disabled="notChanged('tags')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.tags)" :disabled="!song.tags"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('tags')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Дата:</div>
            <input class="input-field" v-model="song.date">
            <button class="btn-round" @click="undoField('date')" :disabled="notChanged('date')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.date)" :disabled="!song.date"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('date')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Время:</div>
            <input class="input-field" v-model="song.time">
            <button class="btn-round" @click="undoField('time')" :disabled="notChanged('time')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.time)" :disabled="!song.time"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('time')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Корневая папка:</div>
            <input class="input-field" v-model="song.rootFolder">
            <button class="btn-round" @click="undoField('rootFolder')" :disabled="notChanged('rootFolder')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.rootFolder)" :disabled="!song.rootFolder"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('rootFolder')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Имя файла:</div>
            <input class="input-field" v-model="song.fileName">
            <button class="btn-round" @click="undoField('fileName')" :disabled="notChanged('fileName')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.fileName)" :disabled="!song.fileName"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('fileName')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="links-table">
            <div class="links-table-column-1">
              <img class="icon-36" alt="boosty" src="../assets/svg/icon_boosty_color.svg">
            </div>
            <div class="links-table-column-2">
              <div class="label-and-input">
                <img class="icon-24" alt="documents" src="../assets/svg/icon_documents.svg">
                <button v-if="song.idBoosty" class="btn-round-wide" @click="openLinkBoosty"><img alt="open" class="icon-open-wide" src="../assets/svg/icon_open.svg"></button>
                <button v-else class="btn-round-wide" @click="openLinkBoostyNew"><img alt="new" class="icon-new-wide" src="../assets/svg/icon_new.svg"></button>
                <button class="btn-round" @click="getBoostyHeader"><img alt="head" class="icon-texthead" src="../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getBoostyBody"><img alt="body" class="icon-textbody" src="../assets/svg/icon_body.svg"></button>
                <button class="btn-round" @click="getLinkBoosty" :disabled="!song.idBoosty"><img alt="link" class="icon-textlink" src="../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idBoosty">
                <button class="btn-round" @click="undoField('idBoosty')" :disabled="notChanged('idBoosty')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idBoosty)" :disabled="!song.idBoosty"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idBoosty')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="files" src="../assets/svg/icon_files.svg">
                <button v-if="song.idBoostyFiles" class="btn-round-wide" @click="openLinkBoostyFiles"><img alt="open" class="icon-open-wide" src="../assets/svg/icon_open.svg"></button>
                <button v-else class="btn-round-wide" @click="openLinkBoostyFilesNew"><img alt="new" class="icon-new-wide" src="../assets/svg/icon_new.svg"></button>
                <button class="btn-round-wide" @click="getBoostyFilesHeader"><img alt="head" class="icon-texthead-wide" src="../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getLinkBoostyFiles" :disabled="!song.idBoostyFiles"><img alt="link" class="icon-textlink" src="../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idBoostyFiles">
                <button class="btn-round" @click="undoField('idBoostyFiles')" :disabled="notChanged('idBoostyFiles')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idBoostyFiles)" :disabled="!song.idBoostyFiles"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idBoostyFiles')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
              </div>
            </div>
          </div>

          <div class="links-table">
            <div class="links-table-column-1">
              <img class="icon-vk2" alt="vk group" src="../assets/svg/icon_vk2.svg">
            </div>
            <div class="links-table-column-2">
              <div class="label-and-input">
                <img class="icon-24" alt="documents" src="../assets/svg/icon_documents.svg">
                <button class="btn-round-wide" @click="openLinkVkGroup" :disabled="!song.idVk"><img alt="open" class="icon-open-wide" src="../assets/svg/icon_open.svg"></button>
                <button class="btn-round-wide" @click="getVkGroupBody"><img alt="body" class="icon-textbody" src="../assets/svg/icon_body.svg"></button>
                <button class="btn-round" @click="getLinkVkGroup" :disabled="!song.idVk"><img alt="link" class="icon-textlink" src="../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idVk">
                <button class="btn-round" @click="undoField('idVk')" :disabled="notChanged('idVk')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idVk)" :disabled="!song.idVk"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idVk')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
              </div>
            </div>
          </div>

          <div class="links-table">
            <div class="links-table-column-1">
              <img class="icon-36" alt="dzen" src="../assets/svg/icon_dzen.svg">
            </div>
            <div class="links-table-column-2">
              <div class="label-and-input">
                <img class="icon-24" alt="karaoke" src="../assets/svg/icon_microphone.svg">
                <button class="btn-round" @click="openLinkDzenKaraokePlay" :disabled="!song.idYoutubeKaraoke"><img alt="play" class="icon-play" src="../assets/svg/icon_play.svg"></button>
                <button class="btn-round" @click="openLinkDzenKaraokeEdit" :disabled="!song.idYoutubeKaraoke"><img alt="edit" class="icon-edit" src="../assets/svg/icon_edit.svg"></button>
                <button class="btn-round" @click="getDzenKaraokeHeader"><img alt="head" class="icon-texthead" src="../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getDzenKaraokeBody"><img alt="body" class="icon-textbody" src="../assets/svg/icon_body.svg"></button>
                <button class="btn-round" @click="getLinkDzenKaraokePlay" :disabled="!song.idYoutubeKaraoke"><img alt="link" class="icon-textlink" src="../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idYoutubeKaraoke">
                <button class="btn-round" @click="undoField('idYoutubeKaraoke')" :disabled="notChanged('idYoutubeKaraoke')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idYoutubeKaraoke)" :disabled="!song.idYoutubeKaraoke"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idYoutubeKaraoke')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="song" src="../assets/svg/icon_song.svg">
                <button class="btn-round" @click="openLinkDzenLyricsPlay" :disabled="!song.idYoutubeLyrics"><img alt="play" class="icon-play" src="../assets/svg/icon_play.svg"></button>
                <button class="btn-round" @click="openLinkDzenLyricsEdit" :disabled="!song.idYoutubeLyrics"><img alt="edit" class="icon-edit" src="../assets/svg/icon_edit.svg"></button>
                <button class="btn-round" @click="getDzenLyricsHeader"><img alt="head" class="icon-texthead" src="../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getDzenLyricsBody"><img alt="body" class="icon-textbody" src="../assets/svg/icon_body.svg"></button>
                <button class="btn-round" @click="getLinkDzenLyricsPlay" :disabled="!song.idYoutubeLyrics"><img alt="link" class="icon-textlink" src="../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idYoutubeLyrics">
                <button class="btn-round" @click="undoField('idYoutubeLyrics')" :disabled="notChanged('idYoutubeLyrics')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idYoutubeLyrics)" :disabled="!song.idYoutubeLyrics"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idYoutubeLyrics')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
              </div>
            </div>
          </div>

          <div class="links-table">
            <div class="links-table-column-1">
              <img class="icon-36" alt="vk" src="../assets/svg/icon_vk.svg">
            </div>
            <div class="links-table-column-2">
              <div class="label-and-input">
                <img class="icon-24" alt="karaoke" src="../assets/svg/icon_microphone.svg">
                <button class="btn-round-wide" @click="openLinkVkKaraoke" :disabled="!song.idVkKaraoke"><img alt="play" class="icon-play-wide" src="../assets/svg/icon_play.svg"></button>
                <button class="btn-round" @click="getVkKaraokeHeader"><img alt="head" class="icon-texthead" src="../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getVkKaraokeBody"><img alt="body" class="icon-textbody" src="../assets/svg/icon_body.svg"></button>
                <button class="btn-round"  @click="getLinkVkKaraoke" :disabled="!song.idVkKaraoke"><img alt="link" class="icon-textlink" src="../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idVkKaraoke">
                <button class="btn-round" @click="undoField('idVkKaraoke')" :disabled="notChanged('idVkKaraoke')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idVkKaraoke)" :disabled="!song.idVkKaraoke"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idVkKaraoke')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="song" src="../assets/svg/icon_song.svg">
                <button class="btn-round-wide" @click="openLinkVkLyrics" :disabled="!song.idVkLyrics"><img alt="play" class="icon-play-wide" src="../assets/svg/icon_play.svg"></button>
                <button class="btn-round" @click="getVkLyricsHeader"><img alt="head" class="icon-texthead" src="../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getVkLyricsBody"><img alt="body" class="icon-textbody" src="../assets/svg/icon_body.svg"></button>
                <button class="btn-round"  @click="getLinkVkLyrics" :disabled="!song.idVkLyrics"><img alt="link" class="icon-textlink" src="../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idVkLyrics">
                <button class="btn-round" @click="undoField('idVkLyrics')" :disabled="notChanged('idVkLyrics')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idVkLyrics)" :disabled="!song.idVkLyrics"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idVkLyrics')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
              </div>
            </div>
          </div>

          <div class="links-table">
            <div class="links-table-column-1">
              <img class="icon-36" alt="tg" src="../assets/svg/icon_telegram.svg">
            </div>
            <div class="links-table-column-2">
              <div class="label-and-input">
                <img class="icon-24" alt="karaoke" src="../assets/svg/icon_microphone.svg">
                <button class="btn-round-wide" @click="openLinkTelegramKaraoke" :disabled="!song.idTelegramKaraoke"><img alt="play" class="icon-play-wide" src="../assets/svg/icon_play.svg"></button>
                <button class="btn-round-wide" @click="getTelegramKaraokeHeader"><img alt="head" class="icon-texthead-wide" src="../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getLinkTelegramKaraoke" :disabled="!song.idTelegramKaraoke"><img alt="link" class="icon-textlink" src="../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idTelegramKaraoke">
                <button class="btn-round" @click="undoField('idTelegramKaraoke')" :disabled="notChanged('idTelegramKaraoke')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idTelegramKaraoke)" :disabled="!song.idTelegramKaraoke"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idTelegramKaraoke')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="song" src="../assets/svg/icon_song.svg">
                <button class="btn-round-wide" @click="openLinkTelegramLyrics" :disabled="!song.idTelegramLyrics"><img alt="play" class="icon-play-wide" src="../assets/svg/icon_play.svg"></button>
                <button class="btn-round-wide" @click="getTelegramLyricsHeader"><img alt="head" class="icon-texthead-wide" src="../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getLinkTelegramLyrics" :disabled="!song.idTelegramLyrics"><img alt="link" class="icon-textlink" src="../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idTelegramLyrics">
                <button class="btn-round" @click="undoField('idTelegramLyrics')" :disabled="notChanged('idTelegramLyrics')"><img alt="undo" class="icon-undo" src="../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idTelegramLyrics)" :disabled="!song.idTelegramLyrics"><img alt="copy" class="icon-copy" src="../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idTelegramLyrics')"><img alt="paste" class="icon-paste" src="../assets/svg/icon_paste.svg"></button>
              </div>
            </div>
          </div>

        </div>
        <div class="column-2">

        </div>
      </div>
      <div class="footer">
        <button class="btn-round-save-double" @click="save" :disabled="notChanged()"><img alt="save" class="icon-save-double" src="../assets/svg/icon_save.svg"></button>
        <button class="btn-round-double" @click="showSubsEdit"><img alt="save" class="icon-edit-double" src="../assets/svg/icon_edit.svg"></button>
<!--        <div>{{diff}}</div>-->
      </div>
    </div>
    <div v-else>
      Не выбрана песня
    </div>
  </div>
</template>

<script>
import SubsEdit from './SubsEdit'

export default {
  name: "SongEdit",
  components: {
    SubsEdit
  },
  data () {
    return {
      isSubsEditVisible: false,
      voices: []
    };
  },
  computed: {
    styleRoot() {
      return {
        padding: 0,
        margin: 0,
        width: 'auto',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        backgroundColor: 'lightyellow'
      }
    },
    song() { return this.$store.getters.getCurrentSong },
    snapshot() { return this.$store.getters.getSnapshotSong },
    diff() { return this.$store.getters.getDiff },

    linkBoosty() { return this.prefixLinkBoosty + this.song.idBoosty; },
    linkBoostyFiles() { return this.prefixLinkBoosty + this.song.idBoostyFiles; },
    linkBoostyNew() { return this.prefixLinkBoostyNew },
    linkVkGroup() { return this.prefixLinkVkGroup + this.song.idVk; },
    linkDzenKaraokePlay() { return this.prefixLinkDzenPlay + this.song.idYoutubeKaraoke; },
    linkDzenKaraokeEdit() { return this.prefixLinkDzenEdit + this.song.idYoutubeKaraoke; },
    linkDzenLyricsPlay() { return this.prefixLinkDzenPlay + this.song.idYoutubeLyrics; },
    linkDzenLyricsEdit() { return this.prefixLinkDzenEdit + this.song.idYoutubeLyrics; },
    linkVkKaraoke() { return this.prefixLinkVk + this.song.idVkKaraoke; },
    linkVkLyrics() { return this.prefixLinkVk + this.song.idVkLyrics; },
    linkTelegramKaraoke() { return this.prefixLinkTelegram + this.song.idTelegramKaraoke; },
    linkTelegramLyrics() { return this.prefixLinkTelegram + this.song.idTelegramLyrics; },

    prefixLinkBoosty: () => { return 'https://boosty.to/svoemesto/posts/'; },
    prefixLinkBoostyNew: () => { return 'https://boosty.to/svoemesto/new-post'; },
    prefixLinkVkGroup: () => { return 'https://vk.com/wall-'; },
    prefixLinkDzenPlay: () => { return 'https://dzen.ru/video/watch/'; },
    prefixLinkDzenEdit: () => { return 'https://dzen.ru/profile/editor/svoemesto/publications?videoEditorPublicationId='; },
    prefixLinkVk: () => { return 'https://vk.com/video'; },
    prefixLinkTelegram: () => { return 'https://t.me/svoemestokaraoke/'; }
  },
  watch: {
    'song.idBoosty.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idBoosty;
          if (value && value.startsWith(this.prefixLinkBoosty)) {
            const newValue = value.replace(this.prefixLinkBoosty, '').replace('?share=post_link', '');
            this.$store.dispatch('setCurrentSongField', {name: 'idBoosty', value: newValue})
          }
        }
      }
    },
    'song.idBoostyFiles.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idBoostyFiles;
          if (value && value.startsWith(this.prefixLinkBoosty)) {
            const newValue = value.replace(this.prefixLinkBoosty, '').replace('?share=post_link', '');
            this.$store.dispatch('setCurrentSongField', {name: 'idBoostyFiles', value: newValue})
          }
        }
      }
    },
    'song.idVk.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idVk;
          if (value && value.startsWith(this.prefixLinkVkGroup)) {
            const newValue = value.replace(this.prefixLinkVkGroup, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idVk', value: newValue})
          }
        }
      }
    },
    'song.idYoutubeKaraoke.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idYoutubeKaraoke;
          if (value && value.startsWith(this.prefixLinkDzenPlay)) {
            const newValue = value.replace(this.prefixLinkDzenPlay, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idYoutubeKaraoke', value: newValue})
          }
        }
      }
    },
    'song.idYoutubeLyrics.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idYoutubeLyrics;
          if (value && value.startsWith(this.prefixLinkDzenPlay)) {
            const newValue = value.replace(this.prefixLinkDzenPlay, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idYoutubeLyrics', value: newValue})
          }
        }
      }
    },
    'song.idVkKaraoke.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idVkKaraoke;
          if (value && value.startsWith(this.prefixLinkVk)) {
            const newValue = value.replace(this.prefixLinkVk, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idVkKaraoke', value: newValue})
          }
        }
      }
    },
    'song.idVkLyrics.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idVkLyrics;
          if (value && value.startsWith(this.prefixLinkVk)) {
            const newValue = value.replace(this.prefixLinkVk, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idVkLyrics', value: newValue})
          }
        }
      }
    },
    'song.idTelegramKaraoke.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idTelegramKaraoke;
          if (value && value.startsWith(this.prefixLinkTelegram)) {
            const newValue = value.replace(this.prefixLinkTelegram, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idTelegramKaraoke', value: newValue})
          }
        }
      }
    },
    'song.idTelegramLyrics.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idTelegramLyrics;
          if (value && value.startsWith(this.prefixLinkTelegram)) {
            const newValue = value.replace(this.prefixLinkTelegram, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idTelegramLyrics', value: newValue})
          }
        }
      }
    }
  },

  methods: {
    async showSubsEdit() {
      this.voices = JSON.parse(await this.$store.getters.getVoices).voices;
      console.log('voices length: ', this.voices.length);
      console.log('voices typeof: ', typeof this.voices);
      console.log('voices: ', this.voices);
      this.isSubsEditVisible = true;
    },
    closeSubsEdit() {
      this.isSubsEditVisible = false;
    },

    async getLinkBoosty() {
      let value = this.linkBoosty;
      await navigator.clipboard.writeText(value)
      console.log('LinkBoosty: ', value);
    },
    openLinkBoosty() {
      window.open(this.linkBoosty, '_blank');
    },
    async openLinkBoostyNew() {
      let value = await this.$store.getters.getBoostyHeader;
      await navigator.clipboard.writeText(value)
      window.open(this.linkBoostyNew, '_blank');
    },
    async getBoostyHeader() {
      let value = await this.$store.getters.getBoostyHeader;
      await navigator.clipboard.writeText(value)
      console.log('BoostyHeader: ', value);
    },
    async getBoostyBody() {
      let value = await this.$store.getters.getBoostyBody;
      await navigator.clipboard.writeText(value)
      console.log('getBoostyBody: ', value);
    },

    async getLinkBoostyFiles() {
      let value = this.linkBoostyFiles;
      await navigator.clipboard.writeText(value)
      console.log('LinkBoostyFiles: ', value);
    },
    openLinkBoostyFiles() {
      window.open(this.linkBoostyFiles, '_blank');
    },
    async openLinkBoostyFilesNew() {
      let value = await this.$store.getters.getBoostyFilesHeader;
      await navigator.clipboard.writeText(value)
      window.open(this.linkBoostyNew, '_blank');
    },
    async getBoostyFilesHeader() {
      let value = await this.$store.getters.getBoostyFilesHeader;
      await navigator.clipboard.writeText(value)
      console.log('BoostyFilesHeader: ', value);
    },

    async getLinkVkGroup() {
      let value = this.linkVkGroup;
      await navigator.clipboard.writeText(value)
      console.log('LinkBoostyFiles: ', value);
    },
    async getVkGroupBody() {
      let value = await this.$store.getters.getVkGroupBody;
      await navigator.clipboard.writeText(value)
      console.log('VkGroupBody: ', value);
    },
    openLinkVkGroup() {
      window.open(this.linkVkGroup, '_blank');
    },

    async getLinkDzenKaraokePlay() {
      let value = this.linkDzenKaraokePlay;
      await navigator.clipboard.writeText(value)
      console.log('LinkDzenKaraokePlay: ', value);
    },
    openLinkDzenKaraokePlay() {
      window.open(this.linkDzenKaraokePlay, '_blank');
    },
    openLinkDzenKaraokeEdit() {
      window.open(this.linkDzenKaraokeEdit, '_blank');
    },
    async getLinkDzenLyricsPlay() {
      let value = this.linkDzenLyricsPlay;
      await navigator.clipboard.writeText(value)
      console.log('LinkDzenLyricsPlay: ', value);
    },
    openLinkDzenLyricsPlay() {
      window.open(this.linkDzenLyricsPlay, '_blank');
    },
    openLinkDzenLyricsEdit() {
      window.open(this.linkDzenLyricsEdit, '_blank');
    },
    async getDzenKaraokeHeader() {
      let value = await this.$store.getters.getDzenKaraokeHeader;
      await navigator.clipboard.writeText(value)
      console.log('DzenKaraokeHeader: ', value);
    },
    async getDzenKaraokeBody() {
      let value = await this.$store.getters.getDzenKaraokeBody;
      await navigator.clipboard.writeText(value)
      console.log('DzenKaraokeBody: ', value);
    },
    async getDzenLyricsHeader() {
      let value = await this.$store.getters.getDzenLyricsHeader;
      await navigator.clipboard.writeText(value)
      console.log('DzenLyricsHeader: ', value);
    },
    async getDzenLyricsBody() {
      let value = await this.$store.getters.getDzenLyricsBody;
      await navigator.clipboard.writeText(value)
      console.log('DzenLyricsBody: ', value);
    },



    async getLinkVkKaraoke() {
      let value = this.linkVkKaraoke;
      await navigator.clipboard.writeText(value)
      console.log('LinkVkKaraoke: ', value);
    },
    openLinkVkKaraoke() {
      window.open(this.linkVkKaraoke, '_blank');
    },
    async getLinkVkLyrics() {
      let value = this.linkVkLyrics;
      await navigator.clipboard.writeText(value)
      console.log('LinkVkLyrics: ', value);
    },
    openLinkVkLyrics() {
      window.open(this.linkVkLyrics, '_blank');
    },
    async getVkKaraokeHeader() {
      let value = await this.$store.getters.getVkKaraokeHeader;
      await navigator.clipboard.writeText(value)
      console.log('VkKaraokeHeader: ', value);
    },
    async getVkKaraokeBody() {
      let value = await this.$store.getters.getVkKaraokeBody;
      await navigator.clipboard.writeText(value)
      console.log('VkKaraokeBody: ', value);
    },
    async getVkLyricsHeader() {
      let value = await this.$store.getters.getVkLyricsHeader;
      await navigator.clipboard.writeText(value)
      console.log('VkLyricsHeader: ', value);
    },
    async getVkLyricsBody() {
      let value = await this.$store.getters.getVkLyricsBody;
      await navigator.clipboard.writeText(value)
      console.log('getVkLyricsBody: ', value);
    },


    async getLinkTelegramKaraoke() {
      let value = this.linkTelegramKaraoke;
      await navigator.clipboard.writeText(value)
      console.log('LinkTelegramKaraoke: ', value);
    },
    openLinkTelegramKaraoke() {
      window.open(this.linkTelegramKaraoke, '_blank');
    },
    async getLinkTelegramLyrics() {
      let value = this.linkTelegramLyrics;
      await navigator.clipboard.writeText(value)
      console.log('LinkTelegramLyrics: ', value);
    },
    openLinkTelegramLyrics() {
      window.open(this.linkTelegramLyrics, '_blank');
    },
    async getTelegramKaraokeHeader() {
      let value = await this.$store.getters.getTelegramKaraokeHeader;
      await navigator.clipboard.writeText(value)
      console.log('TelegramKaraokeHeader: ', value);
    },
    async getTelegramLyricsHeader() {
      let value = await this.$store.getters.getTelegramLyricsHeader;
      await navigator.clipboard.writeText(value)
      console.log('TelegramLyricsHeader: ', value);
    },

    notChanged(name) {
      if (name) {
        return this.song[name] === this.snapshot[name];
      } else {
        return this.diff.length === 0;
      }
    },
    edit() {

    },
    save() {
      let params = {};
      for (let diff of this.diff) {
        params[diff.name] = diff.new;
      }
      return this.$store.dispatch('save', params)
    },
    undoField(name) {
      return this.$store.dispatch('setCurrentSongField', {name: name, value: this.snapshot[name]})
    },
    async copyToClipboard(value) {
      await navigator.clipboard.writeText(value)
    },
    async pasteFromClipboard(name) {
      await navigator.clipboard.readText().then(data => {
        return this.$store.dispatch('setCurrentSongField', {name: name, value: data})
      });
    }
  }
}
</script>

<style scoped>

.header {
  height: 100px;
  margin: 0;
  margin-bottom: auto;
  padding: 0;
  width: 500px;
  background-color: aquamarine;
}

.header-song-name {
  text-align: center;
  font-size: 24pt;
  line-height: 75%;
  padding-top: 10px;
}

.header-song-description {
  text-align: center;
  font-size: 16pt;
  padding-top: 5px;
}

.body {
  margin: 0;
  background-color:brown;
  width: 800px;
  display: flex;
  flex-direction: row;
}

.links-table {
  margin-top: 10px;
  background-color: white;
  width: 500px;
  display: flex;
  flex-direction: row;
}

.links-table-column-1 {
  width: 84px;
  background-color: white;
  display: flex;
  justify-content: center;
  align-items: center;
}

.links-table-column-2 {
  width: max-content;
  background-color: white;
}

.label-and-input {
  display: flex;
}

.label {
  font-size: small;
  text-align: right;
  width: 110px;
  padding-right: 2px;
  padding-top: 2px;
}

.input-field {
  display: block;
  padding-bottom: 3px;
  width: 310px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}
.input-field:hover {
  background-color: lightyellow;
}
.input-field:focus {
  background-color: cyan;
}

.input-link-field {
  display: block;
  padding-bottom: 3px;
  margin-left: 2px;
  width: 180px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}
.input-link-field:hover {
  background-color: lightyellow;
}
.input-link-field:focus {
  background-color: cyan;
}

.btn-round {
  border: solid 1px black;
  border-radius: 25%;
  width: 24px;
  height: 24px;
  margin-left: 2px;
  background-color: antiquewhite;
}
.btn-round:hover {
  background-color: lightpink;
}
.btn-round:focus {
  background-color: darksalmon;
}
.btn-round[disabled] {
  background-color: lightgray;
}

.btn-round-wide {
  border: solid 1px black;
  border-radius: 6px;
  width: 50px;
  height: 24px;
  margin-left: 2px;
  background-color: antiquewhite;
}
.btn-round-wide:hover {
  background-color: lightpink;
}
.btn-round-wide:focus {
  background-color: darksalmon;
}
.btn-round-wide[disabled] {
  background-color: lightgray;
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

.btn-round-save-double {
  border: solid 1px black;
  border-radius: 6px;
  width: 50px;
  height: 50px;
  margin-left: 2px;
  background-color: red;
}
.btn-round-save-double:hover {
  background-color: darkred;
}
.btn-round-save-double:focus {
  background-color: greenyellow;
}
.btn-round-save-double[disabled] {
  background-color: lightgray;
}

.icon-copy {
  width: 24px;
  height: 24px;
  margin-left: -6px;
  margin-top: -6px;
}



.icon-paste {
  width: 24px;
  height: 24px;
  margin-left: -6px;
  margin-top: -6px;
}

.icon-save {
  width: 18px;
  height: 18px;
  margin-left: -4px;
  margin-top: -5px;
}

.icon-save-double {
  width: 36px;
  height: 36px;
  margin-left: 0;
  margin-top: 0;
}

.icon-undo {
  width: 18px;
  height: 18px;
  margin-left: -4px;
  margin-top: -5px;
}

.icon-new-wide {
  width: 18px;
  height: 18px;
  margin-left: 0;
  margin-top: -5px;
}

.icon-open-wide {
  width: 18px;
  height: 18px;
  margin-left: 0;
  margin-top: -5px;
}

.icon-texthead {
  width: 18px;
  height: 18px;
  margin-left: -4px;
  margin-top: -5px;
}

.icon-texthead-wide {
  width: 18px;
  height: 18px;
  margin-left: 0;
  margin-top: -5px;
}

.icon-textbody {
  width: 18px;
  height: 18px;
  margin-left: -4px;
  margin-top: -5px;
}

.icon-textlink {
  width: 18px;
  height: 18px;
  margin-left: -4px;
  margin-top: -5px;
}

.icon-36 {
  width: 36px;
  height: 36px;
}

.icon-vk2 {
  width: 24px;
  height: 24px;
}

.icon-play {
  width: 24px;
  height: 24px;
  margin-left: -5px;
  margin-top: -5px;
}

.icon-play-wide {
  width: 50px;
  height: 24px;
  margin-left: -5px;
  margin-top: -5px;
}

.icon-edit {
  width: 24px;
  height: 24px;
  margin-left: -5px;
  margin-top: -5px;
}

.icon-edit-double {
  width: 50px;
  height: 50px;
  margin-left: -5px;
  margin-top: -5px;
}


.icon-24 {
  width: 24px;
  height: 24px;
}

.footer {
  margin: 0;
  margin-top: auto;
  width: 800px;
  background-color: dodgerblue;
  display: flex;
  flex-direction: row;
}

.column-1 {
  width: 500px;
  background-color: white;
  height: calc(100vh - 40px - 200px);
}

.column-2 {
  width: max-content;
  background-color: antiquewhite;
  height: calc(100vh - 40px - 200px);
}
</style>