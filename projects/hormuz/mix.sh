#!/usr/bin/env bash
# mix.sh <silent_video> <out.mp4> : voice (scene k at k*10+0.3s) + low drone + whoosh on every cut
set -euo pipefail
V="$1"; OUT="$2"
in=(); f=""; L=""
for k in 1 2 3 4 5 6; do in+=(-i voice/block0$k.wav); d=$(( (k-1)*10000 + 300 )); f+="[$k:a]aresample=48000,adelay=${d}|${d},volume=1.6[v$k];"; L+="[v$k]"; done
f+="sine=f=55:d=60,aresample=48000,tremolo=f=0.4:d=0.6,volume=0.10[dr];"
f+="sine=f=82.5:d=60,aresample=48000,tremolo=f=0.27:d=0.7,volume=0.06[dr2];"
w=""; for c in 1 2 3 4 5; do t=$(( c*10000 - 350 )); f+="anoisesrc=d=0.6:c=pink:a=0.5,aresample=48000,highpass=f=400,lowpass=f=3500,afade=t=in:d=0.3,afade=t=out:st=0.3:d=0.3,adelay=${t}|${t}[w$c];"; w+="[w$c]"; done
f+="${L}[dr][dr2]${w}amix=inputs=13:normalize=0:duration=longest,atrim=0:60,alimiter=limit=0.9[a]"
ffmpeg -v error -y -i "$V" "${in[@]}" -filter_complex "$f" -map 0:v -map "[a]" -c:v copy -c:a aac -b:a 192k -ar 48000 -t 60 "$OUT"
