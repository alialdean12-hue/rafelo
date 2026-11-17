#!/usr/bin/env python3
"""Arabic narration (Microsoft neural voices via edge-tts), one mp3 per line + word timings.
    pip install edge-tts
    python3 tools/voice.py script.txt out_dir [--voice ar-AE-HamdanNeural] [--rate +10%]
Writes out_dir/l1.mp3, l2.mp3, ... and out_dir/words.json ([[word, seconds], ...] per line)."""
import sys, json, asyncio, os, argparse, certifi
# the session proxy re-signs TLS; edge-tts pins certifi, so point certifi at the proxy CA when present
if os.path.exists('/root/.ccr/ca-bundle.crt'): certifi.where = lambda: '/root/.ccr/ca-bundle.crt'
import edge_tts

ap = argparse.ArgumentParser(); ap.add_argument('script'); ap.add_argument('out')
ap.add_argument('--voice', default='ar-AE-HamdanNeural'); ap.add_argument('--rate', default='+10%')
a = ap.parse_args(); os.makedirs(a.out, exist_ok=True)

async def one(i, text):
    c = edge_tts.Communicate(text, a.voice, rate=a.rate, boundary='WordBoundary')
    audio, words = b'', []
    async for m in c.stream():
        if m['type'] == 'audio': audio += m['data']
        elif m['type'] == 'WordBoundary': words.append([m['text'], m['offset'] / 1e7])
    assert audio, f'no audio for line {i}'
    open(f'{a.out}/l{i}.mp3', 'wb').write(audio)
    return words

lines = [l for l in open(a.script).read().split('\n') if l.strip()]
json.dump([asyncio.run(one(i, l)) for i, l in enumerate(lines, 1)], open(f'{a.out}/words.json', 'w'), ensure_ascii=False)
print(f'{len(lines)} lines -> {a.out}')
