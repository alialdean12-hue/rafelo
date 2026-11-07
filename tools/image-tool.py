#!/usr/bin/env python3
# image-tool.py — خادم توليد صور (FLUX.1 schnell / Cloudflare Workers AI). ملف واحد.
# التشغيل: python3 image-tool.py  [--port 8000]
import base64, json, os, sys, time, urllib.error, urllib.parse, urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ACC = os.environ.get("CLOUDFLARE_ACCOUNT_ID", "dcd46978eb6be240a350849b7a112b1e")
TOK = os.environ.get("CLOUDFLARE_API_TOKEN", "proxy-injected")  # real token is injected by the session proxy
MODEL = "@cf/black-forest-labs/flux-1-schnell"
OUT = Path(__file__).parent / "images"
OUT.mkdir(exist_ok=True)
DAILY, USED = 10000.0, {}


def gen(prompt: str) -> bytes:
    """Cloudflare لا يقبل seed/width/height — نرسل prompt و steps فقط."""
    url = f"https://api.cloudflare.com/client/v4/accounts/{ACC}/ai/run/{MODEL}"
    body = json.dumps({"prompt": prompt, "steps": 4}).encode()
    req = urllib.request.Request(url, data=body, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", f"Bearer {TOK}")
    try:
        with urllib.request.urlopen(req, timeout=240) as r:
            d = json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"HTTP {e.code}: {e.read().decode()[:200]}") from None
    if not d.get("success"):
        raise RuntimeError(str(d.get("errors"))[:200])
    img = d["result"]["image"]
    return base64.b64decode(img.split(",", 1)[1] if img.startswith("data:") else img)


def fit(data: bytes, w: int, h: int) -> bytes:
    """قصّ/تحجيم للأبعاد المطلوبة. يحتاج Pillow وإلا يرجع الأصل."""
    try:
        import io
        from PIL import Image
        im = Image.open(io.BytesIO(data)).convert("RGB"); sw, sh = im.size
        t, s = w / h, sw / sh
        if abs(t - s) > .01:
            if t > s: nh = int(sw / t); im = im.crop((0, (sh - nh) // 2, sw, (sh - nh) // 2 + nh))
            else: nw = int(sh * t); im = im.crop(((sw - nw) // 2, 0, (sw - nw) // 2 + nw, sh))
        if im.size != (w, h): im = im.resize((w, h), Image.LANCZOS)
        b = io.BytesIO(); im.save(b, "JPEG", quality=92); return b.getvalue()
    except Exception:
        return data


def usage() -> dict:
    u = USED.get("n", 0.0)
    return {"images_today": USED.get("c", 0), "neurons_used": round(u, 2), "quota": DAILY,
            "remaining": round(max(0, DAILY - u), 2),
            "estimate_images_left": int(max(0, DAILY - u) // 57.6)}


def make(prompt: str, w: int = 1024, h: int = 1024):
    if usage()["remaining"] <= 0: raise RuntimeError("نفدت الحصة اليومية (تتجدد 00:00 UTC)")
    w = max(256, min(1536, int(w))); h = max(256, min(1536, int(h)))
    t0 = time.time(); data = fit(gen(prompt), w, h)
    name = f"img-{int(t0)}.jpg"; (OUT / name).write_bytes(data)
    USED["c"] = USED.get("c", 0) + 1; USED["n"] = USED.get("n", 0) + 57.6
    print(f"OK {name} {len(data)//1024}KB {time.time()-t0:.1f}s left~{usage()['estimate_images_left']}", flush=True)
    return data, name


class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def _c(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET,POST,OPTIONS")
    def _j(self, o, s=200):
        b = json.dumps(o, ensure_ascii=False, indent=1).encode()
        self.send_response(s); self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(b))); self._c(); self.end_headers(); self.wfile.write(b)
    def _img(self, d, n):
        self.send_response(200); self.send_header("Content-Type", "image/jpeg")
        self.send_header("Content-Length", str(len(d))); self.send_header("X-Filename", n)
        self._c(); self.end_headers(); self.wfile.write(d)
    def do_OPTIONS(self): self.send_response(204); self._c(); self.end_headers()
    def do_GET(self):
        p = urllib.parse.urlparse(self.path); path = p.path.rstrip("/") or "/"
        q = {k: v[0] for k, v in urllib.parse.parse_qs(p.query).items()}
        try:
            if path in ("/", "/health"):
                return self._j({"status": "ok", "model": MODEL, "ready": bool(ACC and TOK),
                                "usage": usage(), "endpoints": {
                                    "image": "/generate?prompt=a+cat&width=1024&height=576",
                                    "json": "/generate?prompt=a+cat&json=1",
                                    "sheet": "/character-sheet?character=young+san",
                                    "usage": "/usage"}})
            if path == "/usage": return self._j(usage())
            if path == "/generate":
                pr = (q.get("prompt") or q.get("p") or "").strip()
                if not pr: return self._j({"error": "add ?prompt=..."}, 400)
                d, n = make(pr, q.get("width") or 1024, q.get("height") or 1024)
                if q.get("json") == "1":
                    return self._j({"success": True, "filename": n, "saved_to": str(OUT / n),
                                    "bytes": len(d), "url": f"/images/{n}", "usage": usage()})
                return self._img(d, n)
            if path == "/character-sheet":
                c = q.get("character") or "a young man"
                v = q.get("views") or "front, three-quarter, side, back"
                pr = (f"character model sheet, turnaround reference sheet of {c}, full body views: {v}, "
                      "three head close-ups and clothing detail callouts, consistent proportions, same "
                      "character identical in all views, clean thick line art, flat color shading, "
                      "animation production art, plain light grey background, no text, high resolution")
                d, n = make(pr, 1024, 1024); return self._img(d, n)
            if path.startswith("/images/"):
                f = (OUT / Path(path[8:]).name).resolve()
                if not str(f).startswith(str(OUT.resolve())) or not f.exists():
                    return self._j({"error": "not found"}, 404)
                return self._img(f.read_bytes(), f.name)
            return self._j({"error": "unknown path", "path": path}, 404)
        except Exception as e:
            return self._j({"error": str(e)}, 502)
    def do_POST(self):
        try:
            n = int(self.headers.get("Content-Length") or 0)
            b = json.loads(self.rfile.read(n) or b"{}")
            d, f = make((b.get("prompt") or "").strip(), b.get("width") or 1024, b.get("height") or 1024)
            if b.get("json"): return self._j({"success": True, "filename": f, "bytes": len(d)})
            return self._img(d, f)
        except Exception as e:
            return self._j({"error": str(e)}, 502)


if __name__ == "__main__":
    port = int(sys.argv[sys.argv.index("--port") + 1]) if "--port" in sys.argv else int(os.environ.get("PORT", 8000))
    print(f"image-tool on http://0.0.0.0:{port}/  |  quota left ~{usage()['estimate_images_left']} images", flush=True)
    s = ThreadingHTTPServer(("0.0.0.0", port), H); s.daemon_threads = True
    try: s.serve_forever()
    except KeyboardInterrupt: pass
