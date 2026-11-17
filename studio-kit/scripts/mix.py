"""Mix narration + effects + bed into ONE normalised stereo track.
  python mix.py mix.json out.wav
mix.json = {"duration": 20, "target_lufs": -14, "items":[{"file":"voice/l1.wav","at":0.3,"gain":1.0}, {"file":"sfx/whoosh.wav","at":3.95,"gain":0.5}, {"file":"sfx/bed.wav","at":0,"gain":0.35}]}
Voice about 10 dB over music/effects: voice gain 1.0, effects 0.3-0.6, bed 0.3-0.45."""
import json, subprocess, sys
cfg = json.load(open(sys.argv[1])); items = cfg["items"]; D = cfg["duration"]
args = ["ffmpeg", "-y", "-loglevel", "error"]; fc = []
for i, it in enumerate(items):
    args += ["-i", it["file"]]; fc.append(f"[{i}]volume={it.get('gain',1)},adelay={int(it['at']*1000)}:all=1[a{i}]")
fc.append("".join(f"[a{i}]" for i in range(len(items))) + f"amix=inputs={len(items)}:normalize=0:duration=longest,atrim=0:{D},apad=whole_dur={D},"
          f"afade=t=out:st={D-0.35}:d=0.35,loudnorm=I={cfg.get('target_lufs',-14)}:TP=-1.5:LRA=11[out]")
subprocess.run(args + ["-filter_complex", ";".join(fc), "-map", "[out]", "-ar", "48000", "-ac", "2", sys.argv[2]], check=True)
print("ok", sys.argv[2])
