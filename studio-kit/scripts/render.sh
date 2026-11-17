#!/bin/sh
# Render one HyperFrames project and mux the mixed audio.
#   sh render.sh <project_dir> <mix.wav|-> <out.mp4> [fps]
# Needs: node>=22, npx hyperframes, ffmpeg. Fails loudly on lint errors.
set -e
P="$1"; AUD="$2"; OUT="$3"; FPS="${4:-24}"
cd "$P"
npx hyperframes lint 2>&1 | tee /tmp/lint.txt | tail -5
if grep -q "✗" /tmp/lint.txt; then echo "LINT ERRORS - fix first"; exit 1; fi
npx hyperframes render --fps "$FPS" -q delivery -o /tmp/silent.mp4 .
if [ "$AUD" = "-" ]; then cp /tmp/silent.mp4 "$OUT"; else
  ffmpeg -y -loglevel error -i /tmp/silent.mp4 -i "$AUD" -map 0:v -map 1:a -c:v copy -c:a aac -b:a 192k -shortest -movflags +faststart "$OUT"
fi
rm -rf .hyperframes renders
ffprobe -v error -show_entries format=duration,size -of compact "$OUT"
