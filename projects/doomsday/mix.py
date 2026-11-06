# python3 mix.py <silent.mp4> <out.mp4>: narration + heartbeat drone + a thud per impact + whoosh per cut + paper tear
import json, subprocess, sys
V, OUT = sys.argv[1:3]
imp = json.load(open('impacts.json'))
ins = ['-i', V] + sum([['-i', f'voice/block0{k}.wav'] for k in range(1, 7)], [])
f, mix = [], []
for k in range(1, 7):
    d = (k - 1) * 10000 + 250
    f.append(f'[{k}:a]aresample=48000,adelay={d}|{d},volume=1.5[v{k}]'); mix.append(f'[v{k}]')
# ~46 BPM heartbeat over a sub drone
f.append('sine=f=48:d=60,aresample=48000,volume=0.16[dr]'); mix.append('[dr]')
f.append("aevalsrc='0.55*sin(2*PI*55*t)*exp(-9*mod(t,1.3))+0.35*sin(2*PI*55*t)*exp(-9*mod(t-0.28,1.3))*gte(mod(t,1.3),0.28)':d=60:s=48000,lowpass=f=140,volume=0.5[hb]"); mix.append('[hb]')
for i, t in enumerate(imp):
    d = int(t * 1000)
    f.append(f"aevalsrc='sin(2*PI*(70-40*t)*t)*exp(-7*t)':d=0.7:s=48000,volume=0.9,adelay={d}|{d}[i{i}]"); mix.append(f'[i{i}]')
    f.append(f'anoisesrc=d=0.25:c=brown:a=0.6,aresample=48000,lowpass=f=900,afade=t=out:d=0.25,adelay={d}|{d}[n{i}]'); mix.append(f'[n{i}]')
for c in range(1, 6):
    d = c * 10000 - 450
    f.append(f'anoisesrc=d=0.6:c=pink:a=0.5,aresample=48000,highpass=f=300,lowpass=f=3000,afade=t=in:d=0.4,afade=t=out:st=0.4:d=0.2,adelay={d}|{d}[w{c}]'); mix.append(f'[w{c}]')
f.append('anoisesrc=d=1.1:c=white:a=0.35,aresample=48000,bandpass=f=2500:w=1800,tremolo=f=38:d=0.9,afade=t=out:st=0.7:d=0.4,adelay=55400|55400[rip]'); mix.append('[rip]')
f.append(''.join(mix) + f'amix=inputs={len(mix)}:normalize=0:duration=longest,atrim=0:60,afade=t=out:st=58.6:d=1.4,alimiter=limit=0.9[a]')
subprocess.run(['ffmpeg', '-v', 'error', '-y', *ins, '-filter_complex', ';'.join(f), '-map', '0:v', '-map', '[a]', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', '-t', '60', OUT], check=True)
