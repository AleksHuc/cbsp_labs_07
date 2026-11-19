const slider = document.querySelector("#request");
const requestValue = document.querySelector("#request-value");
const buttons = [...document.querySelectorAll("[data-publication]")];
const eventRows = [...document.querySelectorAll("[data-time]")];
const measureRows = [...document.querySelectorAll("[data-measure]")];
const scale = document.querySelector("#time-scale");
const error = document.querySelector("#error");
const chart = document.querySelector(".latency-chart");
const replayButton = document.querySelector("#replay");
const number = new Intl.NumberFormat("en");
const ms = value => `${number.format(value)} ms`;

let publication = "full";
let activeRequest;
let timeline;
let audioContext;
let excerpt;
let replay = null;

function updateControls(requestMs) {
  if (requestMs !== undefined) {
    requestValue.value = ms(requestMs);
    slider.setAttribute("aria-valuetext", requestValue.value);
  }
  for (const button of buttons) {
    button.setAttribute("aria-pressed", button.dataset.publication === publication);
  }
}

async function update(requestMs) {
  stopReplay();
  replayButton.disabled = true;
  activeRequest?.abort();
  const request = new AbortController();
  activeRequest = request;
  error.hidden = true;
  updateControls(requestMs);

  try {
    const query = requestMs === undefined ? "" : `?publication=${publication}&request=${requestMs}`;
    const response = await fetch(`/api/latency${query}`, { signal: request.signal });
    if (!response.ok) {
      throw new Error(await response.text());
    }
    const model = await response.json();
    if (!request.signal.aborted) {
      render(model);
    }
  } catch (exception) {
    if (!request.signal.aborted) {
      error.textContent = `Could not update the timeline. ${exception.message}`;
      error.hidden = false;
    }
  }
}

function render(model) {
  timeline = model;
  replayButton.disabled = false;
  publication = model.publication;
  slider.max = model.maxRequestMs;
  slider.value = model.requestMs;
  slider.disabled = false;
  document.querySelector("#request-max").textContent = ms(model.maxRequestMs);
  for (const button of buttons) {
    button.disabled = false;
  }
  updateControls(model.requestMs);

  const percent = time => `${time / model.timelineEndMs * 100}%`;
  for (const row of eventRows) {
    const timestamp = model[row.dataset.time];
    row.querySelector("i").style.left = percent(timestamp);
    row.querySelector("output").value = ms(timestamp);
  }
  for (const row of measureRows) {
    const measure = row.dataset.measure;
    const from = measure === "startupMs" ? model.requestMs : model.captureMs;
    const bar = row.querySelector("i");
    bar.style.left = percent(from);
    bar.style.width = percent(model.playoutMs - from);
    row.querySelector("output").value = ms(model[measure]);
  }

  const ticks = Array.from({ length: 5 }, (_, index) => {
    const time = model.timelineEndMs * index / 4;
    const tick = document.createElement("span");
    tick.textContent = `${time / 1_000} s`;
    tick.style.left = percent(time);
    return tick;
  });
  scale.replaceChildren(...ticks);
}

function clickSound(frequency) {
  const rate = audioContext.sampleRate;
  const buffer = audioContext.createBuffer(1, Math.round(rate * 0.07), rate);
  const samples = buffer.getChannelData(0);
  for (let i = 0; i < samples.length; i++) {
    const envelope = (1 - i / samples.length) ** 2;
    samples[i] = 0.32 * envelope * Math.sin(2 * Math.PI * frequency * i / rate);
  }
  return buffer;
}

function stopReplay() {
  const sources = replay;
  replay = null;
  for (const source of sources ?? []) {
    source.stop();
  }
  chart.removeAttribute("data-playing");
  replayButton.textContent = "Play timeline";
}

async function toggleReplay() {
  if (replay !== null) {
    stopReplay();
    return;
  }

  const current = new Set();
  const model = timeline;
  replay = current;
  replayButton.textContent = "Stop playback";
  error.hidden = true;
  try {
    audioContext ??= new AudioContext();
    await audioContext.resume();
    if (!excerpt) {
      const response = await fetch("/excerpt.wav");
      if (!response.ok) {
        throw new Error("The audio excerpt is unavailable.");
      }
      excerpt = await audioContext.decodeAudioData(await response.arrayBuffer());
    }
    if (replay !== current) return;

    const sounds = [
      [model.captureMs, clickSound(320)],
      [model.requestMs, clickSound(880)],
      [model.playoutMs, excerpt]
    ];
    // Use the audio clock for the cues and the visual playhead.
    const start = audioContext.currentTime + 0.04;
    for (const [timeMs, buffer] of sounds) {
      const source = audioContext.createBufferSource();
      source.buffer = buffer;
      source.connect(audioContext.destination);
      source.addEventListener("ended", () => {
        current.delete(source);
        if (replay === current && current.size === 0) stopReplay();
      }, { once: true });
      source.start(start + timeMs / 1000);
      current.add(source);
    }

    const advance = () => {
      if (replay !== current) return;
      const elapsedMs = Math.max(0, (audioContext.currentTime - start) * 1000);
      chart.style.setProperty("--playhead", `${Math.min(100, elapsedMs / model.timelineEndMs * 100)}%`);
      requestAnimationFrame(advance);
    };
    advance();
    chart.setAttribute("data-playing", "");
  } catch (exception) {
    if (replay !== current) return;
    stopReplay();
    error.textContent = `Could not play the timeline. ${exception.message}`;
    error.hidden = false;
  }
}

replayButton.addEventListener("click", toggleReplay);
slider.addEventListener("input", () => update(Number(slider.value)));
for (const button of buttons) {
  button.addEventListener("click", () => {
    publication = button.dataset.publication;
    update(Number(slider.value));
  });
}
update();
