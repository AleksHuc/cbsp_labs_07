const variantList = document.querySelector("#variants");
const variantTemplate = document.querySelector("#variant");
const segmentTemplate = document.querySelector("#segment");
const segmentList = document.querySelector("#segments");
const reload = document.querySelector("#next");
const reset = document.querySelector("#reset");
const summary = document.querySelector("#summary");
const playlistPath = document.querySelector("#playlist-path");
const removed = document.querySelector("#removed");
const error = document.querySelector("#error");
const player = document.querySelector("#playback");
const audioSegments = document.querySelector("#audio-segments");
const audioError = document.querySelector("#audio-error");
const number = new Intl.NumberFormat("en");
let variants;
let selected = 0;
let current = null;
let history = new Map();
let audioUrl = null;

async function getJson(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(await response.text());
  }
  return response.json();
}

async function start() {
  try {
    variants = await getJson("/api/variants");
    variants.forEach((variant, index) => {
      const card = variantTemplate.content.cloneNode(true);
      const radio = card.querySelector("input");
      radio.value = index;
      card.querySelector("strong").textContent = `${number.format(variant.bandwidthBps)} bit/s peak`;
      card.querySelector("small").textContent = variant.path.split("/").slice(-2).join("/");
      radio.addEventListener("change", () => loadWindow(index, "initial"));
      variantList.append(card);
    });
    await loadWindow(0, "initial");
  } catch (exception) {
    showError(exception);
  }
}

async function loadWindow(variant, snapshot) {
  setControls(true);
  error.hidden = true;
  const previous = snapshot === "initial" ? new Map() : history;
  try {
    const params = new URLSearchParams({ variant, window: snapshot, downloaded: [...previous.keys()].join(",") });
    const next = await getJson(`/api/window?${params}`);
    const present = new Set(next.segments.map(segment => segment.sequence));
    const departed = snapshot === "initial" ? [] : current.segments.filter(segment => !present.has(segment.sequence));
    const fetched = new Map(next.downloads.map(transfer => [transfer.sequence, {
      size: transfer.bytes,
      pcm: Uint8Array.from(atob(transfer.pcm), character => character.charCodeAt(0)),
    }]));
    selected = variant;
    current = next;
    history = new Map([...previous, ...fetched]);
    render(next, previous, fetched, departed);
    if (snapshot === "initial" || fetched.size > 0) {
      updateAudio(snapshot === "initial", next.previewSampleRate);
    }
  } catch (exception) {
    showError(exception);
  } finally {
    setControls(false);
  }
}

function setControls(busy) {
  reload.disabled = busy || current === null;
  reset.disabled = busy;
  for (const radio of variantList.querySelectorAll("input")) {
    radio.disabled = busy;
    radio.checked = Number(radio.value) === selected;
  }
}

function showError(exception) {
  error.textContent = `Could not load the playlist. ${exception.message}`;
  error.hidden = false;
  if (current === null) {
    summary.textContent = "Playlist unavailable";
  }
}

function render(playlist, previous, fetched, departed) {
  const count = playlist.downloads.length;
  summary.textContent = count === 0 ? "No segment downloads"
    : `${count} segment ${count === 1 ? "download" : "downloads"}`;
  playlistPath.textContent = variants[selected].path;
  removed.hidden = departed.length === 0;
  removed.textContent = departed.length === 1 ? `Segment ${departed[0].sequence} left this playlist.`
    : `Segments ${departed.map(segment => segment.sequence).join(", ")} left this playlist.`;

  const tiles = playlist.segments.map(segment => {
    const tile = segmentTemplate.content.firstElementChild.cloneNode(true);
    const sequence = segment.sequence;
    const state = fetched.has(sequence) ? (previous.has(sequence) ? "repeat" : "new")
      : previous.has(sequence) ? "cached" : "pending";
    const states = { repeat: "Downloaded again", new: "Downloaded now", cached: "Already downloaded", pending: "Awaiting download" };
    tile.dataset.sequence = sequence;
    tile.dataset.state = state;
    tile.querySelector(".segment-number").textContent = `Segment ${sequence}`;
    tile.querySelector(".segment-state").textContent = states[state];
    tile.querySelector(".segment-file").textContent = segment.path.split("/").at(-1);
    const [duration, size] = tile.querySelectorAll("dd");
    duration.textContent = `${segment.durationSeconds.toFixed(3)} s`;
    size.textContent = history.has(sequence) ? `${number.format(history.get(sequence).size)} B` : "–";
    return tile;
  });
  segmentList.replaceChildren(...tiles);
}

function updateAudio(resetPosition, sampleRate) {
  const segments = [...history.keys()].sort((a, b) => a - b);
  const position = resetPosition ? 0 : player.currentTime;
  const resume = !resetPosition && !player.paused && !player.ended;
  const previousUrl = audioUrl;
  player.pause();
  audioError.hidden = true;

  if (segments.length === 0) {
    player.removeAttribute("src");
    player.load();
    audioUrl = null;
    audioSegments.textContent = "Waiting for segments…";
  } else {
    const source = URL.createObjectURL(wave(segments.map(sequence => history.get(sequence).pcm), sampleRate));
    audioUrl = source;
    const loaded = () => {
      if (audioUrl !== source) return;
      if (position > 0) {
        player.currentTime = Math.min(position, player.duration);
      }
      if (resume) {
        player.play().catch(() => {
          if (audioUrl === source) showAudioError("Press Play to continue the extended preview.");
        });
      }
    };
    player.addEventListener("loadedmetadata", loaded, { once: true });
    player.src = source;
    audioSegments.textContent = `Downloaded segments ${segments.join(", ")}`;
  }

  if (previousUrl !== null) {
    URL.revokeObjectURL(previousUrl);
  }
}

function wave(chunks, sampleRate) {
  const channels = 1;
  const bitsPerSample = 16;
  const blockAlign = channels * bitsPerSample / 8;
  const dataBytes = chunks.reduce((total, chunk) => total + chunk.byteLength, 0);
  const header = new DataView(new ArrayBuffer(44));
  const tag = (offset, text) => {
    for (let i = 0; i < text.length; i++) {
      header.setUint8(offset + i, text.charCodeAt(i));
    }
  };
  tag(0, "RIFF");
  header.setUint32(4, 36 + dataBytes, true);
  tag(8, "WAVEfmt ");
  header.setUint32(16, 16, true);
  header.setUint16(20, 1, true); // Linear PCM.
  header.setUint16(22, channels, true);
  header.setUint32(24, sampleRate, true);
  header.setUint32(28, sampleRate * blockAlign, true);
  header.setUint16(32, blockAlign, true);
  header.setUint16(34, bitsPerSample, true);
  tag(36, "data");
  header.setUint32(40, dataBytes, true);
  return new Blob([header.buffer, ...chunks], { type: "audio/wav" });
}

function showAudioError(message) {
  audioError.textContent = message;
  audioError.hidden = false;
}

player.addEventListener("error", () => {
  if (player.error) showAudioError("The browser could not open the WAV preview.");
});
player.addEventListener("playing", () => { audioError.hidden = true; });
reset.addEventListener("click", () => loadWindow(selected, "initial"));
reload.addEventListener("click", () => loadWindow(selected, "reload"));
start();
