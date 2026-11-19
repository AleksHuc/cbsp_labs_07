# Lab 07: Audio streaming

Observe HTTP audio and HLS delivery, simulate playback buffers and latency,
and send MIDI events over UDP for timed playback.

This is a Java starter for **nine required tasks**. `TODO (Task N)` comments
mark the algorithms and connections you must implement. Each unfinished
method or branch throws `UnsupportedOperationException("TODO (Task N): ...")`
with the task number and the missing step. The experiment runners, HTTP
servers, browser interfaces, media fixtures, plots and checks are supplied.

## Setup and commands

Use **JDK 21 or newer** and **Maven 3.9 or newer**. Open the directory
containing `pom.xml` as a Maven project. The first build needs access to the
declared Maven plugins. The experiments use the JDK HTTP server and client.
Use a browser that supports MP3 playback for the listening comparison.

Run from the project root.

```bash
mvn clean compile
mvn -q exec:java "-Dexec.args=--parameters 00123456"
mvn -q exec:java "-Dexec.args=--check1 00123456"
mvn -q exec:java "-Dexec.args=--task1 00123456"
mvn test
```

Replace `00123456` with your eight-digit student ID, including leading zeroes.
Use task numbers **1 through 9**. The commands in each task compile your latest
changes before launching the experiment. In IntelliJ, run `Main.main` with
`--check1 00123456` as program arguments and the project root as the working
directory.

- `--parameters ID` prints the assigned settings.
- `--checkN ID` runs the experiment and exports results without a browser or
  an audio device.
- `--taskN ID` runs the same experiment and starts its interactive browser
  page. Open the printed URL and press `Ctrl+C` when finished.
- `--task8 ID --sound` and `--task9 ID --sound` also play the initial run
  through Java Sound.
- `mvn test` runs the checks for all nine tasks.

The starter compiles and `--parameters` works immediately. Task commands and
`mvn test` fail at the first unfinished TODO they reach. The `--checkN` and
`--taskN` commands use the same Java methods. After finishing a task and its
prerequisites, run `--checkN` before opening the browser experiment.

Outputs go to **`output/<student-id>/`**. Running a command again replaces
files with matching names and retains unrelated files. The browser server
binds to `127.0.0.1` on an available local port.

## Implementation order and TODO locations

Source files are in `src/main/java/cbsp/lab07/`. Complete the marked methods
while retaining their signatures and the supplied checks.

| Task | Implement | Earlier tasks needed |
| ---- | --------- | -------------------- |
| 1 | `ByteRanges.select` | None |
| 2 | `HlsPlaylists.segments`, `HlsPlaylists.pending` | None |
| 3 | `AdaptiveBuffer.transfer`, `AdaptiveBuffer.useHigh` | None |
| 4 | `JitterBuffer.decide` | None |
| 5 | `Latency.measure` | None |
| 6 | `MidiNetwork.encode`, `MidiNetwork.send` | None |
| 7 | `MidiNetwork.decode`, `MidiNetwork.receive` | 6 |
| 8 | `MidiNetwork.Scheduled.getDelay`, `MidiExperiments.startReceiver` | 6 and 7 |
| 9 | `MidiExperiments.cleanupNotes` | 6, 7 and 8 |

## Reproducible assignment settings

The settings in `parameters.txt` use generator version **`lab07-v1`**. The
last digit of your student ID determines the following values. The fixed
comparison scenarios are shared by everyone.

| Setting | Used in |
| ------- | ------- |
| `throughputKbps` | Task 3, assigned network profile |
| `packet4ArrivalMs` | Task 4, assigned arrival for packet 4 |
| `midiNote` | Tasks 6–9, pitch of the first note |

## Task 1 · HTTP audio and seeking

**Goal.** Serve a requested interval from an audio file and inspect how a
browser retrieves more data after a seek.

**Your work in `ByteRanges.java`.** Complete `select(range, size)`. The
TODO handles either `null` for a whole-file response or a
`RequestedRange` with an in-bounds start and an optional last byte. Byte
positions start at zero.

- For `null`, return `Selection.full(size)` for a `200` response.
- For a range, use `range.first()` as the first byte. If `range.last()` is
  `null`, use the last byte of the file (`size - 1`). Otherwise, cap the
  requested last byte at `size - 1`. Both positions are inclusive. Calculate
  the length as `last - first + 1` and return `Selection(206, first, length)`.

The prepared code parses `Range`, handles requests starting beyond the file
with `416`, formats `Content-Range` and sends the selected bytes. Unsupported
forms such as suffix ranges and multiple intervals use a whole-file response
in this lab. [HTTP Semantics, RFC 9110 §14](https://www.rfc-editor.org/rfc/rfc9110.html#section-14)

**Prepared experiment.** Both player controls use the same 45-second mono MP3
with a quiet repeating melody and spoken timestamps. The range route supports
the byte selection above. The whole-file route answers every GET for the MP3
with `200` and the entire file. In the browser experiment, both routes use
the same paced response of approximately 24 kB/s to make seeking beyond the
buffer easier to observe.

### Run and inspect

```bash
mvn -q compile exec:java "-Dexec.args=--check1 00123456"
mvn -q compile exec:java "-Dexec.args=--task1 00123456"
```

1. Run `--check1`. Compare the rows in `task1-http.csv` for no `Range`,
   `bytes=48000-95999` and `bytes=48000-` across both routes. The closed
   range should produce **48,000 bytes** and `206` on the range route.
   The whole-file route should return the full recording and `200`.
2. Run `--task1` and open the printed URL. Press Play on one player, wait for
   sound, then click **Jump to 0:35** while the target is outside the shaded
   buffered intervals. The playback marker and seek result show where the
   player lands. Click **Reset player** to repeat the run with an empty buffer.
3. Repeat on the other player and compare the `Range`, status and
   `Content-Range` in the live request lists. Requests observed after the
   button press are marked **After jump**. The browser chooses its own byte
   positions and may continue the existing transfer. Use the scripted
   requests to verify the HTTP responses and press `Ctrl+C` when finished.

| Export                   | Contents                                                                                                                                       |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `parameters.txt`         | Student ID and assignment version                                                                                                              |
| `task1-http.csv`         | Route, request `Range`, status, `Content-Range`, `Content-Length` and bytes received by the scripted client                                    |
| `task1-browser-http.csv` | Request order, route, method, request `Range`, response headers, bytes written by the server, completion flag and elapsed time in milliseconds |

The live request list shows response headers as they are sent. The CSV records
each transfer when it completes or ends early. Its `bytes_written` counts
bytes handed to the server's response stream. The timeline shows the browser's
buffered audio intervals in seconds.

### Verification

Use `--check1` to verify this task on its own. The full test suite includes
`HttpChecks`, which covers closed and open ranges, clipping, invalid and
unsatisfiable inputs, large positions, the full response, and byte-for-byte
response bodies from the loopback server.

## Task 2 · HLS playlists and segment selection

**Goal.** Assign sequence numbers to the entries in an HLS media playlist and
find the new segments when that playlist is reloaded.

**Your work in `HlsPlaylists.java`.** Complete the two marked methods.

1. `segments(window)` takes a prepared `MediaWindow` with a `firstSequence`
   and an ordered list of entries. Each entry contains a `durationSeconds`
   and a resolved `uri`. Create a `Segment` for each entry. Give the first
   entry `firstSequence` and increment the number by one for each following
   entry. Preserve the supplied durations, URIs and playlist order.
2. `pending(playlist, downloaded)` returns the segments whose sequence numbers
   are absent from the set of successful downloads, in playlist order. The
   set belongs to the selected variant and may have gaps.

The prepared parser reads `BANDWIDTH` from the multivariant playlist and
`#EXT-X-MEDIA-SEQUENCE` and `#EXTINF` from the selected media playlist. It
resolves relative URIs against the playlist that contains them. Sequence
numbering starts at `0` if `#EXT-X-MEDIA-SEQUENCE` is absent. [HLS playlists, RFC 8216 §4](https://www.rfc-editor.org/rfc/rfc8216.html#section-4)

**Prepared experiment.** The server offers two encodings of the same short
excerpt from Task 1. **AAC-LC** is the codec, **MPEG-TS** is the container for
the `.ts` segments, and **HLS over HTTP** is the delivery method. The names
`audio-64` and `audio-128` refer to the nominal encoder rates. The master
playlist advertises peak segment rates of 75,751 and 148,501 bit/s, which
include container overhead. The prepared runner selects the lower advertised
rate for this experiment. [Segment rate and `BANDWIDTH`, RFC 8216 §4.1 and §4.3.4.2](https://www.rfc-editor.org/rfc/rfc8216.html#section-4.3.4.2)

### Run and inspect

```bash
mvn -q compile exec:java "-Dexec.args=--check2 00123456"
mvn -q compile exec:java "-Dexec.args=--task2 00123456"
```

1. Run `--check2`. In `task2-variants.csv`, check that both playlist URIs were
   found and the lower-rate variant is selected.
2. In `task2-segments.csv`, expect **10, 11, 12** under `initial` and only
   **13** under `reload`. The server changes the selected playlist at the
   same URL from window `10–12` to `11–13`. The runner adds sequence numbers to
   its download history after successful HTTP transfers.
3. Run `--task2` and open the printed URL. Choose a variant and click
   **Reload playlist**. Segment 10 leaves the window, 11 and 12 stay marked
   as already downloaded, and 13 shows the new download. Reload again to
   see the same window with zero new downloads. Click **Reset** or select
   another variant to start a fresh run. Stop the server with `Ctrl+C`.

The browser shows the segments and byte counts from the Java client's actual
HTTP requests. **Play downloaded audio** plays a PCM WAV assembled from the
decoded audio of those exact AAC segments. Prepared PCM excerpts are matched
to the downloaded AAC frames, so playback uses the browser's WAV decoder.
Play the roughly six-second excerpt, then
reload the playlist to add segment 13 and extend the preview to roughly
eight seconds. The preview includes segment 10 once it has been downloaded.
Choosing another variant starts a new preview for that variant.

`--task2` also writes the scripted run's `.ts` files and
`task2-media/index.m3u8` for playback in an HLS player.

| Export               | Contents                                                                                                                    |
| -------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| `task2-variants.csv` | Media playlist URI, advertised peak bit rate in bit/s, and the selected variant                                             |
| `task2-segments.csv` | Playlist snapshot, variant playlist URI, media sequence number, segment URI, duration in seconds, and downloaded byte count |
| `task2-media/`       | Downloaded AAC in MPEG-TS and the finite local playlist written by `--task2`                                                |

### Verification

`HlsChecks` checks relative URIs, fractional durations, explicit and default
sequence numbering, and filtering when the download history has gaps. It
also runs the two-window HTTP experiment and compares every downloaded file
with the original fixture bytes. Use `--check2` to run this task on its own.

## Task 3 · Adaptive bitrate and the audio buffer

**Goal.** Choose a segment size the connection can deliver before the buffered audio runs out.

**Your work in `AdaptiveBuffer.java`.** Complete two methods.

1. `transfer(bufferedSeconds, sizeBytes, throughputKbps, segmentSeconds)`
   returns the download time, buffer level after delivery and stall time.
   Convert bytes to bits and kbit/s to bit/s before dividing. Playback drains
   one second of buffered audio per second until the buffer reaches zero.
   Add the segment duration when the download finishes. If the download took
   longer than the initial buffer, the difference is the stall time.
2. `useHigh(bufferedSeconds, highSizeBytes, throughputKbps)` chooses the
   higher-rate segment when it can finish before or just as the buffer runs
   out. Reuse the transfer calculation.

### Run and inspect

```bash
mvn -q compile exec:java "-Dexec.args=--check3 00123456"
mvn -q compile exec:java "-Dexec.args=--task3 00123456"
```

`--check3` simulates four sequential two-second segments
with an initial buffer of 3 s. The segments are 32,000 bytes at 128 kbit/s or
16,000 bytes at 64 kbit/s. Both policies fetch the first segment at 128 kbit/s.
Starting at the second segment, one policy applies `useHigh` while the other
keeps 128 kbit/s. At a fixed 96 kbit/s, the second segment takes about 2.67 s
at the higher rate, causing a stall of about 0.33 s. At the lower rate it
takes about 1.33 s and brings the buffer back to 3 s. The assigned profile
varies throughput between downloads.

`task3-buffer.csv` includes both profiles and policies. `task3-buffer.png`
and `task3-assigned-buffer.png` show the buffer in seconds, including jumps
when segments arrive and flat sections at zero during stalls.

**Browser experiment.** Run `--task3` and open the printed URL. Both players
start with 3 s of audio. Set the throughput for the next segment, then click
**Download next segment**. Each click simulates one transfer for each player.
Compare the selected bitrates, download times and stalls in the two cards
and see where the buffers empty or refill in the shared plot. Each card
has its own audio player. The WAV previews join the initial buffer and the
simulated segments from the 64 or 128 kbit/s encodes of the same excerpt,
with silence where the buffer empties. Each download refreshes both previews.
Playing one pauses the other.

Leave the slider at 96 kbit/s for the first two transfers, then play each
preview. **Adaptive bitrate** plays through continuously for 7 s.
**Fixed bitrate** inserts about 0.33 s of silence at 5 s, just before the
second segment. Move the slider between transfers to try a different profile.
Click **Reset** to start a new four-segment run. Stop the server with `Ctrl+C`.

## Task 4 · Jitter and packet deadlines

**Goal.** Determine which packets can be played at their scheduled times.

**Your work in `JitterBuffer.java`.** Complete `decide(mediaTimeMs, delayMs,
arrivalMs)`. Add `delayMs` to the media timestamp to find the playout
deadline. A `null` arrival means `LOST`. An arrival after the deadline is
`LATE`. An arrival at or before the deadline is `ON_TIME`. The runner already
supplies arrivals in expected packet order, including missing entries.

### Run and inspect

```bash
mvn -q compile exec:java "-Dexec.args=--check4 00123456"
mvn -q compile exec:java "-Dexec.args=--task4 00123456"
```

`--check4` compares delays of 40 and 50 ms. Each packet
contains 20 ms of audio. Packet 3 arrives at 47 ms, before packet 2 at 55 ms.
Packet 4 arrives at 103 ms. It misses the 100 ms deadline and meets the
110 ms deadline. Packet 5 is lost in both cases. The assigned trace varies
packet 4's arrival time.

Use `task4-packets.csv` and `task4-playout.png` to compare arrivals and
deadlines. `--task4` also writes `task4-delay40.wav` and `task4-delay50.wav`
for the fixed trace. Both repeat the same tone. The runner inserts 20 ms of
silence for each late or missing packet and a gap between repetitions.

**Browser experiment.** Open the URL printed by `--task4`. Use the slider
or the **40 ms** and **50 ms** buttons to move the deadlines of all five
packets. Packet 4 arrives at 103 ms and changes from late to on time once
the playout delay reaches 43 ms. Use **Listen to this run** to hear the tone
with the current decisions. The browser timeline and audio both use the
result of `decide`. Stop the server with `Ctrl+C`.

## Task 5 · Startup and capture-to-playout latency

**Goal.** Measure the delay from pressing Play and the delay from capturing the first sample.

**Your work in `Latency.java`.** Complete `measure(capturedMs, requestedMs,
playedMs)`. Return `Measurements(startupMs, captureToPlayoutMs)`. Measure
startup from the playback request to playout. Measure capture-to-playout
from the capture time to playout of that same sample.

### Run and inspect

```bash
mvn -q compile exec:java "-Dexec.args=--check5 00123456"
mvn -q compile exec:java "-Dexec.args=--task5 00123456"
```

`--check5` writes `task5-latency.csv` and
`task5-timeline.png`. In scenario 1 the sample is captured at 0 ms, Play is
requested at 2,600 ms and the sample is played at 3,000 ms. The two
measurements are 400 ms and 3,000 ms. The prepared timeline places
publication at 2,800 ms. Scenarios 2 and 3 model a complete
segment published at 2,000 ms and a partial segment published at 500 ms.
In both, playback is requested at 300 ms and playout starts 300 ms after
both the request and publication have occurred. Earlier publication reduces
both measured delays by 1,500 ms in this model.

**Browser experiment.** Run `--task5` and open the printed URL. Move the
slider to change when Play is pressed or select **Full segment** and
**Partial segment** to change the publication time. The timeline marks
capture, request, publication and first sound. The two colored intervals
show the measurements returned by `measure` alongside their endpoints.
Click **Play timeline** to replay the selected scenario in real time. A low
click marks capture, a higher click marks the Play request, and a short
excerpt starts at playout. The playhead shows when the segment is published.

At a 300 ms Play request, the excerpt starts at 2,300 ms for **Full segment**
and at 800 ms for **Partial segment**. For the full segment, move the Play
request to 1,500 ms and replay. The time from the second click to the excerpt
falls from 2,000 ms to 800 ms. The time from the first click to the excerpt
stays at 2,300 ms. Stop the server with `Ctrl+C`.

## Task 6 · Encode and send MIDI events

**Goal.** Encode each event and send it in one UDP datagram. Complete Tasks
6–9 in order and run the check after each one.

**Your work in `MidiNetwork.java`.** Complete two methods.

1. `encode(event)` writes 12 bytes using a `ByteBuffer`. Write the sequence
   number and the relative media timestamp in milliseconds as two four-byte
   big-endian integers. Then write one byte each for the note-on flag
   (`1` for `Note On`, `0` for `Note Off`), channel index, note and velocity.
   Return the byte array. The prepared datagrams use channel indices 0–15.
2. `send(socket, destination, event)` sends those bytes in one
   `DatagramPacket` to `destination`.

### Run and inspect

```bash
mvn -q compile exec:java "-Dexec.args=--check6 00123456"
mvn -q compile exec:java "-Dexec.args=--task6 00123456"
```

`--check6` sends a prepared `Note On` and `Note Off`
over loopback. `task6-midi-send.csv` records the received byte count and hex
payload for each datagram. Both payloads should contain 12 bytes and carry
media timestamps of 0 and 500 ms. The prepared sender can vary the time it
actually sends each datagram independently of its media timestamp.

## Task 7 · Receive events and calculate deadlines

**Goal.** Place incoming MIDI events on the receiver's local playback clock.

**Your work in `MidiNetwork.java`.** Complete two methods.

1. `decode(packet)` returns `null` if the received datagram length differs
   from `MESSAGE_BYTES`. Wrap its data with `ByteBuffer.wrap`, using the
   packet's offset and length. Read the two integers with `getInt`, followed
   by the four unsigned byte values in the same order as `encode`. Use
   `Byte.toUnsignedInt(buffer.get())` for each byte. Pass the six values to
   the prepared `validatedEvent` helper and return its result.
2. `receive(socket, onEvent, clock)` reads one datagram into `receiveBuffer()`.
   Record the arrival with `clock.nowNanos()` and decode it. Return `false`
   if decoding returns `null`. For a valid event, get the local origin with
   `clock.originNanos(arrival)` and add the media timestamp converted from
   milliseconds to nanoseconds. Pass `Scheduled(event, arrival, deadline)`
   to the callback with `onEvent.accept(...)` and return `true`. The prepared
   clock uses the first valid arrival plus 70 ms as the origin for the session.

### Run and inspect

```bash
mvn -q compile exec:java "-Dexec.args=--check7 00123456"
mvn -q compile exec:java "-Dexec.args=--task7 00123456"
```

`--check7` receives the two datagrams with prepared
arrival times of 30 and 580 ms. The calculated deadlines are 100 and 600 ms.
In `task7-midi-timing.csv`, the interval is 550 ms for playback at arrival
and 500 ms for scheduled playback. This check uses `received::add` as the
callback to collect results in a list before you implement timed playback.

## Task 8 · Play events at their deadlines

**Goal.** Use `DelayQueue` to play events while the receive thread waits for
more datagrams.

**Your work.** Complete two methods.

1. In `MidiNetwork.Scheduled.getDelay(unit)`, subtract `System.nanoTime()`
   from the deadline and convert the difference from nanoseconds to the
   requested unit. The prepared `compareTo` orders events by deadline and
   uses their sequence numbers for ties.
2. In `MidiExperiments.startReceiver(...)`, submit a task to the supplied
   executor. Inside the task, call `readUntilIdle` and pass the supplied
   `onEvent` callback. The prepared caller supplies the callback that adds
   events to the queue. Use a `Callable<Void>` by returning `null` after the
   loop finishes. Return the `Future<Void>` from `submit` immediately. The
   runner checks it after the playback queue is drained.

### Run and inspect

```bash
mvn -q compile exec:java "-Dexec.args=--check8 00123456"
mvn -q compile exec:java "-Dexec.args=--task8 00123456"
```

`--check8` runs a real UDP loopback session and writes
`task8-midi-playout.csv`. The receive thread can wait for UDP while the
playout loop takes events once their deadlines are due. The callback runs
on the receive thread and forwards valid events to the queue. Compare `arrival_ms`,
`deadline_ms` and `playout_ms` for `Note Off`. It can play while the socket is
waiting for the next packet. The deadlines are 500 ms apart and actual
playout times include OS scheduling variation. To hear the note through a Java
Sound synthesizer, run the browser experiment with `--sound`.

```bash
mvn -q compile exec:java "-Dexec.args=--task8 00123456 --sound"
```

## Task 9 · Clean up after packet loss

**Goal.** Keep a missing `Note Off` from leaving a note sounding after the
receiver stops.

**Your work in `MidiExperiments.java`.** Complete `cleanupNotes`. The
prepared playout code tracks active notes by channel and note number. At
shutdown, iterate over the remaining entries and call `release` with each
key and its recorded start time. Pass the supplied trace to `release` so
the cleanup also appears in the browser recording. The helper sends
`Note Off` and logs the cleanup row.

### Run and inspect

```bash
mvn -q compile exec:java "-Dexec.args=--check9 00123456"
mvn -q compile exec:java "-Dexec.args=--task9 00123456"
```

`--check9` runs the earlier checks and adds a loopback
session in which the prepared sender sends only `Note On`. After 900 ms of
input inactivity and once the queue is drained, the receiver releases the
active note. `task9-midi-loss.csv` contains a `playout` row for `Note On`
and a `cleanup` row for `Note Off`. To hear the complete session through
Java Sound, run the browser experiment with `--sound`.

```bash
mvn -q compile exec:java "-Dexec.args=--task9 00123456 --sound"
```

### Browser timeline for Tasks 6–9

Run `--task6`, `--task7`, `--task8` or `--task9` with your ID and open the
printed URL. The page captures a real loopback run for the selected task.
Click **Next event** to examine each step, move the **Event position**
slider to revisit a step, or use **Replay at ¼ speed**. **Run again** records
a fresh run.

- **Task 6** shows sends, arrivals and the received hexadecimal payload.
- **Task 7** shows when the callback produces an event and its deadline.
- **Task 8** sends two notes so you can inspect the queue with multiple
  events waiting. Watch when each event enters the queue, when it plays and
  which notes are active at the selected point in the trace.
- **Task 9** marks the `Note Off` deliberately omitted by the sender and
  shows the receiver releasing the active note during cleanup.

In Tasks 8 and 9, leave **Sound** checked and click **Replay at ¼ speed**
to hear the MIDI notes in the browser. Each recorded `Note On` starts a
synthesized tone at its MIDI pitch and velocity. `Note Off` or cleanup
releases it. Overlapping notes sound together. Pause, stepping and moving
the slider stop the audio until you resume the replay.

The timeline positions and readouts use recorded milliseconds. The replay
speed stretches the note durations and the intervals between them, keeping
their pitches. Press `Ctrl+C` to stop the server.

After completing Tasks 6–9, you can run the sender and receiver separately.
Start the receiver in one terminal and the sender in a second terminal.

```bash
mvn -q compile
java -cp target/classes cbsp.lab07.Main --receive6 00123456 5006
```

```bash
java -cp target/classes cbsp.lab07.Main --send6 00123456 127.0.0.1 5006
```

On two computers, substitute the receiver's IP address for `127.0.0.1`. The
separate receiver writes `task9-midi-receiver.csv` with the three-note phrase.
Use `--receive6 00123456 5006 --sound` to hear that session through an
available Java Sound synthesizer.

## Verification

Run `mvn test` after completing all nine tasks. It runs `HttpChecks`,
`HlsChecks` and `StreamingChecks` without an audio device. `StreamingChecks`
verifies buffer calculations and bitrate choice, packet classification at
the deadline, both latency measurements, and MIDI datagrams, callback
delivery, timing and cleanup over UDP. The repository's GitHub Actions
workflow checks that the starter builds and prints the assigned settings.

Run `--check1` through `--check9` to generate the experiment outputs under
your ID. Use the browser experiments for the request traces, timelines and
listening comparisons described in each task.

## Code map

| File                                                                                                       | Responsibility                                                                        |
| ---------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| `ByteRanges.java`                                                                                          | Byte selection exercise, prepared header parsing and `Content-Range` formatting       |
| `HlsPlaylists.java`                                                                                        | Sequence numbering and segment selection exercises, prepared playlist parsing         |
| `AdaptiveBuffer.java`, `JitterBuffer.java`, `Latency.java`                                                 | Buffer, packet deadline and latency exercises                                         |
| `MidiNetwork.java`                                                                                         | Wire format, UDP send and receive, and the timed queue exercises                      |
| `MidiExperiments.java`                                                                                     | Prepared UDP MIDI sessions and the cleanup exercise                                   |
| `MidiTrace.java`, `MidiBrowser.java`, `src/main/resources/cbsp/lab07/midi/`                                | Recorded MIDI events, playback queue and active note visualization                    |
| `Main.java`, `TaskParameters.java`                                                                         | Command dispatch, version and output directory                                        |
| `Task1.java`, `AudioServer.java`                                                                           | Scripted range requests and browser experiment                                        |
| `Task2.java`, `HlsServer.java`                                                                             | Playlist reload, segment downloads and media exports                                  |
| `Task3.java`, `Task4.java`, `Task5.java`                                                                   | Prepared timing simulations                                                           |
| `LocalLabBrowser.java`, `Task2Browser.java`, `Task3Browser.java`, `Task4Browser.java`, `Task5Browser.java` | Prepared browser comparisons backed by the Java exercises                             |
| `Csv.java`, `LabResources.java`, `Plots.java`                                                              | Shared CSV formatting, fixture loading and headless plotting                          |
| `BrowserAudioRequests.java`, `src/main/resources/cbsp/lab07/task1/`, `episode.mp3`                         | Interactive seek comparison, live HTTP headers and the original synthesized recording |
| `src/main/resources/cbsp/lab07/task2/`                                                                     | Multivariant playlist, two live windows per variant and AAC segments                  |
| `TsAac.java`, `Task2Preview.java`, `src/main/resources/cbsp/lab07/task2/preview/`                          | Match downloaded AAC frames to the prepared PCM preview                               |
| `HttpChecks.java`, `HlsChecks.java`, `StreamingChecks.java`                                                | Device-free algorithm, file and network checks                                        |

The assignment follows **Lab 7: Audio streaming**, slides 2–4, 7–10 and 12–16.
