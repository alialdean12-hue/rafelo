"""Narration with Microsoft neural voices (edge-tts, free, no key).
  python tts_edge.py lines.json outdir --voice ar-SA-HamedNeural --rate=+0%
lines.json = [{"id":"l1","text":"..."}]  ->  outdir/l1.wav (trimmed, 48k mono) + outdir/durations.json
Voices: ar-SA-HamedNeural, ar-SA-ZariyahNeural (Fusha) | ar-AE-HamdanNeural, ar-AE-FatimaNeural (Emirati)
        ar-EG-ShakirNeural, ar-EG-SalmaNeural (Egyptian)
Behind a TLS-inspecting proxy it uses SSL_CERT_FILE automatically."""
import argparse, asyncio, json, os, subprocess, sys
from pathlib import Path
ca = os.environ.get("SSL_CERT_FILE") or os.environ.get("REQUESTS_CA_BUNDLE")
if ca:
    import certifi; certifi.where = lambda: ca          # edge-tts builds its SSL context from certifi
import edge_tts

async def one(text, voice, rate, out):
    for attempt in range(3):
        try:
            await edge_tts.Communicate(text, voice, rate=rate).save(out); return
        except Exception as e:
            if attempt == 2: raise
            await asyncio.sleep(2)

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("lines"); ap.add_argument("out")
    ap.add_argument("--voice", default="ar-SA-HamedNeural"); ap.add_argument("--rate", default="+0%")
    a = ap.parse_args(); out = Path(a.out); out.mkdir(parents=True, exist_ok=True)
    lines = json.loads(Path(a.lines).read_text()); dur = {}
    for ln in lines:
        raw = out / f"{ln['id']}.mp3"; wav = out / f"{ln['id']}.wav"
        asyncio.run(one(ln["text"], a.voice, a.rate, str(raw)))
        sil = "silenceremove=start_periods=1:start_threshold=-45dB"
        subprocess.run(["ffmpeg","-y","-loglevel","error","-i",str(raw),"-af",f"{sil},areverse,{sil},areverse,aresample=48000","-ac","1",str(wav)],check=True)
        d = float(subprocess.check_output(["ffprobe","-v","error","-show_entries","format=duration","-of","csv=p=0",str(wav)]))
        dur[ln["id"]] = round(d, 3); print(ln["id"], dur[ln["id"]], "s")
    (out / "durations.json").write_text(json.dumps(dur, indent=1))
main()
