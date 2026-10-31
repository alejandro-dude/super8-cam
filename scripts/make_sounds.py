#!/usr/bin/env python3
"""Synthesizes two short, original SFX (no external audio used):
  - shutter_click.wav : a brief mechanical click
  - film_wind.wav      : a looping film-advance motor whir with clicks

Both are procedurally generated and placed in app/src/main/res/raw/.
"""
import numpy as np
import wave
import os

SR = 44100

def to_pcm16(x):
    x = np.clip(x, -1.0, 1.0)
    return (x * 32767).astype(np.int16)

def write_wav(path, samples, sr=SR):
    pcm = to_pcm16(samples)
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(pcm.tobytes())

def make_click(duration=0.07, freq=1800, decay=28):
    n = int(SR * duration)
    t = np.arange(n) / SR
    noise = np.random.default_rng(1).uniform(-1, 1, n)
    tone = np.sin(2 * np.pi * freq * t)
    env = np.exp(-decay * t)
    sig = (0.6 * noise + 0.4 * tone) * env
    return sig

def make_wind_loop(duration=2.0, motor_hz=140, click_rate_hz=18):
    n = int(SR * duration)
    t = np.arange(n) / SR
    rng = np.random.default_rng(2)

    # Motor hum: a few harmonics of a low frequency, amplitude-modulated
    # slightly for a mechanical, non-electronic feel.
    motor = (
        0.5 * np.sin(2 * np.pi * motor_hz * t)
        + 0.25 * np.sin(2 * np.pi * motor_hz * 2 * t)
        + 0.12 * np.sin(2 * np.pi * motor_hz * 3 * t)
    )
    wobble = 1.0 + 0.04 * np.sin(2 * np.pi * 6 * t)
    motor *= wobble

    # Filtered noise bed for the mechanical "whirr" texture.
    noise = rng.uniform(-1, 1, n)
    kernel = np.ones(25) / 25
    noise = np.convolve(noise, kernel, mode="same")

    # Sprocket clicks at a steady rate (like film teeth pulling down frames).
    clicks = np.zeros(n)
    click_period = SR / click_rate_hz
    click_len = int(SR * 0.004)
    i = 0.0
    while i < n:
        idx = int(i)
        end = min(idx + click_len, n)
        length = end - idx
        if length > 0:
            env = np.exp(-60 * np.arange(length) / SR)
            clicks[idx:end] += 0.5 * env * rng.uniform(-1, 1, length)
        i += click_period

    sig = 0.5 * motor + 0.25 * noise + clicks

    # Ensure a seamless loop: crossfade the tail into the head.
    fade = int(SR * 0.05)
    sig[:fade] = sig[:fade] * (np.arange(fade) / fade) + sig[-fade:] * (1 - np.arange(fade) / fade)
    sig = sig[: n - 0]  # keep length; loop point is handled by the crossfade above

    # Normalize to a sensible level.
    sig = sig / (np.max(np.abs(sig)) + 1e-6) * 0.85
    return sig

def main():
    out_dir = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
    out_dir = os.path.abspath(out_dir)
    os.makedirs(out_dir, exist_ok=True)

    click = make_click()
    write_wav(os.path.join(out_dir, "shutter_click.wav"), click)

    wind = make_wind_loop()
    write_wav(os.path.join(out_dir, "film_wind.wav"), wind)

    print("Wrote:", os.path.join(out_dir, "shutter_click.wav"))
    print("Wrote:", os.path.join(out_dir, "film_wind.wav"))

if __name__ == "__main__":
    main()
