package cbsp.lab07;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

final class Task3Audio {
    private static final int SAMPLE_RATE = 48_000;
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    private final byte[] high;
    private final byte[] low;

    Task3Audio() throws IOException {
        high = readClip(Task3.HIGH_KBPS);
        low = readClip(Task3.LOW_KBPS);
    }

    byte[] wave(Task3.Run run) throws IOException {
        int mediaOffset = pcmBytes(Task3.INITIAL_BUFFER_SECONDS);
        int segmentBytes = pcmBytes(Task3.SEGMENT_SECONDS);
        var pcm = new ByteArrayOutputStream();
        pcm.write(high, 0, mediaOffset);
        for (var step : run.steps()) {
            // When the previous audio runs out, pause before playing the next segment.
            pcm.writeBytes(new byte[pcmBytes(step.transfer().stallSeconds())]);
            byte[] clip = step.encodingKbps() == Task3.HIGH_KBPS ? high : low;
            pcm.write(clip, mediaOffset, segmentBytes);
            mediaOffset += segmentBytes;
        }

        byte[] samples = pcm.toByteArray();
        var wave = new ByteArrayOutputStream();
        try (var audio = new AudioInputStream(new ByteArrayInputStream(samples), FORMAT,
                samples.length / FORMAT.getFrameSize())) {
            AudioSystem.write(audio, AudioFileFormat.Type.WAVE, wave);
        }
        return wave.toByteArray();
    }

    private static int pcmBytes(double seconds) {
        return Math.toIntExact(Math.round(seconds * SAMPLE_RATE)) * FORMAT.getFrameSize();
    }

    private static byte[] readClip(int bitrateKbps) throws IOException {
        byte[] compressed = LabResources.read("task3/audio-" + bitrateKbps + ".pcm.gz");
        try (var stream = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return stream.readAllBytes();
        }
    }
}
