package cbsp.lab07;

import java.io.IOException;

final class LabResources {
    private LabResources() { }

    static byte[] read(String name) throws IOException {
        try (var stream = LabResources.class.getResourceAsStream("/cbsp/lab07/" + name)) {
            if (stream == null) {
                throw new IOException("Missing Lab 07 resource: " + name);
            }
            return stream.readAllBytes();
        }
    }
}
