package cbsp.lab07;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Extracts the ADTS audio carried by the lab's audio-only MPEG-TS segments. */
final class TsAac {
    private static final int PACKET_BYTES = 188;
    private static final int PES_HEADER_BYTES = 9;

    private TsAac() { }

    static byte[] extract(byte[] transport) throws IOException {
        if (transport.length % PACKET_BYTES != 0) {
            throw new IOException("Incomplete transport stream packet");
        }
        var audio = new ByteArrayOutputStream();
        int audioPid = -1;
        int remaining = 0;
        for (int packet = 0; packet < transport.length; packet += PACKET_BYTES) {
            if (unsigned(transport[packet]) != 0x47) {
                throw new IOException("Missing transport stream sync byte");
            }
            int flags = transport[packet + 3];
            if ((flags & 0x10) == 0) {
                continue;
            }
            int end = packet + PACKET_BYTES;
            int payload = packet + 4;
            if ((flags & 0x20) != 0) {
                payload += 1 + unsigned(transport[payload]);
            }
            if (payload >= end) {
                throw new IOException("Invalid transport stream payload offset");
            }

            int pid = ((transport[packet + 1] & 0x1f) << 8) | unsigned(transport[packet + 2]);
            boolean startsUnit = (transport[packet + 1] & 0x40) != 0;
            boolean audioStart = startsUnit && startsAudioPes(transport, payload, end);
            if (audioPid == -1 && audioStart) {
                audioPid = pid;
            }
            if (pid != audioPid) {
                continue;
            }

            if (startsUnit) {
                if (!audioStart || remaining > 0) {
                    throw new IOException("Incomplete or invalid audio PES packet");
                }
                int packetLength = (unsigned(transport[payload + 4]) << 8) | unsigned(transport[payload + 5]);
                int optionalHeaderLength = unsigned(transport[payload + 8]);
                // The PES length begins after its length field and includes three further header bytes.
                remaining = packetLength - 3 - optionalHeaderLength;
                payload += PES_HEADER_BYTES + optionalHeaderLength;
                if (remaining <= 0 || payload > end) {
                    throw new IOException("Invalid audio PES payload length");
                }
            }

            int count = Math.min(remaining, end - payload);
            audio.write(transport, payload, count);
            remaining -= count;
        }
        if (audio.size() == 0 || remaining != 0) {
            throw new IOException("No complete AAC audio in this segment");
        }
        byte[] adts = audio.toByteArray();
        verifyFrames(adts);
        return adts;
    }

    private static boolean startsAudioPes(byte[] transport, int start, int end) {
        return end - start >= PES_HEADER_BYTES && transport[start] == 0 && transport[start + 1] == 0
                && transport[start + 2] == 1 && (transport[start + 3] & 0xe0) == 0xc0;
    }

    private static void verifyFrames(byte[] audio) throws IOException {
        for (int start = 0; start < audio.length;) {
            if (audio.length - start < 7 || unsigned(audio[start]) != 0xff || (audio[start + 1] & 0xf6) != 0xf0) {
                throw new IOException("Invalid AAC frame header");
            }
            int length = ((audio[start + 3] & 3) << 11) | (unsigned(audio[start + 4]) << 3)
                    | (unsigned(audio[start + 5]) >>> 5);
            if (length < 7 || length > audio.length - start) {
                throw new IOException("Incomplete AAC frame");
            }
            start += length;
        }
    }

    private static int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }
}
