"""Pixabay stock search/download (free licence, no attribution required).
  PIXABAY_KEY=xxxx python pixabay.py videos "gold bars" outdir [--n 5]
  PIXABAY_KEY=xxxx python pixabay.py photos "gold bars" outdir [--n 5]
  python pixabay.py vertical in.mp4 out.mp4 [--start 1 --dur 3.6]   # centre-crop to 1080x1920 @24fps, no audio
The key MUST be a URL parameter (?key=...). Never print or commit it; read it from the PIXABAY_KEY environment variable."""
import argparse, json, os, subprocess, sys, urllib.parse, urllib.request
from pathlib import Path
def get(url, ua="studio-kit"):
    return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": ua}), timeout=60).read()
def main():
    p = argparse.ArgumentParser(); p.add_argument("mode"); p.add_argument("a"); p.add_argument("b"); p.add_argument("--n", type=int, default=5)
    p.add_argument("--start", type=float, default=0); p.add_argument("--dur", type=float, default=4)
    x = p.parse_args()
    if x.mode == "vertical":
        subprocess.run(["ffmpeg","-y","-loglevel","error","-ss",str(x.start),"-t",str(x.dur),"-i",x.a,"-vf","crop='min(iw,ih*9/16)':ih,scale=1080:1920,fps=24","-an","-c:v","libx264","-crf","22","-preset","fast","-pix_fmt","yuv420p",x.b],check=True); return
    key = os.environ.get("PIXABAY_KEY") or sys.exit("set PIXABAY_KEY")
    kind = "videos/" if x.mode == "videos" else ""
    q = urllib.parse.urlencode({"key": key, "q": x.a, "per_page": max(3, x.n), "safesearch": "true"})
    hits = json.loads(get(f"https://pixabay.com/api/{kind}?{q}"))["hits"][: x.n]
    out = Path(x.b); out.mkdir(parents=True, exist_ok=True)
    for h in hits:
        if kind: u = h["videos"]["medium"]["url"]; name = f"v{h['id']}.mp4"; info = f"{h['duration']}s {h['videos']['medium']['width']}x{h['videos']['medium']['height']}"
        else: u = h["largeImageURL"]; name = f"p{h['id']}.jpg"; info = f"{h['imageWidth']}x{h['imageHeight']}"
        (out / name).write_bytes(get(u)); print(name, info, h.get("tags", ""))
main()
