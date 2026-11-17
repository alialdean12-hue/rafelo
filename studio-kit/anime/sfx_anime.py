import numpy as np, wave, sys
SR=48000; N=SR*8; out=np.zeros(N)
rng=np.random.default_rng(7)
def add(sig,t,g=1.0):
    i=int(t*SR); n=min(len(sig),N-i)
    if n>0: out[i:i+n]+=sig[:n]*g
def pluck(f,dur=1.6,damp=0.996):
    p=int(SR/f); buf=rng.uniform(-1,1,p); n=int(dur*SR); y=np.empty(n)
    for i in range(n):
        y[i]=buf[i%p]; buf[i%p]=damp*0.5*(buf[i%p]+buf[(i+1)%p])
    return y
def bell(f,dur=1.4):
    t=np.arange(int(dur*SR))/SR
    return (np.sin(2*np.pi*f*t)+0.4*np.sin(2*np.pi*f*2.76*t)*np.exp(-6*t))*np.exp(-3.2*t)
def lp(x,a):
    y=np.empty_like(x); s=0.0
    for i,v in enumerate(x):
        s+=a*(v-s); y[i]=s
    return y
# night bed: soft wind + crickets
t=np.arange(N)/SR
wind=lp(rng.normal(0,1,N),0.02)*(0.6+0.4*np.sin(2*np.pi*0.17*t))
out+=wind*0.5
crick=np.sin(2*np.pi*4300*t)*(np.sin(2*np.pi*38*t)>0.2)*(np.sin(2*np.pi*1.3*t)>0.55)
out+=crick*0.012
# koto-like opening phrase (D minor pentatonic), establish
D4,F4,G4,A4,C5,D5,F5,A5=293.66,349.23,392.0,440.0,523.25,587.33,698.46,880.0
for f,tm in [(D4,0.15),(A4,0.75),(G4,1.35),(D5,1.85)]: add(pluck(f),tm,0.32)
# anticipation: low pluck, then whoosh on the release
add(pluck(146.83,1.2),3.0,0.35)
w=rng.normal(0,1,int(0.45*SR)); env=np.sin(np.linspace(0,np.pi,len(w)))**2
w=(w-lp(w,0.08))*env; add(w,3.18,0.28)
# lantern rises: bell arpeggio
for k,f in enumerate([D5,F5,A5,D5*2]): add(bell(f),3.42+k*0.2,0.16)
# button: closing notes
for f,tm in [(A4,6.05),(F4,6.55),(D4,7.05)]: add(pluck(f,1.4),tm,0.26)
add(bell(D5,1.2),7.05,0.08)
fade=np.ones(N); k=int(0.35*SR); fade[-k:]=np.linspace(1,0,k); fade[:int(0.2*SR)]=np.linspace(0,1,int(0.2*SR))
out*=fade; out/=max(1e-9,np.abs(out).max())/0.7
w=wave.open(sys.argv[1],'wb'); w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((out*32767).astype('<i2').tobytes()); w.close()
