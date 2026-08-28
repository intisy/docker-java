package io.github.intisy.docker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Minimal internal helper to run a native command with a timeout,
 * capturing combined stdout and stderr without deadlocking on full pipes.
 *
 * @author Finn Birich
 */
final class ProcessRunner {

    static final class Result {
        final int exitCode;
        final String output;
        final boolean timedOut;

        Result(int exitCode, String output, boolean timedOut) {
            this.exitCode = exitCode;
            this.output = output;
            this.timedOut = timedOut;
        }
    }

    private ProcessRunner() {
    }

    static Result run(long timeoutMillis, String... command) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        final StringBuilder output = new StringBuilder();
        Thread drainer = new Thread(new Runnable() {
            @Override
            public void run() {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (output) {
                            output.append(line).append('\n');
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        });
        drainer.setDaemon(true);
        drainer.start();

        try {
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                synchronized (output) {
                    return new Result(Integer.MIN_VALUE, output.toString(), true);
                }
            }
            drainer.join(5000);
            synchronized (output) {
                return new Result(process.exitValue(), output.toString(), false);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Interrupted while waiting for command: " + command[0], e);
        }
    }
}
