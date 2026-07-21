<template>
  <div :style="styleRoot">
    <div v-if="song">
      <subs-edit v-if="isSubsEditVisible" :voices="voices" :song="song" @close="closeSubsEdit" />
      <custom-confirm
        v-if="isCustomConfirmVisible"
        :params="customConfirmParams"
        @close="closeCustomConfirm"
      />
      <health-report-table
        v-if="isHealthReportTableVisible"
        :id="song.id"
        @close="closeHealthReportTable"
      />
      <family-songs-modal
        v-if="isFamilySongsVisible"
        :id="song.id"
        @select="selectFamilySong"
        @close="closeFamilySongs"
      />
      <ReviewModal
        v-if="isAssignReviewVisible"
        @close="isAssignReviewVisible = false"
        @reviewed="onAssignmentReviewed"
      />
      <song-karaoke-editor-modal
        v-if="isKaraokeEditorVisible"
        :id="song.id"
        mode="song"
        :target="karaokeEditorTarget"
        @close="closeKaraokeEditor"
      />
      <datalist id="list_hours">
        <option v-for="hour in hours" :key="hour" :value="hour" />
      </datalist>
      <datalist id="list_free_time_slots">
        <option v-for="freeTimeSlot in freeTimeSlots" :key="freeTimeSlot" :value="freeTimeSlot" />
      </datalist>
      <!-- Заголовок с названием песни и автора-->
      <div class="header">
        <div class="header-column-1">
          <div class="header-song-id" @click="copyToClipboard(song.id, 'id')">{{ song.id }}</div>
          <div style="display: flex; flex-direction: row; gap: 4px; justify-content: center">
            <button
              class="btn-round"
              title="Открыть плеер"
              style="display: flex; align-items: center; justify-content: center"
              @click="openPlayer"
            >
              ▶
            </button>
            <a
              class="btn-round"
              style="
                display: flex;
                align-items: center;
                justify-content: center;
                text-decoration: none;
                color: black;
              "
              :href="`/api/song/${song.id}/playerfile`"
              download
              title="Скачать .smkaraoke"
              >⬇</a
            >
          </div>
          <div v-if="song.idStatus < 3" style="margin-top: 4px">
            <button
              v-if="assignmentInfo"
              class="assign-badge"
              :class="`assign-badge-${assignmentInfo.status}`"
              :title="assignmentInfo.assigneeName"
              @click="openAssignmentReview"
            >
              {{ assignStatusLabel(assignmentInfo.status) }}
            </button>
            <select v-else class="assign-select" @change="onAssignSelect">
              <option value="" selected disabled>Назначить…</option>
              <option v-for="u in editorSiteUsers" :key="u.id" :value="u.id">
                {{ u.displayName || u.email }}
              </option>
            </select>
          </div>
        </div>
        <div class="header-column-2">
          <div class="header-song-author" @click="copyToClipboard(song.author, 'author')">
            {{ song.author }}
          </div>
          <div class="header-song-album" @click="copyToClipboard(song.album, 'album')">
            {{ song.year }} - {{ song.album }}
          </div>
          <div class="header-song-name" @click="copyToClipboard(song.songName, 'songName')">
            {{ song.songName }}
          </div>
        </div>
      </div>
      <!-- Тело-->
      <div class="body">
        <!-- Первый столбец тела -->
        <div class="column-1">
          <div class="label-and-input">
            <div class="label">Композиция:</div>
            <input v-model="song.songName" class="input-field" />
            <button
              class="btn-round"
              :disabled="notChanged('songName')"
              @click="undoField('songName')"
            >
              <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
            </button>
            <button
              class="btn-round"
              :disabled="!song.songName"
              @click="copyToClipboard(song.songName, 'songName')"
            >
              <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
            </button>
            <button class="btn-round" @click="pasteFromClipboard('songName')">
              <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
            </button>
          </div>
          <div class="label-and-input">
            <div class="label">Исполнитель:</div>
            <input v-model="song.author" class="input-field" />
            <button class="btn-round" :disabled="notChanged('author')" @click="undoField('author')">
              <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
            </button>
            <button
              class="btn-round"
              :disabled="!song.author"
              @click="copyToClipboard(song.author, 'author')"
            >
              <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
            </button>
            <button class="btn-round" @click="pasteFromClipboard('author')">
              <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
            </button>
          </div>
          <div class="label-and-input">
            <div class="label">Год:</div>
            <input v-model="song.year" class="input-field" />
            <button class="btn-round" :disabled="notChanged('year')" @click="undoField('year')">
              <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
            </button>
            <button
              class="btn-round"
              :disabled="!song.year"
              @click="copyToClipboard(song.year, 'year')"
            >
              <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
            </button>
            <button class="btn-round" @click="pasteFromClipboard('year')">
              <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
            </button>
          </div>
          <div class="label-and-input">
            <div class="label">Альбом:</div>
            <input v-model="song.album" class="input-field" />
            <button class="btn-round" :disabled="notChanged('album')" @click="undoField('album')">
              <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
            </button>
            <button
              class="btn-round"
              :disabled="!song.album"
              @click="copyToClipboard(song.album, 'album')"
            >
              <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
            </button>
            <button class="btn-round" @click="pasteFromClipboard('album')">
              <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
            </button>
          </div>
          <div class="label-and-input">
            <div class="label">№ трека:</div>
            <input v-model="song.track" class="input-field" />
            <button class="btn-round" :disabled="notChanged('track')" @click="undoField('track')">
              <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
            </button>
            <button
              class="btn-round"
              :disabled="!song.track"
              @click="copyToClipboard(song.track, 'track')"
            >
              <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
            </button>
            <button class="btn-round" @click="pasteFromClipboard('track')">
              <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
            </button>
          </div>
          <div class="label-and-input">
            <div class="label">Тональность:</div>
            <input v-model="song.key" class="input-field" />
            <button class="btn-round" :disabled="notChanged('key')" @click="undoField('key')">
              <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
            </button>
            <button
              class="btn-round"
              :disabled="!song.key"
              @click="copyToClipboard(song.key, 'key')"
            >
              <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
            </button>
            <button class="btn-round" @click="pasteFromClipboard('key')">
              <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
            </button>
          </div>
          <div class="label-and-input">
            <div class="label">Темп:</div>
            <input v-model="song.bpm" class="input-field" />
            <button class="btn-round" :disabled="notChanged('bpm')" @click="undoField('bpm')">
              <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
            </button>
            <button
              class="btn-round"
              :disabled="!song.bpm"
              @click="copyToClipboard(song.bpm, 'bpm')"
            >
              <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
            </button>
            <button class="btn-round" @click="pasteFromClipboard('bpm')">
              <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
            </button>
          </div>
          <div class="label-and-input">
            <div class="label">Тэги:</div>
            <input v-model="song.tags" class="input-field" />
            <button class="btn-round" :disabled="notChanged('tags')" @click="undoField('tags')">
              <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
            </button>
            <button
              class="btn-round"
              :disabled="!song.tags"
              @click="copyToClipboard(song.tags, 'tags')"
            >
              <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
            </button>
            <button class="btn-round" @click="pasteFromClipboard('tags')">
              <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
            </button>
          </div>
          <div class="label-and-input">
            <div class="label">Тип песни:</div>
            <select v-model="song.songType" class="input-field">
              <option v-for="opt in songTypeOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
            <button
              class="btn-round"
              :disabled="notChanged('songType')"
              @click="undoField('songType')"
            >
              <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
            </button>
            <button
              class="btn-round"
              :disabled="!song.songType"
              @click="copyToClipboard(song.songType, 'songType')"
            >
              <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
            </button>
          </div>
          <div class="label-and-input">
            <div class="label">Дата:</div>
            <input v-model="song.date" class="input-field" list="list_free_time_slots" />
            <button class="btn-round" :disabled="notChanged('date')" @click="undoField('date')">
              <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
            </button>
            <button
              class="btn-round"
              :disabled="!song.date"
              @click="copyToClipboard(song.date, 'date')"
            >
              <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
            </button>
            <button class="btn-round" @click="pasteFromClipboard('date')">
              <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
            </button>
          </div>
          <div class="label-and-input">
            <div class="label">Время:</div>
            <input v-model="song.time" class="input-field" list="list_hours" />
            <button class="btn-round" :disabled="notChanged('time')" @click="undoField('time')">
              <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
            </button>
            <button
              class="btn-round"
              :disabled="!song.time"
              @click="copyToClipboard(song.time, 'time')"
            >
              <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
            </button>
            <button class="btn-round" @click="pasteFromClipboard('time')">
              <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
            </button>
          </div>
          <div class="label-and-input">
            <div class="label">Корневая папка:</div>
            <input v-model="song.rootFolder" class="input-field" />
            <button
              class="btn-round"
              :disabled="notChanged('rootFolder')"
              @click="undoField('rootFolder')"
            >
              <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
            </button>
            <button
              class="btn-round"
              :disabled="!song.rootFolder"
              @click="copyToClipboard(song.rootFolder, 'rootFolder')"
            >
              <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
            </button>
            <button class="btn-round" @click="pasteFromClipboard('rootFolder')">
              <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
            </button>
          </div>
          <div class="label-and-input">
            <div class="label">Имя файла:</div>
            <input v-model="song.fileName" class="input-field" />
            <button
              class="btn-round"
              :disabled="notChanged('fileName')"
              @click="undoField('fileName')"
            >
              <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
            </button>
            <button
              class="btn-round"
              :disabled="!song.fileName"
              @click="copyToClipboard(song.fileName, 'fileName')"
            >
              <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
            </button>
            <button class="btn-round" @click="pasteFromClipboard('fileName')">
              <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
            </button>
          </div>
          <div class="label-and-input">
            <div class="label">Версия:</div>
            <input v-model="song.resultVersion" class="input-field" />
            <button
              class="btn-round"
              :disabled="notChanged('resultVersion')"
              @click="undoField('resultVersion')"
            >
              <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
            </button>
            <button
              class="btn-round"
              :disabled="!song.resultVersion"
              @click="copyToClipboard(song.resultVersion, 'resultVersion')"
            >
              <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
            </button>
            <button class="btn-round" @click="pasteFromClipboard('resultVersion')">
              <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
            </button>
          </div>
          <div class="label-and-input">
            <div class="label">Root ID:</div>
            <input v-model="song.rootId" class="input-field" />
            <button class="btn-round" :disabled="notChanged('rootId')" @click="undoField('rootId')">
              <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
            </button>
            <button
              class="btn-round"
              :disabled="!song.rootId"
              @click="copyToClipboard(song.rootId, 'rootId')"
            >
              <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
            </button>
            <button class="btn-round" @click="pasteFromClipboard('rootId')">
              <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
            </button>
          </div>
          <div class="label-and-input">
            <div class="label-wide">Аудио-родитель (id):</div>
            <input v-model="song.audioParentId" class="input-field-short" />
            <button
              class="btn-round"
              :disabled="notChanged('audioParentId')"
              @click="undoField('audioParentId')"
            >
              <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
            </button>
            <button
              class="btn-round"
              :disabled="!song.audioParentId"
              @click="copyToClipboard(song.audioParentId, 'audioParentId')"
            >
              <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
            </button>
            <button class="btn-round" @click="pasteFromClipboard('audioParentId')">
              <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
            </button>
          </div>
          <div class="label-and-input">
            <div class="label-wide">Аудио-схожесть, %:</div>
            <input v-model="song.audioSimilarityPercent" class="input-field-short" />
            <button
              class="btn-round"
              :disabled="notChanged('audioSimilarityPercent')"
              @click="undoField('audioSimilarityPercent')"
            >
              <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
            </button>
            <button
              class="btn-round"
              :disabled="!song.audioSimilarityPercent"
              @click="copyToClipboard(song.audioSimilarityPercent, 'audioSimilarityPercent')"
            >
              <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
            </button>
            <button class="btn-round" @click="pasteFromClipboard('audioSimilarityPercent')">
              <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
            </button>
          </div>
          <div class="label-and-input">
            <div class="label-wide">Аудио-сдвиг, мс:</div>
            <input v-model="song.audioDeltaMs" class="input-field-short" />
            <button
              class="btn-round"
              :disabled="notChanged('audioDeltaMs')"
              @click="undoField('audioDeltaMs')"
            >
              <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
            </button>
            <button
              class="btn-round"
              :disabled="!song.audioDeltaMs"
              @click="copyToClipboard(song.audioDeltaMs, 'audioDeltaMs')"
            >
              <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
            </button>
            <button class="btn-round" @click="pasteFromClipboard('audioDeltaMs')">
              <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
            </button>
          </div>

          <div class="links-table">
            <div class="links-table-column-1">
              <!--              <img class="icon-36" alt="boosty" src="../../../assets/svg/icon_boosty_color.svg">-->
              <img class="icon-24" alt="documents" src="../../../assets/svg/icon_sponsr.svg" />
            </div>
            <div class="links-table-column-2">
              <!--              <div class="label-and-input">-->
              <!--                <img class="icon-24" alt="documents" src="../../../assets/svg/icon_documents.svg">-->
              <!--                <button v-if="song.idBoosty" class="btn-round-wide" @click="openLinkBoosty"><img alt="open" class="icon-open-wide" src="../../../assets/svg/icon_open.svg"></button>-->
              <!--                <button v-else class="btn-round-wide" @click="openLinkBoostyNew"><img alt="new" class="icon-new-wide" src="../../../assets/svg/icon_new.svg"></button>-->
              <!--                <button class="btn-round" @click="getBoostyHeader"><img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg"></button>-->
              <!--                <button class="btn-round" @click="getBoostyBody"><img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg"></button>-->
              <!--                <button class="btn-round" @click="getLinkBoosty" :disabled="!song.idBoosty"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>-->
              <!--                <input class="input-link-field" v-model="song.idBoosty">-->
              <!--                <input class="input-field-version" v-model="song.versionBoosty">-->
              <!--                <button class="btn-round" @click="undoField('idBoosty')" :disabled="notChanged('idBoosty')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>-->
              <!--                <button class="btn-round" @click="copyToClipboard(song.idBoosty, 'idBoosty')" :disabled="!song.idBoosty"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>-->
              <!--                <button class="btn-round" @click="pasteFromClipboard('idBoosty')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>-->
              <!--              </div>-->
              <!--              <div class="label-and-input">-->
              <!--                <img class="icon-24" alt="files" src="../../../assets/svg/icon_files.svg">-->
              <!--                <button v-if="song.idBoostyFiles" class="btn-round-wide" @click="openLinkBoostyFiles"><img alt="open" class="icon-open-wide" src="../../../assets/svg/icon_open.svg"></button>-->
              <!--                <button v-else class="btn-round-wide" @click="openLinkBoostyFilesNew"><img alt="new" class="icon-new-wide" src="../../../assets/svg/icon_new.svg"></button>-->
              <!--                <button class="btn-round-wide" @click="getBoostyFilesHeader"><img alt="head" class="icon-texthead-wide" src="../../../assets/svg/icon_head.svg"></button>-->
              <!--                <button class="btn-round" @click="getLinkBoostyFiles" :disabled="!song.idBoostyFiles"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>-->
              <!--                <input class="input-link-field" v-model="song.idBoostyFiles">-->
              <!--                <input class="input-field-version" v-model="song.versionBoostyFiles">-->
              <!--                <button class="btn-round" @click="undoField('idBoostyFiles')" :disabled="notChanged('idBoostyFiles')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>-->
              <!--                <button class="btn-round" @click="copyToClipboard(song.idBoostyFiles, 'idBoostyFiles')" :disabled="!song.idBoostyFiles"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>-->
              <!--                <button class="btn-round" @click="pasteFromClipboard('idBoostyFiles')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>-->
              <!--              </div>-->
              <div class="label-and-input">
                <!--                <img class="icon-24" alt="documents" src="../../../assets/svg/icon_sponsr.svg">-->
                <button v-if="song.idSponsr" class="btn-round-wide" @click="openLinkSponsr">
                  <img alt="open" class="icon-open-wide" src="../../../assets/svg/icon_open.svg" />
                </button>
                <button v-else class="btn-round-wide" @click="openLinkSponsrNew">
                  <img alt="new" class="icon-new-wide" src="../../../assets/svg/icon_new.svg" />
                </button>
                <button class="btn-round" @click="getSponsrHeader">
                  <img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg" />
                </button>
                <button class="btn-round" @click="getSponsrBody">
                  <img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg" />
                </button>
                <button class="btn-round" :disabled="!song.idSponsr" @click="getLinkSponsrPlay">
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idSponsr" class="input-link-field" />
                <input v-model="song.versionSponsr" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idSponsr')"
                  @click="undoField('idSponsr')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idSponsr"
                  @click="copyToClipboard(song.idSponsr, 'idSponsr')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idSponsr')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
            </div>
          </div>

          <div class="links-table">
            <div class="links-table-column-1">
              <img class="icon-vk2" alt="vk group" src="../../../assets/svg/icon_vk2.svg" />
            </div>
            <div class="links-table-column-2">
              <div class="label-and-input">
                <img class="icon-24" alt="documents" src="../../../assets/svg/icon_documents.svg" />
                <button class="btn-round-wide" :disabled="!song.idVk" @click="openLinkVkGroup">
                  <img alt="open" class="icon-open-wide" src="../../../assets/svg/icon_open.svg" />
                </button>
                <button class="btn-round-wide" @click="getVkGroupBody">
                  <img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg" />
                </button>
                <button class="btn-round" @click="getVkGroupBodySponsr">
                  <img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg" />
                </button>
                <button class="btn-round" :disabled="!song.idVk" @click="getLinkVkGroup">
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idVk" class="input-link-field" />
                <button class="btn-round" :disabled="notChanged('idVk')" @click="undoField('idVk')">
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idVk"
                  @click="copyToClipboard(song.idVk, 'idVk')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idVk')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
            </div>
          </div>

          <div v-if="!song.exclusive" class="links-table">
            <div class="links-table-column-1">
              <img class="icon-36" alt="dzen" src="../../../assets/svg/icon_dzen.svg" />
            </div>
            <div class="links-table-column-2">
              <div class="label-and-input">
                <img class="icon-24" alt="karaoke" src="../../../assets/svg/icon_microphone.svg" />
                <button
                  class="btn-round"
                  :disabled="!song.idDzenKaraoke"
                  @click="openLinkDzenKaraokePlay"
                >
                  <img alt="play" class="icon-play" src="../../../assets/svg/icon_play.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idDzenKaraoke"
                  @click="openLinkDzenKaraokeEdit"
                >
                  <img alt="edit" class="icon-edit" src="../../../assets/svg/icon_edit.svg" />
                </button>
                <button class="btn-round" @click="getDzenKaraokeHeader">
                  <img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg" />
                </button>
                <button class="btn-round" @click="getDzenKaraokeBody">
                  <img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idDzenKaraoke"
                  @click="getLinkDzenKaraokePlay"
                >
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idDzenKaraoke" class="input-link-field" />
                <input v-model="song.versionDzenKaraoke" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idDzenKaraoke')"
                  @click="undoField('idDzenKaraoke')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idDzenKaraoke"
                  @click="copyToClipboard(song.idDzenKaraoke, 'idDzenKaraoke')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idDzenKaraoke')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="song" src="../../../assets/svg/icon_song.svg" />
                <button
                  class="btn-round"
                  :disabled="!song.idDzenLyrics"
                  @click="openLinkDzenLyricsPlay"
                >
                  <img alt="play" class="icon-play" src="../../../assets/svg/icon_play.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idDzenLyrics"
                  @click="openLinkDzenLyricsEdit"
                >
                  <img alt="edit" class="icon-edit" src="../../../assets/svg/icon_edit.svg" />
                </button>
                <button class="btn-round" @click="getDzenLyricsHeader">
                  <img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg" />
                </button>
                <button class="btn-round" @click="getDzenLyricsBody">
                  <img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idDzenLyrics"
                  @click="getLinkDzenLyricsPlay"
                >
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idDzenLyrics" class="input-link-field" />
                <input v-model="song.versionDzenLyrics" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idDzenLyrics')"
                  @click="undoField('idDzenLyrics')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idDzenLyrics"
                  @click="copyToClipboard(song.idDzenLyrics, 'idDzenLyrics')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idDzenLyrics')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="demo" src="../../../assets/svg/icon_demo.svg" />
                <button
                  class="btn-round"
                  :disabled="!song.idDzenDemo"
                  @click="openLinkDzenDemoPlay"
                >
                  <img alt="play" class="icon-play" src="../../../assets/svg/icon_play.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idDzenDemo"
                  @click="openLinkDzenDemoEdit"
                >
                  <img alt="edit" class="icon-edit" src="../../../assets/svg/icon_edit.svg" />
                </button>
                <button class="btn-round" @click="getDzenDemoHeader">
                  <img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg" />
                </button>
                <button class="btn-round" @click="getDzenDemoBody">
                  <img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg" />
                </button>
                <button class="btn-round" :disabled="!song.idDzenDemo" @click="getLinkDzenDemoPlay">
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idDzenDemo" class="input-link-field" />
                <input v-model="song.versionDzenDemo" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idDzenDemo')"
                  @click="undoField('idDzenDemo')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idDzenDemo"
                  @click="copyToClipboard(song.idDzenDemo, 'idDzenDemo')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idDzenDemo')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
              <div v-if="showChordsDzen" class="label-and-input">
                <img class="icon-24" alt="chords" src="../../../assets/svg/icon_chords.svg" />
                <button
                  class="btn-round"
                  :disabled="!song.idDzenChords"
                  @click="openLinkDzenChordsPlay"
                >
                  <img alt="play" class="icon-play" src="../../../assets/svg/icon_play.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idDzenChords"
                  @click="openLinkDzenChordsEdit"
                >
                  <img alt="edit" class="icon-edit" src="../../../assets/svg/icon_edit.svg" />
                </button>
                <button class="btn-round" @click="getDzenChordsHeader">
                  <img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg" />
                </button>
                <button class="btn-round" @click="getDzenChordsBody">
                  <img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idDzenChords"
                  @click="getLinkDzenChordsPlay"
                >
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idDzenChords" class="input-link-field" />
                <input v-model="song.versionDzenChords" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idDzenChords')"
                  @click="undoField('idDzenChords')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idDzenChords"
                  @click="copyToClipboard(song.idDzenChords, 'idDzenChords')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idDzenChords')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
              <div v-if="showMelodyDzen" class="label-and-input">
                <img class="icon-24" alt="melody" src="../../../assets/svg/icon_melody.svg" />
                <button
                  class="btn-round"
                  :disabled="!song.idDzenMelody"
                  @click="openLinkDzenTabsPlay"
                >
                  <img alt="play" class="icon-play" src="../../../assets/svg/icon_play.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idDzenMelody"
                  @click="openLinkDzenTabsEdit"
                >
                  <img alt="edit" class="icon-edit" src="../../../assets/svg/icon_edit.svg" />
                </button>
                <button class="btn-round" @click="getDzenTabsHeader">
                  <img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg" />
                </button>
                <button class="btn-round" @click="getDzenTabsBody">
                  <img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idDzenMelody"
                  @click="getLinkDzenTabsPlay"
                >
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idDzenMelody" class="input-link-field" />
                <input v-model="song.versionDzenMelody" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idDzenMelody')"
                  @click="undoField('idDzenMelody')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idDzenMelody"
                  @click="copyToClipboard(song.idDzenMelody, 'idDzenMelody')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idDzenMelody')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
            </div>
          </div>

          <div v-if="!song.exclusive" class="links-table">
            <div class="links-table-column-1">
              <img class="icon-36" alt="vk" src="../../../assets/svg/icon_vk.svg" />
            </div>
            <div class="links-table-column-2">
              <div class="label-and-input">
                <img class="icon-24" alt="karaoke" src="../../../assets/svg/icon_microphone.svg" />
                <button
                  class="btn-round-wide"
                  :disabled="!song.idVkKaraoke"
                  @click="openLinkVkKaraoke"
                >
                  <img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg" />
                </button>
                <button class="btn-round" @click="getVkKaraokeHeader">
                  <img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg" />
                </button>
                <button class="btn-round" @click="getVkKaraokeBody">
                  <img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg" />
                </button>
                <button class="btn-round" :disabled="!song.idVkKaraoke" @click="getLinkVkKaraoke">
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idVkKaraoke" class="input-link-field" />
                <input v-model="song.versionVkKaraoke" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idVkKaraoke')"
                  @click="undoField('idVkKaraoke')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idVkKaraoke"
                  @click="copyToClipboard(song.idVkKaraoke, 'idVkKaraoke')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idVkKaraoke')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="song" src="../../../assets/svg/icon_song.svg" />
                <button
                  class="btn-round-wide"
                  :disabled="!song.idVkLyrics"
                  @click="openLinkVkLyrics"
                >
                  <img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg" />
                </button>
                <button class="btn-round" @click="getVkLyricsHeader">
                  <img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg" />
                </button>
                <button class="btn-round" @click="getVkLyricsBody">
                  <img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg" />
                </button>
                <button class="btn-round" :disabled="!song.idVkLyrics" @click="getLinkVkLyrics">
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idVkLyrics" class="input-link-field" />
                <input v-model="song.versionVkLyrics" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idVkLyrics')"
                  @click="undoField('idVkLyrics')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idVkLyrics"
                  @click="copyToClipboard(song.idVkLyrics, 'idVkLyrics')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idVkLyrics')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="demo" src="../../../assets/svg/icon_demo.svg" />
                <button class="btn-round-wide" :disabled="!song.idVkDemo" @click="openLinkVkDemo">
                  <img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg" />
                </button>
                <button class="btn-round" @click="getVkDemoHeader">
                  <img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg" />
                </button>
                <button class="btn-round" @click="getVkDemoBody">
                  <img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg" />
                </button>
                <button class="btn-round" :disabled="!song.idVkDemo" @click="getLinkVkDemo">
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idVkDemo" class="input-link-field" />
                <input v-model="song.versionVkDemo" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idVkDemo')"
                  @click="undoField('idVkDemo')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idVkDemo"
                  @click="copyToClipboard(song.idVkDemo, 'idVkDemo')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idVkDemo')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
              <div v-if="showChordsVk" class="label-and-input">
                <img class="icon-24" alt="song" src="../../../assets/svg/icon_chords.svg" />
                <button
                  class="btn-round-wide"
                  :disabled="!song.idVkChords"
                  @click="openLinkVkChords"
                >
                  <img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg" />
                </button>
                <button class="btn-round" @click="getVkChordsHeader">
                  <img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg" />
                </button>
                <button class="btn-round" @click="getVkChordsBody">
                  <img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg" />
                </button>
                <button class="btn-round" :disabled="!song.idVkChords" @click="getLinkVkChords">
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idVkChords" class="input-link-field" />
                <input v-model="song.versionVkChords" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idVkChords')"
                  @click="undoField('idVkChords')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idVkChords"
                  @click="copyToClipboard(song.idVkChords, 'idVkChords')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idVkChords')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
              <div v-if="showMelodyVk" class="label-and-input">
                <img class="icon-24" alt="song" src="../../../assets/svg/icon_melody.svg" />
                <button class="btn-round-wide" :disabled="!song.idVkMelody" @click="openLinkVkTabs">
                  <img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg" />
                </button>
                <button class="btn-round" @click="getVkTabsHeader">
                  <img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg" />
                </button>
                <button class="btn-round" @click="getVkTabsBody">
                  <img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg" />
                </button>
                <button class="btn-round" :disabled="!song.idVkMelody" @click="getLinkVkTabs">
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idVkMelody" class="input-link-field" />
                <input v-model="song.versionVkMelody" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idVkMelody')"
                  @click="undoField('idVkMelody')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idVkMelody"
                  @click="copyToClipboard(song.idVkMelody, 'idVkMelody')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idVkMelody')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
            </div>
          </div>

          <!--          <div class="links-table" v-if="!song.exclusive">-->
          <!--            <div class="links-table-column-1">-->
          <!--              <img class="icon-36" alt="pl" src="../../../assets/svg/icon_pl.svg">-->
          <!--            </div>-->
          <!--            <div class="links-table-column-2">-->
          <!--              <div class="label-and-input">-->
          <!--                <img class="icon-24" alt="karaoke" src="../../../assets/svg/icon_microphone.svg">-->
          <!--                <button class="btn-round" @click="openLinkPlKaraokePlay" :disabled="!song.idPlKaraoke"><img alt="play" class="icon-play" src="../../../assets/svg/icon_play.svg"></button>-->
          <!--                <button class="btn-round" @click="openLinkPlKaraokeEdit" :disabled="!song.idPlKaraoke"><img alt="edit" class="icon-edit" src="../../../assets/svg/icon_edit.svg"></button>-->
          <!--                <button class="btn-round" @click="getPlKaraokeHeader"><img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg"></button>-->
          <!--                <button class="btn-round" @click="getPlKaraokeBody"><img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg"></button>-->
          <!--                <button class="btn-round" @click="getLinkPlKaraokePlay" :disabled="!song.idPlKaraoke"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>-->
          <!--                <input class="input-link-field" v-model="song.idPlKaraoke">-->
          <!--                <input class="input-field-version" v-model="song.versionPlKaraoke">-->
          <!--                <button class="btn-round" @click="undoField('idPlKaraoke')" :disabled="notChanged('idPlKaraoke')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>-->
          <!--                <button class="btn-round" @click="copyToClipboard(song.idPlKaraoke,'idPlKaraoke')" :disabled="!song.idPlKaraoke"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>-->
          <!--                <button class="btn-round" @click="pasteFromClipboard('idPlKaraoke')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>-->
          <!--              </div>-->
          <!--              <div class="label-and-input">-->
          <!--                <img class="icon-24" alt="song" src="../../../assets/svg/icon_song.svg">-->
          <!--                <button class="btn-round" @click="openLinkPlLyricsPlay" :disabled="!song.idPlLyrics"><img alt="play" class="icon-play" src="../../../assets/svg/icon_play.svg"></button>-->
          <!--                <button class="btn-round" @click="openLinkPlLyricsEdit" :disabled="!song.idPlLyrics"><img alt="edit" class="icon-edit" src="../../../assets/svg/icon_edit.svg"></button>-->
          <!--                <button class="btn-round" @click="getPlLyricsHeader"><img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg"></button>-->
          <!--                <button class="btn-round" @click="getPlLyricsBody"><img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg"></button>-->
          <!--                <button class="btn-round" @click="getLinkPlLyricsPlay" :disabled="!song.idPlLyrics"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>-->
          <!--                <input class="input-link-field" v-model="song.idPlLyrics">-->
          <!--                <input class="input-field-version" v-model="song.versionPlLyrics">-->
          <!--                <button class="btn-round" @click="undoField('idPlLyrics')" :disabled="notChanged('idPlLyrics')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>-->
          <!--                <button class="btn-round" @click="copyToClipboard(song.idPlLyrics, 'idPlLyrics')" :disabled="!song.idPlLyrics"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>-->
          <!--                <button class="btn-round" @click="pasteFromClipboard('idPlLyrics')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>-->
          <!--              </div>-->
          <!--              <div class="label-and-input">-->
          <!--                <img class="icon-24" alt="song" src="../../../assets/svg/icon_chords.svg">-->
          <!--                <button class="btn-round" @click="openLinkPlChordsPlay" :disabled="!song.idPlChords"><img alt="play" class="icon-play" src="../../../assets/svg/icon_play.svg"></button>-->
          <!--                <button class="btn-round" @click="openLinkPlChordsEdit" :disabled="!song.idPlChords"><img alt="edit" class="icon-edit" src="../../../assets/svg/icon_edit.svg"></button>-->
          <!--                <button class="btn-round" @click="getPlChordsHeader"><img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg"></button>-->
          <!--                <button class="btn-round" @click="getPlChordsBody"><img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg"></button>-->
          <!--                <button class="btn-round" @click="getLinkPlChordsPlay" :disabled="!song.idPlChords"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>-->
          <!--                <input class="input-link-field" v-model="song.idPlChords">-->
          <!--                <input class="input-field-version" v-model="song.versionPlChords">-->
          <!--                <button class="btn-round" @click="undoField('idPlChords')" :disabled="notChanged('idPlChords')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>-->
          <!--                <button class="btn-round" @click="copyToClipboard(song.idPlChords,'idPlChords')" :disabled="!song.idPlChords"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>-->
          <!--                <button class="btn-round" @click="pasteFromClipboard('idPlChords')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>-->
          <!--              </div>-->
          <!--              <div class="label-and-input">-->
          <!--                <img class="icon-24" alt="song" src="../../../assets/svg/icon_melody.svg">-->
          <!--                <button class="btn-round" @click="openLinkPlTabsPlay" :disabled="!song.idPlMelody"><img alt="play" class="icon-play" src="../../../assets/svg/icon_play.svg"></button>-->
          <!--                <button class="btn-round" @click="openLinkPlTabsEdit" :disabled="!song.idPlMelody"><img alt="edit" class="icon-edit" src="../../../assets/svg/icon_edit.svg"></button>-->
          <!--                <button class="btn-round" @click="getPlTabsHeader"><img alt="head" class="icon-texthead" src="../../../assets/svg/icon_head.svg"></button>-->
          <!--                <button class="btn-round" @click="getPlTabsBody"><img alt="body" class="icon-textbody" src="../../../assets/svg/icon_body.svg"></button>-->
          <!--                <button class="btn-round" @click="getLinkPlTabsPlay" :disabled="!song.idPlMelody"><img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg"></button>-->
          <!--                <input class="input-link-field" v-model="song.idPlMelody">-->
          <!--                <input class="input-field-version" v-model="song.versionPlMelody">-->
          <!--                <button class="btn-round" @click="undoField('idPlMelody')" :disabled="notChanged('idPlMelody')"><img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg"></button>-->
          <!--                <button class="btn-round" @click="copyToClipboard(song.idPlMelody,'idPlMelody')" :disabled="!song.idPlMelody"><img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg"></button>-->
          <!--                <button class="btn-round" @click="pasteFromClipboard('idPlMelody')"><img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg"></button>-->
          <!--              </div>-->
          <!--            </div>-->
          <!--          </div>-->

          <div v-if="!song.exclusive" class="links-table">
            <div class="links-table-column-1">
              <img class="icon-36" alt="tg" src="../../../assets/svg/icon_telegram.svg" />
            </div>
            <div class="links-table-column-2">
              <div class="label-and-input">
                <img class="icon-24" alt="karaoke" src="../../../assets/svg/icon_microphone.svg" />
                <button
                  class="btn-round-wide"
                  :disabled="!song.idTelegramKaraoke"
                  @click="openLinkTelegramKaraoke"
                >
                  <img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg" />
                </button>
                <button class="btn-round-wide" @click="getTelegramKaraokeHeader">
                  <img
                    alt="head"
                    class="icon-texthead-wide"
                    src="../../../assets/svg/icon_head.svg"
                  />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idTelegramKaraoke"
                  @click="getLinkTelegramKaraoke"
                >
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idTelegramKaraoke" class="input-link-field" />
                <input v-model="song.versionTelegramKaraoke" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idTelegramKaraoke')"
                  @click="undoField('idTelegramKaraoke')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idTelegramKaraoke"
                  @click="copyToClipboard(song.idTelegramKaraoke, 'idTelegramKaraoke')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idTelegramKaraoke')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="song" src="../../../assets/svg/icon_song.svg" />
                <button
                  class="btn-round-wide"
                  :disabled="!song.idTelegramLyrics"
                  @click="openLinkTelegramLyrics"
                >
                  <img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg" />
                </button>
                <button class="btn-round-wide" @click="getTelegramLyricsHeader">
                  <img
                    alt="head"
                    class="icon-texthead-wide"
                    src="../../../assets/svg/icon_head.svg"
                  />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idTelegramLyrics"
                  @click="getLinkTelegramLyrics"
                >
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idTelegramLyrics" class="input-link-field" />
                <input v-model="song.versionTelegramLyrics" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idTelegramLyrics')"
                  @click="undoField('idTelegramLyrics')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idTelegramLyrics"
                  @click="copyToClipboard(song.idTelegramLyrics, 'idTelegramLyrics')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idTelegramLyrics')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="demo" src="../../../assets/svg/icon_demo.svg" />
                <button
                  class="btn-round-wide"
                  :disabled="!song.idTelegramDemo"
                  @click="openLinkTelegramDemo"
                >
                  <img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg" />
                </button>
                <button class="btn-round-wide" @click="getTelegramDemoHeader">
                  <img
                    alt="head"
                    class="icon-texthead-wide"
                    src="../../../assets/svg/icon_head.svg"
                  />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idTelegramDemo"
                  @click="getLinkTelegramDemo"
                >
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idTelegramDemo" class="input-link-field" />
                <input v-model="song.versionTelegramDemo" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idTelegramDemo')"
                  @click="undoField('idTelegramDemo')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idTelegramDemo"
                  @click="copyToClipboard(song.idTelegramDemo, 'idTelegramDemo')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idTelegramDemo')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
              <div v-if="showChordsTelegram" class="label-and-input">
                <img class="icon-24" alt="song" src="../../../assets/svg/icon_chords.svg" />
                <button
                  class="btn-round-wide"
                  :disabled="!song.idTelegramChords"
                  @click="openLinkTelegramChords"
                >
                  <img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg" />
                </button>
                <button class="btn-round-wide" @click="getTelegramChordsHeader">
                  <img
                    alt="head"
                    class="icon-texthead-wide"
                    src="../../../assets/svg/icon_head.svg"
                  />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idTelegramChords"
                  @click="getLinkTelegramChords"
                >
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idTelegramChords" class="input-link-field" />
                <input v-model="song.versionTelegramChords" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idTelegramChords')"
                  @click="undoField('idTelegramChords')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idTelegramChords"
                  @click="copyToClipboard(song.idTelegramChords, 'idTelegramChords')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idTelegramChords')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
              <div v-if="showMelodyTelegram" class="label-and-input">
                <img class="icon-24" alt="song" src="../../../assets/svg/icon_melody.svg" />
                <button
                  class="btn-round-wide"
                  :disabled="!song.idTelegramMelody"
                  @click="openLinkTelegramTabs"
                >
                  <img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg" />
                </button>
                <button class="btn-round-wide" @click="getTelegramTabsHeader">
                  <img
                    alt="head"
                    class="icon-texthead-wide"
                    src="../../../assets/svg/icon_head.svg"
                  />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idTelegramMelody"
                  @click="getLinkTelegramTabs"
                >
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idTelegramMelody" class="input-link-field" />
                <input v-model="song.versionTelegramMelody" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idTelegramMelody')"
                  @click="undoField('idTelegramMelody')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idTelegramMelody"
                  @click="copyToClipboard(song.idTelegramMelody, 'idTelegramMelody')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idTelegramMelody')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
            </div>
          </div>

          <div v-if="!song.exclusive" class="links-table">
            <div class="links-table-column-1">
              <img class="icon-36" alt="tg" src="../../../assets/svg/icon_max.svg" />
            </div>
            <div class="links-table-column-2">
              <div class="label-and-input">
                <img class="icon-24" alt="karaoke" src="../../../assets/svg/icon_microphone.svg" />
                <button
                  class="btn-round-wide"
                  :disabled="!song.idMaxKaraoke"
                  @click="openLinkMaxKaraoke"
                >
                  <img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg" />
                </button>
                <button class="btn-round-wide" @click="getMaxKaraokeHeader">
                  <img
                    alt="head"
                    class="icon-texthead-wide"
                    src="../../../assets/svg/icon_head.svg"
                  />
                </button>
                <button class="btn-round" :disabled="!song.idMaxKaraoke" @click="getLinkMaxKaraoke">
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idMaxKaraoke" class="input-link-field" />
                <input v-model="song.versionMaxKaraoke" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idMaxKaraoke')"
                  @click="undoField('idMaxKaraoke')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idMaxKaraoke"
                  @click="copyToClipboard(song.idMaxKaraoke, 'idMaxKaraoke')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idMaxKaraoke')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="song" src="../../../assets/svg/icon_song.svg" />
                <button
                  class="btn-round-wide"
                  :disabled="!song.idMaxLyrics"
                  @click="openLinkMaxLyrics"
                >
                  <img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg" />
                </button>
                <button class="btn-round-wide" @click="getMaxLyricsHeader">
                  <img
                    alt="head"
                    class="icon-texthead-wide"
                    src="../../../assets/svg/icon_head.svg"
                  />
                </button>
                <button class="btn-round" :disabled="!song.idMaxLyrics" @click="getLinkMaxLyrics">
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idMaxLyrics" class="input-link-field" />
                <input v-model="song.versionMaxLyrics" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idMaxLyrics')"
                  @click="undoField('idMaxLyrics')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idMaxLyrics"
                  @click="copyToClipboard(song.idMaxLyrics, 'idMaxLyrics')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idMaxLyrics')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
              <div class="label-and-input">
                <img class="icon-24" alt="demo" src="../../../assets/svg/icon_demo.svg" />
                <button class="btn-round-wide" :disabled="!song.idMaxDemo" @click="openLinkMaxDemo">
                  <img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg" />
                </button>
                <button class="btn-round-wide" @click="getMaxDemoHeader">
                  <img
                    alt="head"
                    class="icon-texthead-wide"
                    src="../../../assets/svg/icon_head.svg"
                  />
                </button>
                <button class="btn-round" :disabled="!song.idMaxDemo" @click="getLinkMaxDemo">
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idMaxDemo" class="input-link-field" />
                <input v-model="song.versionMaxDemo" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idMaxDemo')"
                  @click="undoField('idMaxDemo')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idMaxDemo"
                  @click="copyToClipboard(song.idMaxDemo, 'idMaxDemo')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idMaxDemo')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
              <div v-if="showChordsMax" class="label-and-input">
                <img class="icon-24" alt="song" src="../../../assets/svg/icon_chords.svg" />
                <button
                  class="btn-round-wide"
                  :disabled="!song.idMaxChords"
                  @click="openLinkMaxChords"
                >
                  <img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg" />
                </button>
                <button class="btn-round-wide" @click="getMaxChordsHeader">
                  <img
                    alt="head"
                    class="icon-texthead-wide"
                    src="../../../assets/svg/icon_head.svg"
                  />
                </button>
                <button class="btn-round" :disabled="!song.idMaxChords" @click="getLinkMaxChords">
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idMaxChords" class="input-link-field" />
                <input v-model="song.versionMaxChords" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idMaxChords')"
                  @click="undoField('idMaxChords')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idMaxChords"
                  @click="copyToClipboard(song.idMaxChords, 'idMaxChords')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idMaxChords')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
              <div v-if="showMelodyVk" class="label-and-input">
                <img class="icon-24" alt="song" src="../../../assets/svg/icon_melody.svg" />
                <button
                  class="btn-round-wide"
                  :disabled="!song.idMaxMelody"
                  @click="openLinkMaxTabs"
                >
                  <img alt="play" class="icon-play-wide" src="../../../assets/svg/icon_play.svg" />
                </button>
                <button class="btn-round-wide" @click="getMaxTabsHeader">
                  <img
                    alt="head"
                    class="icon-texthead-wide"
                    src="../../../assets/svg/icon_head.svg"
                  />
                </button>
                <button class="btn-round" :disabled="!song.idMaxMelody" @click="getLinkMaxTabs">
                  <img alt="link" class="icon-textlink" src="../../../assets/svg/icon_link.svg" />
                </button>
                <input v-model="song.idMaxMelody" class="input-link-field" />
                <input v-model="song.versionMaxMelody" class="input-field-version" />
                <button
                  class="btn-round"
                  :disabled="notChanged('idMaxMelody')"
                  @click="undoField('idMaxMelody')"
                >
                  <img alt="undo" class="icon-undo" src="../../../assets/svg/icon_undo.svg" />
                </button>
                <button
                  class="btn-round"
                  :disabled="!song.idMaxMelody"
                  @click="copyToClipboard(song.idMaxMelody, 'idMaxMelody')"
                >
                  <img alt="copy" class="icon-copy" src="../../../assets/svg/icon_copy.svg" />
                </button>
                <button class="btn-round" @click="pasteFromClipboard('idMaxMelody')">
                  <img alt="paste" class="icon-paste" src="../../../assets/svg/icon_paste.svg" />
                </button>
              </div>
            </div>
          </div>

          <div class="links-table">
            <div class="links-table-column-1">Статус</div>
            <div class="links-table-column-2">
              <button
                class="group-button"
                :class="statusButtonClass(0)"
                type="button"
                value="0"
                @click="setStatus(0)"
              >
                ❎
              </button>
              <button
                class="group-button"
                :class="statusButtonClass(1)"
                type="button"
                value="1"
                @click="setStatus(1)"
              >
                Тxt🛠
              </button>
              <button
                class="group-button"
                :class="statusButtonClass(2)"
                type="button"
                value="2"
                @click="setStatus(2)"
              >
                Txt✅
              </button>
              <button
                class="group-button"
                :class="statusButtonClass(3)"
                type="button"
                value="3"
                title="Проект создан — доступно в онлайн-плеере"
                @click="setStatus(3)"
              >
                Prj🛠
              </button>
              <button
                class="group-button group-button-legacy"
                :class="statusButtonClass(4)"
                type="button"
                value="4"
                title="Легаси: этап старого MLT-рендера, не влияет на онлайн-плеер"
                @click="setStatus(4)"
              >
                Prj✅
              </button>
              <button
                class="group-button group-button-legacy"
                :class="statusButtonClass(6)"
                type="button"
                value="6"
                title="Легаси: этап старого MLT-рендера, не влияет на онлайн-плеер"
                @click="setStatus(6)"
              >
                ✅
              </button>
            </div>
          </div>
        </div>
        <!-- Второй столбец тела -->
        <div class="column-2">
          <div class="picture-author">
            <img class="image-author" alt="Author image" :src="imageAuthorBase64" />
          </div>
          <div class="picture-album">
            <img class="image-album" alt="Album image" :src="imageAlbumBase64" />
          </div>
          <div class="label-and-input">
            <div class="label-medium">Эксклюзивно на sponsr:</div>
            <button
              class="group-button-round-wide"
              :class="exclusiveButtonClass(true)"
              type="button"
              value="true"
              @click="setExclusive(true)"
            >
              ДА
            </button>
            <button
              class="group-button-round-wide"
              :class="exclusiveButtonClass(false)"
              type="button"
              value="false"
              @click="setExclusive(false)"
            >
              НЕТ
            </button>
          </div>
          <div class="label-and-input">
            <div class="label-medium">Бесплатно на sponsr:</div>
            <button
              class="group-button-round-wide"
              :class="freeButtonClass(true)"
              type="button"
              value="true"
              @click="setFree(true)"
            >
              ДА
            </button>
            <button
              class="group-button-round-wide"
              :class="freeButtonClass(false)"
              type="button"
              value="false"
              @click="setFree(false)"
            >
              НЕТ
            </button>
          </div>
          <div class="label-and-input">
            <div class="label-medium">Разрешить подписку на эту песню:</div>
            <button
              class="group-button-round-wide"
              :class="songSubscriptionButtonClass(true)"
              type="button"
              value="true"
              @click="setSongSubscriptionAllowed(true)"
            >
              ДА
            </button>
            <button
              class="group-button-round-wide"
              :class="songSubscriptionButtonClass(false)"
              type="button"
              value="false"
              @click="setSongSubscriptionAllowed(false)"
            >
              НЕТ
            </button>
          </div>
          <div class="create-picture-buttons-group">
            <button class="group-button" title="Открыть на сайте" @click="openMainLink">
              Открыть на сайте sm-karaoke.ru
            </button>
            <button
              class="group-button"
              title="Обновить на сервере"
              :disabled="!allowUpdateRemote"
              @click="updateRemote"
            >
              Обновить на сервере
            </button>
            <button
              class="group-button"
              :class="toSyncButtonClass(toSync)"
              title="Добавить в SYNC-таблицу на сервере"
              :disabled="!allowAddSync"
              @click="toSyncRemote"
            >
              Добавить в SYNC-таблицу на сервере
            </button>
            <button
              class="group-button"
              title="Скопировать поля из другой песни"
              @click="copyFieldsFromAnother"
            >
              Скопировать поля из другой песни
            </button>
            <button
              class="group-button"
              title="Показать песни из той же группы (id/root_id)"
              @click="showFamilySongs"
            >
              Похожие версии песни
            </button>
            <button
              class="group-button"
              :disabled="isFindingAudioParent"
              title="Акустически сравнить со всеми кандидатами (семья + поиск по названию) и сохранить наиболее похожую как аудио-родителя"
              @click="findAudioParent"
            >
              {{ isFindingAudioParent ? 'Поиск…' : 'Найти похожую по аудио' }}
            </button>
            <button
              class="group-button"
              :disabled="songHealthReports.length === 0"
              title="Health Report"
              @click="showHealthReportTable"
            >
              Health Report ({{ songHealthReports.length }})
            </button>
            <BDropdown text="Создать ..." class="d-grid" menu-class="w-100">
              <BDropdownGroup header="Картинка">
                <!--                <BDropdownItem @click="createPictureBoostyTeaser" title="Создать картинку Boosty Teaser">Boosty Teaser</BDropdownItem>-->
                <!--                <BDropdownItem @click="createPictureBoostyFiles" title="Создать картинку Boosty Files">Boosty Files</BDropdownItem>-->
                <BDropdownItem
                  title="Создать картинку Sponsr Teaser"
                  @click="createPictureSponsrTeaser"
                  >Sponsr Files</BDropdownItem
                >
                <BDropdownItem title="Создать картинку KARAOKE" @click="createPictureKaraoke"
                  >KARAOKE</BDropdownItem
                >
                <BDropdownItem title="Создать картинку LYRICS" @click="createPictureLyrics"
                  >LYRICS</BDropdownItem
                >
                <BDropdownItem title="Создать картинку CHORDS" @click="createPictureChords"
                  >CHORDS</BDropdownItem
                >
                <BDropdownItem title="Создать картинку TABS" @click="createPictureTabs"
                  >TABS</BDropdownItem
                >
              </BDropdownGroup>
              <BDropdownDivider />
              <BDropdownGroup header="Текст">
                <BDropdownItem title="Создать текст KARAOKE" @click="createDescriptionFileKaraoke"
                  >KARAOKE</BDropdownItem
                >
                <BDropdownItem title="Создать текст LYRICS" @click="createDescriptionFileLyrics"
                  >LYRICS</BDropdownItem
                >
                <BDropdownItem title="Создать текст CHORDS" @click="createDescriptionFileChords"
                  >CHORDS</BDropdownItem
                >
                <BDropdownItem title="Создать текст TABS" @click="createDescriptionFileTabs"
                  >TABS</BDropdownItem
                >
              </BDropdownGroup>
              <BDropdownGroup header="Разное">
                <BDropdownItem title="Найти BPM и TEMP из файла" @click="createKeyBpmFinderProcess"
                  >Найти BPM и TEMP из файла</BDropdownItem
                >
                <BDropdownItem
                  title="Рендер LYRICS из онлайн-плеера"
                  @click="createRenderMp4Version('LYRICS')"
                  >Рендер LYRICS</BDropdownItem
                >
                <BDropdownItem
                  title="Рендер KARAOKE из онлайн-плеера"
                  @click="createRenderMp4Version('KARAOKE')"
                  >Рендер KARAOKE</BDropdownItem
                >
                <BDropdownItem
                  title="Рендер DEMO из онлайн-плеера"
                  @click="createRenderMp4Version('DEMO')"
                  >Рендер DEMO</BDropdownItem
                >
              </BDropdownGroup>
            </BDropdown>
            <!--            <button class="group-button" @click="createPictureBoostyTeaser" title="Создать картинку Boosty Teaser">Создать картинку Boosty Teaser</button>-->
            <!--            <button class="group-button" @click="createPictureBoostyFiles" title="Создать картинку Boosty Files">Создать картинку Boosty Files</button>-->
            <!--            <button class="group-button" @click="createPictureSponsrTeaser" title="Создать картинку Sponsr Teaser">Создать картинку Sponsr Files</button>-->
            <!--            <button class="group-button" @click="createPictureVK" title="Создать картинку VK">Создать картинку VK</button>-->
            <!--            <button class="group-button" @click="createPictureVKlink" title="Создать картинку VKlink">Создать картинку VKlink</button>-->
            <!--            <button class="group-button" @click="createPictureKaraoke" title="Создать картинку KARAOKE">Создать картинку KARAOKE</button>-->
            <!--            <button class="group-button" @click="createPictureLyrics" title="Создать картинку LYRICS">Создать картинку LYRICS</button>-->
            <!--            <button class="group-button" @click="createPictureChords" title="Создать картинку CHORDS">Создать картинку CHORDS</button>-->
            <!--            <button class="group-button" @click="createPictureTabs" title="Создать картинку TABS">Создать картинку TABS</button>-->
            <!--            <button class="group-button" @click="createDescriptionFileKaraoke" title="Создать текст KARAOKE">Создать текст KARAOKE</button>-->
            <!--            <button class="group-button" @click="createDescriptionFileLyrics" title="Создать текст LYRICS">Создать текст LYRICS</button>-->
            <!--            <button class="group-button" @click="createDescriptionFileChords" title="Создать текст CHORDS">Создать текст CHORDS</button>-->
            <!--            <button class="group-button" @click="createDescriptionFileTabs" title="Создать текст TABS">Создать текст TABS</button>-->
            <button
              class="group-button"
              title="PLAY KARAOKE"
              :style="{ backgroundColor: song.processColorMeltKaraoke }"
              @click="playKaraoke"
            >
              PLAY KARAOKE
            </button>
            <button
              class="group-button"
              title="PLAY LYRICS"
              :style="{ backgroundColor: song.processColorMeltLyrics }"
              @click="playLyrics"
            >
              PLAY LYRICS
            </button>
            <button
              class="group-button"
              title="PLAY CHORDS"
              :style="{ backgroundColor: song.processColorMeltChords }"
              @click="playChords"
            >
              PLAY CHORDS
            </button>
            <button
              class="group-button"
              title="PLAY TABS"
              :style="{ backgroundColor: song.processColorMeltMelody }"
              @click="playTabs"
            >
              PLAY TABS
            </button>
            <button class="group-button" title="PLAY DEMO" @click="playDemo">PLAY DEMO</button>
            <div class="group-button">
              <b-form-rating id="rate-inline-form" v-model="song.rate" inline />
            </div>
            <div class="navigation-buttons">
              <button
                class="group-button-left-right"
                title="⬅"
                :disabled="song.id === getNeighbourSongId('Left')"
                @click="goToLeftSong"
              >
                ⬅
              </button>
              <div class="navigation-buttons-column">
                <button
                  class="group-button-up-down"
                  title="⬆"
                  :disabled="song.id === getNeighbourSongId('Previous')"
                  @click="goToPreviousSong"
                >
                  ⬆
                </button>
                <button
                  class="group-button-up-down"
                  title="⬇"
                  :disabled="song.id === getNeighbourSongId('Next')"
                  @click="goToNextSong"
                >
                  ⬇
                </button>
              </div>
              <button
                class="group-button-left-right"
                title="➡"
                :disabled="song.id === getNeighbourSongId('Right')"
                @click="goToRightSong"
              >
                ➡
              </button>
            </div>
          </div>
        </div>
        <!-- Третий столбец тела -->
        <div class="column-3">
          <div v-if="textFormatted" class="formatted-text-area">
            <div class="formatted-text" v-html="textFormatted" />
          </div>
        </div>
        <div class="column-4">
          <div v-if="notesFormatted" class="formatted-notes-area">
            <div class="formatted-notes" v-html="notesFormatted" />
          </div>
        </div>
        <div class="column-5">
          <div v-if="chordsFormatted" class="formatted-chords-area">
            <div class="formatted-chords" v-html="chordsFormatted" />
          </div>
        </div>
      </div>
      <!--      <health-report-table :id="song.id" />-->
      <!-- Подвал -->
      <div class="footer">
        <button
          class="btn-round-save-double"
          :disabled="notChanged()"
          title="Сохранить"
          @click="save"
        >
          <img alt="saveSong" class="icon-save-double" src="../../../assets/svg/icon_save.svg" />
        </button>
        <button
          class="btn-round-double"
          :disabled="disabledSearchTextForSong"
          title="Найти текст песни"
          @click="searchTextForSong"
        >
          <img
            alt="search texts for song"
            class="icon-40"
            src="../../../assets/svg/icon_search_text.svg"
          />
        </button>
        <button
          v-if="song.idStatus === 0"
          class="btn-round-double"
          title="Найти оригинал песни в базе"
          @click="findOriginalForSong"
        >
          <img
            alt="find original song"
            class="icon-40"
            src="../../../assets/svg/icon_find_original.svg"
          />
        </button>
        <button class="btn-round-double" title="Редактировать субтитры" @click="showSubsEdit">
          <img alt="edit subs" class="icon-edit-double" src="../../../assets/svg/icon_edit.svg" />
        </button>
        <button
          class="btn-round-double"
          title="Онлайн-редактор караоке-разметки"
          @click="showKaraokeEditor"
        >
          <img alt="karaoke editor" class="icon-40" src="../../../assets/svg/icon_microphone.svg" />
        </button>
        <button class="btn-round-double" title="Создать караоке" @click="createKaraoke">
          <img alt="create karaoke" class="icon-40" src="../../../assets/svg/icon_song.svg" />
        </button>
        <button class="btn-round-double" title="Создать DEMUCS2" @click="createDemucs2">
          <img alt="create demucs2" class="icon-40" src="../../../assets/svg/icon_demucs2.svg" />
        </button>
        <button class="btn-round-double" title="Создать DEMUCS5" @click="createDemucs5">
          <img alt="create demucs5" class="icon-40" src="../../../assets/svg/icon_demucs5.svg" />
        </button>
        <!-- <button class="btn-round-double" @click="createMP3Karaoke" title="Создать MP3 Karaoke"><img alt="create mp3 karaoke" class="icon-40" src="../../../assets/svg/icon_mp3karaoke.svg"></button> -->
        <!-- <button class="btn-round-double" @click="createMP3Lyrics" title="Создать MP3 Lyrics"><img alt="create mp3 lyrics" class="icon-40" src="../../../assets/svg/icon_mp3lyrics.svg"></button> -->
        <button class="btn-round-double" title="Создать SYMLINKS" @click="createSymlinks">
          <img alt="create symlink" class="icon-40" src="../../../assets/svg/icon_symlink.svg" />
        </button>
        <button class="btn-round-double" title="Создать SHEETSAGE" @click="createSheetsage">
          <img alt="create sheetsage" class="icon-40" src="../../../assets/svg/icon_chords.svg" />
        </button>
        <button
          class="btn-round-double"
          title="установить дату/время публикации для песен автора, начиная с текущей"
          @click="setDateTimeAuthor"
        >
          <img alt="calendar" class="icon-40" src="../../../assets/svg/icon_calendar_later.svg" />
        </button>
        <button class="btn-round-double" title="Удалить песню" @click="deleteSong">
          <img alt="delete" class="icon-40" src="../../../assets/svg/icon_delete.svg" />
        </button>
      </div>
    </div>
    <div v-else>Не выбрана песня</div>
  </div>
</template>

<script>
import SubsEdit from './SubsEdit.vue'
import CustomConfirm from '../../Common/CustomConfirm.vue'
import HealthReportTable from '../../Common/HealthReport/HealthReportTable.vue'
import FamilySongsModal from './FamilySongsModal.vue'
import ReviewModal from '../../SongEditor/ReviewModal.vue'
import SongKaraokeEditorModal from '../../SongEditor/SongKaraokeEditorModal.vue'
import {
  BFormRating,
  BDropdown,
  BDropdownItem,
  BDropdownDivider,
  BDropdownGroup,
} from 'bootstrap-vue-next'
import { useToast } from 'bootstrap-vue-next'
import { h } from 'vue'

// import { ToastPlugin } from 'bootstrap-vue'

const ASSIGN_STATUS_LABELS = {
  assigned: 'Назначено',
  in_progress: 'В работе',
  submitted: 'На проверке',
  approved: 'Одобрено',
  rejected: 'Отклонено',
}

// Тип песни: dbValue — то, что хранится в БД (tbl_settings.song_type) и в SettingsDTO.songType,
// и попадает в ApiController.songs2Update как параметр songType. Значение по умолчанию — 'song'
// (см. SongType.SONG в karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongType.kt).
const SONG_TYPE_OPTIONS = [
  { value: 'song', label: 'Песня (вокал + музыка)' },
  { value: 'instrumental', label: 'Инструментал (только музыка)' },
  { value: 'poetry', label: 'Стихи (только вокал)' },
]

/**
 * Главная форма редактирования песни (Karaoke-редактор).
 *
 * Это самый большой и сложный Vue-компонент в проекте (~3000 строк).
 * Содержит 8 логических секций:
 * 1. **Header**: id, автор, альбом, имя файла, тип песни, статус.
 * 2. **Аудио**: source/origin/mix/vocals/accompaniment, BPM, Key.
 * 3. **Текст**: `subsText` (исходник), `translatedText` (перевод),
 *    `chordsText` (аккорды), автоматический поиск через `SearchText`.
 * 4. **Markers**: маркеры слогов/нот/аккордов для MLT (см. `mlt-generator.md`).
 * 5. **Render params**: ~150 параметров рендера (шрифты, цвета, отступы,
 *    тайминги, горизонт, watermark, fade-in/out).
 * 6. **MLT preview**: превью XML-структуры.
 * 7. **Публикация**: ссылки на соцсети (TG/VK/Dzen/Boosty/Sponsr).
 * 8. **Repair flags**: флаги ремонта (`isAudioAnalizeNeed`, `isMelodyNeed`,
 *    `isChordsNeed`, `isVocalNeed`, `isAccompanimentNeed`).
 *
 * Автосохранение: `watch(diff)` с debounce 1 сек → `Settings.saveToDb()`.
 *
 * Использует `SubsEdit` для inline-редактирования текста/аккордов,
 * `SearchText` для LLM-поиска текстов и аккордов.
 *
 * @see docs/features/mlt-generator.md
 * @see docs/features/llm-lyrics-search.md
 */
export default {
  name: 'SongEdit',
  components: {
    CustomConfirm,
    HealthReportTable,
    FamilySongsModal,
    ReviewModal,
    SongKaraokeEditorModal,
    SubsEdit,
    BFormRating,
    BDropdown,
    BDropdownItem,
    BDropdownDivider,
    BDropdownGroup,
  },
  props: {
    parentRoute: {
      type: String,
      required: true,
    },
    songsDigests: {
      type: Array,
      required: false,
      default: () => [],
    },
    publishDigest: {
      type: Array,
      required: false,
      default: () => [[]],
    },
  },
  data() {
    return {
      isSubsEditVisible: false,
      isCustomConfirmVisible: false,
      isHealthReportTableVisible: false,
      isFamilySongsVisible: false,
      isFindingAudioParent: false,
      isAssignReviewVisible: false,
      isKaraokeEditorVisible: false,
      karaokeEditorTarget: 'local',
      voices: [],
      customConfirmParams: undefined,
      imageAuthorBase64: '',
      imageAlbumBase64: '',
      textFormatted: '',
      notesFormatted: '',
      chordsFormatted: '',
      autoSave: true,
      autoSaveDelayMs: 1000,
      saveTimer: undefined,
      isSaving: false,
      allowUpdateRemote: false,
      allowUpdateLocal: false,
      allowAddSync: false,
      showChordsIfEmpty: false,
      showMelodyIfEmpty: false,
      hours: ['11:00', '12:00', '13:00', '14:00', '15:00', '16:00'],
      createToast: () => {},
    }
  },
  computed: {
    assignmentInfo() {
      return this.song ? this.$store.getters.getAssignmentStatusBySongId[this.song.id] : undefined
    },
    editorSiteUsers() {
      return this.$store.getters.getEditorSiteUsers || []
    },
    songTypeOptions() {
      return SONG_TYPE_OPTIONS
    },
    disabledSearchTextForSong() {
      return this.song.haveSourceText
    },
    showChordsDzen() {
      return this.showChordsIfEmpty || this.song.idDzenChords
    },
    showChordsVk() {
      return this.showChordsIfEmpty || this.song.idVkChords
    },
    showChordsTelegram() {
      return this.showChordsIfEmpty || this.song.idTelegramChords
    },
    showChordsMax() {
      return this.showChordsIfEmpty || this.song.idMaxChords
    },
    showMelodyDzen() {
      return this.showMelodyIfEmpty || this.song.idDzenMelody
    },
    showMelodyVk() {
      return this.showMelodyIfEmpty || this.song.idVkMelody
    },
    showMelodyTelegram() {
      return this.showMelodyIfEmpty || this.song.idTelegramMelody
    },
    showMelodyMax() {
      return this.showMelodyIfEmpty || this.song.idMaxMelody
    },
    styleRoot() {
      return {
        padding: 0,
        margin: 0,
        width: 'auto',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        fontFamily: 'Avenir, Helvetica, Arial, sans-serif',
        // backgroundColor: 'lightyellow'
      }
    },
    song() {
      return this.$store.getters.getCurrentSong
    },
    songHealthReports() {
      return this.$store.getters.getCurrentSongHealthReports
    },
    previousSongId() {
      return this.$store.getters.getPreviousSongId
    },
    nextSongId() {
      return this.$store.getters.getNextSongId
    },
    leftSongId() {
      return this.$store.getters.getLeftSongId
    },
    rightSongId() {
      return this.$store.getters.getRightSongId
    },
    snapshot() {
      return this.$store.getters.getSnapshotSong
    },
    diff() {
      return this.$store.getters.getSongDiff
    },
    toSync() {
      return this.$store.getters.getToSync
    },
    freeTimeSlots() {
      return this.$store.getters.getFreeTimeSlots
    },

    mainLink() {
      return this.prefixMainLink + this.song.id
    },
    linkBoosty() {
      return this.prefixLinkBoosty + this.song.idBoosty
    },
    linkSponsrEdit() {
      return this.prefixLinkSponsrEdit + this.song.idSponsr
    },
    linkSponsrPlay() {
      return this.prefixLinkSponsrPlay + this.song.idSponsr
    },
    linkBoostyFiles() {
      return this.prefixLinkBoosty + this.song.idBoostyFiles
    },
    linkBoostyNew() {
      return this.prefixLinkBoostyNew
    },
    linkSponsrNew() {
      return this.prefixLinkSponsrNew
    },
    linkVkGroup() {
      return this.prefixLinkVkGroup + this.song.idVk
    },
    linkDzenKaraokePlay() {
      return this.prefixLinkDzenPlay + this.song.idDzenKaraoke
    },
    linkDzenKaraokeEdit() {
      return this.prefixLinkDzenEdit + this.song.idDzenKaraoke
    },
    linkDzenLyricsPlay() {
      return this.prefixLinkDzenPlay + this.song.idDzenLyrics
    },
    linkDzenLyricsEdit() {
      return this.prefixLinkDzenEdit + this.song.idDzenLyrics
    },
    linkDzenChordsPlay() {
      return this.prefixLinkDzenPlay + this.song.idDzenChords
    },
    linkDzenChordsEdit() {
      return this.prefixLinkDzenEdit + this.song.idDzenChords
    },
    linkDzenTabsPlay() {
      return this.prefixLinkDzenPlay + this.song.idDzenMelody
    },
    linkDzenTabsEdit() {
      return this.prefixLinkDzenEdit + this.song.idDzenMelody
    },
    linkVkKaraoke() {
      return this.prefixLinkVk + this.song.idVkKaraoke
    },
    linkVkLyrics() {
      return this.prefixLinkVk + this.song.idVkLyrics
    },
    linkVkChords() {
      return this.prefixLinkVk + this.song.idVkChords
    },
    linkVkTabs() {
      return this.prefixLinkVk + this.song.idVkMelody
    },
    linkTelegramKaraoke() {
      return this.prefixLinkTelegram + this.song.idTelegramKaraoke
    },
    linkTelegramLyrics() {
      return this.prefixLinkTelegram + this.song.idTelegramLyrics
    },
    linkTelegramChords() {
      return this.prefixLinkTelegram + this.song.idTelegramChords
    },
    linkTelegramTabs() {
      return this.prefixLinkTelegram + this.song.idTelegramMelody
    },
    linkMaxKaraoke() {
      return this.prefixLinkMax + this.song.idMaxKaraoke
    },
    linkMaxLyrics() {
      return this.prefixLinkMax + this.song.idMaxLyrics
    },
    linkMaxChords() {
      return this.prefixLinkMax + this.song.idMaxChords
    },
    linkMaxTabs() {
      return this.prefixLinkMax + this.song.idMaxMelody
    },
    linkDzenDemoPlay() {
      return this.prefixLinkDzenPlay + this.song.idDzenDemo
    },
    linkDzenDemoEdit() {
      return this.prefixLinkDzenEdit + this.song.idDzenDemo
    },
    linkVkDemo() {
      return this.prefixLinkVk + this.song.idVkDemo
    },
    linkTelegramDemo() {
      return this.prefixLinkTelegram + this.song.idTelegramDemo
    },
    linkMaxDemo() {
      return this.prefixLinkMax + this.song.idMaxDemo
    },
    linkPlKaraokePlay() {
      return this.prefixLinkPlPlay + this.song.idPlKaraoke
    },
    linkPlKaraokeEdit() {
      return this.prefixLinkPlEdit + this.song.idPlKaraoke + this.suffixLinkPlEdit
    },
    linkPlLyricsPlay() {
      return this.prefixLinkPlPlay + this.song.idPlLyrics
    },
    linkPlLyricsEdit() {
      return this.prefixLinkPlEdit + this.song.idPlLyrics + this.suffixLinkPlEdit
    },
    linkPlChordsPlay() {
      return this.prefixLinkPlPlay + this.song.idPlChords
    },
    linkPlChordsEdit() {
      return this.prefixLinkPlEdit + this.song.idPlChords + this.suffixLinkPlEdit
    },
    linkPlTabsPlay() {
      return this.prefixLinkPlPlay + this.song.idPlMelody
    },
    linkPlTabsEdit() {
      return this.prefixLinkPlEdit + this.song.idPlMelody + this.suffixLinkPlEdit
    },

    prefixMainLink: () => {
      return 'https://sm-karaoke.ru/song?id='
    },
    prefixLinkBoosty: () => {
      return 'https://boosty.to/svoemesto/posts/'
    },
    prefixLinkSponsrEdit: () => {
      return 'https://sponsr.ru/smkaraoke/manage/post/'
    },
    prefixLinkSponsrPlay: () => {
      return 'https://sponsr.ru/smkaraoke/'
    },
    prefixLinkBoostyNew: () => {
      return 'https://boosty.to/svoemesto/new-post'
    },
    prefixLinkSponsrNew: () => {
      return 'https://sponsr.ru/smkaraoke/manage/post/new/'
    },
    prefixLinkVkGroup: () => {
      return 'https://vk.com/wall-'
    },
    prefixLinkDzenPlay: () => {
      return 'https://dzen.ru/video/watch/'
    },
    prefixLinkDzenEdit: () => {
      return 'https://dzen.ru/profile/editor/svoemesto/publications?videoEditorPublicationId='
    },
    prefixLinkVk: () => {
      return 'https://vkvideo.ru/video'
    },
    prefixLinkVk2: () => {
      return 'https://vk.com/video'
    },
    prefixLinkTelegram: () => {
      return 'https://t.me/svoemestokaraoke/'
    },
    prefixLinkMax: () => {
      return 'https://max.ru/c/-70935843913828/'
    },

    prefixLinkPlPlay: () => {
      return 'https://plvideo.ru/watch?v='
    },
    prefixLinkPlEdit: () => {
      return 'https://studio.plvideo.ru/channel/bbj0HWC8H7ii/video/'
    },
    suffixLinkPlEdit: () => {
      return '/edit'
    },
  },
  watch: {
    diff: {
      deep: true, // КРИТИЧНО для отслеживания изменений внутри массива
      handler(_newVal) {
        if (this.diff.length !== 0 && this.autoSave) {
          clearTimeout(this.saveTimer)
          this.saveTimer = setTimeout(this.save, this.autoSaveDelayMs)
        }
      },
    },
    song: {
      handler() {
        this.$store
          .dispatch('getAuthorPictureBase64Promise')
          .then((image) => (this.imageAuthorBase64 = image))
        this.$store
          .dispatch('getAlbumPictureBase64Promise')
          .then((image) => (this.imageAlbumBase64 = image))
        this.$store
          .dispatch('getTextFormattedPromise')
          .then((textFormatted) => (this.textFormatted = textFormatted))
        this.$store
          .dispatch('getNotesFormattedPromise')
          .then((notesFormatted) => (this.notesFormatted = notesFormatted))
        this.$store
          .dispatch('getChordsFormattedPromise')
          .then((chordsFormatted) => (this.chordsFormatted = chordsFormatted))
        this.reloadAssignmentStatus()
      },
    },
    // На случай когда сервер ещё не отдаёт songType (старая БД/билд) — подставляем дефолт 'song'.
    'song.songType': {
      handler(newVal) {
        if (!newVal || !SONG_TYPE_OPTIONS.some((o) => o.value === newVal)) {
          this.$store.dispatch('setCurrentSongField', { name: 'songType', value: 'song' })
        }
      },
    },
    'song.idBoosty.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idBoosty
          if (value && value.startsWith(this.prefixLinkBoosty)) {
            const newVal = value.replace(this.prefixLinkBoosty, '').replace('?share=post_link', '')
            this.$store.dispatch('setCurrentSongField', { name: 'idBoosty', value: newVal })
          }
          if (value !== '' && this.song.versionBoosty === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionBoosty',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionBoosty !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionBoosty', value: 0 })
          }
        }
      },
    },
    'song.idSponsr.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idSponsr
          if (value && value.startsWith(this.prefixLinkSponsrPlay)) {
            const newValue = value.replace(this.prefixLinkSponsrPlay, '').split('/')[0]
            this.$store.dispatch('setCurrentSongField', { name: 'idSponsr', value: newValue })
          }
          if (value !== '' && this.song.versionSponsr === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionSponsr',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionSponsr !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionSponsr', value: 0 })
          }
        }
      },
    },
    'song.idBoostyFiles.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idBoostyFiles
          if (value && value.startsWith(this.prefixLinkBoosty)) {
            const newValue = value
              .replace(this.prefixLinkBoosty, '')
              .replace('?share=post_link', '')
            this.$store.dispatch('setCurrentSongField', { name: 'idBoostyFiles', value: newValue })
          }
          if (value !== '' && this.song.versionBoostyFiles === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionBoostyFiles',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionBoostyFiles !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionBoostyFiles', value: 0 })
          }
        }
      },
    },
    'song.idVk.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idVk
          if (value && value.startsWith(this.prefixLinkVkGroup)) {
            const newValue = value.replace(this.prefixLinkVkGroup, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idVk', value: newValue })
          }
        }
      },
    },
    'song.idDzenKaraoke.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idDzenKaraoke
          if (value && value.startsWith(this.prefixLinkDzenPlay)) {
            const newValue = value.replace(this.prefixLinkDzenPlay, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idDzenKaraoke', value: newValue })
          }
          if (value !== '' && this.song.versionDzenKaraoke === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionDzenKaraoke',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionDzenKaraoke !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionDzenKaraoke', value: 0 })
          }
        }
      },
    },
    'song.idDzenLyrics.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idDzenLyrics
          if (value && value.startsWith(this.prefixLinkDzenPlay)) {
            const newValue = value.replace(this.prefixLinkDzenPlay, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idDzenLyrics', value: newValue })
          }
          if (value !== '' && this.song.versionDzenLyrics === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionDzenLyrics',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionDzenLyrics !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionDzenLyrics', value: 0 })
          }
        }
      },
    },
    'song.idDzenChords.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idDzenChords
          if (value && value.startsWith(this.prefixLinkDzenPlay)) {
            const newValue = value.replace(this.prefixLinkDzenPlay, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idDzenChords', value: newValue })
          }
          if (value !== '' && this.song.versionDzenChords === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionDzenChords',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionDzenChords !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionDzenChords', value: 0 })
          }
        }
      },
    },
    'song.idDzenMelody.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idDzenMelody
          if (value && value.startsWith(this.prefixLinkDzenPlay)) {
            const newValue = value.replace(this.prefixLinkDzenPlay, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idDzenMelody', value: newValue })
          }
          if (value !== '' && this.song.versionDzenMelody === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionDzenMelody',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionDzenMelody !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionDzenMelody', value: 0 })
          }
        }
      },
    },
    'song.idDzenDemo.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idDzenDemo
          if (value && value.startsWith(this.prefixLinkDzenPlay)) {
            const newValue = value.replace(this.prefixLinkDzenPlay, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idDzenDemo', value: newValue })
          }
          if (value !== '' && this.song.versionDzenDemo === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionDzenDemo',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionDzenDemo !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionDzenDemo', value: 0 })
          }
        }
      },
    },
    'song.idPlKaraoke.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idPlKaraoke
          if (value && value.startsWith(this.prefixLinkPlPlay)) {
            const newValue = value.replace(this.prefixLinkPlPlay, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idPlKaraoke', value: newValue })
          }
          if (value !== '' && this.song.versionPlKaraoke === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionPlKaraoke',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionPlKaraoke !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionPlKaraoke', value: 0 })
          }
        }
      },
    },
    'song.idPlLyrics.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idPlLyrics
          if (value && value.startsWith(this.prefixLinkPlPlay)) {
            const newValue = value.replace(this.prefixLinkPlPlay, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idPlLyrics', value: newValue })
          }
          if (value !== '' && this.song.versionPlLyrics === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionPlLyrics',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionPlLyrics !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionPlLyrics', value: 0 })
          }
        }
      },
    },
    'song.idPlChords.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idPlChords
          if (value && value.startsWith(this.prefixLinkPlPlay)) {
            const newValue = value.replace(this.prefixLinkPlPlay, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idPlChords', value: newValue })
          }
          if (value !== '' && this.song.versionPlChords === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionPlChords',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionPlChords !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionPlChords', value: 0 })
          }
        }
      },
    },
    'song.idPlMelody.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idPlMelody
          if (value && value.startsWith(this.prefixLinkPlPlay)) {
            const newValue = value.replace(this.prefixLinkPlPlay, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idPlMelody', value: newValue })
          }
          if (value !== '' && this.song.versionPlMelody === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionPlMelody',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionPlMelody !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionPlMelody', value: 0 })
          }
        }
      },
    },
    'song.idVkKaraoke.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idVkKaraoke
          if (value && value.startsWith(this.prefixLinkVk)) {
            const newValue = value.replace(this.prefixLinkVk, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idVkKaraoke', value: newValue })
          }
          if (value && value.startsWith(this.prefixLinkVk2)) {
            const newValue = value.replace(this.prefixLinkVk2, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idVkKaraoke', value: newValue })
          }
          if (value !== '' && this.song.versionVkKaraoke === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionVkKaraoke',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionVkKaraoke !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionVkKaraoke', value: 0 })
          }
        }
      },
    },
    'song.idVkLyrics.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idVkLyrics
          if (value && value.startsWith(this.prefixLinkVk)) {
            const newValue = value.replace(this.prefixLinkVk, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idVkLyrics', value: newValue })
          }
          if (value && value.startsWith(this.prefixLinkVk2)) {
            const newValue = value.replace(this.prefixLinkVk2, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idVkLyrics', value: newValue })
          }
          if (value !== '' && this.song.versionVkLyrics === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionVkLyrics',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionVkLyrics !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionVkLyrics', value: 0 })
          }
        }
      },
    },
    'song.idVkChords.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idVkChords
          if (value && value.startsWith(this.prefixLinkVk)) {
            const newValue = value.replace(this.prefixLinkVk, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idVkChords', value: newValue })
          }
          if (value && value.startsWith(this.prefixLinkVk2)) {
            const newValue = value.replace(this.prefixLinkVk2, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idVkChords', value: newValue })
          }
          if (value !== '' && this.song.versionVkChords === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionVkChords',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionVkChords !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionVkChords', value: 0 })
          }
        }
      },
    },
    'song.idVkMelody.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idVkMelody
          if (value && value.startsWith(this.prefixLinkVk)) {
            const newValue = value.replace(this.prefixLinkVk, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idVkMelody', value: newValue })
          }
          if (value && value.startsWith(this.prefixLinkVk2)) {
            const newValue = value.replace(this.prefixLinkVk2, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idVkMelody', value: newValue })
          }
          if (value !== '' && this.song.versionVkMelody === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionVkMelody',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionVkMelody !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionVkMelody', value: 0 })
          }
        }
      },
    },
    'song.idVkDemo.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idVkDemo
          if (value && value.startsWith(this.prefixLinkVk)) {
            const newValue = value.replace(this.prefixLinkVk, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idVkDemo', value: newValue })
          }
          if (value && value.startsWith(this.prefixLinkVk2)) {
            const newValue = value.replace(this.prefixLinkVk2, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idVkDemo', value: newValue })
          }
          if (value !== '' && this.song.versionVkDemo === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionVkDemo',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionVkDemo !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionVkDemo', value: 0 })
          }
        }
      },
    },
    'song.idTelegramKaraoke.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idTelegramKaraoke
          if (value && value.startsWith(this.prefixLinkTelegram)) {
            const newValue = value.replace(this.prefixLinkTelegram, '')
            this.$store.dispatch('setCurrentSongField', {
              name: 'idTelegramKaraoke',
              value: newValue,
            })
          }
          if (value !== '' && value !== '-' && this.song.versionTelegramKaraoke === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionTelegramKaraoke',
              value: this.song.resultVersion,
            })
          } else if ((value === '' || value === '-') && this.song.versionTelegramKaraoke !== 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionTelegramKaraoke',
              value: 0,
            })
          }
        }
      },
    },
    'song.idTelegramLyrics.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idTelegramLyrics
          if (value && value.startsWith(this.prefixLinkTelegram)) {
            const newValue = value.replace(this.prefixLinkTelegram, '')
            this.$store.dispatch('setCurrentSongField', {
              name: 'idTelegramLyrics',
              value: newValue,
            })
          }
          if (value !== '' && this.song.versionTelegramLyrics === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionTelegramLyrics',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionTelegramLyrics !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionTelegramLyrics', value: 0 })
          }
        }
      },
    },
    'song.idTelegramChords.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idTelegramChords
          if (value && value.startsWith(this.prefixLinkTelegram)) {
            const newValue = value.replace(this.prefixLinkTelegram, '')
            this.$store.dispatch('setCurrentSongField', {
              name: 'idTelegramChords',
              value: newValue,
            })
          }
          if (value !== '' && this.song.versionTelegramChords === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionTelegramChords',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionTelegramChords !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionTelegramChords', value: 0 })
          }
        }
      },
    },
    'song.idTelegramMelody.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idTelegramMelody
          if (value && value.startsWith(this.prefixLinkTelegram)) {
            const newValue = value.replace(this.prefixLinkTelegram, '')
            this.$store.dispatch('setCurrentSongField', {
              name: 'idTelegramMelody',
              value: newValue,
            })
          }
          if (value !== '' && this.song.versionTelegramMelody === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionTelegramMelody',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionTelegramMelody !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionTelegramMelody', value: 0 })
          }
        }
      },
    },
    'song.idTelegramDemo.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idTelegramDemo
          if (value && value.startsWith(this.prefixLinkTelegram)) {
            const newValue = value.replace(this.prefixLinkTelegram, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idTelegramDemo', value: newValue })
          }
          if (value !== '' && this.song.versionTelegramDemo === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionTelegramDemo',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionTelegramDemo !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionTelegramDemo', value: 0 })
          }
        }
      },
    },
    'song.idMaxKaraoke.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idMaxKaraoke
          if (value && value.startsWith(this.prefixLinkMax)) {
            const newValue = value.replace(this.prefixLinkMax, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idMaxKaraoke', value: newValue })
          }
          if (value !== '' && value !== '-' && this.song.versionMaxKaraoke === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionMaxKaraoke',
              value: this.song.resultVersion,
            })
          } else if ((value === '' || value === '-') && this.song.versionMaxKaraoke !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionMaxKaraoke', value: 0 })
          }
        }
      },
    },
    'song.idMaxLyrics.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idMaxLyrics
          if (value && value.startsWith(this.prefixLinkMax)) {
            const newValue = value.replace(this.prefixLinkMax, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idMaxLyrics', value: newValue })
          }
          if (value !== '' && this.song.versionMaxLyrics === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionMaxLyrics',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionMaxLyrics !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionMaxLyrics', value: 0 })
          }
        }
      },
    },
    'song.idMaxChords.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idMaxChords
          if (value && value.startsWith(this.prefixLinkMax)) {
            const newValue = value.replace(this.prefixLinkMax, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idMaxChords', value: newValue })
          }
          if (value !== '' && this.song.versionMaxChords === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionMaxChords',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionMaxChords !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionMaxChords', value: 0 })
          }
        }
      },
    },
    'song.idMaxMelody.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idMaxMelody
          if (value && value.startsWith(this.prefixLinkMax)) {
            const newValue = value.replace(this.prefixLinkMax, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idMaxMelody', value: newValue })
          }
          if (value !== '' && this.song.versionMaxMelody === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionMaxMelody',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionMaxMelody !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionMaxMelody', value: 0 })
          }
        }
      },
    },
    'song.idMaxDemo.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.idMaxDemo
          if (value && value.startsWith(this.prefixLinkMax)) {
            const newValue = value.replace(this.prefixLinkMax, '')
            this.$store.dispatch('setCurrentSongField', { name: 'idMaxDemo', value: newValue })
          }
          if (value !== '' && this.song.versionMaxDemo === 0) {
            this.$store.dispatch('setCurrentSongField', {
              name: 'versionMaxDemo',
              value: this.song.resultVersion,
            })
          } else if (value === '' && this.song.versionMaxDemo !== 0) {
            this.$store.dispatch('setCurrentSongField', { name: 'versionMaxDemo', value: 0 })
          }
        }
      },
    },
    'song.date.value': {
      deep: true,
      handler() {
        if (this.song) {
          const value = this.song.date
          const regex = /^(\d{2}\.\d{2}\.\d{2})\s(\d{2}:\d{2})$/
          const match = value.match(regex)
          if (match) {
            const date = match[1]
            const time = match[2]
            this.$store.dispatch('setCurrentSongField', { name: 'date', value: date })
            this.$store.dispatch('setCurrentSongField', { name: 'time', value: time })
          }
        }
      },
    },
  },
  async mounted() {
    const { create } = useToast()
    this.createToast = create
    this.$store
      .dispatch('getAuthorPictureBase64Promise')
      .then((image) => (this.imageAuthorBase64 = image))
    this.$store
      .dispatch('getAlbumPictureBase64Promise')
      .then((image) => (this.imageAlbumBase64 = image))
    this.$store
      .dispatch('getTextFormattedPromise')
      .then((textFormatted) => (this.textFormatted = textFormatted))
    this.$store
      .dispatch('getNotesFormattedPromise')
      .then((notesFormatted) => (this.notesFormatted = notesFormatted))
    this.$store
      .dispatch('getChordsFormattedPromise')
      .then((chordsFormatted) => (this.chordsFormatted = chordsFormatted))
    this.autoSave = await this.propAutoSave()
    this.showChordsIfEmpty = await this.propShowChordsIfEmpty()
    this.showMelodyIfEmpty = await this.propShowMelodyIfEmpty()
    this.allowUpdateRemote = await this.propAllowUpdateRemote()
    this.allowUpdateLocal = await this.propAllowUpdateLocal()
    this.allowAddSync = await this.propAllowAddSync()
    this.autoSaveDelayMs = Number(await this.propAutoSaveDelayMs())
    // Источник (local/server) для кнопки «Назначить» — KaraokeProperty editorAssignmentDefaultTarget.
    await this.$store.dispatch('loadEditorDefaultTarget')
    this.$store.dispatch('loadEditorSiteUsers', this.$store.getters.getEditorDefaultTarget)
    this.reloadAssignmentStatus()
  },

  methods: {
    openPlayer() {
      window.open(`/player/${this.song.id}`, '_blank')
    },
    // --- Кнопка «Назначить»/«Назначено» (онлайн-редактор) -----------------------------------
    reloadAssignmentStatus() {
      if (!this.song) return
      const target = this.$store.getters.getEditorDefaultTarget
      return this.$store.dispatch('loadAssignmentStatusBySongIds', {
        songIds: [this.song.id],
        target,
      })
    },
    assignStatusLabel(s) {
      return ASSIGN_STATUS_LABELS[s] || 'Назначено'
    },
    async onAssignSelect(event) {
      const assigneeId = event.target.value
      event.target.value = ''
      if (!assigneeId) return
      const songId = this.song.id
      const target = this.$store.getters.getEditorDefaultTarget
      let res = await this.$store.dispatch('assignSong', { songId, assigneeId, target })
      if (res && res.error === 'markers_exist') {
        const clearMarkers = window.confirm(
          'В песне уже есть маркеры. Удалить их при назначении задания?',
        )
        res = await this.$store.dispatch('assignSong', { songId, assigneeId, target, clearMarkers })
      }
      if (res && res.ok) {
        this.reloadAssignmentStatus()
      } else {
        alert('Не удалось назначить: ' + ((res && res.error) || 'неизвестная ошибка'))
      }
    },
    async openAssignmentReview() {
      if (!this.assignmentInfo) return
      await this.$store.dispatch('loadAssignmentById', {
        id: this.assignmentInfo.assignmentId,
        target: this.$store.getters.getEditorDefaultTarget,
      })
      this.isAssignReviewVisible = true
    },
    onAssignmentReviewed() {
      this.isAssignReviewVisible = false
      this.reloadAssignmentStatus()
    },
    searchTextForSong() {
      this.customConfirmParams = {
        header: 'Подтвердите поиск текста',
        body: `Найти в Интернете тексты для этой песни?`,
        timeout: 10,
        callback: this.doSearchTextForSong,
      }
      this.isCustomConfirmVisible = true
    },
    doSearchTextForSong() {
      this.$store.dispatch('searchTextForSong')
    },
    findOriginalForSong() {
      this.customConfirmParams = {
        header: 'Подтвердите поиск оригинала',
        body: `Найти в базе "оригинал" этой песни для копирования текста? Если оригинал не найден - будет запущен поиск текста в Интернете.`,
        timeout: 10,
        callback: this.doFindOriginalForSong,
      }
      this.isCustomConfirmVisible = true
    },
    doFindOriginalForSong() {
      this.$store.dispatch('findOriginalForSong')
    },
    showFamilySongs() {
      this.isFamilySongsVisible = true
    },
    closeFamilySongs() {
      this.isFamilySongsVisible = false
    },
    async selectFamilySong(payload) {
      this.isFamilySongsVisible = false
      const data = await this.$store.dispatch('selectFamilySongPromise', {
        idAnother: payload.id,
        deltaMs: payload.deltaMs,
      })
      const result = JSON.parse(data)
      if (result) {
        this.song.rootId = result.rootId
        this.song.idStatus = result.idStatus
      }
    },
    async findAudioParent() {
      if (this.isFindingAudioParent) return
      this.isFindingAudioParent = true
      try {
        const data = await this.$store.dispatch('findAudioParentPromise')
        const result = JSON.parse(data)
        if (result) {
          this.song.audioParentId = result.audioParentId
          this.song.audioSimilarityPercent = result.audioSimilarityPercent
          this.song.audioDeltaMs = result.audioDeltaMs
        }
      } finally {
        this.isFindingAudioParent = false
      }
    },
    async propAutoSave() {
      const propValue = await this.$store.getters.getPropValue('autoSave')
      return propValue === 'true'
    },
    async propShowChordsIfEmpty() {
      const propValue = await this.$store.getters.getPropValue('showChordsIfEmpty')
      return propValue === 'true'
    },
    async propShowMelodyIfEmpty() {
      const propValue = await this.$store.getters.getPropValue('showMelodyIfEmpty')
      return propValue === 'true'
    },
    async propAllowUpdateRemote() {
      const propValue = await this.$store.getters.getPropValue('allowUpdateRemote')
      return propValue === 'true'
    },
    async propAllowUpdateLocal() {
      const propValue = await this.$store.getters.getPropValue('allowUpdateLocal')
      return propValue === 'true'
    },
    async propAllowAddSync() {
      const propValue = await this.$store.getters.getPropValue('allowAddSync')
      return propValue === 'true'
    },
    async propAutoSaveDelayMs() {
      return await this.$store.getters.getPropValue('autoSaveDelayMs')
    },
    setStatus(idStatus) {
      this.song.idStatus = idStatus
      let status = 'N/A'
      switch (idStatus) {
        case 0: {
          status = 'NONE'
          break
        }
        case 1: {
          status = 'TEXT_CREATE'
          break
        }
        case 2: {
          status = 'TEXT_CHECK'
          break
        }
        case 3: {
          status = 'PROJECT_CREATE'
          break
        }
        case 4: {
          status = 'PROJECT_CHECK'
          break
        }
        case 5: {
          status = 'RENDERING'
          break
        }
        case 6: {
          status = 'DONE'
          break
        }
      }
      this.song.status = status
    },
    setExclusive(exclusive) {
      this.song.exclusive = exclusive
    },
    setFree(free) {
      this.song.free = free
    },
    // Разрешение подписки на песню: 0 = разрешено (тариф по умолчанию), -1 = автор запретил.
    setSongSubscriptionAllowed(allowed) {
      this.song.idTariff = allowed ? 0 : -1
    },
    songSubscriptionButtonClass(allowed) {
      const isAllowedNow = this.song.idTariff !== -1
      return allowed === isAllowedNow ? 'group-button-round-wide-active' : ''
    },
    toSyncButtonClass(toSync) {
      return toSync ? 'group-button-active' : ''
    },
    statusButtonClass(status) {
      return status === this.song.idStatus ? 'group-button-active' : ''
    },
    exclusiveButtonClass(exclusive) {
      return exclusive === this.song.exclusive ? 'group-button-round-wide-active' : ''
    },
    freeButtonClass(free) {
      return free === this.song.free ? 'group-button-round-wide-active' : ''
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
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '100px', textAlign: 'center', borderRadius: '10px' },
          },
          {
            fldName: 'fields',
            fldLabel: 'Поля:',
            fldValue: 'SOURCE_TEXT;RESULT_TEXT;SOURCE_MARKERS',
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '400px', textAlign: 'right', borderRadius: '10px' },
          },
        ],
      }
      this.isCustomConfirmVisible = true
    },
    doСopyFieldsFromAnother(result) {
      this.$store.dispatch('copyFieldsFromAnotherPromise', {
        idAnother: result.idAnother,
        fields: result.fields,
      })
    },
    createPictureBoostyTeaser() {
      this.customConfirmParams = {
        header: 'Подтвердите создание картинки Boosty Teaser',
        body: `Создать картинку Boosty Teaser для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreatePictureBoostyTeaser,
      }
      this.isCustomConfirmVisible = true
    },
    doCreatePictureBoostyTeaser() {
      this.$store.dispatch('createPictureBoostyTeaserPromise')
    },
    createPictureSponsrTeaser() {
      this.customConfirmParams = {
        header: 'Подтвердите создание картинки Sponsr Teaser',
        body: `Создать картинку Sponsr Teaser для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreatePictureSponsrTeaser,
      }
      this.isCustomConfirmVisible = true
    },
    doCreatePictureSponsrTeaser() {
      this.$store.dispatch('createPictureSponsrTeaserPromise')
    },
    createPictureBoostyFiles() {
      this.customConfirmParams = {
        header: 'Подтвердите создание картинки Boosty Files',
        body: `Создать картинку Boosty Files для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreatePictureBoostyFiles,
      }
      this.isCustomConfirmVisible = true
    },
    doCreatePictureBoostyFiles() {
      this.$store.dispatch('createPictureBoostyFilesPromise')
    },
    createPictureVK() {
      this.customConfirmParams = {
        header: 'Подтвердите создание картинки VK',
        body: `Создать картинку VK для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreatePictureVK,
      }
      this.isCustomConfirmVisible = true
    },
    doCreatePictureVK() {
      this.$store.dispatch('createPictureVKPromise')
    },
    createPictureVKlink() {
      this.customConfirmParams = {
        header: 'Подтвердите создание картинки VKlink',
        body: `Создать картинку VKlink для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreatePictureVKlink,
      }
      this.isCustomConfirmVisible = true
    },
    doCreatePictureVKlink() {
      this.$store.dispatch('createPictureVKlinkPromise')
    },
    createPictureKaraoke() {
      this.customConfirmParams = {
        header: 'Подтвердите создание картинки KARAOKE',
        body: `Создать картинку KARAOKE для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreatePictureKaraoke,
      }
      this.isCustomConfirmVisible = true
    },
    doCreatePictureKaraoke() {
      this.$store.dispatch('createPictureKaraokePromise')
    },
    createPictureLyrics() {
      this.customConfirmParams = {
        header: 'Подтвердите создание картинки LYRICS',
        body: `Создать картинку LYRICS для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreatePictureLyrics,
      }
      this.isCustomConfirmVisible = true
    },
    doCreatePictureLyrics() {
      this.$store.dispatch('createPictureLyricsPromise')
    },
    createPictureChords() {
      this.customConfirmParams = {
        header: 'Подтвердите создание картинки CHORDS',
        body: `Создать картинку CHORDS для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreatePictureChords,
      }
      this.isCustomConfirmVisible = true
    },
    doCreatePictureChords() {
      this.$store.dispatch('createPictureChordsPromise')
    },

    createPictureTabs() {
      this.customConfirmParams = {
        header: 'Подтвердите создание картинки MELODY',
        body: `Создать картинку MELODY для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreatePictureMelody,
      }
      this.isCustomConfirmVisible = true
    },
    doCreatePictureMelody() {
      this.$store.dispatch('createPictureTabsPromise')
    },

    createDescriptionFileKaraoke() {
      this.customConfirmParams = {
        header: 'Подтвердите создание текста KARAOKE',
        body: `Создать текст KARAOKE для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreateDescriptionFileKaraoke,
      }
      this.isCustomConfirmVisible = true
    },
    doCreateDescriptionFileKaraoke() {
      this.$store.dispatch('createDescriptionFileKaraokePromise')
    },
    createDescriptionFileLyrics() {
      this.customConfirmParams = {
        header: 'Подтвердите создание текста LYRICS',
        body: `Создать текст LYRICS для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreateDescriptionFileLyrics,
      }
      this.isCustomConfirmVisible = true
    },
    doCreateDescriptionFileLyrics() {
      this.$store.dispatch('createDescriptionFileLyricsPromise')
    },
    createDescriptionFileChords() {
      this.customConfirmParams = {
        header: 'Подтвердите создание текста CHORDS',
        body: `Создать текст CHORDS для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreateDescriptionFileChords,
      }
      this.isCustomConfirmVisible = true
    },
    doCreateDescriptionFileChords() {
      this.$store.dispatch('createDescriptionFileChordsPromise')
    },

    createDescriptionFileTabs() {
      this.customConfirmParams = {
        header: 'Подтвердите создание текста TABS',
        body: `Создать текст TABS для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreateDescriptionFileTabs,
      }
      this.isCustomConfirmVisible = true
    },
    doCreateDescriptionFileTabs() {
      this.$store.dispatch('createDescriptionFileTabsPromise')
    },

    createKeyBpmFinderProcess() {
      this.customConfirmParams = {
        header: 'Подтвердите создание BPM и TEMP из файла',
        body: `Создать текст TABS для песни <strong>«${this.song.songName}»</strong>?`,
        callback: this.doCreateKeyBpmFinderProcess,
      }
      this.isCustomConfirmVisible = true
    },
    doCreateKeyBpmFinderProcess() {
      this.$store.dispatch('createKeyBpmFinderProcessPromise')
    },

    createRenderMp4Version(version) {
      this.customConfirmParams = {
        header: `Подтвердите рендер ${version}`,
        body: `Запустить рендер ${version} из онлайн-плеера для песни <strong>«${this.song.songName}»</strong>?`,
        callback: () => this.doCreateRenderMp4Version(version),
      }
      this.isCustomConfirmVisible = true
    },
    doCreateRenderMp4Version(version) {
      this.$store.dispatch('createRenderMp4VersionPromise', version)
    },

    playLyrics() {
      this.$store.getters.playLyrics()
    },
    playKaraoke() {
      this.$store.getters.playKaraoke()
    },
    playChords() {
      this.$store.getters.playChords()
    },
    playTabs() {
      this.$store.getters.playTabs()
    },
    playDemo() {
      this.$store.getters.playRenderMp4Version('DEMO')()
    },

    goToPreviousSong() {
      const id = this.getNeighbourSongId('Previous')
      this.$store.dispatch('setCurrentSongId', id)
    },
    goToNextSong() {
      const id = this.getNeighbourSongId('Next')
      this.$store.dispatch('setCurrentSongId', id)
    },
    goToLeftSong() {
      const id = this.getNeighbourSongId('Left')
      this.$store.dispatch('setCurrentSongId', id)
    },
    goToRightSong() {
      const id = this.getNeighbourSongId('Right')
      this.$store.dispatch('setCurrentSongId', id)
    },
    getNeighbourSongId(neighbour) {
      // Нужно определить, находимся ли мы с "песнях" или в "публикациях"
      let id = this.song.id
      switch (this.parentRoute) {
        case 'Songs': {
          const index = this.songsDigests.findIndex((song) => song.id === id)
          if (index !== -1) {
            switch (neighbour) {
              case 'Left': {
                break
              }
              case 'Right': {
                break
              }
              case 'Next': {
                id = index < this.songsDigests.length - 1 ? this.songsDigests[index + 1].id : id
                break
              }
              case 'Previous': {
                id = index > 0 ? this.songsDigests[index - 1].id : id
                break
              }
            }
          }
          break
        }
        case 'Publications': {
          const targetId = id
          let targetRowIndex = -1
          let targetCellIndex = -1

          // Поиск целевого объекта
          for (let i = 0; i < this.publishDigest.length; i++) {
            const row = this.publishDigest[i].csrCells
            const indexCell = row.findIndex(
              (cell) => cell.settingsDTO && cell.settingsDTO.id === targetId,
            )
            if (indexCell !== -1) {
              targetRowIndex = i
              targetCellIndex = indexCell
              break
            }
          }
          if (targetRowIndex !== -1 && targetCellIndex !== -1) {
            switch (neighbour) {
              case 'Left': {
                id =
                  targetCellIndex > 0
                    ? this.publishDigest[targetRowIndex].csrCells[targetCellIndex - 1].settingsDTO
                      ? this.publishDigest[targetRowIndex].csrCells[targetCellIndex - 1].settingsDTO
                          .id
                      : id
                    : id
                break
              }
              case 'Right': {
                id =
                  targetCellIndex < this.publishDigest[targetRowIndex].csrCells.length - 1
                    ? this.publishDigest[targetRowIndex].csrCells[targetCellIndex + 1].settingsDTO
                      ? this.publishDigest[targetRowIndex].csrCells[targetCellIndex + 1].settingsDTO
                          .id
                      : id
                    : id
                break
              }
              case 'Next': {
                id =
                  targetRowIndex < this.publishDigest.length - 1
                    ? this.publishDigest[targetRowIndex + 1].csrCells[targetCellIndex].settingsDTO
                      ? this.publishDigest[targetRowIndex + 1].csrCells[targetCellIndex].settingsDTO
                          .id
                      : id
                    : id
                break
              }
              case 'Previous': {
                id =
                  targetRowIndex > 0
                    ? this.publishDigest[targetRowIndex - 1].csrCells[targetCellIndex].settingsDTO
                      ? this.publishDigest[targetRowIndex - 1].csrCells[targetCellIndex].settingsDTO
                          .id
                      : id
                    : id
                break
              }
            }
          }
          break
        }
        default: {
          switch (neighbour) {
            case 'Left': {
              id = this.leftSongId
              break
            }
            case 'Right': {
              id = this.rightSongId
              break
            }
            case 'Next': {
              id = this.nextSongId
              break
            }
            case 'Previous': {
              id = this.previousSongId
              break
            }
          }
          break
        }
      }
      return id
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
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px' },
          },
          {
            fldName: 'priorKaraoke',
            fldLabel: 'Приоритет Karaoke:',
            fldValue: this.$store.getters.getLastPriorKaraoke,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px' },
          },
          {
            fldName: 'priorChords',
            fldLabel: 'Приоритет Chords:',
            fldValue: this.$store.getters.getLastPriorChords,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px' },
          },
          {
            fldName: 'priorMelody',
            fldLabel: 'Приоритет Tabs:',
            fldValue: this.$store.getters.getLastPriorMelody,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px' },
          },
          {
            fldName: 'priorDemo',
            fldLabel: 'Приоритет Demo:',
            fldValue: this.$store.getters.getLastPriorDemo,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px' },
          },
          {
            fldName: 'threadId',
            fldLabel: 'threadId:',
            fldValue: this.$store.getters.getLastThreadId,
            fldLabelStyle: { width: '200px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px' },
          },
        ],
      }
      this.isCustomConfirmVisible = true
    },
    doCreateKaraoke(result) {
      this.$store.dispatch('setLastPriorLyrics', { value: result.priorLyrics })
      this.$store.dispatch('setLastPriorKaraoke', { value: result.priorKaraoke })
      this.$store.dispatch('setLastPriorChords', { value: result.priorChords })
      this.$store.dispatch('setLastPriorMelody', { value: result.priorMelody })
      this.$store.dispatch('setLastPriorDemo', { value: result.priorDemo })
      this.$store.dispatch('setLastThreadId', { value: result.threadId })
      this.$store.dispatch('createKaraokePromise', {
        priorLyrics: result.priorLyrics,
        priorKaraoke: result.priorKaraoke,
        priorChords: result.priorChords,
        priorMelody: result.priorMelody,
        priorDemo: result.priorDemo,
        threadId: result.threadId,
      })
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
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px' },
          },
          {
            fldName: 'threadId',
            fldLabel: 'threadId:',
            fldValue: this.$store.getters.getLastThreadId,
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px' },
          },
        ],
      }
      this.isCustomConfirmVisible = true
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
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px' },
          },
          {
            fldName: 'threadId',
            fldLabel: 'threadId:',
            fldValue: this.$store.getters.getLastThreadId,
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px' },
          },
        ],
      }
      this.isCustomConfirmVisible = true
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
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px' },
          },
          {
            fldName: 'threadId',
            fldLabel: 'threadId:',
            fldValue: this.$store.getters.getLastThreadId,
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px' },
          },
        ],
      }
      this.isCustomConfirmVisible = true
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
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px' },
          },
          {
            fldName: 'threadId',
            fldLabel: 'threadId:',
            fldValue: this.$store.getters.getLastThreadId,
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px' },
          },
        ],
      }
      this.isCustomConfirmVisible = true
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
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px' },
          },
          {
            fldName: 'threadId',
            fldLabel: 'threadId:',
            fldValue: this.$store.getters.getLastThreadId,
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px' },
          },
        ],
      }
      this.isCustomConfirmVisible = true
    },
    doCreateDemucs2(result) {
      this.$store.dispatch('setLastPriorDemucs', { value: result.prior })
      this.$store.dispatch('setLastThreadId', { value: result.threadId })
      this.$store.dispatch('createDemucs2Promise', {
        prior: result.prior,
        threadId: result.threadId,
      })
    },
    doCreateDemucs5(result) {
      this.$store.dispatch('setLastPriorDemucs', { value: result.prior })
      this.$store.dispatch('setLastThreadId', { value: result.threadId })
      this.$store.dispatch('createDemucs5Promise', {
        prior: result.prior,
        threadId: result.threadId,
      })
    },
    doCreateSheetsage(result) {
      this.$store.dispatch('setLastPriorDemucs', { value: result.prior })
      this.$store.dispatch('setLastThreadId', { value: result.threadId })
      this.$store.dispatch('createSheetsagePromise', {
        prior: result.prior,
        threadId: result.threadId,
      })
    },
    doCreateMP3Karaoke(result) {
      this.$store.dispatch('setLastPriorDemucs', { value: result.prior })
      this.$store.dispatch('setLastThreadId', { value: result.threadId })
      this.$store.dispatch('createMP3KaraokePromise', {
        prior: result.prior,
        threadId: result.threadId,
      })
    },
    doCreateMP3Lyrics(result) {
      this.$store.dispatch('setLastPriorDemucs', { value: result.prior })
      this.$store.dispatch('setLastThreadId', { value: result.threadId })
      this.$store.dispatch('createMP3LyricsPromise', {
        prior: result.prior,
        threadId: result.threadId,
      })
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
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px' },
          },
          {
            fldName: 'threadId',
            fldLabel: 'threadId:',
            fldValue: this.$store.getters.getLastThreadId,
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px' },
            fldValueStyle: { width: '80px', textAlign: 'center', borderRadius: '10px' },
          },
        ],
      }
      this.isCustomConfirmVisible = true
    },
    doCreateSymlinks(result) {
      this.$store.dispatch('setLastPriorSymlinks', { value: result.prior })
      this.$store.dispatch('setLastThreadId', { value: result.threadId })
      this.$store.dispatch('createSymlinksPromise', {
        prior: result.prior,
        threadId: result.threadId,
      })
    },
    setDateTimeAuthor() {
      this.customConfirmParams = {
        header: 'Подтвердите изменение дат',
        body: `Вы действительно хотите установить дату/время публикации для песен автора, начиная с текущей?`,
        timeout: 10,
        callback: this.doSetDateTimeAuthor,
      }
      this.isCustomConfirmVisible = true
    },
    doSetDateTimeAuthor() {
      this.$store.dispatch('setDateTimeAuthorPromise')
    },
    deleteSong() {
      this.customConfirmParams = {
        header: 'Подтвердите удаление песни',
        body: `Удалить песню <strong>«${this.song.songName}»</strong>?`,
        timeout: 10,
        callback: this.doDeleteSong,
      }
      this.isCustomConfirmVisible = true
    },
    doDeleteSong() {
      this.$store.dispatch('deleteCurrentSong')
      this.$emit('close')
    },
    async showSubsEdit() {
      this.voices = JSON.parse(await this.$store.getters.getVoices).voices
      this.isSubsEditVisible = true
    },
    closeSubsEdit() {
      this.$store
        .dispatch('getTextFormattedPromise')
        .then((textFormatted) => (this.textFormatted = textFormatted))
      this.$store
        .dispatch('getNotesFormattedPromise')
        .then((notesFormatted) => (this.notesFormatted = notesFormatted))
      this.$store
        .dispatch('getChordsFormattedPromise')
        .then((chordsFormatted) => (this.chordsFormatted = chordsFormatted))
      this.isSubsEditVisible = false
    },
    showKaraokeEditor() {
      // При открытии редактора из карточки песни — режим 'song', пишем в Settings той же БД,
      // что сейчас активна для заданий (assignmentsTarget), единообразно с логикой остальных
      // target-aware эндпоинтов SongEditorController.
      try {
        this.karaokeEditorTarget = this.$store.getters.getAssignmentsTarget || 'local'
      } catch (e) {
        this.karaokeEditorTarget = 'local'
      }
      this.isKaraokeEditorVisible = true
    },
    closeKaraokeEditor() {
      this.isKaraokeEditorVisible = false
      // После закрытия редактора обновим локальные formatted-поля (правки могли изменить маркеры).
      this.$store
        .dispatch('getTextFormattedPromise')
        .then((textFormatted) => (this.textFormatted = textFormatted))
      this.$store
        .dispatch('getNotesFormattedPromise')
        .then((notesFormatted) => (this.notesFormatted = notesFormatted))
      this.$store
        .dispatch('getChordsFormattedPromise')
        .then((chordsFormatted) => (this.chordsFormatted = chordsFormatted))
    },
    closeCustomConfirm() {
      this.isCustomConfirmVisible = false
    },
    showHealthReportTable() {
      this.isHealthReportTableVisible = true
    },
    closeHealthReportTable() {
      this.$store.dispatch('setCurrentSongHealthReports', this.song.id)
      this.isHealthReportTableVisible = false
    },
    async getLinkBoosty() {
      let value = this.linkBoosty
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkBoosty', value)
    },
    async getLinkSponsrPlay() {
      let value = this.linkSponsrPlay
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkSponsrPlay', value)
    },
    async openLinkSponsrNew() {
      let value = await this.$store.getters.getSponsrHeader
      await navigator.clipboard.writeText(value)
      // this.showCopyToClipboardToast('getSponsrHeader', value);
      window.open(this.linkSponsrNew, '_blank')
    },
    openLinkSponsr() {
      window.open(this.linkSponsrEdit, '_blank')
    },
    openLinkBoosty() {
      window.open(this.linkBoosty, '_blank')
    },
    openMainLink() {
      window.open(this.mainLink, '_blank')
    },
    updateRemote() {
      this.$store.dispatch('updateOneRemoteSettingsPromise', this.song.id)
    },
    toSyncRemote() {
      // this.$store.dispatch('toSyncOneRemoteSettingsPromise', this.song.id);
      this.$store.dispatch('changeToSync')
    },
    async openLinkBoostyNew() {
      let value = await this.$store.getters.getBoostyHeader
      await navigator.clipboard.writeText(value)
      // this.showCopyToClipboardToast('getBoostyHeader', value);
      window.open(this.linkBoostyNew, '_blank')
    },
    async getBoostyHeader() {
      let value = await this.$store.getters.getBoostyHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getBoostyHeader', value)
    },
    async getBoostyBody() {
      let value = await this.$store.getters.getBoostyBody
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getBoostyBody', value)
    },
    async getSponsrHeader() {
      let value = await this.$store.getters.getSponsrHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getSponsrHeader', value)
    },
    async getSponsrBody() {
      let value = await this.$store.getters.getSponsrBody
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getSponsrBody', value)
    },
    async getLinkBoostyFiles() {
      let value = this.linkBoostyFiles
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkBoostyFiles', value)
    },
    openLinkBoostyFiles() {
      window.open(this.linkBoostyFiles, '_blank')
    },
    async openLinkBoostyFilesNew() {
      let value = await this.$store.getters.getBoostyFilesHeader
      await navigator.clipboard.writeText(value)
      // this.showCopyToClipboardToast('getBoostyFilesHeader', value);
      window.open(this.linkBoostyNew, '_blank')
    },
    async getBoostyFilesHeader() {
      let value = await this.$store.getters.getBoostyFilesHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getBoostyFilesHeader', value)
    },

    async getLinkVkGroup() {
      let value = this.linkVkGroup
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkVkGroup', value)
    },
    async getVkGroupBody() {
      let value = await this.$store.getters.getVkGroupBody
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkGroupBody', value)
    },
    async getVkGroupBodySponsr() {
      let value = await this.$store.getters.getVkGroupBodySponsr
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkGroupBodySponsr', value)
    },
    openLinkVkGroup() {
      window.open(this.linkVkGroup, '_blank')
    },

    async getLinkDzenKaraokePlay() {
      let value = this.linkDzenKaraokePlay
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkDzenKaraokePlay', value)
    },
    openLinkDzenKaraokePlay() {
      window.open(this.linkDzenKaraokePlay, '_blank')
    },
    openLinkDzenKaraokeEdit() {
      window.open(this.linkDzenKaraokeEdit, '_blank')
    },
    async getLinkDzenLyricsPlay() {
      let value = this.linkDzenLyricsPlay
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkDzenLyricsPlay', value)
    },
    openLinkDzenLyricsPlay() {
      window.open(this.linkDzenLyricsPlay, '_blank')
    },
    openLinkDzenLyricsEdit() {
      window.open(this.linkDzenLyricsEdit, '_blank')
    },
    async getLinkDzenChordsPlay() {
      let value = this.linkDzenChordsPlay
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkDzenChordsPlay', value)
    },
    openLinkDzenChordsPlay() {
      window.open(this.linkDzenChordsPlay, '_blank')
    },
    openLinkDzenChordsEdit() {
      window.open(this.linkDzenChordsEdit, '_blank')
    },
    async getLinkDzenTabsPlay() {
      let value = this.linkDzenTabsPlay
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkDzenTabsPlay', value)
    },
    openLinkDzenTabsPlay() {
      window.open(this.linkDzenTabsPlay, '_blank')
    },
    openLinkDzenTabsEdit() {
      window.open(this.linkDzenTabsEdit, '_blank')
    },
    async getLinkDzenDemoPlay() {
      let value = this.linkDzenDemoPlay
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkDzenDemoPlay', value)
    },
    openLinkDzenDemoPlay() {
      window.open(this.linkDzenDemoPlay, '_blank')
    },
    openLinkDzenDemoEdit() {
      window.open(this.linkDzenDemoEdit, '_blank')
    },

    async getDzenKaraokeHeader() {
      let value = await this.$store.getters.getDzenKaraokeHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getDzenKaraokeHeader', value)
    },
    async getDzenKaraokeBody() {
      let value = await this.$store.getters.getDzenKaraokeBody
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getDzenKaraokeBody', value)
    },
    async getDzenLyricsHeader() {
      let value = await this.$store.getters.getDzenLyricsHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getDzenLyricsHeader', value)
    },
    async getDzenLyricsBody() {
      let value = await this.$store.getters.getDzenLyricsBody
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getDzenLyricsBody', value)
    },
    async getDzenChordsHeader() {
      let value = await this.$store.getters.getDzenChordsHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getDzenChordsHeader', value)
    },
    async getDzenChordsBody() {
      let value = await this.$store.getters.getDzenChordsBody
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getDzenChordsBody', value)
    },
    async getDzenTabsHeader() {
      let value = await this.$store.getters.getDzenTabsHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getDzenTabsHeader', value)
    },
    async getDzenTabsBody() {
      let value = await this.$store.getters.getDzenTabsBody
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getDzenTabsBody', value)
    },
    async getDzenDemoHeader() {
      let value = await this.$store.getters.getDzenDemoHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getDzenDemoHeader', value)
    },
    async getDzenDemoBody() {
      let value = await this.$store.getters.getDzenDemoBody
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getDzenDemoBody', value)
    },

    async getLinkPlKaraokePlay() {
      let value = this.linkPlKaraokePlay
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkPlKaraokePlay', value)
    },
    openLinkPlKaraokePlay() {
      window.open(this.linkPlKaraokePlay, '_blank')
    },
    openLinkPlKaraokeEdit() {
      window.open(this.linkPlKaraokeEdit, '_blank')
    },
    async getLinkPlLyricsPlay() {
      let value = this.linkPlLyricsPlay
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkPlLyricsPlay', value)
    },
    openLinkPlLyricsPlay() {
      window.open(this.linkPlLyricsPlay, '_blank')
    },
    openLinkPlLyricsEdit() {
      window.open(this.linkPlLyricsEdit, '_blank')
    },
    async getLinkPlChordsPlay() {
      let value = this.linkPlChordsPlay
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkPlChordsPlay', value)
    },
    openLinkPlChordsPlay() {
      window.open(this.linkPlChordsPlay, '_blank')
    },
    openLinkPlChordsEdit() {
      window.open(this.linkPlChordsEdit, '_blank')
    },
    async getLinkPlTabsPlay() {
      let value = this.linkPlTabsPlay
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkPlTabsPlay', value)
    },
    openLinkPlTabsPlay() {
      window.open(this.linkPlTabsPlay, '_blank')
    },
    openLinkPlTabsEdit() {
      window.open(this.linkPlTabsEdit, '_blank')
    },

    async getPlKaraokeHeader() {
      let value = await this.$store.getters.getPlKaraokeHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getPlKaraokeHeader', value)
    },
    async getPlKaraokeBody() {
      let value = await this.$store.getters.getPlKaraokeBody
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getPlKaraokeBody', value)
    },
    async getPlLyricsHeader() {
      let value = await this.$store.getters.getPlLyricsHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getPlLyricsHeader', value)
    },
    async getPlLyricsBody() {
      let value = await this.$store.getters.getPlLyricsBody
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getPlLyricsBody', value)
    },
    async getPlChordsHeader() {
      let value = await this.$store.getters.getPlChordsHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getPlChordsHeader', value)
    },
    async getPlChordsBody() {
      let value = await this.$store.getters.getPlChordsBody
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getPlChordsBody', value)
    },
    async getPlTabsHeader() {
      let value = await this.$store.getters.getPlTabsHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getPlTabsHeader', value)
    },
    async getPlTabsBody() {
      let value = await this.$store.getters.getPlTabsBody
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getPlTabsBody', value)
    },

    async getLinkVkKaraoke() {
      let value = this.linkVkKaraoke
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkVkKaraoke', value)
    },
    openLinkVkKaraoke() {
      window.open(this.linkVkKaraoke, '_blank')
    },
    async getLinkVkLyrics() {
      let value = this.linkVkLyrics
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkVkLyrics', value)
    },
    openLinkVkLyrics() {
      window.open(this.linkVkLyrics, '_blank')
    },
    async getLinkVkChords() {
      let value = this.linkVkChords
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkVkChords', value)
    },
    openLinkVkChords() {
      window.open(this.linkVkChords, '_blank')
    },
    async getLinkVkTabs() {
      let value = this.linkVkTabs
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkVkTabs', value)
    },
    openLinkVkTabs() {
      window.open(this.linkVkTabs, '_blank')
    },
    async getLinkVkDemo() {
      let value = this.linkVkDemo
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkVkDemo', value)
    },
    openLinkVkDemo() {
      window.open(this.linkVkDemo, '_blank')
    },

    async getVkKaraokeHeader() {
      let value = await this.$store.getters.getVkKaraokeHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkKaraokeHeader', value)
    },

    async getVkKaraokeBody() {
      let value = await this.$store.getters.getVkKaraokeBody
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkKaraokeBody', value)
    },
    async getVkLyricsHeader() {
      let value = await this.$store.getters.getVkLyricsHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkLyricsHeader', value)
    },
    async getVkLyricsBody() {
      let value = await this.$store.getters.getVkLyricsBody
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkLyricsBody', value)
    },
    async getVkChordsHeader() {
      let value = await this.$store.getters.getVkChordsHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkChordsHeader', value)
    },
    async getVkChordsBody() {
      let value = await this.$store.getters.getVkChordsBody
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkChordsBody', value)
    },
    async getVkTabsHeader() {
      let value = await this.$store.getters.getVkTabsHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkTabsHeader', value)
    },
    async getVkTabsBody() {
      let value = await this.$store.getters.getVkTabsBody
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkTabsBody', value)
    },
    async getVkDemoHeader() {
      let value = await this.$store.getters.getVkDemoHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkDemoHeader', value)
    },
    async getVkDemoBody() {
      let value = await this.$store.getters.getVkDemoBody
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getVkDemoBody', value)
    },

    async getLinkTelegramKaraoke() {
      let value = this.linkTelegramKaraoke
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkTelegramKaraoke', value)
    },
    openLinkTelegramKaraoke() {
      window.open(this.linkTelegramKaraoke, '_blank')
    },
    async getLinkTelegramLyrics() {
      let value = this.linkTelegramLyrics
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkTelegramLyrics', value)
    },
    openLinkTelegramLyrics() {
      window.open(this.linkTelegramLyrics, '_blank')
    },
    async getLinkTelegramChords() {
      let value = this.linkTelegramChords
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkTelegramChords', value)
    },
    openLinkTelegramChords() {
      window.open(this.linkTelegramChords, '_blank')
    },
    async getLinkTelegramTabs() {
      let value = this.linkTelegramTabs
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkTelegramTabs', value)
    },
    openLinkTelegramTabs() {
      window.open(this.linkTelegramTabs, '_blank')
    },
    async getLinkTelegramDemo() {
      let value = this.linkTelegramDemo
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkTelegramDemo', value)
    },
    openLinkTelegramDemo() {
      window.open(this.linkTelegramDemo, '_blank')
    },

    async getTelegramKaraokeHeader() {
      let value = await this.$store.getters.getTelegramKaraokeHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getTelegramKaraokeHeader', value)
    },

    async getTelegramLyricsHeader() {
      let value = await this.$store.getters.getTelegramLyricsHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getTelegramLyricsHeader', value)
    },
    async getTelegramChordsHeader() {
      let value = await this.$store.getters.getTelegramChordsHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getTelegramChordsHeader', value)
    },
    async getTelegramTabsHeader() {
      let value = await this.$store.getters.getTelegramTabsHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getTelegramTabsHeader', value)
    },
    async getTelegramDemoHeader() {
      let value = await this.$store.getters.getTelegramDemoHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getTelegramDemoHeader', value)
    },

    async getLinkMaxKaraoke() {
      let value = this.linkMaxKaraoke
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkMaxKaraoke', value)
    },
    openLinkMaxKaraoke() {
      window.open(this.linkMaxKaraoke, '_blank')
    },
    async getLinkMaxLyrics() {
      let value = this.linkMaxLyrics
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkMaxLyrics', value)
    },
    openLinkMaxLyrics() {
      window.open(this.linkMaxLyrics, '_blank')
    },
    async getLinkMaxChords() {
      let value = this.linkMaxChords
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkMaxChords', value)
    },
    openLinkMaxChords() {
      window.open(this.linkMaxChords, '_blank')
    },
    async getLinkMaxTabs() {
      let value = this.linkMaxTabs
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkMaxTabs', value)
    },
    openLinkMaxTabs() {
      window.open(this.linkMaxTabs, '_blank')
    },
    async getLinkMaxDemo() {
      let value = this.linkMaxDemo
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('linkMaxDemo', value)
    },
    openLinkMaxDemo() {
      window.open(this.linkMaxDemo, '_blank')
    },

    async getMaxKaraokeHeader() {
      let value = await this.$store.getters.getMaxKaraokeHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getMaxKaraokeHeader', value)
    },

    async getMaxLyricsHeader() {
      let value = await this.$store.getters.getMaxLyricsHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getMaxLyricsHeader', value)
    },
    async getMaxChordsHeader() {
      let value = await this.$store.getters.getMaxChordsHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getMaxChordsHeader', value)
    },
    async getMaxTabsHeader() {
      let value = await this.$store.getters.getMaxTabsHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getMaxTabsHeader', value)
    },
    async getMaxDemoHeader() {
      let value = await this.$store.getters.getMaxDemoHeader
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast('getMaxDemoHeader', value)
    },

    notChanged(name) {
      if (name) {
        return this.song[name] === this.snapshot[name]
      } else {
        return this.diff.length === 0
      }
    },
    edit() {},
    save() {
      clearTimeout(this.saveTimer)
      this.executeSave()
    },
    undoField(name) {
      return this.$store.dispatch('setCurrentSongField', { name: name, value: this.snapshot[name] })
    },
    async copyToClipboard(value, fieldName) {
      await navigator.clipboard.writeText(value)
      this.showCopyToClipboardToast(fieldName, value)
    },
    async pasteFromClipboard(name) {
      await navigator.clipboard.readText().then((data) => {
        return this.$store.dispatch('setCurrentSongField', { name: name, value: data })
      })
    },
    showCopyToClipboardToast(fieldName, fieldValue) {
      if (document.hidden) return
      // Use a shorter name for this.$createElement
      // const h = this.$createElement

      // Функция для преобразования текста с \n в массив VNodes с <br>
      const createTextWithLineBreaks = (text) => {
        if (typeof text !== 'string') {
          return [String(text)]
        }

        const lines = text.split('\n')
        const vnodes = []

        lines.forEach((line, index) => {
          // Добавляем текст строки
          vnodes.push(line)
          // Если это не последняя строка, добавляем <br>
          if (index < lines.length - 1) {
            vnodes.push(h('br'))
          }
        })

        return vnodes
      }

      // Создаем сообщение с возможными переносами строк
      const vNodesMsg = h('div', [
        h('div', { style: { display: 'flex', flexDirection: 'row', flexWrap: 'wrap' } }, [
          h(
            'div',
            {
              style: {
                fontFamily: 'sans-serif',
                fontSize: 'small',
                textAlign: 'left',
                paddingRight: '5px',
              },
            },
            [`Значение поля `],
          ),
          h(
            'div',
            {
              style: {
                fontFamily: 'monospace',
                fontSize: 'small',
                textAlign: 'left',
                fontWeight: 'bold',
                paddingRight: '5px',
                color: 'darkred',
              },
            },
            [fieldName],
          ),
          h('div', { style: { fontFamily: 'sans-serif', fontSize: 'small', textAlign: 'left' } }, [
            ` скопировано в буфер обмена:`,
          ]),
        ]),
        // h('br'),
        h(
          'div',
          { style: { fontFamily: 'monospace', fontSize: 'x-small', textAlign: 'left' } },
          createTextWithLineBreaks(fieldValue),
        ),
      ])

      this.createToast({
        slots: { default: () => [vNodesMsg] },
        title: 'COPY',
        autoHideDelay: 3000,
        bodyClass: 'toast-body-copytoclipboard',
        headerClass: 'toast-header-copytoclipboard',
        appendToast: false,
        position: 'top-start',
        // modelValue: true
      })
    },
    // Асинхронный метод непосредственного сохранения
    async executeSave() {
      // Если сохранение уже идет, просто выходим, чтобы не дублировать запросы
      if (this.isSaving) return

      this.isSaving = true

      try {
        // Собираем актуальные изменения
        let params = {}
        for (let diffItem of this.diff) {
          params[diffItem.name] = diffItem.new
        }

        // Если изменений нет (например, нажали кнопку вручную, но всё уже сохранено)
        if (Object.keys(params).length === 0) {
          this.isSaving = false
          return
        }

        // Ждем завершения сохранения на сервере
        await this.$store.dispatch('saveSong', params)

        // ПОСТ-ОБРАБОТКА:
        // Проверяем, не появились ли новые изменения ПОКА шёл запрос к серверу
        if (this.diff.length > 0) {
          // Если появились, сразу ставим их в очередь на следующий автосейв
          this.saveTimer = setTimeout(this.executeSave, this.autoSaveDelayMs)
        }
      } catch (error) {
        console.error('Ошибка автосохранения:', error)
      } finally {
        this.isSaving = false
      }
    },
  },
}
</script>

<style scoped>
.header {
  display: flex;
  flex-direction: row;
  justify-content: flex-start;
  border: thin dashed darkgray;
  border-radius: 10px;
}

.header-column-1 {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 5px 0;
  background-color: transparent;
}

.header-column-2 {
  display: flex;
  flex-direction: column;
  padding: 5px 0;
  background-color: transparent;
  width: 100%;
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

.header-song-id {
  text-align: center;
  margin: 0 0 0 10px;
  font-family: monospace;
  font-weight: bold;
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
/* Легаси-этапы старого MLT-рендера (статусы 4/6) — больше не влияют на готовность к онлайн-плееру
   (та наступает уже на статусе 3), визуально приглушаем, но оставляем кликабельными. */
.group-button-legacy {
  opacity: 0.5;
  filter: grayscale(60%);
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

.label-wide {
  font-size: small;
  text-align: right;
  width: 220px;
  padding-right: 2px;
  padding-top: 2px;
}

.label-medium {
  font-size: small;
  text-align: right;
  width: 146px;
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

.input-field-short {
  display: block;
  padding-bottom: 3px;
  width: 150px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}
.input-field-short:hover {
  background-color: lightyellow;
}
.input-field-short:focus {
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
  background-color: antiquewhite;
  cursor: pointer;
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
.assign-select {
  max-width: 130px;
  box-sizing: border-box;
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  font-size: 0.72rem;
  padding: 2px 6px;
  border: none;
  border-radius: 3px;
  background-color: #eef0f2;
  color: #555;
  cursor: pointer;
}
.assign-select:hover {
  background-color: #e2e5e8;
}
.assign-badge {
  max-width: 130px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.72rem;
  font-weight: 700;
  border: none;
  border-radius: 3px;
  padding: 3px 7px;
  cursor: pointer;
}
.assign-badge-assigned {
  background: #e2e6ea;
  color: #5a6570;
}
.assign-badge-in_progress {
  background: #dbeafe;
  color: #1e5fbf;
}
.assign-badge-submitted {
  background: #fef3c7;
  color: #92700a;
}
.assign-badge-approved {
  background: #d1f5d8;
  color: #24803a;
}
.assign-badge-rejected {
  background: #ffe0cc;
  color: #b8500f;
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

.group-button-round-wide {
  font-size: small;
  border: solid 1px black;
  border-radius: 6px;
  width: 50px;
  height: 24px;
  margin-left: 2px;
  background-color: antiquewhite;
}
.group-button-round-wide:hover {
  background-color: lightpink;
}
.group-button-round-wide:focus {
  background-color: darksalmon;
}
.group-button-round-wide[disabled] {
  background-color: lightgray;
}
.group-button-round-wide-active {
  background-color: dodgerblue;
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
