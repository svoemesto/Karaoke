/* eslint-disable */
!function (e, t) {
    "object" == typeof exports && "undefined" != typeof module ? t(exports) : "function" == typeof define && define.amd ? define(["exports"], t) : t((e = "undefined" != typeof globalThis ? globalThis : e || self).StompJs = {})
}(this, (function (e) {
    "use strict";
    const t = "\n", s = "\0";

    class n {
        constructor(e) {
            const {
                command: t,
                headers: s,
                body: n,
                binaryBody: i,
                escapeHeaderValues: o,
                skipContentLengthHeader: c
            } = e;
            this.command = t, this.headers = Object.assign({}, s || {}), i ? (this._binaryBody = i, this.isBinaryBody = !0) : (this._body = n || "", this.isBinaryBody = !1), this.escapeHeaderValues = o || !1, this.skipContentLengthHeader = c || !1
        }

        get body() {
            return !this._body && this.isBinaryBody && (this._body = (new TextDecoder).decode(this._binaryBody)), this._body || ""
        }

        get binaryBody() {
            return this._binaryBody || this.isBinaryBody || (this._binaryBody = (new TextEncoder).encode(this._body)), this._binaryBody
        }

        static fromRawFrame(e, t) {
            const s = {}, i = e => e.replace(/^\s+|\s+$/g, "");
            for (const o of e.headers.reverse()) {
                o.indexOf(":");
                const c = i(o[0]);
                let r = i(o[1]);
                t && "CONNECT" !== e.command && "CONNECTED" !== e.command && (r = n.hdrValueUnEscape(r)), s[c] = r
            }
            return new n({command: e.command, headers: s, binaryBody: e.binaryBody, escapeHeaderValues: t})
        }

        toString() {
            return this.serializeCmdAndHeaders()
        }

        serialize() {
            const e = this.serializeCmdAndHeaders();
            return this.isBinaryBody ? n.toUnit8Array(e, this._binaryBody).buffer : e + this._body + s
        }

        serializeCmdAndHeaders() {
            const e = [this.command];
            this.skipContentLengthHeader && delete this.headers["content-length"];
            for (const t of Object.keys(this.headers || {})) {
                const s = this.headers[t];
                this.escapeHeaderValues && "CONNECT" !== this.command && "CONNECTED" !== this.command ? e.push(`${t}:${n.hdrValueEscape(`${s}`)}`) : e.push(`${t}:${s}`)
            }
            return (this.isBinaryBody || !this.isBodyEmpty() && !this.skipContentLengthHeader) && e.push(`content-length:${this.bodyLength()}`), e.join(t) + t + t
        }

        isBodyEmpty() {
            return 0 === this.bodyLength()
        }

        bodyLength() {
            const e = this.binaryBody;
            return e ? e.length : 0
        }

        static sizeOfUTF8(e) {
            return e ? (new TextEncoder).encode(e).length : 0
        }

        static toUnit8Array(e, t) {
            const s = (new TextEncoder).encode(e), n = new Uint8Array([0]),
                i = new Uint8Array(s.length + t.length + n.length);
            return i.set(s), i.set(t, s.length), i.set(n, s.length + t.length), i
        }

        static marshall(e) {
            return new n(e).serialize()
        }

        static hdrValueEscape(e) {
            return e.replace(/\\/g, "\\\\").replace(/\r/g, "\\r").replace(/\n/g, "\\n").replace(/:/g, "\\c")
        }

        static hdrValueUnEscape(e) {
            return e.replace(/\\r/g, "\r").replace(/\\n/g, "\n").replace(/\\c/g, ":").replace(/\\\\/g, "\\")
        }
    }

    class i {
        constructor(e, t) {
            this.onFrame = e, this.onIncomingPing = t, this._encoder = new TextEncoder, this._decoder = new TextDecoder, this._token = [], this._initState()
        }

        parseChunk(e, t = !1) {
            let s;
            if (s = "string" == typeof e ? this._encoder.encode(e) : new Uint8Array(e), t && 0 !== s[s.length - 1]) {
                const e = new Uint8Array(s.length + 1);
                e.set(s, 0), e[s.length] = 0, s = e
            }
            for (let e = 0; e < s.length; e++) {
                const t = s[e];
                this._onByte(t)
            }
        }

        _collectFrame(e) {
            0 !== e && 13 !== e && (10 !== e ? (this._onByte = this._collectCommand, this._reinjectByte(e)) : this.onIncomingPing())
        }

        _collectCommand(e) {
            if (13 !== e) return 10 === e ? (this._results.command = this._consumeTokenAsUTF8(), void (this._onByte = this._collectHeaders)) : void this._consumeByte(e)
        }

        _collectHeaders(e) {
            13 !== e && (10 !== e ? (this._onByte = this._collectHeaderKey, this._reinjectByte(e)) : this._setupCollectBody())
        }

        _reinjectByte(e) {
            this._onByte(e)
        }

        _collectHeaderKey(e) {
            if (58 === e) return this._headerKey = this._consumeTokenAsUTF8(), void (this._onByte = this._collectHeaderValue);
            this._consumeByte(e)
        }

        _collectHeaderValue(e) {
            if (13 !== e) return 10 === e ? (this._results.headers.push([this._headerKey, this._consumeTokenAsUTF8()]), this._headerKey = void 0, void (this._onByte = this._collectHeaders)) : void this._consumeByte(e)
        }

        _setupCollectBody() {
            const e = this._results.headers.filter((e => "content-length" === e[0]))[0];
            e ? (this._bodyBytesRemaining = parseInt(e[1], 10), this._onByte = this._collectBodyFixedSize) : this._onByte = this._collectBodyNullTerminated
        }

        _collectBodyNullTerminated(e) {
            0 !== e ? this._consumeByte(e) : this._retrievedBody()
        }

        _collectBodyFixedSize(e) {
            0 != this._bodyBytesRemaining-- ? this._consumeByte(e) : this._retrievedBody()
        }

        _retrievedBody() {
            this._results.binaryBody = this._consumeTokenAsRaw();
            try {
                this.onFrame(this._results)
            } catch (e) {
                console.log("Ignoring an exception thrown by a frame handler. Original exception: ", e)
            }
            this._initState()
        }

        _consumeByte(e) {
            this._token.push(e)
        }

        _consumeTokenAsUTF8() {
            return this._decoder.decode(this._consumeTokenAsRaw())
        }

        _consumeTokenAsRaw() {
            const e = new Uint8Array(this._token);
            return this._token = [], e
        }

        _initState() {
            this._results = {
                command: void 0,
                headers: [],
                binaryBody: void 0
            }, this._token = [], this._headerKey = void 0, this._onByte = this._collectFrame
        }
    }

    var o, c;
    e.StompSocketState = void 0, (o = e.StompSocketState = e.StompSocketState || (e.StompSocketState = {}))[o.CONNECTING = 0] = "CONNECTING", o[o.OPEN = 1] = "OPEN", o[o.CLOSING = 2] = "CLOSING", o[o.CLOSED = 3] = "CLOSED", e.ActivationState = void 0, (c = e.ActivationState = e.ActivationState || (e.ActivationState = {}))[c.ACTIVE = 0] = "ACTIVE", c[c.DEACTIVATING = 1] = "DEACTIVATING", c[c.INACTIVE = 2] = "INACTIVE";

    class r {
        constructor(e) {
            this.versions = e
        }

        supportedVersions() {
            return this.versions.join(",")
        }

        protocolVersions() {
            return this.versions.map((e => `v${e.replace(".", "")}.stomp`))
        }
    }

    r.V1_0 = "1.0", r.V1_1 = "1.1", r.V1_2 = "1.2", r.default = new r([r.V1_2, r.V1_1, r.V1_0]);

    class a {
        constructor(e, t, s) {
            this._client = e, this._webSocket = t, this._connected = !1, this._serverFrameHandlers = {
                CONNECTED: e => {
                    this.debug(`connected to server ${e.headers.server}`), this._connected = !0, this._connectedVersion = e.headers.version, this._connectedVersion === r.V1_2 && (this._escapeHeaderValues = !0), this._setupHeartbeat(e.headers), this.onConnect(e)
                }, MESSAGE: e => {
                    const t = e.headers.subscription, s = this._subscriptions[t] || this.onUnhandledMessage, n = e,
                        i = this, o = this._connectedVersion === r.V1_2 ? n.headers.ack : n.headers["message-id"];
                    n.ack = (e = {}) => i.ack(o, t, e), n.nack = (e = {}) => i.nack(o, t, e), s(n)
                }, RECEIPT: e => {
                    const t = this._receiptWatchers[e.headers["receipt-id"]];
                    t ? (t(e), delete this._receiptWatchers[e.headers["receipt-id"]]) : this.onUnhandledReceipt(e)
                }, ERROR: e => {
                    this.onStompError(e)
                }
            }, this._counter = 0, this._subscriptions = {}, this._receiptWatchers = {}, this._partialData = "", this._escapeHeaderValues = !1, this._lastServerActivityTS = Date.now(), this.debug = s.debug, this.stompVersions = s.stompVersions, this.connectHeaders = s.connectHeaders, this.disconnectHeaders = s.disconnectHeaders, this.heartbeatIncoming = s.heartbeatIncoming, this.heartbeatOutgoing = s.heartbeatOutgoing, this.splitLargeFrames = s.splitLargeFrames, this.maxWebSocketChunkSize = s.maxWebSocketChunkSize, this.forceBinaryWSFrames = s.forceBinaryWSFrames, this.logRawCommunication = s.logRawCommunication, this.appendMissingNULLonIncoming = s.appendMissingNULLonIncoming, this.discardWebsocketOnCommFailure = s.discardWebsocketOnCommFailure, this.onConnect = s.onConnect, this.onDisconnect = s.onDisconnect, this.onStompError = s.onStompError, this.onWebSocketClose = s.onWebSocketClose, this.onWebSocketError = s.onWebSocketError, this.onUnhandledMessage = s.onUnhandledMessage, this.onUnhandledReceipt = s.onUnhandledReceipt, this.onUnhandledFrame = s.onUnhandledFrame
        }

        get connectedVersion() {
            return this._connectedVersion
        }

        get connected() {
            return this._connected
        }

        start() {
            const e = new i((e => {
                const t = n.fromRawFrame(e, this._escapeHeaderValues);
                this.logRawCommunication || this.debug(`<<< ${t}`);
                (this._serverFrameHandlers[t.command] || this.onUnhandledFrame)(t)
            }), (() => {
                this.debug("<<< PONG")
            }));
            this._webSocket.onmessage = t => {
                if (this.debug("Received data"), this._lastServerActivityTS = Date.now(), this.logRawCommunication) {
                    const e = t.data instanceof ArrayBuffer ? (new TextDecoder).decode(t.data) : t.data;
                    this.debug(`<<< ${e}`)
                }
                e.parseChunk(t.data, this.appendMissingNULLonIncoming)
            }, this._webSocket.onclose = e => {
                this.debug(`Connection closed to ${this._webSocket.url}`), this._cleanUp(), this.onWebSocketClose(e)
            }, this._webSocket.onerror = e => {
                this.onWebSocketError(e)
            }, this._webSocket.onopen = () => {
                const e = Object.assign({}, this.connectHeaders);
                this.debug("Web Socket Opened..."), e["accept-version"] = this.stompVersions.supportedVersions(), e["heart-beat"] = [this.heartbeatOutgoing, this.heartbeatIncoming].join(","), this._transmit({
                    command: "CONNECT",
                    headers: e
                })
            }
        }

        _setupHeartbeat(s) {
            if (s.version !== r.V1_1 && s.version !== r.V1_2) return;
            if (!s["heart-beat"]) return;
            const [n, i] = s["heart-beat"].split(",").map((e => parseInt(e, 10)));
            if (0 !== this.heartbeatOutgoing && 0 !== i) {
                const s = Math.max(this.heartbeatOutgoing, i);
                this.debug(`send PING every ${s}ms`), this._pinger = setInterval((() => {
                    this._webSocket.readyState === e.StompSocketState.OPEN && (this._webSocket.send(t), this.debug(">>> PING"))
                }), s)
            }
            if (0 !== this.heartbeatIncoming && 0 !== n) {
                const e = Math.max(this.heartbeatIncoming, n);
                this.debug(`check PONG every ${e}ms`), this._ponger = setInterval((() => {
                    const t = Date.now() - this._lastServerActivityTS;
                    t > 2 * e && (this.debug(`did not receive server activity for the last ${t}ms`), this._closeOrDiscardWebsocket())
                }), e)
            }
        }

        _closeOrDiscardWebsocket() {
            this.discardWebsocketOnCommFailure ? (this.debug("Discarding websocket, the underlying socket may linger for a while"), this.discardWebsocket()) : (this.debug("Issuing close on the websocket"), this._closeWebsocket())
        }

        forceDisconnect() {
            this._webSocket && (this._webSocket.readyState !== e.StompSocketState.CONNECTING && this._webSocket.readyState !== e.StompSocketState.OPEN || this._closeOrDiscardWebsocket())
        }

        _closeWebsocket() {
            this._webSocket.onmessage = () => {
            }, this._webSocket.close()
        }

        discardWebsocket() {
            var e, t;
            "function" != typeof this._webSocket.terminate && (e = this._webSocket, t = e => this.debug(e), e.terminate = function () {
                const s = () => {
                };
                this.onerror = s, this.onmessage = s, this.onopen = s;
                const n = new Date, i = Math.random().toString().substring(2, 8), o = this.onclose;
                this.onclose = e => {
                    const s = (new Date).getTime() - n.getTime();
                    t(`Discarded socket (#${i})  closed after ${s}ms, with code/reason: ${e.code}/${e.reason}`)
                }, this.close(), o?.call(e, {
                    code: 4001,
                    reason: `Quick discarding socket (#${i}) without waiting for the shutdown sequence.`,
                    wasClean: !1
                })
            }), this._webSocket.terminate()
        }

        _transmit(e) {
            const {command: t, headers: s, body: i, binaryBody: o, skipContentLengthHeader: c} = e, r = new n({
                command: t,
                headers: s,
                body: i,
                binaryBody: o,
                escapeHeaderValues: this._escapeHeaderValues,
                skipContentLengthHeader: c
            });
            let a = r.serialize();
            if (this.logRawCommunication ? this.debug(`>>> ${a}`) : this.debug(`>>> ${r}`), this.forceBinaryWSFrames && "string" == typeof a && (a = (new TextEncoder).encode(a)), "string" == typeof a && this.splitLargeFrames) {
                let e = a;
                for (; e.length > 0;) {
                    const t = e.substring(0, this.maxWebSocketChunkSize);
                    e = e.substring(this.maxWebSocketChunkSize), this._webSocket.send(t), this.debug(`chunk sent = ${t.length}, remaining = ${e.length}`)
                }
            } else this._webSocket.send(a)
        }

        dispose() {
            if (this.connected) try {
                const e = Object.assign({}, this.disconnectHeaders);
                e.receipt || (e.receipt = "close-" + this._counter++), this.watchForReceipt(e.receipt, (e => {
                    this._closeWebsocket(), this._cleanUp(), this.onDisconnect(e)
                })), this._transmit({command: "DISCONNECT", headers: e})
            } catch (e) {
                this.debug(`Ignoring error during disconnect ${e}`)
            } else this._webSocket.readyState !== e.StompSocketState.CONNECTING && this._webSocket.readyState !== e.StompSocketState.OPEN || this._closeWebsocket()
        }

        _cleanUp() {
            this._connected = !1, this._pinger && (clearInterval(this._pinger), this._pinger = void 0), this._ponger && (clearInterval(this._ponger), this._ponger = void 0)
        }

        publish(e) {
            const {destination: t, headers: s, body: n, binaryBody: i, skipContentLengthHeader: o} = e,
                c = Object.assign({destination: t}, s);
            this._transmit({command: "SEND", headers: c, body: n, binaryBody: i, skipContentLengthHeader: o})
        }

        watchForReceipt(e, t) {
            this._receiptWatchers[e] = t
        }

        subscribe(e, t, s = {}) {
            (s = Object.assign({}, s)).id || (s.id = "sub-" + this._counter++), s.destination = e, this._subscriptions[s.id] = t, this._transmit({
                command: "SUBSCRIBE",
                headers: s
            });
            const n = this;
            return {id: s.id, unsubscribe: e => n.unsubscribe(s.id, e)}
        }

        unsubscribe(e, t = {}) {
            t = Object.assign({}, t), delete this._subscriptions[e], t.id = e, this._transmit({
                command: "UNSUBSCRIBE",
                headers: t
            })
        }

        begin(e) {
            const t = e || "tx-" + this._counter++;
            this._transmit({command: "BEGIN", headers: {transaction: t}});
            const s = this;
            return {
                id: t, commit() {
                    s.commit(t)
                }, abort() {
                    s.abort(t)
                }
            }
        }

        commit(e) {
            this._transmit({command: "COMMIT", headers: {transaction: e}})
        }

        abort(e) {
            this._transmit({command: "ABORT", headers: {transaction: e}})
        }

        ack(e, t, s = {}) {
            s = Object.assign({}, s), this._connectedVersion === r.V1_2 ? s.id = e : s["message-id"] = e, s.subscription = t, this._transmit({
                command: "ACK",
                headers: s
            })
        }

        nack(e, t, s = {}) {
            return s = Object.assign({}, s), this._connectedVersion === r.V1_2 ? s.id = e : s["message-id"] = e, s.subscription = t, this._transmit({
                command: "NACK",
                headers: s
            })
        }
    }

    class h {
        constructor(t = {}) {
            this.stompVersions = r.default, this.connectionTimeout = 0, this.reconnectDelay = 5e3, this.heartbeatIncoming = 1e4, this.heartbeatOutgoing = 1e4, this.splitLargeFrames = !1, this.maxWebSocketChunkSize = 8192, this.forceBinaryWSFrames = !1, this.appendMissingNULLonIncoming = !1, this.discardWebsocketOnCommFailure = !1, this.state = e.ActivationState.INACTIVE;
            const s = () => {
            };
            this.debug = s, this.beforeConnect = s, this.onConnect = s, this.onDisconnect = s, this.onUnhandledMessage = s, this.onUnhandledReceipt = s, this.onUnhandledFrame = s, this.onStompError = s, this.onWebSocketClose = s, this.onWebSocketError = s, this.logRawCommunication = !1, this.onChangeState = s, this.connectHeaders = {}, this._disconnectHeaders = {}, this.configure(t)
        }

        get webSocket() {
            return this._stompHandler?._webSocket
        }

        get disconnectHeaders() {
            return this._disconnectHeaders
        }

        set disconnectHeaders(e) {
            this._disconnectHeaders = e, this._stompHandler && (this._stompHandler.disconnectHeaders = this._disconnectHeaders)
        }

        get connected() {
            return !!this._stompHandler && this._stompHandler.connected
        }

        get connectedVersion() {
            return this._stompHandler ? this._stompHandler.connectedVersion : void 0
        }

        get active() {
            return this.state === e.ActivationState.ACTIVE
        }

        _changeState(e) {
            this.state = e, this.onChangeState(e)
        }

        configure(e) {
            Object.assign(this, e)
        }

        activate() {
            const t = () => {
                this.active ? this.debug("Already ACTIVE, ignoring request to activate") : (this._changeState(e.ActivationState.ACTIVE), this._connect())
            };
            this.state === e.ActivationState.DEACTIVATING ? (this.debug("Waiting for deactivation to finish before activating"), this.deactivate().then((() => {
                t()
            }))) : t()
        }

        async _connect() {
            if (await this.beforeConnect(), this._stompHandler) return void this.debug("There is already a stompHandler, skipping the call to connect");
            if (!this.active) return void this.debug("Client has been marked inactive, will not attempt to connect");
            this.connectionTimeout > 0 && (this._connectionWatcher && clearTimeout(this._connectionWatcher), this._connectionWatcher = setTimeout((() => {
                this.connected || (this.debug(`Connection not established in ${this.connectionTimeout}ms, closing socket`), this.forceDisconnect())
            }), this.connectionTimeout)), this.debug("Opening Web Socket...");
            const t = this._createWebSocket();
            this._stompHandler = new a(this, t, {
                debug: this.debug,
                stompVersions: this.stompVersions,
                connectHeaders: this.connectHeaders,
                disconnectHeaders: this._disconnectHeaders,
                heartbeatIncoming: this.heartbeatIncoming,
                heartbeatOutgoing: this.heartbeatOutgoing,
                splitLargeFrames: this.splitLargeFrames,
                maxWebSocketChunkSize: this.maxWebSocketChunkSize,
                forceBinaryWSFrames: this.forceBinaryWSFrames,
                logRawCommunication: this.logRawCommunication,
                appendMissingNULLonIncoming: this.appendMissingNULLonIncoming,
                discardWebsocketOnCommFailure: this.discardWebsocketOnCommFailure,
                onConnect: e => {
                    if (this._connectionWatcher && (clearTimeout(this._connectionWatcher), this._connectionWatcher = void 0), !this.active) return this.debug("STOMP got connected while deactivate was issued, will disconnect now"), void this._disposeStompHandler();
                    this.onConnect(e)
                },
                onDisconnect: e => {
                    this.onDisconnect(e)
                },
                onStompError: e => {
                    this.onStompError(e)
                },
                onWebSocketClose: t => {
                    this._stompHandler = void 0, this.state === e.ActivationState.DEACTIVATING && this._changeState(e.ActivationState.INACTIVE), this.onWebSocketClose(t), this.active && this._schedule_reconnect()
                },
                onWebSocketError: e => {
                    this.onWebSocketError(e)
                },
                onUnhandledMessage: e => {
                    this.onUnhandledMessage(e)
                },
                onUnhandledReceipt: e => {
                    this.onUnhandledReceipt(e)
                },
                onUnhandledFrame: e => {
                    this.onUnhandledFrame(e)
                }
            }), this._stompHandler.start()
        }

        _createWebSocket() {
            let e;
            if (this.webSocketFactory) e = this.webSocketFactory(); else {
                if (!this.brokerURL) throw new Error("Either brokerURL or webSocketFactory must be provided");
                e = new WebSocket(this.brokerURL, this.stompVersions.protocolVersions())
            }
            return e.binaryType = "arraybuffer", e
        }

        _schedule_reconnect() {
            this.reconnectDelay > 0 && (this.debug(`STOMP: scheduling reconnection in ${this.reconnectDelay}ms`), this._reconnector = setTimeout((() => {
                this._connect()
            }), this.reconnectDelay))
        }

        async deactivate(t = {}) {
            const s = t.force || !1, n = this.active;
            let i;
            if (this.state === e.ActivationState.INACTIVE) return this.debug("Already INACTIVE, nothing more to do"), Promise.resolve();
            if (this._changeState(e.ActivationState.DEACTIVATING), this._reconnector && (clearTimeout(this._reconnector), this._reconnector = void 0), !this._stompHandler || this.webSocket.readyState === e.StompSocketState.CLOSED) return this._changeState(e.ActivationState.INACTIVE), Promise.resolve();
            {
                const e = this._stompHandler.onWebSocketClose;
                i = new Promise(((t, s) => {
                    this._stompHandler.onWebSocketClose = s => {
                        e(s), t()
                    }
                }))
            }
            return s ? this._stompHandler?.discardWebsocket() : n && this._disposeStompHandler(), i
        }

        forceDisconnect() {
            this._stompHandler && this._stompHandler.forceDisconnect()
        }

        _disposeStompHandler() {
            this._stompHandler && this._stompHandler.dispose()
        }

        publish(e) {
            this._checkConnection(), this._stompHandler.publish(e)
        }

        _checkConnection() {
            if (!this.connected) throw new TypeError("There is no underlying STOMP connection")
        }

        watchForReceipt(e, t) {
            this._checkConnection(), this._stompHandler.watchForReceipt(e, t)
        }

        subscribe(e, t, s = {}) {
            return this._checkConnection(), this._stompHandler.subscribe(e, t, s)
        }

        unsubscribe(e, t = {}) {
            this._checkConnection(), this._stompHandler.unsubscribe(e, t)
        }

        begin(e) {
            return this._checkConnection(), this._stompHandler.begin(e)
        }

        commit(e) {
            this._checkConnection(), this._stompHandler.commit(e)
        }

        abort(e) {
            this._checkConnection(), this._stompHandler.abort(e)
        }

        ack(e, t, s = {}) {
            this._checkConnection(), this._stompHandler.ack(e, t, s)
        }

        nack(e, t, s = {}) {
            this._checkConnection(), this._stompHandler.nack(e, t, s)
        }
    }

    class d {
        constructor(e) {
            this.client = e
        }

        get outgoing() {
            return this.client.heartbeatOutgoing
        }

        set outgoing(e) {
            this.client.heartbeatOutgoing = e
        }

        get incoming() {
            return this.client.heartbeatIncoming
        }

        set incoming(e) {
            this.client.heartbeatIncoming = e
        }
    }

    class l extends h {
        constructor(e) {
            super(), this.maxWebSocketFrameSize = 16384, this._heartbeatInfo = new d(this), this.reconnect_delay = 0, this.webSocketFactory = e, this.debug = (...e) => {
                console.log(...e)
            }
        }

        _parseConnect(...e) {
            let t, s, n, i = {};
            if (e.length < 2) throw new Error("Connect requires at least 2 arguments");
            if ("function" == typeof e[1]) [i, s, n, t] = e; else if (6 === e.length) [i.login, i.passcode, s, n, t, i.host] = e; else [i.login, i.passcode, s, n, t] = e;
            return [i, s, n, t]
        }

        connect(...e) {
            const t = this._parseConnect(...e);
            t[0] && (this.connectHeaders = t[0]), t[1] && (this.onConnect = t[1]), t[2] && (this.onStompError = t[2]), t[3] && (this.onWebSocketClose = t[3]), super.activate()
        }

        disconnect(e, t = {}) {
            e && (this.onDisconnect = e), this.disconnectHeaders = t, super.deactivate()
        }

        send(e, t = {}, s = "") {
            const n = !1 === (t = Object.assign({}, t))["content-length"];
            n && delete t["content-length"], this.publish({
                destination: e,
                headers: t,
                body: s,
                skipContentLengthHeader: n
            })
        }

        set reconnect_delay(e) {
            this.reconnectDelay = e
        }

        get ws() {
            return this.webSocket
        }

        get version() {
            return this.connectedVersion
        }

        get onreceive() {
            return this.onUnhandledMessage
        }

        set onreceive(e) {
            this.onUnhandledMessage = e
        }

        get onreceipt() {
            return this.onUnhandledReceipt
        }

        set onreceipt(e) {
            this.onUnhandledReceipt = e
        }

        get heartbeat() {
            return this._heartbeatInfo
        }

        set heartbeat(e) {
            this.heartbeatIncoming = e.incoming, this.heartbeatOutgoing = e.outgoing
        }
    }

    class m {
        static client(e, t) {
            null == t && (t = r.default.protocolVersions());
            return new l((() => new (m.WebSocketClass || WebSocket)(e, t)))
        }

        static over(e) {
            let t;
            return "function" == typeof e ? t = e : (console.warn("Stomp.over did not receive a factory, auto reconnect will not work. Please see https://stomp-js.github.io/api-docs/latest/classes/Stomp.html#over"), t = () => e), new l(t)
        }
    }

    m.WebSocketClass = null, e.Client = h, e.CompatClient = l, e.FrameImpl = n, e.Parser = i, e.Stomp = m, e.StompConfig = class {
    }, e.StompHeaders = class {
    }, e.Versions = r
}));