package com.teleroom.broadcaster;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class MediaPipeline {
    private static final String VIDEO_FILTER = "scale=1280:720:force_original_aspect_ratio=decrease,"
            + "pad=1280:720:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=30,format=yuv420p";
    private final String ffmpeg;
    private final String ffprobe;
    private final ProcessRunner processRunner;

    MediaPipeline(String ffmpeg, String ffprobe, ProcessRunner processRunner) {
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
        this.processRunner = processRunner;
    }

    void verifyTools() throws Exception {
        processRunner.capture(List.of(ffmpeg, "-version"), "FFmpeg");
        processRunner.capture(List.of(ffprobe, "-version"), "FFprobe");
    }

    Path prepare(List<TelegramChannelPlaylist.DownloadedVideo> videos, Path dataDirectory) throws Exception {
        Path normalizedDirectory = dataDirectory.resolve("normalized");
        Files.createDirectories(normalizedDirectory);
        List<Path> normalizedVideos = new ArrayList<>(videos.size());
        for (int i = 0; i < videos.size(); i++) {
            var video = videos.get(i);
            Path output = normalizedDirectory.resolve(String.format("video-%06d.mp4", i + 1));
            System.out.printf("Preparing video %d of %d.%n", i + 1, videos.size());
            normalize(video.path(), output, video.messageId());
            normalizedVideos.add(output);
        }
        Path playlist = dataDirectory.resolve("playlist.ffconcat");
        List<String> lines = new ArrayList<>(normalizedVideos.size() + 1);
        lines.add("ffconcat version 1.0");
        for (Path video : normalizedVideos) {
            lines.add("file '" + escapeConcatPath(video.toAbsolutePath().normalize()) + "'");
        }
        Files.write(playlist, lines, StandardCharsets.UTF_8);
        return playlist;
    }

    void publish(Path playlist, String streamUrl, String streamKey) throws Exception {
        System.out.println("Publishing the playlist to Telegram. Press Ctrl+C to stop.");
        processRunner.run(List.of(ffmpeg, "-hide_banner", "-nostdin", "-loglevel", "warning", "-nostats",
                "-progress", "pipe:1", "-re", "-f", "concat", "-safe", "0", "-i",
                playlist.toAbsolutePath().toString(), "-c:v", "copy", "-c:a", "aac", "-b:a", "128k",
                "-ar", "44100", "-ac", "2", "-af", "aresample=async=1", "-f", "flv", streamUrl),
                streamKey, streamUrl);
        System.out.println("The final video finished; the Telegram stream has ended.");
    }

    private void normalize(Path input, Path output, long messageId) throws Exception {
        boolean hasAudio = hasAudioStream(input, messageId);
        List<String> command = new ArrayList<>(List.of(ffmpeg, "-hide_banner", "-nostdin", "-loglevel",
                "error", "-y", "-i", input.toAbsolutePath().toString()));
        if (!hasAudio) command.addAll(List.of("-f", "lavfi", "-i",
                "anullsrc=channel_layout=stereo:sample_rate=44100"));
        command.addAll(List.of("-map", "0:v:0", "-map", hasAudio ? "0:a:0" : "1:a:0", "-vf",
                VIDEO_FILTER, "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-pix_fmt",
                "yuv420p", "-c:a", "aac", "-b:a", "128k", "-ar", "44100", "-ac", "2", "-af",
                "apad", "-shortest", "-movflags", "+faststart", output.toAbsolutePath().toString()));
        processRunner.run(command, null, null);
    }

    private boolean hasAudioStream(Path input, long messageId) throws Exception {
        String output = processRunner.capture(List.of(ffprobe, "-v", "error", "-select_streams", "a:0",
                "-show_entries", "stream=codec_type", "-of", "csv=p=0", input.toAbsolutePath().toString()),
                "FFprobe for message " + messageId);
        return output.lines().anyMatch(line -> line.trim().equals("audio"));
    }

    private static String escapeConcatPath(Path path) {
        return path.toString().replace('\\', '/').replace("'", "'\\''");
    }

    static final class ProcessRunner {
        private final AtomicReference<Process> activeProcess = new AtomicReference<>();
        ProcessRunner() {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Process process = activeProcess.get();
                if (process != null && process.isAlive()) process.destroy();
            }, "broadcaster-process-shutdown"));
        }

        String capture(List<String> command, String toolName) throws Exception {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            activeProcess.set(process);
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() < 8192) output.append(line).append(System.lineSeparator());
                }
                int exitCode = process.waitFor();
                if (exitCode != 0) throw new IOException(toolName + " exited with code " + exitCode + ".");
                return output.toString();
            } catch (InterruptedException exception) {
                process.destroy();
                Thread.currentThread().interrupt();
                throw exception;
            } finally {
                activeProcess.compareAndSet(process, null);
                stopIfAlive(process);
            }
        }

        void run(List<String> command, String... secrets) throws Exception {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            activeProcess.set(process);
            String progressTime = "unknown";
            String speed = "unknown";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String safe = redact(line, secrets);
                    if (safe.startsWith("out_time=")) progressTime = safe.substring("out_time=".length());
                    else if (safe.startsWith("speed=")) speed = safe.substring("speed=".length());
                    else if (safe.startsWith("progress=")) {
                        System.out.printf("Stream progress: %s, speed %s.%n", progressTime, speed);
                    } else if (!safe.matches("^[A-Za-z0-9_]+=.*$")) System.out.println(safe);
                }
                int exitCode = process.waitFor();
                if (exitCode != 0) throw new IOException("FFmpeg exited with code " + exitCode + ".");
            } catch (InterruptedException exception) {
                process.destroy();
                Thread.currentThread().interrupt();
                throw exception;
            } finally {
                activeProcess.compareAndSet(process, null);
                stopIfAlive(process);
            }
        }

        private static String redact(String line, String... secrets) {
            String safe = line;
            if (secrets != null) for (String secret : secrets) {
                if (secret != null && !secret.isBlank()) safe = safe.replace(secret, "[REDACTED]");
            }
            return safe;
        }

        private static void stopIfAlive(Process process) throws InterruptedException {
            if (process.isAlive()) {
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                }
            }
        }
    }
}
