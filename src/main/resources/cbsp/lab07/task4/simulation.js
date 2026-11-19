const slider = document.querySelector("#delay");
const delayValue = document.querySelector("#delay-value");
const presets = [...document.querySelectorAll("[data-delay]")];
const error = document.querySelector("#error");
const summary = document.querySelector("#summary");
const list = document.querySelector("#packets");
const ruler = document.querySelector("#ruler");
const preview = document.querySelector("#preview");
const template = document.querySelector("#packet-row");
const labels = { ON_TIME: "On time", LATE: "Late", LOST: "Lost" };
let activeRequest;

async function update(delay) {
  activeRequest?.abort();
  const request = new AbortController();
  activeRequest = request;
  error.hidden = true;
  if (delay !== undefined) {
    delayValue.value = `${delay} ms`;
    slider.setAttribute("aria-valuetext", `${delay} ms`);
    summary.textContent = "Updating packets…";
    if (preview.hasAttribute("src")) {
      preview.removeAttribute("src");
      preview.load();
    }
  }

  try {
    const query = delay === undefined ? "" : `?delay=${delay}`;
    const response = await fetch(`/api/jitter${query}`, { signal: request.signal });
    if (!response.ok) {
      throw new Error(await response.text());
    }
    const model = await response.json();
    if (request.signal.aborted) {
      return;
    }
    render(model);
  } catch (exception) {
    if (!request.signal.aborted) {
      error.textContent = `Could not update the simulation. ${exception.message}`;
      error.hidden = false;
      summary.textContent = "Simulation unavailable";
    }
  }
}

function render(model) {
  slider.min = model.minDelayMs;
  slider.max = model.maxDelayMs;
  slider.value = model.delayMs;
  slider.disabled = false;
  delayValue.value = `${model.delayMs} ms`;
  slider.setAttribute("aria-valuetext", delayValue.value);
  document.querySelector("#delay-min").textContent = `${model.minDelayMs} ms`;
  document.querySelector("#delay-max").textContent = `${model.maxDelayMs} ms`;
  for (const button of presets) {
    button.disabled = false;
    button.setAttribute("aria-pressed", Number(button.dataset.delay) === model.delayMs);
  }

  // Keep the time scale fixed while the delay changes.
  const last = model.packets.at(-1);
  const scale = Math.ceil(Math.max(last.deadlineMs - model.delayMs + model.maxDelayMs,
    ...model.packets.map(packet => packet.arrivalMs ?? 0)) / 20) * 20;
  const marks = [];
  for (let time = 0; time <= scale; time += scale / 4) {
    const mark = document.createElement("span");
    mark.textContent = time === scale ? `${time} ms` : time;
    mark.style.left = `${time / scale * 100}%`;
    marks.push(mark);
  }
  ruler.replaceChildren(...marks);

  const rows = model.packets.map(packet => {
    const row = template.content.firstElementChild.cloneNode(true);
    row.dataset.outcome = packet.outcome;
    row.dataset.packet = packet.number;
    row.querySelector(".packet").textContent = `Packet ${packet.number}`;
    row.querySelector(".result strong").textContent = labels[packet.outcome];
    row.querySelector(".result small").textContent = packet.arrivalMs === null
      ? `Deadline ${packet.deadlineMs} ms`
      : `Arrival ${packet.arrivalMs} · Due ${packet.deadlineMs} ms`;
    const arrival = row.querySelector(".arrival");
    arrival.hidden = packet.arrivalMs === null;
    arrival.style.left = `${packet.arrivalMs / scale * 100}%`;
    row.querySelector(".deadline").style.left = `${packet.deadlineMs / scale * 100}%`;
    return row;
  });
  list.replaceChildren(...rows);
  const onTime = model.packets.filter(packet => packet.outcome === "ON_TIME").length;
  summary.textContent = `${onTime} of ${model.packets.length} packets on time`;
  preview.src = `/preview.wav?delay=${model.delayMs}`;
}

slider.addEventListener("input", () => update(Number(slider.value)));
for (const button of presets) {
  button.addEventListener("click", () => update(Number(button.dataset.delay)));
}
update();
