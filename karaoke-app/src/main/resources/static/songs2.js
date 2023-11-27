var stompClient = null;

function connect() {
    var socket = new SockJS('/message');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);
        stompClient.subscribe('/messages/recordchange', function (recordchange) {
            console.log("Получено сообщение...");
            updateSettingsFromMessage(JSON.parse(recordchange.body));
        });
    });
}

var currentTimeStamp = new Date().getTime();
var currentRow;
var prev_album;
var prev_author;
const prefix_link_boosty = "https://boosty.to/svoemesto/posts/";
const prefix_link_vk_wall = "https://vk.com/wall-";
const prefix_link_dzen_play = "https://dzen.ru/video/watch/";
const prefix_link_dzen_edit = "https://dzen.ru/profile/editor/svoemesto/publications?videoEditorPublicationId=";
const prefix_link_vk_play = "https://vk.com/video";
const prefix_link_vk_edit = "https://vk.com/video";
const prefix_link_telegram_play = "https://t.me/svoemestokaraoke/";
const prefix_link_telegram_edit = "https://t.me/svoemestokaraoke/";

var el_image_author = document.getElementById("image_author");
var el_image_album = document.getElementById("image_album");
var el_id_text = document.getElementById("settings_id_text");
var el_id = document.getElementById("settings_id");
var el_songName = document.getElementById("settings_songName");
var el_author = document.getElementById("settings_author");
var el_year = document.getElementById("settings_year");
var el_album = document.getElementById("settings_album");
var el_track = document.getElementById("settings_track");
var el_date = document.getElementById("settings_date");
var el_time = document.getElementById("settings_time");
var el_key = document.getElementById("settings_key");
var el_bpm = document.getElementById("settings_bpm");
var el_ms = document.getElementById("settings_ms");
var el_fileName = document.getElementById("settings_fileName");
var el_rootFolder = document.getElementById("settings_rootFolder");
var el_idBoosty = document.getElementById("settings_idBoosty");
var el_idVk = document.getElementById("settings_idVk");
var el_idYoutubeLyrics = document.getElementById("settings_idYoutubeLyrics");
var el_idYoutubeKaraoke = document.getElementById("settings_idYoutubeKaraoke");
var el_idYoutubeChords = document.getElementById("settings_idYoutubeChords");
var el_idVkLyrics = document.getElementById("settings_idVkLyrics");
var el_idVkKaraoke = document.getElementById("settings_idVkKaraoke");
var el_idVkChords = document.getElementById("settings_idVkChords");
var el_idTelegramLyrics = document.getElementById("settings_idTelegramLyrics");
var el_idTelegramKaraoke = document.getElementById("settings_idTelegramKaraoke");
var el_idTelegramChords = document.getElementById("settings_idTelegramChords");
var el_tags = document.getElementById("settings_tags");
var el_status = document.getElementById("select_status");

var el_button_link_boosty = document.getElementById("button_link_boosty");
var el_button_link_vk = document.getElementById("button_link_vk");
var el_button_link_play_YoutubeLyrics = document.getElementById("button_link_play_YoutubeLyrics");
var el_button_link_edit_YoutubeLyrics = document.getElementById("button_link_edit_YoutubeLyrics");
var el_button_link_play_YoutubeKaraoke = document.getElementById("button_link_play_YoutubeKaraoke");
var el_button_link_edit_YoutubeKaraoke = document.getElementById("button_link_edit_YoutubeKaraoke");
var el_button_link_play_YoutubeChords = document.getElementById("button_link_play_YoutubeChords");
var el_button_link_edit_YoutubeChords = document.getElementById("button_link_edit_YoutubeChords");
var el_button_link_play_VkLyrics = document.getElementById("button_link_play_VkLyrics");
var el_button_link_edit_VkLyrics = document.getElementById("button_link_edit_VkLyrics");
var el_button_link_play_VkKaraoke = document.getElementById("button_link_play_VkKaraoke");
var el_button_link_edit_VkKaraoke = document.getElementById("button_link_edit_VkKaraoke");
var el_button_link_play_VkChords = document.getElementById("button_link_play_VkChords");
var el_button_link_edit_VkChords = document.getElementById("button_link_edit_VkChords");
var el_button_link_play_TelegramLyrics = document.getElementById("button_link_play_TelegramLyrics");
var el_button_link_edit_TelegramLyrics = document.getElementById("button_link_edit_TelegramLyrics");
var el_button_link_play_TelegramKaraoke = document.getElementById("button_link_play_TelegramKaraoke");
var el_button_link_edit_TelegramKaraoke = document.getElementById("button_link_edit_TelegramKaraoke");
var el_button_link_play_TelegramChords = document.getElementById("button_link_play_TelegramChords");
var el_button_link_edit_TelegramChords = document.getElementById("button_link_edit_TelegramChords");

$(function () {

    currentTimeStamp = new Date().getTime();

    el_image_author = document.getElementById("image_author");
    el_image_album = document.getElementById("image_album");
    el_id_text = document.getElementById("settings_id_text");
    el_id = document.getElementById("settings_id");
    el_songName = document.getElementById("settings_songName");
    el_author = document.getElementById("settings_author");
    el_year = document.getElementById("settings_year");
    el_album = document.getElementById("settings_album");
    el_track = document.getElementById("settings_track");
    el_date = document.getElementById("settings_date");
    el_time = document.getElementById("settings_time");
    el_key = document.getElementById("settings_key");
    el_bpm = document.getElementById("settings_bpm");
    el_ms = document.getElementById("settings_ms");
    el_fileName = document.getElementById("settings_fileName");
    el_rootFolder = document.getElementById("settings_rootFolder");
    el_idBoosty = document.getElementById("settings_idBoosty");
    el_idVk = document.getElementById("settings_idVk");
    el_idYoutubeLyrics = document.getElementById("settings_idYoutubeLyrics");
    el_idYoutubeKaraoke = document.getElementById("settings_idYoutubeKaraoke");
    el_idYoutubeChords = document.getElementById("settings_idYoutubeChords");
    el_idVkLyrics = document.getElementById("settings_idVkLyrics");
    el_idVkKaraoke = document.getElementById("settings_idVkKaraoke");
    el_idVkChords = document.getElementById("settings_idVkChords");
    el_idTelegramLyrics = document.getElementById("settings_idTelegramLyrics");
    el_idTelegramKaraoke = document.getElementById("settings_idTelegramKaraoke");
    el_idTelegramChords = document.getElementById("settings_idTelegramChords");
    el_tags = document.getElementById("settings_tags");
    el_status = document.getElementById("select_status");

    el_button_link_boosty = document.getElementById("button_link_boosty");
    el_button_link_vk = document.getElementById("button_link_vk");
    el_button_link_play_YoutubeLyrics = document.getElementById("button_link_play_YoutubeLyrics");
    el_button_link_edit_YoutubeLyrics = document.getElementById("button_link_edit_YoutubeLyrics");
    el_button_link_play_YoutubeKaraoke = document.getElementById("button_link_play_YoutubeKaraoke");
    el_button_link_edit_YoutubeKaraoke = document.getElementById("button_link_edit_YoutubeKaraoke");
    el_button_link_play_YoutubeChords = document.getElementById("button_link_play_YoutubeChords");
    el_button_link_edit_YoutubeChords = document.getElementById("button_link_edit_YoutubeChords");
    el_button_link_play_VkLyrics = document.getElementById("button_link_play_VkLyrics");
    el_button_link_edit_VkLyrics = document.getElementById("button_link_edit_VkLyrics");
    el_button_link_play_VkKaraoke = document.getElementById("button_link_play_VkKaraoke");
    el_button_link_edit_VkKaraoke = document.getElementById("button_link_edit_VkKaraoke");
    el_button_link_play_VkChords = document.getElementById("button_link_play_VkChords");
    el_button_link_edit_VkChords = document.getElementById("button_link_edit_VkChords");
    el_button_link_play_TelegramLyrics = document.getElementById("button_link_play_TelegramLyrics");
    el_button_link_edit_TelegramLyrics = document.getElementById("button_link_edit_TelegramLyrics");
    el_button_link_play_TelegramKaraoke = document.getElementById("button_link_play_TelegramKaraoke");
    el_button_link_edit_TelegramKaraoke = document.getElementById("button_link_edit_TelegramKaraoke");
    el_button_link_play_TelegramChords = document.getElementById("button_link_play_TelegramChords");
    el_button_link_edit_TelegramChords = document.getElementById("button_link_edit_TelegramChords");

});


function setDisabledElements(value) {

    el_songName.disabled = value;
    el_author.disabled = value;
    el_year.disabled = value;
    el_album.disabled = value;
    el_track.disabled = value;
    el_date.disabled = value;
    el_time.disabled = value;
    el_key.disabled = value;
    el_bpm.disabled = value;
    el_ms.disabled = value;
    el_fileName.disabled = value;
    el_rootFolder.disabled = value;
    el_idBoosty.disabled = value;
    el_idVk.disabled = value;
    el_idYoutubeLyrics.disabled = value;
    el_idYoutubeKaraoke.disabled = value;
    el_idYoutubeChords.disabled = value;
    el_idVkLyrics.disabled = value;
    el_idVkKaraoke.disabled = value;
    el_idVkChords.disabled = value;
    el_idTelegramLyrics.disabled = value;
    el_idTelegramKaraoke.disabled = value;
    el_idTelegramChords.disabled = value;
    el_status.disabled = value;
    el_tags.disabled = value;

    el_button_link_boosty.disabled = (el_idBoosty.value === '');
    el_button_link_vk.disabled = (el_idVk.value === '');
    el_button_link_play_YoutubeLyrics.disabled = (el_idYoutubeLyrics.value === '');
    el_button_link_edit_YoutubeLyrics.disabled = (el_idYoutubeLyrics.value === '');
    el_button_link_play_YoutubeKaraoke.disabled = (el_idYoutubeKaraoke.value === '');
    el_button_link_edit_YoutubeKaraoke.disabled = (el_idYoutubeKaraoke.value === '');
    el_button_link_play_YoutubeChords.disabled = (el_idYoutubeChords.value === '');
    el_button_link_edit_YoutubeChords.disabled = (el_idYoutubeChords.value === '');

    el_button_link_play_VkLyrics.disabled = (el_idVkLyrics.value === '');
    el_button_link_edit_VkLyrics.disabled = (el_idVkLyrics.value === '');
    el_button_link_play_VkKaraoke.disabled = (el_idVkKaraoke.value === '');
    el_button_link_edit_VkKaraoke.disabled = (el_idVkKaraoke.value === '');
    el_button_link_play_VkChords.disabled = (el_idVkChords.value === '');
    el_button_link_edit_VkChords.disabled = (el_idVkChords.value === '');

    el_button_link_play_TelegramLyrics.disabled = (el_idTelegramLyrics.value === '');
    el_button_link_edit_TelegramLyrics.disabled = (el_idTelegramLyrics.value === '');
    el_button_link_play_TelegramKaraoke.disabled = (el_idTelegramKaraoke.value === '');
    el_button_link_edit_TelegramKaraoke.disabled = (el_idTelegramKaraoke.value === '');
    el_button_link_play_TelegramChords.disabled = (el_idTelegramChords.value === '');
    el_button_link_edit_TelegramChords.disabled = (el_idTelegramChords.value === '');

    const buttonTogglesStatus = document.getElementsByName('btn_status');
    for (let i = 0; i < buttonTogglesStatus.length; i++) {
        if (value === true) {
            buttonTogglesStatus[i].classList.remove('active');
        }
        buttonTogglesStatus[i].disabled = value;
    }

}


function updateFormElementsFromTableRows(row) {

    prev_album = el_album.value;
    prev_author = el_author.value;

    highlightCurrentRow(currentRow, false);
    currentRow = row;
    highlightCurrentRow(currentRow, true);

    var id = currentRow.id.substring(3);
    el_id.value = document.getElementById(`fld_${id}_id`).innerText;
    el_songName.value = document.getElementById(`fld_${id}_songName`).innerText;
    doCensored();
    el_author.value = document.getElementById(`fld_${id}_author`).innerText;
    el_year.value = document.getElementById(`fld_${id}_year`).innerText;
    el_album.value = document.getElementById(`fld_${id}_album`).innerText;
    el_track.value = document.getElementById(`fld_${id}_track`).innerText;
    el_date.value = document.getElementById(`fld_${id}_date`).innerText;
    el_time.value = document.getElementById(`fld_${id}_time`).innerText;
    el_key.value = document.getElementById(`fld_${id}_key`).innerText;
    el_bpm.value = document.getElementById(`fld_${id}_bpm`).innerText;
    el_ms.value = document.getElementById(`fld_${id}_ms`).innerText;
    el_fileName.value = document.getElementById(`fld_${id}_fileName`).innerText;
    el_rootFolder.value = document.getElementById(`fld_${id}_rootFolder`).innerText;
    el_idBoosty.value = document.getElementById(`fld_${id}_idBoosty`).innerText;
    el_idVk.value = document.getElementById(`fld_${id}_idVk`).innerText;
    el_idYoutubeLyrics.value = document.getElementById(`fld_${id}_idYoutubeLyrics`).innerText;
    el_idYoutubeKaraoke.value = document.getElementById(`fld_${id}_idYoutubeKaraoke`).innerText;
    el_idYoutubeChords.value = document.getElementById(`fld_${id}_idYoutubeChords`).innerText;
    el_idVkLyrics.value = document.getElementById(`fld_${id}_idVkLyrics`).innerText;
    el_idVkKaraoke.value = document.getElementById(`fld_${id}_idVkKaraoke`).innerText;
    el_idVkChords.value = document.getElementById(`fld_${id}_idVkChords`).innerText;
    el_idTelegramLyrics.value = document.getElementById(`fld_${id}_idTelegramLyrics`).innerText;
    el_idTelegramKaraoke.value = document.getElementById(`fld_${id}_idTelegramKaraoke`).innerText;
    el_idTelegramChords.value = document.getElementById(`fld_${id}_idTelegramChords`).innerText;
    el_tags.value = document.getElementById(`fld_${id}_tags`).innerText;
    el_status.value = document.getElementById(`fld_${id}_idStatus`).innerText;

    var buttonTogglesStatus = document.getElementsByName('btn_status');
    for (let i = 0; i < buttonTogglesStatus.length; i++) {
        buttonTogglesStatus[i].classList.remove('active');
        if (buttonTogglesStatus[i].value === document.getElementById("select_status").selectedIndex.toString()) {
            buttonTogglesStatus[i].classList.add('active');
        }
    }

    getSongPictureAuthor();
    getSongPictureAlbum();

}

function updateButtonsLinks() {

    el_button_link_boosty.href = prefix_link_boosty + el_idBoosty.value
    el_button_link_vk.href = prefix_link_vk_wall + el_idVk.value;
    el_button_link_play_YoutubeLyrics.href = prefix_link_dzen_play + el_idYoutubeLyrics.value
    el_button_link_edit_YoutubeLyrics.href = prefix_link_dzen_edit + el_idYoutubeLyrics.value
    el_button_link_play_YoutubeKaraoke.href = prefix_link_dzen_play + el_idYoutubeKaraoke.value
    el_button_link_edit_YoutubeKaraoke.href = prefix_link_dzen_edit + el_idYoutubeKaraoke.value
    el_button_link_play_YoutubeChords.href = prefix_link_dzen_play + el_idYoutubeChords.value
    el_button_link_edit_YoutubeChords.href = prefix_link_dzen_edit + el_idYoutubeChords.value

    el_button_link_play_VkLyrics.href = prefix_link_vk_play + el_idVkLyrics.value
    el_button_link_edit_VkLyrics.href = prefix_link_vk_edit + el_idVkLyrics.value
    el_button_link_play_VkKaraoke.href = prefix_link_vk_play + el_idVkKaraoke.value
    el_button_link_edit_VkKaraoke.href = prefix_link_vk_edit + el_idVkKaraoke.value
    el_button_link_play_VkChords.href = prefix_link_vk_play + el_idVkChords.value
    el_button_link_edit_VkChords.href = prefix_link_vk_edit + el_idVkChords.value

    el_button_link_play_TelegramLyrics.href = prefix_link_telegram_play + el_idTelegramLyrics.value
    el_button_link_edit_TelegramLyrics.href = prefix_link_telegram_edit + el_idTelegramLyrics.value
    el_button_link_play_TelegramKaraoke.href = prefix_link_telegram_play + el_idTelegramKaraoke.value
    el_button_link_edit_TelegramKaraoke.href = prefix_link_telegram_edit + el_idTelegramKaraoke.value
    el_button_link_play_TelegramChords.href = prefix_link_telegram_play + el_idTelegramChords.value
    el_button_link_edit_TelegramChords.href = prefix_link_telegram_edit + el_idTelegramChords.value

}

function highlightCurrentRow(row, isSelected) {

    if (row !== undefined) {
        var id = row.id.substring(3);
        highlightElement(document.getElementById(`fld_${id}_id`), isSelected);
        highlightElement(document.getElementById(`fld_${id}_songName`), isSelected);
        highlightElement(document.getElementById(`fld_${id}_author`), isSelected);
        highlightElement(document.getElementById(`fld_${id}_year`), isSelected);
        highlightElement(document.getElementById(`fld_${id}_album`), isSelected);
        highlightElement(document.getElementById(`fld_${id}_track`), isSelected);
        highlightElement(document.getElementById(`fld_${id}_date`), isSelected);
        highlightElement(document.getElementById(`fld_${id}_time`), isSelected);
        highlightElement(document.getElementById(`fld_${id}_flagBoosty`), isSelected);
        highlightElement(document.getElementById(`fld_${id}_flagVk`), isSelected);
        highlightElement(document.getElementById(`fld_${id}_flagYoutubeLyrics`), isSelected);
        highlightElement(document.getElementById(`fld_${id}_flagYoutubeKaraoke`), isSelected);
        highlightElement(document.getElementById(`fld_${id}_flagVkLyrics`), isSelected);
        highlightElement(document.getElementById(`fld_${id}_flagVkKaraoke`), isSelected);
        highlightElement(document.getElementById(`fld_${id}_flagTelegramLyrics`), isSelected);
        highlightElement(document.getElementById(`fld_${id}_flagTelegramKaraoke`), isSelected);
        highlightElement(document.getElementById(`fld_${id}_tags`), isSelected);
        highlightElement(document.getElementById(`fld_${id}_status`), isSelected);
    }

}

function highlightElement(element, isSelected) {
    if (element !== undefined) {
        if (isSelected === true) {
            element.style.borderTopStyle = "solid";
            element.style.borderTopWidth = "2px";
            element.style.borderTopColor = "red";
            element.style.borderBottomStyle = "solid";
            element.style.borderBottomWidth = "2px";
            element.style.borderBottomColor = "red";
        } else {
            element.style.borderTopStyle = "none";
            element.style.borderTopWidth = "thin";
            element.style.borderTopColor = "black";
            element.style.borderBottomStyle = "none";
            element.style.borderBottomWidth = "thin";
            element.style.borderBottomColor = "black";
        }
    }
}


function mouseClickRow(row) {
    updateFormElementsFromTableRows(row);
    setDisabledElements(false);
    updateButtonsLinks();
}

function doPostForm() {

    var dataString = $('#myForm').serialize();
    var id = el_id.value;

    updateButtonsLinks();

    $.ajax({
        type: "POST",
        url: "/songs2_update",
        data: dataString,
        success: function () {

            // updateSettings(id)
        }
    });
    return false;
}

function doFilterForm() {
    var filter_id = encodeURIComponent(document.getElementById("filter_id").value);
    var filter_songName = encodeURIComponent(document.getElementById("filter_songName").value);
    var filter_author = encodeURIComponent(document.getElementById("filter_author").value);
    var filter_year = encodeURIComponent(document.getElementById("filter_year").value);
    var filter_album = encodeURIComponent(document.getElementById("filter_album").value);
    var filter_track = encodeURIComponent(document.getElementById("filter_track").value);
    var filter_tags = encodeURIComponent(document.getElementById("filter_tags").value);
    var filter_date = encodeURIComponent(document.getElementById("filter_date").value);
    var filter_time = encodeURIComponent(document.getElementById("filter_time").value);
    var filter_status = encodeURIComponent(document.getElementById("filter_status").value);
    var filter_flag_boosty = encodeURIComponent(document.getElementById("filter_flag_boosty").value);
    var filter_flag_vk = encodeURIComponent(document.getElementById("filter_flag_vk").value);
    var filter_flag_yl = encodeURIComponent(document.getElementById("filter_flag_yl").value);
    var filter_flag_yk = encodeURIComponent(document.getElementById("filter_flag_yk").value);
    var filter_flag_yc = encodeURIComponent(document.getElementById("filter_flag_yc").value);
    var filter_flag_vkl = encodeURIComponent(document.getElementById("filter_flag_vkl").value);
    var filter_flag_vkk = encodeURIComponent(document.getElementById("filter_flag_vkk").value);
    var filter_flag_vkc = encodeURIComponent(document.getElementById("filter_flag_vkc").value);
    var url = "/songs2?filter_id=" + filter_id +
        "&filter_songName=" + filter_songName +
        "&filter_author=" + filter_author +
        "&filter_year=" + filter_year +
        "&filter_album=" + filter_album +
        "&filter_track=" + filter_track +
        "&filter_tags=" + filter_tags +
        "&filter_date=" + filter_date +
        "&filter_time=" + filter_time +
        "&filter_status=" + filter_status +
        "&flag_boosty=" + filter_flag_boosty +
        "&flag_vk=" + filter_flag_vk +
        "&flag_youtube_lyrics=" + filter_flag_yl +
        "&flag_youtube_karaoke=" + filter_flag_yk +
        "&flag_youtube_chords=" + filter_flag_yc +
        "&flag_vk_lyrics=" + filter_flag_vkl +
        "&flag_vk_karaoke=" + filter_flag_vkk +
        "&flag_vk_chords=" + filter_flag_vkc;
    window.location.href = url;

}

function copySongName() {
    navigator.clipboard.writeText(el_songName.value);
}

function doRowColor() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/color", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        var row = document.getElementById("row" + id);
        row.style.backgroundColor = result;
    }
}

function doTextYoutubeLyricsWOHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textyoutubelyricswoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
    }
}

function doTextYoutubeLyricsBtWOHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textyoutubelyricsbtwoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
    }
}

function doTextYoutubeKaraokeWOHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textyoutubekaraokewoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
    }
}

function doTextYoutubeKaraokeBtWOHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textyoutubekaraokebtwoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
    }
}

function doTextYoutubeChordsWOHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textyoutubechordswoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
    }
}

function doTextYoutubeChordsBtWOHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textyoutubechordsbtwoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
    }
}


function doTextYoutubeLyricsHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textyoutubelyricsheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
    }
}

function doTextYoutubeLyricsBtHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textyoutubelyricsbtheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
    }
}

function doTextYoutubeKaraokeHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textyoutubekaraokeheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
    }
}

function doTextYoutubeKaraokeBtHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textyoutubekaraokebtheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
    }
}

function doTextYoutubeChordsHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textyoutubechordsheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
    }
}

function doTextYoutubeChordsBtHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textyoutubechordsbtheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
    }
}

function doTextVkLyricsWOHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvklyricswoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextVkLyricsBtWOHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvklyricsbtwoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextVkKaraokeWOHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvkkaraokewoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextVkKaraokeBtWOHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvkkaraokebtwoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextVkChordsWOHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvkchordswoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextVkChordsBtWOHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvkchordsbtwoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}


function doTextVkLyricsHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvklyricsheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextVkLyricsBtHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvklyricsbtheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextVkKaraokeHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvkkaraokeheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextVkKaraokeBtHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvkkaraokebtheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextVkChordsHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvkchordsheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextVkChordsBtHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvkchordsbtheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}


function doTextTelegramLyricsWOHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/texttelegramlyricswoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextTelegramLyricsBtWOHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/texttelegramlyricsbtwoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextTelegramKaraokeWOHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/texttelegramkaraokewoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextTelegramKaraokeBtWOHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/texttelegramkaraokebtwoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextTelegramChordsWOHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/texttelegramchordswoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextTelegramChordsBtWOHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/texttelegramchordsbtwoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}


function doTextTelegramLyricsHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/texttelegramlyricsheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextTelegramLyricsBtHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/texttelegramlyricsbtheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextTelegramKaraokeHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/texttelegramkaraokeheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextTelegramKaraokeBtHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/texttelegramkaraokebtheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextTelegramChordsHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/texttelegramchordsheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextTelegramChordsBtHeader() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/texttelegramchordsbtheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}


function doTextBoostyHead() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textboostyhead", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doTextBoostyBody() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textboostybody", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
        // alert(result);
    }
}

function doBoostyLink() {
    var link = "https://boosty.to/svoemesto/posts/" + document.getElementById("settings_idBoosty").value
    navigator.clipboard.writeText(link);
}

function doTextVKBody() {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvkbody", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        navigator.clipboard.writeText(result);
    }
}

function doVKLink() {
    navigator.clipboard.writeText(document.getElementById("button_link_vk").href);
}

function doCreateKaraoke() {
    var id = el_id.value;
    $.ajax({
        type: "GET",
        url: "/song/" + id + "/createkaraoke",
        data: "",
        success: function (data) {
            let startIndex = data.indexOf("<div>");
            let endIndex = data.indexOf("</div>", startIndex);
            let textBetweenDivs = data.substring(startIndex + 5, endIndex);
            alert(textBetweenDivs);
        }
    });
}

function doSetDateTimeAuthor() {
    if (confirm("Вы действительно хотите установить дату/время публикации для песен автора, начиная с текущей?")) {
        var id = el_id.value;
        $.ajax({
            type: "GET",
            url: "/song/" + id + "/setpublishdatetimetoauthor",
            data: "",
            success: function (data) {
                alert("Готово!");
            }
        });
    }

}


function doEditSubs(voice) {
    var id = el_id.value;
    var link = "song/" + id + "/" + voice + "/editsubs"
    window.open(link, '_blank');
}

function doCreateKdenliveFiles() {
    var id = el_id.value;
    var dataString = {'overrideKdenliveFile': true, 'overrideKdenliveSubsFile': false}
    $.ajax({
        type: "GET",
        url: "/song/" + id + "/createkdenlivefiles",
        data: dataString,
        success: function (data) {
            let startIndex = data.indexOf("<div>");
            let endIndex = data.indexOf("</div>", startIndex);
            let textBetweenDivs = data.substring(startIndex + 5, endIndex);
            alert(textBetweenDivs);
        }
    });
}

function doDelete() {

    if (confirm("Удалить?")) {
        var id = el_id.value;
        $.ajax({
            type: "GET",
            url: "/song/" + id + "/delete",
            data: "",
            success: function (data) {

                currentRow.remove();

                alert("Удалено");
            }
        });
    }
}

function doCensored() {
    $.ajax({
        type: "POST",
        url: "/utils/censored",
        data: {"source": el_songName.value},
        success: function (data) {
            el_id_text.innerText = '«' + data + '»';
        }
    });
}

function doCreateKaraokeAll() {
    var txt = ""
    var tbl = document.getElementById("leftTable");
    var cnt = 0;
    for (var i = 0, row; row = tbl.rows[i]; i++) {
        cnt++;
        var col = row.cells;
        txt = txt + col[0].innerText + ";"
    }
    if (confirm("Выбрано песен: " + cnt + "\nВы действительно хотите создать караоке для всех этих песен?")) {

        console.log("txt: " + txt);
        $.ajax({
            type: "POST",
            url: "/songs/createkaraokeall",
            data: {"txt": txt},
            success: function (data) {
                let startIndex = data.indexOf("<div>");
                let endIndex = data.indexOf("</div>", startIndex);
                let textBetweenDivs = data.substring(startIndex + 5, endIndex);
                alert(textBetweenDivs);
            }
        });
    }
}


function doCreateDemucs2All() {
    var txt = ""
    var tbl = document.getElementById("leftTable");
    var cnt = 0;
    for (var i = 0, row; row = tbl.rows[i]; i++) {
        cnt++;
        var col = row.cells;
        txt = txt + col[0].innerText + ";"
    }
    if (confirm("Выбрано песен: " + cnt + "\nВы действительно хотите создать DEMUCS2 для всех этих песен?")) {

        console.log("txt: " + txt);
        $.ajax({
            type: "POST",
            url: "/songs/createdemucs2all",
            data: {"txt": txt},
            success: function (data) {
                let startIndex = data.indexOf("<div>");
                let endIndex = data.indexOf("</div>", startIndex);
                let textBetweenDivs = data.substring(startIndex + 5, endIndex);
                alert(textBetweenDivs);
            }
        });
    }
}

function doCreate720pKaraokeAll() {
    var txt = ""
    var tbl = document.getElementById("leftTable");
    var cnt = 0;
    for (var i = 0, row; row = tbl.rows[i]; i++) {
        cnt++;
        var col = row.cells;
        txt = txt + col[0].innerText + ";"
    }
    if (confirm("Выбрано песен: " + cnt + "\nВы действительно хотите создать 720p karaoke для всех этих песен?")) {

        console.log("txt: " + txt);
        $.ajax({
            type: "POST",
            url: "/songs/create720pkaraokeall",
            data: {"txt": txt},
            success: function (data) {
                let startIndex = data.indexOf("<div>");
                let endIndex = data.indexOf("</div>", startIndex);
                let textBetweenDivs = data.substring(startIndex + 5, endIndex);
                alert(textBetweenDivs);
            }
        });
    }
}


function doCreate720pLyricsAll() {
    var txt = ""
    var tbl = document.getElementById("leftTable");
    var cnt = 0;
    for (var i = 0, row; row = tbl.rows[i]; i++) {
        cnt++;
        var col = row.cells;
        txt = txt + col[0].innerText + ";"
    }
    if (confirm("Выбрано песен: " + cnt + "\nВы действительно хотите создать 720p lyrics для всех этих песен?")) {

        console.log("txt: " + txt);
        $.ajax({
            type: "POST",
            url: "/songs/create720plyricsall",
            data: {"txt": txt},
            success: function (data) {
                let startIndex = data.indexOf("<div>");
                let endIndex = data.indexOf("</div>", startIndex);
                let textBetweenDivs = data.substring(startIndex + 5, endIndex);
                alert(textBetweenDivs);
            }
        });
    }
}

function doCreateTagsAll() {
    $.ajax({
        type: "GET",
        url: "/songs/createtags",
        data: "",
        success: function (data) {
            alert("OK");
        }
    });
}

function doSearchTextAll() {
    var txt = ""
    var tbl = document.getElementById("leftTable");
    var cnt = 0;
    for (var i = 0, row; row = tbl.rows[i]; i++) {
        cnt++;
        var col = row.cells;
        txt = txt + col[0].innerText + ";"
    }
    if (confirm("Выбрано песен: " + cnt + "\nВы действительно хотите найти в интернете тексты для всех песен, у которых еще нет текстов?")) {

        console.log("txt: " + txt);
        $.ajax({
            type: "POST",
            url: "/songs/searchsongtextall",
            data: {"txt": txt},
            success: function (data) {
                let startIndex = data.indexOf("<div>");
                let endIndex = data.indexOf("</div>", startIndex);
                let textBetweenDivs = data.substring(startIndex + 5, endIndex);
                alert(textBetweenDivs);
            }
        });
    }
}

function extractYoutubeVideoId(inputElement) {
    // Получаем значение из поля ввода
    var inputValue = inputElement.value.trim();

    if (inputValue.startsWith(prefix_link_dzen_play)) {
        const id = inputValue.replace(prefix_link_dzen_play, '');
        inputElement.value = id;

        el_button_link_play_YoutubeLyrics.href = prefix_link_dzen_play + el_idYoutubeLyrics.value;
        el_button_link_edit_YoutubeLyrics.href = prefix_link_dzen_edit + el_idYoutubeLyrics.value;
        el_button_link_play_YoutubeKaraoke.href = prefix_link_dzen_play + el_idYoutubeKaraoke.value;
        el_button_link_edit_YoutubeKaraoke.href = prefix_link_dzen_edit + el_idYoutubeKaraoke.value;
        el_button_link_play_YoutubeChords.href = prefix_link_dzen_play + el_idYoutubeChords.value;
        el_button_link_edit_YoutubeChords.href = prefix_link_dzen_edit + el_idYoutubeChords.value;

        el_button_link_play_YoutubeLyrics.disabled = (el_idYoutubeLyrics.value === '');
        el_button_link_edit_YoutubeLyrics.disabled = (el_idYoutubeLyrics.value === '');
        el_button_link_play_YoutubeKaraoke.disabled = (el_idYoutubeKaraoke.value === '');
        el_button_link_edit_YoutubeKaraoke.disabled = (el_idYoutubeKaraoke.value === '');
        el_button_link_play_YoutubeChords.disabled = (el_idYoutubeChords.value === '');
        el_button_link_edit_YoutubeChords.disabled = (el_idYoutubeChords.value === '');

    }
}

function extractVkVideoId(inputElement) {
    // Получаем значение из поля ввода
    var inputValue = inputElement.value.trim();

    if (inputValue.startsWith(prefix_link_vk_play)) {
        const id = inputValue.replace(prefix_link_vk_play, '');
        inputElement.value = id;

        el_button_link_play_VkLyrics.href = prefix_link_vk_play + el_idVkLyrics.value;
        el_button_link_edit_VkLyrics.href = prefix_link_vk_edit + el_idVkLyrics.value;
        el_button_link_play_VkKaraoke.href = prefix_link_vk_play + el_idVkKaraoke.value;
        el_button_link_edit_VkKaraoke.href = prefix_link_vk_edit + el_idVkKaraoke.value;
        el_button_link_play_VkChords.href = prefix_link_vk_play + el_idVkChords.value;
        el_button_link_edit_VkChords.href = prefix_link_vk_edit + el_idVkChords.value;

        el_button_link_play_VkLyrics.disabled = (el_idVkLyrics.value === '');
        el_button_link_edit_VkLyrics.disabled = (el_idVkLyrics.value === '');
        el_button_link_play_VkKaraoke.disabled = (el_idVkKaraoke.value === '');
        el_button_link_edit_VkKaraoke.disabled = (el_idVkKaraoke.value === '');
        el_button_link_play_VkChords.disabled = (el_idVkChords.value === '');
        el_button_link_edit_VkChords.disabled = (el_idVkChords.value === '');

    }
}


function extractTelegramVideoId(inputElement) {
    // Получаем значение из поля ввода
    var inputValue = inputElement.value.trim();

    if (inputValue.startsWith(prefix_link_telegram_play)) {
        const id = inputValue.replace(prefix_link_telegram_play, '');
        inputElement.value = id;

        el_button_link_play_TelegramLyrics.href = prefix_link_telegram_play + el_idTelegramLyrics.value;
        el_button_link_edit_TelegramLyrics.href = prefix_link_telegram_edit + el_idTelegramLyrics.value;
        el_button_link_play_TelegramKaraoke.href = prefix_link_telegram_play + el_idTelegramKaraoke.value;
        el_button_link_edit_TelegramKaraoke.href = prefix_link_telegram_edit + el_idTelegramKaraoke.value;
        el_button_link_play_TelegramChords.href = prefix_link_telegram_play + el_idTelegramChords.value;
        el_button_link_edit_TelegramChords.href = prefix_link_telegram_edit + el_idTelegramChords.value;

        el_button_link_play_TelegramLyrics.disabled = (el_idTelegramLyrics.value === '');
        el_button_link_edit_TelegramLyrics.disabled = (el_idTelegramLyrics.value === '');
        el_button_link_play_TelegramKaraoke.disabled = (el_idTelegramKaraoke.value === '');
        el_button_link_edit_TelegramKaraoke.disabled = (el_idTelegramKaraoke.value === '');
        el_button_link_play_TelegramChords.disabled = (el_idTelegramChords.value === '');
        el_button_link_edit_TelegramChords.disabled = (el_idTelegramChords.value === '');

    }
}

function extractBoostyId(inputElement) {
    // Получаем значение из поля ввода
    var inputValue = inputElement.value.trim();

    if (inputValue.startsWith(prefix_link_boosty) && inputValue.endsWith('?share=post_link')) {
        const id = inputValue.replace(prefix_link_boosty, '').replace('?share=post_link', '');
        inputElement.value = id;
        el_button_link_boosty.href = prefix_link_boosty + el_idBoosty.value;
        el_button_link_boosty.disabled = (el_idBoosty.value === '');
    }
}

function extractVkId(inputElement) {
    // Получаем значение из поля ввода
    var inputValue = inputElement.value.trim();

    if (inputValue.startsWith(prefix_link_vk_wall)) {
        const id = inputValue.replace(prefix_link_vk_wall, '');
        inputElement.value = id;
        el_button_link_vk.href = prefix_link_vk_wall + el_idVk.value;
        el_button_link_vk.disabled = (el_idVk.value === '');
    }
}

function openLinkYoutubeEditKaraoke() {

    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textyoutubekaraokewoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        copyToClipboard(result);
        var link = prefix_link_dzen_edit + el_idYoutubeKaraoke.value;
        window.open(link, '_blank');
    }

}

function openLinkYoutubeEditKaraokeBt() {

    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textyoutubekaraokebtwoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        copyToClipboard(result);
        var link = prefix_link_dzen_edit + el_idYoutubeKaraokeBt.value;
        window.open(link, '_blank');
    }

}

function openLinkYoutubeEditLyrics() {

    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textyoutubelyricswoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        copyToClipboard(result);
        var link = prefix_link_dzen_edit + el_idYoutubeLyrics.value;
        window.open(link, '_blank');
    }

}

function openLinkYoutubeEditLyricsBt() {

    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textyoutubelyricsbtwoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        copyToClipboard(result);
        var link = prefix_link_dzen_edit + el_idYoutubeLyricsBt.value;
        window.open(link, '_blank');
    }

}

function openLinkYoutubeEditChords() {

    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textyoutubechordswoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        copyToClipboard(result);
        var link = prefix_link_dzen_edit + el_idYoutubeChords.value;
        window.open(link, '_blank');
    }

}

function openLinkYoutubeEditChordsBt() {

    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textyoutubechordsbtwoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        copyToClipboard(result);
        var link = prefix_link_dzen_edit + el_idYoutubeChordsBt.value;
        window.open(link, '_blank');
    }

}


function openLinkYoutubePlayKaraoke() {
    var link = prefix_link_dzen_play + el_idYoutubeKaraoke.value;
    window.open(link, '_blank');
}

function doYoutubeKaraokeLink() {
    var link = prefix_link_dzen_play + el_idYoutubeKaraoke.value
    navigator.clipboard.writeText(link);
}

function openLinkYoutubePlayKaraokeBt() {
    var link = prefix_link_dzen_play + el_idYoutubeKaraokeBt.value
    window.open(link, '_blank');
    var id = el_id.value;
}

function doYoutubeKaraokeBtLink() {
    var link = prefix_link_dzen_play + el_idYoutubeKaraokeBt.value
    navigator.clipboard.writeText(link);
}

function openLinkYoutubePlayLyrics() {
    var link = prefix_link_dzen_play + el_idYoutubeLyrics.value
    window.open(link, '_blank');

}

function doYoutubeLyricsLink() {
    var link = prefix_link_dzen_play + el_idYoutubeLyrics.value
    navigator.clipboard.writeText(link);
}

function openLinkYoutubePlayLyricsBt() {
    var link = prefix_link_dzen_play + el_idYoutubeLyricsBt.value
    window.open(link, '_blank');
}

function doYoutubeLyricsBtLink() {
    var link = prefix_link_dzen_play + el_idYoutubeLyricsBt.value
    navigator.clipboard.writeText(link);
}

function openLinkYoutubePlayChords() {
    var link = prefix_link_dzen_play + el_idYoutubeChords.value
    window.open(link, '_blank');
}

function doYoutubeChordsLink() {
    var link = prefix_link_dzen_play + el_idYoutubeChords.value
    navigator.clipboard.writeText(link);
}

function openLinkYoutubePlayChordsBt() {
    var link = prefix_link_dzen_play + el_idYoutubeChordsBt.value
    window.open(link, '_blank');
}

function doYoutubeChordsBtLink() {
    var link = prefix_link_dzen_play + el_idYoutubeChordsBt.value
    navigator.clipboard.writeText(link);
}


function openLinkVkEditKaraoke() {

    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvkkaraokewoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        copyToClipboard(result)
        var link = prefix_link_vk_edit + el_idVkKaraoke.value;
        window.open(link, '_blank');
    }

}

function doVkKaraokeLink() {
    var link = prefix_link_vk_play + el_idVkKaraoke.value;
    navigator.clipboard.writeText(link);
}

function openLinkVkEditKaraokeBt() {

    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvkkaraokebtwoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        copyToClipboard(result)
        var link = prefix_link_vk_edit + el_idVkKaraokeBt.value;
        window.open(link, '_blank');
    }

}

function doVkKaraokeBtLink() {
    var link = prefix_link_vk_play + el_idVkKaraokeBt.value;
    navigator.clipboard.writeText(link);
}

function openLinkVkEditLyrics() {

    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvklyricswoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        copyToClipboard(result)
        var link = prefix_link_vk_edit + el_idVkLyrics.value;
        window.open(link, '_blank');
    }

}

function doVkLyricsLink() {
    var link = prefix_link_vk_play + el_idVkLyrics.value;
    navigator.clipboard.writeText(link);
}

function openLinkVkEditLyricsBt() {

    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvklyricsbtwoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        copyToClipboard(result)
        var link = prefix_link_vk_edit + el_idVkLyricsBt.value;
        window.open(link, '_blank');
    }

}

function doVkLyricsBtLink() {
    var link = prefix_link_vk_play + el_idVkLyricsBt.value;
    navigator.clipboard.writeText(link);
}

function openLinkVkEditChords() {

    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvkchordswoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        copyToClipboard(result)
        var link = prefix_link_vk_edit + el_idVkChords.value;
        window.open(link, '_blank');
    }

}

function doVkChordsLink() {
    var link = prefix_link_vk_play + el_idVkChords.value;
    navigator.clipboard.writeText(link);
}

function openLinkVkEditChordsBt() {

    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvkchordsbtwoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        copyToClipboard(result)
        var link = prefix_link_vk_edit + el_idVkChordsBt.value;
        window.open(link, '_blank');
    }

}

function doVkChordsBtLink() {
    var link = prefix_link_vk_play + el_idVkChordsBt.value;
    navigator.clipboard.writeText(link);
}

function openLinkVkPlayKaraoke() {

    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvkkaraokewoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        copyToClipboard(result)
        var link = prefix_link_vk_play + el_idVkKaraoke.value;
        window.open(link, '_blank');
    }

}

function openLinkVkPlayKaraokeBt() {
    var link = prefix_link_vk_play + el_idVkKaraokeBt.value
    window.open(link, '_blank');
    var id = el_id.value;
}

function openLinkVkPlayLyrics() {

    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/textvklyricswoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        copyToClipboard(result)
        var link = prefix_link_vk_play + el_idVkLyrics.value;
        window.open(link, '_blank');
    }

}

function openLinkVkPlayLyricsBt() {
    var link = "https://vk.com/video" + el_idVkLyricsBt.value
    window.open(link, '_blank');
}

function openLinkVkPlayChords() {
    var link = prefix_link_vk_play + el_idVkChords.value
    window.open(link, '_blank');
}

function openLinkVkPlayChordsBt() {
    var link = prefix_link_vk_play + el_idVkChordsBt.value
    window.open(link, '_blank');
}


function openLinkTelegramEditKaraoke() {

    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/texttelegramkaraokewoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        copyToClipboard(result)
        var link = prefix_link_telegram_edit + el_idTelegramKaraoke.value;
        window.open(link, '_blank');
    }

}

function doTelegramKaraokeLink() {
    var link = prefix_link_telegram_play + el_idTelegramKaraoke.value;
    navigator.clipboard.writeText(link);
}

function openLinkTelegramEditKaraokeBt() {

    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/texttelegramkaraokebtwoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        copyToClipboard(result)
        var link = prefix_link_telegram_edit + el_idTelegramKaraokeBt.value;
        window.open(link, '_blank');
    }

}

function doTelegramKaraokeBtLink() {
    var link = prefix_link_telegram_play + el_idTelegramKaraokeBt.value;
    navigator.clipboard.writeText(link);
}

function openLinkTelegramEditLyrics() {

    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/texttelegramlyricswoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        copyToClipboard(result)
        var link = prefix_link_telegram_edit + el_idTelegramLyrics.value;
        window.open(link, '_blank');
    }

}

function doTelegramLyricsLink() {
    var link = prefix_link_telegram_play + el_idTelegramLyrics.value;
    navigator.clipboard.writeText(link);
}

function openLinkTelegramEditLyricsBt() {

    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/texttelegramlyricsbtwoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        copyToClipboard(result)
        var link = prefix_link_telegram_edit + el_idTelegramLyricsBt.value;
        window.open(link, '_blank');
    }

}

function doTelegramLyricsBtLink() {
    var link = prefix_link_telegram_play + el_idTelegramLyricsBt.value;
    navigator.clipboard.writeText(link);
}

function openLinkTelegramEditChords() {

    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/texttelegramchordswoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        copyToClipboard(result)
        var link = prefix_link_telegram_edit + el_idTelegramChords.value;
        window.open(link, '_blank');
    }

}

function doTelegramChordsLink() {
    var link = prefix_link_telegram_play + el_idTelegramChords.value;
    navigator.clipboard.writeText(link);
}

function openLinkTelegramEditChordsBt() {

    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/texttelegramchordsbtwoheader", false);
    request.send();
    if (request.status === 200) {
        var result = request.responseText;
        copyToClipboard(result)
        var link = prefix_link_telegram_edit + el_idTelegramChordsBt.value;
        window.open(link, '_blank');
    }

}

function doTelegramChordsBtLink() {
    var link = prefix_link_telegram_play + el_idTelegramChordsBt.value;
    navigator.clipboard.writeText(link);
}

function openLinkTelegramPlayKaraoke() {
    var link = prefix_link_telegram_play + el_idTelegramKaraoke.value
    window.open(link, '_blank');
}

function openLinkTelegramPlayKaraokeBt() {
    var link = prefix_link_telegram_play + el_idTelegramKaraokeBt.value
    window.open(link, '_blank');
    var id = el_id.value;
}

function openLinkTelegramPlayLyrics() {
    var link = prefix_link_telegram_play + el_idTelegramLyrics.value
    window.open(link, '_blank');

}

function openLinkTelegramPlayLyricsBt() {
    var link = prefix_link_telegram_play + el_idTelegramLyricsBt.value
    window.open(link, '_blank');
}

function openLinkTelegramPlayChords() {
    var link = prefix_link_telegram_play + el_idTelegramChords.value
    window.open(link, '_blank');
}

function openLinkTelegramPlayChordsBt() {
    var link = prefix_link_telegram_play + el_idTelegramChordsBt.value
    window.open(link, '_blank');
}


function copyToClipboard(text) {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.setAttribute('readonly', '');
    textarea.style.position = 'absolute';
    textarea.style.left = '-9999px';
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
}

function doProcess(link) {
    var id = el_id.value;
    var request = new XMLHttpRequest();
    request.open('GET', "/song/" + id + "/" + link, true);
    request.send();
    if (link === "doprocesslyrics") {
        document.getElementById(`fld_${id}_flag_lyrics`).style.backgroundColor = "#FFFF00";
    } else if (link === "doprocesskaraoke") {
        document.getElementById(`fld_${id}_flag_karaoke`).style.backgroundColor = "#FFFF00";
    } else if (link === "doprocesschords") {
        document.getElementById(`fld_${id}_flag_chords`).style.backgroundColor = "#FFFF00";
    } else if (link === "doprocesslyricsbt") {
        document.getElementById(`fld_${id}_flag_lyricsbt`).style.backgroundColor = "#FFFF00";
    } else if (link === "doprocesskaraokebt") {
        document.getElementById(`fld_${id}_flag_karaokebt`).style.backgroundColor = "#FFFF00";
    } else if (link === "doprocesschordsbt") {
        document.getElementById(`fld_${id}_flag_chordsbt`).style.backgroundColor = "#FFFF00";
    } else if (link === "doprocessall") {
        document.getElementById(`fld_${id}_flag_lyrics`).style.backgroundColor = "#FFFF00";
        document.getElementById(`fld_${id}_flag_karaoke`).style.backgroundColor = "#FFFF00";
        document.getElementById(`fld_${id}_flag_lyricsbt`).style.backgroundColor = "#FFFF00";
        document.getElementById(`fld_${id}_flag_karaokebt`).style.backgroundColor = "#FFFF00";
    } else if (link === "doprocessallwolyrics") {
        document.getElementById(`fld_${id}_flag_karaoke`).style.backgroundColor = "#FFFF00";
        document.getElementById(`fld_${id}_flag_lyricsbt`).style.backgroundColor = "#FFFF00";
        document.getElementById(`fld_${id}_flag_karaokebt`).style.backgroundColor = "#FFFF00";
    }
}


$(function () {

    var tbl = document.getElementById("leftTable");
    for (var i = 0, rows; rows = tbl.rows[i]; i++) {
        rows.className = "sub-row" + i % 2;
    }

    const buttonTogglesStatus = document.getElementsByName('btn_status');
    for (let i = 0; i < buttonTogglesStatus.length; i++) {
        buttonTogglesStatus[i].addEventListener('click', function () {
            const selectedValue = this.value;
            for (let j = 0; j < buttonTogglesStatus.length; j++) {
                buttonTogglesStatus[j].classList.remove('active');
            }
            this.classList.add('active');
            var selectElement = document.getElementById("select_status");
            selectElement.selectedIndex = selectedValue;
            doPostForm();
        });
    }

    const fieldsFilter = document.getElementsByName('filter_field');
    for (let i = 0; i < fieldsFilter.length; i++) {
        fieldsFilter[i].addEventListener('keydown', function (event) {
            if (event.keyCode === 13) {
                doFilterForm();
            }
        });
    }

    setDisabledElements(true);

});

function doPlay(id, name) {
    $.ajax({
        type: "GET",
        url: `/song/${id}/${name}`,
        data: "",
        success: function (data) {
        }
    });
}

function updateSettingsFromMessage(recordchange) {
    var id = recordchange.recordChangeId;
    var table = recordchange.recordChangeTableName;
    var diffs = recordchange.recordChangeDiffs;
    if (table === 'tbl_settings') {

        var idRow = `row${id}`;
        var elementRow = document.getElementById(idRow);
        if (elementRow !== null) {

            diffs.forEach((diff) => {
                var recordDiffName = diff.recordDiffName;
                var recordDiffValueNew = diff.recordDiffValueNew;
                switch (recordDiffName) {

                    case 'song_name':
                        document.getElementById(`fld_${id}_songName`).innerText = recordDiffValueNew;
                        break;

                    case 'song_author':
                        document.getElementById(`fld_${id}_author`).innerText = recordDiffValueNew;
                        break;

                    case 'song_album':
                        document.getElementById(`fld_${id}_album`).innerText = recordDiffValueNew;
                        break;

                    case 'publish_date':
                        document.getElementById(`fld_${id}_date`).innerText = recordDiffValueNew;
                        break;

                    case 'publish_time':
                        document.getElementById(`fld_${id}_time`).innerText = recordDiffValueNew;
                        break;

                    case 'song_year':
                        document.getElementById(`fld_${id}_year`).innerText = recordDiffValueNew;
                        break;

                    case 'song_track':
                        document.getElementById(`fld_${id}_track`).innerText = recordDiffValueNew;
                        break;

                    case 'song_tone':
                        document.getElementById(`fld_${id}_key`).innerText = recordDiffValueNew;
                        break;

                    case 'song_bpm':
                        document.getElementById(`fld_${id}_bpm`).innerText = recordDiffValueNew;
                        break;

                    case 'song_ms':
                        document.getElementById(`fld_${id}_ms`).innerText = recordDiffValueNew;
                        break;

                    case 'file_name':
                        document.getElementById(`fld_${id}_fileName`).innerText = recordDiffValueNew;
                        break;

                    case 'root_folder':
                        document.getElementById(`fld_${id}_rootFolder`).innerText = recordDiffValueNew;
                        break;

                    case 'id_boosty':
                        document.getElementById(`fld_${id}_idBoosty`).innerText = recordDiffValueNew;
                        if (recordDiffValueNew !== "") {
                            document.getElementById(`fld_${id}_flagBoosty`).innerText = '✓';
                        } else {
                            document.getElementById(`fld_${id}_flagBoosty`).innerText = '';
                        }
                        break;

                    case 'id_vk':
                        document.getElementById(`fld_${id}_idVk`).innerText = recordDiffValueNew;
                        if (recordDiffValueNew !== "") {
                            document.getElementById(`fld_${id}_flagVk`).innerText = '✓';
                        } else {
                            document.getElementById(`fld_${id}_flagVk`).innerText = '';
                        }
                        break;

                    case 'id_youtube_lyrics':
                        document.getElementById(`fld_${id}_idYoutubeLyrics`).innerText = recordDiffValueNew;
                        if (recordDiffValueNew !== "") {
                            document.getElementById(`fld_${id}_flagYoutubeLyrics`).innerText = '✓';
                        } else {
                            document.getElementById(`fld_${id}_flagYoutubeLyrics`).innerText = '';
                        }
                        break;

                    case 'id_youtube_karaoke':
                        document.getElementById(`fld_${id}_idYoutubeKaraoke`).innerText = recordDiffValueNew;
                        if (recordDiffValueNew !== "") {
                            document.getElementById(`fld_${id}_flagYoutubeKaraoke`).innerText = '✓';
                        } else {
                            document.getElementById(`fld_${id}_flagYoutubeKaraoke`).innerText = '';
                        }
                        break;

                    case 'id_youtube_chords':
                        document.getElementById(`fld_${id}_idYoutubeChords`).innerText = recordDiffValueNew;
                        if (recordDiffValueNew !== "") {
                            document.getElementById(`fld_${id}_flagYoutubeChords`).innerText = '✓';
                        } else {
                            document.getElementById(`fld_${id}_flagYoutubeChords`).innerText = '';
                        }
                        break;

                    case 'id_vk_lyrics':
                        document.getElementById(`fld_${id}_idVkLyrics`).innerText = recordDiffValueNew;
                        if (recordDiffValueNew !== "") {
                            document.getElementById(`fld_${id}_flagVkLyrics`).innerText = '✓';
                        } else {
                            document.getElementById(`fld_${id}_flagVkLyrics`).innerText = '';
                        }
                        break;

                    case 'id_vk_karaoke':
                        document.getElementById(`fld_${id}_idVkKaraoke`).innerText = recordDiffValueNew;
                        if (recordDiffValueNew !== "") {
                            document.getElementById(`fld_${id}_flagVkKaraoke`).innerText = '✓';
                        } else {
                            document.getElementById(`fld_${id}_flagVkKaraoke`).innerText = '';
                        }
                        break;

                    case 'id_vk_chords':
                        document.getElementById(`fld_${id}_idVkChords`).innerText = recordDiffValueNew;
                        if (recordDiffValueNew !== "") {
                            document.getElementById(`fld_${id}_flagVkChords`).innerText = '✓';
                        } else {
                            document.getElementById(`fld_${id}_flagVkChords`).innerText = '';
                        }
                        break;

                    case 'id_telegram_lyrics':
                        document.getElementById(`fld_${id}_idTelegramLyrics`).innerText = recordDiffValueNew;
                        if (recordDiffValueNew !== "") {
                            document.getElementById(`fld_${id}_flagTelegramLyrics`).innerText = '✓';
                        } else {
                            document.getElementById(`fld_${id}_flagTelegramLyrics`).innerText = '';
                        }
                        break;

                    case 'id_telegram_karaoke':
                        document.getElementById(`fld_${id}_idTelegramKaraoke`).innerText = recordDiffValueNew;
                        if (recordDiffValueNew !== "") {
                            document.getElementById(`fld_${id}_flagTelegramKaraoke`).innerText = '✓';
                        } else {
                            document.getElementById(`fld_${id}_flagTelegramKaraoke`).innerText = '';
                        }
                        break;

                    case 'id_telegram_chords':
                        document.getElementById(`fld_${id}_idTelegramChords`).innerText = recordDiffValueNew;
                        if (recordDiffValueNew !== "") {
                            document.getElementById(`fld_${id}_flagTelegramChords`).innerText = '✓';
                        } else {
                            document.getElementById(`fld_${id}_flagTelegramChords`).innerText = '';
                        }
                        break;

                    case 'id_status':
                        document.getElementById(`fld_${id}_idStatus`).innerText = recordDiffValueNew;
                        break;

                    case 'status':
                        document.getElementById(`fld_${id}_status`).innerText = recordDiffValueNew;
                        break;

                    case 'tags':
                        document.getElementById(`fld_${id}_tags`).innerText = recordDiffValueNew;
                        break;

                    case 'processColorMeltLyrics':
                        document.getElementById(`fld_${id}_flagYoutubeLyrics`).style.backgroundColor = recordDiffValueNew;
                        break;

                    case 'processColorMeltKaraoke':
                        document.getElementById(`fld_${id}_flagYoutubeKaraoke`).style.backgroundColor = recordDiffValueNew;
                        break;

                    case 'processColorVkLyrics':
                        document.getElementById(`fld_${id}_flagVkLyrics`).style.backgroundColor = recordDiffValueNew;
                        break;

                    case 'processColorVkKaraoke':
                        document.getElementById(`fld_${id}_flagVkKaraoke`).style.backgroundColor = recordDiffValueNew;
                        break;

                    case 'processColorTelegramLyrics':
                        document.getElementById(`fld_${id}_flagTelegramLyrics`).style.backgroundColor = recordDiffValueNew;
                        break;

                    case 'processColorTelegramKaraoke':
                        document.getElementById(`fld_${id}_flagTelegramKaraoke`).style.backgroundColor = recordDiffValueNew;
                        break;

                    case 'processColorVk':
                        document.getElementById(`fld_${id}_flagVk`).style.backgroundColor = recordDiffValueNew;
                        break;

                    case 'processColorBoosty':
                        document.getElementById(`fld_${id}_flagBoosty`).style.backgroundColor = recordDiffValueNew;
                        break;

                    case 'color':
                        document.getElementById(`fld_${id}_id`).style.backgroundColor = recordDiffValueNew;
                        document.getElementById(`fld_${id}_songName`).style.backgroundColor = recordDiffValueNew;
                        document.getElementById(`fld_${id}_author`).style.backgroundColor = recordDiffValueNew;
                        document.getElementById(`fld_${id}_year`).style.backgroundColor = recordDiffValueNew;
                        document.getElementById(`fld_${id}_album`).style.backgroundColor = recordDiffValueNew;
                        document.getElementById(`fld_${id}_track`).style.backgroundColor = recordDiffValueNew;
                        document.getElementById(`fld_${id}_date`).style.backgroundColor = recordDiffValueNew;
                        document.getElementById(`fld_${id}_time`).style.backgroundColor = recordDiffValueNew;
                        document.getElementById(`fld_${id}_tags`).style.backgroundColor = recordDiffValueNew;
                        document.getElementById(`fld_${id}_status`).style.backgroundColor = recordDiffValueNew;
                        break;

                    case 'MELT_LYRICS':
                        document.getElementById(`fld_${id}_flagYoutubeLyrics`).style.width = recordDiffValueNew;
                        break;

                    case 'MELT_KARAOKE':
                        document.getElementById(`fld_${id}_flagYoutubeKaraoke`).style.width = recordDiffValueNew;
                        break;

                    case 'FF_720_LYR':
                        console.log("FF_720_LYR");
                        document.getElementById(`fld_${id}_flagYoutubeLyrics`).style.width = recordDiffValueNew;
                        if (recordDiffValueNew !== "100%") {
                            document.getElementById(`fld_${id}_flagYoutubeLyrics`).style.backgroundColor = "#0000FF";
                        } else {
                            document.getElementById(`fld_${id}_flagYoutubeLyrics`).style.backgroundColor = "#FF00FF";
                        }
                        break;

                    case 'FF_720_KAR':
                        console.log("FF_720_KAR");
                        document.getElementById(`fld_${id}_flagYoutubeKaraoke`).style.width = recordDiffValueNew;
                        if (recordDiffValueNew !== "100%") {
                            document.getElementById(`fld_${id}_flagYoutubeKaraoke`).style.backgroundColor = "#0000FF";
                        } else {
                            document.getElementById(`fld_${id}_flagYoutubeKaraoke`).style.backgroundColor = "#FF00FF";
                        }
                        break;

                    default:
                }
            });

        }

    }
}


$(function () {
    connect();
});

function getSongPictureAlbum() {
    if (prev_album !== el_album.value) {
        var id = el_id.value;
        var request = new XMLHttpRequest();
        request.open('GET', "/song/" + id + "/picturealbum", false);
        request.send();
        if (request.status === 200) {
            el_image_album.src = 'data:image/gif;base64,' + request.responseText;
        }
    }
}


function getSongPictureAuthor() {
    if (prev_author !== el_author.value) {
        var id = el_id.value;
        var request = new XMLHttpRequest();
        request.open('GET', "/song/" + id + "/pictureauthor", false);
        request.send();
        if (request.status === 200) {
            el_image_author.src = 'data:image/gif;base64,' + request.responseText;
        }
    }
}
