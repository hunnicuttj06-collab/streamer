package com.teleroom.broadcaster;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
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

    List<WebPlaylistServer.Video> prepareForWeb(
            List<TelegramChannelPlaylist.DownloadedVideo> videos, Path outputDirectory) throws Exception {
        Files.createDirectories(outputDirectory);
        List<WebPlaylistServer.Video> result = new ArrayList<>(videos.size());
        for (int i = 0; i < videos.size(); i++) {
            var video = videos.get(i);
            String name = String.format("video-%06d.mp4", i + 1);
            Path output = outputDirectory.resolve(name);
            System.out.printf("Preparing video %d of %d for the web player.%n", i + 1, videos.size());
            normalize(video.path(), output, video.messageId());
            result.add(new WebPlaylistServer.Video(name, "Video " + (i + 1)));
        }
        return List.copyOf(result);
    }

    private void normalize(Path input, Path output, long messageId) throws Exception {
        boolean hasAudio = hasAudioStream(input, messageId);
        List<String> command = new ArrayList<>(List.of(ffmpeg, "-hide_banner", "-nostdin",
                "-loglevel", "error", "-y", "-i", input.toAbsolutePath().toString()));
        if (!hasAudio) command.addAll(List.of("-f", "lavfi", "-i",
                "anullsrc=channel_layout=stereo:sample_rate=44100"));
        command.addAll(List.of("-map", "0:v:0", "-map", hasAudio ? "0:a:0" : "1:a:0",
                "-vf", VIDEO_FILTER, "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
                "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "128k", "-ar", "44100",
                "-ac", "2", "-af", "apad", "-shortest", "-movflags", "+faststart",
                output.toAbsolutePath().toString()));
        processRunner.run(command);
    }

    private boolean hasAudioStream(Path input, long messageId) throws Exception {
        String output = processRunner.capture(List.of(ffprobe, "-v", "error", "-select_streams", "a:0",
                "-show_entries", "stream=codec_type", "-of", "csv=p=0", input.toAbsolutePath().toString()),
                "FFprobe for message " + messageId);
        return output.lines().anyMatch(line -> line.trim().equals("audio"));
    }

    static final class ProcessRunner {
        private final AtomicReference<Process> activeProcess = new AtomicReference<>();

        ProcessRunner() {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Process process = activeProcess.get();
                if (process != null && process.isAlive()) process.destroy();
            }, "playlist-process-shutdown"));
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
                if (process.waitFor() != 0) throw new IOException(toolName + " failed.");
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

        void run(List<String> command) throws Exception {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            activeProcess.set(process);
            try (InputStream input = process.getInputStream()) {
                input.transferTo(System.out);
                if (process.waitFor() != 0) throw new IOException("FFmpeg could not prepare a video.");
            } catch (InterruptedException exception) {
                process.destroy();
                Thread.currentThread().interrupt();
                throw exception;
            } finally {
                activeProcess.compareAndSet(process, null);
                stopIfAlive(process);
            }
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
