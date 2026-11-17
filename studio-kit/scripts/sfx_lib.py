"""Synthesised sound effects, all free and offline (numpy only).
  python sfx_lib.py <name> out.wav     names: whoosh heartbeat flatline bed_sea bed_night pluck bell hit riser
  python sfx_lib.py list
Every file is 48 kHz mono 16-bit. Mix them with mix.py."""
import sys, wave
import numpy as np
SR = 48000
rng = np.random.default_rng(7)
def lp(x, a):
    y = np.empty_like(x); s = 0.0
    for i, v in enumerate(x): s += a * (v - s); y[i] = s
    return y
def norm(x, peak=0.8): return x / (np.abs(x).max() + 1e-9) * peak
def whoosh(d=0.45):
    w = rng.normal(0, 1, int(d * SR)); env = np.sin(np.linspace(0, np.pi, len(w))) ** 2
    return norm((w - lp(w, 0.08)) * env)
def hit(d=0.35):
    t = np.arange(int(d * SR)) / SR; return norm(np.sin(2*np.pi*(60-30*t/d)*t) * np.exp(-9*t))
def heartbeat():
    t = np.arange(int(0.6 * SR)) / SR
    a = np.sin(2*np.pi*58*t) * np.exp(-28*t); b = np.sin(2*np.pi*50*t) * np.exp(-30*np.clip(t-0.19, 0, None)) * (t > .19)
    return norm(a * 1.2 + b)
def flatline(d=1.9):
    t = np.arange(int(d * SR)) / SR; return norm(np.sin(2*np.pi*880*t) * np.minimum(1, t*40) * np.minimum(1, (d-t)*6), .15)
def bed_sea(d=20):
    t = np.arange(int(d * SR)) / SR; w = lp(rng.normal(0, 1, len(t)), 0.012) * (0.6 + 0.4*np.sin(2*np.pi*0.11*t)); return norm(w, .35)
def bed_night(d=20):
    t = np.arange(int(d * SR)) / SR; w = lp(rng.normal(0, 1, len(t)), 0.02) * (0.6 + 0.4*np.sin(2*np.pi*0.17*t))
    c = np.sin(2*np.pi*4300*t) * (np.sin(2*np.pi*38*t) > .2) * (np.sin(2*np.pi*1.3*t) > .55) * .02
    return norm(w, .4) + c
def pluck(f=293.66, d=1.6, damp=0.996):
    p = int(SR / f); buf = rng.uniform(-1, 1, p); n = int(d * SR); y = np.empty(n)
    for i in range(n): y[i] = buf[i % p]; buf[i % p] = damp * 0.5 * (buf[i % p] + buf[(i+1) % p])
    return norm(y, .5)
def bell(f=587.33, d=1.4):
    t = np.arange(int(d * SR)) / SR
    return norm((np.sin(2*np.pi*f*t) + .4*np.sin(2*np.pi*f*2.76*t)*np.exp(-6*t)) * np.exp(-3.2*t), .5)
def riser(d=1.5):
    t = np.arange(int(d * SR)) / SR; return norm(np.sin(2*np.pi*(200+900*(t/d)**2)*t) * (t/d), .4)
FX = dict(whoosh=whoosh, hit=hit, heartbeat=heartbeat, flatline=flatline, bed_sea=bed_sea, bed_night=bed_night, pluck=pluck, bell=bell, riser=riser)
def save(path, x):
    w = wave.open(path, "wb"); w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((np.clip(x, -1, 1) * 32767).astype("<i2").tobytes()); w.close()
if __name__ == "__main__":
    if len(sys.argv) < 2 or sys.argv[1] == "list": print(" ".join(FX)); sys.exit()
    save(sys.argv[2], FX[sys.argv[1]]())
