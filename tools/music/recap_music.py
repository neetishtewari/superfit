# Makes the monthly recap tracks app/src/main/res/raw/recap_music_01.ogg .. _12.ogg: one original
# 40-ish second loop per month, synthesised from scratch so Superfit owns them outright.
# Each month has its own key, tempo, chords, groove and sound, with a mood that fits it
# (January fresh start, June sunny, October cosy, December festive).
# Run from this folder: python3 recap_music.py  (needs numpy and ffmpeg)
import numpy as np, wave, subprocess, os, sys

SR = 44100

# name, bpm, key shift, chord progression (semitones from C, chord tones), arp pattern, sound, drums
MAJ7 = lambda r: [r, r + 4, r + 7, r + 11]
MIN7 = lambda r: [r, r + 3, r + 7, r + 10]
ADD9 = lambda r: [r, r + 4, r + 7, r + 14]
SUS2 = lambda r: [r, r + 2, r + 7, r + 12]
MAJ = lambda r: [r, r + 4, r + 7, r + 12]
MIN = lambda r: [r, r + 3, r + 7, r + 12]

MONTHS = {
    1: dict(bpm=104, key=2, prog=[ADD9(0), SUS2(7), MIN7(9), MAJ7(5)], arp=[0, 2, 1, 3, 2, 1, 3, 2], bright=1.0, drums="steady"),   # fresh start
    2: dict(bpm=88, key=5, prog=[MAJ7(0), MIN7(9), MIN7(2), ADD9(7)], arp=[0, 1, 2, 3, 3, 2, 1, 0], bright=0.6, drums="soft"),     # warm
    3: dict(bpm=100, key=0, prog=[MAJ(0), MAJ(7), MIN(9), MAJ(5)], arp=[0, 1, 2, 1, 3, 1, 2, 1], bright=0.9, drums="steady"),      # spring
    4: dict(bpm=110, key=7, prog=[SUS2(0), ADD9(5), MIN7(9), SUS2(7)], arp=[0, 2, 3, 2, 1, 2, 3, 2], bright=1.0, drums="bouncy"),  # bright
    5: dict(bpm=96, key=4, prog=[MAJ7(5), MAJ7(0), MIN7(4), MIN7(9)], arp=[3, 2, 1, 0, 1, 2, 3, 2], bright=0.8, drums="steady"),   # easy
    6: dict(bpm=116, key=9, prog=[MAJ(0), MAJ(5), MIN(9), MAJ(7)], arp=[0, 1, 2, 3, 2, 3, 1, 2], bright=1.1, drums="bouncy"),      # sunny
    7: dict(bpm=92, key=3, prog=[MIN7(9), MAJ7(5), MAJ7(0), ADD9(7)], arp=[0, 2, 1, 2, 3, 2, 1, 2], bright=0.7, drums="soft"),     # monsoon
    8: dict(bpm=106, key=10, prog=[ADD9(0), MIN7(4), MAJ7(5), SUS2(7)], arp=[0, 1, 3, 1, 2, 1, 3, 1], bright=0.9, drums="steady"), # upbeat
    9: dict(bpm=98, key=1, prog=[MIN7(2), ADD9(7), MAJ7(0), MIN7(9)], arp=[1, 2, 3, 2, 0, 2, 3, 2], bright=0.8, drums="steady"),   # back to it
    10: dict(bpm=96, key=0, prog=[MAJ7(5), MIN7(4), MIN7(2), ADD9(0)], arp=[0, 1, 2, 3, 2, 1, 3, 2], bright=0.7, drums="steady"),  # cosy (the first track)
    11: dict(bpm=90, key=8, prog=[MAJ7(0), MAJ7(5), MIN7(2), SUS2(7)], arp=[0, 3, 2, 3, 1, 3, 2, 3], bright=0.6, drums="soft"),    # festive glow
    12: dict(bpm=112, key=6, prog=[MAJ(0), MIN(9), MAJ(5), MAJ(7)], arp=[0, 1, 2, 3, 2, 1, 2, 3], bright=1.1, drums="bouncy"),     # celebration
}


def midi(n):
    return 440 * 2 ** ((n - 69) / 12)


def render(month, m):
    rng = np.random.default_rng(month)
    beat = 60 / m["bpm"]
    bars = 16
    N = int(round(bars * 4 * beat * SR))
    out = np.zeros((2, N))

    def add(sig, start, pan=(1, 1)):
        s = int(start * SR) % N
        idx = (np.arange(len(sig)) + s) % N
        np.add.at(out[0], idx, sig * pan[0])
        np.add.at(out[1], idx, sig * pan[1])

    base = 48 + m["key"] if m["key"] < 6 else 36 + m["key"]
    barlen = 4 * beat
    b = m["bright"]
    for rep in range(2):
        for ci, ch in enumerate(m["prog"]):
            t0 = (rep * 8 + ci * 2) * barlen
            dur = 2 * barlen
            notes = [base + n for n in ch]
            t = np.arange(int((dur + 1.5) * SR)) / SR
            env = np.minimum(1, t / 0.8) * np.where(t < dur, 1, np.exp(-(t - dur) * 3))
            for n in notes:
                for det, pan in ((-0.07, (0.9, 0.5)), (0.07, (0.5, 0.9))):
                    f = midi(n + 12) * 2 ** (det / 12)
                    v = np.sin(2 * np.pi * f * t + 0.3 * np.sin(2 * np.pi * 0.3 * t)) + 0.25 * b * np.sin(4 * np.pi * f * t) + 0.08 * b * np.sin(6 * np.pi * f * t)
                    add(0.035 * v * env, t0, pan)
            root = notes[0] - 12 if notes[0] - 12 >= 36 else notes[0]
            hits = (0, 1.5, 2, 3.5, 4, 5.5, 6, 7.5) if m["drums"] != "soft" else (0, 2, 4, 6)
            for hb in hits:
                tb = np.arange(int(0.6 * SR)) / SR
                f = midi(root)
                e = np.minimum(1, tb / 0.01) * np.exp(-tb * 3.5)
                add(0.22 * (np.sin(2 * np.pi * f * tb) + 0.3 * np.sin(4 * np.pi * f * tb)) * e, t0 + hb * beat)
            for step in range(16):
                if rep == 0 and step % 2 == 1:
                    continue
                n = notes[m["arp"][step % 8]] + 24
                tb = np.arange(int(1.2 * SR)) / SR
                f = midi(n)
                s = np.zeros_like(tb)
                for h in range(1, 6):
                    s += np.sin(2 * np.pi * f * h * tb) / h ** (2.2 - 0.6 * b) * np.exp(-tb * (4 + 3 * h))
                s *= np.minimum(1, tb / 0.004)
                add((0.07 if rep else 0.05) * s, t0 + step * beat / 2, (0.8, 0.5) if step % 2 else (0.5, 0.8))
    for barI in range(bars):
        t0 = barI * barlen
        for q in range(4):
            if q in (0, 2) or (m["drums"] == "bouncy" and q == 3 and barI % 2):
                tb = np.arange(int(0.4 * SR)) / SR
                ph = 2 * np.pi * np.cumsum(45 + 75 * np.exp(-tb * 30)) / SR
                add((0.22 if m["drums"] == "soft" else 0.32) * np.sin(ph) * np.exp(-tb * 9), t0 + (q + (0.5 if q == 3 else 0)) * beat)
            if q in (1, 3) and m["drums"] != "soft":
                tb = np.arange(int(0.25 * SR)) / SR
                nz = np.diff(rng.standard_normal(len(tb) + 1))
                add(0.05 * nz * np.exp(-tb * 22), t0 + q * beat)
            for h in ((0, 0.5) if m["drums"] != "soft" else (0.5,)):
                tb = np.arange(int(0.06 * SR)) / SR
                nz = np.diff(np.diff(rng.standard_normal(len(tb) + 2)))
                add((0.012 if h == 0 else 0.02) * nz * np.exp(-tb * 70), t0 + (q + h) * beat, (0.6, 0.9))
    L = int(2.2 * SR)
    tb = np.arange(L) / SR
    for c in range(2):
        ir = rng.standard_normal(L) * np.exp(-tb * 2.6)
        ir[: int(0.02 * SR)] = 0
        ir /= np.sqrt((ir ** 2).sum())
        irp = np.zeros(N)
        irp[:L] = ir
        out[c] = out[c] + 0.28 * np.real(np.fft.ifft(np.fft.fft(out[c]) * np.fft.fft(irp)))
    out /= np.abs(out).max() / 0.7
    return (out.T * 32767).astype(np.int16)


if __name__ == "__main__":
    dest = sys.argv[1] if len(sys.argv) > 1 else "../../app/src/main/res/raw"
    for month, m in MONTHS.items():
        pcm = render(month, m)
        wav = f"/tmp/recap_{month:02d}.wav"
        with wave.open(wav, "wb") as w:
            w.setnchannels(2)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        ogg = os.path.join(dest, f"recap_music_{month:02d}.ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "0", ogg], check=True)
        os.remove(wav)
        print(ogg, os.path.getsize(ogg))
