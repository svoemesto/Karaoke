// Thrown when required audio isn't available yet (missing file in storage, no mp3 rendered, etc.) —
// distinguished from other init errors so the UI can show a friendly message instead of raw details.
class PlayerUnavailableError extends Error {}

export default class KaraokePlayer {
  // Usage modes:
  //   new KaraokePlayer(container, songId, apiBase)                      — online, loads via /api
  //   new KaraokePlayer(container, { songId, assignmentId }, apiBase)    — online, but with the
  //     given voice's markers overridden by that assignment's UNAPPROVED draft (song-editor review
  //     preview — see /api/song/{id}/playerdata's assignmentId param on the backend)
  //   new KaraokePlayer(container, { smkaraoke: File|Blob }) — from local .smkaraoke file
  //   new KaraokePlayer(container, { smkaraokeUrl: string }) — download .smkaraoke from URL
  constructor(container, songIdOrOptions, apiBase) {
    this.container = container
    if (songIdOrOptions !== null && typeof songIdOrOptions === 'object' && ('smkaraoke' in songIdOrOptions || 'smkaraokeUrl' in songIdOrOptions)) {
      this._mode = songIdOrOptions.smkaraoke ? 'blob' : 'url-smkaraoke'
      this._smkaraokeSource = songIdOrOptions.smkaraoke ?? songIdOrOptions.smkaraokeUrl
    } else if (songIdOrOptions !== null && typeof songIdOrOptions === 'object') {
      this._mode = 'api'
      this.songId = songIdOrOptions.songId
      this.assignmentId = songIdOrOptions.assignmentId ?? null
      this.target = songIdOrOptions.target ?? null
      this.apiBase = apiBase
    } else {
      this._mode = 'api'
      this.songId = songIdOrOptions
      this.assignmentId = null
      this.target = null
      this.apiBase = apiBase
    }
    this._smkaraokeObjectUrls = []

    this.audioCtx = null
    this.accBuffer = null
    this.vocBuffer = null
    this.accSource = null
    this.vocSource = null
    this.accGain = null
    this.vocGain = null
    this.startedAt = 0
    this.pausedAt = 0
    this.isPlaying = false
    this.duration = 0
    this._volumeAnchored = false   // "якорь": при true все треки идут к одной громкости
    // Уровни громкости дорожек (%). Персистентны на весь инстанс, чтобы при смене песни в плейлисте
    // (playSong) уровни ползунков «Музыка»/«Голос» и якорь сцепки наследовались следующим треком.
    this._accVol = 100
    this._vocVol = 0

    // Pre-roll: splash (5s) + silent offset before first syllable
    this._splashDur = 5.0
    this._silentOffset = 0
    this._preroll = 5.0          // splashDur + silentOffset, recalculated after parse
    this._isPrerolling = false   // true while wall-clock preroll is active
    this._dtPaused = 0           // display time when paused during preroll
    this._prerollRef = 0         // Date.now() when preroll clock was last started
    this._prerollTimeout = null  // setTimeout handle for preroll→audio transition

    this.data = null
    this.lines = []
    this.voiceLines = []
    this._bgCanvas = null
    this._ready = false
    this._loadProgress = null   // null = неопределённо (спиннер), 0..1 = доля скачанного (проценты)

    this._logoImg = null
    this._startFadeStartedAt = null // Date.now() когда песня стала готова: переход logo→splash (fade-out 0.5с + fade-in 0.5с)
    this._endFadeStartedAt = null   // Date.now() когда началась idle-анимация после _onEnded(): logo→чёрный→splash

    this.canvas = null
    this.ctx = null
    this.animId = null

    this.wsAcc = null
    this.wsVoc = null
    this._lastWsSync = 0
    this._endedHandled = false

    // Необязательный внешний колбэк, вызывается один раз при естественном окончании трека
    // (из _onEnded). Используется страницей плейлиста для авто-перехода к следующей песне.
    this.onTrackEnded = null

    // Display mode: 'embed' (small box on the host page, e.g. the song page's player card) vs
    // 'page' (this player's own container fills the whole viewport it lives in — that's already
    // true by default for a top-level /player/:id route AND for a same-origin iframe once its own
    // box is resized to 100vw/100vh by the host page). Detected once at construction, not
    // reconfigurable: a genuinely top-level page (webvue3 route, or the public site's "secret
    // gesture" new-tab flow) has no host page to ask for a resize, so there IS no 'embed' mode for
    // it — the wide-mode button stays permanently disabled in that case.
    this._isEmbedded = window.self !== window.top
    this._displayMode = this._isEmbedded ? 'embed' : 'page'
    this._isFullscreen = false
    this._preFullscreenDisplayMode = null   // restored on exiting fullscreen

    this._resizeHandler = () => this._resizeCanvas()
    this._fsHandler = () => this._onFullscreenChange()
    this._menuOutsideClickHandler = () => this._closeMenu()
  }

  async init() {
    // Phase 1: show animated background immediately, before any network requests
    this._buildUI()
    this._bgCanvas = this._generateStarfield()
    this._startRenderLoop()
    this._loadImage('/KARAOKE_LOGO.png').then(img => { this._logoImg = img }).catch(() => {})

    try {
      if (this._mode === 'api') {
        const qs = this.assignmentId
          ? `?assignmentId=${encodeURIComponent(this.assignmentId)}${this.target ? `&target=${encodeURIComponent(this.target)}` : ''}`
          : ''
        const resp = await fetch(`${this.apiBase}/song/${this.songId}/playerdata${qs}`)
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
        this.data = await resp.json()
      } else {
        this.data = await this._loadSmkaraoke(this._smkaraokeSource)
      }
      if (!this.data?.audioAccompanimentUrl || !this.data?.audioVocalsUrl) {
        throw new PlayerUnavailableError('Missing required audio tracks')
      }
      await Promise.all([
        new FontFace('Roboto', 'url(/fonts/Roboto.ttf)', { weight: '900', style: 'normal' })
          .load().then(f => document.fonts.add(f)),
        new FontFace('Roboto', 'url(/fonts/Roboto-Italic.ttf)', { weight: '900', style: 'italic' })
          .load().then(f => document.fonts.add(f)),
        // Matches the Kotlin MLT renderer's splashstartChordDescriptionFont exactly (KaraokeProperties.kt)
        // — the key/bpm line uses Fira Sans, not Roboto. Without this loaded explicitly, requesting
        // weight 400 for a family that only has a 900-weight FontFace registered falls back
        // unpredictably (e.g. to whatever "Roboto" the page's own CSS happens to have loaded, if any).
        new FontFace('FiraSansExtraCondensed', 'url(/fonts/FiraSansExtraCondensed-Medium.ttf)', { weight: '400', style: 'normal' })
          .load().then(f => document.fonts.add(f))
      ])
      this._albumImg = this.data.albumImageUrl ? await this._loadImage(this.data.albumImageUrl).catch(() => null) : null
      this._artistImg = this.data.artistImageUrl ? await this._loadImage(this.data.artistImageUrl).catch(() => null) : null
      await this._loadAudio()
      // Parse after audio load so this.duration is known
      this.lines = this._parseMarkers(this.data.markers || [])
      this._buildFlashTimes()
      // Compute silent offset (mirrors Kotlin getStartSilentOffsetMs):
      // if first syllable < 5s, prepend silence so counters have time to appear
      this._silentOffset = this._computeSilentOffset()
      this._preroll = this._splashDur + this._silentOffset
      this._isPrerolling = true
      this._dtPaused = 0
      this._buildWaveforms()
      this._ready = true
      this._startFadeStartedAt = Date.now()   // запустить переход logo→splash
      this._updateExportMenuAvailability()
    } catch (e) {
      console.error('KaraokePlayer init error:', e)
      const loading = this.container?.querySelector('#kp-loading')
      if (loading) {
        loading.textContent = e instanceof PlayerUnavailableError
          ? 'Извините, данная песня пока не может быть проиграна в плеере'
          : `Ошибка: ${e.message}`
        loading.style.background = 'transparent'
        loading.style.display = 'flex'
      }
    }
  }

  // ─── Preroll ──────────────────────────────────────────────────────────────

  // Mirrors Kotlin getStartSilentOffsetMs(): if first syllable appears before 5s,
  // prepend that many seconds of silence so counters can appear before the first syllable.
  _computeSilentOffset() {
    let timeFirst = Infinity
    for (const voice of (this.data?.markers || [])) {
      for (const m of voice) {
        if ((m.markertype === 'syllables' || m.markertype === 'note') && m.time < timeFirst)
          timeFirst = m.time
      }
    }
    if (!isFinite(timeFirst) || timeFirst > this._splashDur) return 0
    return this._splashDur - timeFirst
  }

  // Display time: 0 = splash start, _preroll = audio starts, _preroll+duration = end.
  // During preroll uses wall clock; during audio uses AudioContext.
  _getDisplayTime() {
    if (this._isPrerolling) {
      const elapsed = this.isPlaying ? (Date.now() - this._prerollRef) / 1000 : 0
      return this._dtPaused + elapsed
    }
    return this._preroll + (this.isPlaying ? this.audioCtx.currentTime - this.startedAt : this.pausedAt)
  }

  // ─── Parsing ──────────────────────────────────────────────────────────────

  // Mirrors Kotlin's String.uppercaseFirstLetter(): skips punctuation/special chars,
  // uppercases the first regular character found.
  _uppercaseFirstLetter(txt) {
    const SKIP = new Set('-_,.!@#№$;%^:&?*()[]{}|/\\"\'`~ «»')
    let result = ''
    let done = false
    for (const ch of txt) {
      if (!done && !SKIP.has(ch)) { result += ch.toUpperCase(); done = true }
      else result += ch
    }
    return result
  }

  _parseMarkers(markersList) {
    const allTextLines = []
    const perVoiceTextLines = markersList.map(() => [])  // per-voice, for independent scroll

    markersList.forEach((voiceMarkers, voiceIdx) => {
      let currentSyllables = []
      let lineStart = null
      let currentGroupId = 0       // updated by SETTING|GROUP|N markers
      let nextLineNeedsCounter = true  // first line of each voice always gets counter

      for (let i = 0; i < voiceMarkers.length; i++) {
        const m = voiceMarkers[i]
        const mt = m.markertype

        if (mt === 'setting') {
          const parts = (m.label || '').split('|')
          if (parts[0] === 'GROUP' && parts[1] !== undefined) {
            // GROUP|N — change current group style for subsequent syllables
            const newGroupId = parseInt(parts[1]) || 0
            if (newGroupId !== currentGroupId && allTextLines.length > 0) {
              nextLineNeedsCounter = true  // group change = verse/chorus boundary
            }
            currentGroupId = newGroupId
          } else if (parts[0] === 'COMMENT') {
            // COMMENT|text — render as a styled label line in the scroll timeline.
            // "COMMENT| " (space only) is a blank separator — skip it.
            const commentContent = (parts[1] || '').replace(/_/g, ' ').trim()
            if (commentContent) {
              // Duration = until the next syllable/endofline/newline marker
              let endTime = m.time + 3.0
              for (let j = i + 1; j < voiceMarkers.length; j++) {
                const mt2 = voiceMarkers[j].markertype
                if (mt2 === 'syllables' || mt2 === 'endofline' || mt2 === 'newline') {
                  endTime = voiceMarkers[j].time; break
                }
              }
              const commentLine = {
                voiceIdx, groupId: currentGroupId,
                startTime: m.time, endTime,
                syllables: [], hasCounter: false, isEmpty: false,
                isComment: true,
                commentText: this._uppercaseFirstLetter(commentContent)
              }
              allTextLines.push(commentLine)
              perVoiceTextLines[voiceIdx].push(commentLine)
            }
          }

        } else if (mt === 'syllables') {
          if (lineStart === null) lineStart = m.time

          // End of this syllable = next endofsyllable / syllables / endofline
          let syllableEnd = null
          for (let j = i + 1; j < voiceMarkers.length; j++) {
            const mt2 = voiceMarkers[j].markertype
            if (mt2 === 'endofsyllable' || mt2 === 'syllables' || mt2 === 'endofline') {
              syllableEnd = voiceMarkers[j].time
              break
            }
          }

          let sylText = (m.label || '').replace(/_/g, ' ')
          if (currentSyllables.length === 0) sylText = this._uppercaseFirstLetter(sylText)

          currentSyllables.push({
            text: sylText,
            startTime: m.time,
            endTime: syllableEnd !== null ? syllableEnd : m.time
          })

        } else if (mt === 'endofsyllable') {
          // Set end of previous syllable; do NOT close the line
          if (currentSyllables.length > 0) {
            currentSyllables[currentSyllables.length - 1].endTime = m.time
          }
          // "⸳" dot character omitted for visual simplicity

        } else if (mt === 'endofline') {
          if (currentSyllables.length > 0) {
            // Last syllable ends here
            const last = currentSyllables[currentSyllables.length - 1]
            if (last.endTime <= last.startTime) last.endTime = m.time

            // Fix syllables where start===end (set to next syllable's start)
            for (let j = 0; j < currentSyllables.length - 1; j++) {
              if (currentSyllables[j].endTime <= currentSyllables[j].startTime) {
                currentSyllables[j].endTime = currentSyllables[j + 1].startTime
              }
            }

            const textLine = {
              voiceIdx,
              groupId: currentGroupId,
              startTime: lineStart,
              endTime: m.time,
              syllables: currentSyllables,
              hasCounter: nextLineNeedsCounter,
              isEmpty: false
            }
            allTextLines.push(textLine)
            perVoiceTextLines[voiceIdx].push(textLine)

            nextLineNeedsCounter = false
            currentSyllables = []
            lineStart = null
          } else {
            // endofline with no syllables = blank line = verse separator
            nextLineNeedsCounter = true
          }

        } else if (mt === 'newline') {
          // newline = explicit verse separator; force-close any open line first
          if (currentSyllables.length > 0 && lineStart !== null) {
            const textLine = {
              voiceIdx,
              groupId: currentGroupId,
              startTime: lineStart,
              endTime: m.time,
              syllables: currentSyllables,
              hasCounter: nextLineNeedsCounter,
              isEmpty: false
            }
            allTextLines.push(textLine)
            perVoiceTextLines[voiceIdx].push(textLine)
            currentSyllables = []
            lineStart = null
          }
          nextLineNeedsCounter = true
        }
      }
    })

    allTextLines.sort((a, b) => a.startTime - b.startTime)
    if (allTextLines.length === 0) {
      this.voiceLines = perVoiceTextLines.map(() => [])
      return []
    }

    // Mirror Kotlin: if gap to next line < 1s, start scrolling early so the scroll window = 1s.
    // scrollStartTime = when the line begins moving up (may be < endTime so fill continues during scroll).
    // endTime is preserved as actual last-syllable end for fill animation.
    for (let i = 0; i < allTextLines.length - 1; i++) {
      const curr = allTextLines[i]
      const next = allTextLines[i + 1]
      if (next.startTime - curr.endTime < 1.0) {
        curr.scrollStartTime = Math.max(curr.startTime, next.startTime - 1.0)
      }
    }

    // Build per-voice scroll lines (each voice scrolls independently in its own column)
    this.voiceLines = perVoiceTextLines.map(vtl => {
      if (vtl.length === 0) return []
      vtl.sort((a, b) => a.startTime - b.startTime)
      // scrollStartTime per-voice: each voice considers only its own adjacent lines
      for (let i = 0; i < vtl.length - 1; i++) {
        const curr = vtl[i]
        const next = vtl[i + 1]
        if (next.startTime - curr.endTime < 1.0) {
          curr.scrollStartTime = Math.max(curr.startTime, next.startTime - 1.0)
        }
      }
      return this._buildScrollLines(vtl, true)  // quickEnd: scroll off fast after voice ends
    })

    return this._buildScrollLines(allTextLines, true)  // quickEnd: guarantee full scroll-off before header returns
  }

  // Insert empty placeholder lines so scroll speed stays consistent through silences.
  // quickEnd=true: trailing empties cover only ~4 line-durations after the last line (for per-voice).
  // quickEnd=false (default): trailing empties spread over the full song duration.
  _buildScrollLines(textLines, quickEnd = false) {
    if (!textLines.length) return []

    const maxDur = Math.max(...textLines.map(l => l.endTime - l.startTime))
    if (maxDur <= 0) return textLines

    const result = []

    // Empty lines before first text line
    const firstStart = textLines[0].startTime
    result.push(this._makeEmptyLine(0))
    const countStart = Math.floor(firstStart / maxDur)
    if (countStart > 0) {
      const step = firstStart / (countStart + 1)
      for (let i = 0; i < countStart; i++) {
        result.push(this._makeEmptyLine((i + 1) * step))
      }
    }
    if (countStart > 0 || firstStart > maxDur * 0.5) {
      textLines[0].hasCounter = true
    }

    // Text lines + empty lines in gaps
    for (let i = 0; i < textLines.length; i++) {
      result.push(textLines[i])

      if (i < textLines.length - 1) {
        const next = textLines[i + 1]
        const gapStart = textLines[i].scrollStartTime ?? textLines[i].endTime
        const gapEnd = next.startTime
        const gap = gapEnd - gapStart

        // Insert empty lines if next line is a verse start OR gap is large
        const needsEmpty = next.hasCounter || gap > maxDur
        if (needsEmpty) {
          const countEmpty = Math.max(next.hasCounter ? 1 : 0, Math.floor(gap / maxDur))
          const step = gap / (countEmpty + 1)
          for (let j = 0; j < countEmpty; j++) {
            result.push(this._makeEmptyLine(gapStart + (j + 1) * step))
          }
        }
      }
    }

    // Empty lines after last text line
    const lastLine = textLines[textLines.length - 1]
    if (quickEnd) {
      // Add enough trailing empty lines to push last text off screen top (~10 lines needed).
      // Use 14 lines spaced maxDur apart to guarantee clearance on any screen resolution.
      const TRAILING = 14
      for (let i = 0; i < TRAILING; i++) {
        result.push(this._makeEmptyLine(lastLine.endTime + (i + 1) * maxDur))
      }
    } else {
      const totalDur = this.duration > 0 ? this.duration : lastLine.endTime + maxDur * 3
      const endGap = totalDur - lastLine.endTime
      const countEnd = Math.min(5, Math.max(1, Math.floor(endGap / maxDur)))
      const stepEnd = endGap / (countEnd + 1)
      for (let i = 0; i < countEnd; i++) {
        result.push(this._makeEmptyLine(lastLine.endTime + (i + 1) * stepEnd))
      }
    }

    result.sort((a, b) => a.startTime - b.startTime)
    return result
  }

  _makeEmptyLine(timeSec) {
    return { voiceIdx: 0, startTime: timeSec, endTime: timeSec, syllables: [], hasCounter: false, isEmpty: true }
  }

  // ─── Smooth scroll position ────────────────────────────────────────────────

  // Returns a float: integer part = line index at centre, fraction = progress toward next
  _getScrollPosition(ct) {
    const lines = this.lines
    if (!lines.length) return 0

    // During silent offset (ct < 0): scroll 4 virtual lines from below center → center.
    // This gives the text a running start so it arrives at the horizon right when audio begins.
    if (ct < 0 && this._silentOffset > 0) {
      const LEAD = 4
      return LEAD * ct / this._silentOffset  // -4 at silence start → 0 at audio start
    }

    // Frozen inside a non-empty text line (until scrollStartTime if set, else until endTime)
    for (let i = 0; i < lines.length; i++) {
      const l = lines[i]
      const freezeUntil = l.scrollStartTime ?? l.endTime
      if (!l.isEmpty && !l.isComment && freezeUntil > l.startTime && ct >= l.startTime && ct <= freezeUntil) {
        return i
      }
    }

    if (ct <= lines[0].startTime) return 0
    if (ct >= lines[lines.length - 1].startTime) return lines.length - 1

    // Interpolate between departure of curr and arrival of next
    for (let i = 0; i < lines.length - 1; i++) {
      const curr = lines[i]
      const next = lines[i + 1]
      const t0 = curr.isEmpty ? curr.startTime : (curr.scrollStartTime ?? curr.endTime)
      const t1 = next.startTime
      if (ct >= t0 && ct < t1) {
        if (t1 <= t0) return i
        return i + (ct - t0) / (t1 - t0)
      }
    }

    return lines.length - 1
  }

  // Per-voice scroll position (same logic as _getScrollPosition but operates on voiceLines[v])
  _getScrollPositionForVoice(ct, voiceIdx) {
    const lines = this.voiceLines?.[voiceIdx]
    if (!lines || !lines.length) return 0

    if (ct < 0 && this._silentOffset > 0) {
      const LEAD = 4
      return LEAD * ct / this._silentOffset
    }

    for (let i = 0; i < lines.length; i++) {
      const l = lines[i]
      const freezeUntil = l.scrollStartTime ?? l.endTime
      if (!l.isEmpty && !l.isComment && freezeUntil > l.startTime && ct >= l.startTime && ct <= freezeUntil) {
        return i
      }
    }

    if (ct <= lines[0].startTime) return 0
    if (ct >= lines[lines.length - 1].startTime) return lines.length - 1

    for (let i = 0; i < lines.length - 1; i++) {
      const curr = lines[i]
      const next = lines[i + 1]
      const t0 = curr.isEmpty ? curr.startTime : (curr.scrollStartTime ?? curr.endTime)
      const t1 = next.startTime
      if (ct >= t0 && ct < t1) {
        if (t1 <= t0) return i
        return i + (ct - t0) / (t1 - t0)
      }
    }

    return lines.length - 1
  }

  // ─── UI ───────────────────────────────────────────────────────────────────

  // Standard desktop-menu semantics: plain items act immediately on click; items that own a
  // submenu (kp-menu-parent) show a ▸ arrow and open it either on hover or on click, matching how
  // native OS/app menus behave. Injected once (idempotent) rather than per-instance inline styles.
  _injectMenuStyles() {
    if (document.getElementById('kp-menu-styles')) return
    const style = document.createElement('style')
    style.id = 'kp-menu-styles'
    style.textContent = `
      .kp-menu { display:none; position:absolute; bottom:100%; right:0; margin-bottom:6px; background:#222; border:1px solid #444; border-radius:6px; padding:4px 0; min-width:190px; box-shadow:0 4px 16px rgba(0,0,0,0.6); z-index:30; font-size:13px; }
      .kp-menu-item { display:flex; align-items:center; justify-content:space-between; gap:16px; padding:7px 14px; cursor:pointer; color:#eee; white-space:nowrap; }
      .kp-menu-item:hover { background:#08f; color:#fff; }
      .kp-menu-separator { height:1px; background:#444; margin:4px 0; }
      .kp-menu-arrow { color:#999; font-size:11px; }
      .kp-menu-item:hover .kp-menu-arrow { color:#fff; }
      .kp-menu-parent { position:relative; }
      .kp-submenu { display:none; position:absolute; right:100%; bottom:-5px; background:#222; border:1px solid #444; border-radius:6px; padding:4px 0; min-width:150px; box-shadow:0 4px 16px rgba(0,0,0,0.6); }
      .kp-menu-parent:hover > .kp-submenu, .kp-menu-parent.kp-submenu-open > .kp-submenu { display:block; }
    `
    document.head.appendChild(style)
  }

  _buildUI() {
    this._injectMenuStyles()
    this.container.style.cssText = 'position:relative;background:#000;user-select:none;font-family:sans-serif'
    this.container.innerHTML = `
      <div style="display:flex;flex-direction:column;height:100vh;background:#000">
        <div id="kp-canvas-wrap" style="flex:1;position:relative;overflow:hidden;min-height:0">
          <canvas id="kp-canvas" style="width:100%;height:100%;display:block"></canvas>
          <div id="kp-loading" style="position:absolute;inset:0;display:none;align-items:center;justify-content:center;color:#aaa;font-size:20px;background:transparent"></div>
        </div>
        <div style="background:#111;padding:4px 10px;display:flex;align-items:stretch;gap:8px">
          <button id="kp-anchor" title="Заякорить громкость всех треков" style="background:none;border:1px solid #444;border-radius:4px;color:#ccc;cursor:pointer;padding:0 8px;line-height:1;align-self:stretch;display:flex;align-items:center;justify-content:center"></button>
          <div style="flex:1;min-width:0">
            <div style="display:flex;align-items:center;gap:8px;margin-bottom:2px">
              <span style="color:#888;font-size:11px;width:44px;text-align:right">Музыка</span>
              <input id="kp-vol-acc" type="range" min="0" max="100" value="${this._accVol}" style="width:80px;cursor:pointer;accent-color:#08f">
              <div id="kp-ws-acc" style="flex:1;height:40px;min-width:0"></div>
            </div>
            <div style="display:flex;align-items:center;gap:8px">
              <span style="color:#888;font-size:11px;width:44px;text-align:right">Голос</span>
              <input id="kp-vol-voc" type="range" min="0" max="100" value="${this._vocVol}" style="width:80px;cursor:pointer;accent-color:#f80">
              <div id="kp-ws-voc" style="flex:1;height:40px;min-width:0"></div>
            </div>
          </div>
          <div style="display:flex;flex-direction:column;gap:2px;align-self:stretch">
            <button id="kp-widemode" title="Широкий" style="flex:1;background:none;border:1px solid #444;border-radius:4px;color:#ccc;cursor:pointer;padding:0 8px;display:flex;align-items:center;justify-content:center;min-width:36px">
              <svg width="16" height="12" viewBox="0 0 16 12" fill="none" stroke="currentColor" stroke-width="1.4"><rect x="0.7" y="0.7" width="14.6" height="10.6" rx="1.6"/></svg>
            </button>
            <button id="kp-fs" title="Полноэкранный режим" style="flex:1;background:none;border:1px solid #444;border-radius:4px;color:#ccc;cursor:pointer;padding:0 8px;display:flex;align-items:center;justify-content:center;min-width:36px;font-size:14px;line-height:1">⛶</button>
          </div>
        </div>
        <div style="background:#111;border-top:1px solid #333;padding:6px 12px;display:flex;align-items:center;gap:10px">
          <button id="kp-play" style="background:none;border:none;color:#fff;font-size:22px;cursor:pointer;padding:0;line-height:1;min-width:28px">▶</button>
          <span id="kp-time" style="color:#888;font-size:12px;min-width:88px">0:00 / 0:00</span>
          <div id="kp-progress-wrap" style="flex:1;height:5px;background:#333;border-radius:3px;cursor:pointer;position:relative">
            <div id="kp-progress-bar" style="height:100%;background:#f80;border-radius:3px;width:0%;pointer-events:none"></div>
          </div>
          <input type="file" id="kp-file-input" accept=".smkaraoke" style="display:none">
          <div id="kp-menu-wrap" style="position:relative">
            <button id="kp-menu-btn" title="Меню" style="background:none;border:none;color:#ccc;font-size:16px;cursor:pointer;padding:0 4px">☰</button>
            <div id="kp-menu" class="kp-menu">
              <div class="kp-menu-item" id="kp-menu-open"><span>Открыть файл...</span></div>
              <div class="kp-menu-item" id="kp-menu-save"><span>Сохранить файл</span></div>
              <div class="kp-menu-separator"></div>
              <div class="kp-menu-item kp-menu-parent" id="kp-menu-export">
                <span>Экспорт аудио...</span><span class="kp-menu-arrow">▸</span>
                <div class="kp-submenu" id="kp-submenu-export">
                  <div class="kp-menu-item" data-stem="vocals"><span>Голос</span></div>
                  <div class="kp-menu-item" data-stem="accompaniment"><span>Минусовка</span></div>
                  <div class="kp-menu-item" data-stem="bass" style="display:none"><span>Бас</span></div>
                  <div class="kp-menu-item" data-stem="drums" style="display:none"><span>Ударные</span></div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>`

    this.canvas = this.container.querySelector('#kp-canvas')
    this.ctx = this.canvas.getContext('2d')
    this._resizeCanvas()

    window.addEventListener('resize', this._resizeHandler)
    document.addEventListener('fullscreenchange', this._fsHandler)

    this.container.querySelector('#kp-play').addEventListener('click', () => this._togglePlay())
    this.container.querySelector('#kp-fs').addEventListener('click', () => this._toggleFullscreen())
    this.container.querySelector('#kp-widemode').addEventListener('click', () => this._toggleDisplayMode())
    this._updateDisplayModeButton()
    this._updateFullscreenButton()
    this.container.querySelector('#kp-file-input').addEventListener('change', e => {
      const file = e.target.files[0]
      if (file) this._loadNewFile(file)
    })
    this._buildMenu()

    const pw = this.container.querySelector('#kp-progress-wrap')
    pw.addEventListener('click', e => {
      const r = pw.getBoundingClientRect()
      const totalDuration = this._preroll + this.duration
      if (totalDuration <= 0) return
      this._seekToDisplayTime(((e.clientX - r.left) / r.width) * totalDuration)
    })

    const accSlider = this.container.querySelector('#kp-vol-acc')
    const vocSlider = this.container.querySelector('#kp-vol-voc')

    accSlider.addEventListener('input', e => {
      this._accVol = Number(e.target.value)
      if (this.accGain) this.accGain.gain.value = e.target.value / 100
      if (this._volumeAnchored) this._syncVolumeSliders(e.target.value, accSlider)
    })
    vocSlider.addEventListener('input', e => {
      this._vocVol = Number(e.target.value)
      if (this.vocGain) this.vocGain.gain.value = e.target.value / 100
      if (this._volumeAnchored) this._syncVolumeSliders(e.target.value, vocSlider)
    })

    const anchorBtn = this.container.querySelector('#kp-anchor')
    this._renderAnchorIcon(anchorBtn, this._volumeAnchored)
    anchorBtn.addEventListener('click', () => {
      this._volumeAnchored = !this._volumeAnchored
      this._renderAnchorIcon(anchorBtn, this._volumeAnchored)
      // Turning the anchor on brings every track to the "Музыка" (accompaniment) level.
      if (this._volumeAnchored) this._syncVolumeSliders(accSlider.value, accSlider)
    })
  }

  // Volume sliders currently played through the mixer ("Музыка"/"Голос"). Sets every
  // slider except `exceptEl` to `value` and applies it to the matching GainNode.
  _syncVolumeSliders(value, exceptEl) {
    const sliders = [
      { el: this.container.querySelector('#kp-vol-acc'), gain: () => this.accGain },
      { el: this.container.querySelector('#kp-vol-voc'), gain: () => this.vocGain }
    ]
    for (const s of sliders) {
      if (s.el === exceptEl) continue
      s.el.value = value
      if (s.el.id === 'kp-vol-acc') this._accVol = Number(value)
      else if (s.el.id === 'kp-vol-voc') this._vocVol = Number(value)
      const gain = s.gain()
      if (gain) gain.gain.value = value / 100
    }
    // exceptEl (тот, что двигали) — тоже фиксируем в персистентном уровне.
    if (exceptEl && exceptEl.id === 'kp-vol-acc') this._accVol = Number(value)
    else if (exceptEl && exceptEl.id === 'kp-vol-voc') this._vocVol = Number(value)
  }

  // Chain-link icon (like the width/height "constrain proportions" toggle in image editors):
  // two joined capsules when linked, pulled apart with a visible gap when unlinked.
  _renderAnchorIcon(btn, linked) {
    btn.style.background = linked ? '#08f' : 'none'
    btn.style.color = linked ? '#fff' : '#ccc'
    const rects = linked
      ? '<rect x="8" y="2" width="8" height="12" rx="4"/><rect x="8" y="10" width="8" height="12" rx="4"/>'
      : '<rect x="8" y="1" width="8" height="9" rx="4"/><rect x="8" y="14" width="8" height="9" rx="4"/>'
    btn.innerHTML = `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">${rects}</svg>`
  }

  // ─── Menu (load file / export audio) ───────────────────────────────────────

  static STEM_EXPORT_MAP = {
    vocals: { urlField: 'audioVocalsUrl', suffix: 'voice' },
    accompaniment: { urlField: 'audioAccompanimentUrl', suffix: 'accompaniment' },
    bass: { urlField: 'audioBassUrl', suffix: 'bass' },
    drums: { urlField: 'audioDrumsUrl', suffix: 'drums' }
  }

  _buildMenu() {
    const menuBtn = this.container.querySelector('#kp-menu-btn')
    const menu = this.container.querySelector('#kp-menu')
    const exportItem = this.container.querySelector('#kp-menu-export')
    const submenu = this.container.querySelector('#kp-submenu-export')
    const openItem = this.container.querySelector('#kp-menu-open')
    const saveItem = this.container.querySelector('#kp-menu-save')

    menuBtn.addEventListener('click', e => {
      e.stopPropagation()
      const isOpen = menu.style.display === 'block'
      this._closeMenu()
      menu.style.display = isOpen ? 'none' : 'block'
    })

    // The submenu already opens on hover via CSS (:hover); click toggles a class so it also
    // works without a pointing device that supports hover (touch, or a deliberate click).
    exportItem.addEventListener('click', e => {
      e.stopPropagation()
      exportItem.classList.toggle('kp-submenu-open')
    })

    openItem.addEventListener('click', () => {
      this._closeMenu()
      this.container.querySelector('#kp-file-input').click()
    })

    saveItem.addEventListener('click', () => {
      this._closeMenu()
      this._saveFile()
    })

    for (const el of submenu.querySelectorAll('[data-stem]')) {
      el.addEventListener('click', () => {
        this._closeMenu()
        this._exportStem(el.dataset.stem)
      })
    }

    document.addEventListener('click', this._menuOutsideClickHandler)
  }

  _closeMenu() {
    const menu = this.container.querySelector('#kp-menu')
    const exportItem = this.container.querySelector('#kp-menu-export')
    if (menu) menu.style.display = 'none'
    if (exportItem) exportItem.classList.remove('kp-submenu-open')
  }

  // Shows "Бас"/"Ударные" export items only if those stems actually exist for the loaded song.
  _updateExportMenuAvailability() {
    const bassItem = this.container.querySelector('[data-stem="bass"]')
    const drumsItem = this.container.querySelector('[data-stem="drums"]')
    if (bassItem) bassItem.style.display = this.data?.audioBassUrl ? 'flex' : 'none'
    if (drumsItem) drumsItem.style.display = this.data?.audioDrumsUrl ? 'flex' : 'none'
  }

  // Base file name for exports, mirrors the backend's ".smkaraoke" naming
  // ("<fileName> [id-<id>]", sanitized) so exported stems match the same pattern
  // e.g. "1996 (17) [Король и Шут] - Камнем по голове [id-533][accompaniment].mp3".
  _getExportBaseName() {
    if (this.data?.exportBaseName) return this.data.exportBaseName
    if (this._mode === 'blob' && this._smkaraokeSource instanceof File) {
      return this._smkaraokeSource.name.replace(/\.smkaraoke$/i, '')
    }
    const parts = [this.data?.songName, this.data?.author].filter(Boolean)
    return parts.length ? parts.join(' - ') : 'karaoke-export'
  }

  // Admin API URL for the full .smkaraoke container of the currently loaded song ('api' mode only).
  _getPlayerFileUrl() {
    return `${this.apiBase}/song/${this.songId}/playerfile`
  }

  async _exportStem(stemKey) {
    const cfg = KaraokePlayer.STEM_EXPORT_MAP[stemKey]
    const url = cfg && this.data?.[cfg.urlField]
    if (!url) return
    const suggestedName = `${this._getExportBaseName()}[${cfg.suffix}].mp3`
    try {
      const resp = await fetch(url)
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
      const blob = await resp.blob()
      await this._saveBlob(blob, suggestedName, 'audio/mpeg', ['.mp3'])
    } catch (e) {
      if (e?.name !== 'AbortError') console.error('Export failed:', e)
    }
  }

  // "Сохранить файл": re-downloads/re-saves the current song as a .smkaraoke container. In 'blob'
  // mode the file is already in hand (just re-save it); in 'url-smkaraoke' mode re-fetch that URL;
  // in 'api' mode fetch it fresh from the admin export endpoint.
  async _saveFile() {
    const suggestedName = `${this._getExportBaseName()}.smkaraoke`
    try {
      let blob
      if (this._mode === 'blob' && this._smkaraokeSource instanceof File) {
        blob = this._smkaraokeSource
      } else if (this._mode === 'url-smkaraoke') {
        const resp = await fetch(this._smkaraokeSource)
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
        blob = await resp.blob()
      } else {
        const resp = await fetch(this._getPlayerFileUrl())
        if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
        blob = await resp.blob()
      }
      await this._saveBlob(blob, suggestedName, 'application/x-smkaraoke', ['.smkaraoke'])
    } catch (e) {
      if (e?.name !== 'AbortError') console.error('Save failed:', e)
    }
  }

  // Shared "save this blob to disk" logic for both stem export and full-file save: native
  // Save-As dialog where supported, otherwise a plain <a download> fallback.
  async _saveBlob(blob, suggestedName, mimeType, extensions) {
    if (window.showSaveFilePicker) {
      const handle = await window.showSaveFilePicker({
        suggestedName,
        types: [{ description: mimeType, accept: { [mimeType]: extensions } }]
      })
      const writable = await handle.createWritable()
      await writable.write(blob)
      await writable.close()
    } else {
      const blobUrl = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = blobUrl
      a.download = suggestedName
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(blobUrl)
    }
  }

  _resizeCanvas() {
    const wrap = this.container.querySelector('#kp-canvas-wrap')
    if (!wrap || !this.canvas) return
    this.canvas.width = wrap.clientWidth
    this.canvas.height = wrap.clientHeight
  }

  // ─── Audio ────────────────────────────────────────────────────────────────

  async _loadAudio() {
    this.audioCtx = new AudioContext()
    this.accGain = this.audioCtx.createGain()
    this.vocGain = this.audioCtx.createGain()
    // Применяем персистентные уровни (наследуются при смене трека в плейлисте).
    this.accGain.gain.value = this._accVol / 100
    this.vocGain.gain.value = this._vocVol / 100
    this.accGain.connect(this.audioCtx.destination)
    this.vocGain.connect(this.audioCtx.destination)

    // Общий прогресс скачивания = сумма байт обоих стемов. Пока Content-Length хотя бы одного
    // ещё не известен (total обоих = 0) — _loadProgress остаётся null (спиннер). Как только оба
    // стема скачаны, начинается decodeAudioData (прогресса не даёт) — _loadProgress уже равен 1,
    // индикатор показывает фазу «Обработка...».
    const prog = { acc: { received: 0, total: 0 }, voc: { received: 0, total: 0 } }
    const updateProgress = () => {
      const total = prog.acc.total + prog.voc.total
      const received = prog.acc.received + prog.voc.received
      this._loadProgress = total > 0 ? Math.min(1, received / total) : null
    }

    const [accBuf, vocBuf] = await Promise.all([
      this._fetchAudio(this.data.audioAccompanimentUrl, (r, t) => { prog.acc = { received: r, total: t }; updateProgress() }),
      this._fetchAudio(this.data.audioVocalsUrl, (r, t) => { prog.voc = { received: r, total: t }; updateProgress() })
    ])
    this.accBuffer = accBuf
    this.vocBuffer = vocBuf
    this.duration = Math.max(accBuf.duration, vocBuf.duration)
  }

  // Скачивает и декодирует аудио. При наличии тела ответа и Content-Length читает поток по чанкам
  // и репортит реальный прогресс через onProgress(received, total); иначе (нет заголовка/тела)
  // читает целиком одним куском — прогресс остаётся неопределённым (спиннер).
  async _fetchAudio(url, onProgress) {
    const resp = await fetch(url)
    if (!resp.ok) throw new PlayerUnavailableError(`Audio fetch failed: ${url}`)
    const total = Number(resp.headers.get('Content-Length')) || 0
    if (!resp.body || !total) {
      return this.audioCtx.decodeAudioData(await resp.arrayBuffer())
    }
    const reader = resp.body.getReader()
    const chunks = []
    let received = 0
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      chunks.push(value)
      received += value.length
      onProgress?.(received, total)
    }
    const all = new Uint8Array(received)
    let pos = 0
    for (const c of chunks) { all.set(c, pos); pos += c.length }
    return this.audioCtx.decodeAudioData(all.buffer)
  }

  // ─── .smkaraoke loader ────────────────────────────────────────────────────

  // Loads a .smkaraoke ZIP container from a File/Blob or a URL string.
  // Returns a data object compatible with the /playerdata API response,
  // with audioAccompanimentUrl / audioVocalsUrl / albumImageUrl / artistImageUrl
  // set to temporary blob: URLs (revoked on destroy()).
  async _loadSmkaraoke(source) {
    const { default: JSZip } = await import('jszip')
    let blob = source
    if (typeof source === 'string') {
      const resp = await fetch(source)
      if (!resp.ok) throw new Error(`Failed to fetch .smkaraoke: HTTP ${resp.status}`)
      blob = await resp.blob()
    }
    const zip = await JSZip.loadAsync(blob)
    const manifestText = await zip.file('manifest.json').async('text')
    const manifest = JSON.parse(manifestText)

    const toUrl = async (path) => {
      const entry = zip.file(path)
      if (!entry) return null
      const b = await entry.async('blob')
      const url = URL.createObjectURL(b)
      this._smkaraokeObjectUrls.push(url)
      return url
    }

    if (manifest.tracks?.accompaniment)
      manifest.audioAccompanimentUrl = await toUrl(manifest.tracks.accompaniment)
    if (manifest.tracks?.vocals)
      manifest.audioVocalsUrl = await toUrl(manifest.tracks.vocals)
    if (manifest.tracks?.bass)
      manifest.audioBassUrl = await toUrl(manifest.tracks.bass)
    if (manifest.tracks?.drums)
      manifest.audioDrumsUrl = await toUrl(manifest.tracks.drums)
    if (manifest.images?.album)
      manifest.albumImageUrl = await toUrl(manifest.images.album)
    if (manifest.images?.artist)
      manifest.artistImageUrl = await toUrl(manifest.images.artist)

    return manifest
  }

  // Extract N peak values (max-abs per block) from AudioBuffer channel 0
  _extractPeaks(buffer, numPeaks) {
    const ch = buffer.getChannelData(0)
    const blockSize = Math.floor(ch.length / numPeaks)
    const peaks = new Array(numPeaks)
    for (let i = 0; i < numPeaks; i++) {
      let max = 0
      const start = i * blockSize
      const end = Math.min(start + blockSize, ch.length)
      for (let j = start; j < end; j++) {
        const v = Math.abs(ch[j])
        if (v > max) max = v
      }
      peaks[i] = max
    }
    return peaks
  }

  _buildWaveforms() {
    const totalDuration = this._preroll + this.duration
    if (totalDuration <= 0 || !this.accBuffer || !this.vocBuffer) return

    // Peaks per second for good visual resolution
    const audioPeakCount = Math.max(500, Math.round(this.duration * 10))
    // Scale silence peaks proportionally so total peaks cover totalDuration
    const silencePeakCount = Math.round(audioPeakCount * this._preroll / this.duration)

    const silence = new Array(silencePeakCount).fill(0)
    const accPeaks = silence.concat(this._extractPeaks(this.accBuffer, audioPeakCount))
    const vocPeaks = silence.concat(this._extractPeaks(this.vocBuffer, audioPeakCount))

    import('wavesurfer.js').then(({ default: WaveSurfer }) => {
      const ac = this.container.querySelector('#kp-ws-acc')
      const vc = this.container.querySelector('#kp-ws-voc')
      if (!ac || !vc) return
      this.wsAcc = WaveSurfer.create({
        container: ac, height: 40,
        waveColor: '#4af', progressColor: '#08f',
        peaks: [accPeaks], duration: totalDuration
      })
      this.wsVoc = WaveSurfer.create({
        container: vc, height: 40,
        waveColor: '#fa4', progressColor: '#f80',
        peaks: [vocPeaks], duration: totalDuration
      })
      // interaction gives seconds within totalDuration → map directly to dt
      this.wsAcc.on('interaction', (newTime) => { if (!this._wsSeeking) this._seekToDisplayTime(newTime) })
      this.wsVoc.on('interaction', (newTime) => { if (!this._wsSeeking) this._seekToDisplayTime(newTime) })
    }).catch(e => console.warn('WaveSurfer load failed:', e))
  }

  _getCurrentTime() {
    if (!this.audioCtx) return 0
    return this.isPlaying ? this.audioCtx.currentTime - this.startedAt : this.pausedAt
  }

  _togglePlay() { this.isPlaying ? this._pause() : this._play() }

  async _play() {
    if (!this.accBuffer || !this.vocBuffer) return
    const dt = this._getDisplayTime()
    this._startFadeStartedAt = null // cancel logo→splash start transition
    this._endFadeStartedAt = null   // cancel any in-progress post-track idle transition

    if (dt < this._preroll) {
      // Pre-roll phase: run on wall clock, schedule audio start
      this._isPrerolling = true
      this._dtPaused = dt
      this._prerollRef = Date.now()
      this.isPlaying = true
      this._endedHandled = false
      const btn = this.container.querySelector('#kp-play')
      if (btn) btn.textContent = '⏸'
      const remainingMs = (this._preroll - dt) * 1000
      clearTimeout(this._prerollTimeout)
      this._prerollTimeout = setTimeout(() => {
        if (this.isPlaying && this._isPrerolling) {
          this._isPrerolling = false
          this._startAudio(0).catch(e => console.error('Audio start failed:', e))
        }
      }, remainingMs)
    } else {
      // Audio phase: start immediately at the saved position
      await this._startAudio(Math.max(0, Math.min(dt - this._preroll, this.duration)))
    }
  }

  async _startAudio(offset) {
    if (this.audioCtx.state === 'suspended') await this.audioCtx.resume()
    const accSrc = this.audioCtx.createBufferSource()
    accSrc.buffer = this.accBuffer
    accSrc.connect(this.accGain)
    const vocSrc = this.audioCtx.createBufferSource()
    vocSrc.buffer = this.vocBuffer
    vocSrc.connect(this.vocGain)
    this.accSource = accSrc
    this.vocSource = vocSrc
    this.startedAt = this.audioCtx.currentTime - offset
    accSrc.start(0, offset)
    vocSrc.start(0, offset)
    this.isPlaying = true
    this._isPrerolling = false
    this._endedHandled = false
    const btn = this.container.querySelector('#kp-play')
    if (btn) btn.textContent = '⏸'
    accSrc.onended = () => {
      if (this.accSource === accSrc && this.isPlaying && !this._endedHandled) {
        this._endedHandled = true
        this._onEnded()
      }
    }
  }

  _stopSources() {
    if (this.accSource) { this.accSource.onended = null; try { this.accSource.stop() } catch {} }
    if (this.vocSource) { this.vocSource.onended = null; try { this.vocSource.stop() } catch {} }
    this.accSource = null
    this.vocSource = null
  }

  _pause() {
    clearTimeout(this._prerollTimeout)
    if (this._isPrerolling) {
      this._dtPaused = this._getDisplayTime()
      this._isPrerolling = false
    } else {
      this.pausedAt = this._getCurrentTime()
      this._endedHandled = true
      this._stopSources()
    }
    this.isPlaying = false
    const btn = this.container.querySelector('#kp-play')
    if (btn) btn.textContent = '▶'
  }

  _onEnded() {
    clearTimeout(this._prerollTimeout)
    this.pausedAt = 0
    this._dtPaused = 0
    this._isPrerolling = true   // next play restarts from splash
    this.isPlaying = false
    this._endFadeStartedAt = Date.now()   // start logo→splash idle transition
    const btn = this.container.querySelector('#kp-play')
    if (btn) btn.textContent = '▶'
    if (this.onTrackEnded) { try { this.onTrackEnded() } catch (e) { console.error('onTrackEnded error:', e) } }
  }

  // --- Публичное управление (для страницы плейлиста) ------------------------------------------
  play() { this._play() }
  pause() { this._pause() }
  togglePlay() { this._togglePlay() }

  // Сменить проигрываемую песню в api-режиме, переиспользуя инстанс (без destroy). Зеркалит
  // teardown/сброс из _loadNewFile, но остаётся в 'api'. autoplay=true — играть сразу по готовности.
  async playSong(songId, autoplay = true) {
    if (this.animId) { cancelAnimationFrame(this.animId); this.animId = null }
    this._endedHandled = true
    this._stopSources()
    if (this.audioCtx) { await this.audioCtx.close(); this.audioCtx = null }
    if (this.wsAcc) { this.wsAcc.destroy(); this.wsAcc = null }
    if (this.wsVoc) { this.wsVoc.destroy(); this.wsVoc = null }
    window.removeEventListener('resize', this._resizeHandler)
    document.removeEventListener('fullscreenchange', this._fsHandler)
    document.removeEventListener('click', this._menuOutsideClickHandler)
    for (const url of this._smkaraokeObjectUrls) URL.revokeObjectURL(url)
    this._smkaraokeObjectUrls = []

    this._mode = 'api'
    this.songId = songId
    this.assignmentId = null
    this.target = null

    this.accBuffer = null; this.vocBuffer = null
    this.accSource = null; this.vocSource = null
    this.accGain = null; this.vocGain = null
    this.startedAt = 0; this.pausedAt = 0
    this.isPlaying = false; this.duration = 0
    this.data = null; this.lines = []; this.voiceLines = []
    this._ready = false
    this._loadProgress = null
    this._endedHandled = false
    this._cachedCanvasW = null; this._cachedVoiceXStart = null
    this._lastWsSync = 0
    this.flashTimes = []
    this._isPrerolling = false; this._dtPaused = 0
    this._silentOffset = 0; this._preroll = this._splashDur
    this._startFadeStartedAt = null
    this._endFadeStartedAt = null
    // _volumeAnchored / _accVol / _vocVol НЕ сбрасываем — уровни громкости и якорь наследуются
    // следующим треком плейлиста (по требованию).
    clearTimeout(this._prerollTimeout); this._prerollTimeout = null

    await this.init()
    if (autoplay && this._ready) this._play()
  }

  _seekTo(time) {
    time = Math.max(0, Math.min(time, this.duration))
    const was = this.isPlaying
    clearTimeout(this._prerollTimeout)
    if (!this._isPrerolling && was) {
      this._endedHandled = true
      this._stopSources()
    }
    this._isPrerolling = false
    this.isPlaying = false
    this.pausedAt = time
    if (was) this._play()
    const totalDuration = this._preroll + this.duration
    if (totalDuration > 0) {
      // WaveSurfer covers [0, totalDuration]; audio starts at _preroll
      const pct = (time + this._preroll) / totalDuration
      this._wsSeeking = true
      try { this.wsAcc?.seekTo(pct) } catch {}
      try { this.wsVoc?.seekTo(pct) } catch {}
      this._wsSeeking = false
    }
  }

  // Seek to display time dt ∈ [0, _preroll + duration] (0 = splash start)
  _seekToDisplayTime(dt) {
    this._startFadeStartedAt = null // manual seek should snap straight to the target frame
    this._endFadeStartedAt = null   // manual seek should snap straight to the target frame
    const totalDuration = this._preroll + this.duration
    dt = Math.max(0, Math.min(dt, totalDuration))
    const audioTime = dt - this._preroll
    if (audioTime >= 0) {
      this._seekTo(audioTime)
    } else {
      // dt < _preroll: seeking into splash or silent offset
      const was = this.isPlaying
      clearTimeout(this._prerollTimeout)
      if (!this._isPrerolling && was) {
        this._endedHandled = true
        this._stopSources()
      }
      this._isPrerolling = true
      this.isPlaying = false
      this._dtPaused = dt
      if (totalDuration > 0) {
        this._wsSeeking = true
        try { this.wsAcc?.seekTo(dt / totalDuration) } catch {}
        try { this.wsVoc?.seekTo(dt / totalDuration) } catch {}
        this._wsSeeking = false
      }
      if (was) this._play()
    }
  }

  _toggleFullscreen() {
    document.fullscreenElement ? document.exitFullscreen() : this.container.requestFullscreen?.()
  }

  // Runs on every 'fullscreenchange' (both entering and leaving). Besides the existing canvas
  // resize, tracks the transition so the wide-mode button can be disabled while fullscreen is
  // active and the player can snap back to whichever display mode ('embed'/'page') it was in right
  // before fullscreen was requested — fullscreen itself is orthogonal to that mode, not a third
  // persistent state of it.
  _onFullscreenChange() {
    this._resizeCanvas()
    const isFs = !!document.fullscreenElement
    if (isFs && !this._isFullscreen) {
      this._isFullscreen = true
      this._preFullscreenDisplayMode = this._displayMode
    } else if (!isFs && this._isFullscreen) {
      this._isFullscreen = false
      const restoreMode = this._preFullscreenDisplayMode
      this._preFullscreenDisplayMode = null
      if (restoreMode) this._setDisplayMode(restoreMode)
    }
    this._updateDisplayModeButton()
    this._updateFullscreenButton()
  }

  // ─── Display mode (embed / page) + fullscreen button state ────────────────

  // Only meaningful when the player actually lives inside an iframe on a host page (this._isEmbedded)
  // — tells that host page (song page's player card) to resize the iframe box itself; the player's
  // own container already fills 100% of whatever box the iframe ends up with (see PlayerView.vue),
  // so no internal layout change is needed here beyond the button's own visual state.
  _setDisplayMode(mode) {
    if (!this._isEmbedded) return
    this._displayMode = mode
    this._updateDisplayModeButton()
    try { window.parent.postMessage({ source: 'karaoke-player', type: 'display-mode', mode }, '*') } catch (e) { /* ignore */ }
  }

  _toggleDisplayMode() {
    if (!this._isEmbedded || this._isFullscreen) return
    this._setDisplayMode(this._displayMode === 'embed' ? 'page' : 'embed')
  }

  _updateDisplayModeButton() {
    const btn = this.container?.querySelector('#kp-widemode')
    if (!btn) return
    const disabled = !this._isEmbedded || this._isFullscreen
    btn.disabled = disabled
    btn.style.opacity = disabled ? '0.35' : '1'
    btn.style.cursor = disabled ? 'default' : 'pointer'
    const active = this._displayMode === 'page'
    btn.style.background = active ? '#08f' : 'none'
    btn.style.color = active ? '#fff' : '#ccc'
  }

  _updateFullscreenButton() {
    const btn = this.container?.querySelector('#kp-fs')
    if (!btn) return
    const active = !!document.fullscreenElement
    btn.style.background = active ? '#08f' : 'none'
    btn.style.color = active ? '#fff' : '#ccc'
  }

  // ─── Render loop ──────────────────────────────────────────────────────────

  _startRenderLoop() {
    const render = () => { this._renderFrame(); this.animId = requestAnimationFrame(render) }
    this.animId = requestAnimationFrame(render)
  }

  // ─── Background ────────────────────────────────────────────────────────────

  _seededRandom(seed) {
    let s = ((seed ^ 0xdeadbeef) >>> 0) | 1
    return () => { s ^= s << 13; s ^= s >>> 17; s ^= s << 5; return (s >>> 0) / 0x100000000 }
  }

  _generateStarfield() {
    // 4096×4096 texture; viewport = 1920×1080 (native, no upscaling)
    // Pan range: X: 0→2176, Y: 0→3016 (triangle-wave oscillation via performance.now)
    const TW = 4096, TH = 4096
    const canvas = new OffscreenCanvas(TW, TH)
    const ctx = canvas.getContext('2d')
    const seed = Math.abs(parseInt(this.songId) || 42)
    const rng = this._seededRandom(seed)

    const palettes = [
      // 0: cool purple/blue  (Color series)
      { base: '#000508', clouds: [[80,20,160],[40,80,200],[100,50,180]], ambient: [10,5,30,0.3], darkCores: true, warmStars: false, streak: false },
      // 1: crimson/magenta   (Mono series)
      { base: '#050002', clouds: [[200,10,60],[180,0,80],[220,60,120]], ambient: [20,0,10,0.25], darkCores: true, warmStars: true, streak: false },
      // 2: dark teal         (Dark series)
      { base: '#000a06', clouds: [[10,100,70],[20,130,80],[0,70,55]], ambient: [5,20,12,0.2], darkCores: false, warmStars: false, streak: false },
      // 3: fire orange/red   (Variable intense)
      { base: '#030100', clouds: [[220,80,0],[200,40,0],[180,60,10],[255,180,20]], ambient: [15,5,0,0.3], darkCores: true, warmStars: true, streak: true },
      // 4: purple+magenta    (Variable multi)
      { base: '#030008', clouds: [[60,20,160],[180,20,200],[30,100,220],[140,80,200]], ambient: [8,5,20,0.25], darkCores: true, warmStars: false, streak: false },
      // 5: warm amber        (Dark warm series)
      { base: '#040200', clouds: [[160,70,10],[140,50,0],[180,100,20],[120,60,0]], ambient: [10,5,0,0.2], darkCores: false, warmStars: true, streak: false },
    ]
    const pal = palettes[seed % palettes.length]

    // Base fill
    ctx.fillStyle = pal.base
    ctx.fillRect(0, 0, TW, TH)

    // Ambient gradient from one corner
    const [ar, ag, ab, aa] = pal.ambient
    const agx = TW * (0.55 + rng() * 0.35), agy = TH * (rng() * 0.45)
    const amGrad = ctx.createRadialGradient(agx, agy, 0, agx, agy, TW * 1.15)
    amGrad.addColorStop(0, `rgba(${ar},${ag},${ab},${aa})`)
    amGrad.addColorStop(1, 'rgba(0,0,0,0)')
    ctx.fillStyle = amGrad
    ctx.fillRect(0, 0, TW, TH)

    // Nebula clouds: 2-4 regions, each built from many overlapping soft gradients
    const nClouds = 2 + Math.floor(rng() * 3)
    for (let c = 0; c < nClouds; c++) {
      const cx = TW * (0.15 + rng() * 0.7)
      const cy = TH * (0.1 + rng() * 0.8)
      const rW = TW * (0.18 + rng() * 0.42)
      const rH = rW * (0.4 + rng() * 0.9)       // can be elongated
      const rot = rng() * Math.PI
      const ci = Math.floor(rng() * pal.clouds.length)
      const [cr, cg, cb] = pal.clouds[ci]
      const ci2 = Math.floor(rng() * pal.clouds.length)
      const [cr2, cg2, cb2] = pal.clouds[ci2]   // secondary for mixing

      // Paint as many overlapping semi-transparent blobs
      const nBlobs = 65 + Math.floor(rng() * 85)
      for (let i = 0; i < nBlobs; i++) {
        // Approx normal distribution within the ellipse
        const u = (rng() + rng() + rng() - 1.5) / 1.5
        const v = (rng() + rng() + rng() - 1.5) / 1.5
        const falloff = rng()
        const dx = u * rW * falloff
        const dy = v * rH * falloff
        const bx = cx + dx * Math.cos(rot) - dy * Math.sin(rot)
        const by = cy + dx * Math.sin(rot) + dy * Math.cos(rot)
        const br = rW * (0.04 + rng() * 0.22)
        const alpha = 0.025 + rng() * 0.065
        const mix = rng() < 0.28
        const R = mix ? cr2 : cr, G = mix ? cg2 : cg, B = mix ? cb2 : cb
        const R2 = Math.round(R * 0.5), G2 = Math.round(G * 0.5), B2 = Math.round(B * 0.5)
        const grad = ctx.createRadialGradient(bx, by, 0, bx, by, br)
        grad.addColorStop(0, `rgba(${R},${G},${B},${alpha})`)
        grad.addColorStop(0.45, `rgba(${R2},${G2},${B2},${alpha * 0.5})`)
        grad.addColorStop(1, 'rgba(0,0,0,0)')
        ctx.fillStyle = grad
        const x0 = Math.max(0, bx - br), y0 = Math.max(0, by - br)
        ctx.fillRect(x0, y0, Math.min(TW, bx + br) - x0, Math.min(TH, by + br) - y0)
      }

      // Bright core highlight
      if (rng() > 0.35) {
        const hR = rW * 0.09
        const hGrad = ctx.createRadialGradient(cx, cy, 0, cx, cy, hR)
        hGrad.addColorStop(0, `rgba(${Math.min(255,cr+100)},${Math.min(255,cg+100)},${Math.min(255,cb+100)},0.3)`)
        hGrad.addColorStop(1, 'rgba(0,0,0,0)')
        ctx.fillStyle = hGrad
        ctx.fillRect(cx - hR, cy - hR, hR * 2, hR * 2)
      }

      // Dark absorption core (dark pillars / negative space)
      if (pal.darkCores && rng() > 0.45) {
        const dR = rW * (0.06 + rng() * 0.14)
        const da = 0.22 + rng() * 0.35
        const dGrad = ctx.createRadialGradient(cx, cy, 0, cx, cy, dR)
        dGrad.addColorStop(0, `rgba(0,0,0,${da})`)
        dGrad.addColorStop(1, 'rgba(0,0,0,0)')
        ctx.fillStyle = dGrad
        ctx.fillRect(cx - dR, cy - dR, dR * 2, dR * 2)
      }
    }

    // Bright stellar jet/streak (fire palette only)
    if (pal.streak) {
      const sx = TW * (0.44 + rng() * 0.14)
      const sy = TH * 0.03
      const ex = TW * (0.5 + (rng() - 0.5) * 0.14)
      const ey = TH * (0.38 + rng() * 0.22)
      const steps = 45
      for (let i = 0; i < steps; i++) {
        const tp = i / (steps - 1)
        const px = sx + (ex - sx) * tp
        const py = sy + (ey - sy) * tp
        const sr2 = TW * (0.008 + (1 - tp) * 0.022 + rng() * 0.006)
        const a = 0.08 + (1 - tp) * 0.28
        const grad = ctx.createRadialGradient(px, py, 0, px, py, sr2)
        grad.addColorStop(0, `rgba(255,240,200,${a})`)
        grad.addColorStop(0.35, `rgba(255,180,50,${a * 0.5})`)
        grad.addColorStop(1, 'rgba(0,0,0,0)')
        ctx.fillStyle = grad
        ctx.fillRect(px - sr2, py - sr2, sr2 * 2, sr2 * 2)
      }
    }

    // Stars: ~3000 total across three tiers
    const numStars = 2800 + Math.floor(rng() * 800)
    for (let i = 0; i < numStars; i++) {
      const x = rng() * TW, y = rng() * TH
      const rv = rng()
      let radius, brightness

      if (rv < 0.74) {          // tiny dim
        radius = 0.3 + rng() * 0.45
        brightness = 0.15 + rng() * 0.4
      } else if (rv < 0.93) {   // medium
        radius = 0.5 + rng() * 0.9
        brightness = 0.45 + rng() * 0.45
      } else {                  // bright with diffuse glow
        radius = 1.0 + rng() * 1.8
        brightness = 0.8 + rng() * 0.2
        const glowR = radius * (3 + rng() * 4)
        const gb = Math.round(brightness * 200)
        const glGrad = ctx.createRadialGradient(x, y, 0, x, y, glowR)
        glGrad.addColorStop(0, `rgba(${gb},${gb},${gb},0.45)`)
        glGrad.addColorStop(1, 'rgba(0,0,0,0)')
        ctx.fillStyle = glGrad
        ctx.fillRect(x - glowR, y - glowR, glowR * 2, glowR * 2)
      }

      const b = Math.round(brightness * 255)
      let sr, sg, sbl
      const cv = rng()
      if (pal.warmStars && cv < 0.35) {
        sr = b; sg = Math.round(b * 0.88); sbl = Math.round(b * 0.65)  // warm yellow
      } else if (cv < 0.68) {
        sr = b; sg = b; sbl = b                                          // white
      } else {
        sr = Math.round(b * 0.82); sg = Math.round(b * 0.9); sbl = b   // cool blue-white
      }
      ctx.fillStyle = `rgb(${sr},${sg},${sbl})`
      ctx.beginPath()
      ctx.arc(x, y, radius, 0, Math.PI * 2)
      ctx.fill()
    }

    return canvas
  }

  _renderBackground(ctx, W, H, dt) {
    if (!this._bgCanvas) return
    // 4096×4096 texture, 1920×1080 viewport (1:1 pixels at HD, scaled to canvas).
    // Pan: triangle-wave oscillation via wall clock — always moving, regardless of audio state.
    const REF_W = 1920, REF_H = 1080
    const MAX_X = 4096 - REF_W   // 2176
    const MAX_Y = 4096 - REF_H   // 3016
    const PERIOD_X = 541          // seconds for one back-and-forth cycle (prime → no repeating pattern)
    const PERIOD_Y = 379
    const t = performance.now() / 1000
    const tx = (t / PERIOD_X) % 2
    const ty = (t / PERIOD_Y) % 2
    const vpX = (tx < 1 ? tx : 2 - tx) * MAX_X   // smooth triangle wave, no jump
    const vpY = (ty < 1 ? ty : 2 - ty) * MAX_Y

    // Always fully visible — fade effects are applied on top via separate overlays
    ctx.drawImage(this._bgCanvas, vpX, vpY, REF_W, REF_H, 0, 0, W, H)
  }

  _renderFrame() {
    const dt = this._getDisplayTime()        // display time: 0=splash start
    const audioTime = dt - this._preroll     // audio-relative time, can be negative

    const W = this.canvas.width
    const H = this.canvas.height
    const ctx = this.ctx

    ctx.fillStyle = '#000'
    ctx.fillRect(0, 0, W, H)
    this._renderBackground(ctx, W, H, dt)

    // Background-only mode: data/audio not ready yet — logo shown unconditionally, independent of
    // any dt/audioTime-based fade (those only make sense once a song is actually loaded).
    if (!this._ready) {
      this._renderLogo(ctx, W, H, 1)
      this._renderLoadingIndicator(ctx, W, H)
      this._updateControls(dt)
      return
    }

    // Стартовый переход логотип→сплэш (сразу после готовности) имеет приоритет над конечным
    // idle-переходом (после _onEnded); в любой момент активен максимум один из них.
    const seq = this._getStartSequenceAlphas() || this._getEndSequenceAlphas()
    const logoAlpha = seq ? seq.logoAlpha : this._getLogoAlpha(audioTime)

    const scale = H / 1080

    if (this._cachedCanvasW !== W) {
      this._cachedCanvasW = W
      const layout = this._computeVoiceLayout(ctx, W)
      this._cachedFontSize = layout.fontSize
      this._cachedVoiceXStart = layout.voiceXStart
    }
    const fontSize = this._cachedFontSize ?? Math.round(54 * scale)
    const voiceXStart = this._cachedVoiceXStart ?? [Math.round(W * 0.05)]
    const xStart = voiceXStart[0]
    const lineHeight = Math.round(fontSize * 1.1)

    // Song end fade-out: karaoke content fades out over last 1s; background stays visible.
    const FADE_OUT = 1.0
    const fadeOutAlpha = (this.duration > 0 && audioTime > this.duration - FADE_OUT)
      ? Math.min(1, (audioTime - (this.duration - FADE_OUT)) / FADE_OUT)
      : 0

    const FADE = 1.0
    if (dt < this._splashDur) {
      // Splash handles its own fade-out internally; background shows through. alphaOverride drives
      // the idle fade-in phase of the post-track transition (see _getEndSequenceAlphas).
      this._renderSplash(ctx, W, H, scale, dt, this._splashDur, seq ? seq.splashAlpha : 1)
    } else {
      // Karaoke: fade in 1s after splash, fade out last 1s of audio — background unaffected.
      const karaokeAlpha = dt < this._splashDur + FADE ? (dt - this._splashDur) / FADE : 1.0
      const finalAlpha = karaokeAlpha * (1 - fadeOutAlpha)
      if (finalAlpha < 1) ctx.globalAlpha = finalAlpha
      this._renderKaraoke(ctx, W, H, scale, fontSize, lineHeight, xStart, audioTime, voiceXStart)
      if (finalAlpha < 1) ctx.globalAlpha = 1.0
    }

    this._renderLogo(ctx, W, H, logoAlpha)
    this._updateControls(dt)
  }

  // Site logo overlay while a song is loaded: hidden the whole way through, except a fade-in over
  // the last 0.5s of the song — carried into the post-track idle transition, see
  // _getEndSequenceAlphas. (While no song is loaded at all, _renderFrame draws the logo directly
  // at alpha=1, bypassing this function entirely.)
  _getLogoAlpha(audioTime) {
    const FADE = 0.5
    if (this.duration > 0 && audioTime > this.duration - FADE) {
      return Math.min(1, (audioTime - (this.duration - FADE)) / FADE)
    }
    return 0
  }

  // Post-track idle transition: right after _onEnded() the logo is already fully visible (faded
  // in over the preceding 0.5s per _getLogoAlpha). Runs on wall-clock time — dt is reset to 0 by
  // _onEnded(), so it can't drive this — over: a hold at full opacity, then two 0.5s phases (logo
  // fades out, then the idle splash screen fades in). A clean cut rather than the two layers
  // crossfading into each other.
  // Стартовый переход сразу после готовности песни (_ready): 0.5с fade-out логотипа, затем 0.5с
  // fade-in сплэша — чистый cut между слоями (как _getEndSequenceAlphas, но без hold). Работает по
  // wall-clock, т.к. воспроизведение ещё не идёт (dt=0, сплэш статичен).
  _getStartSequenceAlphas() {
    if (this._startFadeStartedAt === null) return null
    const FADE = 0.5
    const t = (Date.now() - this._startFadeStartedAt) / 1000
    if (t < FADE) return { logoAlpha: 1 - t / FADE, splashAlpha: 0 }
    if (t < FADE * 2) return { logoAlpha: 0, splashAlpha: (t - FADE) / FADE }
    this._startFadeStartedAt = null
    return null
  }

  _getEndSequenceAlphas() {
    if (this._endFadeStartedAt === null) return null
    const HOLD = 3.0
    const FADE = 0.5
    const elapsed = (Date.now() - this._endFadeStartedAt) / 1000
    if (elapsed < HOLD) return { logoAlpha: 1, splashAlpha: 0 }
    const t = elapsed - HOLD
    if (t < FADE) return { logoAlpha: 1 - t / FADE, splashAlpha: 0 }
    if (t < FADE * 2) return { logoAlpha: 0, splashAlpha: (t - FADE) / FADE }
    this._endFadeStartedAt = null
    return null
  }

  _renderLogo(ctx, W, H, alpha) {
    if (!this._logoImg || alpha <= 0) return
    const boxW = W * 0.4
    const boxH = H * 0.4
    ctx.globalAlpha = alpha
    this._drawImageFit(ctx, this._logoImg, (W - boxW) / 2, (H - boxH) / 2, boxW, boxH)
    ctx.globalAlpha = 1.0
  }

  // Индикатор загрузки под логотипом, рисуется только в фазе !_ready. Как только известен хоть
  // какой-то прогресс скачивания (_loadProgress != null) — показываем прогресс-полосу и держим её
  // до готовности: при p<1 подпись «N%», при p>=1 (скачивание закончилось, идёт decodeAudioData,
  // прогресса по нему нет) полоса стоит заполненной с подписью «Обработка...». Спиннер — только для
  // самой начальной фазы (p===null: playerdata/шрифты/картинки, байт ещё нет). Это убирает резкий
  // визуальный скачок «полоса → спиннер», когда скачивание с локального хранилища почти мгновенно.
  _renderLoadingIndicator(ctx, W, H) {
    const p = this._loadProgress
    const cx = W / 2
    const y = Math.round(H * 0.72)   // ниже бокса логотипа (40%×40%, центрирован)
    const scale = H / 1080
    const ACCENT = 'rgb(255,136,0)'      // #f80 — акцент плеера
    const TRACK = 'rgba(255,255,255,0.15)'

    if (p !== null) {
      // Есть прогресс скачивания: полоса + подпись «N%» / «Обработка...»
      const barW = Math.min(W * 0.4, Math.round(480 * scale))
      const barH = Math.max(4, Math.round(8 * scale))
      const barX = cx - barW / 2
      const r = barH / 2
      ctx.fillStyle = TRACK
      ctx.beginPath(); ctx.roundRect(barX, y, barW, barH, r); ctx.fill()
      if (p < 1) {
        // Скачивание: заполнение = доля скачанного
        ctx.fillStyle = ACCENT
        ctx.beginPath(); ctx.roundRect(barX, y, Math.max(barH, barW * p), barH, r); ctx.fill()
      } else {
        // Декодирование (прогресса нет): полоса заполнена целиком, мягкая пульсация яркости
        const pulse = 0.4 + 0.6 * (0.5 + 0.5 * Math.sin(performance.now() / 1000 * Math.PI * 1.6))
        ctx.globalAlpha = pulse
        ctx.fillStyle = ACCENT
        ctx.beginPath(); ctx.roundRect(barX, y, barW, barH, r); ctx.fill()
        ctx.globalAlpha = 1
      }

      const fs = Math.max(14, Math.round(28 * scale))
      ctx.font = `600 ${fs}px sans-serif`
      ctx.fillStyle = 'rgba(255,255,255,0.85)'
      ctx.textAlign = 'center'
      ctx.textBaseline = 'top'
      ctx.fillText(p < 1 ? `${Math.round(p * 100)}%` : 'Обработка...', cx, y + barH + Math.round(14 * scale))
      ctx.textAlign = 'left'
      ctx.textBaseline = 'alphabetic'
    } else {
      // Начальная фаза, байт ещё нет: спиннер + «Загрузка...»
      const radius = Math.max(14, Math.round(26 * scale))
      const lw = Math.max(3, Math.round(4 * scale))
      const t = performance.now() / 1000
      const start = (t * Math.PI * 1.6) % (Math.PI * 2)
      ctx.save()
      ctx.lineWidth = lw
      ctx.strokeStyle = TRACK
      ctx.beginPath(); ctx.arc(cx, y, radius, 0, Math.PI * 2); ctx.stroke()
      ctx.strokeStyle = ACCENT
      ctx.lineCap = 'round'
      ctx.beginPath(); ctx.arc(cx, y, radius, start, start + Math.PI * 0.5); ctx.stroke()
      ctx.restore()

      const fs = Math.max(12, Math.round(22 * scale))
      ctx.font = `500 ${fs}px sans-serif`
      ctx.fillStyle = 'rgba(255,255,255,0.7)'
      ctx.textAlign = 'center'
      ctx.textBaseline = 'top'
      ctx.fillText('Загрузка...', cx, y + radius + Math.round(16 * scale))
      ctx.textAlign = 'left'
      ctx.textBaseline = 'alphabetic'
    }
  }

  // ─── Splash ───────────────────────────────────────────────────────────────

  _loadImage(url) {
    return new Promise((resolve, reject) => {
      const img = new Image()
      img.onload = () => resolve(img)
      img.onerror = reject
      img.src = url
    })
  }

  // Word-wrap text into lines fitting maxW at the current ctx.font.
  _wrapTextToLines(ctx, text, maxW) {
    const words = text.split(' ')
    const lines = []
    let current = ''
    for (const word of words) {
      const test = current ? current + ' ' + word : word
      if (current && ctx.measureText(test).width > maxW) {
        lines.push(current)
        current = word
      } else {
        current = test
      }
    }
    if (current) lines.push(current)
    return lines
  }

  // Draw auto-sized, word-wrapped text centred in the given area.
  // Mirrors Kotlin's areaText.multiLines() + auto font sizing.
  _drawAutoFitText(ctx, text, areaX, areaY, areaW, areaH, fontWeight, fontFamily) {
    if (!text) return
    let bestFontSize = 8
    let bestLines = [text]
    for (let nLines = 1; nLines <= 4; nLines++) {
      let lo = 8, hi = Math.floor(areaH / nLines / 1.2)
      while (lo <= hi) {
        const mid = (lo + hi) >> 1
        ctx.font = `${fontWeight} ${mid}px ${fontFamily}`
        const wrapped = this._wrapTextToLines(ctx, text, areaW)
        const maxLineW = wrapped.reduce((m, l) => Math.max(m, ctx.measureText(l).width), 0)
        if (wrapped.length <= nLines && mid * 1.2 * wrapped.length <= areaH && maxLineW <= areaW) {
          if (mid > bestFontSize) { bestFontSize = mid; bestLines = wrapped }
          lo = mid + 1
        } else {
          hi = mid - 1
        }
      }
    }
    ctx.font = `${fontWeight} ${bestFontSize}px ${fontFamily}`
    const lineH = bestFontSize * 1.2
    const totalH = lineH * bestLines.length
    const startY = areaY + (areaH - totalH) / 2
    const cx = areaX + areaW / 2
    ctx.textAlign = 'center'
    ctx.textBaseline = 'top'
    bestLines.forEach((line, i) => ctx.fillText(line, cx, startY + i * lineH))
    ctx.textAlign = 'left'
    ctx.textBaseline = 'alphabetic'
  }

  // Draw image maintaining aspect ratio (object-fit: contain), centred in the box.
  _drawImageFit(ctx, img, x, y, boxW, boxH) {
    const s = Math.min(boxW / img.naturalWidth, boxH / img.naturalHeight)
    const dw = img.naturalWidth * s
    const dh = img.naturalHeight * s
    ctx.drawImage(img, x + (boxW - dw) / 2, y + (boxH - dh) / 2, dw, dh)
  }

  // Mirrors Kotlin MkoSplashStart.template() (simplified — no version/comment labels):
  //   border = frameH*0.05 = 54, padding = 50 (frame-space 1920×1080)
  //   Album  400×400 at frame (233, 54)
  //   Author 1000×400 at frame (687, 54)
  //   Song name (yellow, auto-size, multi-line) in area between images and chord desc
  //   Chord desc "Key: «…», bpm: N" (salmon, size 40) at bottom
  //   Fade: static at full alpha, 1→0 over last 1s (fades into karaoke)
  //   Unified scale = H/1080 for both axes → aspect ratio preserved; content centred horizontally.
  _renderSplash(ctx, W, H, _scale, ct, splashDur, alphaOverride = 1) {
    const FADE = 1.0
    const fadeOut = ct > splashDur - FADE ? Math.max(0, (splashDur - ct) / FADE) : 1.0
    ctx.globalAlpha = fadeOut * alphaOverride

    // "Contain" scaling: fit 1920×1080 frame into canvas preserving aspect ratio, centred.
    const sc = Math.min(W / 1920, H / 1080)
    const ox = (W - 1920 * sc) / 2   // horizontal offset
    const oy = (H - 1080 * sc) / 2   // vertical offset

    const BORDER = 54   // 1080 * 0.05
    const PAD = 50

    // Album image 400×400 at frame (233, 54)
    if (this._albumImg) {
      this._drawImageFit(ctx, this._albumImg,
        ox + 233 * sc, oy + BORDER * sc, 400 * sc, 400 * sc)
    }
    // Author image 1000×400 at frame (687, 54)
    if (this._artistImg) {
      this._drawImageFit(ctx, this._artistImg,
        ox + 687 * sc, oy + BORDER * sc, 1000 * sc, 400 * sc)
    }

    this._setShadow(ctx, sc)

    // Chord description at frame y ≈ 1005 (1080 - border/2 - fontSize*1.2)
    const chordSz = Math.round(40 * sc)
    const chordFrameY = 1080 - BORDER * 0.5 - 40 * 1.2  // ≈ 1005
    const chordY = oy + chordFrameY * sc
    const keyStr = this.data.key ? `Key: «${this.data.key}», bpm: ${this.data.bpm}` : `bpm: ${this.data.bpm}`
    ctx.font = `400 ${chordSz}px FiraSansExtraCondensed, sans-serif`
    ctx.fillStyle = 'rgb(255,127,127)'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'top'
    ctx.fillText(keyStr, ox + 960 * sc, chordY)

    // Song name: yellow, auto-size, area between images and chord desc
    const textAreaX = ox + PAD * sc
    const textAreaY = oy + (BORDER + 400 + PAD * 0.5) * sc
    const textAreaW = (1920 - 2 * PAD) * sc
    const textAreaH = chordY - textAreaY - PAD * sc * 0.5
    ctx.fillStyle = 'rgb(255,255,127)'
    this._drawAutoFitText(ctx, this.data.songName || '', textAreaX, textAreaY, textAreaW, Math.max(10, textAreaH), '900', 'Roboto, sans-serif')

    this._clearShadow(ctx)
    ctx.globalAlpha = 1.0
    ctx.textAlign = 'left'
    ctx.textBaseline = 'alphabetic'
  }

  // ─── Karaoke frame ────────────────────────────────────────────────────────

  _renderKaraoke(ctx, W, H, scale, fontSize, lineHeight, xStart, ct, voiceXStart = null) {
    // Text center = H/2 + 7px: Kotlin positions active line at H/2 + |horizonOffsetPx| (horizonOffsetPx=-7)
    const centerY = H / 2 + Math.round(7 * scale)

    // Horizon lines mirror Kotlin MkoHorizon (anchored to H/2, not text center):
    //   upper = H/2 - rectH/2 + 3  =  centerY - rectH/2 - 4*scale
    //   lower = H/2 + rectH/2 + 7  =  centerY + rectH/2
    const rectH = fontSize * 1.1
    const horizonColor = this._getHorizonColor(ct)
    const horizonLineH = Math.max(1, Math.round(3 * scale))
    ctx.fillStyle = horizonColor
    const topH = Math.round(centerY - rectH / 2 - 4 * scale)
    const botH = Math.round(centerY + rectH / 2)
    ctx.fillRect(0, topH, W, horizonLineH)
    ctx.fillRect(0, botH, W, horizonLineH)

    // Paint lower horizon with text line positions (Kotlin's paintHorizon):
    // lower horizon gets white segments (horizonColors[0]=white) for each voice-0 text line,
    // x proportional to lineStartMs, width proportional to line duration
    if (this.duration > 0) {
      ctx.fillStyle = 'rgb(255,255,255)'
      for (const line of this.lines) {
        if (line.isEmpty || line.isComment || line.voiceIdx !== 0) continue
        const x = Math.round(line.startTime / this.duration * W)
        const w = Math.round(line.endTime / this.duration * W) - x
        if (w > 0) ctx.fillRect(x, botH, w, horizonLineH)
      }
    }

    // Progress overlay (Kotlin MkoProgress): semi-transparent dark rect covers the
    // right (unplayed) portion of both horizon lines, slides right as song plays.
    // brushcolor = "0,0,0,170" → rgba(0,0,0, 170/255 ≈ 0.667)
    if (this.duration > 0) {
      const progressX = Math.round(ct / this.duration * W)
      const overlayW = W - progressX
      if (overlayW > 0) {
        ctx.fillStyle = 'rgba(0,0,0,0.667)'
        ctx.fillRect(progressX, topH, overlayW, horizonLineH)
        ctx.fillRect(progressX, botH, overlayW, horizonLineH)
      }
    }

    // Flash: both horizon lines briefly turn red at each line start and counter beat
    // Kotlin: flashColor=rgb(255,0,0), opacity 0→1 instant then linear fade to 0
    const flashOpacity = this._getFlashOpacity(ct)
    if (flashOpacity > 0) {
      ctx.globalAlpha = flashOpacity
      ctx.fillStyle = 'rgb(255,0,0)'
      ctx.fillRect(0, topH, W, horizonLineH)
      ctx.fillRect(0, botH, W, horizonLineH)
      ctx.globalAlpha = 1.0
    }

    const nVoices = this.voiceLines?.length || 0
    const halfVisible = Math.ceil(H / lineHeight) + 2

    if (nVoices > 1 && voiceXStart && voiceXStart.length > 1) {
      // Multi-voice: each voice renders in its own column with its own scroll position
      for (let v = 0; v < nVoices; v++) {
        const vLines = this.voiceLines[v]
        if (!vLines || !vLines.length) continue
        const scrollPosV = this._getScrollPositionForVoice(ct, v)
        const vXStart = voiceXStart[v] ?? xStart

        const iMin = Math.max(0, Math.floor(scrollPosV) - halfVisible)
        const iMax = Math.min(vLines.length - 1, Math.ceil(scrollPosV) + halfVisible)

        for (let i = iMin; i <= iMax; i++) {
          const line = vLines[i]
          if (line.isEmpty) continue
          const y = Math.round(centerY + (i - scrollPosV) * lineHeight)
          if (y < -lineHeight || y > H + lineHeight) continue

          if (line.isComment) {
            const commentFontSize = Math.round(fontSize * 0.75)
            ctx.font = `italic 900 ${commentFontSize}px Roboto, sans-serif`
            ctx.textBaseline = 'middle'
            this._setShadow(ctx, scale)
            ctx.fillStyle = '#d2691e'
            ctx.fillText(line.commentText, vXStart, y)
            this._clearShadow(ctx)
            ctx.textBaseline = 'alphabetic'
            continue
          }

          if (!line.syllables.length) continue
          const isActive = ct >= line.startTime && ct < line.endTime
          const isSung = !isActive && ct >= line.endTime
          this._renderLine(ctx, line, y, fontSize, vXStart, W, ct, isActive, isSung)
        }
      }
    } else {
      // Single voice: original rendering path
      const scrollPos = this._getScrollPosition(ct)
      const iMin = Math.max(0, Math.floor(scrollPos) - halfVisible)
      const iMax = Math.min(this.lines.length - 1, Math.ceil(scrollPos) + halfVisible)

      for (let i = iMin; i <= iMax; i++) {
        const line = this.lines[i]
        if (line.isEmpty) continue
        const y = Math.round(centerY + (i - scrollPos) * lineHeight)
        if (y < -lineHeight || y > H + lineHeight) continue

        if (line.isComment) {
          const commentFontSize = Math.round(fontSize * 0.75)
          ctx.font = `italic 900 ${commentFontSize}px Roboto, sans-serif`
          ctx.textBaseline = 'middle'
          this._setShadow(ctx, scale)
          ctx.fillStyle = '#d2691e'
          ctx.fillText(line.commentText, xStart, y)
          this._clearShadow(ctx)
          ctx.textBaseline = 'alphabetic'
          continue
        }

        if (!line.syllables.length) continue
        const isActive = ct >= line.startTime && ct < line.endTime
        const isSung = !isActive && ct >= line.endTime
        this._renderLine(ctx, line, y, fontSize, xStart, W, ct, isActive, isSung)
      }
    }

    this._renderCounter(ctx, W, H, scale, fontSize, centerY, xStart, ct)

    // Fader overlay: black gradients at top and bottom (mirrors Kotlin MkoFaderText).
    // Drawn after text so it covers text bleed at screen edges.
    this._renderFader(ctx, W, H, fontSize)

    const hSlide = this._getHeaderSlide(ct)
    this._renderHeader(ctx, W, H, scale, hSlide)
  }

  // Mirrors Kotlin MkoFaderText: gradient overlays at top (black→transparent) and
  // bottom (transparent→black), each h = 2 * symbolHeight ≈ 2 * fontSize * 1.1.
  // Top fader also provides the opaque backing under the HEADER.
  _renderFader(ctx, W, H, fontSize) {
    const faderH = Math.round(fontSize * 1.1 * 2)

    const topGrad = ctx.createLinearGradient(0, 0, 0, faderH)
    topGrad.addColorStop(0, 'rgba(0,0,0,1)')
    topGrad.addColorStop(1, 'rgba(0,0,0,0)')
    ctx.fillStyle = topGrad
    ctx.fillRect(0, 0, W, faderH)

    const botGrad = ctx.createLinearGradient(0, H - faderH, 0, H)
    botGrad.addColorStop(0, 'rgba(0,0,0,0)')
    botGrad.addColorStop(1, 'rgba(0,0,0,1)')
    ctx.fillStyle = botGrad
    ctx.fillRect(0, H - faderH, W, faderH)
  }

  _getHorizonColor(ct) {
    const active = this.lines.find(l => !l.isEmpty && !l.isComment && ct >= l.startTime && ct < l.endTime)
    const COLORS = ['rgb(0,200,0)', 'rgb(200,0,0)', 'rgb(0,100,200)']
    return COLORS[((active ? active.voiceIdx : 0)) % COLORS.length]
  }

  // Flash times: all text line starts + counter lines get n*halfNote offsets (n=1..4)
  // Mirrors Kotlin timesForFlashSet in Settings.kt
  _buildFlashTimes() {
    const bpm = this.data?.bpm || 120
    const halfNote = (60 / bpm) * 2
    const set = new Set()
    for (const line of this.lines) {
      if (line.isEmpty || line.isComment) continue
      set.add(line.startTime)
      if (line.hasCounter) {
        for (let n = 1; n <= 4; n++) {
          const t = line.startTime - n * halfNote
          if (t > -this._silentOffset) set.add(t)
        }
      }
    }
    this.flashTimes = [...set].sort((a, b) => a - b)
  }

  _getFlashOpacity(ct) {
    const times = this.flashTimes
    if (!times || !times.length) return 0
    // Binary search: find latest event time ≤ ct
    let lo = 0, hi = times.length - 1, idx = -1
    while (lo <= hi) {
      const mid = (lo + hi) >> 1
      if (times[mid] <= ct) { idx = mid; lo = mid + 1 }
      else hi = mid - 1
    }
    if (idx < 0) return 0
    const T = times[idx]
    const nextT = idx + 1 < times.length ? times[idx + 1] : (this.duration || T + 1)
    const D = Math.min(1.0, nextT - T)  // fade duration: min(1s, gap to next event)
    const elapsed = ct - T
    if (elapsed < 0 || elapsed >= D) return 0
    return Math.max(0, 1 - elapsed / D)
  }

  // ─── Line rendering ───────────────────────────────────────────────────────

  // Group styles matching Kotlin's spanStyleGroup0-3 and SubsEdit.vue SPAN_STYLE_GROUP0-3:
  //   0 = white  normal   1 = yellow italic   2 = cyan  normal   3 = green italic
  static _groupStyle(groupId) {
    const T = [
      { color: '#ffffff',          italic: false },
      { color: 'rgb(255,255,155)', italic: true  },
      { color: '#00bfff',          italic: false },
      { color: '#00ff00',          italic: true  },
    ]
    return T[groupId % T.length] ?? T[0]
  }

  _renderLine(ctx, line, centerY, fontSize, xStart, W, ct, isActive, isSung) {
    const scale = W / 1920
    const g = KaraokePlayer._groupStyle(line.groupId ?? line.voiceIdx)
    ctx.font = `${g.italic ? 'italic ' : ''}900 ${fontSize}px Roboto, sans-serif`
    ctx.textBaseline = 'middle'

    let x = xStart
    const syls = line.syllables.map(s => {
      const w = ctx.measureText(s.text).width
      const r = { text: s.text, startTime: s.startTime, endTime: s.endTime, x, w }
      x += w
      return r
    })
    const totalW = x - xStart

    const rectH = fontSize * 1.1
    const rectY = Math.round(centerY - rectH / 2)
    const textColor = g.color
    const FILL_COLOR = 'rgb(255,128,0)'
    const SHORT_MS = 0.75  // mirror Kotlin shortSubtitleMs = 750ms

    // Compute fill width and animated height (mirrors Kotlin startTransformProperty/endTransformProperty).
    // Short syllable (≤750ms): fill height stays at 5/7 throughout.
    // Long syllable (>750ms): fill height grows from 5/7 to full over the syllable.
    // When syllables are consecutive (no gap), start height of current = end height of previous.
    const _fillGeometry = (atTime) => {
      let currIdx = -1
      for (let si = 0; si < syls.length; si++) {
        if (atTime >= syls[si].startTime) currIdx = si
        else break
      }

      // deltaY: top/bottom inset of fill rect (0 = full height, rectH/7 = 5/7 height)
      let deltaY
      if (currIdx < 0) {
        deltaY = rectH / 7
      } else {
        const cur = syls[currIdx]
        const isShortCur = (cur.endTime - cur.startTime) <= SHORT_MS
        const endDelta = isShortCur ? rectH / 7 : 0

        let startDelta
        if (currIdx === 0) {
          startDelta = rectH / 7
        } else {
          const prev = syls[currIdx - 1]
          const hasGap = prev.endTime < cur.startTime
          if (hasGap) {
            startDelta = rectH / 7
          } else {
            startDelta = (prev.endTime - prev.startTime) <= SHORT_MS ? rectH / 7 : 0
          }
        }

        const progress = (cur.endTime > cur.startTime && atTime < cur.endTime)
          ? (atTime - cur.startTime) / (cur.endTime - cur.startTime)
          : (atTime >= cur.endTime ? 1.0 : 0.0)
        deltaY = startDelta + (endDelta - startDelta) * progress
      }

      // Fill width
      let fillW = 0
      for (const s of syls) {
        if (atTime < s.startTime) break
        fillW = (s.endTime > s.startTime && atTime < s.endTime)
          ? (s.x - xStart) + s.w * (atTime - s.startTime) / (s.endTime - s.startTime)
          : (s.x - xStart) + s.w
      }

      return { fillW, fillY: rectY + deltaY, fillH: rectH - 2 * deltaY }
    }

    if (isActive) {
      const { fillW, fillY, fillH } = _fillGeometry(ct)

      ctx.fillStyle = FILL_COLOR
      ctx.fillRect(xStart, fillY, fillW, fillH)
      this._setShadow(ctx, scale)
      ctx.fillStyle = textColor
      for (const s of syls) ctx.fillText(s.text, s.x, centerY)
      this._clearShadow(ctx)

    } else if (isSung) {
      const { fillY, fillH } = _fillGeometry(line.endTime)
      const fillAlpha = Math.max(0, 1 - (ct - line.endTime) / 1.0)
      if (fillAlpha > 0) {
        ctx.globalAlpha = fillAlpha
        ctx.fillStyle = FILL_COLOR
        ctx.fillRect(xStart, fillY, totalW, fillH)
        ctx.globalAlpha = 1.0
      }
      this._setShadow(ctx, scale)
      ctx.fillStyle = textColor
      for (const s of syls) ctx.fillText(s.text, s.x, centerY)
      this._clearShadow(ctx)

    } else {
      this._setShadow(ctx, scale)
      ctx.fillStyle = textColor
      for (const s of syls) ctx.fillText(s.text, s.x, centerY)
      this._clearShadow(ctx)
    }

    ctx.textBaseline = 'alphabetic'
  }

  _setShadow(ctx, scale) {
    ctx.shadowColor = 'rgba(0,0,0,1)'
    ctx.shadowOffsetX = 3 * scale
    ctx.shadowOffsetY = 3 * scale
    ctx.shadowBlur = 3 * scale
  }

  _clearShadow(ctx) {
    ctx.shadowColor = 'transparent'
    ctx.shadowOffsetX = 0
    ctx.shadowOffsetY = 0
    ctx.shadowBlur = 0
  }

  // Mirror Kotlin's getFontSize(): font size + per-voice x start positions.
  // For n voices: combined width of all voices' max lines + (n-1)*margin ≤ W - 2*margin.
  // Returns { fontSize, voiceXStart: [x0, x1, ...] }
  _computeVoiceLayout(ctx, W) {
    const margin = Math.round(W * 0.05)
    const nVoices = Math.max(1, this.voiceLines?.length || 1)
    const defaultFontSize = Math.round(54 * W / 1920)

    if (!this.lines || this.lines.length === 0) {
      return { fontSize: defaultFontSize, voiceXStart: [margin] }
    }

    const REF_SIZE = 40
    const maxWidthPerVoice = new Array(nVoices).fill(0)

    for (const line of this.lines) {
      if (line.isEmpty || line.isComment || !line.syllables.length) continue
      const v = Math.min(line.voiceIdx, nVoices - 1)
      const g = KaraokePlayer._groupStyle(line.groupId ?? line.voiceIdx)
      ctx.font = `${g.italic ? 'italic ' : ''}900 ${REF_SIZE}px Roboto, sans-serif`
      let lineW = 0
      for (const s of line.syllables) lineW += ctx.measureText(s.text).width
      if (lineW > maxWidthPerVoice[v]) maxWidthPerVoice[v] = lineW
    }

    // Available width: left margin + (n-1) separator margins + right margin = (n+1)*margin
    const maxTotalW = W - margin * (nVoices + 1)
    const totalRefW = maxWidthPerVoice.reduce((a, b) => a + b, 0)

    let fontSize
    if (totalRefW <= 0 || maxTotalW <= 0) {
      fontSize = defaultFontSize
    } else {
      fontSize = Math.max(10, Math.min(Math.floor(REF_SIZE * maxTotalW / totalRefW), 200))
    }

    const scale = fontSize / REF_SIZE
    const voiceXStart = []
    let xCursor = margin
    for (let i = 0; i < nVoices; i++) {
      voiceXStart.push(xCursor)
      xCursor += Math.round(maxWidthPerVoice[i] * scale) + margin
    }

    return { fontSize, voiceXStart }
  }

  // ─── Counter ──────────────────────────────────────────────────────────────

  // Mirrors Kotlin MkoCounter + Settings.kt counter transform properties:
  // - font size = same fontSize as text lines (Kotlin: mltProp.getFontSize())
  // - canvas starts at y=0 (counter center = centerY), moves to y=-symbolHeightPx over halfNote
  // - opacity linearly 1→0 over the full halfNote duration
  // - x centered within left margin [0, xStart] (Kotlin: mkoCounterPositionXPx)
  // countersColors: 0=green, 1=yellow, 2=yellow, 3=red, 4=red
  _renderCounter(ctx, W, H, scale, fontSize, centerY, xStart, ct) {
    const bpm = this.data?.bpm
    if (!bpm || bpm === 0) return
    const halfNote = (60 / bpm) * 2
    const rectH = fontSize * 1.1
    const COLORS = ['rgb(0,255,0)', 'rgb(255,255,0)', 'rgb(255,255,0)', 'rgb(255,0,0)', 'rgb(255,0,0)']
    const counterX = Math.round(xStart / 2)

    for (const line of this.lines) {
      if (!line.hasCounter) continue

      for (let n = 4; n >= 0; n--) {
        const tStart = line.startTime - n * halfNote
        const tEnd = tStart + halfNote

        if (ct >= tStart && ct < tEnd) {
          const progress = (ct - tStart) / halfNote
          const counterCenterY = Math.round(centerY - progress * rectH)

          ctx.save()
          ctx.globalAlpha = 1 - progress
          ctx.font = `900 ${fontSize}px Roboto, sans-serif`
          ctx.fillStyle = COLORS[n] || 'rgb(255,255,255)'
          ctx.textAlign = 'center'
          ctx.textBaseline = 'middle'
          this._setShadow(ctx, scale)
          ctx.fillText(String(n), counterX, counterCenterY)
          ctx.restore()
          break
        }
      }
    }
  }

  // ─── Header ───────────────────────────────────────────────────────────────

  // Returns normalized slide progress: 0 = fully visible (y=0), 1 = fully hidden (y=-headerH).
  // Mirrors Kotlin Settings.kt propHeaderLineTps: slides over 4 halfNotes at song start,
  // hides until last text line, then slides back over 4 halfNotes.
  _getHeaderSlide(ct) {
    const bpm = this.data?.bpm || 120
    const halfNote = (60 / bpm) * 2

    const firstCounterLine = this.lines.find(l => !l.isEmpty && l.hasCounter)
    if (!firstCounterLine) return 0  // no counter → always visible

    // Kotlin: startTimeFirstCounterMs = timesForFlashList[0] = firstLine.startTime - 4*halfNote
    const hideStart = firstCounterLine.startTime - 4 * halfNote
    const hideEnd = firstCounterLine.startTime

    const lastTextLine = [...this.lines].reverse().find(l => !l.isEmpty && !l.isComment)
    const showStart = lastTextLine ? lastTextLine.endTime : Infinity
    const showEnd = showStart + 4 * halfNote

    if (ct < hideStart) return 0
    if (ct < hideEnd) return (ct - hideStart) / (hideEnd - hideStart)
    if (ct < showStart) return 1
    if (ct < showEnd) return 1 - (ct - showStart) / (showEnd - showStart)
    return 0
  }

  _renderHeader(ctx, W, H, scale, slideProgress) {
    // slideProgress: 0=visible (y=0), 1=hidden (y=-headerH). Mirror Kotlin y: 0 → -592px.
    const headerH = Math.round(592 * scale)
    const slideY = -Math.round(headerH * slideProgress)
    if (slideY <= -headerH) return  // fully hidden

    ctx.save()
    ctx.translate(0, slideY)

    // Background: solid black (0..346px) + downward gradient fade (346..692px)
    const solidH = Math.round(346 * scale)
    ctx.fillStyle = '#000000'
    ctx.fillRect(0, 0, W, solidH)
    const grad = ctx.createLinearGradient(0, solidH, 0, solidH * 2)
    grad.addColorStop(0, 'rgba(0,0,0,1)')
    grad.addColorStop(1, 'rgba(0,0,0,0)')
    ctx.fillStyle = grad
    ctx.fillRect(0, solidH, W, solidH)

    // Logos: artist x=63.85% W, album x=89.27% W, y=36px, uniform scale W*0.00025
    const logoScale = W * 0.00025
    const logoY = Math.round(36 * scale)
    if (this._artistImg) {
      ctx.drawImage(this._artistImg,
        Math.round(W * 0.6385), logoY,
        Math.round(this._artistImg.naturalWidth * logoScale),
        Math.round(this._artistImg.naturalHeight * logoScale))
    }
    if (this._albumImg) {
      ctx.drawImage(this._albumImg,
        Math.round(W * 0.8927), logoY,
        Math.round(this._albumImg.naturalWidth * logoScale),
        Math.round(this._albumImg.naturalHeight * logoScale))
    }

    this._setShadow(ctx, scale)
    ctx.textBaseline = 'top'

    const xOff = Math.round(W * 0.05)
    const snSize = Math.round(80 * scale)
    const rowSize = Math.round(30 * scale)
    const maxSongNameW = Math.round(1200 * W / 1920) - xOff

    // Song name: auto-fit font size to maxSongNameW
    let snSz = snSize
    ctx.font = `900 ${snSz}px Roboto, sans-serif`
    const songName = this.data.songName || ''
    while (snSz > 10 && ctx.measureText(songName).width > maxSongNameW) {
      snSz--
      ctx.font = `900 ${snSz}px Roboto, sans-serif`
    }
    ctx.fillStyle = 'rgb(255,255,127)'
    ctx.fillText(songName, xOff, 0)

    // Metadata rows: label (cyan, right-aligned) + value (yellow)
    const rows = [
      { label: 'Исполнитель:', value: this.data.author || '' },
      { label: 'Альбом:', value: (this.data.album || '') + (this.data.year ? ` (${this.data.year})` : '') },
    ]
    if (this.data.key) rows.push({ label: 'Тональность:', value: this.data.key })
    if (this.data.bpm) rows.push({ label: 'Темп:', value: `${this.data.bpm} bpm` })

    ctx.font = `900 ${rowSize}px Roboto, sans-serif`
    const maxLabelW = Math.max(...rows.map(r => ctx.measureText(r.label).width))
    const valueX = xOff + maxLabelW

    const snLineH = snSz * 1.25
    const rowH = rowSize * 1.35
    let y = Math.round(snLineH)

    for (const row of rows) {
      const labelW = ctx.measureText(row.label).width
      ctx.fillStyle = 'rgb(85,255,255)'
      ctx.fillText(row.label, xOff + maxLabelW - labelW, y)
      ctx.fillStyle = 'rgb(255,255,127)'
      ctx.fillText(row.value, valueX, y)
      y += rowH
    }

    this._clearShadow(ctx)
    ctx.textBaseline = 'alphabetic'
    ctx.restore()
  }

  // ─── Controls ─────────────────────────────────────────────────────────────

  // dt = display time (0 = splash start); total = _preroll + duration
  _updateControls(dt) {
    const totalDuration = this._preroll + this.duration

    const pct = totalDuration > 0 ? (dt / totalDuration) * 100 : 0
    const bar = this.container.querySelector('#kp-progress-bar')
    if (bar) bar.style.width = `${Math.min(pct, 100)}%`

    const timeEl = this.container.querySelector('#kp-time')
    if (timeEl) timeEl.textContent = `${this._fmtTime(dt)} / ${this._fmtTime(totalDuration)}`

    const now = Date.now()
    if (now - this._lastWsSync > 200 && totalDuration > 0) {
      this._lastWsSync = now
      // WaveSurfer tracks the full timeline: seekTo(dt / totalDuration)
      const pctWs = dt / totalDuration
      try { this.wsAcc?.seekTo(pctWs) } catch {}
      try { this.wsVoc?.seekTo(pctWs) } catch {}
    }
  }

  _fmtTime(sec) {
    if (!sec || isNaN(sec) || sec < 0) return '0:00'
    const m = Math.floor(sec / 60)
    const s = Math.floor(sec % 60)
    return `${m}:${s.toString().padStart(2, '0')}`
  }

  async _loadNewFile(file) {
    if (this.animId) { cancelAnimationFrame(this.animId); this.animId = null }
    this._endedHandled = true
    this._stopSources()
    if (this.audioCtx) { await this.audioCtx.close(); this.audioCtx = null }
    if (this.wsAcc) { this.wsAcc.destroy(); this.wsAcc = null }
    if (this.wsVoc) { this.wsVoc.destroy(); this.wsVoc = null }
    window.removeEventListener('resize', this._resizeHandler)
    document.removeEventListener('fullscreenchange', this._fsHandler)
    document.removeEventListener('click', this._menuOutsideClickHandler)
    for (const url of this._smkaraokeObjectUrls) URL.revokeObjectURL(url)
    this._smkaraokeObjectUrls = []

    this._mode = 'blob'
    this._smkaraokeSource = file
    this.accBuffer = null; this.vocBuffer = null
    this.accSource = null; this.vocSource = null
    this.accGain = null; this.vocGain = null
    this.startedAt = 0; this.pausedAt = 0
    this.isPlaying = false; this.duration = 0
    this.data = null; this.lines = []; this.voiceLines = []
    this._ready = false
    this._loadProgress = null
    this._endedHandled = false
    this._cachedCanvasW = null; this._cachedVoiceXStart = null
    this._lastWsSync = 0
    this.flashTimes = []
    this._isPrerolling = false; this._dtPaused = 0
    this._silentOffset = 0; this._preroll = this._splashDur
    this._startFadeStartedAt = null
    this._endFadeStartedAt = null
    this._volumeAnchored = false
    clearTimeout(this._prerollTimeout); this._prerollTimeout = null

    await this.init()
  }

  destroy() {
    if (this.animId) cancelAnimationFrame(this.animId)
    clearTimeout(this._prerollTimeout)
    this._endedHandled = true
    this._stopSources()
    this.audioCtx?.close()
    this.wsAcc?.destroy()
    this.wsVoc?.destroy()
    window.removeEventListener('resize', this._resizeHandler)
    document.removeEventListener('fullscreenchange', this._fsHandler)
    document.removeEventListener('click', this._menuOutsideClickHandler)
    for (const url of this._smkaraokeObjectUrls) URL.revokeObjectURL(url)
    this._smkaraokeObjectUrls = []
    this._bgCanvas = null
    this._ready = false
  }
}
