// Sound effects made with the browser's built-in synthesizer, so there are no audio files to load.
// The on/off choice is remembered in this browser.

const KEY = 'tl_sound';
let ctx: AudioContext | null = null;
let on = true;

try {
  on = localStorage.getItem(KEY) !== 'off';
} catch {
  /* storage blocked: stay on */
}

export const isSoundOn = () => on;

export function setSoundOn(next: boolean) {
  on = next;
  try {
    localStorage.setItem(KEY, next ? 'on' : 'off');
  } catch {
    /* ignore */
  }
}

function audio(): AudioContext | null {
  if (!on) return null;
  try {
    if (!ctx) {
      const AC = window.AudioContext ?? (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      ctx = new AC();
    }
    if (ctx.state === 'suspended') void ctx.resume();
    return ctx;
  } catch {
    return null;
  }
}

/** One short note. endFreq slides the pitch for "whoosh" or "thunk" effects. */
function tone(freq: number, delay: number, length: number, type: OscillatorType, volume: number, endFreq?: number) {
  const a = audio();
  if (!a) return;
  const t0 = a.currentTime + delay;
  const osc = a.createOscillator();
  const gain = a.createGain();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, t0);
  if (endFreq) osc.frequency.exponentialRampToValueAtTime(endFreq, t0 + length);
  gain.gain.setValueAtTime(0.0001, t0);
  gain.gain.exponentialRampToValueAtTime(volume, t0 + 0.012);
  gain.gain.exponentialRampToValueAtTime(0.0001, t0 + length);
  osc.connect(gain).connect(a.destination);
  osc.start(t0);
  osc.stop(t0 + length + 0.02);
}

const PENTATONIC = [0, 2, 4, 7, 9];
const note = (semitones: number) => 523.25 * Math.pow(2, semitones / 12); // C5 and up

export const sfx = {
  /** Call from a tap or click so the browser allows sound later. */
  unlock() {
    audio();
  },
  /** n = how many pieces laid in this drag. The pitch climbs a scale as the track grows. */
  place(n: number) {
    const i = Math.max(0, n - 1);
    const octave = Math.min(2, Math.floor(i / 5));
    tone(note(PENTATONIC[i % 5] + octave * 12 - 12), 0, 0.11, 'triangle', 0.2);
  },
  erase() {
    tone(320, 0, 0.09, 'sine', 0.16, 190);
  },
  mark() {
    tone(240, 0, 0.06, 'square', 0.05);
  },
  unmark() {
    tone(190, 0, 0.06, 'square', 0.04);
  },
  click() {
    tone(660, 0, 0.05, 'sine', 0.1);
  },
  thunk() {
    tone(170, 0, 0.14, 'sine', 0.25, 80);
  },
  /** Puzzle solved: a rising run, then a held top note. */
  solve() {
    [0, 4, 7, 12].forEach((s, i) => tone(note(s), i * 0.09, i === 3 ? 0.45 : 0.14, 'triangle', 0.22));
    tone(note(16), 0.36, 0.5, 'sine', 0.1);
  },
  soundOn() {
    tone(note(0), 0, 0.08, 'triangle', 0.18);
    tone(note(7), 0.08, 0.12, 'triangle', 0.18);
  },
};
