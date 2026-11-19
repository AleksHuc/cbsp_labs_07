const TARGET = 35;
const RECORDING_SECONDS = 45;
const playerTemplate = document.querySelector("#player");
const players = [...document.querySelectorAll(".seek-player")].map(setUp);

function clock(seconds) {
  const total = Math.max(0, Math.floor(seconds + .001));
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, "0")}`;
}

function percent(seconds) {
  return `${Math.max(0, Math.min(100, seconds / RECORDING_SECONDS * 100))}%`;
}

function bufferedIntervals(audio) {
  return Array.from({ length: audio.buffered.length }, (_, i) => [audio.buffered.start(i), audio.buffered.end(i)]);
}

function setUp(card) {
  card.append(playerTemplate.content.cloneNode(true));
  const player = {
    card,
    audio: card.querySelector("audio"),
    button: card.querySelector(".jump"),
    seekResult: card.querySelector(".seek-result"),
    requestSummary: card.querySelector(".request-summary"),
    requestList: card.querySelector(".requests ul"),
  };
  player.audio.setAttribute("aria-labelledby", card.getAttribute("aria-labelledby"));
  card.querySelector(".seek-target").style.left = percent(TARGET);
  for (const event of ["loadedmetadata", "timeupdate", "progress", "seeking", "emptied"]) {
    player.audio.addEventListener(event, () => renderPlayback(player));
  }
  player.audio.addEventListener("seeked", () => {
    settleJump(player);
    renderPlayback(player);
  });
  player.audio.addEventListener("play", () => {
    for (const other of players) {
      if (other !== player) {
        other.audio.pause();
      }
    }
  });
  player.audio.addEventListener("error", () => {
    if (player.audio.error) {
      player.seekResult.textContent = "Audio could not be loaded.";
    }
  });
  player.button.addEventListener("click", () => jump(player));
  card.querySelector(".restart").addEventListener("click", () => reset(player));
  reset(player);
  return player;
}

function reset(player) {
  player.audio.pause();
  const view = crypto.randomUUID().replaceAll("-", "");
  player.run = { view, rows: [], position: 0, jump: null, preparingJump: false, lastRendered: null };
  player.audio.src = `${player.card.dataset.src}?view=${view}`;
  player.seekResult.textContent = "";
  renderPlayback(player);
  renderRequests(player);
}

function renderPlayback(player) {
  const { audio, card, run } = player;
  if (!audio.seeking) {
    run.position = audio.currentTime;
  }
  player.button.disabled = audio.readyState < HTMLMediaElement.HAVE_METADATA || run.preparingJump || audio.seeking;
  card.querySelector(".playhead").style.left = percent(run.position);
  card.querySelector(".playhead-value").value = clock(run.position);

  const intervals = bufferedIntervals(audio);
  card.querySelector(".buffered").replaceChildren(...intervals.map(([start, end]) => {
    const band = document.createElement("span");
    band.style.left = percent(start);
    band.style.width = percent(end - start);
    return band;
  }));
  card.querySelector(".buffer-label").textContent = intervals.length === 0 ? "Buffered: 0:00"
    : `Buffered: ${intervals.map(([start, end]) => `${clock(start)}–${clock(end)}`).join(", ")}`;
}

async function jump(player) {
  const { audio, run } = player;
  run.preparingJump = true;
  renderPlayback(player);
  const rows = await getRequests(run).catch(() => run.rows);
  if (run !== player.run) {
    return;
  }
  const buffered = bufferedIntervals(audio).some(([start, end]) => start <= TARGET && TARGET <= end);
  const attempt = { before: new Set(rows.map(row => row.id)), buffered, settled: false };
  run.jump = attempt;
  player.seekResult.textContent = `Seeking to ${clock(TARGET)}…`;
  audio.currentTime = TARGET;
  run.preparingJump = false;
  renderPlayback(player);
  renderRequests(player);
  setTimeout(() => {
    if (player.run === run && run.jump === attempt) {
      settleJump(player);
      renderPlayback(player);
    }
  }, 450);
}

function settleJump(player) {
  const attempt = player.run.jump;
  if (attempt === null || attempt.settled || player.audio.seeking) {
    return;
  }
  attempt.settled = true;
  const position = player.audio.currentTime;
  const landed = Math.abs(position - TARGET) < 1;
  const result = landed ? `Reached ${clock(TARGET)}.` : `Playback stayed near ${clock(position)}.`;
  player.seekResult.textContent = `${result} Target was ${attempt.buffered ? "inside" : "outside"} the buffer.`;
}

async function getRequests(run) {
  const response = await fetch(`/api/requests?view=${run.view}`);
  if (!response.ok) {
    throw new Error("Could not read HTTP requests.");
  }
  return response.json();
}

async function poll() {
  await Promise.all(players.map(async player => {
    const run = player.run;
    try {
      const rows = await getRequests(run);
      if (run === player.run) {
        run.rows = rows;
        renderRequests(player);
      }
    } catch (error) {
      if (run === player.run) {
        player.requestSummary.textContent = error.message;
      }
    }
  }));
  setTimeout(poll, 300);
}

function renderRequests(player) {
  const { rows, jump } = player.run;
  const afterJump = jump === null ? [] : rows.filter(row => !jump.before.has(row.id));
  player.requestSummary.textContent = jump === null ? (rows.length ? "Latest audio responses" : "Waiting for playback.")
    : `${afterJump.length} new HTTP ${afterJump.length === 1 ? "request" : "requests"} after the jump`;
  const visible = rows.slice(0, 6);
  const key = JSON.stringify([visible, afterJump.map(row => row.id)]);
  if (key !== player.run.lastRendered) {
    player.run.lastRendered = key;
    const afterIds = new Set(afterJump.map(row => row.id));
    player.requestList.replaceChildren(...visible.map(row => requestRow(row, afterIds.has(row.id))));
  }
}

function requestRow(row, afterJump) {
  const item = document.createElement("li");
  const heading = document.createElement("div");
  heading.className = "request-heading";
  const status = document.createElement("strong");
  status.className = "status";
  status.dataset.status = row.status;
  status.textContent = `${row.method} ${row.status}`;
  const state = document.createElement("span");
  state.className = "request-state";
  state.textContent = [row.state, afterJump ? "After jump" : ""].filter(Boolean).join(" · ");
  heading.append(status, state);

  const details = document.createElement("dl");
  details.className = "request-details";
  for (const [label, value] of [["Range", row.range], ["Content-Range", row.contentRange]]) {
    const pair = document.createElement("div");
    const term = document.createElement("dt");
    const description = document.createElement("dd");
    term.textContent = label;
    description.textContent = value ?? "–";
    pair.append(term, description);
    details.append(pair);
  }
  item.append(heading, details);
  return item;
}

poll();
