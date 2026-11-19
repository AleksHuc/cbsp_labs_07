package cbsp.lab07;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

final class Task2Preview {
    static final int SAMPLE_RATE = 48_000;

    private Task2Preview() { }

    /** Mono 16-bit little-endian PCM prepared from the exact AAC frames that were downloaded. */
    static byte[] pcm(byte[] transport) throws IOException {
        String key = audioKey(TsAac.extract(transport));
        byte[] compressed = LabResources.read("task2/preview/" + key + ".pcm.gz");
        try (var input = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return input.readAllBytes();
        }
    }

    private static String audioKey(byte[] adts) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(adts));
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError(ex);
        }
    }
}
