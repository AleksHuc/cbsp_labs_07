export const playoutEvent = event => event.action === "PLAYED" || event.action === "CLEANUP";
export const noteKey = event => `${event.channel}:${event.note}`;

function noteIntervals(events) {
  const active = new Map();
  const intervals = [];
  for (const event of events) {
    if (!playoutEvent(event)) continue;
    const key = noteKey(event);
    if (active.has(key)) {
      intervals.push({ attack: active.get(key), endMs: event.timeMs });
    }
    if (event.noteOn) active.set(key, event);
    else active.delete(key);
  }
  // A capture can end mid-note, so the replay ends any remaining sound there.
  for (const attack of active.values()) {
    intervals.push({ attack, endMs: events.at(-1).timeMs });
  }
  return intervals;
}

export class MidiSound {
  #context;
  #voices = new Set();

  async enable() {
    this.#context ??= new AudioContext();
    if (this.#context.state !== "running") {
      await this.#context.resume();
    }
  }

  schedule(events, fromMs, rate) {
    const context = this.#context;
    const origin = context.currentTime + 0.025;
    const toAudioTime = ms => origin + (ms - fromMs) / (1_000 * rate);

    for (const { attack, endMs } of noteIntervals(events)) {
      const startMs = Math.max(attack.timeMs, fromMs);
      if (endMs <= startMs) continue;
      this.#play(attack, toAudioTime(startMs), toAudioTime(endMs));
    }
    return () => Math.max(0, (context.currentTime - origin) * 1_000 * rate);
  }

  #play(event, start, end) {
    const context = this.#context;
    const oscillator = context.createOscillator();
    const envelope = context.createGain();
    const level = 0.13 * event.velocity / 127;
    oscillator.type = "triangle";
    oscillator.frequency.setValueAtTime(440 * 2 ** ((event.note - 69) / 12), start);
    envelope.gain.setValueAtTime(0, start);
    envelope.gain.linearRampToValueAtTime(level, start + Math.min(0.008, (end - start) / 2));
    envelope.gain.setValueAtTime(level, end);
    envelope.gain.linearRampToValueAtTime(0, end + 0.015);
    oscillator.connect(envelope).connect(context.destination);
    oscillator.addEventListener("ended", () => {
      this.#voices.delete(oscillator);
      oscillator.disconnect();
      envelope.disconnect();
    }, { once: true });
    oscillator.start(start);
    this.#voices.add(oscillator);
    oscillator.stop(end + 0.015);
  }

  stop() {
    for (const oscillator of this.#voices) {
      oscillator.stop(this.#context.currentTime);
    }
    this.#voices.clear();
  }
}
