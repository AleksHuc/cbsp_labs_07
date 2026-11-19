const throughput = document.querySelector("#throughput");
const throughputValue = document.querySelector("#throughput-value");
const next = document.querySelector("#next");
const reset = document.querySelector("#reset");
const progress = document.querySelector("#progress");
const error = document.querySelector("#error");
const canvas = document.querySelector("#buffer-plot");
const policies = [...document.querySelectorAll("[data-policy]")];
const players = policies.map(card => card.querySelector("audio"));
const theme = getComputedStyle(document.documentElement);
const colors = [theme.getPropertyValue("--adaptive").trim(), theme.getPropertyValue("--fixed").trim()];
const kbps = value => `${value} kbit/s`;
const seconds = value => `${value.toFixed(2)} s`;

let model = null;
let throughputs = [];
let loading = false;

async function simulate(profile) {
  loading = true;
  error.hidden = true;
  updateControls();
  try {
    const query = new URLSearchParams({ throughput: profile.join(",") });
    const response = await fetch(`/api/simulation?${query}`);
    if (!response.ok) {
      throw new Error(await response.text());
    }
    model = await response.json();
    throughputs = profile;
    throughput.min = model.minThroughputKbps;
    throughput.max = model.maxThroughputKbps;
    if (profile.length === 0) {
      throughput.value = model.defaultThroughputKbps;
    }
    document.querySelector("#throughput-min").textContent = kbps(model.minThroughputKbps);
    document.querySelector("#throughput-max").textContent = kbps(model.maxThroughputKbps);
    updateThroughput();
    render();
    for (const player of players) {
      player.src = `/${player.closest("[data-policy]").dataset.policy}.wav?${query}`;
    }
  } catch (exception) {
    error.textContent = `Could not update the simulation. ${exception.message}`;
    error.hidden = false;
    if (model === null) {
      progress.textContent = "Simulation unavailable";
    }
  } finally {
    loading = false;
    updateControls();
  }
}

function updateThroughput() {
  const value = kbps(throughput.value);
  throughputValue.value = value;
  throughput.setAttribute("aria-valuetext", value);
}

function updateControls() {
  const finished = model !== null && throughputs.length === model.segmentCount;
  next.disabled = loading || model === null || finished;
  throughput.disabled = next.disabled;
  reset.disabled = loading || model !== null && throughputs.length === 0;
}

function render() {
  const count = throughputs.length;
  progress.textContent = count === 0 ? `0 of ${model.segmentCount} segments downloaded`
    : `Segment ${count} of ${model.segmentCount} · ${kbps(throughputs[count - 1])}`;

  const yMax = bufferScale();
  for (const card of policies) {
    const run = model[card.dataset.policy];
    const buffered = run.points.at(-1)[1];
    const last = run.segments.at(-1);
    card.querySelector("[data-buffer]").textContent = buffered.toFixed(2);
    card.querySelector("[data-bar]").style.width = `${buffered / yMax * 100}%`;
    card.querySelector("[data-encoding]").textContent = last ? kbps(last.encodingKbps) : "–";
    card.querySelector("[data-download]").textContent = last ? seconds(last.downloadSeconds) : "–";
    const stall = card.querySelector("[data-stall]");
    stall.textContent = last ? (last.stallSeconds > 0 ? seconds(last.stallSeconds) : "No stall") : "–";
    stall.toggleAttribute("data-stalled", last?.stallSeconds > 0);
  }
  drawPlot();
}

function bufferScale() {
  return Math.max(4, Math.ceil(Math.max(...model.adaptive.points.concat(model.fixed.points).map(point => point[1]))));
}

function drawPlot() {
  if (model === null) {
    return;
  }
  const { width, height } = canvas.getBoundingClientRect();
  const ratio = window.devicePixelRatio || 1;
  canvas.width = Math.round(width * ratio);
  canvas.height = Math.round(height * ratio);
  const context = canvas.getContext("2d");
  context.scale(ratio, ratio);

  const left = 42;
  const right = width - 18;
  const top = 23;
  const bottom = height - 42;
  const end = Math.max(model.adaptive.points.at(-1)[0], model.fixed.points.at(-1)[0]);
  const xStep = Math.max(1, Math.ceil(end / 5));
  const xMax = xStep * Math.max(4, Math.ceil(end / xStep));
  const yMax = bufferScale();
  const x = time => left + time / xMax * (right - left);
  const y = buffer => bottom - buffer / yMax * (bottom - top);

  context.font = "13px system-ui, sans-serif";
  context.fillStyle = "#476275";
  context.fillText("Buffered audio (s)", 0, 12);
  context.strokeStyle = "#dbe4e9";
  context.lineWidth = 1;
  context.beginPath();
  for (let tick = 0; tick <= yMax; tick++) {
    context.moveTo(left, y(tick));
    context.lineTo(right, y(tick));
    context.textAlign = "right";
    context.fillText(tick, left - 11, y(tick) + 4);
  }
  for (let tick = 0; tick <= xMax; tick += xStep) {
    context.moveTo(x(tick), top);
    context.lineTo(x(tick), bottom);
    context.textAlign = "center";
    context.fillText(tick, x(tick), bottom + 18);
  }
  context.stroke();
  context.textAlign = "right";
  context.fillText("Time (s)", right, height - 3);

  [model.adaptive.points, model.fixed.points].forEach((points, i) => {
    context.strokeStyle = colors[i];
    context.fillStyle = colors[i];
    context.lineWidth = i === 0 ? 3.5 : 2.5;
    context.setLineDash(i === 0 ? [] : [7, 4]);
    context.beginPath();
    context.moveTo(x(points[0][0]), y(points[0][1]));
    for (const [time, buffer] of points.slice(1)) {
      context.lineTo(x(time), y(buffer));
    }
    context.stroke();
    const [latestTime, latestBuffer] = points.at(-1);
    context.beginPath();
    context.arc(x(latestTime), y(latestBuffer), i === 0 ? 5 : 3, 0, 2 * Math.PI);
    context.fill();
  });
}

throughput.addEventListener("input", updateThroughput);
next.addEventListener("click", () => simulate([...throughputs, Number(throughput.value)]));
reset.addEventListener("click", () => simulate([]));
for (const player of players) {
  player.addEventListener("play", () => {
    for (const other of players) {
      if (other !== player) {
        other.pause();
      }
    }
  });
}
new ResizeObserver(drawPlot).observe(canvas);
simulate([]);
