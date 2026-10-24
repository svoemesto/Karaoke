#!/usr/bin/env python3
"""Заглушка приватного registry для регрессионных проверок (OpenProject #379).

Логирует Authorization-заголовок каждого запроса в stderr:
    "<METHOD> <path> <authorization-header|NONE>"

Режимы (env):
  STUB_MODE=strict  — верные креды → 200, неверные/аноним → 401 (штатный registry)
  STUB_MODE=weak    — аноним → 401, но ЛЮБЫЕ креды → 200 (сломанный htpasswd:
                      принимает неверный пароль — именно этот случай ловит
                      проверка «неверный пароль → 401»)
  STUB_MODE=broken  — ЛЮБЫЕ креды (и аноним) → 200 (совсем открытый registry)
"""
import base64
import http.server
import os
import sys

MODE = os.environ.get("STUB_MODE", "strict")
GOOD_USER = os.environ.get("STUB_USER", "smokeuser")
GOOD_PASS = os.environ.get("STUB_PASS", "smokepass")


class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _authorized(self):
        auth = self.headers.get("Authorization", "")
        if not auth.startswith("Basic "):
            return False
        try:
            raw = base64.b64decode(auth[6:]).decode("utf-8", "replace")
        except Exception:
            return False
        return raw == f"{GOOD_USER}:{GOOD_PASS}"

    def _respond(self, code, body=b"", ctype="application/json"):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if body:
            self.wfile.write(body)

    def _handle(self, method):
        path = self.path.split("?")[0]
        auth = self.headers.get("Authorization", "NONE")
        print(f"{method} {path} {auth}", file=sys.stderr, flush=True)
        if MODE == "broken":
            if path.startswith("/v2/"):
                if path.endswith("/_catalog"):
                    self._respond(200, b'{"repositories":[]}')
                elif "/manifests/" in path:
                    self._respond(200, b'{"schemaVersion":2}')
                else:
                    self._respond(200, b"{}")
            else:
                self._respond(404, b"{}")
            return
        if path == "/" or not path.startswith("/v2/"):
            self._respond(404, b"{}")
            return
        if MODE == "weak":
            # Сломанный htpasswd: аноним не пускает, но ЛЮБЫЕ креды принимает.
            if not auth or auth == "NONE":
                self._respond(401, b'{"errors":[{"code":"UNAUTHORIZED"}]}')
                return
            if path.endswith("/_catalog"):
                self._respond(200, b'{"repositories":[]}')
            elif "/manifests/" in path:
                self._respond(200, b'{"schemaVersion":2}')
            else:
                self._respond(200, b"{}")
            return
        if not self._authorized():
            self._respond(401, b'{"errors":[{"code":"UNAUTHORIZED"}]}')
            return
        if path.endswith("/_catalog"):
            self._respond(200, b'{"repositories":[]}')
        elif "/manifests/" in path:
            self._respond(200, b'{"schemaVersion":2}')
        else:
            self._respond(200, b"{}")

    def do_GET(self):
        self._handle("GET")

    def do_HEAD(self):
        self._handle("HEAD")

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 15500
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler)
    print(f"stub registry on {port} mode={MODE}", file=sys.stderr, flush=True)
    srv.serve_forever()
