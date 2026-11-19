package cbsp.lab07;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        if (args.length == 0 || args.length == 1 && args[0].equals("--help")) {
            help();
            return;
        }
        if (!acceptedArguments(args)) {
            help();
            System.exit(2);
            return;
        }

        try {
            TaskParameters parameters = new TaskParameters(args[1]);
            if (args[0].equals("--parameters")) {
                System.out.print(parameters.text());
                return;
            }
            if (args[0].equals("--send6")) {
                MidiExperiments.sendTo(args[2], port(args[3]), parameters);
                return;
            }

            Path output = parameters.outputDirectory();
            Files.createDirectories(output);
            Files.writeString(output.resolve("parameters.txt"), parameters.text());
            switch (args[0]) {
                case "--check1", "--task1" -> Task1.run(output, args[0].equals("--task1"));
                case "--check2" -> Task2.run(output, false);
                case "--task2" -> {
                    Task2.run(output, true);
                    Task2Browser.run();
                }
                case "--check3" -> Task3.run(output, parameters);
                case "--task3" -> {
                    Task3.run(output, parameters);
                    Task3Browser.run();
                }
                case "--check4" -> Task4.run(output, parameters, false);
                case "--task4" -> {
                    Task4.run(output, parameters, true);
                    Task4Browser.run();
                }
                case "--check5" -> Task5.run(output);
                case "--task5" -> {
                    Task5.run(output);
                    Task5Browser.run();
                }
                case "--check6" -> MidiExperiments.runSend(output, parameters);
                case "--check7" -> MidiExperiments.runReceive(output, parameters);
                case "--check8" -> MidiExperiments.runPlayback(output, parameters, false);
                case "--check9" -> MidiExperiments.runSession(output, parameters, false);
                case "--task6", "--task7", "--task8", "--task9" -> runMidiBrowser(args, output, parameters);
                case "--receive6" -> MidiExperiments.listen(output, port(args[2]), args.length == 4);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            System.err.println(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            System.exit(2);
        }
    }

    private static void runMidiBrowser(String[] args, Path output, TaskParameters parameters) throws Exception {
        int task = args[0].charAt("--task".length()) - '0';
        boolean sound = args.length == 3;
        switch (task) {
            case 6 -> MidiExperiments.runSend(output, parameters);
            case 7 -> MidiExperiments.runReceive(output, parameters);
            case 8 -> MidiExperiments.runPlayback(output, parameters, sound);
            case 9 -> MidiExperiments.runSession(output, parameters, sound);
        }
        MidiBrowser.run(task, parameters);
    }

    private static boolean acceptedArguments(String[] args) {
        return switch (args[0]) {
            case "--send6" -> args.length == 4;
            case "--receive6" -> args.length == 3 || args.length == 4 && args[3].equals("--sound");
            case "--task8", "--task9" -> args.length == 2 || args.length == 3 && args[2].equals("--sound");
            default -> args.length == 2 && args[0].matches("--(parameters|(check|task)[1-9])");
        };
    }

    private static int port(String text) {
        int port = Integer.parseInt(text);
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("UDP port must be between 1 and 65535.");
        }
        return port;
    }

    private static void help() {
        System.out.println("""
                Usage: Main <command> <eight-digit-student-id> [command arguments]
                --parameters   Show assignment settings
                --check1       Run loopback HTTP requests and export a CSV
                --task1        Export the same CSV and start the browser experiment
                --check2       Fetch HLS playlists and selected segments and export CSVs
                --task2        Save the CSVs and downloaded media, then start the HLS browser experiment
                --check3       Simulate adaptive bitrate and export buffer data and plots
                --task3        Export the same results and start the interactive buffer comparison
                --check4       Simulate packet jitter and export a playout plot
                --task4        Write the CSV, plot and comparison WAVs, then start the interactive jitter demo
                --check5       Measure playback latencies and export a timeline
                --task5        Export the same results and start the interactive latency comparison
                --check6       Send MIDI datagrams and inspect the received bytes
                --check7       Receive MIDI datagrams and calculate playout deadlines
                --check8       Run the scheduled MIDI playout test over UDP
                --check9       Run the complete MIDI session, including a missing Note Off
                --task6..9     Run the selected MIDI exercise and open the browser timeline
                --task8 / --task9 ID --sound   Play the run with Java Sound before opening the timeline
                --receive6 ID PORT [--sound]   Receive and schedule UDP MIDI events
                --send6 ID HOST PORT           Send the prepared melody to a receiver
                Output: output/<student-id>/""");
    }
}
