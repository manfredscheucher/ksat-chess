#!/usr/bin/env python3
"""Drive the built website in headless Chrome over the DevTools protocol (stdlib only).

usage: web_e2e.py URL OUTDIR WIDTHxHEIGHT step...
steps: click:X:Y  wait:SECONDS  shot:NAME  key:Escape
"""
import base64, json, os, socket, struct, subprocess, sys, tempfile, time, urllib.request

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
PORT = 9333

class Ws:
    def __init__(self, url):
        host, path = url[len("ws://"):].split("/", 1)
        h, p = host.split(":")
        self.s = socket.create_connection((h, int(p)))
        self.s.sendall((f"GET /{path} HTTP/1.1\r\nHost: {host}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                        "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n").encode())
        buf = b""
        while b"\r\n\r\n" not in buf:
            buf += self.s.recv(4096)
        self.n = 0

    def _read(self, k):
        b = b""
        while len(b) < k:
            b += self.s.recv(k - len(b))
        return b

    def recv(self):
        data = b""
        while True:
            h = self._read(2)
            n = h[1] & 127
            if n == 126: n = struct.unpack(">H", self._read(2))[0]
            elif n == 127: n = struct.unpack(">Q", self._read(8))[0]
            data += self._read(n)
            if h[0] & 0x80:
                return json.loads(data)

    def call(self, method, **params):
        self.n += 1
        msg = json.dumps({"id": self.n, "method": method, "params": params}).encode()
        n = len(msg)
        head = bytes([0x81]) + (bytes([0x80 | n]) if n < 126 else bytes([0x80 | 126]) + struct.pack(">H", n))
        self.s.sendall(head + b"\0\0\0\0" + msg)
        while True:
            r = self.recv()
            if r.get("method") in ("Runtime.exceptionThrown", "Log.entryAdded"):
                print("page:", json.dumps(r["params"])[:300])
            if r.get("id") == self.n:
                return r.get("result")

def main():
    url, out, size, *steps = sys.argv[1:]
    w, h = size.split("x")
    os.makedirs(out, exist_ok=True)
    proc = subprocess.Popen([CHROME, "--headless=new", "--no-first-run", f"--remote-debugging-port={PORT}",
                             f"--user-data-dir={tempfile.mkdtemp()}", f"--window-size={w},{h}", url],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        for _ in range(50):
            try:
                tabs = json.load(urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json"))
                page = next(t for t in tabs if t["type"] == "page")
                break
            except Exception:
                time.sleep(0.2)
        ws = Ws(page["webSocketDebuggerUrl"])
        ws.call("Runtime.enable"); ws.call("Log.enable")
        for step in steps:
            kind, *a = step.split(":")
            if kind == "wait":
                end = time.time() + float(a[0])
                while time.time() < end:
                    ws.call("Runtime.evaluate", expression="1")
                    time.sleep(0.2)
            elif kind == "click":
                x, y = float(a[0]), float(a[1])
                ws.call("Input.dispatchMouseEvent", type="mouseMoved", x=x, y=y)
                time.sleep(0.1)
                ws.call("Input.dispatchMouseEvent", type="mousePressed", x=x, y=y, button="left", clickCount=1, buttons=1)
                time.sleep(0.2)
                ws.call("Input.dispatchMouseEvent", type="mouseReleased", x=x, y=y, button="left", clickCount=1)
                time.sleep(0.5)
            elif kind == "key":
                for t in ("keyDown", "keyUp"):
                    ws.call("Input.dispatchKeyEvent", type=t, key=a[0], code=a[0], windowsVirtualKeyCode=27 if a[0] == "Escape" else 0)
                time.sleep(0.3)
            elif kind == "shot":
                data = ws.call("Page.captureScreenshot", format="png")["data"]
                with open(os.path.join(out, a[0] + ".png"), "wb") as f:
                    f.write(base64.b64decode(data))
    finally:
        proc.terminate()

main()
