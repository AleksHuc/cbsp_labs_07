package cbsp.lab07;

import java.nio.file.Path;
import java.util.Arrays;

record TaskParameters(String studentId) {
    static final String VERSION = "lab07-v1";

    TaskParameters {
        if (studentId == null || !studentId.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("Student ID must contain exactly eight digits.");
        }
    }

    Path outputDirectory() {
        return Path.of("output", studentId);
    }

    int[] throughputKbps() {
        return switch (lastDigit() % 3) {
            case 0 -> new int[]{96, 80, 120, 100};
            case 1 -> new int[]{112, 88, 96, 144};
            default -> new int[]{128, 72, 104, 120};
        };
    }

    long packet4ArrivalMs() {
        return 98L + lastDigit() % 3 * 6L;
    }

    int midiNote() {
        return new int[]{60, 62, 65, 67}[lastDigit() % 4];
    }

    String text() {
        return "version=" + VERSION + "\nstudentId=" + studentId + "\nthroughputKbps="
                + Arrays.toString(throughputKbps()) + "\npacket4ArrivalMs=" + packet4ArrivalMs()
                + "\nmidiNote=" + midiNote() + "\n";
    }

    private int lastDigit() {
        return studentId.charAt(studentId.length() - 1) - '0';
    }
}
