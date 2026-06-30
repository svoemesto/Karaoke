export default class KaraokePlayer {
  constructor(container, songId, apiBase) {
    this.container = container
    this.songId = songId
    this.apiBase = apiBase

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

    this.data = null
    this.lines = []

    this.canvas = null
    this.ctx = null
    this.animId = null

    this.wsAcc = null
    this.wsVoc = null
    this._lastWsSync = 0
    this._endedHandled = false

    this._resizeHandler = () => this._resizeCanvas()
    this._fsHandler = () => this._resizeCanvas()
  }

  async init() {
    try {
      const resp = await fetch(`${this.apiBase}/song/${this.songId}/playerdata`)
      if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
      this.data = await resp.json()
      this._buildUI()
      await this._loadAudio()
      // Parse after audio load so this.duration is known
      this.lines = this._parseMarkers(this.data.markers || [])
      this._buildWaveforms()
      this._startRenderLoop()
    } catch (e) {
      console.error('KaraokePlayer init error:', e)
      if (this.container) {
        this.container.innerHTML = `<div style="color:#f66;padding:40px;font-size:18px;font-family:sans-serif">Ошибка загрузки: ${e.message}</div>`
      }
    }
  }

  // ─── Parsing ──────────────────────────────────────────────────────────────

  _parseMarkers(markersList) {
    const allTextLines = []

    markersList.forEach((voiceMarkers, voiceIdx) => {
      let currentSyllables = []
      let lineStart = null

      for (let i = 0; i < voiceMarkers.length; i++) {
        const m = voiceMarkers[i]
        const mt = m.markertype

        if (mt === 'syllables') {
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

          currentSyllables.push({
            text: (m.label || '').replace(/_/g, ' '),
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

            allTextLines.push({
              voiceIdx,
              startTime: lineStart,
              endTime: m.time,
              syllables: currentSyllables,
              hasCounter: false,
              isEmpty: false
            })

            currentSyllables = []
            lineStart = null
          }
          // endofline with no syllables = blank verse separator; gap will create empty lines

        } else if (mt === 'newline') {
          // Verse separator; force-close any open line
          if (currentSyllables.length > 0 && lineStart !== null) {
            allTextLines.push({
              voiceIdx,
              startTime: lineStart,
              endTime: m.time,
              syllables: currentSyllables,
              hasCounter: false,
              isEmpty: false
            })
            currentSyllables = []
            lineStart = null
          }
        }
      }
    })

    allTextLines.sort((a, b) => a.startTime - b.startTime)
    if (allTextLines.length === 0) return []
    return this._buildScrollLines(allTextLines)
  }

  // Insert empty placeholder lines so scroll speed stays consistent through silences
  _buildScrollLines(textLines) {
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
        const gapStart = textLines[i].endTime
        const gapEnd = textLines[i + 1].startTime
        const gap = gapEnd - gapStart

        if (gap > maxDur) {
          const countEmpty = Math.floor(gap / maxDur)
          const step = gap / (countEmpty + 1)
          for (let j = 0; j < countEmpty; j++) {
            result.push(this._makeEmptyLine(gapStart + (j + 1) * step))
          }
          textLines[i + 1].hasCounter = true
        }
      }
    }

    // Empty lines after last text line
    const lastLine = textLines[textLines.length - 1]
    const totalDur = this.duration > 0 ? this.duration : lastLine.endTime + maxDur * 3
    const endGap = totalDur - lastLine.endTime
    const countEnd = Math.min(5, Math.max(1, Math.floor(endGap / maxDur)))
    const stepEnd = endGap / (countEnd + 1)
    for (let i = 0; i < countEnd; i++) {
      result.push(this._makeEmptyLine(lastLine.endTime + (i + 1) * stepEnd))
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

    // Frozen inside a non-empty text line
    for (let i = 0; i < lines.length; i++) {
      const l = lines[i]
      if (!l.isEmpty && l.endTime > l.startTime && ct >= l.startTime && ct <= l.endTime) {
        return i
      }
    }

    if (ct <= lines[0].startTime) return 0
    if (ct >= lines[lines.length - 1].startTime) return lines.length - 1

    // Interpolate between departure of curr and arrival of next
    for (let i = 0; i < lines.length - 1; i++) {
      const curr = lines[i]
      const next = lines[i + 1]
      const t0 = curr.isEmpty ? curr.startTime : curr.endTime
      const t1 = next.startTime
      if (ct >= t0 && ct < t1) {
        if (t1 <= t0) return i
        return i + (ct - t0) / (t1 - t0)
      }
    }

    return lines.length - 1
  }

  // ─── UI ───────────────────────────────────────────────────────────────────

  _buildUI() {
    this.container.style.cssText = 'position:relative;background:#000;user-select:none;font-family:sans-serif'
    this.container.innerHTML = `
      <div style="display:flex;flex-direction:column;height:100vh;background:#000">
        <div id="kp-canvas-wrap" style="flex:1;position:relative;overflow:hidden;min-height:0">
          <canvas id="kp-canvas" style="width:100%;height:100%;display:block"></canvas>
          <div id="kp-loading" style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;color:#aaa;font-size:20px;background:#000">
            Загрузка аудио...
          </div>
        </div>
        <div style="background:#111;padding:4px 10px">
          <div style="display:flex;align-items:center;gap:8px;margin-bottom:2px">
            <span style="color:#888;font-size:11px;width:44px;text-align:right">Музыка</span>
            <input id="kp-vol-acc" type="range" min="0" max="100" value="100" style="width:80px;cursor:pointer;accent-color:#08f">
            <div id="kp-ws-acc" style="flex:1;height:40px;min-width:0"></div>
          </div>
          <div style="display:flex;align-items:center;gap:8px">
            <span style="color:#888;font-size:11px;width:44px;text-align:right">Голос</span>
            <input id="kp-vol-voc" type="range" min="0" max="100" value="100" style="width:80px;cursor:pointer;accent-color:#f80">
            <div id="kp-ws-voc" style="flex:1;height:40px;min-width:0"></div>
          </div>
        </div>
        <div style="background:#111;border-top:1px solid #333;padding:6px 12px;display:flex;align-items:center;gap:10px">
          <button id="kp-play" style="background:none;border:none;color:#fff;font-size:22px;cursor:pointer;padding:0;line-height:1;min-width:28px">▶</button>
          <span id="kp-time" style="color:#888;font-size:12px;min-width:88px">0:00 / 0:00</span>
          <div id="kp-progress-wrap" style="flex:1;height:5px;background:#333;border-radius:3px;cursor:pointer;position:relative">
            <div id="kp-progress-bar" style="height:100%;background:#f80;border-radius:3px;width:0%;pointer-events:none"></div>
          </div>
          <button id="kp-fs" style="background:none;border:none;color:#ccc;font-size:16px;cursor:pointer;padding:0 4px">⛶</button>
        </div>
      </div>`

    this.canvas = this.container.querySelector('#kp-canvas')
    this.ctx = this.canvas.getContext('2d')
    this._resizeCanvas()

    window.addEventListener('resize', this._resizeHandler)
    document.addEventListener('fullscreenchange', this._fsHandler)

    this.container.querySelector('#kp-play').addEventListener('click', () => this._togglePlay())
    this.container.querySelector('#kp-fs').addEventListener('click', () => this._toggleFullscreen())

    const pw = this.container.querySelector('#kp-progress-wrap')
    pw.addEventListener('click', e => {
      const r = pw.getBoundingClientRect()
      this._seekTo(((e.clientX - r.left) / r.width) * this.duration)
    })

    this.container.querySelector('#kp-vol-acc').addEventListener('input', e => {
      if (this.accGain) this.accGain.gain.value = e.target.value / 100
    })
    this.container.querySelector('#kp-vol-voc').addEventListener('input', e => {
      if (this.vocGain) this.vocGain.gain.value = e.target.value / 100
    })
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
    this.accGain.connect(this.audioCtx.destination)
    this.vocGain.connect(this.audioCtx.destination)

    const [accBuf, vocBuf] = await Promise.all([
      this._fetchAudio(this.data.audioAccompanimentUrl),
      this._fetchAudio(this.data.audioVocalsUrl)
    ])
    this.accBuffer = accBuf
    this.vocBuffer = vocBuf
    this.duration = Math.max(accBuf.duration, vocBuf.duration)

    const loading = this.container.querySelector('#kp-loading')
    if (loading) loading.style.display = 'none'
  }

  async _fetchAudio(url) {
    const resp = await fetch(url)
    if (!resp.ok) throw new Error(`Audio fetch failed: ${url}`)
    return this.audioCtx.decodeAudioData(await resp.arrayBuffer())
  }

  _buildWaveforms() {
    import('wavesurfer.js').then(({ default: WaveSurfer }) => {
      const ac = this.container.querySelector('#kp-ws-acc')
      const vc = this.container.querySelector('#kp-ws-voc')
      if (!ac || !vc) return
      this.wsAcc = WaveSurfer.create({ container: ac, url: this.data.audioAccompanimentUrl, interact: false, height: 40, waveColor: '#4af', progressColor: '#08f' })
      this.wsVoc = WaveSurfer.create({ container: vc, url: this.data.audioVocalsUrl, interact: false, height: 40, waveColor: '#fa4', progressColor: '#f80' })
    }).catch(e => console.warn('WaveSurfer load failed:', e))
  }

  _getCurrentTime() {
    if (!this.audioCtx) return 0
    return this.isPlaying ? this.audioCtx.currentTime - this.startedAt + this.pausedAt : this.pausedAt
  }

  _togglePlay() { this.isPlaying ? this._pause() : this._play() }

  async _play() {
    if (!this.accBuffer || !this.vocBuffer) return
    if (this.audioCtx.state === 'suspended') await this.audioCtx.resume()

    const offset = Math.max(0, this.pausedAt)
    this._endedHandled = false

    this.accSource = this.audioCtx.createBufferSource()
    this.accSource.buffer = this.accBuffer
    this.accSource.connect(this.accGain)

    this.vocSource = this.audioCtx.createBufferSource()
    this.vocSource.buffer = this.vocBuffer
    this.vocSource.connect(this.vocGain)

    this.startedAt = this.audioCtx.currentTime - offset
    this.accSource.start(0, offset)
    this.vocSource.start(0, offset)
    this.isPlaying = true

    const btn = this.container.querySelector('#kp-play')
    if (btn) btn.textContent = '⏸'

    this.accSource.onended = () => {
      if (this.isPlaying && !this._endedHandled) { this._endedHandled = true; this._onEnded() }
    }
  }

  _pause() {
    this.pausedAt = this._getCurrentTime()
    this._endedHandled = true
    this.accSource?.stop()
    this.vocSource?.stop()
    this.isPlaying = false
    const btn = this.container.querySelector('#kp-play')
    if (btn) btn.textContent = '▶'
  }

  _onEnded() {
    this.pausedAt = 0
    this.isPlaying = false
    const btn = this.container.querySelector('#kp-play')
    if (btn) btn.textContent = '▶'
  }

  _seekTo(time) {
    time = Math.max(0, Math.min(time, this.duration))
    const was = this.isPlaying
    if (was) { this._endedHandled = true; this.accSource?.stop(); this.vocSource?.stop(); this.isPlaying = false }
    this.pausedAt = time
    if (was) this._play()
    if (this.duration > 0) {
      const pct = time / this.duration
      try { this.wsAcc?.seekTo(pct) } catch {}
      try { this.wsVoc?.seekTo(pct) } catch {}
    }
  }

  _toggleFullscreen() {
    document.fullscreenElement ? document.exitFullscreen() : this.container.requestFullscreen?.()
  }

  // ─── Render loop ──────────────────────────────────────────────────────────

  _startRenderLoop() {
    const render = () => { this._renderFrame(); this.animId = requestAnimationFrame(render) }
    this.animId = requestAnimationFrame(render)
  }

  _renderFrame() {
    const ct = this._getCurrentTime()
    const W = this.canvas.width
    const H = this.canvas.height
    const ctx = this.ctx

    ctx.fillStyle = '#000'
    ctx.fillRect(0, 0, W, H)

    const scale = H / 1080
    const fontSize = Math.round(54 * scale)
    const lineHeight = Math.round(fontSize * 1.6)
    const xStart = Math.round(W * 0.05)

    const SPLASH_DUR = 5.0
    if (ct < SPLASH_DUR && this.data) {
      this._renderSplash(ctx, W, H, scale, ct, SPLASH_DUR)
    } else {
      this._renderKaraoke(ctx, W, H, scale, fontSize, lineHeight, xStart, ct)
    }

    this._updateControls(ct)
  }

  // ─── Splash ───────────────────────────────────────────────────────────────

  _renderSplash(ctx, W, H, scale, ct, splashDur) {
    const fadeOut = ct > splashDur - 0.4 ? (splashDur - ct) / 0.4 : 1.0
    ctx.globalAlpha = Math.max(0, fadeOut)
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'

    const ts = Math.round(60 * scale)
    ctx.font = `bold ${ts}px sans-serif`
    ctx.fillStyle = 'rgb(255,255,127)'
    ctx.fillText(this.data.songName || '', W / 2, H / 2 - ts * 0.7)

    const ss = Math.round(36 * scale)
    ctx.font = `${ss}px sans-serif`
    ctx.fillStyle = 'rgb(85,255,255)'
    ctx.fillText(this.data.author || '', W / 2, H / 2 + ts * 0.15)

    const as = Math.round(26 * scale)
    ctx.font = `${as}px sans-serif`
    ctx.fillStyle = 'rgb(255,255,127)'
    ctx.fillText(this.data.album || '', W / 2, H / 2 + ts * 0.15 + ss * 1.3)

    ctx.globalAlpha = 1.0
    ctx.textAlign = 'left'
    ctx.textBaseline = 'alphabetic'
  }

  // ─── Karaoke frame ────────────────────────────────────────────────────────

  _renderKaraoke(ctx, W, H, scale, fontSize, lineHeight, xStart, ct) {
    const centerY = H / 2
    const scrollPos = this._getScrollPosition(ct)

    // Horizon lines
    const horizonColor = this._getHorizonColor(ct)
    ctx.strokeStyle = horizonColor
    ctx.lineWidth = Math.max(1, Math.round(2 * scale))
    const topH = Math.round(centerY - lineHeight * 0.6)
    const botH = Math.round(centerY + lineHeight * 0.6)
    ctx.beginPath(); ctx.moveTo(0, topH); ctx.lineTo(W, topH); ctx.stroke()
    ctx.beginPath(); ctx.moveTo(0, botH); ctx.lineTo(W, botH); ctx.stroke()

    // Visible lines
    const halfVisible = Math.ceil(H / lineHeight) + 2
    const iMin = Math.max(0, Math.floor(scrollPos) - halfVisible)
    const iMax = Math.min(this.lines.length - 1, Math.ceil(scrollPos) + halfVisible)

    for (let i = iMin; i <= iMax; i++) {
      const line = this.lines[i]
      if (line.isEmpty || !line.syllables.length) continue
      const y = Math.round(centerY + (i - scrollPos) * lineHeight)
      if (y < -lineHeight || y > H + lineHeight) continue

      const isActive = ct >= line.startTime && ct < line.endTime
      const isSung = !isActive && ct >= line.endTime
      this._renderLine(ctx, line, y, fontSize, xStart, W, ct, isActive, isSung)
    }

    this._renderCounter(ctx, W, H, scale, ct)

    const hAlpha = this._getHeaderAlpha(ct)
    this._renderHeader(ctx, W, H, scale, hAlpha)
  }

  _getHorizonColor(ct) {
    const active = this.lines.find(l => !l.isEmpty && ct >= l.startTime && ct < l.endTime)
    const COLORS = ['rgb(0,200,0)', 'rgb(200,0,0)', 'rgb(0,100,200)']
    return COLORS[((active ? active.voiceIdx : 0)) % COLORS.length]
  }

  // ─── Line rendering ───────────────────────────────────────────────────────

  _renderLine(ctx, line, centerY, fontSize, xStart, W, ct, isActive, isSung) {
    const isV1 = line.voiceIdx === 1
    ctx.font = isV1 ? `italic bold ${fontSize}px sans-serif` : `bold ${fontSize}px sans-serif`
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
    const textColor = isV1 ? 'rgb(255,255,155)' : '#ffffff'
    const FILL_COLOR = 'rgb(255,128,0)'
    const SUNG_COLOR = 'rgba(255,255,255,0.35)'

    if (isActive) {
      // Compute smooth fill width
      let fillW = 0
      for (const s of syls) {
        if (ct < s.startTime) break
        if (s.endTime > s.startTime && ct < s.endTime) {
          fillW = (s.x - xStart) + s.w * (ct - s.startTime) / (s.endTime - s.startTime)
        } else {
          fillW = (s.x - xStart) + s.w
        }
      }

      // Orange fill
      ctx.fillStyle = FILL_COLOR
      ctx.fillRect(xStart, rectY, fillW, rectH)

      // Text clipped to filled region
      ctx.save()
      ctx.beginPath(); ctx.rect(xStart, rectY - 1, fillW, rectH + 2); ctx.clip()
      ctx.fillStyle = textColor
      for (const s of syls) ctx.fillText(s.text, s.x, centerY)
      ctx.restore()

      // Text clipped to unfilled region
      ctx.save()
      ctx.beginPath(); ctx.rect(xStart + fillW, rectY - 1, W, rectH + 2); ctx.clip()
      ctx.fillStyle = textColor
      for (const s of syls) ctx.fillText(s.text, s.x, centerY)
      ctx.restore()

    } else if (isSung) {
      ctx.fillStyle = FILL_COLOR
      ctx.fillRect(xStart, rectY, totalW, rectH)
      ctx.fillStyle = SUNG_COLOR
      for (const s of syls) ctx.fillText(s.text, s.x, centerY)

    } else {
      ctx.fillStyle = textColor
      for (const s of syls) ctx.fillText(s.text, s.x, centerY)
    }

    ctx.textBaseline = 'alphabetic'
  }

  // ─── Counter ──────────────────────────────────────────────────────────────

  _renderCounter(ctx, W, H, scale, ct) {
    const bpm = this.data?.bpm
    if (!bpm || bpm === 0) return
    const halfNote = (60 / bpm) * 2

    // n=4 red, n=3 red, n=2 yellow, n=1 yellow, n=0 green
    const COLORS = ['rgb(0,255,0)', 'rgb(255,255,0)', 'rgb(255,255,0)', 'rgb(255,0,0)', 'rgb(255,0,0)']

    for (const line of this.lines) {
      if (!line.hasCounter) continue

      for (let n = 4; n >= 0; n--) {
        // Counter n appears at (lineStart - n*halfNote), visible for 1 halfNote
        const tStart = line.startTime - n * halfNote
        const tEnd = tStart + halfNote

        if (ct >= tStart && ct < tEnd) {
          const progress = (ct - tStart) / halfNote
          const fs = Math.round(72 * scale)
          const fadeStart = 0.75
          const yShift = progress > fadeStart ? -((progress - fadeStart) / (1 - fadeStart)) * fs : 0
          const alpha = progress > fadeStart ? 1 - (progress - fadeStart) / (1 - fadeStart) : 1

          ctx.save()
          ctx.globalAlpha = alpha
          ctx.font = `bold ${fs}px sans-serif`
          ctx.fillStyle = COLORS[n] || '#fff'
          ctx.textAlign = 'center'
          ctx.textBaseline = 'middle'
          ctx.fillText(String(n), Math.round(W * 0.03) + fs / 2, H / 2 + yShift)
          ctx.restore()
          break
        }
      }
    }
  }

  // ─── Header ───────────────────────────────────────────────────────────────

  _getHeaderAlpha(ct) {
    const SPLASH_END = 5.0
    if (ct < SPLASH_END) return 0

    const bpm = this.data?.bpm || 120
    const halfNote = (60 / bpm) * 2

    const firstCounterLine = this.lines.find(l => !l.isEmpty && l.hasCounter)
    if (!firstCounterLine) return 1

    // Header hides when counter=4 appears (4 halfNotes before the line)
    const hideStart = firstCounterLine.startTime - 4 * halfNote
    const hideEnd = firstCounterLine.startTime  // = hideStart + 4*halfNote

    // Header returns after last text line
    const lastTextLine = [...this.lines].reverse().find(l => !l.isEmpty)
    const showStart = lastTextLine ? lastTextLine.endTime : Infinity
    const showEnd = showStart + 4 * halfNote

    if (ct < hideStart) return 1
    if (ct < hideEnd) return 1 - (ct - hideStart) / (hideEnd - hideStart)
    if (ct < showStart) return 0
    if (ct < showEnd) return (ct - showStart) / (showEnd - showStart)
    return 1
  }

  _renderHeader(ctx, W, H, scale, alpha) {
    if (alpha <= 0) return
    ctx.globalAlpha = alpha

    const ts = Math.round(38 * scale)
    const ss = Math.round(22 * scale)
    const xOff = Math.round(W * 0.05)
    const yOff = Math.round(20 * scale)

    ctx.textBaseline = 'top'

    ctx.font = `bold ${ts}px sans-serif`
    ctx.fillStyle = 'rgb(255,255,127)'
    ctx.fillText(this.data.songName || '', xOff, yOff)

    const y2 = yOff + ts + Math.round(6 * scale)
    ctx.font = `${ss}px sans-serif`
    ctx.fillStyle = 'rgb(85,255,255)'
    const authorLabel = 'Исполнитель: '
    ctx.fillText(authorLabel, xOff, y2)
    ctx.fillStyle = 'rgb(255,255,127)'
    ctx.fillText(this.data.author || '', xOff + ctx.measureText(authorLabel).width, y2)

    const y3 = y2 + ss + Math.round(3 * scale)
    ctx.fillStyle = 'rgb(85,255,255)'
    const albumLabel = 'Альбом: '
    ctx.fillText(albumLabel, xOff, y3)
    ctx.fillStyle = 'rgb(255,255,127)'
    ctx.fillText(this.data.album || '', xOff + ctx.measureText(albumLabel).width, y3)

    ctx.globalAlpha = 1.0
    ctx.textBaseline = 'alphabetic'
  }

  // ─── Controls ─────────────────────────────────────────────────────────────

  _updateControls(ct) {
    const pct = this.duration > 0 ? (ct / this.duration) * 100 : 0
    const bar = this.container.querySelector('#kp-progress-bar')
    if (bar) bar.style.width = `${pct}%`

    const timeEl = this.container.querySelector('#kp-time')
    if (timeEl) timeEl.textContent = `${this._fmtTime(ct)} / ${this._fmtTime(this.duration)}`

    const now = Date.now()
    if (now - this._lastWsSync > 200 && this.duration > 0) {
      this._lastWsSync = now
      const pctWs = ct / this.duration
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

  destroy() {
    if (this.animId) cancelAnimationFrame(this.animId)
    this._endedHandled = true
    try { this.accSource?.stop() } catch {}
    try { this.vocSource?.stop() } catch {}
    this.audioCtx?.close()
    this.wsAcc?.destroy()
    this.wsVoc?.destroy()
    window.removeEventListener('resize', this._resizeHandler)
    document.removeEventListener('fullscreenchange', this._fsHandler)
  }
}
