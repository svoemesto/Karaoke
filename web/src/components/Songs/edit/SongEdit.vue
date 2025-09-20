<template>
  <div :style="styleRoot">
    <div v-if="song">
      <subs-edit v-if="isSubsEditVisible" :voices="voices" :song="song" @close="closeSubsEdit"/>
      <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />
      <!-- Заголовок с названием песни и автора-->
      <div class="header">
        <div class="header-song-author">{{song.author}}</div>
        <div class="header-song-album">{{song.year}} - {{song.album}}</div>
        <div class="header-song-name">{{song.songName}}</div>
      </div>
      <!-- Тело-->
      <div class="body">
        <!-- Первый столбец тела -->
        <div class="column-1">
          <div class="label-and-input">
            <div class="label">Композиция:</div>
            <input class="input-field" v-model="song.songName">
            <button class="btn-round" @click="undoField('songName')" :disabled="notChanged('songName')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.songName)" :disabled="!song.songName"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('songName')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Исполнитель:</div>
            <input class="input-field" v-model="song.author">
            <button class="btn-round" @click="undoField('author')" :disabled="notChanged('author')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.author)" :disabled="!song.author"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('author')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Год:</div>
            <input class="input-field" v-model="song.year">
            <button class="btn-round" @click="undoField('year')" :disabled="notChanged('year')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.year)" :disabled="!song.year"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('year')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Альбом:</div>
            <input class="input-field" v-model="song.album">
            <button class="btn-round" @click="undoField('album')" :disabled="notChanged('album')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.album)" :disabled="!song.album"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('album')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">№ трека:</div>
            <input class="input-field" v-model="song.track">
            <button class="btn-round" @click="undoField('track')" :disabled="notChanged('track')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.track)" :disabled="!song.track"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('track')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Тональность:</div>
            <input class="input-field" v-model="song.key">
            <button class="btn-round" @click="undoField('key')" :disabled="notChanged('key')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.key)" :disabled="!song.key"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('key')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Темп:</div>
            <input class="input-field" v-model="song.bpm">
            <button class="btn-round" @click="undoField('bpm')" :disabled="notChanged('bpm')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.bpm)" :disabled="!song.bpm"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('bpm')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Тэги:</div>
            <input class="input-field" v-model="song.tags">
            <button class="btn-round" @click="undoField('tags')" :disabled="notChanged('tags')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.tags)" :disabled="!song.tags"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('tags')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Дата:</div>
            <input class="input-field" v-model="song.date">
            <button class="btn-round" @click="undoField('date')" :disabled="notChanged('date')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.date)" :disabled="!song.date"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('date')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Время:</div>
            <input class="input-field" v-model="song.time">
            <button class="btn-round" @click="undoField('time')" :disabled="notChanged('time')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.time)" :disabled="!song.time"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('time')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Корневая папка:</div>
            <input class="input-field" v-model="song.rootFolder">
            <button class="btn-round" @click="undoField('rootFolder')" :disabled="notChanged('rootFolder')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.rootFolder)" :disabled="!song.rootFolder"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('rootFolder')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Имя файла:</div>
            <input class="input-field" v-model="song.fileName">
            <button class="btn-round" @click="undoField('fileName')" :disabled="notChanged('fileName')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.fileName)" :disabled="!song.fileName"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('fileName')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="label-and-input">
            <div class="label">Версия:</div>
            <input class="input-field" v-model="song.resultVersion">
            <button class="btn-round" @click="undoField('resultVersion')" :disabled="notChanged('resultVersion')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
            <button class="btn-round" @click="copyToClipboard(song.resultVersion)" :disabled="!song.resultVersion"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
            <button class="btn-round" @click="pasteFromClipboard('resultVersion')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
          </div>
          <div class="links-table">
            <div class="links-table-column-1">
              <img class="icon-36" alt="boosty" src="../../../assets/svg/icon_boosty_color.svg">
            </div>
            <div class="links-table-column-2">
              <div class="label-and-input">
                <img class="icon-24" alt="documents" src="../../../assets/svg/icon_documents.svg">
                <button v-if="song.idBoosty" class="btn-round-wide" @click="openLinkBoosty"><img alt="open" class="icon-open-wide" src="../../../assets/svg/icon_open.svg"></button>
                <button v-else class="btn-round-wide" @click="openLinkBoostyNew"><img alt="new" class="icon-new-wide" src="../../../assets/svg/icon_new.svg"></button>
                <button class="btn-round" @click="getBoostyHeader"><img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getBoostyBody"><img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg"></button>
                <button class="btn-round" @click="getLinkBoosty" :disabled="!song.idBoosty"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idBoosty">
                <input class="input-field-version" v-model="song.versionBoosty">
                <button class="btn-round" @click="undoField('idBoosty')" :disabled="notChanged('idBoosty')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idBoosty)" :disabled="!song.idBoosty"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idBoosty')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="files" src="../../../assets/svg/icon_files.svg">
                <button v-if="song.idBoostyFiles" class="btn-round-wide" @click="openLinkBoostyFiles"><img alt="open" class="icon-open-wide" src="../../../assets/svg/icon_open.svg"></button>
                <button v-else class="btn-round-wide" @click="openLinkBoostyFilesNew"><img alt="new" class="icon-new-wide" src="../../../assets/svg/icon_new.svg"></button>
                <button class="btn-round-wide" @click="getBoostyFilesHeader"><img alt="head" class="icon-texthead-wide" src="../../../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getLinkBoostyFiles" :disabled="!song.idBoostyFiles"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idBoostyFiles">
                <input class="input-field-version" v-model="song.versionBoostyFiles">
                <button class="btn-round" @click="undoField('idBoostyFiles')" :disabled="notChanged('idBoostyFiles')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idBoostyFiles)" :disabled="!song.idBoostyFiles"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idBoostyFiles')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="documents" src="../../../assets/svg/icon_sponsr.svg">
                <button v-if="song.idSponsr" class="btn-round-wide" @click="openLinkSponsr"><img alt="open" class="icon-open-wide" src="../../../assets/svg/icon_open.svg"></button>
                <button v-else class="btn-round-wide" @click="openLinkSponsrNew"><img alt="new" class="icon-new-wide" src="../../../assets/svg/icon_new.svg"></button>
                <button class="btn-round" @click="getSponsrHeader"><img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getSponsrBody"><img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg"></button>
                <button class="btn-round" @click="getLinkSponsrPlay" :disabled="!song.idSponsr"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idSponsr">
                <input class="input-field-version" v-model="song.versionSponsr">
                <button class="btn-round" @click="undoField('idSponsr')" :disabled="notChanged('idSponsr')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idSponsr)" :disabled="!song.idSponsr"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idSponsr')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
              </div>
            </div>
          </div>

          <div class="links-table">
            <div class="links-table-column-1">
              <img class="icon-vk2" alt="vk group" src="../../../assets/svg/icon_vk2.svg">
            </div>
            <div class="links-table-column-2">
              <div class="label-and-input">
                <img class="icon-24" alt="documents" src="../../../assets/svg/icon_documents.svg">
                <button class="btn-round-wide" @click="openLinkVkGroup" :disabled="!song.idVk"><img alt="open" class="icon-open-wide" src="../../../assets/svg/icon_open.svg"></button>
                <button class="btn-round-wide" @click="getVkGroupBody"><img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg"></button>
                <button class="btn-round" @click="getLinkVkGroup" :disabled="!song.idVk"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idVk">
                <button class="btn-round" @click="undoField('idVk')" :disabled="notChanged('idVk')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idVk)" :disabled="!song.idVk"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idVk')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
              </div>
            </div>
          </div>

          <div class="links-table">
            <div class="links-table-column-1">
              <img class="icon-36" alt="dzen" src="../../../assets/svg/icon_dzen.svg">
            </div>
            <div class="links-table-column-2">
              <div class="label-and-input">
                <img class="icon-24" alt="karaoke" src="../../../assets/svg/icon_microphone.svg">
                <button class="btn-round" @click="openLinkDzenKaraokePlay" :disabled="!song.idDzenKaraoke"><img alt="play" class="icon-play" src="../../../assets/svg/icon_play.svg"></button>
                <button class="btn-round" @click="openLinkDzenKaraokeEdit" :disabled="!song.idDzenKaraoke"><img alt="edit" class="icon-edit" src="../../../assets/svg/icon_edit.svg"></button>
                <button class="btn-round" @click="getDzenKaraokeHeader"><img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getDzenKaraokeBody"><img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg"></button>
                <button class="btn-round" @click="getLinkDzenKaraokePlay" :disabled="!song.idDzenKaraoke"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idDzenKaraoke">
                <input class="input-field-version" v-model="song.versionDzenKaraoke">
                <button class="btn-round" @click="undoField('idDzenKaraoke')" :disabled="notChanged('idDzenKaraoke')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idDzenKaraoke)" :disabled="!song.idDzenKaraoke"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idDzenKaraoke')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="song" src="../../../assets/svg/icon_song.svg">
                <button class="btn-round" @click="openLinkDzenLyricsPlay" :disabled="!song.idDzenLyrics"><img alt="play" class="icon-play" src="../../../assets/svg/icon_play.svg"></button>
                <button class="btn-round" @click="openLinkDzenLyricsEdit" :disabled="!song.idDzenLyrics"><img alt="edit" class="icon-edit" src="../../../assets/svg/icon_edit.svg"></button>
                <button class="btn-round" @click="getDzenLyricsHeader"><img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getDzenLyricsBody"><img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg"></button>
                <button class="btn-round" @click="getLinkDzenLyricsPlay" :disabled="!song.idDzenLyrics"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idDzenLyrics">
                <input class="input-field-version" v-model="song.versionDzenLyrics">
                <button class="btn-round" @click="undoField('idDzenLyrics')" :disabled="notChanged('idDzenLyrics')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idDzenLyrics)" :disabled="!song.idDzenLyrics"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idDzenLyrics')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="chords" src="../../../assets/svg/icon_chords.svg">
                <button class="btn-round" @click="openLinkDzenChordsPlay" :disabled="!song.idDzenChords"><img alt="play" class="icon-play" src="../../../assets/svg/icon_play.svg"></button>
                <button class="btn-round" @click="openLinkDzenChordsEdit" :disabled="!song.idDzenChords"><img alt="edit" class="icon-edit" src="../../../assets/svg/icon_edit.svg"></button>
                <button class="btn-round" @click="getDzenChordsHeader"><img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getDzenChordsBody"><img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg"></button>
                <button class="btn-round" @click="getLinkDzenChordsPlay" :disabled="!song.idDzenChords"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idDzenChords">
                <input class="input-field-version" v-model="song.versionDzenChords">
                <button class="btn-round" @click="undoField('idDzenChords')" :disabled="notChanged('idDzenChords')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idDzenChords)" :disabled="!song.idDzenChords"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idDzenChords')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="melody" src="../../../assets/svg/icon_melody.svg">
                <button class="btn-round" @click="openLinkDzenTabsPlay" :disabled="!song.idDzenMelody"><img alt="play" class="icon-play" src="../../../assets/svg/icon_play.svg"></button>
                <button class="btn-round" @click="openLinkDzenTabsEdit" :disabled="!song.idDzenMelody"><img alt="edit" class="icon-edit" src="../../../assets/svg/icon_edit.svg"></button>
                <button class="btn-round" @click="getDzenTabsHeader"><img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getDzenTabsBody"><img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg"></button>
                <button class="btn-round" @click="getLinkDzenTabsPlay" :disabled="!song.idDzenMelody"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idDzenMelody">
                <input class="input-field-version" v-model="song.versionDzenMelody">
                <button class="btn-round" @click="undoField('idDzenMelody')" :disabled="notChanged('idDzenMelody')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idDzenMelody)" :disabled="!song.idDzenMelody"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idDzenMelody')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
              </div>
            </div>
          </div>

          <div class="links-table">
            <div class="links-table-column-1">
              <img class="icon-36" alt="vk" src="../../../assets/svg/icon_vk.svg">
            </div>
            <div class="links-table-column-2">
              <div class="label-and-input">
                <img class="icon-24" alt="karaoke" src="../../../assets/svg/icon_microphone.svg">
                <button class="btn-round-wide" @click="openLinkVkKaraoke" :disabled="!song.idVkKaraoke"><img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg"></button>
                <button class="btn-round" @click="getVkKaraokeHeader"><img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getVkKaraokeBody"><img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg"></button>
                <button class="btn-round"  @click="getLinkVkKaraoke" :disabled="!song.idVkKaraoke"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idVkKaraoke">
                <input class="input-field-version" v-model="song.versionVkKaraoke">
                <button class="btn-round" @click="undoField('idVkKaraoke')" :disabled="notChanged('idVkKaraoke')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idVkKaraoke)" :disabled="!song.idVkKaraoke"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idVkKaraoke')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="song" src="../../../assets/svg/icon_song.svg">
                <button class="btn-round-wide" @click="openLinkVkLyrics" :disabled="!song.idVkLyrics"><img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg"></button>
                <button class="btn-round" @click="getVkLyricsHeader"><img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getVkLyricsBody"><img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg"></button>
                <button class="btn-round"  @click="getLinkVkLyrics" :disabled="!song.idVkLyrics"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idVkLyrics">
                <input class="input-field-version" v-model="song.versionVkLyrics">
                <button class="btn-round" @click="undoField('idVkLyrics')" :disabled="notChanged('idVkLyrics')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idVkLyrics)" :disabled="!song.idVkLyrics"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idVkLyrics')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="song" src="../../../assets/svg/icon_chords.svg">
                <button class="btn-round-wide" @click="openLinkVkChords" :disabled="!song.idVkChords"><img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg"></button>
                <button class="btn-round" @click="getVkChordsHeader"><img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getVkChordsBody"><img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg"></button>
                <button class="btn-round"  @click="getLinkVkChords" :disabled="!song.idVkChords"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idVkChords">
                <input class="input-field-version" v-model="song.versionVkChords">
                <button class="btn-round" @click="undoField('idVkChords')" :disabled="notChanged('idVkChords')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idVkChords)" :disabled="!song.idVkChords"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idVkChords')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="song" src="../../../assets/svg/icon_melody.svg">
                <button class="btn-round-wide" @click="openLinkVkTabs" :disabled="!song.idVkMelody"><img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg"></button>
                <button class="btn-round" @click="getVkTabsHeader"><img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getVkTabsBody"><img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg"></button>
                <button class="btn-round" @click="getLinkVkTabs" :disabled="!song.idVkMelody"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idVkMelody">
                <input class="input-field-version" v-model="song.versionVkMelody">
                <button class="btn-round" @click="undoField('idVkMelody')" :disabled="notChanged('idVkMelody')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idVkMelody)" :disabled="!song.idVkMelody"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idVkMelody')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
              </div>
            </div>
          </div>

          <div class="links-table">
            <div class="links-table-column-1">
              <img class="icon-36" alt="pl" src="../../../assets/svg/icon_pl.svg">
            </div>
            <div class="links-table-column-2">
              <div class="label-and-input">
                <img class="icon-24" alt="karaoke" src="../../../assets/svg/icon_microphone.svg">
                <button class="btn-round" @click="openLinkPlKaraokePlay" :disabled="!song.idPlKaraoke"><img alt="play" class="icon-play" src="../../../assets/svg/icon_play.svg"></button>
                <button class="btn-round" @click="openLinkPlKaraokeEdit" :disabled="!song.idPlKaraoke"><img alt="edit" class="icon-edit" src="../../../assets/svg/icon_edit.svg"></button>
                <button class="btn-round" @click="getPlKaraokeHeader"><img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getPlKaraokeBody"><img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg"></button>
                <button class="btn-round" @click="getLinkPlKaraokePlay" :disabled="!song.idPlKaraoke"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idPlKaraoke">
                <input class="input-field-version" v-model="song.versionPlKaraoke">
                <button class="btn-round" @click="undoField('idPlKaraoke')" :disabled="notChanged('idPlKaraoke')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idPlKaraoke)" :disabled="!song.idPlKaraoke"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idPlKaraoke')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="song" src="../../../assets/svg/icon_song.svg">
                <button class="btn-round" @click="openLinkPlLyricsPlay" :disabled="!song.idPlLyrics"><img alt="play" class="icon-play" src="../../../assets/svg/icon_play.svg"></button>
                <button class="btn-round" @click="openLinkPlLyricsEdit" :disabled="!song.idPlLyrics"><img alt="edit" class="icon-edit" src="../../../assets/svg/icon_edit.svg"></button>
                <button class="btn-round" @click="getPlLyricsHeader"><img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getPlLyricsBody"><img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg"></button>
                <button class="btn-round" @click="getLinkPlLyricsPlay" :disabled="!song.idPlLyrics"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idPlLyrics">
                <input class="input-field-version" v-model="song.versionPlLyrics">
                <button class="btn-round" @click="undoField('idPlLyrics')" :disabled="notChanged('idPlLyrics')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idPlLyrics)" :disabled="!song.idPlLyrics"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idPlLyrics')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="song" src="../../../assets/svg/icon_chords.svg">
                <button class="btn-round" @click="openLinkPlChordsPlay" :disabled="!song.idPlChords"><img alt="play" class="icon-play" src="../../../assets/svg/icon_play.svg"></button>
                <button class="btn-round" @click="openLinkPlChordsEdit" :disabled="!song.idPlChords"><img alt="edit" class="icon-edit" src="../../../assets/svg/icon_edit.svg"></button>
                <button class="btn-round" @click="getPlChordsHeader"><img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getPlChordsBody"><img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg"></button>
                <button class="btn-round" @click="getLinkPlChordsPlay" :disabled="!song.idPlChords"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idPlChords">
                <input class="input-field-version" v-model="song.versionPlChords">
                <button class="btn-round" @click="undoField('idPlChords')" :disabled="notChanged('idPlChords')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idPlChords)" :disabled="!song.idPlChords"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idPlChords')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="song" src="../../../assets/svg/icon_melody.svg">
                <button class="btn-round" @click="openLinkPlTabsPlay" :disabled="!song.idPlMelody"><img alt="play" class="icon-play" src="../../../assets/svg/icon_play.svg"></button>
                <button class="btn-round" @click="openLinkPlTabsEdit" :disabled="!song.idPlMelody"><img alt="edit" class="icon-edit" src="../../../assets/svg/icon_edit.svg"></button>
                <button class="btn-round" @click="getPlTabsHeader"><img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getPlTabsBody"><img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg"></button>
                <button class="btn-round" @click="getLinkPlTabsPlay" :disabled="!song.idPlMelody"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idPlMelody">
                <input class="input-field-version" v-model="song.versionPlMelody">
                <button class="btn-round" @click="undoField('idPlMelody')" :disabled="notChanged('idPlMelody')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idPlMelody)" :disabled="!song.idPlMelody"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idPlMelody')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
              </div>
            </div>
          </div>

          <div class="links-table">
            <div class="links-table-column-1">
              <img class="icon-36" alt="tg" src="../../../assets/svg/icon_telegram.svg">
            </div>
            <div class="links-table-column-2">
              <div class="label-and-input">
                <img class="icon-24" alt="karaoke" src="../../../assets/svg/icon_microphone.svg">
                <button class="btn-round-wide" @click="openLinkTelegramKaraoke" :disabled="!song.idTelegramKaraoke"><img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg"></button>
                <button class="btn-round-wide" @click="getTelegramKaraokeHeader"><img alt="head" class="icon-texthead-wide" src="../../../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getLinkTelegramKaraoke" :disabled="!song.idTelegramKaraoke"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idTelegramKaraoke">
                <input class="input-field-version" v-model="song.versionTelegramKaraoke">
                <button class="btn-round" @click="undoField('idTelegramKaraoke')" :disabled="notChanged('idTelegramKaraoke')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idTelegramKaraoke)" :disabled="!song.idTelegramKaraoke"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idTelegramKaraoke')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="song" src="../../../assets/svg/icon_song.svg">
                <button class="btn-round-wide" @click="openLinkTelegramLyrics" :disabled="!song.idTelegramLyrics"><img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg"></button>
                <button class="btn-round-wide" @click="getTelegramLyricsHeader"><img alt="head" class="icon-texthead-wide" src="../../../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getLinkTelegramLyrics" :disabled="!song.idTelegramLyrics"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idTelegramLyrics">
                <input class="input-field-version" v-model="song.versionTelegramLyrics">
                <button class="btn-round" @click="undoField('idTelegramLyrics')" :disabled="notChanged('idTelegramLyrics')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idTelegramLyrics)" :disabled="!song.idTelegramLyrics"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idTelegramLyrics')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="song" src="../../../assets/svg/icon_chords.svg">
                <button class="btn-round-wide" @click="openLinkTelegramChords" :disabled="!song.idTelegramChords"><img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg"></button>
                <button class="btn-round-wide" @click="getTelegramChordsHeader"><img alt="head" class="icon-texthead-wide" src="../../../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getLinkTelegramChords" :disabled="!song.idTelegramChords"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idTelegramChords">
                <input class="input-field-version" v-model="song.versionTelegramChords">
                <button class="btn-round" @click="undoField('idTelegramChords')" :disabled="notChanged('idTelegramChords')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idTelegramChords)" :disabled="!song.idTelegramChords"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idTelegramChords')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="song" src="../../../assets/svg/icon_melody.svg">
                <button class="btn-round-wide" @click="openLinkTelegramTabs" :disabled="!song.idTelegramMelody"><img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg"></button>
                <button class="btn-round-wide" @click="getTelegramTabsHeader"><img alt="head" class="icon-texthead-wide" src="../../../assets/svg/icon_head.svg"></button>
                <button class="btn-round" @click="getLinkTelegramTabs" :disabled="!song.idTelegramMelody"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>
                <input class="input-link-field" v-model="song.idTelegramMelody">
                <input class="input-field-version" v-model="song.versionTelegramMelody">
                <button class="btn-round" @click="undoField('idTelegramMelody')" :disabled="notChanged('idTelegramMelody')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>
                <button class="btn-round" @click="copyToClipboard(song.idTelegramMelody)" :disabled="!song.idTelegramMelody"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>
                <button class="btn-round" @click="pasteFromClipboard('idTelegramMelody')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>
              </div>
            </div>
          </div>

          <div class="links-table">
            <div class="links-table-column-1">
              Статус
            </div>
            <div class="links-table-column-2">
              <button class="group-button" :class="statusButtonClass(0)" type="button" value="0" @click="setStatus(0)">❎</button>
              <button class="group-button" :class="statusButtonClass(1)" type="button" value="1" @click="setStatus(1)">Тxt🛠</button>
              <button class="group-button" :class="statusButtonClass(2)" type="button" value="2" @click="setStatus(2)">Txt✅</button>
              <button class="group-button" :class="statusButtonClass(3)" type="button" value="3" @click="setStatus(3)">Prj🛠</button>
              <button class="group-button" :class="statusButtonClass(4)" type="button" value="4" @click="setStatus(4)">Prj✅</button>
              <button class="group-button" :class="statusButtonClass(6)" type="button" value="6" @click="setStatus(6)">✅</button>
            </div>
          </div>

        </div>
        <!-- Второй столбец тела -->
        <div class="column-2">
          <div class="picture-author">
            <img class="image-author" alt="Author image" :src="imageAuthorBase64">
          </div>
          <div class="picture-album">
            <img class="image-album" alt="Album image" :src="imageAlbumBase64">
          </div>
          <div class="create-picture-buttons-group">
            <button class="group-button" @click="openMainLink" title="Открыть на сайте">Открыть на сайте sm-karaoke.ru</button>
            <button class="group-button" @click="updateRemote" title="Обновить на сервере">Обновить на сервере</button>
            <button class="group-button" @click="toSyncRemote" title="Добавить в SYNC-таблицу на сервере">Добавить в SYNC-таблицу на сервере</button>
            <button class="group-button" @click="copyFieldsFromAnother" title="Скопировать поля из другой песни">Скопировать поля из другой песни</button>
            <button class="group-button" @click="createPictureBoostyTeaser" title="Создать картинку Boosty Teaser">Создать картинку Boosty Teaser</button>
            <button class="group-button" @click="createPictureBoostyFiles" title="Создать картинку Boosty Files">Создать картинку Boosty Files</button>
            <button class="group-button" @click="createPictureSponsrTeaser" title="Создать картинку Sponsr Teaser">Создать картинку Sponsr Files</button>
<!--            <button class="group-button" @click="createPictureVK" title="Создать картинку VK">Создать картинку VK</button>-->
<!--            <button class="group-button" @click="createPictureVKlink" title="Создать картинку VKlink">Создать картинку VKlink</button>-->
            <button class="group-button" @click="createPictureKaraoke" title="Создать картинку KARAOKE">Создать картинку KARAOKE</button>
            <button class="group-button" @click="createPictureLyrics" title="Создать картинку LYRICS">Создать картинку LYRICS</button>
            <button class="group-button" @click="createPictureChords" title="Создать картинку CHORDS">Создать картинку CHORDS</button>
            <button class="group-button" @click="createPictureTabs" title="Создать картинку TABS">Создать картинку TABS</button>
            <button class="group-button" @click="createDescriptionFileKaraoke" title="Создать текст KARAOKE">Создать текст KARAOKE</button>
            <button class="group-button" @click="createDescriptionFileLyrics" title="Создать текст LYRICS">Создать текст LYRICS</button>
            <button class="group-button" @click="createDescriptionFileChords" title="Создать текст CHORDS">Создать текст CHORDS</button>
            <button class="group-button" @click="createDescriptionFileTabs" title="Создать текст TABS">Создать текст TABS</button>
            <button class="group-button" @click="playKaraoke" title="PLAY KARAOKE" :style="{ backgroundColor: song.processColorMeltKaraoke }">PLAY KARAOKE</button>
            <button class="group-button" @click="playLyrics" title="PLAY LYRICS" :style="{ backgroundColor: song.processColorMeltLyrics }">PLAY LYRICS</button>
            <button class="group-button" @click="playChords" title="PLAY CHORDS" :style="{ backgroundColor: song.processColorMeltChords }">PLAY CHORDS</button>
            <button class="group-button" @click="playTabs" title="PLAY TABS" :style="{ backgroundColor: song.processColorMeltMelody }">PLAY TABS</button>
            <div class="navigation-buttons">
              <button class="group-button-left-right" @click="goToLeftSong" title="⬅">⬅</button>
              <div class="navigation-buttons-column">
                <button class="group-button-up-down" @click="goToPreviousSong" title="⬆">⬆</button>
                <button class="group-button-up-down" @click="goToNextSong" title="⬇">⬇</button>
              </div>
              <button class="group-button-left-right" @click="goToRightSong" title="➡">➡</button>
            </div>
            <div class="group-button">
              <b-form-rating
                  id="rate-inline-form"
                  inline
                  v-model="song.rate"
              ></b-form-rating>
            </div>
          </div>
        </div>
        <!-- Третий столбец тела -->
        <div class="column-3">
          <div class="formatted-text-area" v-if="textFormatted">
            <div class="formatted-text" v-html="textFormatted"></div>
          </div>
        </div>
        <div class="column-4">
          <div class="formatted-notes-area" v-if="notesFormatted">
            <div class="formatted-notes" v-html="notesFormatted"></div>
          </div>
        </div>
        <div class="column-5">
          <div class="formatted-chords-area" v-if="chordsFormatted">
            <div class="formatted-chords" v-html="chordsFormatted"></div>
          </div>
        </div>
      </div>
      <!-- Подвал -->
      <div class="footer">
        <button class="btn-round-save-double" @click="save" :disabled="notChanged()" title="Сохранить"><img alt="saveSong" class="icon-save-double" src="../../../assets/svg/icon_save.svg"></button>
        <button class="btn-round-double" @click="showSubsEdit" title="Редактировать субтитры"><img alt="edit subs" class="icon-edit-double" src="../../../assets/svg/icon_edit.svg"></button>
        <button class="btn-round-double" @click="createKaraoke" title="Создать караоке"><img alt="create karaoke" class="icon-40" src="../../../assets/svg/icon_song.svg"></button>
        <button class="btn-round-double" @click="createDemucs2" title="Создать DEMUCS2"><img alt="create demucs2" class="icon-40" src="../../../assets/svg/icon_demucs2.svg"></button>
        <button class="btn-round-double" @click="createDemucs5" title="Создать DEMUCS5"><img alt="create demucs5" class="icon-40" src="../../../assets/svg/icon_demucs5.svg"></button>
        <button class="btn-round-double" @click="createMP3Karaoke" title="Создать MP3 Karaoke"><img alt="create mp3 karaoke" class="icon-40" src="../../../assets/svg/icon_mp3karaoke.svg"></button>
        <button class="btn-round-double" @click="createMP3Lyrics" title="Создать MP3 Lyrics"><img alt="create mp3 lyrics" class="icon-40" src="../../../assets/svg/icon_mp3lyrics.svg"></button>
        <button class="btn-round-double" @click="createSymlinks" title="Создать SYMLINKS"><img alt="create symlink" class="icon-40" src="../../../assets/svg/icon_symlink.svg"></button>
        <button class="btn-round-double" @click="createSheetsage" title="Создать SHEETSAGE"><img alt="create sheetsage" class="icon-40" src="../../../assets/svg/icon_chords.svg"></button>
        <button class="btn-round-double" @click="setDateTimeAuthor" title="установить дату/время публикации для песен автора, начиная с текущей"><img alt="calendar" class="icon-40" src="../../../assets/svg/icon_calendar_later.svg"></button>
        <button class="btn-round-double" @click="deleteSong" title="Удалить песню"><img alt="delete" class="icon-40" src="../../../assets/svg/icon_delete.svg"></button>
      </div>
    </div>
    <div v-else>
      Не выбрана песня
    </div>
  </div>
</template>

<script>
import SubsEdit from './SubsEdit.vue'
import CustomConfirm from "../../Common/CustomConfirm.vue";
// import { ToastPlugin } from 'bootstrap-vue'

export default {
  name: "SongEdit",
  components: {
    CustomConfirm,
    SubsEdit
  },
  data () {
    return {
      isSubsEditVisible: false,
      isCustomConfirmVisible: false,
      voices: [],
      customConfirmParams: undefined,
      imageAuthorBase64: '',
      imageAlbumBase64: '',
      textFormatted: '',
      notesFormatted: '',
      chordsFormatted: '',
      autoSave: true,
      autoSaveDelayMs: 1000,
      saveTimer: undefined
    };
  },
  async mounted() {
    this.$store.dispatch('getAuthorPictureBase64Promise').then(image => this.imageAuthorBase64 = image);
    this.$store.dispatch('getAlbumPictureBase64Promise').then(image => this.imageAlbumBase64 = image);
    this.$store.dispatch('getTextFormattedPromise').then(textFormatted => this.textFormatted = textFormatted);
    this.$store.dispatch('getNotesFormattedPromise').then(notesFormatted => this.notesFormatted = notesFormatted);
    this.$store.dispatch('getChordsFormattedPromise').then(chordsFormatted => this.chordsFormatted = chordsFormatted);
    this.autoSave = await this.propAutoSave();
    this.autoSaveDelayMs = Number(await this.propAutoSaveDelayMs());
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
        fontFamily: 'Avenir, Helvetica, Arial, sans-serif'
        // backgroundColor: 'lightyellow'
      }
    },
    song() { return this.$store.getters.getCurrentSong },
    previousSongId() { return this.$store.getters.getPreviousSongId },
    nextSongId() { return this.$store.getters.getNextSongId },
    leftSongId() { return this.$store.getters.getLeftSongId },
    rightSongId() { return this.$store.getters.getRightSongId },
    snapshot() { return this.$store.getters.getSnapshotSong },
    diff() { return this.$store.getters.getSongDiff },

    mainLink() { return this.prefixMainLink + this.song.id; },
    linkBoosty() { return this.prefixLinkBoosty + this.song.idBoosty; },
    linkSponsrEdit() { return this.prefixLinkSponsrEdit + this.song.idSponsr; },
    linkSponsrPlay() { return this.prefixLinkSponsrPlay + this.song.idSponsr; },
    linkBoostyFiles() { return this.prefixLinkBoosty + this.song.idBoostyFiles; },
    linkBoostyNew() { return this.prefixLinkBoostyNew },
    linkSponsrNew() { return this.prefixLinkSponsrNew },
    linkVkGroup() { return this.prefixLinkVkGroup + this.song.idVk; },
    linkDzenKaraokePlay() { return this.prefixLinkDzenPlay + this.song.idDzenKaraoke; },
    linkDzenKaraokeEdit() { return this.prefixLinkDzenEdit + this.song.idDzenKaraoke; },
    linkDzenLyricsPlay() { return this.prefixLinkDzenPlay + this.song.idDzenLyrics; },
    linkDzenLyricsEdit() { return this.prefixLinkDzenEdit + this.song.idDzenLyrics; },
    linkDzenChordsPlay() { return this.prefixLinkDzenPlay + this.song.idDzenChords; },
    linkDzenChordsEdit() { return this.prefixLinkDzenEdit + this.song.idDzenChords; },
    linkDzenTabsPlay() { return this.prefixLinkDzenPlay + this.song.idDzenMelody; },
    linkDzenTabsEdit() { return this.prefixLinkDzenEdit + this.song.idDzenMelody; },
    linkVkKaraoke() { return this.prefixLinkVk + this.song.idVkKaraoke; },
    linkVkLyrics() { return this.prefixLinkVk + this.song.idVkLyrics; },
    linkVkChords() { return this.prefixLinkVk + this.song.idVkChords; },
    linkVkTabs() { return this.prefixLinkVk + this.song.idVkMelody; },
    linkTelegramKaraoke() { return this.prefixLinkTelegram + this.song.idTelegramKaraoke; },
    linkTelegramLyrics() { return this.prefixLinkTelegram + this.song.idTelegramLyrics; },
    linkTelegramChords() { return this.prefixLinkTelegram + this.song.idTelegramChords; },
    linkTelegramTabs() { return this.prefixLinkTelegram + this.song.idTelegramMelody; },
    linkPlKaraokePlay() { return this.prefixLinkPlPlay + this.song.idPlKaraoke; },
    linkPlKaraokeEdit() { return this.prefixLinkPlEdit + this.song.idPlKaraoke + this.suffixLinkPlEdit; },
    linkPlLyricsPlay() { return this.prefixLinkPlPlay + this.song.idPlLyrics; },
    linkPlLyricsEdit() { return this.prefixLinkPlEdit + this.song.idPlLyrics + this.suffixLinkPlEdit; },
    linkPlChordsPlay() { return this.prefixLinkPlPlay + this.song.idPlChords; },
    linkPlChordsEdit() { return this.prefixLinkPlEdit + this.song.idPlChords + this.suffixLinkPlEdit; },
    linkPlTabsPlay() { return this.prefixLinkPlPlay + this.song.idPlMelody; },
    linkPlTabsEdit() { return this.prefixLinkPlEdit + this.song.idPlMelody + this.suffixLinkPlEdit; },

    prefixMainLink: () => { return 'https://sm-karaoke.ru/song?id='; },
    prefixLinkBoosty: () => { return 'https://boosty.to/svoemesto/posts/'; },
    prefixLinkSponsrEdit: () => { return 'https://sponsr.ru/smkaraoke/manage/post/'; },
    prefixLinkSponsrPlay: () => { return 'https://sponsr.ru/smkaraoke/'; },
    prefixLinkBoostyNew: () => { return 'https://boosty.to/svoemesto/new-post'; },
    prefixLinkSponsrNew: () => { return 'https://sponsr.ru/smkaraoke/manage/post/new/'; },
    prefixLinkVkGroup: () => { return 'https://vk.com/wall-'; },
    prefixLinkDzenPlay: () => { return 'https://dzen.ru/video/watch/'; },
    prefixLinkDzenEdit: () => { return 'https://dzen.ru/profile/editor/svoemesto/publications?videoEditorPublicationId='; },
    prefixLinkVk: () => { return 'https://vkvideo.ru/video'; },
    prefixLinkVk2: () => { return 'https://vk.com/video'; },
    prefixLinkTelegram: () => { return 'https://t.me/svoemestokaraoke/'; },

    prefixLinkPlPlay: () => { return 'https://plvideo.ru/watch?v='; },
    prefixLinkPlEdit: () => { return 'https://studio.plvideo.ru/channel/bbj0HWC8H7ii/video/'; },
    suffixLinkPlEdit: () => { return '/edit'; },

  },
  watch: {
    diff: {
      async handler () {
        console.log('watch diff propAutoSave', this.autoSave);
        if (this.diff.length !== 0 && this.autoSave) {
          console.log('watch diff propAutoSaveDelayMs', this.autoSaveDelayMs);
          clearTimeout(this.saveTimer);
          this.saveTimer = setTimeout(this.save, this.autoSaveDelayMs);
        }
      }
    },
    song: {
      handler () {
        this.$store.dispatch('getAuthorPictureBase64Promise').then(image => this.imageAuthorBase64 = image);
        this.$store.dispatch('getAlbumPictureBase64Promise').then(image => this.imageAlbumBase64 = image);
        this.$store.dispatch('getTextFormattedPromise').then(textFormatted => this.textFormatted = textFormatted);
        this.$store.dispatch('getNotesFormattedPromise').then(notesFormatted => this.notesFormatted = notesFormatted);
        this.$store.dispatch('getChordsFormattedPromise').then(chordsFormatted => this.chordsFormatted = chordsFormatted);
      }
    },
    'song.idBoosty.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idBoosty;
          if (value && value.startsWith(this.prefixLinkBoosty)) {
            const newVal = value.replace(this.prefixLinkBoosty, '').replace('?share=post_link', '');
            this.$store.dispatch('setCurrentSongField', {name: 'idBoosty', value: newVal})
          }
          if (value !== '' && this.song.versionBoosty === 0) {
              this.$store.dispatch('setCurrentSongField', {name: 'versionBoosty', value: this.song.resultVersion});
          } else if (value === '' && this.song.versionBoosty !== 0){
              this.$store.dispatch('setCurrentSongField', {name: 'versionBoosty', value: 0});
          }
        }
      }
    },
    'song.idSponsr.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idSponsr;
          if (value && value.startsWith(this.prefixLinkSponsrPlay)) {
            const newValue = value.replace(this.prefixLinkSponsrPlay, '').split('/')[0];
            this.$store.dispatch('setCurrentSongField', {name: 'idSponsr', value: newValue})
          }
          if (value !== '' && this.song.versionSponsr === 0) {
            this.$store.dispatch('setCurrentSongField', {name: 'versionSponsr', value: this.song.resultVersion});
          } else if (value === '' && this.song.versionSponsr !== 0){
            this.$store.dispatch('setCurrentSongField', {name: 'versionSponsr', value: 0});
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
          if (value !== '' && this.song.versionBoostyFiles === 0) {
            this.$store.dispatch('setCurrentSongField', {name: 'versionBoostyFiles', value: this.song.resultVersion});
          } else if (value === '' && this.song.versionBoostyFiles !== 0){
            this.$store.dispatch('setCurrentSongField', {name: 'versionBoostyFiles', value: 0});
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
    'song.idDzenKaraoke.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idDzenKaraoke;
          if (value && value.startsWith(this.prefixLinkDzenPlay)) {
            const newValue = value.replace(this.prefixLinkDzenPlay, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idDzenKaraoke', value: newValue})
          }
          if (value !== '' && this.song.versionDzenKaraoke === 0) {
            this.$store.dispatch('setCurrentSongField', {name: 'versionDzenKaraoke', value: this.song.resultVersion});
          } else if (value === '' && this.song.versionDzenKaraoke !== 0){
            this.$store.dispatch('setCurrentSongField', {name: 'versionDzenKaraoke', value: 0});
          }
        }
      }
    },
    'song.idDzenLyrics.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idDzenLyrics;
          if (value && value.startsWith(this.prefixLinkDzenPlay)) {
            const newValue = value.replace(this.prefixLinkDzenPlay, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idDzenLyrics', value: newValue})
          }
          if (value !== '' && this.song.versionDzenLyrics === 0) {
            this.$store.dispatch('setCurrentSongField', {name: 'versionDzenLyrics', value: this.song.resultVersion});
          } else if (value === '' && this.song.versionDzenLyrics !== 0){
            this.$store.dispatch('setCurrentSongField', {name: 'versionDzenLyrics', value: 0});
          }
        }
      }
    },
    'song.idDzenChords.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idDzenChords;
          if (value && value.startsWith(this.prefixLinkDzenPlay)) {
            const newValue = value.replace(this.prefixLinkDzenPlay, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idDzenChords', value: newValue})
          }
          if (value !== '' && this.song.versionDzenChords === 0) {
            this.$store.dispatch('setCurrentSongField', {name: 'versionDzenChords', value: this.song.resultVersion});
          } else if (value === '' && this.song.versionDzenChords !== 0){
            this.$store.dispatch('setCurrentSongField', {name: 'versionDzenChords', value: 0});
          }
        }
      }
    },
    'song.idDzenMelody.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idDzenMelody;
          if (value && value.startsWith(this.prefixLinkDzenPlay)) {
            const newValue = value.replace(this.prefixLinkDzenPlay, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idDzenMelody', value: newValue})
          }
          if (value !== '' && this.song.versionDzenMelody === 0) {
            this.$store.dispatch('setCurrentSongField', {name: 'versionDzenMelody', value: this.song.resultVersion});
          } else if (value === '' && this.song.versionDzenMelody !== 0){
            this.$store.dispatch('setCurrentSongField', {name: 'versionDzenMelody', value: 0});
          }
        }
      }
    },
    'song.idPlKaraoke.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idPlKaraoke;
          if (value && value.startsWith(this.prefixLinkPlPlay)) {
            const newValue = value.replace(this.prefixLinkPlPlay, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idPlKaraoke', value: newValue})
          }
          if (value !== '' && this.song.versionPlKaraoke === 0) {
            this.$store.dispatch('setCurrentSongField', {name: 'versionPlKaraoke', value: this.song.resultVersion});
          } else if (value === '' && this.song.versionPlKaraoke !== 0){
            this.$store.dispatch('setCurrentSongField', {name: 'versionPlKaraoke', value: 0});
          }
        }
      }
    },
    'song.idPlLyrics.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idPlLyrics;
          if (value && value.startsWith(this.prefixLinkPlPlay)) {
            const newValue = value.replace(this.prefixLinkPlPlay, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idPlLyrics', value: newValue})
          }
          if (value !== '' && this.song.versionPlLyrics === 0) {
            this.$store.dispatch('setCurrentSongField', {name: 'versionPlLyrics', value: this.song.resultVersion});
          } else if (value === '' && this.song.versionPlLyrics !== 0){
            this.$store.dispatch('setCurrentSongField', {name: 'versionPlLyrics', value: 0});
          }
        }
      }
    },
    'song.idPlChords.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idPlChords;
          if (value && value.startsWith(this.prefixLinkPlPlay)) {
            const newValue = value.replace(this.prefixLinkPlPlay, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idPlChords', value: newValue})
          }
          if (value !== '' && this.song.versionPlChords === 0) {
            this.$store.dispatch('setCurrentSongField', {name: 'versionPlChords', value: this.song.resultVersion});
          } else if (value === '' && this.song.versionPlChords !== 0){
            this.$store.dispatch('setCurrentSongField', {name: 'versionPlChords', value: 0});
          }
        }
      }
    },
    'song.idPlMelody.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idPlMelody;
          if (value && value.startsWith(this.prefixLinkPlPlay)) {
            const newValue = value.replace(this.prefixLinkPlPlay, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idPlMelody', value: newValue})
          }
          if (value !== '' && this.song.versionPlMelody === 0) {
            this.$store.dispatch('setCurrentSongField', {name: 'versionPlMelody', value: this.song.resultVersion});
          } else if (value === '' && this.song.versionPlMelody !== 0){
            this.$store.dispatch('setCurrentSongField', {name: 'versionPlMelody', value: 0});
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
            this.$store.dispatch('setCurrentSongField', {name: 'idVkKaraoke', value: newValue});
          }
          if (value && value.startsWith(this.prefixLinkVk2)) {
            const newValue = value.replace(this.prefixLinkVk2, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idVkKaraoke', value: newValue});
          }
          if (value !== '' && this.song.versionVkKaraoke === 0) {
            this.$store.dispatch('setCurrentSongField', {name: 'versionVkKaraoke', value: this.song.resultVersion});
          } else if (value === '' && this.song.versionVkKaraoke !== 0){
            this.$store.dispatch('setCurrentSongField', {name: 'versionVkKaraoke', value: 0});
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
            this.$store.dispatch('setCurrentSongField', {name: 'idVkLyrics', value: newValue});
          }
          if (value && value.startsWith(this.prefixLinkVk2)) {
            const newValue = value.replace(this.prefixLinkVk2, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idVkLyrics', value: newValue});
          }
          if (value !== '' && this.song.versionVkLyrics === 0) {
            this.$store.dispatch('setCurrentSongField', {name: 'versionVkLyrics', value: this.song.resultVersion});
          } else if (value === '' && this.song.versionVkLyrics !== 0){
            this.$store.dispatch('setCurrentSongField', {name: 'versionVkLyrics', value: 0});
          }
        }
      }
    },
    'song.idVkChords.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idVkChords;
          if (value && value.startsWith(this.prefixLinkVk)) {
            const newValue = value.replace(this.prefixLinkVk, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idVkChords', value: newValue});
          }
          if (value && value.startsWith(this.prefixLinkVk2)) {
            const newValue = value.replace(this.prefixLinkVk2, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idVkChords', value: newValue});
          }
          if (value !== '' && this.song.versionVkChords === 0) {
            this.$store.dispatch('setCurrentSongField', {name: 'versionVkChords', value: this.song.resultVersion});
          } else if (value === '' && this.song.versionVkChords !== 0){
            this.$store.dispatch('setCurrentSongField', {name: 'versionVkChords', value: 0});
          }
        }
      }
    },
    'song.idVkMelody.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idVkMelody;
          if (value && value.startsWith(this.prefixLinkVk)) {
            const newValue = value.replace(this.prefixLinkVk, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idVkMelody', value: newValue});
          }
          if (value && value.startsWith(this.prefixLinkVk2)) {
            const newValue = value.replace(this.prefixLinkVk2, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idVkMelody', value: newValue});
          }
          if (value !== '' && this.song.versionVkMelody === 0) {
            this.$store.dispatch('setCurrentSongField', {name: 'versionVkMelody', value: this.song.resultVersion});
          } else if (value === '' && this.song.versionVkMelody !== 0){
            this.$store.dispatch('setCurrentSongField', {name: 'versionVkMelody', value: 0});
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
          if (value !== '' && value !== '-' && this.song.versionTelegramKaraoke === 0) {
            this.$store.dispatch('setCurrentSongField', {name: 'versionTelegramKaraoke', value: this.song.resultVersion});
          } else if ((value === '' || value === '-') && this.song.versionTelegramKaraoke !== 0){
            this.$store.dispatch('setCurrentSongField', {name: 'versionTelegramKaraoke', value: 0});
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
          if (value !== '' && this.song.versionTelegramLyrics === 0) {
            this.$store.dispatch('setCurrentSongField', {name: 'versionTelegramLyrics', value: this.song.resultVersion});
          } else if (value === '' && this.song.versionTelegramLyrics !== 0){
            this.$store.dispatch('setCurrentSongField', {name: 'versionTelegramLyrics', value: 0});
          }
        }
      }
    },
    'song.idTelegramChords.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idTelegramChords;
          if (value && value.startsWith(this.prefixLinkTelegram)) {
            const newValue = value.replace(this.prefixLinkTelegram, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idTelegramChords', value: newValue})
          }
          if (value !== '' && this.song.versionTelegramChords === 0) {
            this.$store.dispatch('setCurrentSongField', {name: 'versionTelegramChords', value: this.song.resultVersion});
          } else if (value === '' && this.song.versionTelegramChords !== 0){
            this.$store.dispatch('setCurrentSongField', {name: 'versionTelegramChords', value: 0});
          }
        }
      }
    },
    'song.idTelegramMelody.value': {
      deep: true,
      handler () {
        if (this.song) {
          const value = this.song.idTelegramMelody;
          if (value && value.startsWith(this.prefixLinkTelegram)) {
            const newValue = value.replace(this.prefixLinkTelegram, '');
            this.$store.dispatch('setCurrentSongField', {name: 'idTelegramMelody', value: newValue})
          }
          if (value !== '' && this.song.versionTelegramMelody === 0) {
            this.$store.dispatch('setCurrentSongField', {name: 'versionTelegramMelody', value: this.song.resultVersion});
          } else if (value === '' && this.song.versionTelegramMelody !== 0){
            this.$store.dispatch('setCurrentSongField', {name: 'versionTelegramMelody', value: 0});
          }
        }
      }
    }
  },

  methods: {
    async propAutoSave() {
      const propValue = await this.$store.getters.getPropValue('autoSave');
      return propValue === 'true'
    },
    async propAutoSaveDelayMs() { return await this.$store.getters.getPropValue('autoSaveDelayMs') },
    setStatus(idStatus) {
      this.song.idStatus = idStatus;
      let status = "N/A";
      switch (idStatus) {
        case 0: {status = 'NONE'; break;}
        case 1: {status = 'TEXT_CREATE'; break;}
        case 2: {status = 'TEXT_CHECK'; break;}
        case 3: {status = 'PROJECT_CREATE'; break;}
        case 4: {status = 'PROJECT_CHECK'; break;}
        case 5: {status = 'RENDERING'; break;}
        case 6: {status = 'DONE'; break;}
      }
      this.song.status = status;
    },
    statusButtonClass(status) {
      return status === this.song.idStatus ? 'group-button-active' : ''
    },
    copyFieldsFromAnother() {
      this.customConfirmParams = {
        header: 'Подтвердите создание караоке',
        body: `Скопировать поля из другой песни для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doСopyFieldsFromAnother,
        fields: [
          {
            fldName: 'idAnother',
            fldLabel: 'ID песни:',
            fldValue: '',
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '100px', textAlign: 'center', borderRadius: '10px'}
          },
          {
            fldName: 'fields',
            fldLabel: 'Поля:',
            fldValue: 'SOURCE_TEXT;RESULT_TEXT;SOURCE_MARKERS',
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '400px', textAlign: 'right', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doСopyFieldsFromAnother(result) {
      this.$store.dispatch('copyFieldsFromAnotherPromise', {idAnother: result.idAnother, fields: result.fields});
    },
    createPictureBoostyTeaser() {
      this.customConfirmParams = {
        header: 'Подтвердите создание картинки Boosty Teaser',
        body: `Создать картинку Boosty Teaser для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreatePictureBoostyTeaser
      }
      this.isCustomConfirmVisible = true;
    },
    doCreatePictureBoostyTeaser() {
      this.$store.dispatch('createPictureBoostyTeaserPromise')
    },
    createPictureSponsrTeaser() {
      this.customConfirmParams = {
        header: 'Подтвердите создание картинки Sponsr Teaser',
        body: `Создать картинку Sponsr Teaser для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreatePictureSponsrTeaser
      }
      this.isCustomConfirmVisible = true;
    },
    doCreatePictureSponsrTeaser() {
      this.$store.dispatch('createPictureSponsrTeaserPromise')
    },
    createPictureBoostyFiles() {
      this.customConfirmParams = {
        header: 'Подтвердите создание картинки Boosty Files',
        body: `Создать картинку Boosty Files для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreatePictureBoostyFiles
      }
      this.isCustomConfirmVisible = true;
    },
    doCreatePictureBoostyFiles() {
      this.$store.dispatch('createPictureBoostyFilesPromise')
    },
    createPictureVK() {
      this.customConfirmParams = {
        header: 'Подтвердите создание картинки VK',
        body: `Создать картинку VK для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreatePictureVK
      }
      this.isCustomConfirmVisible = true;
    },
    doCreatePictureVK() {
      this.$store.dispatch('createPictureVKPromise')
    },
    createPictureVKlink() {
      this.customConfirmParams = {
        header: 'Подтвердите создание картинки VKlink',
        body: `Создать картинку VKlink для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreatePictureVKlink
      }
      this.isCustomConfirmVisible = true;
    },
    doCreatePictureVKlink() {
      this.$store.dispatch('createPictureVKlinkPromise')
    },
    createPictureKaraoke() {
      this.customConfirmParams = {
        header: 'Подтвердите создание картинки KARAOKE',
        body: `Создать картинку KARAOKE для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreatePictureKaraoke
      }
      this.isCustomConfirmVisible = true;
    },
    doCreatePictureKaraoke() {
      this.$store.dispatch('createPictureKaraokePromise')
    },
    createPictureLyrics() {
      this.customConfirmParams = {
        header: 'Подтвердите создание картинки LYRICS',
        body: `Создать картинку LYRICS для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreatePictureLyrics
      }
      this.isCustomConfirmVisible = true;
    },
    doCreatePictureLyrics() {
      this.$store.dispatch('createPictureLyricsPromise')
    },
    createPictureChords() {
      this.customConfirmParams = {
        header: 'Подтвердите создание картинки CHORDS',
        body: `Создать картинку CHORDS для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreatePictureChords
      }
      this.isCustomConfirmVisible = true;
    },
    doCreatePictureChords() {
      this.$store.dispatch('createPictureChordsPromise')
    },

    createPictureTabs() {
      this.customConfirmParams = {
        header: 'Подтвердите создание картинки MELODY',
        body: `Создать картинку MELODY для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreatePictureMelody
      }
      this.isCustomConfirmVisible = true;
    },
    doCreatePictureMelody() {
      this.$store.dispatch('createPictureTabsPromise')
    },

    createDescriptionFileKaraoke() {
      this.customConfirmParams = {
        header: 'Подтвердите создание текста KARAOKE',
        body: `Создать текст KARAOKE для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreateDescriptionFileKaraoke
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateDescriptionFileKaraoke() {
      this.$store.dispatch('createDescriptionFileKaraokePromise')
    },
    createDescriptionFileLyrics() {
      this.customConfirmParams = {
        header: 'Подтвердите создание текста LYRICS',
        body: `Создать текст LYRICS для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreateDescriptionFileLyrics
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateDescriptionFileLyrics() {
      this.$store.dispatch('createDescriptionFileLyricsPromise')
    },
    createDescriptionFileChords() {
      this.customConfirmParams = {
        header: 'Подтвердите создание текста CHORDS',
        body: `Создать текст CHORDS для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreateDescriptionFileChords
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateDescriptionFileChords() {
      this.$store.dispatch('createDescriptionFileChordsPromise')
    },

    createDescriptionFileTabs() {
      this.customConfirmParams = {
        header: 'Подтвердите создание текста TABS',
        body: `Создать текст TABS для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreateDescriptionFileTabs
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateDescriptionFileTabs() {
      this.$store.dispatch('createDescriptionFileTabsPromise')
    },

    playLyrics() {
      this.$store.getters.playLyrics();
    },
    playKaraoke() {
      this.$store.getters.playKaraoke();
    },
    playChords() {
      this.$store.getters.playChords();
    },
    playTabs() {
      this.$store.getters.playTabs();
    },

    goToPreviousSong() {
      const id = this.previousSongId;
      this.$store.commit('setCurrentSongId', id);
    },
    goToNextSong() {
      const id = this.nextSongId;
      this.$store.commit('setCurrentSongId', id);
    },
    goToLeftSong() {
      const id = this.leftSongId;
      this.$store.commit('setCurrentSongId', id);
    },
    goToRightSong() {
      const id = this.rightSongId;
      this.$store.commit('setCurrentSongId', id);
    },
    createKaraoke() {
      this.customConfirmParams = {
        header: 'Подтвердите создание караоке',
        body: `Создать караоке для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreateKaraoke,
        fields: [
          {
            fldName: 'priorLyrics',
            fldLabel: 'Приоритет Lyrics:',
            fldValue: this.$store.getters.getLastPriorLyrics,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px'}
          },
          {
            fldName: 'priorKaraoke',
            fldLabel: 'Приоритет Karaoke:',
            fldValue: this.$store.getters.getLastPriorKaraoke,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px'}
          },
          {
            fldName: 'priorChords',
            fldLabel: 'Приоритет Chords:',
            fldValue: this.$store.getters.getLastPriorChords,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px'}
          },
          {
            fldName: 'priorMelody',
            fldLabel: 'Приоритет Tabs:',
            fldValue: this.$store.getters.getLastPriorMelody,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateKaraoke(result) {
      this.$store.dispatch('setLastPriorLyrics', {value: result.priorLyrics});
      this.$store.dispatch('setLastPriorKaraoke', {value: result.priorKaraoke});
      this.$store.dispatch('setLastPriorChords', {value: result.priorChords});
      this.$store.dispatch('setLastPriorMelody', {value: result.priorMelody});
      this.$store.dispatch('createKaraokePromise', {priorLyrics: result.priorLyrics, priorKaraoke: result.priorKaraoke, priorChords: result.priorChords, priorMelody: result.priorMelody});
    },
    createDemucs2() {
      this.customConfirmParams = {
        header: 'Подтвердите создание DEMUCS2',
        body: `Создать DEMUCS2 для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreateDemucs2,
        fields: [
          {
            fldName: 'prior',
            fldLabel: 'Приоритет:',
            fldValue: this.$store.getters.getLastPriorDemucs,
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    createDemucs5() {
      this.customConfirmParams = {
        header: 'Подтвердите создание DEMUCS5',
        body: `Создать DEMUCS5 для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreateDemucs5,
        fields: [
          {
            fldName: 'prior',
            fldLabel: 'Приоритет:',
            fldValue: this.$store.getters.getLastPriorDemucs,
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    createSheetsage() {
      this.customConfirmParams = {
        header: 'Подтвердите создание SHEETSAGE',
        body: `Создать SHEETSAGE для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreateSheetsage,
        fields: [
          {
            fldName: 'prior',
            fldLabel: 'Приоритет:',
            fldValue: this.$store.getters.getLastPriorDemucs,
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    createMP3Karaoke() {
      this.customConfirmParams = {
        header: 'Подтвердите создание MP3 KARAOKE',
        body: `Создать MP3 KARAOKE для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreateMP3Karaoke,
        fields: [
          {
            fldName: 'prior',
            fldLabel: 'Приоритет:',
            fldValue: this.$store.getters.getLastPriorDemucs,
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    createMP3Lyrics() {
      this.customConfirmParams = {
        header: 'Подтвердите создание MP3 LYRICS',
        body: `Создать MP3 LYRICS для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreateMP3Lyrics,
        fields: [
          {
            fldName: 'prior',
            fldLabel: 'Приоритет:',
            fldValue: this.$store.getters.getLastPriorDemucs,
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateDemucs2(result) {
      this.$store.dispatch('setLastPriorDemucs', {value: result.prior});
      this.$store.dispatch('createDemucs2Promise', { prior: result.prior })
    },
    doCreateDemucs5(result) {
      this.$store.dispatch('setLastPriorDemucs', {value: result.prior});
      this.$store.dispatch('createDemucs5Promise', { prior: result.prior })
    },
    doCreateSheetsage(result) {
      this.$store.dispatch('setLastPriorDemucs', {value: result.prior});
      this.$store.dispatch('createSheetsagePromise', { prior: result.prior })
    },
    doCreateMP3Karaoke(result) {
      this.$store.dispatch('setLastPriorDemucs', {value: result.prior});
      this.$store.dispatch('createMP3KaraokePromise', { prior: result.prior })
    },
    doCreateMP3Lyrics(result) {
      this.$store.dispatch('setLastPriorDemucs', {value: result.prior});
      this.$store.dispatch('createMP3LyricsPromise', { prior: result.prior })
    },
    createSymlinks() {
      this.customConfirmParams = {
        header: 'Подтвердите создание SYMLINKs',
        body: `Создать SYMLINKs для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreateSymlinks,
        fields: [
            {
              fldName: 'prior',
              fldLabel: 'Приоритет:',
              fldValue: this.$store.getters.getLastPriorSymlinks,
              fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px'},
              fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px'}
            }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doCreateSymlinks(result) {
      this.$store.dispatch('setLastPriorSymlinks', {value: result.prior});
      this.$store.dispatch('createSymlinksPromise', { prior: result.prior } )
    },
    setDateTimeAuthor() {
      this.customConfirmParams = {
        header: 'Подтвердите изменение дат',
        body: `Вы действительно хотите установить дату/время публикации для песен автора, начиная с текущей?`,
        timeout: 10,
        callback: this.doSetDateTimeAuthor
      }
      this.isCustomConfirmVisible = true;
    },
    doSetDateTimeAuthor() {
      this.$store.dispatch('setDateTimeAuthorPromise')
    },
    deleteSong() {
      this.customConfirmParams = {
        header: 'Подтвердите удаление песни',
        body: `Удалить песню <strong>«${this.song.songName}»</strong>?`,
        timeout: 10,
        callback: this.doDeleteSong
      }
      this.isCustomConfirmVisible = true;
    },
    doDeleteSong() {
      this.$store.commit('deleteCurrentSong');
      this.$emit('close');
    },
    async showSubsEdit() {
      this.voices = JSON.parse(await this.$store.getters.getVoices).voices;
      this.isSubsEditVisible = true;
    },
    closeSubsEdit() {
      this.$store.dispatch('getTextFormattedPromise').then(textFormatted => this.textFormatted = textFormatted);
      this.$store.dispatch('getNotesFormattedPromise').then(notesFormatted => this.notesFormatted = notesFormatted);
      this.$store.dispatch('getChordsFormattedPromise').then(chordsFormatted => this.chordsFormatted = chordsFormatted);
      this.isSubsEditVisible = false;
    },
    closeCustomConfirm() {
      this.isCustomConfirmVisible = false;
    },

    async getLinkBoosty() {
      let value = this.linkBoosty;
      await navigator.clipboard.writeText(value);
      this.showCopyToClipboardToast('linkBoosty', value);
    },
    async getLinkSponsrPlay() {
      let value = this.linkSponsrPlay;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkSponsrPlay', value);
    },
    async openLinkSponsrNew() {
      let value = await this.$store.getters.getSponsrHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getSponsrHeader', value);
      window.open(this.linkSponsrNew, '_blank');
    },
    openLinkSponsr() {
      window.open(this.linkSponsrEdit, '_blank');
    },
    openLinkBoosty() {
      window.open(this.linkBoosty, '_blank');
    },
    openMainLink() {
      window.open(this.mainLink, '_blank');
    },
    updateRemote() {
      this.$store.dispatch('updateOneRemoteSettingsPromise', this.song.id);
    },
    toSyncRemote() {
      this.$store.dispatch('toSyncOneRemoteSettingsPromise', this.song.id);
    },
    async openLinkBoostyNew() {
      let value = await this.$store.getters.getBoostyHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getBoostyHeader', value);
      window.open(this.linkBoostyNew, '_blank');
    },
    async getBoostyHeader() {
      let value = await this.$store.getters.getBoostyHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getBoostyHeader', value);
    },
    async getBoostyBody() {
      let value = await this.$store.getters.getBoostyBody;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getBoostyBody', value);
    },
    async getSponsrHeader() {
      let value = await this.$store.getters.getSponsrHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getSponsrHeader', value);
    },
    async getSponsrBody() {
      let value = await this.$store.getters.getSponsrBody;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getSponsrBody', value);
    },
    async getLinkBoostyFiles() {
      let value = this.linkBoostyFiles;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkBoostyFiles', value);
    },
    openLinkBoostyFiles() {
      window.open(this.linkBoostyFiles, '_blank');
    },
    async openLinkBoostyFilesNew() {
      let value = await this.$store.getters.getBoostyFilesHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getBoostyFilesHeader', value);
      window.open(this.linkBoostyNew, '_blank');
    },
    async getBoostyFilesHeader() {
      let value = await this.$store.getters.getBoostyFilesHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getBoostyFilesHeader', value);
    },

    async getLinkVkGroup() {
      let value = this.linkVkGroup;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkVkGroup', value);
    },
    async getVkGroupBody() {
      let value = await this.$store.getters.getVkGroupBody;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkGroupBody', value);
    },
    openLinkVkGroup() {
      window.open(this.linkVkGroup, '_blank');
    },

    async getLinkDzenKaraokePlay() {
      let value = this.linkDzenKaraokePlay;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkDzenKaraokePlay', value);
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
      this.showCopyToClipboardToast('linkDzenLyricsPlay', value);
    },
    openLinkDzenLyricsPlay() {
      window.open(this.linkDzenLyricsPlay, '_blank');
    },
    openLinkDzenLyricsEdit() {
      window.open(this.linkDzenLyricsEdit, '_blank');
    },
    async getLinkDzenChordsPlay() {
      let value = this.linkDzenChordsPlay;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkDzenChordsPlay', value);
    },
    openLinkDzenChordsPlay() {
      window.open(this.linkDzenChordsPlay, '_blank');
    },
    openLinkDzenChordsEdit() {
      window.open(this.linkDzenChordsEdit, '_blank');
    },
    async getLinkDzenTabsPlay() {
      let value = this.linkDzenTabsPlay;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkDzenTabsPlay', value);
    },
    openLinkDzenTabsPlay() {
      window.open(this.linkDzenTabsPlay, '_blank');
    },
    openLinkDzenTabsEdit() {
      window.open(this.linkDzenTabsEdit, '_blank');
    },

    async getDzenKaraokeHeader() {
      let value = await this.$store.getters.getDzenKaraokeHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getDzenKaraokeHeader', value);
    },
    async getDzenKaraokeBody() {
      let value = await this.$store.getters.getDzenKaraokeBody;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getDzenKaraokeBody', value);
    },
    async getDzenLyricsHeader() {
      let value = await this.$store.getters.getDzenLyricsHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getDzenLyricsHeader', value);
    },
    async getDzenLyricsBody() {
      let value = await this.$store.getters.getDzenLyricsBody;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getDzenLyricsBody', value);
    },
    async getDzenChordsHeader() {
      let value = await this.$store.getters.getDzenChordsHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getDzenChordsHeader', value);
    },
    async getDzenChordsBody() {
      let value = await this.$store.getters.getDzenChordsBody;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getDzenChordsBody', value);
    },
    async getDzenTabsHeader() {
      let value = await this.$store.getters.getDzenTabsHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getDzenTabsHeader', value);
    },
    async getDzenTabsBody() {
      let value = await this.$store.getters.getDzenTabsBody;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getDzenTabsBody', value);
    },


    async getLinkPlKaraokePlay() {
      let value = this.linkPlKaraokePlay;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkPlKaraokePlay', value);
    },
    openLinkPlKaraokePlay() {
      window.open(this.linkPlKaraokePlay, '_blank');
    },
    openLinkPlKaraokeEdit() {
      window.open(this.linkPlKaraokeEdit, '_blank');
    },
    async getLinkPlLyricsPlay() {
      let value = this.linkPlLyricsPlay;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkPlLyricsPlay', value);
    },
    openLinkPlLyricsPlay() {
      window.open(this.linkPlLyricsPlay, '_blank');
    },
    openLinkPlLyricsEdit() {
      window.open(this.linkPlLyricsEdit, '_blank');
    },
    async getLinkPlChordsPlay() {
      let value = this.linkPlChordsPlay;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkPlChordsPlay', value);
    },
    openLinkPlChordsPlay() {
      window.open(this.linkPlChordsPlay, '_blank');
    },
    openLinkPlChordsEdit() {
      window.open(this.linkPlChordsEdit, '_blank');
    },
    async getLinkPlTabsPlay() {
      let value = this.linkPlTabsPlay;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkPlTabsPlay', value);
    },
    openLinkPlTabsPlay() {
      window.open(this.linkPlTabsPlay, '_blank');
    },
    openLinkPlTabsEdit() {
      window.open(this.linkPlTabsEdit, '_blank');
    },

    async getPlKaraokeHeader() {
      let value = await this.$store.getters.getPlKaraokeHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getPlKaraokeHeader', value);
    },
    async getPlKaraokeBody() {
      let value = await this.$store.getters.getPlKaraokeBody;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getPlKaraokeBody', value);
    },
    async getPlLyricsHeader() {
      let value = await this.$store.getters.getPlLyricsHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getPlLyricsHeader', value);
    },
    async getPlLyricsBody() {
      let value = await this.$store.getters.getPlLyricsBody;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getPlLyricsBody', value);
    },
    async getPlChordsHeader() {
      let value = await this.$store.getters.getPlChordsHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getPlChordsHeader', value);
    },
    async getPlChordsBody() {
      let value = await this.$store.getters.getPlChordsBody;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getPlChordsBody', value);
    },
    async getPlTabsHeader() {
      let value = await this.$store.getters.getPlTabsHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getPlTabsHeader', value);
    },
    async getPlTabsBody() {
      let value = await this.$store.getters.getPlTabsBody;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getPlTabsBody', value);
    },

    async getLinkVkKaraoke() {
      let value = this.linkVkKaraoke;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkVkKaraoke', value);
    },
    openLinkVkKaraoke() {
      window.open(this.linkVkKaraoke, '_blank');
    },
    async getLinkVkLyrics() {
      let value = this.linkVkLyrics;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkVkLyrics', value);
    },
    openLinkVkLyrics() {
      window.open(this.linkVkLyrics, '_blank');
    },
    async getLinkVkChords() {
      let value = this.linkVkChords;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkVkChords', value);
    },
    openLinkVkChords() {
      window.open(this.linkVkChords, '_blank');
    },
    async getLinkVkTabs() {
      let value = this.linkVkTabs;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkVkTabs', value);
    },
    openLinkVkTabs() {
      window.open(this.linkVkTabs, '_blank');
    },

    async getVkKaraokeHeader() {
      let value = await this.$store.getters.getVkKaraokeHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkKaraokeHeader', value);
    },

    async getVkKaraokeBody() {
      let value = await this.$store.getters.getVkKaraokeBody;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkKaraokeBody', value);
    },
    async getVkLyricsHeader() {
      let value = await this.$store.getters.getVkLyricsHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkLyricsHeader', value);
    },
    async getVkLyricsBody() {
      let value = await this.$store.getters.getVkLyricsBody;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkLyricsBody', value);
    },
    async getVkChordsHeader() {
      let value = await this.$store.getters.getVkChordsHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkChordsHeader', value);
    },
    async getVkChordsBody() {
      let value = await this.$store.getters.getVkChordsBody;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkChordsBody', value);
    },
    async getVkTabsHeader() {
      let value = await this.$store.getters.getVkTabsHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkTabsHeader', value);
    },
    async getVkTabsBody() {
      let value = await this.$store.getters.getVkTabsBody;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkTabsBody', value);
    },

    async getLinkTelegramKaraoke() {
      let value = this.linkTelegramKaraoke;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkTelegramKaraoke', value);
    },
    openLinkTelegramKaraoke() {
      window.open(this.linkTelegramKaraoke, '_blank');
    },
    async getLinkTelegramLyrics() {
      let value = this.linkTelegramLyrics;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkTelegramLyrics', value);
    },
    openLinkTelegramLyrics() {
      window.open(this.linkTelegramLyrics, '_blank');
    },
    async getLinkTelegramChords() {
      let value = this.linkTelegramChords;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkTelegramChords', value);
    },
    openLinkTelegramChords() {
      window.open(this.linkTelegramChords, '_blank');
    },
    async getLinkTelegramTabs() {
      let value = this.linkTelegramTabs;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkTelegramTabs', value);
    },
    openLinkTelegramTabs() {
      window.open(this.linkTelegramTabs, '_blank');
    },

    async getTelegramKaraokeHeader() {
      let value = await this.$store.getters.getTelegramKaraokeHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getTelegramKaraokeHeader', value);
    },

    async getTelegramLyricsHeader() {
      let value = await this.$store.getters.getTelegramLyricsHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getTelegramLyricsHeader', value);
    },
    async getTelegramChordsHeader() {
      let value = await this.$store.getters.getTelegramChordsHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getTelegramChordsHeader', value);
    },
    async getTelegramTabsHeader() {
      let value = await this.$store.getters.getTelegramTabsHeader;
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getTelegramTabsHeader', value);
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
      clearTimeout(this.saveTimer);
      let params = {};
      for (let diff of this.diff) {
        params[diff.name] = diff.new;
      }
      return this.$store.dispatch('saveSong', params)
    },
    undoField(name) {
      return this.$store.dispatch('setCurrentSongField', {name: name, value: this.snapshot[name]})
    },
    async copyToClipboard(value) {
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('', value);
    },
    async pasteFromClipboard(name) {
      await navigator.clipboard.readText().then(data => {
        return this.$store.dispatch('setCurrentSongField', {name: name, value: data})
      });
    },
    showCopyToClipboardToast(fieldName, fieldValue) {
      // Use a shorter name for this.$createElement
      const h = this.$createElement
      // Create the message
      const vNodesMsg = h(
          'div',
          [
            `Значение поля `,
            h('strong', fieldName),
            ` скопировано в буфер обмена:`,
            h('br'),
            h('div', { style: { fontFamily: 'monospace', fontSize: 'x-small' } }, fieldValue)
          ],

      )
      this.$bvToast.toast([vNodesMsg], {
        title: 'COPY',
        autoHideDelay: 3000,
        toaster: 'b-toaster-top-left',
        bodyClass: 'toast-body-info',
        headerClass: 'toast-header-info',
        appendToast: false,
        // noAutoHide: true
      })
    }
  }
}
</script>

<style scoped>

.header {
  border: thin dashed darkgray;
  border-radius: 10px;
  padding: 5px 0;
  background-color: transparent;
}

.body {
  margin: 0;
  display: flex;
  flex-direction: row;
  height: max-content;
  background-color: transparent;
  z-index: 100;
}

.footer {
  display: flex;
  flex-direction: row;
  justify-content: center;
  border: thin dashed darkgray;
  border-radius: 10px;
  padding: 5px 0;
  background-color: transparent;
}

.header-song-name {
  text-align: center;
  font-size: 24pt;
  font-weight: 400;
}

.header-song-author {
  text-align: center;
  font-size: 12pt;
  margin: 0 auto;
}

.header-song-album {
  text-align: center;
  font-size: 12pt;
  margin: 0 auto;
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

.group-button {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: auto;
}
.group-button-left-right {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: 75px;
  font-size: xx-large;
}
.group-button-up-down {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
  width: 100px;
  font-size: xx-large;
}
.group-button-active {
  background-color: dodgerblue;
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

.input-field-version {
  display: block;
  padding-bottom: 3px;
  width: 30px;
  text-align: center;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}
.input-field-version:hover {
  background-color: lightyellow;
}
.input-field-version:focus {
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

.create-picture-buttons-group {
  font-size: small;
  display: flex;
  flex-direction: column;
}
.icon-copy {
  width: 24px;
  height: 24px;
  margin-left: -6px;
  margin-top: -10px;
}



.icon-paste {
  width: 24px;
  height: 24px;
  margin-left: -6px;
  margin-top: -10px;
}

.icon-save {
  width: 18px;
  height: 18px;
  margin-left: -4px;
  margin-top: -10px;
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
  margin-top: -10px;
}

.icon-new-wide {
  width: 18px;
  height: 18px;
  margin-left: 0;
  margin-top: -10px;
}

.icon-open-wide {
  width: 18px;
  height: 18px;
  margin-left: 0;
  margin-top: -10px;
}

.icon-texthead {
  width: 18px;
  height: 18px;
  margin-left: -4px;
  margin-top: -10px;
}

.icon-texthead-wide {
  width: 18px;
  height: 18px;
  margin-left: 0;
  margin-top: -10px;
}

.icon-textbody {
  width: 18px;
  height: 18px;
  margin-left: -4px;
  margin-top: -10px;
}

.icon-textlink {
  width: 18px;
  height: 18px;
  margin-left: -4px;
  margin-top: -10px;
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
  margin-top: -10px;
}

.icon-play-wide {
  width: 50px;
  height: 24px;
  margin-left: -5px;
  margin-top: -10px;
}

.icon-edit {
  width: 24px;
  height: 24px;
  margin-left: -5px;
  margin-top: -10px;
}

.icon-edit-double {
  width: 40px;
  height: 40px;
  margin-left: 0;
  margin-top: -5px;
}

.icon-song-double {
  width: 50px;
  height: 50px;
  margin-left: -5px;
  margin-top: -10px;
}

.icon-24 {
  width: 24px;
  height: 24px;
}

.icon-40 {
  width: 40px;
  height: 40px;
}

.picture-author {
  background-color: black;
  width: 250px;
  height: 100px;
}
.image-author {
  width: 250px;
  height: 100px;
}

.picture-album {
  background-color: black;
  width: 250px;
  height: 250px;
}
.image-album {
  width: 250px;
  height: 250px;
}

.formatted-text-area {
  width: auto;
  height: 100%;
}

.formatted-notes-area {
  width: auto;
  height: 100%;
}

.formatted-chords-area {
  width: auto;
  height: 100%;
}

.formatted-text {
  overflow-y: scroll;
  background-color: black;
  max-height: 630px;
  text-align: left;
  padding: 10px;
  font-size: smaller;
  min-width: 300px;
  min-height: 944px;
}

.formatted-notes {
  overflow-y: scroll;
  background-color: black;
  max-height: 944px;
  text-align: left;
  padding: 10px;
  font-size: smaller;
  min-width: 0;
  min-height: 0;
}

.formatted-chords {
  overflow-y: scroll;
  background-color: black;
  max-height: 944px;
  text-align: left;
  padding: 10px;
  font-size: smaller;
  min-width: 0;
  min-height: 0;
}

.column-1 {
  width: max-content;
  height: max-content;
  /*background-color: white;*/
  margin: 5px 5px 5px 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.column-2 {
  width: max-content;
  height: max-content;
  margin: 5px 0;
}

.column-3 {
  width: max-content;
  height: max-content;
  margin: 5px 0;
}

.column-4 {
  width: max-content;
  height: max-content;
  margin: 5px 0;
}

.column-5 {
  width: max-content;
  height: max-content;
  margin: 5px 5px 5px 5px;
}

.navigation-buttons {
  display: flex;
  flex-direction: row;
}
.navigation-buttons-column {
  display: flex;
  flex-direction: column;
}

</style>