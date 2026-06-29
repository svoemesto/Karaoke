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
    this.activeLineIndex = -1
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
      this.lines = this._parseMarkers(this.data.markers || [])
      this._buildUI()
      await this._loadAudio()
      this._buildWaveforms()
      this._startRenderLoop()
    } catch (e) {
      console.error('KaraokePlayer init error:', e)
      if (this.container) {
        this.container.innerHTML = `<div style="color:#f66;padding:40px;font-size:18px;font-family:sans-serif">Ошибка загрузки: ${e.message}</div>`
      }
    }
  }

  // Маркеры → массив строк
  _parseMarkers(markersList) {
    const lines = []

    markersList.forEach((voiceMarkers, voiceIdx) => {
      let currentLine = null
      let isAfterEmpty = true

      for (let i = 0; i < voiceMarkers.length; i++) {
        const m = voiceMarkers[i]
        const mt = m.markertype

        if (mt === 'syllables') {
          if (!currentLine) {
            currentLine = {
              voiceIdx,
              startTime: m.time,
              endTime: null,
              syllables: [],
              hasCounter: isAfterEmpty,
              text: ''
            }
          }
          // Конец слога = время следующего syllables или endofline
          let syllableEnd = null
          for (let j = i + 1; j < voiceMarkers.length; j++) {
            const mt2 = voiceMarkers[j].markertype
            if (mt2 === 'syllables' || mt2 === 'endofline' || mt2 === 'endofsyllable') {
              syllableEnd = voiceMarkers[j].time
              break
            }
          }
          currentLine.syllables.push({
            text: m.label,
            startTime: m.time,
            endTime: syllableEnd
          })
          currentLine.text += m.label
          isAfterEmpty = false

        } else if (mt === 'endofline' || mt === 'endofsyllable') {
          if (currentLine) {
            currentLine.endTime = m.time
            lines.push(currentLine)
            currentLine = null
          }
          if (mt === 'endofline') isAfterEmpty = false

        } else if (mt === 'newline') {
          if (currentLine) {
            currentLine.endTime = m.time
            lines.push(currentLine)
            currentLine = null
          }
          isAfterEmpty = true
        }
      }

      if (currentLine) {
        lines.push(currentLine)
      }
    })

    lines.sort((a, b) => a.startTime - b.startTime)
    return lines
  }

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
      </div>
    `

    this.canvas = this.container.querySelector('#kp-canvas')
    this.ctx = this.canvas.getContext('2d')
    this._resizeCanvas()

    window.addEventListener('resize', this._resizeHandler)
    document.addEventListener('fullscreenchange', this._fsHandler)

    this.container.querySelector('#kp-play').addEventListener('click', () => this._togglePlay())
    this.container.querySelector('#kp-fs').addEventListener('click', () => this._toggleFullscreen())

    const pw = this.container.querySelector('#kp-progress-wrap')
    pw.addEventListener('click', (e) => {
      const r = pw.getBoundingClientRect()
      this._seekTo(((e.clientX - r.left) / r.width) * this.duration)
    })

    this.container.querySelector('#kp-vol-acc').addEventListener('input', (e) => {
      if (this.accGain) this.accGain.gain.value = e.target.value / 100
    })
    this.container.querySelector('#kp-vol-voc').addEventListener('input', (e) => {
      if (this.vocGain) this.vocGain.gain.value = e.target.value / 100
    })
  }

  _resizeCanvas() {
    const wrap = this.container.querySelector('#kp-canvas-wrap')
    if (!wrap || !this.canvas) return
    this.canvas.width = wrap.clientWidth
    this.canvas.height = wrap.clientHeight
  }

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
    const buf = await resp.arrayBuffer()
    return this.audioCtx.decodeAudioData(buf)
  }

  _buildWaveforms() {
    import('wavesurfer.js').then(({ default: WaveSurfer }) => {
      const accContainer = this.container.querySelector('#kp-ws-acc')
      const vocContainer = this.container.querySelector('#kp-ws-voc')
      if (!accContainer || !vocContainer) return

      this.wsAcc = WaveSurfer.create({
        container: accContainer,
        url: this.data.audioAccompanimentUrl,
        interact: false,
        height: 40,
        waveColor: '#4af',
        progressColor: '#08f'
      })
      this.wsVoc = WaveSurfer.create({
        container: vocContainer,
        url: this.data.audioVocalsUrl,
        interact: false,
        height: 40,
        waveColor: '#fa4',
        progressColor: '#f80'
      })
    }).catch(e => console.warn('WaveSurfer load failed:', e))
  }

  _getCurrentTime() {
    if (!this.audioCtx) return 0
    if (this.isPlaying) {
      return this.audioCtx.currentTime - this.startedAt + this.pausedAt
    }
    return this.pausedAt
  }

  _togglePlay() {
    if (this.isPlaying) this._pause()
    else this._play()
  }

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

    const playBtn = this.container.querySelector('#kp-play')
    if (playBtn) playBtn.textContent = '⏸'

    this.accSource.onended = () => {
      if (this.isPlaying && !this._endedHandled) {
        this._endedHandled = true
        this._onEnded()
      }
    }
  }

  _pause() {
    this.pausedAt = this._getCurrentTime()
    this._endedHandled = true
    this.accSource?.stop()
    this.vocSource?.stop()
    this.isPlaying = false
    const playBtn = this.container.querySelector('#kp-play')
    if (playBtn) playBtn.textContent = '▶'
  }

  _onEnded() {
    this.pausedAt = 0
    this.isPlaying = false
    const playBtn = this.container.querySelector('#kp-play')
    if (playBtn) playBtn.textContent = '▶'
  }

  _seekTo(time) {
    time = Math.max(0, Math.min(time, this.duration))
    const wasPlaying = this.isPlaying
    if (wasPlaying) {
      this._endedHandled = true
      this.accSource?.stop()
      this.vocSource?.stop()
      this.isPlaying = false
    }
    this.pausedAt = time
    if (wasPlaying) this._play()
    if (this.duration > 0) {
      const pct = time / this.duration
      try { this.wsAcc?.seekTo(pct) } catch {}
      try { this.wsVoc?.seekTo(pct) } catch {}
    }
  }

  _toggleFullscreen() {
    if (document.fullscreenElement) {
      document.exitFullscreen()
    } else {
      this.container.requestFullscreen?.()
    }
  }

  _startRenderLoop() {
    const render = () => {
      this._renderFrame()
      this.animId = requestAnimationFrame(render)
    }
    this.animId = requestAnimationFrame(render)
  }

  _renderFrame() {
    const ct = this._getCurrentTime()
    const W = this.canvas.width
    const H = this.canvas.height
    const ctx = this.ctx

    ctx.clearRect(0, 0, W, H)
    ctx.fillStyle = '#000'
    ctx.fillRect(0, 0, W, H)

    const scale = H / 1080
    const fontSize = Math.round(54 * scale)
    const lineHeight = Math.round(fontSize * 1.45)
    const xStart = Math.round(W * 0.05)
    const FILL_COLOR = 'rgb(255,128,0)'
    const SUNG_COLOR = 'rgba(255,255,255,0.35)'

    const SPLASH_DUR = 5.0
    if (ct < SPLASH_DUR && this.data) {
      this._renderSplash(ctx, W, H, scale, ct, SPLASH_DUR)
    } else {
      this._renderKaraoke(ctx, W, H, scale, fontSize, lineHeight, xStart, FILL_COLOR, SUNG_COLOR, ct)
    }

    this._updateControls(ct)
  }

  _renderSplash(ctx, W, H, scale, ct, splashDur) {
    const fadeOut = ct > splashDur - 0.4 ? (splashDur - ct) / 0.4 : 1.0
    ctx.globalAlpha = Math.max(0, fadeOut)

    const ts = Math.round(60 * scale)
    ctx.font = `bold ${ts}px sans-serif`
    ctx.fillStyle = 'rgb(255,255,127)'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
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

  _getHeaderAlpha(ct) {
    const SPLASH_END = 5.0
    if (ct < SPLASH_END) return 0

    const bpm = this.data?.bpm || 120
    const halfNote = (60 / bpm) * 2

    const firstCounterLine = this.lines.find(l => l.hasCounter && l.startTime > SPLASH_END)
    if (!firstCounterLine) return 1

    const hideStart = firstCounterLine.startTime - halfNote * 5
    const hideDur = halfNote * 4
    const hideEnd = hideStart + hideDur

    const lastLine = this.lines[this.lines.length - 1]
    const showStart = lastLine ? lastLine.endTime + halfNote * 4 : Infinity

    if (ct < hideStart) return 1
    if (ct < hideEnd) return 1 - (ct - hideStart) / hideDur
    if (ct < showStart) return 0
    return Math.min(1, (ct - showStart) / hideDur)
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

    ctx.font = `${ss}px sans-serif`
    const y2 = yOff + ts + Math.round(6 * scale)
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

  _renderCounter(ctx, W, H, scale, ct) {
    const bpm = this.data?.bpm
    if (!bpm || bpm === 0) return
    const halfNote = (60 / bpm) * 2

    const COLORS = [
      'rgb(0,255,0)',
      'rgb(255,255,0)',
      'rgb(255,255,0)',
      'rgb(255,0,0)',
      'rgb(255,0,0)'
    ]

    for (const line of this.lines) {
      if (!line.hasCounter) continue

      for (let n = 4; n >= 0; n--) {
        const tStart = line.startTime - (n + 1) * halfNote
        const tEnd = line.startTime - n * halfNote
        if (ct >= tStart && ct < tEnd) {
          const progress = (ct - tStart) / halfNote
          const fs = Math.round(72 * scale)
          const yShift = progress > 0.75 ? -((progress - 0.75) / 0.25) * fs : 0
          const alpha = progress > 0.75 ? 1 - (progress - 0.75) / 0.25 : 1

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

  _renderKaraoke(ctx, W, H, scale, fontSize, lineHeight, xStart, FILL_COLOR, SUNG_COLOR, ct) {
    // Найти активную строку
    let activeIdx = -1
    for (let i = 0; i < this.lines.length; i++) {
      const l = this.lines[i]
      if (ct >= l.startTime && l.endTime != null && ct < l.endTime) {
        activeIdx = i
        break
      }
    }
    if (activeIdx === -1 && this.lines.length > 0) {
      if (ct < this.lines[0].startTime) {
        activeIdx = 0
      } else {
        // Между строками — показать предыдущую
        for (let i = this.lines.length - 1; i >= 0; i--) {
          if (this.lines[i].startTime <= ct) {
            activeIdx = i
            break
          }
        }
      }
    }

    if (this.lines.length === 0) return

    const centerY = H / 2

    // Горизонтальные линии
    const horizonColor = this._getHorizonColor(activeIdx)
    const lw = Math.max(1, Math.round(2 * scale))
    ctx.strokeStyle = horizonColor
    ctx.lineWidth = lw
    const topH = centerY - Math.round(lineHeight * 0.58)
    const botH = centerY + Math.round(lineHeight * 0.58)
    ctx.beginPath(); ctx.moveTo(0, topH); ctx.lineTo(W, topH); ctx.stroke()
    ctx.beginPath(); ctx.moveTo(0, botH); ctx.lineTo(W, botH); ctx.stroke()

    // Строки
    const visible = 5
    const start = Math.max(0, activeIdx - visible)
    const end = Math.min(this.lines.length - 1, activeIdx + visible)

    for (let i = start; i <= end; i++) {
      const line = this.lines[i]
      const offset = i - activeIdx
      const y = centerY + offset * lineHeight

      const isActive = i === activeIdx && ct >= line.startTime && line.endTime != null && ct < line.endTime
      const isSung = i < activeIdx

      this._renderLine(ctx, line, y, fontSize, xStart, W, ct, isActive, isSung, FILL_COLOR, SUNG_COLOR)
    }

    this._renderCounter(ctx, W, H, scale, ct)

    const hAlpha = this._getHeaderAlpha(ct)
    this._renderHeader(ctx, W, H, scale, hAlpha)
  }

  _getHorizonColor(idx) {
    if (idx < 0 || idx >= this.lines.length) return 'rgb(0,180,0)'
    const COLORS = ['rgb(0,200,0)', 'rgb(200,0,0)', 'rgb(0,100,200)']
    return COLORS[this.lines[idx].voiceIdx % COLORS.length]
  }

  _renderLine(ctx, line, centerY, fontSize, xStart, W, ct, isActive, isSung, FILL_COLOR, SUNG_COLOR) {
    if (!line.syllables.length) return

    const isV1 = line.voiceIdx === 1
    ctx.font = isV1 ? `italic bold ${fontSize}px sans-serif` : `bold ${fontSize}px sans-serif`
    ctx.textBaseline = 'middle'

    // Вычислить X для каждого слога
    let x = xStart
    const syls = line.syllables.map(syl => {
      const w = ctx.measureText(syl.text).width
      const r = { text: syl.text, startTime: syl.startTime, endTime: syl.endTime, x, w }
      x += w
      return r
    })
    const totalW = x - xStart

    if (isActive) {
      // Плавная заливка
      let fillW = 0
      for (const s of syls) {
        if (s.startTime > ct) break
        if (s.endTime != null && ct < s.endTime) {
          const dur = s.endTime - s.startTime
          const prog = dur > 0 ? (ct - s.startTime) / dur : 1
          fillW = (s.x - xStart) + s.w * prog
        } else {
          fillW = (s.x - xStart) + s.w
        }
      }

      const rectH = fontSize * 1.1
      const rectY = centerY - rectH / 2

      // Оранжевый фон под спетой частью
      ctx.fillStyle = FILL_COLOR
      ctx.fillRect(xStart, rectY, fillW, rectH)

      // Текст: спетая часть (белый/жёлтый поверх оранжевого)
      ctx.save()
      ctx.beginPath()
      ctx.rect(xStart, rectY, fillW, rectH)
      ctx.clip()
      ctx.fillStyle = isV1 ? 'rgb(255,255,155)' : '#ffffff'
      for (const s of syls) ctx.fillText(s.text, s.x, centerY)
      ctx.restore()

      // Текст: неспетая часть
      ctx.save()
      ctx.beginPath()
      ctx.rect(xStart + fillW, rectY - 1, W, rectH + 2)
      ctx.clip()
      ctx.fillStyle = isV1 ? 'rgb(255,255,155)' : '#ffffff'
      for (const s of syls) ctx.fillText(s.text, s.x, centerY)
      ctx.restore()

    } else if (isSung) {
      const rectH = fontSize * 1.1
      const rectY = centerY - rectH / 2
      ctx.fillStyle = FILL_COLOR
      ctx.fillRect(xStart, rectY, totalW, rectH)
      ctx.fillStyle = SUNG_COLOR
      for (const s of syls) ctx.fillText(s.text, s.x, centerY)

    } else {
      ctx.fillStyle = isV1 ? 'rgb(255,255,155)' : '#ffffff'
      for (const s of syls) ctx.fillText(s.text, s.x, centerY)
    }

    ctx.textBaseline = 'alphabetic'
  }

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
