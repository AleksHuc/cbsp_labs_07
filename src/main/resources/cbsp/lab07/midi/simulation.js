import { MidiSound, noteKey, playoutEvent } from "/midi-sound.js";

const taskButtons = [...document.querySelectorAll("[data-task]")];
const record = document.querySelector("#record");
const play = document.querySelector("#play");
const step = document.querySelector("#step");
const soundControl = document.querySelector("#sound-control");
const soundToggle = document.querySelector("#sound");
const audioStatus = document.querySelector("#audio-status");
const slider = document.querySelector("#position");
const clock = document.querySelector("#clock");
const countLabel = document.querySelector("#count");
const status = document.querySelector("#status");
const error = document.querySelector("#error");
const current = document.querySelector("#current");
const queue = document.querySelector("#queue");
const notes = document.querySelector("#notes");
const ruler = document.querySelector(".midi-ruler");
const lanes = document.querySelector(".midi-lanes");
const action = {
  SENT: ["Datagram sent", "sender"], ARRIVED: ["Datagram received", "receiver"],
  SCHEDULED: ["Deadline calculated", "scheduler"], QUEUED: ["Added to queue", "scheduler"],
  PLAYED: ["Played", "playout"], OMITTED: ["Note Off omitted by sender", "sender"],
  CLEANUP: ["Released during cleanup", "playout"],
};
const lesson = {
  6: "Inspect the received datagram to see its 12-byte payload.",
  7: "Follow each arrival through decoding to its local deadline.",
  8: "Watch the playout queue hand off notes at their deadlines.",
  9: "Follow the held note until the receiver releases it during cleanup.",
};
const noteNames = ["C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"];
const ms = time => `${time.toFixed(2)} ms`;
const sound = new MidiSound();
const replayRate = 0.25;

let task;
let events = [];
let markers = [];
let position = 0;
let time = 0;
let axisEnd = 1000;
let frame = 0;
let replay = null;
let loading = false;

function show(taskNumber) {
  task = taskNumber;
  for (const button of taskButtons) {
    button.setAttribute("aria-pressed", Number(button.dataset.task) === task);
  }
  document.querySelector("#queue-card").hidden = !(task >= 7);
  document.querySelector("#notes-card").hidden = !(task >= 8);
  document.querySelector("#queue-heading").textContent = task === 7 ? "Calculated deadlines" : "Playback queue";
  soundControl.hidden = task < 8;
}

async function run(taskNumber) {
  stop();
  loading = true;
  error.hidden = true;
  audioStatus.hidden = true;
  events = [];
  if (taskNumber !== undefined) {
    show(taskNumber);
  }
  status.textContent = "Recording the MIDI session…";
  drawTrace();
  seek(0, 0, true);
  updateControls();
  try {
    const query = taskNumber === undefined ? "" : `?task=${taskNumber}`;
    const response = await fetch(`/api/trace${query}`);
    if (!response.ok) {
      throw new Error(await response.text());
    }
    const trace = await response.json();
    show(trace.task);
    events = trace.events;
    status.textContent = lesson[task];
    drawTrace();
    seek(0, 0, true);
  } catch (exception) {
    status.textContent = "Recording unavailable";
    error.textContent = exception.message;
    error.hidden = false;
  } finally {
    loading = false;
    updateControls();
  }
}

function drawTrace() {
  axisEnd = Math.max(1000, Math.ceil((events.at(-1)?.timeMs ?? 0) / 250) * 250);
  for (const lane of lanes.querySelectorAll("[data-lane]")) {
    lane.replaceChildren();
  }
  markers = events.map(event => {
    const [label, lane] = action[event.action];
    const marker = document.createElement("span");
    marker.className = "midi-marker";
    marker.dataset.action = event.action;
    marker.style.left = percent(event.timeMs);
    marker.title = `${label} · ${ms(event.timeMs)}`;
    lanes.querySelector(`[data-lane="${lane}"]`).append(marker);
    return marker;
  });
  ruler.replaceChildren(...Array.from({ length: 5 }, (_, i) => {
    const tick = document.createElement("span");
    const value = i * axisEnd / 4;
    tick.textContent = value;
    tick.style.left = percent(value);
    return tick;
  }));
  slider.max = events.length;
}

function percent(value) {
  return `${value / axisEnd * 100}%`;
}

function seek(next, nextTime = events[next - 1]?.timeMs ?? 0, force = false) {
  const changed = position !== next;
  position = next;
  time = nextTime;
  clock.value = ms(time);
  lanes.style.setProperty("--playhead", percent(time));
  if (changed || force) {
    slider.value = position;
    countLabel.value = `${position} / ${events.length}`;
    slider.setAttribute("aria-valuetext", `Event ${position} of ${events.length}`);
    markers.forEach((marker, i) => marker.toggleAttribute("data-seen", i < position));
    renderState();
    updateControls();
  }
}

function renderState() {
  const visible = events.slice(0, position);
  const waiting = new Map();
  const held = new Map();
  for (const event of visible) {
    if (event.action === "SCHEDULED" || event.action === "QUEUED") {
      waiting.set(event.sequence, event);
    } else if (playoutEvent(event)) {
      waiting.delete(event.sequence);
      const key = noteKey(event);
      if (event.noteOn) {
        held.set(key, event);
      } else {
        held.delete(key);
      }
    }
  }
  const latest = visible.at(-1);
  if (latest) {
    const event = detail(action[latest.action][0], `${noteType(latest)} · ${noteLabel(latest)}`);
    current.replaceChildren(...event.childNodes);
    if (latest.sequence >= 0) {
      const fields = document.createElement("p");
      fields.className = "empty";
      fields.textContent = `Event ${latest.sequence} · Media time ${latest.mediaMs} ms · Velocity ${latest.velocity}`;
      current.append(fields);
    }
    if (latest.deadlineMs !== null) {
      const deadline = document.createElement("p");
      deadline.textContent = `Deadline ${ms(latest.deadlineMs)}`;
      current.append(deadline);
    }
    if (latest.payloadHex !== null) {
      const bytes = document.createElement("code");
      bytes.textContent = latest.payloadHex.match(/../g)?.join(" ") ?? "";
      current.append(bytes);
    }
  } else {
    current.replaceChildren(empty("p", "Step forward or play the trace."));
  }

  const ordered = [...waiting.values()].sort((a, b) => a.deadlineMs - b.deadlineMs || a.sequence - b.sequence);
  queue.replaceChildren(...(ordered.length ? ordered.map(event =>
    detail(`${noteType(event)} · ${noteLabel(event)}`, `Due ${ms(event.deadlineMs)}`)) : [empty("li", "Empty")]));
  notes.replaceChildren(...(held.size ? [...held.values()].map(event =>
    detail(noteLabel(event), `Since ${ms(event.timeMs)}`)) : [empty("li", "None")]));
}

function noteType(event) {
  return event.noteOn ? "Note On" : "Note Off";
}

function noteLabel(event) {
  const name = `${noteNames[event.note % 12]}${Math.floor(event.note / 12) - 1}`;
  return `${name} (MIDI ${event.note}), ch. ${event.channel}`;
}

function detail(title, description) {
  const item = document.createElement("li");
  const heading = document.createElement("strong");
  const text = document.createElement("small");
  heading.textContent = title;
  text.textContent = description;
  item.append(heading, text);
  return item;
}

function empty(tag, content) {
  const item = document.createElement(tag);
  item.className = "empty";
  item.textContent = content;
  return item;
}

function updateControls() {
  for (const button of taskButtons) {
    button.disabled = loading;
  }
  record.disabled = loading || task === undefined;
  play.disabled = loading || events.length === 0;
  step.disabled = loading || position >= events.length;
  slider.disabled = loading || events.length === 0;
  soundToggle.disabled = loading || replay !== null;
  play.textContent = replay === null ? "Replay at ¼ speed" : "Pause";
}

function stop(finish = false) {
  cancelAnimationFrame(frame);
  frame = 0;
  replay = null;
  if (!finish) sound.stop();
  updateControls();
}

async function togglePlay() {
  if (replay !== null) {
    stop();
    return;
  }
  if (position === events.length) {
    seek(0);
  }
  const start = time;
  const end = events.at(-1).timeMs;
  const currentReplay = {};
  replay = currentReplay;
  audioStatus.hidden = true;
  updateControls();

  sound.stop();
  let elapsedMs;
  if (task >= 8 && soundToggle.checked) {
    try {
      await sound.enable();
      if (replay !== currentReplay) return;
      elapsedMs = sound.schedule(events, start, replayRate);
    } catch {
      if (replay !== currentReplay) return;
      sound.stop();
      audioStatus.textContent = "Audio could not start. Continuing with the visual replay.";
      audioStatus.hidden = false;
    }
  }
  const wallStart = performance.now();
  elapsedMs ??= () => (performance.now() - wallStart) * replayRate;
  const advance = () => {
    const elapsed = Math.min(end, start + elapsedMs());
    let next = position;
    while (next < events.length && events[next].timeMs <= elapsed) {
      next++;
    }
    seek(next, elapsed);
    if (next === events.length) {
      stop(true);
    } else {
      frame = requestAnimationFrame(advance);
    }
  };
  frame = requestAnimationFrame(advance);
  updateControls();
}

for (const button of taskButtons) {
  button.addEventListener("click", () => run(Number(button.dataset.task)));
}
record.addEventListener("click", () => run(task));
slider.addEventListener("input", () => { stop(); seek(Number(slider.value)); });
step.addEventListener("click", () => { stop(); seek(position + 1); });
play.addEventListener("click", togglePlay);
window.addEventListener("pagehide", () => stop());
run();
