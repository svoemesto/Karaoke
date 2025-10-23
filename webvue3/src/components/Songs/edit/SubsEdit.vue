<template>
  <transition name="modal-fade">
    <div class="se-subsedit-modal-backdrop">
      <div class="se-subsedit-area">
        <div class="se-subsedit-body">

          <custom-confirm v-if="isCustomConfirmVisible" :params="customConfirmParams" @close="closeCustomConfirm" />

          <div class="se-grid-item-header">
            <div class="se-subsedit-header-song-name">«{{song.songName}}»</div>
            <div class="se-subsedit-header-song-description">«{{song.author}}», альбом: «{{song.album}}», год: {{song.year}}</div>
            <div class="se-voice">
              <label for="select-voice" class="se-label-for-check">Голос:</label>
              <select id="select-voice" v-model="currentVoice" style="width: 100px;">
                <option v-for="value in lstVoices" :key="value.value" :value="value.value" :label="value.text"/>
              </select>
            </div>
            <div class="se-tabs">
              <label for="select-tabs" class="se-label-for-check">Вариант табов:</label>
              <select id="select-tabs" v-model="indexTabsVariant" style="width: 100px;">
                <option v-for="value in lstIndexesTabsVariant" :key="value.value" :value="value.value" :label="value.text"/>
              </select>
            </div>
            <div class="se-sound">
              <label class="se-label-for-sound">Звук:</label>
              <button class="se-group-button" :class="soundButtonClass('voice')" type="button" value="voice" @click="setSound('voice')">Голос</button>
              <button class="se-group-button" :class="soundButtonClass('song')" type="button" value="song" @click="setSound('song')">Песня</button>
              <button class="se-group-button" :class="soundButtonClass('minus')" type="button" value="minus" @click="setSound('minus')">Минус</button>
              <button class="se-group-button" :class="soundButtonClass('drums')" type="button" value="minus" @click="setSound('drums')">Ударные</button>
              <button class="se-group-button" :class="soundButtonClass('bass')" type="button" value="minus" @click="setSound('bass')">Бас</button>
            </div>
            <div class="se-beat">
              <label class="se-label-for-sound">Такты:</label>
              <button class="se-group-button" :class="beatButtonClass(1)" type="button" value="1" @click="setBeat(1)">1/1</button>
              <button class="se-group-button" :class="beatButtonClass(2)" type="button" value="2" @click="setBeat(2)">1/2</button>
              <button class="se-group-button" :class="beatButtonClass(4)" type="button" value="3" @click="setBeat(4)">1/4</button>
              <button class="se-group-button" :class="beatButtonClass(8)" type="button" value="8" @click="setBeat(8)">1/8</button>
              <button class="se-group-button" :class="beatButtonClass(16)" type="button" value="16" @click="setBeat(16)">1/16</button>
              <button class="se-group-button" :class="beatButtonClass(32)" type="button" value="32" @click="setBeat(32)">1/32</button>
            </div>
            <div class="se-markers">
              <button class="se-group-button" :value="isShowMarkerTypeSyllables" :class="merkerButtonClass(isShowMarkerTypeSyllables)" type="button" @click="onOffShowMarkerType('syllables')">syll</button>
              <button class="se-group-button" :value="isShowMarkerTypeSetting" :class="merkerButtonClass(isShowMarkerTypeSetting)" type="button" @click="onOffShowMarkerType('setting')">sett</button>
              <button class="se-group-button" :value="isShowMarkerTypeEndofline" :class="merkerButtonClass(isShowMarkerTypeEndofline)" type="button" @click="onOffShowMarkerType('endofline')">eol</button>
              <button class="se-group-button" :value="isShowMarkerTypeEndofsyllable" :class="merkerButtonClass(isShowMarkerTypeEndofsyllable)" type="button" @click="onOffShowMarkerType('endofsyllable')">eos</button>
              <button class="se-group-button" :value="isShowMarkerTypeNewline" :class="merkerButtonClass(isShowMarkerTypeNewline)" type="button" @click="onOffShowMarkerType('newline')">nl</button>
              <button class="se-group-button" :value="isShowMarkerTypeUnmute" :class="merkerButtonClass(isShowMarkerTypeUnmute)" type="button" @click="onOffShowMarkerType('unmute')">unmute</button>
              <button class="se-group-button" :value="isShowMarkerTypeNote" :class="merkerButtonClass(isShowMarkerTypeNote)" type="button" @click="onOffShowMarkerType('note')">note</button>
              <button class="se-group-button" :value="isShowMarkerTypeChord" :class="merkerButtonClass(isShowMarkerTypeChord)" type="button" @click="onOffShowMarkerType('chord')">chord</button>
              <button class="se-group-button" :value="isShowMarkerTypeBeat" :class="merkerButtonClass(isShowMarkerTypeBeat)" type="button" @click="onOffShowMarkerType('beat')">beat</button>
              <button class="se-group-button" :value="isShowMarkerTypeEOLCH" :class="merkerButtonClass(isShowMarkerTypeEOLCH)" type="button" @click="onOffShowMarkerType('eolch')">eolch</button>
              <button class="se-group-button" :value="isShowMarkerTypeEOCH" :class="merkerButtonClass(isShowMarkerTypeEOCH)" type="button" @click="onOffShowMarkerType('eoch')">eoch</button>
              <button class="se-group-button" :value="isShowMarkerTypeNLCH" :class="merkerButtonClass(isShowMarkerTypeNLCH)" type="button" @click="onOffShowMarkerType('nlch')">nlch</button>
              <button class="se-group-button" :value="isShowMarkerTypeEOLN" :class="merkerButtonClass(isShowMarkerTypeEOLN)" type="button" @click="onOffShowMarkerType('eoln')">eoln</button>
              <button class="se-group-button" :value="isShowMarkerTypeEON" :class="merkerButtonClass(isShowMarkerTypeEON)" type="button" @click="onOffShowMarkerType('eon')">eon</button>
              <button class="se-group-button" :value="isShowMarkerTypeNLN" :class="merkerButtonClass(isShowMarkerTypeNLN)" type="button" @click="onOffShowMarkerType('nln')">nln</button>
            </div>
          </div>
          <div class="se-grid-item-waveform">
            <div class="se-item-left-waveform">
              <div class="se-item-left-label-and-input">
                <div class="se-item-left-label">Type:</div>
                <input class="se-item-left-input-field" v-model="currentMarker.markertype" @focus="setEditMode(false)" @blur="setEditMode(true)">
              </div>
              <div class="se-item-left-label-and-input">
                <div class="se-item-left-label">Time:</div>
                <input class="se-item-left-input-field" v-model="currentMarker.time" @focus="setEditMode(false)" @blur="setEditMode(true)">
              </div>
              <div class="se-item-left-label-and-input">
                <div class="se-item-left-label">Label:</div>
                <input class="se-item-left-input-field" v-model="currentMarker.label" @focus="setEditMode(false)" @blur="setEditMode(true)">
              </div>
              <div class="se-item-left-label-and-input">
                <div class="se-item-left-label">Note:</div>
                <input class="se-item-left-input-field" v-model="currentMarker.note" @focus="setEditMode(false)" @blur="setEditMode(true)">
              </div>
              <div class="se-item-left-label-and-input">
                <div class="se-item-left-label">Chord:</div>
                <input class="se-item-left-input-field" v-model="currentMarker.chord" @focus="setEditMode(false)" @blur="setEditMode(true)">
              </div>
              <div class="se-item-left-label-and-input">
                <div class="se-item-left-label">String|Lad:</div>
                <input class="se-item-left-input-field" v-model="currentMarker.stringlad" @focus="setEditMode(false)" @blur="setEditMode(true)">
              </div>
              <div class="se-item-left-label-and-input">
                <button class="se-group-button" :class="se-lockladButtonClass()" type="button" @click="onOffLocklad()">locklad</button>
<!--                <div class="se-item-left-label">LockLad:</div>-->
<!--                <input class="se-item-left-input-field" v-model="currentMarker.locklad" @focus="setEditMode(false)" @blur="setEditMode(true)">-->
              </div>
            </div>
            <div class="se-item-waveform" id="waveform"></div>
            <div class="se-item-right-waveform"></div>
          </div>
          <div class="se-grid-item-slider">
            <input class="se-item-slider-zoom" id="slider-zoom" data-action="zoom" type="range" min="12" max="1000" value="12">
            <input class="se-item-slider-volume" id="slider-volume" data-action="volume" type="range" step="0.05" min="0" max="1" value="1">
          </div>
          <div class="se-grid-item-controls">
            <div class="se-group-controls-region-buttons">
              <button v-if="isRegionMode" class="se-group-button" type="button" @click="setRegionMode(!isRegionMode)">
                <img alt="Выключить регион" class="se-icon-40" title="Выключить регион" src="../../../assets/svg/icon_region_mode_on.svg">
              </button>
              <button v-else class="se-group-button" type="button" @click="setRegionMode(!isRegionMode)">
                <img alt="Включить регион" class="se-icon-40" title="Включить регион" src="../../../assets/svg/icon_region_mode_off.svg">
              </button>
              <button v-if="isMoveMode" class="se-group-button" type="button" @click="setMoveMode(!isMoveMode)" :disabled="!isRegionMode">
                <img alt="Выключить режим сдвига маркеров" class="se-icon-40" title="Выключить режим сдвига маркеров" src="../../../assets/svg/icon_markers_in_region_move_on.svg">
              </button>
              <button v-else class="se-group-button" type="button" @click="setMoveMode(!isMoveMode)" :disabled="!isRegionMode">
                <img alt="Включить режим сдвига маркеров" class="se-icon-40" title="Включить режим сдвига маркеров" src="../../../assets/svg/icon_markers_in_region_move_off.svg">
              </button>
              <button class="se-group-button" type="button" @click="pasteMarkersInRegionToNewPlace" :disabled="!isRegionMode">
                <img alt="Вставить маркеры из региона в новое место" class="se-icon-40" title="Вставить маркеры из региона в новое место" src="../../../assets/svg/icon_markers_in_region_paste.svg">
              </button>
              <button class="se-group-button" type="button" @click="deleteMarkersInRegion" :disabled="!isRegionMode">
                <img alt="далить маркеры из региона" class="se-icon-40" title="Удалить маркеры из региона" src="../../../assets/svg/icon_markers_in_region_delete.svg">
              </button>
            </div>
            <div class="se-group-edit-play-speed-buttons">
              <div class="se-group-edit-speed-buttons">
                <label class="se-label-for-group-edit-speed-buttons">Edit:</label>
                <button class="se-group-button" :class="editSpeedButtonClass(0.3)" type="button" value="0.3" @click="setEditSpeed(0.3)">0.3</button>
                <button class="se-group-button" :class="editSpeedButtonClass(0.4)" type="button" value="0.4" @click="setEditSpeed(0.4)">0.4</button>
                <button class="se-group-button" :class="editSpeedButtonClass(0.5)" type="button" value="0.5" @click="setEditSpeed(0.5)">0.5</button>
                <button class="se-group-button" :class="editSpeedButtonClass(0.75)" type="button" value="0.75" @click="setEditSpeed(0.75)">0.75</button>
                <button class="se-group-button" :class="editSpeedButtonClass(1.0)" type="button" value="1.0" @click="setEditSpeed(1.0)">1.0</button>
                <div class="se-edit-mode" :class="editModeButtonClass()"></div>
                <button class="se-group-button" :class="editModeTypeButtonClass('syllables')" type="button" value="syllables" @click="setEditModeType('syllables')">syll</button>
                <button class="se-group-button" :class="editModeTypeButtonClass('note')" type="button" value="note" @click="setEditModeType('note')">note</button>
                <button class="se-group-button" :class="editModeTypeButtonClass('chord')" type="button" value="chord" @click="setEditModeType('chord')">syll</button>
              </div>
              <div class="se-group-play-speed-buttons">
                <label class="se-label-for-group-play-speed-buttons">Play:</label>
                <button class="se-group-button" :class="playSpeedButtonClass(0.5)" type="button" value="0.5" @click="setPlaySpeed(0.5)">0.5</button>
                <button class="se-group-button" :class="playSpeedButtonClass(0.75)" type="button" value="0.75" @click="setPlaySpeed(0.75)">0.75</button>
                <button class="se-group-button" :class="playSpeedButtonClass(1.0)" type="button" value="1.0" @click="setPlaySpeed(1.0)">1.0</button>
                <button class="se-group-button" :class="playSpeedButtonClass(1.25)" type="button" value="1.25" @click="setPlaySpeed(1.25)">1.25</button>
                <button class="se-group-button" :class="playSpeedButtonClass(1.5)" type="button" value="1.5" @click="setPlaySpeed(1.5)">1.5</button>
                <button class="se-group-button" :class="playSpeedButtonClass(1.75)" type="button" value="1.75" @click="setPlaySpeed(1.75)">1.75</button>
                <button class="se-group-button" :class="playSpeedButtonClass(2.0)" type="button" value="2.0" @click="setPlaySpeed(2.0)">2.0</button>
                <button class="se-group-button" :class="playSpeedButtonClass(2.25)" type="button" value="2.25" @click="setPlaySpeed(2.25)">2.25</button>
                <button class="se-group-button" :class="playSpeedButtonClass(2.5)" type="button" value="2.5" @click="setPlaySpeed(2.5)">2.5</button>
                <button class="se-group-button" :class="playSpeedButtonClass(2.75)" type="button" value="2.75" @click="setPlaySpeed(2.75)">2.75</button>
                <button class="se-group-button" :class="playSpeedButtonClass(3.0)" type="button" value="3.0" @click="setPlaySpeed(3.0)">3.0</button>
              </div>
            </div>
            <div class="se-group-controls-markers-buttons">
              <div class="se-group-control-buttons">
                <button class="se-group-button" :class="pressedButtonClass(pressedBL)" @mousedown.left="pressedBL = true" @mouseup.left="pressedBL = false">
                  <img alt="previous marker" class="se-icon-previous-marker" title="[" src="../../../assets/svg/icon_previous_marker.svg">
                </button>
                <button class="se-group-button" :class="pressedButtonClass(pressedZ)" @mousedown.left="pressedZ = true" @mouseup.left="pressedZ = false">
                  <img alt="fast-fast backward" class="se-icon-fast-fast-backward" title="Z" src="../../../assets/svg/icon_fast_fast_backward.svg">
                </button>
                <button class="se-group-button" :class="pressedButtonClass(pressedA)" @mousedown.left="pressedA = true" @mouseup.left="pressedA = false">
                  <img alt="fast backward" class="se-icon-fast-backward" title="A" src="../../../assets/svg/icon_fast_backward.svg">
                </button>
                <button class="se-group-button" :class="pressedButtonClass(pressedQ)" @mousedown.left="pressedQ = true" @mouseup.left="pressedQ = false">
                  <img alt="step backward" class="se-icon-step-backward" title="Q" src="../../../assets/svg/icon_step_backward.svg">
                </button>
                <button class="se-group-button" :class="playPauseButtonClass(isPlaying)" @click="playPause" title="X" >
                  <img alt="play-pause" class="se-icon-play-pause" src="../../../assets/svg/icon_play_pause.svg">
                </button>
                <button class="se-group-button" :class="pressedButtonClass(pressedE)" @mousedown.left="pressedE = true" @mouseup.left="pressedE = false">
                  <img alt="step forward" class="se-icon-step-forward" title="E" src="../../../assets/svg/icon_step_forward.svg">
                </button>
                <button class="se-group-button" :class="pressedButtonClass(pressedD)" @mousedown.left="pressedD = true" @mouseup.left="pressedD = false">
                  <img alt="fast forward" class="se-icon-fast-forward" title="D" src="../../../assets/svg/icon_fast_forward.svg">
                </button>
                <button class="se-group-button" :class="pressedButtonClass(pressedC)" @mousedown.left="pressedC = true" @mouseup.left="pressedC = false">
                  <img alt="fast-fast forward" class="se-icon-fast-fast-forward" title="C" src="../../../assets/svg/icon_fast_fast_forward.svg">
                </button>
                <button class="se-group-button" :class="pressedButtonClass(pressedBR)" @mousedown.left="pressedBR = true" @mouseup.left="pressedBR = false">
                  <img alt="next marker" class="se-icon-next-marker" title="]" src="../../../assets/svg/icon_next_marker.svg">
                </button>
              </div>
              <div class="se-group-markers-buttons">
                <button class="se-group-button" :class="pressedButtonClass(pressedW)" @mousedown.left="pressedW = true" @mouseup.left="pressedW = false">
                  <img alt="add marker" class="se-icon-add-marker" title="W" src="../../../assets/svg/icon_add_marker_orange.svg">
                </button>
                <button class="se-group-button" :class="pressedButtonClass(pressedS)" @mousedown.left="pressedS = true" @mouseup.left="pressedS = false">
                  <img alt="delete marker" class="se-icon-delete-marker" title="S" src="../../../assets/svg/icon_delete_marker.svg">
                </button>
                <button class="se-group-button" :class="pressedButtonClass(pressed1)" @mousedown.left="pressed1 = true" @mouseup.left="pressed1 = false">
                  <img alt="end of line marker" class="se-icon-end-of-line-marker" title="1" src="../../../assets/svg/icon_add_marker_red.svg">
                </button>
                <button class="se-group-button" :class="pressedButtonClass(pressed2)" @mousedown.left="pressed2 = true" @mouseup.left="pressed2 = false">
                  <img alt="end of line marker" class="se-icon-end-of-line-marker" title="2" src="../../../assets/svg/icon_add_marker_red.svg">
                </button>
                <button class="se-group-button" :class="pressedButtonClass(pressed3)" @mousedown.left="pressed3 = true" @mouseup.left="pressed3 = false">
                  <img alt="end of line and add marker" class="se-icon-end-of-line-and-add-marker" title="3" src="../../../assets/svg/icon_add_marker_red_orange.svg">
                </button>
                <button class="se-group-button" :class="pressedButtonClass(pressed4)" @mousedown.left="pressed4 = true" @mouseup.left="pressed4 = false">
                  <img alt="new line marker" class="se-icon-new-line-marker" title="4" src="../../../assets/svg/icon_add_marker_magenta.svg">
                </button>
                <button class="se-group-button" :class="pressedButtonClass(pressed0)" @mousedown.left="pressed0 = true" @mouseup.left="pressed0 = false">
                  <img alt="mute marker" class="se-icon-mute-marker" title="0" src="../../../assets/svg/icon_add_marker_yellow.svg">
                </button>
                <button class="se-group-button" :class="pressedButtonClass(pressedT)" @mousedown.left="pressedT = true" @mouseup.left="pressedT = false">
                  <img alt="group 0 marker" class="se-icon-group0-marker" title="T" src="../../../assets/svg/icon_add_marker_blue_white.svg">
                </button>
                <button class="se-group-button" :class="pressedButtonClass(pressedY)" @mousedown.left="pressedY = true" @mouseup.left="pressedY = false">
                  <img alt="group 1 marker" class="se-icon-group1-marker" title="Y" src="../../../assets/svg/icon_add_marker_yellow_white.svg">
                </button>
                <button class="se-group-button" :class="pressedButtonClass(pressedU)" @mousedown.left="pressedU = true" @mouseup.left="pressedU = false">
                  <img alt="group 2 marker" class="se-icon-group2-marker" title="U" src="../../../assets/svg/icon_add_marker_aqua_white.svg">
                </button>
                <button class="se-group-button" :class="pressedButtonClass(pressedI)" @mousedown.left="pressedI = true" @mouseup.left="pressedI = false">
                  <img alt="group 3 marker" class="se-icon-group3-marker" title="I" src="../../../assets/svg/icon_add_marker_green_white.svg">
                </button>
                <button class="se-group-button" :class="pressedButtonClass(pressedP)" @mousedown.left="pressedP = true" @mouseup.left="pressedP = false">
                  <img alt="group 4 marker" class="se-icon-group4-marker" title="P" src="../../../assets/svg/icon_add_marker_gray_white.svg">
                </button>
                <button class="se-group-button" :class="pressedButtonClass(pressedO)" @mousedown.left="pressedO = true" @mouseup.left="pressedO = false">
                  <img alt="comment marker" class="se-icon-comment-marker" title="O" src="../../../assets/svg/icon_add_marker_magenta_white.svg">
                </button>
              </div>
            </div>
            <div class="se-group-actions-buttons">
              <button class="se-group-button" type="button" @click="doMarkersDec">
                <img alt="diffbeatsdec" class="se-icon-40" title="Сдвинуть маркеры влево" src="../../../assets/svg/icon_move_markers_left.svg">
              </button>
              <button class="se-group-button" type="button" @click="doMarkersInc">
                <img alt="diffbeatsinc" class="se-icon-40" title="Сдвинуть маркеры вправо" src="../../../assets/svg/icon_move_markers_right.svg">
              </button>
              <button class="se-group-button" type="button" @click="addAccent">
                <img alt="erase markers" class="se-icon-40" title="Добавить ударение" src="../../../assets/svg/icon_accent.svg">
              </button>
              <button class="se-group-button" type="button" @click="eraseMarkers">
                <img alt="erase markers" class="se-icon-40" title="Очистить маркеры" src="../../../assets/svg/icon_erase.svg">
              </button>
              <button class="se-group-button" type="button" @click="addWordToDict">
                <img alt="add to dict" class="se-icon-40" title="Добавить слово в словарь" src="../../../assets/svg/icon_dict_add_e.svg">
              </button>
              <button class="se-group-button" type="button" @click="removeWordFromDict">
                <img alt="remove from dict" class="se-icon-40" title="Удалить слово из словаря" src="../../../assets/svg/icon_dict_remove_e.svg">
              </button>
<!--              <button class="se-group-button" type="button" @click="doSearchText">-->
<!--                <img alt="search text" class="se-icon-40" title="Найти в Интернете текст песни" src="../../../assets/svg/icon_search_text.svg">-->
<!--              </button>-->
              <button class="se-group-button" type="button" @click="doReplaceText">
                <img alt="replace text" class="se-icon-40" title="Произвести замену текста согласно правилам" src="../../../assets/svg/icon_replace_text.svg">
              </button>
<!--              <button class="se-group-button" type="button" @click="doBpmAdd">-->
<!--                <img alt="add bpm" class="se-icon-40" title="Добавить BPM из файла sheetsage" src="../../../assets/svg/icon_bpm.svg">-->
<!--              </button>-->
<!--              <button class="se-group-button" type="button" @click="doChordsAdd">-->
<!--                <img alt="add chords" class="se-icon-40" title="Добавить аккорды из файла sheetsage" src="../../../assets/svg/icon_chords_add.svg">-->
<!--              </button>-->
<!--              <button class="se-group-button" type="button" @click="doChordsDel">-->
<!--                <img alt="clear chords" class="se-icon-40" title="Очистить аккорды" src="../../../assets/svg/icon_chords_del.svg">-->
<!--              </button>-->
<!--              <button class="se-group-button" type="button" @click="doDiffBeatsInc">-->
<!--                <img alt="diffbeatsinc" class="se-icon-40" title="Сдвинуть аккорды вправо" src="../../../assets/svg/icon_diffbeatsinc.svg">-->
<!--              </button>-->
<!--              <button class="se-group-button" type="button" @click="doDiffBeatsDec">-->
<!--                <img alt="diffbeatsdec" class="se-icon-40" title="Сдвинуть аккорды влево" src="../../../assets/svg/icon_diffbeatsdec.svg">-->
<!--              </button>-->
              <button class="se-group-button" type="button" @click="save">
                <img alt="saveSong" class="se-icon-40" title="Save" src="../../../assets/svg/icon_save.svg">
              </button>
            </div>

          </div>
          <textarea id="editor" class="se-grid-item-sourcetext" v-model="sourceText" @focus="setEditMode(false)" @blur="setEditMode(true)">

          </textarea>
          <div class="se-grid-item-tail">
            <div class="se-tail" v-html="tail"></div>
          </div>
          <div class="se-grid-item-wrapper-text">
            <b-tabs>
              <b-tab title="Текст" active>
                <div class="se-grid-item-text" v-html="textFormatted"></div>
              </b-tab>
              <b-tab title="Ноты">
                <div class="se-grid-item-notes" v-html="notesFormatted"></div>
              </b-tab>
              <b-tab title="Аккорды">
                <div class="se-grid-item-chords" v-html="chordsFormatted"></div>
              </b-tab>
            </b-tabs>
          </div>

          <div class="se-grid-item-footer">
            <button type="button" class="se-btn-close" @click="close">Выход</button>
          </div>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>

import WaveSurfer from 'wavesurfer.js'
import Hover from 'wavesurfer.js/dist/plugins/hover.esm.js'
import RegionsPlugin from 'wavesurfer.js/dist/plugins/regions.esm.js'
import TimelinePlugin from 'wavesurfer.js/dist/plugins/timeline.esm.js'
import Minimap from 'wavesurfer.js/dist/plugins/minimap.esm.js'
import CustomConfirm from "../../Common/CustomConfirm.vue";
// import {TabsPlugin} from 'bootstrap-vue'
// import Vue from "vue";
// Vue.use(TabsPlugin)
// import ZoomPlugin from 'wavesurfer.js/dist/plugins/zoom.esm.js'
// import { isStringContainThisSymbols } from '@/lib/utils'
export default {
  name: "SubsEdit",
  components: {
    CustomConfirm
  },
  data() {
    return {
      currentVoice: 0,
      currentTime: 0,
      currentSyllablesIndex: 0,
      currentMarkersIndex: 0,
      indexTabsVariant: 0,
      currentMarker: {
        markertype: '',
        time: '',
        label: '',
        note: '',
        chord: '',
        stringlad: '',
        locklad: '',
        position: '',
        color: ''
      },
      visibleStartTime: -1,
      visibleEndTime: -1,
      duration: 0,
      tail: '',
      dataVoices: this.voices,
      sound: 'voice',
      beat: 4,
      sourceText: '',
      textFormatted: '',
      notesFormatted: '',
      chordsFormatted: '',
      sourceSyllables: [],
      loadedMarkers: [],
      sourceMarkers: [],
      regionMarker: undefined,
      regionMarkerStart: undefined,
      regionMarkerEnd: undefined,
      editSpeed: 0.75,
      editModeType: 'syllables',
      playSpeed: 1.0,
      isEditMode: false,
      isRegionMode: false,
      isMoveMode: false,
      isPlaying: false,
      pressedMidiNotes: new Set(),
      pressedX: false, // play/pause, X
      pressedA: false, // play назад, A
      pressedD: false, // play вперёд, D
      pressedQ: false, // шаг назад, Q
      pressedE: false, // шаг вперёд, E
      pressedZ: false, // быстро назад, Z
      pressedC: false, // быстро вперёд, C
      pressedBL: false, // предыдущий маркер, [
      pressedBR: false, // следующий маркер, ]
      pressedComma: false, // предыдущий слоговый маркер, <
      pressedPeriod: false, // следующий слоговый маркер, >
      pressedS: false, // удалить маркер, S
      pressedW: false, // оранжевый маркер "слог", W
      pressed1: false, // красно-оранжевый маркер "конец слога", 1
      pressed2: false, // красный маркер "конец строки", 2
      pressed3: false, // красно-оранжевый маркер "конец строки + слог", 3
      pressed4: false, // малиновый маркер "новая строка", 4
      pressed5: false, // "конец строки + новая строка + слог", 5
      pressed6: false, // "аккорд", 6
      pressed0: false, // жёлтый маркер "приглушение", 0
      pressedT: false, // сине-белый маркер "группа 0", T
      pressedY: false, // жёлто-белый маркер "группа 1", Y
      pressedU: false, // голубо-белый маркер "группа 2", U
      pressedI: false, // зелёно-белый маркер "группа 3", I
      pressedP: false, // серо-белый маркер "группа 4", P
      pressedO: false, // малиново-белый маркер "комментарий", O
      ws: undefined,
      wsRegions: undefined,
      sliderZoom: undefined,
      sliderVolume: undefined,
      intervalSkipBackward: undefined,
      intervalSkipForward: undefined,
      intervalPressZ: undefined,
      intervalPressC: undefined,
      intervalPressBL: undefined,
      intervalPressBR: undefined,
      intervalPressComma: undefined,
      intervalPressPeriod: undefined,
      firstVisibleMarkerTime: 0,
      lastVisibleMarkerTime: 0,
      activeRegion: null,
      isCustomConfirmVisible: false,
      customConfirmParams: undefined,
      selectedText: '',
      midi: null,
      isShowMarkerTypeSyllables: true,
      isShowMarkerTypeSetting: true,
      isShowMarkerTypeEndofline: true,
      isShowMarkerTypeEndofsyllable: true,
      isShowMarkerTypeNewline: true,
      isShowMarkerTypeUnmute: true,
      isShowMarkerTypeNote: true,
      isShowMarkerTypeChord: true,
      isShowMarkerTypeBeat: true,
      isShowMarkerTypeEOLCH: true,
      isShowMarkerTypeEOCH: true,
      isShowMarkerTypeNLCH: true,
      isShowMarkerTypeEOLN: true,
      isShowMarkerTypeEON: true,
      isShowMarkerTypeNLN: true,
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
      async handler() {
        if (this.currentVoice === this.dataVoices.length) {
          this.dataVoices.push({
            text: '',
            markers: [],
            syllables: []
          })
        }
        this.sourceText = await this.$store.getters.getSourceText(this.currentVoice);
        this.loadedMarkers = await this.$store.getters.getSourceMarkers(this.currentVoice);
        this.sourceMarkers = [];
        if (this.loadedMarkers.length > 0 && this.sourceMarkers.length === 0) {
          this.wsRegions.clearRegions();
          for (let index = 0; index < this.loadedMarkers.length; index++) {
            let marker = Object.assign({} , this.loadedMarkers[index]);
            if (
                ( marker.markertype === 'setting' && marker.label && (
                    marker.label === 'COMMENT| '
                ))
                ||
                ( marker.markertype === 'syllables' && marker.label && (marker.label.trim() === '') )
            ) {
              console.log('ignored')
            } else {
              marker.region = this.createRegionMarker(marker);
              this.sourceMarkers.push(marker)
            }
          }
          this.createBeatMarkers();
        }
      }
    },
    sound: {
      handler () {
        this.loadSong();
      }
    },
    beat: {
      handler () {
        this.createBeatMarkers();
      }
    },
    playSpeed: {
      handler () {
        this.ws.setPlaybackRate(this.playSpeed);
      }
    },
    sourceSyllables: {
      handler () {
        this.updateMarkersBySyllables();
      }
    },
    sourceMarkers: {
      handler () {
        this.currentSyllablesIndex = this.getCurrentSyllablesIndex;
        this.currentMarkersIndex = this.getCurrentMarkersIndex;
        this.textFormatted = this.getFormattedText;
        this.notesFormatted = this.getFormattedNotes;
        this.chordsFormatted = this.getFormattedChords;
      }
    },
    sourceText: {
      handler () {
        this.sourceSyllables = this.getSyllables;
        this.updateMarkersBySyllables();
        this.tail = this.getTail;
        this.textFormatted = this.getFormattedText;
        this.notesFormatted = this.getFormattedNotes;
        this.chordsFormatted = this.getFormattedChords;
      }
    },
    currentTime: {
      handler () {
        this.currentSyllablesIndex = this.getCurrentSyllablesIndex;
        this.currentMarkersIndex = this.getCurrentMarkersIndex;
      }
    },
    currentSyllablesIndex: {
      handler () {
        this.tail = this.getTail;
        this.textFormatted = this.getFormattedText;
        this.notesFormatted = this.getFormattedNotes;
        this.chordsFormatted = this.getFormattedChords;
      }
    },
    currentMarkersIndex: {
      handler () {
        if (this.sourceMarkers === undefined || this.currentMarkersIndex === undefined) {
          this.currentMarker = this.dummyMarker;
        } else {
          if (this.sourceMarkers[this.currentMarkersIndex] === undefined) {
            this.currentMarker = this.dummyMarker;
          } else {
            this.currentMarker = this.sourceMarkers[this.currentMarkersIndex];
          }
        }
      }
    },
    currentMarker: {
      handler () {
        this.currentMarker.region.setContent(this.getRegionContentFromMarker(this.currentMarker));
        this.tail = this.getTail;
        this.textFormatted = this.getFormattedText;
        this.notesFormatted = this.getFormattedNotes;
        this.chordsFormatted = this.getFormattedChords;
        this.sourceMarkers.splice(0,1,this.sourceMarkers[0]);
      }
    },
    indexTabsVariant: {
      handler () {
        this.notesFormatted = this.getFormattedNotes;
      }
    },
    pressedX: {
      handler () {
        if (this.pressedX) {
          if (!this.ws.isPlaying()) {
            this.ws.play();
          } else {
            this.ws.pause();
          }
        }
      }
    },
    pressedA: {
      handler () {
        if (!this.isEditMode) return;
        if (this.pressedA) {
          this.ws.setPlaybackRate(this.editSpeed);
          this.intervalSkipBackward = setInterval(() => {this.ws.skip(-this.editSpeed * 0.01);}, 10);
        } else {
          clearInterval(this.intervalSkipBackward);
          this.ws.setPlaybackRate(this.playSpeed);
          this.ws.pause();
        }
      }
    },
    pressedD: {
      handler () {
        if (!this.isEditMode) return;
        if (this.pressedD) {
          this.ws.setPlaybackRate(this.editSpeed);
          this.intervalSkipForward = setInterval(() => {
              if (!this.ws.isPlaying()) {
                this.ws.play();
              }
            }, 16);
        } else {
          clearInterval(this.intervalSkipForward);
          this.ws.setPlaybackRate(this.playSpeed);
          this.ws.pause();
        }
      }
    },
    pressedQ: {
      handler () {
        if (!this.isEditMode) return;
        if (this.pressedQ) {
          this.ws.skip(-0.01);
        }
      }
    },
    pressedE: {
      handler () {
        if (!this.isEditMode) return;
        if (this.pressedE) {
          this.ws.skip(0.01);
        }
      }
    },
    pressedZ: {
      handler () {
        if (!this.isEditMode) return;
        if (this.pressedZ) {
          this.intervalPressZ = setInterval(() => { this.ws.skip(-0.1); }, 50);
        } else {
          clearInterval(this.intervalPressZ);
        }
      }
    },
    pressedC: {
      handler () {
        if (!this.isEditMode) return;
        if (this.pressedC) {
          this.intervalPressC = setInterval(() => { this.ws.skip(0.1); }, 50);
        } else {
          clearInterval(this.intervalPressC);
        }
      }
    },
    pressedS: { handler () { if (!this.isEditMode) return; if (this.pressedS) { this.deleteMarker(); } } },
    pressedW: {
      handler () {
        if (!this.isEditMode) return;
        if (this.pressedW) {
          switch (this.editModeType) {
            case 'syllables':
              this.addMarker('syllables');
              break;
            case 'note':
              this.addMarker('note', '_♪_');
              break;
            case 'chord':
              this.addMarker('chord');
              break;
          }
        }
      }
    },
    pressed1: {
      handler () {
        if (!this.isEditMode) return;
        if (this.pressed1) {
          switch (this.editModeType) {
            case 'syllables':
              this.addMarker('endofsyllable');
              break;
            case 'note':
              this.addMarker('eon');
              break;
            case 'chord':
              this.addMarker('eoch');
              break;
          }
        }
      }
    },
    pressed2: {
      handler () {
        if (!this.isEditMode) return;
        if (this.pressed2) {
          switch (this.editModeType) {
            case 'syllables':
              this.addMarker('endofline');
              break;
            case 'note':
              this.addMarker('eoln');
              break;
            case 'chord':
              this.addMarker('eolch');
              break;
          }
        }
      }
    },
    pressed3: { handler () { if (!this.isEditMode) return; if (this.pressed3) {
      // this.addMarker('syllables', '', true, true);
        switch (this.editModeType) {
          case 'syllables':
            this.addMarker('endofline', '', false, false);
            this.addMarker('syllables', '', true, false);
            break;
          case 'note':
            this.addMarker('eoln', '', false, false);
            this.addMarker('note',  '_♪_', true, false);
            break;
          case 'chord':
            this.addMarker('eolch', '', false, false);
            this.addMarker('chord', '', true, false);
            break;
        }
    } } },
    pressed4: {
      handler () {
        if (!this.isEditMode) return;
        if (this.pressed4) {
          switch (this.editModeType) {
            case 'syllables':
              this.addMarker('newline');
              break;
            case 'note':
              this.addMarker('nln');
              break;
            case 'chord':
              this.addMarker('nlch');
              break;
          }
        }
      }
    },
    pressed5: { handler () { if (!this.isEditMode) return; if (this.pressed5) {
        switch (this.editModeType) {
          case 'syllables':
            this.addMarker('endofline', '', false, false);
            this.addMarker('newline', '', true, false);
            this.addMarker('syllables', '', true, false);
            break;
          case 'note':
            this.addMarker('eoln', '', false, false);
            this.addMarker('nln', '', true, false);
            this.addMarker('note', '_♪_', true, false);
            break;
          case 'chord':
            this.addMarker('eolch', '', false, false);
            this.addMarker('nlch', '', true, false);
            this.addMarker('chord', '', true, false);
            break;
        }
      } } },

    pressed6: { handler () { if (!this.isEditMode) return; if (this.pressed6) { this.addMarker('chord', '', true, false); } } },
    pressed0: { handler () { if (!this.isEditMode) return; if (this.pressed0) { this.addMarker('unmute'); } } },
    pressedT: { handler () { if (!this.isEditMode) return; if (this.pressedT) { this.addMarker('setting', 'GROUP|0'); } } },
    pressedY: { handler () { if (!this.isEditMode) return; if (this.pressedY) { this.addMarker('setting', 'GROUP|1'); } } },
    pressedU: { handler () { if (!this.isEditMode) return; if (this.pressedU) { this.addMarker('setting', 'GROUP|2'); } } },
    pressedI: { handler () { if (!this.isEditMode) return; if (this.pressedI) { this.addMarker('setting', 'GROUP|3'); } } },
    pressedP: { handler () { if (!this.isEditMode) return; if (this.pressedP) { this.addMarker('setting', 'GROUP|4'); } } },
    pressedO: { handler () { if (!this.isEditMode) return; if (this.pressedO) { this.addSettingMarker(); } } },
    pressedBL: {
      handler () {
        if (!this.isEditMode) return;
        if (this.pressedBL) {
          this.goToPreviousMarker();
          this.intervalPressBL = setInterval(() => { this.goToPreviousMarker(); }, 100);
        }  else {
          clearInterval(this.intervalPressBL);
        }
      }
    },
    pressedBR: {
      handler () {
        if (!this.isEditMode) return;
        if (this.pressedBR) {
          this.goToNextMarker();
          this.intervalPressBR = setInterval(() => { this.goToNextMarker(); }, 100);
        }  else {
          clearInterval(this.intervalPressBR);
        }
      }
    },
    pressedComma: {
      handler () {
        if (!this.isEditMode) return;
        if (this.pressedComma) {
          switch (this.editModeType) {
            case 'syllables':
              this.goToPreviousMarker('syllables');
              this.intervalPressComma = setInterval(() => { this.goToPreviousMarker('syllables'); }, 100);
              break;
            case 'note':
              this.goToPreviousMarker('note');
              this.intervalPressComma = setInterval(() => { this.goToPreviousMarker('note'); }, 100);
              break;
            case 'chord':
              this.goToPreviousMarker('chord');
              this.intervalPressComma = setInterval(() => { this.goToPreviousMarker('chord'); }, 100);
              break;
          }
        }  else {
          clearInterval(this.intervalPressComma);
        }
      }
    },
    pressedPeriod: {
      handler () {
        if (!this.isEditMode) return;
        if (this.pressedPeriod) {
          switch (this.editModeType) {
            case 'syllables':
              this.goToNextMarker('syllables');
              this.intervalPressPeriod = setInterval(() => { this.goToNextMarker('syllables'); }, 100);
              break;
            case 'note':
              this.goToNextMarker('note');
              this.intervalPressPeriod = setInterval(() => { this.goToNextMarker('note'); }, 100);
              break;
            case 'chord':
              this.goToNextMarker('chord');
              this.intervalPressPeriod = setInterval(() => { this.goToNextMarker('chord'); }, 100);
              break;
          }
        }  else {
          clearInterval(this.intervalPressPeriod);
        }
      }
    },
    isEditMode: {
      handler () {
        if (this.isEditMode) {
          document.addEventListener('keydown', this.listenerKeyDown, false);
          document.addEventListener('keyup', this.listenerKeyUp, false);
        } else {
          document.removeEventListener('keydown', this.listenerKeyDown, false);
          document.removeEventListener('keyup', this.listenerKeyUp, false);
          clearInterval(this.intervalSkipBackward);
          clearInterval(this.intervalSkipForward);
          this.ws.setPlaybackRate(this.playSpeed);
          if (this.ws.isPlaying()) {
            this.ws.pause();
          }
        }
      }
    },
    markerTypesToShow: {
      handler () {
        this.redrawMarkers();
      }
    }
  },
  computed: {
    dummyMarker() {
      return {
        markertype: '',
        time: '',
        label: '',
        note: '',
        chord: '',
        position: '',
        color: ''
      }
    },
    markerTypesToShow() {
      let result = [];
      if (this.isShowMarkerTypeSyllables) { result.push('syllables') }
      if (this.isShowMarkerTypeSetting) { result.push('setting') }
      if (this.isShowMarkerTypeEndofline) { result.push('endofline') }
      if (this.isShowMarkerTypeEndofsyllable) { result.push('endofsyllable') }
      if (this.isShowMarkerTypeNewline) { result.push('newline') }
      if (this.isShowMarkerTypeUnmute) { result.push('unmute') }
      if (this.isShowMarkerTypeNote) { result.push('note') }
      if (this.isShowMarkerTypeChord) { result.push('chord') }
      if (this.isShowMarkerTypeBeat) { result.push('beat') }
      if (this.isShowMarkerTypeEOLN) { result.push('eoln') }
      if (this.isShowMarkerTypeEON) { result.push('eon') }
      if (this.isShowMarkerTypeNLN) { result.push('nln') }
      if (this.isShowMarkerTypeEOLCH) { result.push('eolch') }
      if (this.isShowMarkerTypeEOCH) { result.push('eoch') }
      if (this.isShowMarkerTypeNLCH) { result.push('nlch') }
      return result;
    },
    getFormattedChords() {

      const SPAN_STYLE_CHORD = `<span style="color: #00BFFF; font-family: monospace; font-size: 15px; font-style: normal; font-weight: bolder;">`
      const SPAN_STYLE_TEXT = `<span style="color: #FFFFFF; font-family: monospace; font-size: 15px; font-style: normal; font-weight: bolder;">`
      const SPAN_END = '</span>';
      const BR = '<br>';
      const markers = this.sourceMarkers;
      const countNotEmptyChords = markers.filter(marker => marker.chord).length;
      if (countNotEmptyChords === 0) return '';
      let wasBr = true;
      let lineChordsIsEmpty = true;
      let result = '';
      let lineChords = '';
      let lineText = '';
      let slide = 0;
      for (let i = 0; i < markers.length; i++) {
        const marker = markers[i];
        switch (marker.markertype) {
          case 'setting':
          case 'endofline':
          case 'eoln':
          case 'eolch':
          case 'nln':
          case 'newline': {
            if (!lineChordsIsEmpty) {
              result += lineChords + BR;
            }
            result += lineText + BR;
            lineChordsIsEmpty = true;
            lineChords = '';
            lineText = '';
            wasBr = true;
            break;
          }
          case 'chord':
          case 'syllables': {

            // Если слог
            let txt = '';
            let txtHtml = '';
            if (marker.markertype === 'chord') {
              if (marker.label === '' || !marker.label || marker.label === marker.chord) {
                txt = '♪  ';
                txtHtml = '♪&nbsp;&nbsp;';
              } else {
                txt = marker.label;
                txtHtml = txt.replaceAll(' ', '&nbsp;');
              }
            } else if (marker.markertype === 'syllables') {
              // Если в маркере не пустой лейбл (т.е. есть слог)
              if (marker.label) {
                txt = marker.label.replaceAll('_', ' '); // Заменяем подчеркивания на пробелы
                txtHtml = marker.label.replaceAll('_', '&nbsp;');
                // Если был перенос строки - инициализируем новые переменны (3 пробела + слог)
                if (wasBr) {
                  txt = '   ' + this.uppercaseFirstLetter(txt);
                  txtHtml = '&nbsp;&nbsp;&nbsp;' + this.uppercaseFirstLetter(marker.label).replaceAll('_', '&nbsp;');
                }
              } else {
                // Если в маркере пустой лейбл - пробел в тексте
                txt = ' ';
                txtHtml = '&nbsp;';
              }
            }

            let chord = '';
            let chordHtml = '';

            // Если в маркере есть аккорд
            if (marker.chord) {
              // Находим аккорд
              lineChordsIsEmpty = false;
              chord = marker.chord;
              chordHtml = chord;

              // Находим позицию гласной буквы в слоге (0 - если гласная первая или если её нет)
              let vowelPosition = 0;
              for (let i = 0; i < txt.length; i++) {
                if ('ЁУЕЫАОЭЯИЮёуеыаоэяиюEUIOAeuioaїієѣ♪'.includes(txt[i])) {
                  vowelPosition = i;
                  break;
                }
              }
              // Добавляем пробелы перед названием аккорда (по позиции гласной)
              for (let i = 0; i < vowelPosition; i++) {
                chord = ' ' + chord;
                chordHtml = '&nbsp;' + chordHtml;
              }

              // Если длина аккорда больше длины текста, то к слайдеру надо добавить кол-во символов разницы длины
              // А если меньше - добавить пробелы после аккорда
              let diff = txt.length - chord.length;
              if (diff < 0) {
                slide -= diff;
              } else if (diff > 0) {
                chord += ' '.repeat(diff);
                chordHtml += '&nbsp;'.repeat(diff);
              }
            } else {
              // Если в маркере нет аккорда - пробелы по длине текста
              // Если слайдер больше нуля
              if (slide > 0) {
                // Если слайдер меньше длинны текущего текста
                if (slide < txt.length ) {
                  // Текст аккорда должен состоять из пробелов по кол-ву "длина текста минус слайд", слайдер в ноль
                  chord = ' '.repeat(txt.length - slide);
                  chordHtml = '&nbsp;'.repeat(txt.length - slide);
                  slide = 0;
                } else if (slide === txt.length) {
                  // Текст аккорда пустой, слайдер в ноль
                  // Текст аккорда должен состоять из пробелов по кол-ву длины текста
                  chord = ' '.repeat(txt.length);
                  chordHtml = '&nbsp;'.repeat(txt.length);
                  slide = 0;
                } else {
                  // Текст аккорда пустой, уменьшаем слайдер на длину текста
                  chord = '';
                  chordHtml = '';
                  slide -= txt.length;
                }
              } else {
                // Текст аккорда должен состоять из пробелов по кол-ву длины текста
                chord = ' '.repeat(txt.length);
                chordHtml = '&nbsp;'.repeat(txt.length);
              }
            }
            lineChords += SPAN_STYLE_CHORD + chordHtml + SPAN_END;
            lineText += SPAN_STYLE_TEXT + txtHtml + SPAN_END;
            wasBr = false;

            break;
          }
          default: {break;}
        }
      }
      return result;
    },
    getFormattedNotes() {
      const stringsForAllNotesArray = this.getStringsForAllNotesInSong()
      if (stringsForAllNotesArray === undefined || stringsForAllNotesArray.length === 0) return '';
      const SPAN_STYLE_CURRENT = `<span style="color: #FF0000; font-family: monospace; font-size: 15px; font-style: normal; font-weight: bolder;">`
      const SPAN_STYLE_NOTE = `<span style="color: #00BFFF; font-family: monospace; font-size: 15px; font-style: normal; font-weight: bolder;">`
      const SPAN_STYLE_TABLINE = `<span style="color: #66BFFF; font-family: monospace; font-size: 15px; font-style: normal; font-weight: bolder;">`
      const SPAN_STYLE_TEXT = `<span style="color: #FFFFFF; font-family: monospace; font-size: 15px; font-style: normal; font-weight: bolder;">`
      const SPAN_STYLE_OCTAVE = `<span style="color: #444444; font-family: monospace; font-size: 15px; font-style: normal; font-weight: bolder;">`
      const SPAN_STYLE_DEFIS = `<span style="color: #666666; font-family: monospace; font-size: 15px; font-style: normal; font-weight: bolder;">`
      const SPAN_END = '</span>';
      const BR = '<br>';
      const stringsForAllNotes = stringsForAllNotesArray[this.indexTabsVariant];
      let stringsForAllNotesIndex = 0;
      const markers = this.sourceMarkers;
      let wasBr = true;
      let result = '';
      let lineNotes = '';
      let lineText = '';
      let strings = ['E‖⎼','B‖⎼','G‖⎼','D‖⎼','A‖⎼','e‖⎼'];
      let previousMarkerType = '';
      let currentMarkerType = '';
      for (let i = 0; i < markers.length; i++) {
        currentMarkerType = markers[i].markertype;
        if (i !== 0 ) {
          previousMarkerType = markers[i-1].markertype;
        }
        const marker = markers[i];
        switch (marker.markertype) {
          case 'setting': {
            if (previousMarkerType === 'note' && currentMarkerType === 'setting') {
              for (let j = 0; j < strings.length; j++) {
                const sn = strings[j];
                result += SPAN_STYLE_TABLINE + sn + '⎼‖' + SPAN_END + BR;
              }
              result += lineNotes + BR + lineText + BR + BR;
              lineNotes = '';
              lineText = '';
              wasBr = true;
            }
            break;
          }
          case 'endofline':
          case 'eoln':
          case 'nln':
          case 'newline': {
            if (!wasBr) {
              for (let j = 0; j < strings.length; j++) {
                const sn = strings[j];
                result += SPAN_STYLE_TABLINE + sn + '⎼‖' + SPAN_END + BR;
              }
              result += lineNotes + BR + lineText + BR + BR;
              lineNotes = '';
              lineText = '';
              wasBr = true;
            }
            break;
          }
          case 'note':
          case 'syllables': {

            if (previousMarkerType === 'note' && currentMarkerType === 'syllables') {
              if (!wasBr) {
                for (let j = 0; j < strings.length; j++) {
                  const sn = strings[j];
                  result += SPAN_STYLE_TABLINE + sn + '⎼‖' + SPAN_END + BR;
                }
                result += lineNotes + BR + lineText + BR + BR;
                lineNotes = '';
                lineText = '';
                wasBr = true;
              }
            }

            // Если слог
            let txt = '';
            let txtHtml = '';
            let endOfWord = true;
            // Если в маркере не пустой лейбл (т.е. есть слог)
            if (marker.label) {
              endOfWord = marker.label.endsWith('_'); // Это конец слова если слог заканчивается подчёркиванием
              txt = marker.label.replaceAll('_', ' '); // Заменяем подчеркивания на пробелы
              txtHtml = marker.label.replaceAll('_', '&nbsp;');
              // Если был перенос строки - инициализируем новые переменны (3 пробела + слог и начало струн)
              if (wasBr) {
                txt = '   ' + this.uppercaseFirstLetter(txt);
                txtHtml = '&nbsp;&nbsp;&nbsp;' + this.uppercaseFirstLetter(marker.label).replaceAll('_', '&nbsp;');
                strings = ['E‖⎼','B‖⎼','G‖⎼','D‖⎼','A‖⎼','e‖⎼'];
              }
            } else {
              // Если в маркере пустой лейбл - пробел в тексте
              txt = ' ';
              txtHtml = '&nbsp;';
            }
            let note = '';
            let noteHtml = '';
            let noteOctave = '';
            let stringNote = ['⎼⎼','⎼⎼','⎼⎼','⎼⎼','⎼⎼','⎼⎼']; // Выделяем по 2 черты на ноту на струне

            // Если в маркере есть нота
            if (marker.note) {
              // Находим ноту и октаву ноты
              const noteParts = marker.note.split('|');
              note = noteParts[0];
              noteHtml = noteParts[0];
              if (noteParts.length > 1) {
                noteOctave = noteParts[1];
              }
              const noteLength = note.length;
              // Находим номер струны и номер лада, подставляем их в массив stringNote
              const sn = marker.locklad ? [marker.stringlad.split('|')[0], marker.stringlad.split('|')[1]] : stringsForAllNotes[stringsForAllNotesIndex];
              marker.stringlad = sn[0] + '|' + sn[1];
              let stringIndex = sn[0];
              let lad = sn[1] + (sn[1] < 10 ? '⎼' : '');
              stringNote.splice(stringIndex, 1, lad);
              stringsForAllNotesIndex++;

              // Находим позицию гласной буквы в слоге (0 - если гласная первая или если её нет)
              let vowelPosition = 0;
              for (let i = 0; i < txt.length; i++) {
                if ('ЁУЕЫАОЭЯИЮёуеыаоэяиюEUIOAeuioaїієѣ♪'.includes(txt[i])) {
                  vowelPosition = i;
                  break;
                }
              }
              // Добавляем пробелы перед названием ноты (по позиции гласной)
              for (let i = 0; i < vowelPosition; i++) {
                note = ' ' + note;
                noteHtml = '&nbsp;' + noteHtml;
              }

              let diff = 0;
              if (wasBr) {
                diff = 3;
                wasBr = false;
              }
              for (let i = 0; i < vowelPosition - diff + (noteLength - 1); i++) {
                for (let j = 0; j < stringNote.length; j++) {
                  const lad = '⎼' + stringNote[j];
                  stringNote.splice(j, 1, lad);
                }
              }


            } else {
              // Если в маркере нет ноты - пробел в названии ноты и одна черта на ноту в каждой струне
              note = ' ';
              noteHtml = '&nbsp;';
              stringNote = ['⎼','⎼','⎼','⎼','⎼','⎼'];
              if (currentMarkerType === 'note' && wasBr) {
                wasBr = false;
              }
            }

            lineNotes += ((i === this.currentMarkersIndex) ? SPAN_STYLE_CURRENT : SPAN_STYLE_NOTE) + noteHtml + SPAN_END + SPAN_STYLE_OCTAVE + noteOctave + SPAN_END;
            lineText += ((i === this.currentMarkersIndex) ? SPAN_STYLE_CURRENT : SPAN_STYLE_TEXT) + txtHtml + SPAN_END;
            if (!endOfWord) {
              lineText += SPAN_STYLE_DEFIS + '-' + SPAN_END;
            }
            const lengthNote = note.length + noteOctave.length;
            const lengthText = txt.length + (endOfWord ? 0 : 1);
            if (lengthNote > lengthText) {
              lineText += ((i === this.currentMarkersIndex) ? SPAN_STYLE_CURRENT : SPAN_STYLE_TEXT);
              for (let i = 0; i < lengthNote-lengthText; i++) {
                lineText += '&nbsp;';
              }
              lineText += SPAN_END;
            } else if (lengthText > lengthNote) {
              lineNotes += ((i === this.currentMarkersIndex) ? SPAN_STYLE_CURRENT : SPAN_STYLE_NOTE);
              for (let i = 0; i < lengthText-lengthNote; i++) {
                lineNotes += '&nbsp;';
                for (let j = 0; j < stringNote.length; j++) {
                  const lad = stringNote[j] + '⎼';
                  stringNote.splice(j, 1, lad);
                }
              }
              lineNotes += SPAN_END;
            }
            for (let j = 0; j < strings.length; j++) {
              const sn = strings[j] + stringNote[j];
              strings.splice(j, 1, sn);
            }
            break;
          }
          default: {break;}
        }
      }
      return result;
    },
    getFormattedText() {
      const SPAN_STYLE_CURRENT = `<span style="color: #FF0000; font-size: 18px; font-style: normal; font-weight: bolder;">`
      const SPAN_STYLE_GROUP0 = `<span style="color: #FFFFFF; font-size: 18px; font-style: normal; font-weight: bolder;">`
      const SPAN_STYLE_GROUP1 = `<span style="color: #FFFF00; font-size: 18px; font-style: italic; font-weight: bolder;">`
      const SPAN_STYLE_GROUP2 = `<span style="color: #00BFFF; font-size: 18px; font-style: normal; font-weight: bolder;">`
      const SPAN_STYLE_GROUP3 = `<span style="color: #00FF00; font-size: 18px; font-style: italic; font-weight: bolder;">`
      const SPAN_STYLE_COMMENT = `<span style="color: #D2691E; font-size: 14px; font-style: italic; font-weight: bolder;">`
      const markers = this.sourceMarkers;
      let spanStyle = SPAN_STYLE_GROUP0;
      let spanStylePrev = spanStyle;
      let wasBr = true;
      let result = '';

      for (let i = 0; i < markers.length; i++) {
        const marker = markers[i];
        switch (marker.markertype) {
          case 'setting': {
            switch (marker.label) {
              case 'GROUP|0': { spanStyle = SPAN_STYLE_GROUP0; break; }
              case 'GROUP|1': { spanStyle = SPAN_STYLE_GROUP1; break; }
              case 'GROUP|2': { spanStyle = SPAN_STYLE_GROUP2; break; }
              case 'GROUP|3': { spanStyle = SPAN_STYLE_GROUP3; break; }
              case 'COMMENT| ': { result += '<br>'; break; }
              default : {
                if (marker.label.startsWith("COMMENT|")) {
                  let txt = marker.label.split("|")[1];
                  result += SPAN_STYLE_COMMENT;
                  result += this.uppercaseFirstLetter(txt.replaceAll('_', ' '));
                  result += '</span>';
                  result += '<br>';
                }
              }
            }
            break;
          }
          case 'endofline':
          case 'newline': {
            result += '<br>';
            wasBr = true;
            break;
          }
          case 'syllables': {
            if (i === this.currentMarkersIndex) {
              result += SPAN_STYLE_CURRENT;
            } else {
              result += spanStyle;
            }

            let txt = marker.label ? marker.label.replaceAll('_', ' ') : '';
            if (wasBr) {
              txt = this.uppercaseFirstLetter(txt);
              wasBr = false
            }
            result += txt;
            result += '</span>';
            break;
          }
          default: {break;} // unmute, note, chord, eolch, eoch, nlch, eoln, eon, nln...
        }
        if (spanStyle !== spanStylePrev) result += '<br>';
        spanStylePrev = spanStyle;
      }
      return result;
    },
    getTail() {
      const SPAN_STYLE_TAIL_CURR = `<span style="color: #0000FF; font-size: 48px; font-style: normal;">`;
      const SPAN_STYLE_TAIL_NEXT = `<span style="color: #FF0000; font-size: 48px; font-style: normal;">`;
      const SPAN_STYLE_TAIL_END = `<span style="color: #000000; font-size: 24px; font-style: normal;">`;
      let result = '';
      let textBegin = '';
      let textCurr = this.currentSyllablesIndex === this.sourceSyllables.length-1 && this.currentSyllablesIndex >= 0 ? this.sourceSyllables[this.sourceSyllables.length-1].replaceAll("_", " ") : '';
      let textNext = '';
      let textEnd = '';

      for (let i = this.currentSyllablesIndex+1 ; i < this.sourceSyllables.length; i++) {
        if (i === this.currentSyllablesIndex+1) {
          textNext += this.sourceSyllables[i].replaceAll("_", " ");
          if (i > 1) {
            textCurr = this.sourceSyllables[i-1].replaceAll("_", " ");
          }
        } else {
          textEnd += this.sourceSyllables[i];
        }
        if (textEnd.length > 40) break;
      }
      textEnd = textEnd.substring(0,40);
      for (let i = this.currentSyllablesIndex; i > 0; i--) {
        textBegin = this.sourceSyllables[i-1] + textBegin;
        if (textBegin.length > 40) break;
      }
      textBegin = textBegin.split("").reverse().join("").substring(0,40).split("").reverse().join("");
      if (textBegin !== '') {
        result = SPAN_STYLE_TAIL_END + textBegin.replaceAll("_", " ").trim() + '</span>';
      }
      if (textNext === '') {
        textNext = '~';
      }
      if (textCurr === '') {
        textCurr = '~';
      }
      result += SPAN_STYLE_TAIL_CURR + textCurr + '</span>';
      result += SPAN_STYLE_TAIL_NEXT + textNext + '</span>';
      if (textEnd !== '') {
        result += SPAN_STYLE_TAIL_END + textEnd.replaceAll("_", " ").trim() + '</span>';
      }
      return result;
    },
    getCurrentSyllablesIndex() {
      let markers = this.sourceMarkers.filter(item => item.markertype === 'syllables');
      const diff = 0.02;
      if (markers.length > 0 && this.currentTime < markers[0].time-diff) return -1;
      for (let i = 0; i < markers.length-1; i++) {
        let marker = markers[i];
        let nextMarker = markers[i+1];
        if (this.currentTime >= marker.time-diff && this.currentTime < nextMarker.time-diff) {
          return i;
        }
      }
      return markers.length-1
    },
    getCurrentMarkersIndex() {
      let markers = this.sourceMarkers;
      const diff = 0.02;
      if (markers.length > 0 && this.currentTime < markers[0].time-diff) return -1;
      for (let i = 0; i < markers.length-1; i++) {
        let marker = markers[i];
        let nextMarker = markers[i+1];
        if (this.currentTime >= marker.time-diff && this.currentTime < nextMarker.time-diff) {
          return i;
        }
      }
      return markers.length-1
    },
    voice() {
      return this.dataVoices.length ? this.dataVoices[this.currentVoice] : [];
    },
    text() {
      return this.voice ? this.voice.text : '';
    },
    markers() {
      return this.voice ? this.voice.markers : [];
    },
    getSyllables() {
      let result = [];
      let words = (this.sourceText.match(/\S+/ig) || []);
      for (let i = 0; i < words.length; i++) {
        const word = words[i];
        const syllables = word.replace(/[ЙЦКНГШЩЗХЪФВПРЛДЖЧСМТЬБQWRTYPSDFGHJKLZXCVBNM-]*[ЁУЕЫАОЭЯИЮEUIOAїієѣ][ЙЦКНГШЩЗХЪФВПРЛДЖЧСМТЬБQWRTYPSDFGHJKLZXCVBNM-]*?(?=[ЦКНГШЩЗХФВПРЛДЖЧСМТБQWRTYPSDFGHJKLZXCVBNM-]?[ЁУЕЫАОЭЯИЮEUIOAїієѣ]|[Й|Y][АИУЕОEUIOAїієѣ])/ig, "$& ").split(" ");
        if (syllables.length === 0) {
          result.push(word + '_')
        } else {
          for (let j = 0; j < syllables.length; j++) {
            let syllable = syllables[j];
            result.push(syllable + (j === syllables.length - 1 ? '_' : ''));
          }
        }
      }
      for (let i = 0; i < result.length; i++) {
        const word = result[i];
        let haveVowel = false;
        for (let j = 0; j < word.length; j++) {
          if ('ЁУЕЫАОЭЯИЮёуеыаоэяиюEUIOAeuioaїієѣ'.includes(word[j])) {
            haveVowel = true;
            break;
          }
        }
        if (!haveVowel) {
          if (i === result.length-1 || (word === '-_' && i !== 0)) {
            result[i-1] = result[i-1] + word;
            result.splice(i,1);
            i--;
          } else if (i < result.length-2) {
            result[i+1] = word + result[i+1];
            result.splice(i,1);
            i--;
          }

        }
      }
      return result;
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
    },
    lstIndexesTabsVariant() {
      let result = [];
      let stringsForAllNotesInSong = this.getStringsForAllNotesInSong();
      for (let i = 0; i < stringsForAllNotesInSong.length; i++) {
        result.push( { value: i, text: i + 1 } );
      }
      return result;
    }
  },
  beforeDestroy() {
    this.wsRegions = null;
    this.ws = null;
    this.dataVoices = null;
    this.sourceSyllables = null;
    this.sourceMarkers = null;
    this.sliderZoom = null;
    this.sliderVolume = null;
    this.intervalSkipBackward = null;
    this.intervalSkipForward = null;
    this.intervalPressZ = null;
    this.intervalPressC = null;
    this.intervalPressBL = null;
    this.intervalPressBR = null;
    this.intervalPressBR = null;
    this.customConfirmParams = null;
  },
  async mounted() {
    // Инициализируем Wavesurfer
    this.initWavesurfer();
    this.isEditMode = true;
    this.sourceText = await this.$store.getters.getSourceText(this.currentVoice);
    this.loadedMarkers = await this.$store.getters.getSourceMarkers(this.currentVoice);
    this.indexTabsVariant = await this.$store.getters.getIndexTabsVariant;

    // Навешиваем обработчики событий на Wavesurfer
    this.ws.on('play', () => { this.isPlaying = true; });
    this.ws.on('pause', () => { this.isPlaying = false; });
    this.ws.on('decode', () => {
      this.duration = this.ws.getDuration();
      if (this.visibleStartTime < 0) this.visibleStartTime = 0;
      if (this.visibleEndTime < 0) this.visibleEndTime = this.duration;
      if (this.loadedMarkers.length > 0 && this.sourceMarkers.length === 0) {
        this.wsRegions.clearRegions();
        for (let index = 0; index < this.loadedMarkers.length; index++) {
          let marker = Object.assign({} , this.loadedMarkers[index]);
          if (
              ( marker.markertype === 'setting' && marker.label && (
                  marker.label === 'COMMENT| '
              ))
              ||
              ( marker.markertype === 'syllables' && marker.label && (marker.label.trim() === '') )
          ) {
            console.log('ignored')
          } else {
            marker.region = this.createRegionMarker(marker);
            this.sourceMarkers.push(marker)
          }
        }
        this.createBeatMarkers();
      }
    });
    this.ws.on('timeupdate', currentTime => {
      if (this.currentTime !== currentTime) {
        this.currentTime = currentTime;
      }
      this.currentSyllablesIndex = this.getCurrentSyllablesIndex;
      this.currentMarkersIndex = this.getCurrentMarkersIndex;
    });
    this.ws.on('scroll', (visibleStartTime, visibleEndTime) => {
      this.visibleStartTime = visibleStartTime;
      this.visibleEndTime = visibleEndTime;
      // this.createMarkers();
    });
    // Инициализируем слайдеры и навешиваем обработчик события
    this.sliderZoom = document.getElementById('slider-zoom');
    this.sliderZoom.addEventListener('input', (e) => {
      const minPxPerSec = e.target.valueAsNumber;
      this.ws.zoom(minPxPerSec);
    });

    this.sliderVolume = document.getElementById('slider-volume');
    this.sliderVolume.addEventListener('input', (e) => {
      const volume = e.target.valueAsNumber;
      this.ws.setVolume(volume);
    });

    // this.wsRegions.enableDragSelection();

    // eslint-disable-next-line

    // this.wsRegions.on('region-update', (region, side) => {
    //   // Изменения в маркере-регионе
    //   console.log('region-update side region', side, region);
    //   console.log('region-update this.regionMarker.region', this.regionMarker.region);
    //   if (this.isRegionMode && region.id === this.regionMarker.region.id) {
    //
    //   } else {
    //
    //   }
    //
    // })

    this.wsRegions.on('region-updated', (region) => {

      const regionMarkerStart = region.start;
      const regionMarkerEnd = region.end;
      const deltaStart = regionMarkerStart - this.regionMarkerStart;
      const deltaEnd = regionMarkerEnd - this.regionMarkerEnd;
      // Изменения в маркере-регионе
      console.log('region-updated region', region);
      if (this.isRegionMode && this.isMoveMode && region.id === this.regionMarker.region.id) {

        if (regionMarkerStart !== this.regionMarkerStart && regionMarkerEnd !== this.regionMarkerEnd) {
          let markersToMove = this.sourceMarkers
              .filter(marker => marker.time >= this.regionMarkerStart && marker.time <= this.regionMarkerEnd)
              .sort(function (a,b) {
                if (a.time > b.time) return 1;
                if (a.time < b.time) return -1;
                if (a.markertype > b.markertype) return 1;
                if (a.markertype < b.markertype) return -1;
                return 0;
              });
          console.log('region-updated markersToMove', markersToMove);
          if (markersToMove.length > 0) {
            markersToMove.forEach((markerToMove) => {
              const newTime = markerToMove.time + deltaStart;

              let newMarker = {
                time: newTime,
                label:  markerToMove.label,
                color:  markerToMove.color,
                position:  markerToMove.position,
                markertype:  markerToMove.markertype
              }

              const indexToInsert = this.sourceMarkers.indexOf(markerToMove);
              markerToMove.region.remove();
              markerToMove.region = null;

              newMarker.region = this.createRegionMarker(newMarker);
              this.sourceMarkers.splice(indexToInsert, 1, newMarker);

            });
            this.updateMarkersBySyllables();
          }
        }

      } else {
        let marker = this.sourceMarkers.filter(item => item.region === region)[0];
        if (marker) {
          marker.time = region.start;
          let label = marker.label;
          let labels = label.split('|');
          if (marker.markertype === 'setting' && labels[0] === 'BPM') {
            this.createBeatMarkers();
          }
          this.updateMarkersBySyllables();
        }
      }

      this.regionMarkerStart = regionMarkerStart;
      this.regionMarkerEnd = regionMarkerEnd;

    })

    this.wsRegions.on('region-clicked', (region, e) => {
      e.stopPropagation() // prevent triggering a click on the waveform
      this.activeRegion = region
    })

    this.wsRegions.on('region-in', (region) => {
      this.activeRegion = region
    })

    this.wsRegions.on('region-out', (region) => {
      if (this.activeRegion === region) {
        this.activeRegion = null
      }
    })

    this.ws.on('interaction', () => {
      this.activeRegion = null
    })

    navigator.permissions.query({ name: "midi", sysex: true }).then((result) => {
      if (result.state === "granted") {
        // Access granted.
      } else if (result.state === "prompt") {
        // Using API will prompt for permission
      }
      // Permission was denied by user prompt or permission policy
    });

    navigator.requestMIDIAccess().then(this.onMIDISuccess, this.onMIDIFailure);

  },
  methods: {
    closeCustomConfirm() {
      this.isCustomConfirmVisible = false;
    },
    save() {
      this.addEndMarker();
      this.$store.dispatch('saveSourceTextAndMarkers', {
        voice: this.currentVoice,
        sourceText: this.sourceText,
        sourceMarkers: JSON.stringify(this.getMarkersToSave()),
        indexTabsVariant: this.indexTabsVariant
      })
    },
    getMarkersToSave() {
      return this.sourceMarkers.map(function (marker) {
        return {
          time: marker.time,
          label: marker.label,
          note: marker.note,
          chord: marker.chord,
          stringlad: marker.stringlad,
          locklad: marker.locklad,
          color: marker.color,
          position: marker.position,
          markertype: marker.markertype
        }
      });
    },
    addEndMarker() {
      if (this.sourceMarkers.length > 0) {
        let lastMarker = this.sourceMarkers[this.sourceMarkers.length-1];
        if (lastMarker.markertype !== 'setting' && lastMarker.label !== 'END') {
          let endMarker = {
            time: this.ws.getDuration(),
            label: 'END',
            color: '#000080',
            position: 'top',
            markertype: 'setting'
          };
          this.sourceMarkers.splice(this.sourceMarkers.length, 0, endMarker);
        }
      }
    },
    listenerKeyDown(e) {
      switch (e.code) {
        case 'KeyX': { if (!this.pressedX) { this.pressedX = true; } break; }
        case 'KeyA': { if (!this.pressedA) { this.pressedA = true; } break; }
        case 'KeyD': { if (!this.pressedD) { this.pressedD = true; } break; }
        case 'KeyQ': { if (!this.pressedQ) { this.pressedQ = true; } break; }
        case 'KeyE': { if (!this.pressedE) { this.pressedE = true; } break; }
        case 'KeyZ': { if (!this.pressedZ) { this.pressedZ = true; } break; }
        case 'KeyC': { if (!this.pressedC) { this.pressedC = true; } break; }
        case 'BracketLeft': { if (!this.pressedBL) { this.pressedBL = true; } break; }
        case 'BracketRight': { if (!this.pressedBR) { this.pressedBR = true; } break; }
        case 'Comma': { if (!this.pressedComma) { this.pressedComma = true; } break; }
        case 'Period': { if (!this.pressedPeriod) { this.pressedPeriod = true; } break; }
        case 'KeyW': { if (!this.pressedW) { this.pressedW = true; } break; }
        case 'KeyS': { if (!this.pressedS) { this.pressedS = true; } break; }
        case 'Digit1': { if (!this.pressed1) { this.pressed1 = true; } break; }
        case 'Digit2': { if (!this.pressed2) { this.pressed2 = true; } break; }
        case 'Digit3': { if (!this.pressed3) { this.pressed3 = true; } break; }
        case 'Digit4': { if (!this.pressed4) { this.pressed4 = true; } break; }
        case 'Digit5': { if (!this.pressed5) { this.pressed5 = true; } break; }
        case 'Digit6': { if (!this.pressed6) { this.pressed6 = true; } break; }
        case 'Digit0': { if (!this.pressed0) { this.pressed0 = true; } break; }
        case 'KeyT': { if (!this.pressedT) { this.pressedT = true; } break; }
        case 'KeyY': { if (!this.pressedY) { this.pressedY = true; } break; }
        case 'KeyU': { if (!this.pressedU) { this.pressedU = true; } break; }
        case 'KeyI': { if (!this.pressedI) { this.pressedI = true; } break; }
        case 'KeyO': { if (!this.pressedO) { this.pressedO = true; } break; }
        case 'KeyP': { if (!this.pressedP) { this.pressedP = true; } break; }
      }
    },
    listenerKeyUp(e) {
      switch (e.code) {
        case 'KeyX': { if (this.pressedX) { this.pressedX = false; } break; }
        case 'KeyA': { if (this.pressedA) { this.pressedA = false; } break; }
        case 'KeyD': { if (this.pressedD) { this.pressedD = false; } break; }
        case 'KeyQ': { if (this.pressedQ) { this.pressedQ = false; } break; }
        case 'KeyE': { if (this.pressedE) { this.pressedE = false; } break; }
        case 'KeyZ': { if (this.pressedZ) { this.pressedZ = false; } break; }
        case 'KeyC': { if (this.pressedC) { this.pressedC = false; } break; }
        case 'BracketLeft': { if (this.pressedBL) { this.pressedBL = false; } break; }
        case 'BracketRight': { if (this.pressedBR) { this.pressedBR = false; } break; }
        case 'Comma': { if (this.pressedComma) { this.pressedComma = false; } break; }
        case 'Period': { if (this.pressedPeriod) { this.pressedPeriod = false; } break; }
        case 'KeyW': { if (this.pressedW) { this.pressedW = false; } break; }
        case 'KeyS': { if (this.pressedS) { this.pressedS = false; } break; }
        case 'Digit1': { if (this.pressed1) { this.pressed1 = false; } break; }
        case 'Digit2': { if (this.pressed2) { this.pressed2 = false; } break; }
        case 'Digit3': { if (this.pressed3) { this.pressed3 = false; } break; }
        case 'Digit4': { if (this.pressed4) { this.pressed4 = false; } break; }
        case 'Digit5': { if (this.pressed5) { this.pressed5 = false; } break; }
        case 'Digit6': { if (this.pressed6) { this.pressed6 = false; } break; }
        case 'Digit0': { if (this.pressed0) { this.pressed0 = false; } break; }
        case 'KeyT': { if (this.pressedT) { this.pressedT = false; } break; }
        case 'KeyY': { if (this.pressedY) { this.pressedY = false; } break; }
        case 'KeyU': { if (this.pressedU) { this.pressedU = false; } break; }
        case 'KeyI': { if (this.pressedI) { this.pressedI = false; } break; }
        case 'KeyO': { if (this.pressedO) { this.pressedO = false; } break; }
        case 'KeyP': { if (this.pressedP) { this.pressedP = false; } break; }
      }
    },
    setEditMode(isEditMode) {
      this.isEditMode = isEditMode;
    },
    setRegionMode(isRegionMode) {
      if (!isRegionMode) {
        this.setMoveMode(isRegionMode)
        this.regionMarker.region.remove();
        this.regionMarker.region = null;
        this.regionMarker = undefined;
        this.regionMarkerStart = undefined;
        this.regionMarkerEnd = undefined;
      } else {
        let timeToAdd = this.currentTime;
        let regionMarker = {
          time: timeToAdd,
          label: '',
          color: '#00990033',
          position: 'top',
          markertype: 'region'
        }
        regionMarker.region = this.wsRegions.addRegion({
          start: regionMarker.time,
          end: regionMarker.time + 5,
          content: regionMarker.label,
          color: regionMarker.color,
          id: this.generateUUID()
        });
        this.regionMarker = regionMarker;
        this.regionMarkerStart = regionMarker.region.start;
        this.regionMarkerEnd = regionMarker.region.end;
      }
      this.isRegionMode = isRegionMode;
    },
    setMoveMode(isMoveMode) {
      console.log('setMoveMode this.regionMarkerStart', this.regionMarkerStart);
      console.log('setMoveMode this.regionMarkerEnd', this.regionMarkerEnd);
      this.isMoveMode = isMoveMode;
      let color = this.isMoveMode ? '#00009933' : '#00990033';
      this.regionMarker.region.remove();
      this.regionMarker.region = null;
      this.regionMarker = undefined;
      let regionMarker = {
        time: this.regionMarkerStart,
        label: '',
        color: color,
        position: 'top',
        markertype: 'region'
      }
      regionMarker.region = this.wsRegions.addRegion({
        start: this.regionMarkerStart,
        end: this.regionMarkerEnd,
        content: regionMarker.label,
        color: regionMarker.color,
        id: this.generateUUID()
      });
      this.regionMarker = regionMarker;
    },
    pasteMarkersInRegionToNewPlace() {
      if (this.isRegionMode) {
        const timeStart = this.regionMarker.region.start;
        const timeEnd = this.regionMarker.region.end;
        let timeToAdd = this.currentTime;
        let markersToCopy = this.sourceMarkers
            .filter(marker => marker.time >= timeStart && marker.time <= timeEnd)
            .sort(function (a,b) {
              if (a.time > b.time) return 1;
              if (a.time < b.time) return -1;
              if (a.markertype > b.markertype) return 1;
              if (a.markertype < b.markertype) return -1;
              return 0;
            });

        if (markersToCopy.length > 0) {
          const delta = timeToAdd - markersToCopy[0].time;
          let indexToInsert = this.currentMarkersIndex;
          markersToCopy.forEach((markerToCopy) => {
            const newTime = markerToCopy.time + delta;
            let newMarker = {
              time: newTime,
              label:  markerToCopy.label,
              color:  markerToCopy.color,
              position:  markerToCopy.position,
              markertype:  markerToCopy.markertype
            }
            newMarker.region = this.createRegionMarker(newMarker);
            this.sourceMarkers.splice(indexToInsert, 0, newMarker);
            indexToInsert++;
          });
          this.updateMarkersBySyllables();
        }
      }
    },
    deleteMarkersInRegion() {
      if (this.isRegionMode) {
        const timeStart = this.regionMarker.region.start;
        const timeEnd = this.regionMarker.region.end;
        let markersToDelete = this.sourceMarkers
            .filter(marker => marker.time >= timeStart && marker.time <= timeEnd)
            .sort(function (a,b) {
              if (a.time > b.time) return 1;
              if (a.time < b.time) return -1;
              if (a.markertype > b.markertype) return 1;
              if (a.markertype < b.markertype) return -1;
              return 0;
            });

        if (markersToDelete.length > 0) {
          markersToDelete.forEach((markerToDelete) => {
            const index = this.sourceMarkers.indexOf(markerToDelete);
            markerToDelete.region.remove();
            markerToDelete.region = null;
            if (index >=0) {
              this.sourceMarkers.splice(index, 1);
            }
          });
          this.updateMarkersBySyllables();
        }
      }
    },
    sortSourceMarkers() {
      this.sourceMarkers.sort(function (a,b) {
        if (a.time > b.time) return 1;
        if (a.time < b.time) return -1;
        if (a.markertype > b.markertype) return 1;
        if (a.markertype < b.markertype) return -1;
        return 0;
      });
    },
    updateMarkersBySyllables() {
      // let needSort = false;
      // for (let i = 0; i < this.sourceMarkers.length; i++) {
      //   let marker = this.sourceMarkers[i];
      //   if (marker.region && marker.time !== marker.region.start) {
      //     marker.time = marker.region.start;
      //     needSort = true;
      //   }
      // }
      // if (needSort) this.sortSourceMarkers();

      this.sortSourceMarkers();

      const MARKER_COLOR_SYLLABLES = '#D2691E';
      const MARKER_COLOR_FIRSTSYLLABLE = '#008000';
      let index = 0;
      let counter = 0;
      let prevEndOfLine = true;
      let color = MARKER_COLOR_FIRSTSYLLABLE;
      for (let i = 0; i < this.sourceMarkers.length; i++) {
        let marker = this.sourceMarkers[i]; //Object.assign({} , this.sourceMarkers[i]);
        if (marker.markertype === 'syllables') {
          if (index >= this.sourceSyllables.length) {
            marker.label = '';
            marker.region.setContent(this.getRegionContentFromMarker(marker));
            this.sourceMarkers.splice(i,1, marker);
            // eslint-disable-next-line
            counter++;
          }
          else if (marker.label !== this.sourceSyllables[index] || marker.color !== color) {
            marker.label = this.sourceSyllables[index];
            marker.color = color;
            marker.region.setOptions({ color: color })
            marker.region.setContent(this.getRegionContentFromMarker(marker));
            this.sourceMarkers.splice(i,1, marker);
            // eslint-disable-next-line
            counter++;
          }
          index++;
          color = MARKER_COLOR_SYLLABLES;
          prevEndOfLine = false;
        } else if (marker.markertype === 'endofline') {
          color = MARKER_COLOR_FIRSTSYLLABLE;
          // eslint-disable-next-line
          prevEndOfLine = true;
        }
      }

      // needSort = false;
      // for (let i = 0; i < this.sourceMarkers.length; i++) {
      //   let marker = this.sourceMarkers[i];
      //   if (marker.region && marker.time !== marker.region.start) {
      //     marker.time = marker.region.start;
      //     needSort = true;
      //   }
      // }
      // if (needSort) this.sortSourceMarkers();

      this.sortSourceMarkers()

    },
    getRegionContentFromMarker(marker) {
      if (!marker || !marker.label) return '';
      let text = marker.label.replaceAll("_"," ").trim();
      let textNote = !marker.note ? '' : '♪' + marker.note.replaceAll("_"," ").trim();
      let textChord = !marker.chord ? '' : '🎼' + marker.chord.replaceAll("_"," ").trim();
      let template = '';
      switch (marker.markertype) {
        case 'syllables': {
          template = `<div><div style="display: flex; flex-direction: column">
                        <div style="
                            background-color: beige;
                            display: block;
                            width: fit-content;">
                        ${text}
                        </div>
                        <div style="
                            background-color: lightpink;
                            display: block;
                            width: fit-content;">
                        ${textNote}
                        </div>
                        <div style="
                            background-color: lightgoldenrodyellow;
                            display: block;
                            width: fit-content;">
                        ${textChord}
                        </div>
                      </div></div>`
          break; }
        case 'eoln':
        case 'eolch':
        case 'endofline': {
          template = `<div><div
                        style="
                            display: block;
                            width: fit-content;
                            margin-top: 216px;
                            margin-left: -13px;
                        ">
                        <svg width="20" height="20" viewBox="0 0 480 800" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M46 731V208L233 36L437 224V731H46Z" fill="red" stroke="black"/>
                        <path fill-rule="evenodd" clip-rule="evenodd" d="M400 679.976C400 702.056 374.6 719.976 352.48 719.976H112.48C90.3999 719.976 80 702.056 80 679.976V244.507C80 239.107 78.4003 233.95 82.2803 230.19L206.44 107.222C221.96 92.0617 245.84 92.0617 261.36 107.222L390.68 230.19C394.52 233.95 400 239.107 400 244.507V679.976ZM464.16 190.581L290.24 22.71C259.16 -7.57 208.64 -7.57 177.56 22.71L8.75977 190.581C1.03977 198.101 0 208.433 0 219.233V719.976C0 764.176 28.3199 799.976 72.4799 799.976H392.48C436.68 799.976 480 764.176 480 719.976V219.233C480 208.433 471.88 198.101 464.16 190.581Z" fill="black"/>
                        </svg>
                      </div></div>`
          break;
        }
        case 'eon':
        case 'eoch':
        case 'endofsyllable': {
          template = `<div><div
                        style="
                            display: block;
                            width: fit-content;
                            margin-top: 216px;
                            margin-left: -13px;
                        ">
                        <svg width="20" height="20" viewBox="0 0 480 800" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M46 731V208L233 36L437 224V731H46Z" fill="crimson" stroke="black"/>
                        <path fill-rule="evenodd" clip-rule="evenodd" d="M400 679.976C400 702.056 374.6 719.976 352.48 719.976H112.48C90.3999 719.976 80 702.056 80 679.976V244.507C80 239.107 78.4003 233.95 82.2803 230.19L206.44 107.222C221.96 92.0617 245.84 92.0617 261.36 107.222L390.68 230.19C394.52 233.95 400 239.107 400 244.507V679.976ZM464.16 190.581L290.24 22.71C259.16 -7.57 208.64 -7.57 177.56 22.71L8.75977 190.581C1.03977 198.101 0 208.433 0 219.233V719.976C0 764.176 28.3199 799.976 72.4799 799.976H392.48C436.68 799.976 480 764.176 480 719.976V219.233C480 208.433 471.88 198.101 464.16 190.581Z" fill="black"/>
                        </svg>
                      </div></div>`
          break;
        }
        case 'nln':
        case 'nlch':
        case 'newline': {
          template = `<div><div
                        style="
                            display: block;
                            width: fit-content;
                            margin-top: 216px;
                            margin-left: -13px;
                        ">
                        <svg width="20" height="20" viewBox="0 0 480 800" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M46 731V208L233 36L437 224V731H46Z" fill="crimson" stroke="black"/>
                        <path fill-rule="evenodd" clip-rule="evenodd" d="M400 679.976C400 702.056 374.6 719.976 352.48 719.976H112.48C90.3999 719.976 80 702.056 80 679.976V244.507C80 239.107 78.4003 233.95 82.2803 230.19L206.44 107.222C221.96 92.0617 245.84 92.0617 261.36 107.222L390.68 230.19C394.52 233.95 400 239.107 400 244.507V679.976ZM464.16 190.581L290.24 22.71C259.16 -7.57 208.64 -7.57 177.56 22.71L8.75977 190.581C1.03977 198.101 0 208.433 0 219.233V719.976C0 764.176 28.3199 799.976 72.4799 799.976H392.48C436.68 799.976 480 764.176 480 719.976V219.233C480 208.433 471.88 198.101 464.16 190.581Z" fill="black"/>
                        </svg>
                      </div></div>`
          break;
        }
        case 'setting': {
          let label = marker.label;
          let labels = label.split('|');
          if (labels.length > 1) {
            switch (labels[0]) {
              case 'GROUP': {
                switch (labels[1]) {
                  case '0': {
                    template = `<div><div
                        style="
                            display: block;
                            width: fit-content;
                            margin-top: 216px;
                            margin-left: -13px;
                        ">
                        <svg width="20" height="20" viewBox="0 0 480 800" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M46 731V208L233 36L437 224V731H46Z" fill="black"/>
                        <path fill="white" d="M291.67 408.114C290.439 403.095 288.498 398.691 285.847 394.903C283.195 391.021 279.881 387.754 275.903 385.102C272.021 382.356 267.475 380.32 262.267 378.994C257.153 377.574 251.519 376.864 245.364 376.864C232.106 376.864 220.79 380.036 211.415 386.381C202.134 392.725 195.032 401.864 190.108 413.795C185.278 425.727 182.864 440.121 182.864 456.977C182.864 474.023 185.184 488.606 189.824 500.727C194.464 512.848 201.377 522.129 210.563 528.568C219.748 535.008 231.159 538.227 244.795 538.227C256.822 538.227 266.813 536.475 274.767 532.972C282.816 529.468 288.83 524.496 292.807 518.057C296.784 511.617 298.773 504.042 298.773 495.33L312.409 496.75H245.932V440.5H374.909V480.841C374.909 507.356 369.275 530.036 358.006 548.881C346.831 567.631 331.396 582.025 311.699 592.062C292.097 602.006 269.606 606.977 244.227 606.977C215.913 606.977 191.055 600.964 169.653 588.938C148.252 576.911 131.538 559.771 119.511 537.517C107.58 515.263 101.614 488.795 101.614 458.114C101.614 434.061 105.259 412.754 112.551 394.193C119.938 375.633 130.165 359.96 143.233 347.176C156.301 334.297 171.405 324.591 188.545 318.057C205.686 311.428 224.057 308.114 243.659 308.114C260.894 308.114 276.898 310.576 291.67 315.5C306.538 320.33 319.653 327.242 331.017 336.239C342.475 345.14 351.708 355.699 358.716 367.915C365.723 380.131 369.985 393.53 371.5 408.114H291.67Z"/>
                        <path fill-rule="evenodd" clip-rule="evenodd" d="M400 679.976C400 702.056 374.6 719.976 352.48 719.976H112.48C90.3999 719.976 80 702.056 80 679.976V244.507C80 239.107 78.4003 233.95 82.2803 230.19L206.44 107.222C221.96 92.0617 245.84 92.0617 261.36 107.222L390.68 230.19C394.52 233.95 400 239.107 400 244.507V679.976ZM464.16 190.581L290.24 22.71C259.16 -7.57 208.64 -7.57 177.56 22.71L8.75977 190.581C1.03977 198.101 0 208.433 0 219.233V719.976C0 764.176 28.3199 799.976 72.4799 799.976H392.48C436.68 799.976 480 764.176 480 719.976V219.233C480 208.433 471.88 198.101 464.16 190.581Z" fill="black"/>
                        </svg>
                      </div></div>`
                    break;
                  }
                  case '1': {
                    template = `<div><div
                        style="
                            display: block;
                            width: fit-content;
                            margin-top: 216px;
                            margin-left: -13px;
                        ">
                        <svg width="20" height="20" viewBox="0 0 480 800" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M46 731V208L233 36L437 224V731H46Z" fill="black"/>
                        <path fill="yellow" d="M291.67 408.114C290.439 403.095 288.498 398.691 285.847 394.903C283.195 391.021 279.881 387.754 275.903 385.102C272.021 382.356 267.475 380.32 262.267 378.994C257.153 377.574 251.519 376.864 245.364 376.864C232.106 376.864 220.79 380.036 211.415 386.381C202.134 392.725 195.032 401.864 190.108 413.795C185.278 425.727 182.864 440.121 182.864 456.977C182.864 474.023 185.184 488.606 189.824 500.727C194.464 512.848 201.377 522.129 210.563 528.568C219.748 535.008 231.159 538.227 244.795 538.227C256.822 538.227 266.813 536.475 274.767 532.972C282.816 529.468 288.83 524.496 292.807 518.057C296.784 511.617 298.773 504.042 298.773 495.33L312.409 496.75H245.932V440.5H374.909V480.841C374.909 507.356 369.275 530.036 358.006 548.881C346.831 567.631 331.396 582.025 311.699 592.062C292.097 602.006 269.606 606.977 244.227 606.977C215.913 606.977 191.055 600.964 169.653 588.938C148.252 576.911 131.538 559.771 119.511 537.517C107.58 515.263 101.614 488.795 101.614 458.114C101.614 434.061 105.259 412.754 112.551 394.193C119.938 375.633 130.165 359.96 143.233 347.176C156.301 334.297 171.405 324.591 188.545 318.057C205.686 311.428 224.057 308.114 243.659 308.114C260.894 308.114 276.898 310.576 291.67 315.5C306.538 320.33 319.653 327.242 331.017 336.239C342.475 345.14 351.708 355.699 358.716 367.915C365.723 380.131 369.985 393.53 371.5 408.114H291.67Z"/>
                        <path fill-rule="evenodd" clip-rule="evenodd" d="M400 679.976C400 702.056 374.6 719.976 352.48 719.976H112.48C90.3999 719.976 80 702.056 80 679.976V244.507C80 239.107 78.4003 233.95 82.2803 230.19L206.44 107.222C221.96 92.0617 245.84 92.0617 261.36 107.222L390.68 230.19C394.52 233.95 400 239.107 400 244.507V679.976ZM464.16 190.581L290.24 22.71C259.16 -7.57 208.64 -7.57 177.56 22.71L8.75977 190.581C1.03977 198.101 0 208.433 0 219.233V719.976C0 764.176 28.3199 799.976 72.4799 799.976H392.48C436.68 799.976 480 764.176 480 719.976V219.233C480 208.433 471.88 198.101 464.16 190.581Z" fill="black"/>
                        </svg>
                      </div></div>`
                    break;
                  }
                  case '2': {
                    template = `<div><div
                        style="
                            display: block;
                            width: fit-content;
                            margin-top: 216px;
                            margin-left: -13px;
                        ">
                        <svg width="20" height="20" viewBox="0 0 480 800" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M46 731V208L233 36L437 224V731H46Z" fill="black"/>
                        <path fill="aqua" d="M291.67 408.114C290.439 403.095 288.498 398.691 285.847 394.903C283.195 391.021 279.881 387.754 275.903 385.102C272.021 382.356 267.475 380.32 262.267 378.994C257.153 377.574 251.519 376.864 245.364 376.864C232.106 376.864 220.79 380.036 211.415 386.381C202.134 392.725 195.032 401.864 190.108 413.795C185.278 425.727 182.864 440.121 182.864 456.977C182.864 474.023 185.184 488.606 189.824 500.727C194.464 512.848 201.377 522.129 210.563 528.568C219.748 535.008 231.159 538.227 244.795 538.227C256.822 538.227 266.813 536.475 274.767 532.972C282.816 529.468 288.83 524.496 292.807 518.057C296.784 511.617 298.773 504.042 298.773 495.33L312.409 496.75H245.932V440.5H374.909V480.841C374.909 507.356 369.275 530.036 358.006 548.881C346.831 567.631 331.396 582.025 311.699 592.062C292.097 602.006 269.606 606.977 244.227 606.977C215.913 606.977 191.055 600.964 169.653 588.938C148.252 576.911 131.538 559.771 119.511 537.517C107.58 515.263 101.614 488.795 101.614 458.114C101.614 434.061 105.259 412.754 112.551 394.193C119.938 375.633 130.165 359.96 143.233 347.176C156.301 334.297 171.405 324.591 188.545 318.057C205.686 311.428 224.057 308.114 243.659 308.114C260.894 308.114 276.898 310.576 291.67 315.5C306.538 320.33 319.653 327.242 331.017 336.239C342.475 345.14 351.708 355.699 358.716 367.915C365.723 380.131 369.985 393.53 371.5 408.114H291.67Z"/>
                        <path fill-rule="evenodd" clip-rule="evenodd" d="M400 679.976C400 702.056 374.6 719.976 352.48 719.976H112.48C90.3999 719.976 80 702.056 80 679.976V244.507C80 239.107 78.4003 233.95 82.2803 230.19L206.44 107.222C221.96 92.0617 245.84 92.0617 261.36 107.222L390.68 230.19C394.52 233.95 400 239.107 400 244.507V679.976ZM464.16 190.581L290.24 22.71C259.16 -7.57 208.64 -7.57 177.56 22.71L8.75977 190.581C1.03977 198.101 0 208.433 0 219.233V719.976C0 764.176 28.3199 799.976 72.4799 799.976H392.48C436.68 799.976 480 764.176 480 719.976V219.233C480 208.433 471.88 198.101 464.16 190.581Z" fill="black"/>
                        </svg>
                      </div></div>`
                    break;
                  }
                  case '3': {
                    template = `<div><div
                        style="
                            display: block;
                            width: fit-content;
                            margin-top: 216px;
                            margin-left: -13px;
                        ">
                        <svg width="20" height="20" viewBox="0 0 480 800" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M46 731V208L233 36L437 224V731H46Z" fill="black"/>
                        <path fill="lime" d="M291.67 408.114C290.439 403.095 288.498 398.691 285.847 394.903C283.195 391.021 279.881 387.754 275.903 385.102C272.021 382.356 267.475 380.32 262.267 378.994C257.153 377.574 251.519 376.864 245.364 376.864C232.106 376.864 220.79 380.036 211.415 386.381C202.134 392.725 195.032 401.864 190.108 413.795C185.278 425.727 182.864 440.121 182.864 456.977C182.864 474.023 185.184 488.606 189.824 500.727C194.464 512.848 201.377 522.129 210.563 528.568C219.748 535.008 231.159 538.227 244.795 538.227C256.822 538.227 266.813 536.475 274.767 532.972C282.816 529.468 288.83 524.496 292.807 518.057C296.784 511.617 298.773 504.042 298.773 495.33L312.409 496.75H245.932V440.5H374.909V480.841C374.909 507.356 369.275 530.036 358.006 548.881C346.831 567.631 331.396 582.025 311.699 592.062C292.097 602.006 269.606 606.977 244.227 606.977C215.913 606.977 191.055 600.964 169.653 588.938C148.252 576.911 131.538 559.771 119.511 537.517C107.58 515.263 101.614 488.795 101.614 458.114C101.614 434.061 105.259 412.754 112.551 394.193C119.938 375.633 130.165 359.96 143.233 347.176C156.301 334.297 171.405 324.591 188.545 318.057C205.686 311.428 224.057 308.114 243.659 308.114C260.894 308.114 276.898 310.576 291.67 315.5C306.538 320.33 319.653 327.242 331.017 336.239C342.475 345.14 351.708 355.699 358.716 367.915C365.723 380.131 369.985 393.53 371.5 408.114H291.67Z"/>
                        <path fill-rule="evenodd" clip-rule="evenodd" d="M400 679.976C400 702.056 374.6 719.976 352.48 719.976H112.48C90.3999 719.976 80 702.056 80 679.976V244.507C80 239.107 78.4003 233.95 82.2803 230.19L206.44 107.222C221.96 92.0617 245.84 92.0617 261.36 107.222L390.68 230.19C394.52 233.95 400 239.107 400 244.507V679.976ZM464.16 190.581L290.24 22.71C259.16 -7.57 208.64 -7.57 177.56 22.71L8.75977 190.581C1.03977 198.101 0 208.433 0 219.233V719.976C0 764.176 28.3199 799.976 72.4799 799.976H392.48C436.68 799.976 480 764.176 480 719.976V219.233C480 208.433 471.88 198.101 464.16 190.581Z" fill="black"/>
                        </svg>
                      </div></div>`
                    break;
                  }
                  case '4': {
                    template = `<div><div
                        style="
                            display: block;
                            width: fit-content;
                            margin-top: 216px;
                            margin-left: -13px;
                        ">
                        <svg width="20" height="20" viewBox="0 0 480 800" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M46 731V208L233 36L437 224V731H46Z" fill="black"/>
                        <path fill="magenta" d="M291.67 408.114C290.439 403.095 288.498 398.691 285.847 394.903C283.195 391.021 279.881 387.754 275.903 385.102C272.021 382.356 267.475 380.32 262.267 378.994C257.153 377.574 251.519 376.864 245.364 376.864C232.106 376.864 220.79 380.036 211.415 386.381C202.134 392.725 195.032 401.864 190.108 413.795C185.278 425.727 182.864 440.121 182.864 456.977C182.864 474.023 185.184 488.606 189.824 500.727C194.464 512.848 201.377 522.129 210.563 528.568C219.748 535.008 231.159 538.227 244.795 538.227C256.822 538.227 266.813 536.475 274.767 532.972C282.816 529.468 288.83 524.496 292.807 518.057C296.784 511.617 298.773 504.042 298.773 495.33L312.409 496.75H245.932V440.5H374.909V480.841C374.909 507.356 369.275 530.036 358.006 548.881C346.831 567.631 331.396 582.025 311.699 592.062C292.097 602.006 269.606 606.977 244.227 606.977C215.913 606.977 191.055 600.964 169.653 588.938C148.252 576.911 131.538 559.771 119.511 537.517C107.58 515.263 101.614 488.795 101.614 458.114C101.614 434.061 105.259 412.754 112.551 394.193C119.938 375.633 130.165 359.96 143.233 347.176C156.301 334.297 171.405 324.591 188.545 318.057C205.686 311.428 224.057 308.114 243.659 308.114C260.894 308.114 276.898 310.576 291.67 315.5C306.538 320.33 319.653 327.242 331.017 336.239C342.475 345.14 351.708 355.699 358.716 367.915C365.723 380.131 369.985 393.53 371.5 408.114H291.67Z"/>
                        <path fill-rule="evenodd" clip-rule="evenodd" d="M400 679.976C400 702.056 374.6 719.976 352.48 719.976H112.48C90.3999 719.976 80 702.056 80 679.976V244.507C80 239.107 78.4003 233.95 82.2803 230.19L206.44 107.222C221.96 92.0617 245.84 92.0617 261.36 107.222L390.68 230.19C394.52 233.95 400 239.107 400 244.507V679.976ZM464.16 190.581L290.24 22.71C259.16 -7.57 208.64 -7.57 177.56 22.71L8.75977 190.581C1.03977 198.101 0 208.433 0 219.233V719.976C0 764.176 28.3199 799.976 72.4799 799.976H392.48C436.68 799.976 480 764.176 480 719.976V219.233C480 208.433 471.88 198.101 464.16 190.581Z" fill="black"/>
                        </svg>
                      </div></div>`
                    break;
                  }
                }
                break;
              }
              case 'CHORD': {
                text = '🎼' + labels[1] + (labels.length > 2 ? '['+ labels[2] +']' : '');
                template = `<div><div
                        style="
                            background-color: beige;
                            display: block;
                            width: fit-content;
                            margin-top: 216px;
                        ">
                        ${text}
                      </div></div>`
                break;
              }
              case 'COMMENT': {
                text = labels[1];
                template = `<div><div
                        style="
                            background-color: beige;
                            display: block;
                            width: fit-content;
                            margin-top: 10px;
                        ">
                        ${text}
                      </div></div>`
                break;
              }
              case 'CAPO': {
                text = '𝄊' + labels[1];
                template = `<div><div
                        style="
                            background-color: beige;
                            display: block;
                            width: fit-content;
                            margin-top: 216px;
                        ">
                        ${text}
                      </div></div>`
                break;
              }
              default: {
                text = labels[0] + ': ' + labels[1];
                template = `<div><div
                        style="
                            background-color: beige;
                            display: block;
                            width: fit-content;
                            margin-top: 10px;
                        ">
                        ${text}
                      </div></div>`
                break;
              }
            }
          }
          break; }
        case 'unmute': { break; }
        case 'note': {
          template = `<div>
                        <div style="
                            background-color: beige;
                            display: block;
                            width: fit-content;
                        ">
                            ${text}
                        </div>
                        <div style="
                            background-color: lightpink;
                            display: block;
                            width: fit-content;">
                            ${textNote}
                        </div>
                      </div>`
          break; }
        case 'chord': {
          let label = marker.chord;
          let labels = label.split('|');
          text = '🎼' + labels[0] + (labels.length > 1 ? '['+ labels[1] +']' : '');
          template = `<div><div
                        style="
                            background-color: beige;
                            display: block;
                            width: fit-content;
                            margin-top: 216px;
                        ">
                        ${text}
                      </div>
                                              <div style="
                            background-color: lightgoldenrodyellow;
                            display: block;
                            width: fit-content;">
                        ${textChord}
                        </div>
                        </div>`
          break; }
        case 'beat0': { break; }
        case 'beat': { break; }
      }

      return this.fromHTML(template);
    },
    fromHTML(html, trim = true) {
      // Process the HTML string.
      html = trim ? html.trim() : html;
      if (!html) return null;

      // Then set up a new template element.
      const template = document.createElement('template');
      template.innerHTML = html;
      const result = template.content.children;

      // Then return either an HTMLElement or HTMLCollection,
      // based on whether the input HTML had one or more roots.
      if (result.length === 1) return result[0];
      return result;
    },
    uppercaseFirstLetter(text) {
      let result = '';
      let flag = false;
      for (let index = 0; index < text.length; index++) {
        let symbolInSymbolString = text[index];
        if (!flag && !`-_,.!@#№$;%^:&?*()[]{}|/\\"'\`~ «»`.includes(symbolInSymbolString)) {
          result += symbolInSymbolString.toUpperCase();
          flag = true;
        } else {
          result += symbolInSymbolString;
        }
      }
      return result
    },
    goToPreviousMarker(markertype) {

      for (let i = this.currentMarkersIndex-1; i >= 0; i--) {
        let currentMarker = this.sourceMarkers[i];
        if (this.isShowMarkerType(currentMarker.markertype) && (markertype === undefined || currentMarker.markertype === markertype)) {
          this.ws.setTime(currentMarker.time);
          return;
        }
      }

      // let currentMarker = this.sourceMarkers[this.currentMarkersIndex];
      // let diff = Math.abs(currentMarker.time - this.currentTime);
      // if (diff < 0.02 && this.currentMarkersIndex > 0) {
      //   currentMarker = this.sourceMarkers[this.currentMarkersIndex-1];
      // }
      // this.ws.setTime(currentMarker.time);
    },
    goToNextMarker(markertype) {

      for (let i = this.currentMarkersIndex+1; i < this.sourceMarkers.length; i++) {
        let currentMarker = this.sourceMarkers[i];
        if (this.isShowMarkerType(currentMarker.markertype) && (markertype === undefined || currentMarker.markertype === markertype)) {
          this.ws.setTime(currentMarker.time);
          return;
        }
      }
      // let currentMarker = this.currentMarkersIndex < this.sourceMarkers.length - 1 ? this.sourceMarkers[this.currentMarkersIndex+1] : this.sourceMarkers[this.currentMarkersIndex];
      // this.ws.setTime(currentMarker.time);
    },
    addSettingMarker() {
      this.isEditMode = false;
      this.customConfirmParams = {
        header: 'Добавление маркера настройки',
        body: `Укажите тип и значение маркера`,
        callback: this.doAddSettingMarker,
        fields: [
          {
            fldName: 'settingType',
            fldLabel: 'Тип:',
            fldValue: this.$store.getters.getLastSettingType,
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '250px', textAlign: 'left', borderRadius: '10px'}
          },
          {
            fldName: 'settingValue',
            fldLabel: 'Значение:',
            fldValue: this.$store.getters.getLastSettingValue,
            fldLabelStyle: { width: '100px', textAlign: 'right', paddingRight: '5px'},
            fldValueStyle: { width: '250px', textAlign: 'left', borderRadius: '10px'}
          }
        ]
      }
      this.isCustomConfirmVisible = true;
    },
    doAddSettingMarker(result) {
      this.$store.dispatch('setLastSettingType', {value: result.settingType});
      this.$store.dispatch('setLastSettingValue', {value: result.settingValue});
      this.addMarker('setting', result.settingType + '|' + result.settingValue);
      this.isEditMode = true;
    },
    addMarker(markerType, settingValue = '', notDelete = false, withEndOfLine = false) {

      const MARKER_COLOR_SYLLABLES = '#D2691E';
      const MARKER_COLOR_NOTE = '#009900';
      const MARKER_COLOR_ENDOFLINE = '#FF0000';
      const MARKER_COLOR_ENDOFSYLLABLE = '#99004C';
      const MARKER_COLOR_NEWLINE = '#FF0000';
      const MARKER_COLOR_SETTING = '#000080';
      const MARKER_COLOR_UNMUTE = '#FFFF00';
      const MARKER_COLOR_BEAT0 = '#303030';
      const MARKER_COLOR_BEAT = '#606060';

      let timeToAdd = this.currentTime;
      let currentMarkerTime = this.currentMarkersIndex >= 0 ? this.sourceMarkers[this.currentMarkersIndex].time : 0;
      let diff = Math.abs(currentMarkerTime - timeToAdd);
      let position = 'bottom';
      let label = '';
      let color = '';
      let needAddBeats = false;
      let needCalcBpm = false;

      switch (markerType) {
        case 'setting': {
          label = settingValue;
          position = 'top';
          color = MARKER_COLOR_SETTING;
          if (settingValue.startsWith('BPM|')) {
            needAddBeats = true;
          }
          if (settingValue.startsWith('CALC|')) {
            needCalcBpm = true;
          }
          break;
        }
        case 'syllables':{
          position = 'bottom';
          color = MARKER_COLOR_SYLLABLES;
          break;
        }
        case 'nln':
        case 'nlch':
        case 'newline':{
          position = 'bottom';
          color = MARKER_COLOR_NEWLINE;
          break;
        }
        case 'eoln':
        case 'eolch':
        case 'endofline':{
          position = 'bottom';
          color = MARKER_COLOR_ENDOFLINE;
          break;
        }
        case 'eon':
        case 'eoch':
        case 'endofsyllable':{
          position = 'bottom';
          color = MARKER_COLOR_ENDOFSYLLABLE;
          break;
        }
        case 'unmute':{
          position = 'top';
          color = MARKER_COLOR_UNMUTE;
          break;
        }
        case 'note':{
          label = settingValue;
          position = 'bottom';
          color = MARKER_COLOR_NOTE;
          break;
        }
        case 'chord':{
          label = settingValue;
          position = 'bottom';
          color = MARKER_COLOR_NOTE;
          break;
        }
        case 'beat0':{
          position = 'top';
          color = MARKER_COLOR_BEAT0;
          break;
        }
        case 'beat':{
          position = 'top';
          color = MARKER_COLOR_BEAT;
          break;
        }
      }

      if (needCalcBpm) {
        // Находим ближайший слева BPM-маркер
        let bpmMarkers = this.sourceMarkers.filter(marker => marker.markertype === 'setting' && marker.label.startsWith('BPM|') && marker.time < this.currentTime);
        if (bpmMarkers.length === 0) return;
        let bpmMarker = bpmMarkers.reverse()[0];
        // Время текущее минус время этого маркера = время такта
        let bpm = Math.round(60 / ((this.currentTime - bpmMarker.time) / 4));
        // Записываем новое значение BPM в маркер
        bpmMarker.label = 'BPM|' + bpm;
        bpmMarker.region.setContent(this.getRegionContentFromMarker(bpmMarker));
        this.createBeatMarkers();
        return;
      }

      let newMarker = {
        time: timeToAdd,
        label: label,
        color: color,
        position: position,
        markertype: markerType
      }

      let indexToInsert = this.currentMarkersIndex + ((diff < 0.002 && !notDelete) ? 0 : 1);
      let countDeleted = (diff < 0.002 && !notDelete) ? 1: 0;

      if (withEndOfLine) {
        let endOfLineMarker = {
          time: timeToAdd,
          label: '',
          color: MARKER_COLOR_ENDOFLINE,
          position: 'bottom',
          markertype: 'endofline'
        }
        endOfLineMarker.region = this.createRegionMarker(endOfLineMarker);
        this.sourceMarkers.splice(indexToInsert, 0, endOfLineMarker);

        newMarker.region = this.createRegionMarker(newMarker);
        this.sourceMarkers.splice(indexToInsert+1, 0, newMarker);

      } else {
        if (countDeleted > 0) {
          let markerToDelete = this.sourceMarkers[indexToInsert];
          markerToDelete.region.remove();
          markerToDelete.region = null;
        }
        newMarker.region = this.createRegionMarker(newMarker);
        this.sourceMarkers.splice(indexToInsert, countDeleted, newMarker);
      }

      this.updateMarkersBySyllables();
      // this.createMarkers(true);
      if (needAddBeats) {
        this.createBeatMarkers();
      }
    },
    createRegionMarker(marker) {
      if (this.isShowMarkerType(marker.markertype)) {
        return this.wsRegions.addRegion({
          start: marker.time,
          content: this.getRegionContentFromMarker(marker),
          color: marker.color,
          id: this.generateUUID() //marker.markertype
        });
      } else {
        return null;
      }
    },
    generateUUID() { // Public Domain/MIT
      let d = new Date().getTime();//Timestamp
      let d2 = ((typeof performance !== 'undefined') && performance.now && (performance.now() * 1000)) || 0;//Time in microseconds since page-load or 0 if unsupported
      return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        let r = Math.random() * 16;//random number between 0 and 16
        if(d > 0){//Use timestamp until depleted
          r = (d + r)%16 | 0;
          d = Math.floor(d/16);
        } else {//Use microseconds since page-load if supported
          r = (d2 + r)%16 | 0;
          d2 = Math.floor(d2/16);
        }
        return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
      });
    },
    deleteMarker() {
      let currentMarker = this.sourceMarkers[this.currentMarkersIndex];
      let isBpm = currentMarker.markertype === 'setting' && currentMarker.label.startsWith('BPM|');
      let diff = Math.abs(currentMarker.time - this.currentTime);
      if (diff < 0.002) {
        if (this.currentMarkersIndex > 0 && Math.abs(this.sourceMarkers[this.currentMarkersIndex-1].time - currentMarker.time) < 0.002) {
          this.currentMarkersIndex--
          currentMarker = this.sourceMarkers[this.currentMarkersIndex]
        }
        currentMarker.region.remove();
        currentMarker.region = null;
        this.sourceMarkers.splice(this.currentMarkersIndex,1);
        this.updateMarkersBySyllables();
        // this.createMarkers(true);
        if (this.currentMarkersIndex < this.sourceMarkers.length) {
          this.ws.setTime(this.sourceMarkers[this.currentMarkersIndex].time);
        } else {
          if (this.sourceMarkers.length > 0) {
            this.ws.setTime(this.sourceMarkers[this.currentMarkersIndex-1].time);
          }
        }
        if (isBpm) {
          this.createBeatMarkers();
        }
      }
    },
    createBeatMarkers() {
      // Удаляем все бит-маркеры
      let countBeatMarkers = this.sourceMarkers.filter(marker => marker.markertype === 'beat').length;
      while (countBeatMarkers !== 0) {
        for (let i = 0; i < this.sourceMarkers.length; i++) {
          let marker = this.sourceMarkers[i];
          if (marker.markertype === 'beat') {
            marker.region.remove();
            marker.region = null;
            this.sourceMarkers.splice(i,1);
            break;
          }
        }
        countBeatMarkers = this.sourceMarkers.filter(marker => marker.markertype === 'beat').length;
      }
      const MARKER_COLOR_BEAT0 = '#808080';
      const MARKER_COLOR_BEAT2 = '#909090';
      const MARKER_COLOR_BEAT4 = '#AAAAAA';
      const MARKER_COLOR_BEAT8 = '#BBBBBB';
      const MARKER_COLOR_BEAT16 = '#CCCCCC';
      const MARKER_COLOR_BEAT32 = '#DDDDDD';
      const beat = this.beat;
      // Получаем список BPM-маркеров
      let bpmMarkers = this.sourceMarkers.filter(marker => marker.markertype === 'setting' && marker.label.startsWith('BPM|'));
      for (let indexBpmMarker = 0; indexBpmMarker < bpmMarkers.length; indexBpmMarker++) {
        let bpmMarker = bpmMarkers[indexBpmMarker];
        let label = bpmMarker.label;
        let labels = label.split('|');
        let bpm = labels[1];
        // Для BPM-маркера вычисляем время такта
        let noteLength4 = 60.0 / bpm;
        let noteLength8 = noteLength4 / 2;
        let noteLength16 = noteLength8 / 2;
        let noteLength32 = noteLength16 / 2;
        // Если это первый BPM-маркер - создаём бит-маркеры к началу
        if (indexBpmMarker === 0) {
          let currentTime = bpmMarker.time - noteLength32;
          let counter = 1;
          while (currentTime > 0) {
            let timeToAdd = currentTime;
            let position = 'bottom';
            let label = '';
            let color = MARKER_COLOR_BEAT32;
            let needAdd = false;
            if (counter % 32 === 0) {
              color = MARKER_COLOR_BEAT0;
              needAdd = true;
            }
            if (counter % 16 === 0 && beat >= 2) {
              color = MARKER_COLOR_BEAT2;
              needAdd = true;
            }
            if (counter % 8 === 0 && beat >= 4) {
              color = MARKER_COLOR_BEAT4;
              needAdd = true;
            }
            if (counter % 4 === 0 && beat >= 8) {
              color = MARKER_COLOR_BEAT8;
              needAdd = true;
            }
            if (counter % 2 === 0 && beat >= 16) {
              color = MARKER_COLOR_BEAT16;
              needAdd = true;
            }
            if (beat >= 32) {
              color = MARKER_COLOR_BEAT32;
              needAdd = true;
            }

            let newMarker = {
              time: timeToAdd,
              label: label,
              color: color,
              position: position,
              markertype: 'beat'
            }

            if (needAdd) {
              newMarker.region = this.createRegionMarker(newMarker);
              this.sourceMarkers.push(newMarker);
            }

            counter++;
            currentTime = currentTime - noteLength32;
          }
        }

        // Создаём бит-маркеры до следующего BPM-маркера или до конца
        let currentTime = bpmMarker.time + noteLength32;
        let counter = 1;
        let nextBpmMarkerTime = bpmMarker.region.totalDuration;
        if (indexBpmMarker < bpmMarkers.length - 1) {
          nextBpmMarkerTime = bpmMarkers[indexBpmMarker+1].time;
        }
        while (currentTime < nextBpmMarkerTime) {
          let timeToAdd = currentTime;
          let position = 'bottom';
          let label = '';
          let color = MARKER_COLOR_BEAT32;
          let needAdd = false;
          if (counter % 32 === 0) {
            color = MARKER_COLOR_BEAT0;
            needAdd = true;
          }
          if (counter % 16 === 0 && beat >= 2) {
            color = MARKER_COLOR_BEAT2;
            needAdd = true;
          }
          if (counter % 8 === 0 && beat >= 4) {
            color = MARKER_COLOR_BEAT4;
            needAdd = true;
          }
          if (counter % 4 === 0 && beat >= 8) {
            color = MARKER_COLOR_BEAT8;
            needAdd = true;
          }
          if (counter % 2 === 0 && beat >= 16) {
            color = MARKER_COLOR_BEAT16;
            needAdd = true;
          }
          if (beat >= 32) {
            color = MARKER_COLOR_BEAT32;
            needAdd = true;
          }
          let newMarker = {
            time: timeToAdd,
            label: label,
            color: color,
            position: position,
            markertype: 'beat'
          }
          if (needAdd) {
            newMarker.region = this.createRegionMarker(newMarker);
            this.sourceMarkers.push(newMarker);
          }

          counter++;
          currentTime = currentTime + noteLength32;
        }

      }
      // this.updateMarkersBySyllables();
      this.sortSourceMarkers();
    },
    async doReplaceText() {
      this.sourceText = await this.$store.getters.getReplacedSymbolsInText(this.sourceText);
      this.doReplaceBrokenMarkers();
    },
    async doBpmAdd() {
      let bpm = await this.$store.getters.getSheetsageinfoBpm;
      const MARKER_COLOR_SETTING = '#000080';
      if (bpm !== '') {
        let newMarker = {
          time: this.currentTime,
          label: "BPM|" + bpm,
          color: MARKER_COLOR_SETTING,
          position: 'top',
          markertype: 'setting'
        }
        newMarker.region = this.createRegionMarker(newMarker);
        this.sourceMarkers.push(newMarker);
      }
    },
    async doChordsAdd() {
      const MARKER_COLOR_CHORD = '#FFFF00';
      const SPAN_STYLE_NOTE = `<span style="color: #00BFFF; font-family: monospace; font-size: 15px; font-style: normal; font-weight: bolder;">`
      const SPAN_STYLE_TEXT = `<span style="color: #FFFFFF; font-family: monospace; font-size: 15px; font-style: normal; font-weight: bolder;">`
      const SPAN_END = '</span>';
      const BR = '<br>';
      const BEAT_SYMBOL_MOD16 = '◉';
      const BEAT_SYMBOL_MOD4 = '•';
      const BEAT_SYMBOL_MOD1 = '◦';
      let result = '';
      let beattimesString = await this.$store.dispatch('getSheetsageinfoBeattimes');
      let beattimes = JSON.parse(beattimesString);

      let strChords = '';
      let strBeats = '';

      for (let beattimesIndex = 0; beattimesIndex < beattimes.length; beattimesIndex++) {
        let symbol = BEAT_SYMBOL_MOD1;
        if (beattimesIndex % 4 === 0) symbol = BEAT_SYMBOL_MOD4;
        if (beattimesIndex % 16 === 0) symbol = BEAT_SYMBOL_MOD16;
        strChords += symbol;
        strBeats += symbol;
      }

      let chordsString = await this.$store.dispatch('getSheetsageinfoChords');
      let chords = JSON.parse(chordsString);
      for (let i = 0; i < chords.length; i++) {
        let chordParams = chords[i].split(' ');
        let chordName = chordParams[0];
        let chordDuration = chordParams[1];
        let chordBeatIndex = chordParams[3];
        if (chordName !== 'S') {
          strChords = strChords.substring(0,chordBeatIndex) + chordName + strChords.substring(Number(chordBeatIndex) + Number(chordName.length));
          let newMarker = {
            time: chordDuration,
            label: chordName,
            color: MARKER_COLOR_CHORD,
            position: 'top',
            markertype: 'chord',
            tag: chords[i]
          }
          newMarker.region = this.createRegionMarker(newMarker);
          this.sourceMarkers.push(newMarker);
        }
      }
      let beattimeIndex = 0;
      let currentBeattime = beattimes[beattimeIndex];
      let nextBeattime = beattimes[beattimeIndex+1];
      let markers = this.sourceMarkers.filter(item => item.markertype === 'syllables' && item.label);
      for (let markerIndex = 0; markerIndex < markers.length; markerIndex++) {
        const marker = markers[markerIndex];
        while (!(currentBeattime < marker.time && nextBeattime >= marker.time)) {
          if (beattimeIndex > beattimes.length - 2) {
            break;
          }
          beattimeIndex++;
          currentBeattime = beattimes[beattimeIndex];
          nextBeattime = beattimes[beattimeIndex+1];
        }
        let markerBeattimeIndex = beattimeIndex+1;
        // if (Math.abs(currentBeattime - marker.time) < Math.abs(nextBeattime - marker.time)) {
        //   markerBeattimeIndex = beattimeIndex
        // }
        let text = marker.label.replaceAll('_',' ').trim()
        strBeats = strBeats.substring(0,markerBeattimeIndex) + text + strBeats.substring(Number(markerBeattimeIndex) + Number(text.length));
      }
      while (strChords.length > 0) {
        const lineChord = strChords.substring(0,64);
        strChords = strChords.length < 64 ? '' : strChords.substring(64);
        const lineBeats = strBeats.substring(0,64);
        strBeats = strBeats.length < 64 ? '' : strBeats.substring(64);
        result += SPAN_STYLE_NOTE + lineChord.replaceAll(BEAT_SYMBOL_MOD1,'&nbsp;') + SPAN_END + BR
        result += SPAN_STYLE_TEXT + lineBeats.replaceAll(BEAT_SYMBOL_MOD1,'&nbsp;').replaceAll(BEAT_SYMBOL_MOD4,'&nbsp;').replaceAll(BEAT_SYMBOL_MOD16,'&nbsp;') + SPAN_END + BR + BR
      }
      this.chordsFormatted = result;
    },
    doChordsDel() {
      // Удаляем все chord-маркеры
      let countChordsMarkers = this.sourceMarkers.filter(marker => marker.markertype === 'chord').length;
      while (countChordsMarkers !== 0) {
        for (let i = 0; i < this.sourceMarkers.length; i++) {
          let marker = this.sourceMarkers[i];
          if (marker.markertype === 'chord') {
            marker.region.remove();
            marker.region = null;
            this.sourceMarkers.splice(i,1);
            break;
          }
        }
        countChordsMarkers = this.sourceMarkers.filter(marker => marker.markertype === 'chord').length;
      }
    },
    async doDiffBeatsInc() {
      let diff = await this.$store.dispatch('getDiffBeatsInc');
      diff = await this.$store.dispatch('getDiffBeatsInc');
      diff = await this.$store.dispatch('getDiffBeatsInc');
      diff = await this.$store.dispatch('getDiffBeatsInc');
      if (diff >= 0) {
        this.doChordsDel();
        await this.doChordsAdd();
      }
    },
    async doDiffBeatsDec() {
      let diff = await this.$store.dispatch('getDiffBeatsDec');
      diff = await this.$store.dispatch('getDiffBeatsDec');
      diff = await this.$store.dispatch('getDiffBeatsDec');
      diff = await this.$store.dispatch('getDiffBeatsDec');
      if (diff >= 0) {
        this.doChordsDel();
        await this.doChordsAdd();
      }
    },
    doMarkersDec() {
      // Берем текущую позицию, находим следующий за ней маркер, вычисляем разницу, отнимаем эту разнуцу у всех последующих маркеров, кроме маркера конца
      let markers = this.sourceMarkers;
      for (let i = 0; i < markers.length-1; i++) {
        let marker = markers[i];
        if (this.currentTime < marker.time) {
          const diff = marker.time - this.currentTime;
          for (let j = i; j < markers.length-1; j++) {
            if (markers[j].markertype !== 'setting' && markers[j].label !== 'END') {
              markers[j].time -= diff;
            }
          }
          this.redrawMarkers();
          return;
        }
      }
    },
    doMarkersInc() {
      // Берем текущую позицию, находим предыдущий маркер, вычисляем разницу, прибавляем эту разнуцу у всех последующих маркеров, кроме маркера конца
      let markers = this.sourceMarkers;
      if (markers.length > 0 && this.currentTime < markers[0].time) return -1;
      for (let i = 0; i < markers.length-1; i++) {
        let marker = markers[i];
        let nextMarker = markers[i+1];
        if (this.currentTime >= marker.time && this.currentTime < nextMarker.time) {
          const diff = this.currentTime - marker.time;
          for (let j = i; j < markers.length-1; j++) {
            if (markers[j].markertype !== 'setting' && markers[j].label !== 'END') {
              markers[j].time += diff;
            }
          }
          this.redrawMarkers();
          return;
        }
      }
    },
    doReplaceBrokenMarkers() {
      for (let i = 0; i < this.sourceMarkers.length; i++) {
        let marker = this.sourceMarkers[i];

        if (marker.region &&
            (marker.time === 0 && marker.markertype === 'setting' && marker.label === 'GROUP|0') ||
            (marker.markertype === 'syllables' && marker.label === '')) {
          marker.region.remove();
          marker.region = null;
          this.sourceMarkers.splice(i,1);
          i--;
        }
        if (marker.region && marker.markertype === 'setting' && marker.label.startsWith('COMMENT| ')) {
          marker.markertype = 'newline';
          marker.label = ''
          marker.region.remove();
          marker.region = null;
          marker.region = this.createRegionMarker(marker);
          this.sourceMarkers.splice(i,1, marker);
        }
      }
    },
    async searchText() {
      this.sourceText = await this.$store.getters.getSearchSongText;
    },
    doSearchText() {
      this.customConfirmParams = {
        header: 'Подтвердите поиск текста',
        body: `Найти в Интернете текст для этой песни?`,
        timeout: 10,
        callback: this.searchText
      }
      this.isCustomConfirmVisible = true;
    },
    getSelectedText() {
      let textComponent = document.getElementById('editor');
      if (textComponent.selectionStart !== undefined) {
        let startPos = textComponent.selectionStart;
        let endPos = textComponent.selectionEnd;
        return  textComponent.value.substring(startPos, endPos);
      }
    },
    eraseMarkers() {
      this.customConfirmParams = {
        header: 'Очистка маркеров',
        body: `Вы действительно хотите удалить все маркеры?`,
        timeout: 10,
        callback: this.doEraseMarkers
      }
      this.isCustomConfirmVisible = true;
    },
    doEraseMarkers() {

      // Удаляем все видимые маркеры
      let countBeatMarkers = this.sourceMarkers.filter(marker => this.isShowMarkerType(marker.markertype)).length;
      while (countBeatMarkers !== 0) {
        for (let i = 0; i < this.sourceMarkers.length; i++) {
          let marker = this.sourceMarkers[i];
          if (this.isShowMarkerType(marker.markertype)) {
            marker.region.remove();
            marker.region = null;
            this.sourceMarkers.splice(i,1);
            break;
          }
        }
        countBeatMarkers = this.sourceMarkers.filter(marker => this.isShowMarkerType(marker.markertype)).length;
      }

      // this.sourceMarkers = [];
      // this.wsRegions.clearRegions();
    },
    addAccent() {
      let textComponent = document.getElementById('editor');
      if (textComponent.selectionStart !== undefined) {
        let startPos = textComponent.selectionStart;
        let endPos = textComponent.selectionEnd;
        let textAccent = '\u0301';
        let textBefore = textComponent.value.substring(0, startPos);
        let textAfter = textComponent.value.substring(endPos);
        let textSelected = textComponent.value.substring(startPos, endPos);
        let result = textBefore + textSelected + textAccent + textAfter;
        this.sourceText = result;
      }
    },
    addWordToDict() {
      let selectedText = this.getSelectedText();
      if (selectedText) {
        this.customConfirmParams = {
          header: 'Добавление слова в словарь',
          body: `Добавить слово «<strong>${selectedText.toLowerCase()}</strong>» в словарь слов с буквой Ё?`,
          timeout: 10,
          callback: this.doAddWordToDict
        }
        this.selectedText = selectedText.toLowerCase();
        this.isCustomConfirmVisible = true;
      }
    },
    doAddWordToDict() {
      let params = {
        dictName: 'Слова с Ё',
        dictValue: this.selectedText,
        dictAction: 'add'
      }
      this.$store.getters.doTfd(params);
    },
    removeWordFromDict() {
      let selectedText = this.getSelectedText();
      if (selectedText) {
        this.customConfirmParams = {
          header: 'Удаление слова в словаря',
          body: `Удалить слово «<strong>${selectedText.toLowerCase()}</strong>» из словарь слов с буквой Ё?`,
          timeout: 10,
          callback: this.doRemoveWordFromDict
        }
        this.selectedText = selectedText.toLowerCase();
        this.isCustomConfirmVisible = true;
      }
    },
    doRemoveWordFromDict() {
      let params = {
        dictName: 'Слова с Ё',
        dictValue: this.selectedText,
        dictAction: 'remove'
      }
      this.$store.getters.doTfd(params);
    },
    loadSong() {
      // let loadSongStartTime = Date.now();
      let currentTime = this.ws.getCurrentTime();
      // let loadStartTime = Date.now();
      this.ws.load('/api/song/' + this.song.id + '/file' + this.sound).then(() => {
        // let loadEndTime = Date.now();
        this.ws.setTime(currentTime);
        this.ws.zoom(this.sliderZoom.value);
        this.ws.setVolume(this.sliderVolume.value);
      });
      // let loadSongEndtTime = Date.now();
    },
    initWavesurfer() {
      this.ws = WaveSurfer.create({
        container: '#waveform',
        waveColor: 'rgb(200, 0, 200)',
        progressColor: 'rgb(100, 0, 100)',
        cursorColor: 'rgb(255, 0, 0)',
        autoCenterImmediately: true,
        autoCenter: true,
        autoScroll: true,
        cursorWidth: 3,
        height: 236,
        // normalize: true,
        barHeight: 1,
        barWidth: 4,
        barRadius: 2,
        hideScrollbar: false,
        plugins: [
          Hover.create({
            lineColor: '#000000',
            lineWidth: 2,
            labelBackground: '#555',
            labelColor: '#fff',
            labelSize: '11px',
            formatTimeCallback: ((seconds) => {
              const mm = Math.floor(seconds/60);
              const ss = Math.floor(seconds) - mm*60;
              const sss = Math.floor((seconds - Math.floor(seconds)) * 1000);
              return (mm < 10 ? '0' : '') + mm + ':' + (ss < 10 ? '0' : '') + ss + '.' + (sss < 10 ? '0' : '') + (sss < 100 ? '0' : '') + sss;
            })
          }),
          TimelinePlugin.create({
            height: 20,
            insertPosition: 'beforebegin',
            timeInterval: 0.2,
            primaryLabelInterval: 5,
            secondaryLabelInterval: 1,
            style: {
              fontSize: '10px',
              color: '#2D5B88',
            },
          }),
          Minimap.create({
            height: 20,
            waveColor: 'rgb(255, 0, 0)',
            progressColor: 'rgb(100, 0, 100)',
          })
        ]
      });

      this.wsRegions = this.ws.registerPlugin(RegionsPlugin.create())

      this.loadSong();

    },
    lockladButtonClass() {
      return this.currentMarker.locklad === 'true' ? 'se-group-button-active' : ''
    },
    merkerButtonClass(isShow) {
      return isShow === true ? 'se-group-button-active' : ''
    },
    soundButtonClass(sound) {
      return sound === this.sound ? 'se-group-button-active' : ''
    },
    beatButtonClass(beat) {
      return beat === this.beat ? 'se-group-button-active' : ''
    },
    editSpeedButtonClass(editSpeed) {
      return editSpeed === this.editSpeed ? 'se-group-button-active' : ''
    },
    editModeTypeButtonClass(editModeType) {
      return editModeType === this.editModeType ? 'se-group-button-active' : ''
    },
    editModeButtonClass() {
      return this.isEditMode ? 'se-edit-mode-on' : ''
    },
    playSpeedButtonClass(playSpeed) {
      return playSpeed === this.playSpeed ? 'se-group-button-active' : ''
    },
    playPauseButtonClass(isPlaying) {
      return isPlaying ? 'se-group-button-active' : ''
    },
    pressedButtonClass(isPressed) {
      return isPressed ? 'se-group-button-active' : ''
    },
    setSound(sound) {
      this.sound = sound;
    },
    setBeat(beat) {
      this.beat = beat;
    },
    onOffLocklad() {
      if (this.currentMarker.locklad === 'true') {
        this.currentMarker.locklad = '';
      } else {
        this.currentMarker.locklad = 'true';
      }
    },
    onOffShowMarkerType(markerType) {
      switch (markerType) {
        case 'syllables': {this.isShowMarkerTypeSyllables = !this.isShowMarkerTypeSyllables; break;}
        case 'setting': {this.isShowMarkerTypeSetting = !this.isShowMarkerTypeSetting; break;}
        case 'newline': {this.isShowMarkerTypeNewline = !this.isShowMarkerTypeNewline; break;}
        case 'endofline': {this.isShowMarkerTypeEndofline = !this.isShowMarkerTypeEndofline; break;}
        case 'endofsyllable': {this.isShowMarkerTypeEndofsyllable = !this.isShowMarkerTypeEndofsyllable; break;}
        case 'unmute': {this.isShowMarkerTypeUnmute = !this.isShowMarkerTypeUnmute; break;}
        case 'note': {this.isShowMarkerTypeNote = !this.isShowMarkerTypeNote; break;}
        case 'chord': {this.isShowMarkerTypeChord = !this.isShowMarkerTypeChord; break;}
        case 'beat': {this.isShowMarkerTypeBeat = !this.isShowMarkerTypeBeat; break;}
        case 'eolch': {this.isShowMarkerTypeEOLCH = !this.isShowMarkerTypeEOLCH; break;}
        case 'eoch': {this.isShowMarkerTypeEOCH = !this.isShowMarkerTypeEOCH; break;}
        case 'nlch': {this.isShowMarkerTypeNLCH = !this.isShowMarkerTypeNLCH; break;}
        case 'eoln': {this.isShowMarkerTypeEOLN = !this.isShowMarkerTypeEOLN; break;}
        case 'eon': {this.isShowMarkerTypeEON = !this.isShowMarkerTypeEON; break;}
        case 'nln': {this.isShowMarkerTypeNLN = !this.isShowMarkerTypeNLN; break;}
      }
    },
    setEditModeType(editModeType) {
      this.editModeType = editModeType;
    },
    setEditSpeed(editSpeed) {
      this.editSpeed = editSpeed;
    },
    setPlaySpeed(playSpeed) {
      this.playSpeed = playSpeed;
    },
    playPause() {
      this.ws.playPause();
    },
    close() {
      this.$emit('close');
    },
    onMIDISuccess(midiAccess) {
      console.log("MIDI ready!");
      this.midi = midiAccess; // store in the global (in real usage, would probably keep in an object instance)
      this.listInputsAndOutputs(this.midi);
      this.startLoggingMIDIInput(this.midi);
    },
    onMIDIFailure(msg) {
      console.error(`Failed to get MIDI access - ${msg}`);
    },
    listInputsAndOutputs(midiAccess) {
      for (const entry of midiAccess.inputs) {
        const input = entry[1];
        console.log(
            `Input port [type:'${input.type}']` +
            ` id:'${input.id}'` +
            ` manufacturer:'${input.manufacturer}'` +
            ` name:'${input.name}'` +
            ` version:'${input.version}'`,
        );
      }

      for (const entry of midiAccess.outputs) {
        const output = entry[1];
        console.log(
            `Output port [type:'${output.type}'] id:'${output.id}' manufacturer:'${output.manufacturer}' name:'${output.name}' version:'${output.version}'`,
        );
      }
    },
    onMIDIMessage(event) {

      for (let i = 0; i < event.data.length; i++) {
        console.log(`event.data[${i}] = '${event.data[i]}'`);
      }

      if (event.data.length === 3) {
        let action = event.data[0];
        let code = event.data[1];
        let volume = event.data[2];
        let note = this.getNote(code);
        if (action === 128) {
          if (this.pressedMidiNotes.has(note)) {
            this.pressedMidiNotes.delete(note);
            console.log(`${note} - отпущено`);
          }
        } else if (action === 144) {
          if (!this.pressedMidiNotes.has(note)) {
            this.pressedMidiNotes.add(note);
            console.log(`${note} - нажато с силой ${volume}`);

            if (this.isEditMode) {
              if (this.currentMarker !== undefined) {
                switch (this.editModeType) {
                  case 'syllables':
                    if (this.currentMarker.markertype === 'syllables') {
                      this.currentMarker.note = note;
                      this.currentMarker.region.setContent(this.getRegionContentFromMarker(this.currentMarker));
                      this.sourceMarkers.splice(0,1,this.sourceMarkers[0]);
                      this.goToNextMarker('syllables');
                    }
                    break;
                  case 'note':
                    if (this.currentMarker.markertype === 'note') {
                      this.currentMarker.note = note;
                      this.currentMarker.region.setContent(this.getRegionContentFromMarker(this.currentMarker));
                      this.sourceMarkers.splice(0,1,this.sourceMarkers[0]);
                      this.goToNextMarker('note');
                    }
                    break;
                  case 'chord':
                    break;
                }

              }
              // this.addMarker('note', note, true);
            }

          }

        }

      }
    },
    startLoggingMIDIInput(midiAccess) {
      midiAccess.inputs.forEach((entry) => {
        entry.onmidimessage = this.onMIDIMessage;
      });
    },
    getNote(bite) {
      let code = bite - 36;
      let notes = ['C','C#','D','D#','E','F','F#','G','G#','A','A#','B'];
      let note = code % 12;
      let octave = Math.floor(code / 12) + 1;
      return `${notes[note]}|${octave}`
    },
    isShowMarkerType(markerType) {
      return this.markerTypesToShow.includes(markerType);
    },
    redrawMarkers() {
      if (this.sourceMarkers.length > 0) {
        this.wsRegions.clearRegions();
        for (let index = 0; index < this.sourceMarkers.length; index++) {
          let marker = this.sourceMarkers[index];
          marker.region = this.createRegionMarker(marker);
        }
      }
    },
    getStringsForAllNotesInSong() {
      const markersList = this.sourceMarkers
          .filter(marker => (marker.markertype === 'syllables' || marker.markertype === 'note'))
          .filter(marker => marker.note)
      const notesList = markersList.map(marker => marker.note)
      const notesSetArray = Array.from(new Set(notesList));
      let notesAndStringsArray = [];
      for (let i = 0; i < notesSetArray.length; i++) {
        const note = notesSetArray[i];
        notesAndStringsArray.push([note, this.getStrings(note)]);
      }
      let result = [];
      let variants = [];
      for (let startLad = 0; startLad < 20; startLad++) {
        let variant = []; // вариант для startLad
        let minLad = 20;
        let maxLad = 0;
        let isBadLad = false;
        for (let markerIndex = 0; markerIndex < markersList.length; markerIndex++) {
          const marker = markersList[markerIndex];
          const stringsForNote = notesAndStringsArray.filter(item => item[0] === marker.note)[0][1]; // массив пар струна-лад
          let tmpArray = stringsForNote.filter(item => item[1] >= startLad).map(item => [Math.abs(item[1]-startLad), item]);
          if (tmpArray !== undefined && tmpArray.length > 0) {
            let minDiffIndex = 0;
            let minDiff = tmpArray[minDiffIndex][0];
            for (let i = 0; i < tmpArray.length; i++) {
              if (minDiff > tmpArray[i][0]) {
                minDiffIndex = i;
                minDiff = tmpArray[i][0];
              }
            }
            if (minLad > tmpArray[minDiffIndex][1][1]) {
              minLad = tmpArray[minDiffIndex][1][1];
            }
            if (maxLad < tmpArray[minDiffIndex][1][1]) {
              maxLad = tmpArray[minDiffIndex][1][1];
            }
            variant.push(tmpArray[minDiffIndex][1]);
          } else {
            isBadLad = true;
            break;
          }
        }
        if (variant.length > 0 && !isBadLad) {
          variants.push({variant: variant, diff: Math.abs(maxLad - minLad)});
        }
      }

      // console.log('variants', variants);
      if (variants.length === 0) return result;

      let minDiff =  variants[0].diff
      for (let i = 0; i < variants.length; i++) {
        let variant = variants[i];
        if (minDiff > variant.diff) {
          minDiff = variant.diff
        }
      }

      for (let i = 0; i < variants.length; i++) {
        let variant = variants[i];
        if (minDiff === variant.diff) {
          result.push(variant.variant);
        }
      }

      if (this.indexTabsVariant > result.length - 1) this.indexTabsVariant = result.length - 1;

      console.log('result', result);

      return result;
    },
    getStrings(note) {
      let result = [];
      const guitar = [
          ['E|4','F|4','F#|4','G|4','G#|4','A|4','A#|4','B|4','C|5','C#|5','D|5','D#|5','E|5','F|5','F#|5','G|5','G#|5','A|5','A#|5','B|5','C|6'],
          ['B|3','C|4','C#|4','D|4','D#|4','E|4','F|4','F#|4','G|4','G#|4','A|4','A#|4','B|4','C|5','C#|5','D|5','D#|5','E|5','F|5','F#|5','G|5'],
          ['G|3','G#|3','A|3','A#|3','B|3','C|4','C#|4','D|4','D#|4','E|4','F|4','F#|4','G|4','G#|4','A|4','A#|4','B|4','C|5','C#|5','D|5','D#|5'],
          ['D|3','D#|3','E|3','F|3','F#|3','G|3','G#|3','A|3','A#|3','B|3','C|4','C#|4','D|4','D#|4','E|4','F|4','F#|4','G|4','G#|4','A|4','A#|4'],
          ['A|2','A#|2','B|2','C|3','C#|3','D|3','D#|3','E|3','F|3','F#|3','G|3','G#|3','A|3','A#|3','B|3','C|4','C#|4','D|4','D#|4','E|4','F|4'],
          ['E|2','F|2','F#|2','G|2','G#|2','A|2','A#|2','B|2','C|3','C#|3','D|3','D#|3','E|3','F|3','F#|3','G|3','G#|3','A|3','A#|3','B|3','C|4']
      ]
      for (let stringIndex = 0; stringIndex < guitar.length; stringIndex++) {
        const string = guitar[stringIndex];
        for (let lad = 0; lad < string.length; lad++) {
          if (note === string[lad]) {
            result.push([stringIndex, lad]);
            break;
          }
        }
      }
      return result
    }
  }
}
</script>

<style scoped>

.se-group-button {
  border: solid black thin;
  border-radius: 5px;
  background-color: white;
}
.se-group-button:hover {
  background-color: lightpink;
}
.se-group-button:focus {
  background-color: darksalmon;
}
.se-group-button[disabled] {
  background-color: lightgray;
}

.se-group-button-active {
  background-color: dodgerblue;
}
.se-group-button-active:hover {
  background-color: lightskyblue;
}

.se-subsedit-area {
  background: #FFFFFF;
  box-shadow: 2px 2px 20px 1px;
  overflow-x: auto;
  display: flex;
  flex-direction: column;
  width: calc(100vw - 50px);
  height: calc(100vh - 50px);
  position: relative;
}

.se-subsedit-header-song-name {
  text-align: center;
  font-size: 24pt;
  line-height: 75%;
  margin-right: auto;
}
.se-subsedit-header-song-description {
  text-align: center;
  font-size: 16pt;
  margin: 0 auto;
}

.se-voice {
  margin: 0 auto;
}

.se-sound {
  margin: 0 auto;
}
.se-beat {
  margin: 0 auto;
}
.se-markers {
  margin: 0 auto;
}

.se-group-edit-speed-buttons {
  display: flex;
  align-items: center;
}

.se-group-edit-play-speed-buttons {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.se-group-controls-markers-buttons {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.se-group-controls-region-buttons {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.se-group-play-speed-buttons {

}

.se-icon-play-pause {
  width: 122px;
  height: 20px;
}

.se-group-control-buttons {
  display: flex;
  margin: 0 10px;
}

.se-group-markers-buttons {
  display: flex;
  margin: 0 10px;
}

.se-edit-mode {
  display: block;
  border: 1px solid black;
  border-radius: 50%;
  background-color: red;
  width: 20px;
  height: 20px;
  margin: 0 5px;
}

.se-edit-mode-on {
  background-color: green;
}

.se-icon-fast-backward,
.se-icon-fast-fast-backward,
.se-icon-step-backward,
.se-icon-step-forward,
.se-icon-fast-forward,
.se-icon-fast-fast-forward,
.se-icon-previous-marker,
.se-icon-next-marker
{
  width: 20px;
  height: 20px;
}

.se-icon-add-marker,
.se-icon-delete-marker,
.se-icon-end-of-line-marker,
.se-icon-end-of-line-and-add-marker,
.se-icon-new-line-marker,
.se-icon-mute-marker,
.se-icon-comment-marker,
.se-icon-group0-marker,
.se-icon-group1-marker,
.se-icon-group2-marker,
.se-icon-group3-marker,
.se-icon-group4-marker
{
  width: 20px;
  height: 20px;
}

.se-icon-40 {
  width: 40px;
  height: 40px;
}

.se-subsedit-body {
  width: 100%;
  height: 100%;
  font-size: 14px;
  font-family: sans-serif;
  display: grid;
  gap: 5px;
  background-color: #ddd;
  grid-template-columns: 300px 100px 1fr;
  grid-template-rows: 50px 271px 50px 50px 1fr 50px;
}

[class^='se-grid-item'] {
  outline: 1px #f90 dashed;
  display: grid;
  background-color: goldenrod;
  align-items: center;
  justify-content: center;
}

.se-grid-item-header {
  grid-column: 1 / -1;
  grid-row: 1 / 1;
  padding: 15px;
  display: flex;
}

.se-grid-item-waveform {
  grid-column: 1 / -1;
  grid-row: 2 / 2;
  display: flex;
}

.se-item-waveform {
  width: 80%;
  /*height: auto;*/
  background-color: #FFFFFF;
  display: block;
}
.se-item-left-waveform {
  width: 10%;
  margin: 5px 5px 5px 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}
.se-item-left-label-and-input {
  display: flex;
}
.se-item-left-label {
  font-size: small;
  text-align: right;
  width: 50px;
  padding-right: 2px;
  padding-top: 2px;
}
.se-item-left-input-field {
  display: block;
  padding-bottom: 3px;
  width: 100px;
  text-align: left;
  font-size: small;
  border-radius: 5px;
  border-color: black;
  border-width: thin;
}
.se-item-right-waveform {
  width: 10%;
  display: block;
}
.se-grid-item-slider {
  grid-column: 1 / 3;
  grid-row: 3 / 3;
  margin: 0 5px;
  display: flex;
  flex-direction: row;
  justify-content: space-between;
}

.se-item-slider-zoom {
  flex: 3;
}
.se-item-slider-volume {
  flex: 1;
  background: transparent;
}
.se-item-slider-volume[type=range] {
  height: 25px;
  -webkit-appearance: none;
  margin: 10px 0;
  width: 100%;
}
.se-item-slider-volume[type=range]::-webkit-slider-runnable-track {
  width: 100%;
  height: 5px;
  cursor: pointer;
  box-shadow: 0 0 0 #000000;
  background: green;
  border-radius: 1px;
  border: 0 solid #000000;
}
.se-item-slider-volume[type=range]::-webkit-slider-thumb {
  box-shadow: 0 0 0 #000000;
  border: 1px solid darkgreen;
  height: 14px;
  width: 14px;
  border-radius: 25px;
  background: lime;
  cursor: pointer;
  -webkit-appearance: none;
  margin-top: -5px;
}

.se-grid-item-controls {
  grid-column: 3 / 3;
  grid-row: 3 / 3;
  display: flex;
}

.se-grid-item-sourcetext {
  grid-column: 1 / 3;
  grid-row: 4 / 6;
  background-color: #eeeeee;
}

.se-grid-item-tail {
  grid-column: 3 / 3;
  grid-row: 4 / 4;
  /*display: block;*/
}
.se-tail {
  display: block;
  margin-top: -11px;
  width: 100%;
  /*margin-left: -125px;*/
}

.se-grid-item-wrapper-text {
  grid-column: 3 / 3;
  grid-row: 5 / 5;
  display: block;
}
.se-grid-item-text {
  column-count: 4;
  column-fill: auto;
  overflow: auto;
  background-color: black;
  display: block;
  text-align: left;
  max-height: 619px;
}
.se-grid-item-notes {
  column-count: 3;
  column-fill: auto;
  overflow: auto;
  background-color: black;
  display: block;
  text-align: left;
  max-height: 619px;
}
.se-grid-item-chords {
  column-count: 5;
  column-fill: auto;
  overflow: auto;
  background-color: black;
  display: block;
  text-align: left;
  max-height: 619px;
}
.se-grid-item-footer {
  grid-column: 1 / -1;
  grid-row: 6 / 6;
  background-color: #2c3e50;
  padding: 10px;
  color: white;
  font-size: larger;
  font-weight: 300;
  display: flex;
  justify-content: flex-end;
}

.se-subsedit-modal-backdrop {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: rgba(0, 0, 0, 0.3);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1055;
}



.se-modal-header,
.se-modal-footer {
  padding: 15px;
  display: flex;
}

.se-modal-header {
  border-bottom: 1px solid #eeeeee;
  color: #4AAE9B;
  justify-content: space-between;
}

.se-modal-footer {
  border-top: 1px solid #eeeeee;
  justify-content: flex-end;
}

.se-modal-body {
  position: relative;
  padding: 20px 10px;
}

.se-btn-close {
  border: 1px solid white;
  border-radius: 10px;
  font-size: 20px;
  cursor: pointer;
  font-weight: bold;
  color: white;
  background: transparent;
  width: 100px;
  height: auto;
}

.se-btn-green {
  color: white;
  background: #4AAE9B;
  border: 1px solid #4AAE9B;
  border-radius: 2px;
}

.se-modal-fade-enter,
.se-modal-fade-leave-active {
  opacity: 0;
}

.se-modal-fade-enter-active,
.se-modal-fade-leave-active {
  transition: opacity .5s ease
}

#waveform ::part(syllables) {
  text-indent: 3px;
  font-family: sans-serif;
  font-size: 14px;
  font-weight: normal;
}

#waveform ::part(setting) {
  text-indent: 3px;
  font-family: sans-serif;
  font-size: 10px;
  font-weight: 900;
  color: blue;
}

#waveform ::part(endofline) {
  text-indent: 3px;
  font-family: sans-serif;
  font-size: 18px;
  font-weight: 900;
  color: red;
}

#waveform ::part(endofsyllable) {
  text-indent: 3px;
  font-family: sans-serif;
  font-size: 18px;
  font-weight: 900;
  color: red;
}

#waveform ::part(newline) {
  text-indent: 3px;
  font-family: sans-serif;
  font-size: 18px;
  font-weight: 900;
}

#waveform ::part(unmute) {
  text-indent: 3px;
  font-family: sans-serif;
  font-size: 18px;
  font-weight: 900;
}

#waveform ::part(region-content) {
  padding: 0;
  margin: 0;
  display: block;
  /*background-color: pink;*/
  /*width: fit-content;*/
  /*border: 1px solid black;*/
}

#waveform ::part(hover-label):before {
  content: '⏱️ ';
}
#waveform ::part(hover-label) {
  margin-top: 195px;
}

</style>