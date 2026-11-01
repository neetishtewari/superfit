# Makes app/src/main/res/raw/recap_music.ogg: an original 40 s loop (96 bpm, Fmaj7-Em7-Dm7-Cmaj7),
# synthesised from scratch so Superfit owns it outright. Run: python3 recap_music.py && ffmpeg -i recap.wav -c:a libvorbis -q:a 4 recap_music.ogg
import numpy as np, wave, sys
sr=44100; bpm=96; beat=60/bpm; bars=16; N=int(round(bars*4*beat*sr))
rng=np.random.default_rng(7)
out=np.zeros((2,N))
def midi(n): return 440*2**((n-69)/12)
def add(sig,start,ch_gain=(1,1)):
    s=int(start*sr)%N; L=len(sig)
    idx=(np.arange(L)+s)%N
    np.add.at(out[0],idx,sig*ch_gain[0]); np.add.at(out[1],idx,sig*ch_gain[1])
# progression Fmaj7, Em7, Dm7, Cmaj7(add9) - 2 bars each
chords=[[53,57,60,64],[52,55,59,62],[50,53,57,60],[48,55,59,62]]
roots=[41,40,38,36]
barlen=4*beat
for rep in range(2):
  for ci,ch in enumerate(chords):
    t0=(rep*8+ci*2)*barlen; dur=2*barlen
    # pad
    t=np.arange(int((dur+1.5)*sr))/sr
    env=np.minimum(1,t/0.8)*np.where(t<dur,1,np.exp(-(t-dur)*3))
    for n in ch:
      for det,pan in ((-0.07,(0.9,0.5)),(0.07,(0.5,0.9))):
        f=midi(n+12)*2**(det/12)
        v=np.sin(2*np.pi*f*t+0.3*np.sin(2*np.pi*0.3*t))+0.25*np.sin(4*np.pi*f*t)+0.08*np.sin(6*np.pi*f*t)
        add(0.035*v*env,t0,pan)
    # bass: beats 1, 2.5, 3 style
    for b in (0,1.5,2,3.5,4,5.5,6,7.5):
      tb=np.arange(int(0.6*sr))/sr
      f=midi(roots[ci])
      e=np.minimum(1,tb/0.01)*np.exp(-tb*3.5)
      add(0.22*(np.sin(2*np.pi*f*tb)+0.3*np.sin(4*np.pi*f*tb))*e,t0+b*beat)
    # arp (second half and lightly in first)
    pat=[0,1,2,3,2,1,3,2]
    for step in range(16):
      if rep==0 and step%2==1: continue
      n=ch[pat[step%8]]+24
      tb=np.arange(int(1.2*sr))/sr
      f=midi(n); s=np.zeros_like(tb)
      for h in range(1,6): s+=np.sin(2*np.pi*f*h*tb)/h**1.6*np.exp(-tb*(4+3*h))
      s*=np.minimum(1,tb/0.004)
      pan=(0.8,0.5) if step%2 else (0.5,0.8)
      add((0.07 if rep else 0.05)*s,t0+step*beat/2,pan)
# drums
for barI in range(bars):
  t0=barI*barlen
  for b in range(4):
    if b in (0,2):
      tb=np.arange(int(0.4*sr))/sr
      ph=2*np.pi*np.cumsum(45+75*np.exp(-tb*30))/sr
      add(0.32*np.sin(ph)*np.exp(-tb*9),t0+b*beat)
    if b in (1,3):
      tb=np.arange(int(0.25*sr))/sr
      nz=np.diff(rng.standard_normal(len(tb)+1))
      add(0.05*nz*np.exp(-tb*22),t0+b*beat)
    for h in (0,0.5):
      tb=np.arange(int(0.06*sr))/sr
      nz=np.diff(np.diff(rng.standard_normal(len(tb)+2)))
      add((0.012 if h==0 else 0.02)*nz*np.exp(-tb*70),t0+(b+h)*beat,(0.6,0.9))
# circular reverb
L=int(2.2*sr); tb=np.arange(L)/sr
for c in range(2):
  ir=rng.standard_normal(L)*np.exp(-tb*2.6); ir[:int(0.02*sr)]=0
  ir/=np.sqrt((ir**2).sum())
  irp=np.zeros(N); irp[:L]=ir
  wet=np.real(np.fft.ifft(np.fft.fft(out[c])*np.fft.fft(irp)))
  out[c]=out[c]+0.28*wet
out/=np.abs(out).max()/0.7
pcm=(out.T*32767).astype(np.int16)
w=wave.open('recap.wav','wb'); w.setnchannels(2); w.setsampwidth(2); w.setframerate(sr); w.writeframes(pcm.tobytes()); w.close()
print(N/sr)
