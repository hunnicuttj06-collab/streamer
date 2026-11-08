package com.teleroom.broadcaster;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class MediaPipeline {
    private static final String VIDEO_FILTER = "scale=1280:720:force_original_aspect_ratio=decrease,"
            + "pad=1280:720:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=30,format=yuv420p";
    private static final String CACHE_VERSION = "media-v2";
    private static final int VIDEO_ENCODER_THREADS = 2;
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
        String cacheKey = cacheKey(input);
        Path metadata = output.resolveSibling(output.getFileName() + ".meta");
        if (Files.isRegularFile(output) && Files.size(output) > 0
                && Files.isRegularFile(metadata) && Files.readString(metadata).equals(cacheKey)) {
            System.out.printf("Reusing prepared video for message %d.%n", messageId);
            return;
        }

        ProbeInfo probe = probe(input, messageId);
        boolean hasAudio = probe.hasAudio();
        List<String> command = new ArrayList<>(List.of(ffmpeg, "-hide_banner", "-nostdin",
                "-loglevel", "error", "-y", "-i", input.toAbsolutePath().toString()));
        if (!hasAudio) command.addAll(List.of("-f", "lavfi", "-i",
                "anullsrc=channel_layout=stereo:sample_rate=44100"));
        command.addAll(List.of("-map", "0:v:0", "-map", hasAudio ? "0:a:0" : "1:a:0"));

        if (probe.canCopyVideo()) {
            // Avoid the expensive decode/scale/re-encode path for browser-friendly 720p H.264.
            command.addAll(List.of("-c:v", "copy"));
        } else {
            command.addAll(List.of("-vf", VIDEO_FILTER, "-c:v", "libx264", "-preset", "veryfast",
                    "-crf", "23", "-threads:v", Integer.toString(VIDEO_ENCODER_THREADS),
                    "-pix_fmt", "yuv420p"));
        }
        if (hasAudio && probe.audioCodec().equals("aac")) {
            command.addAll(List.of("-c:a", "copy"));
        } else {
            command.addAll(List.of("-c:a", "aac", "-b:a", "128k", "-ar", "44100", "-ac", "2",
                    "-af", "apad"));
        }
        command.addAll(List.of("-shortest", "-movflags", "+faststart", output.toAbsolutePath().toString()));
        processRunner.run(command);
        Files.writeString(metadata, cacheKey, StandardCharsets.UTF_8);
    }

    private ProbeInfo probe(Path input, long messageId) throws Exception {
        String output = processRunner.capture(List.of(ffprobe, "-v", "error", "-show_entries",
                "stream=codec_type,codec_name,width,height,pix_fmt", "-of", "flat",
                input.toAbsolutePath().toString()), "FFprobe for message " + messageId);
        Map<Integer, Map<String, String>> streams = new TreeMap<>();
        for (String line : output.lines().toList()) {
            int prefixEnd = line.indexOf('=');
            if (prefixEnd < 0) continue;
            String key = line.substring(0, prefixEnd);
            if (!key.startsWith("streams.stream.")) continue;
            int fieldStart = "streams.stream.".length();
            int fieldEnd = key.indexOf('.', fieldStart);
            if (fieldEnd < 0) continue;
            try {
                int index = Integer.parseInt(key.substring(fieldStart, fieldEnd));
                String value = line.substring(prefixEnd + 1).trim();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                streams.computeIfAbsent(index, ignored -> new TreeMap<>())
                        .put(key.substring(fieldEnd + 1), value);
            } catch (NumberFormatException ignored) {
                // Ignore unexpected FFprobe output lines.
            }
        }

        Map<String, String> video = streams.values().stream()
                .filter(stream -> "video".equals(stream.get("codec_type"))).findFirst().orElse(Map.of());
        Map<String, String> audio = streams.values().stream()
                .filter(stream -> "audio".equals(stream.get("codec_type"))).findFirst().orElse(Map.of());
        if (video.isEmpty()) throw new IOException("No video stream found for message " + messageId + ".");
        int width = parseDimension(video.get("width"));
        int height = parseDimension(video.get("height"));
        boolean canCopyVideo = "h264".equals(video.get("codec_name"))
                && "yuv420p".equals(video.get("pix_fmt"))
                && width > 0 && width <= 1280 && height > 0 && height <= 720;
        return new ProbeInfo(!audio.isEmpty(), canCopyVideo, audio.getOrDefault("codec_name", ""));
    }

    private static int parseDimension(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException | NullPointerException exception) {
            return -1;
        }
    }

    private static String cacheKey(Path input) throws IOException {
        Path absolute = input.toAbsolutePath().normalize();
        FileTime modified = Files.getLastModifiedTime(absolute);
        return CACHE_VERSION + "\n" + absolute + "\n" + Files.size(absolute) + "\n" + modified.toMillis();
    }

    private record ProbeInfo(boolean hasAudio, boolean canCopyVideo, String audioCodec) { }

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
