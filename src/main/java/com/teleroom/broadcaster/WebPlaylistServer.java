package com.teleroom.broadcaster;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/** Serves the player and fetches each Telegram video only when playback requests it. */
final class WebPlaylistServer {
    private final String bindAddress;
    private final int port;
    private final Path videoDirectory;
    private final List<Video> videos;
    private final String channelTitle;
    private final Path tokenFile;
    private final String configuredToken;
    private final VideoFetcher videoFetcher;

    WebPlaylistServer(String bindAddress, int port, Path videoDirectory, List<Video> videos,
            String channelTitle, Path tokenFile, String configuredToken, VideoFetcher videoFetcher) {
        this.bindAddress = bindAddress;
        this.port = port;
        this.videoDirectory = videoDirectory.toAbsolutePath().normalize();
        this.videos = List.copyOf(videos);
        this.channelTitle = channelTitle;
        this.tokenFile = tokenFile;
        this.configuredToken = configuredToken;
        this.videoFetcher = videoFetcher;
    }

    void start() throws Exception {
        String token = getAccessToken();
        HttpServer server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        server.createContext("/", exchange -> handle(exchange, token));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("\nPlaylist server is ready.");
        System.out.println("Open this private player URL in your browser or configure it as your Telegram Mini App:");
        System.out.printf("http://localhost:%d/?token=%s%n", port, token);
        System.out.println("Videos are fetched and prepared only when selected for playback.");
        System.out.println("For public Mini App use, put this server behind HTTPS (see Caddy instructions in README).");
        new CountDownLatch(1).await();
    }

    private String getAccessToken() throws Exception {
        if (configuredToken != null) {
            if (configuredToken.length() < 32) {
                throw new IllegalArgumentException("PLAYER_ACCESS_TOKEN must be at least 32 characters.");
            }
            return configuredToken;
        }
        if (Files.exists(tokenFile)) return Files.readString(tokenFile).trim();
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Files.createDirectories(tokenFile.getParent());
        Files.writeString(tokenFile, token + System.lineSeparator(), StandardCharsets.UTF_8);
        return token;
    }

    private void handle(HttpExchange exchange, String token) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") && exchange.getRequestMethod().equals("GET")) {
                servePlayer(exchange);
            } else if (path.equals("/api/playlist") && authorized(exchange, token)) {
                servePlaylist(exchange);
            } else if (path.startsWith("/videos/") && authorized(exchange, token)) {
                serveVideo(exchange, path.substring("/videos/".length()));
            } else if (path.equals("/api/playlist") || path.startsWith("/videos/")) {
                sendText(exchange, 403, "Access denied. Open the private player link.");
            } else {
                sendText(exchange, 404, "Not found.");
            }
        } catch (Exception exception) {
            if (exchange.getResponseCode() == -1) sendText(exchange, 500, "The request could not be completed.");
        }
    }

    private static boolean authorized(HttpExchange exchange, String token) {
        String supplied = queryParameter(exchange.getRequestURI().getRawQuery(), "token");
        if (supplied == null) return false;
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    private void servePlayer(HttpExchange exchange) throws IOException {
        try (InputStream resource = WebPlaylistServer.class.getResourceAsStream("/web/index.html")) {
            if (resource == null) {
                sendText(exchange, 500, "Player page is missing.");
                return;
            }
            byte[] content = resource.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
        }
    }

    private void servePlaylist(HttpExchange exchange) throws IOException {
        StringBuilder json = new StringBuilder("{\"channel\":\"")
                .append(jsonEscape(channelTitle)).append("\",\"videos\":[");
        for (int i = 0; i < videos.size(); i++) {
            Video video = videos.get(i);
            if (i > 0) json.append(',');
            json.append("{\"title\":\"").append(jsonEscape(video.title()))
                    .append("\",\"url\":\"/videos/").append(video.fileName())
                    .append("?token=").append(queryEncode(queryParameter(
                            exchange.getRequestURI().getRawQuery(), "token")))
                    .append("\"}");
        }
        json.append("]}");
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private void serveVideo(HttpExchange exchange, String fileName) throws Exception {
        if (!fileName.matches("video-[0-9]{6}\\.mp4") || !exchange.getRequestMethod().equals("GET")) {
            sendText(exchange, 404, "Video not found.");
            return;
        }
        Video video = videos.stream().filter(item -> item.fileName().equals(fileName)).findFirst().orElse(null);
        if (video == null) {
            sendText(exchange, 404, "Video not found.");
            return;
        }
        Path file = videoFetcher.fetch(video).toAbsolutePath().normalize();
        if (!file.startsWith(videoDirectory) || !Files.isRegularFile(file)) {
            sendText(exchange, 404, "Video not found.");
            return;
        }
        long size = Files.size(file);
        long start = 0;
        long end = size - 1;
        int status = 200;
        String range = exchange.getRequestHeaders().getFirst("Range");
        if (range != null) {
            long[] parsed = parseRange(range, size);
            if (parsed == null) {
                exchange.getResponseHeaders().set("Content-Range", "bytes */" + size);
                exchange.sendResponseHeaders(416, -1);
                return;
            }
            start = parsed[0];
            end = parsed[1];
            status = 206;
        }
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "video/mp4");
        headers.set("Accept-Ranges", "bytes");
        headers.set("Cache-Control", "private, no-store");
        if (status == 206) headers.set("Content-Range", "bytes " + start + "-" + end + "/" + size);
        long length = end - start + 1;
        exchange.sendResponseHeaders(status, length);
        try (InputStream input = Files.newInputStream(file)) {
            input.skipNBytes(start);
            byte[] buffer = new byte[64 * 1024];
            long remaining = length;
            while (remaining > 0) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) break;
                exchange.getResponseBody().write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    private static long[] parseRange(String range, long size) {
        if (!range.startsWith("bytes=") || range.indexOf(',') >= 0 || size == 0) return null;
        String[] parts = range.substring(6).split("-", -1);
        if (parts.length != 2) return null;
        try {
            long start;
            long end;
            if (parts[0].isEmpty()) {
                long suffix = Long.parseLong(parts[1]);
                if (suffix <= 0) return null;
                start = Math.max(0, size - suffix);
                end = size - 1;
            } else {
                start = Long.parseLong(parts[0]);
                end = parts[1].isEmpty() ? size - 1 : Long.parseLong(parts[1]);
            }
            if (start < 0 || start >= size || end < start) return null;
            return new long[]{start, Math.min(end, size - 1)};
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String queryParameter(String query, String key) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (URLDecoder.decode(pair[0], StandardCharsets.UTF_8).equals(key)) {
                return URLDecoder.decode(pair.length == 2 ? pair[1] : "", StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String queryEncode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    @FunctionalInterface
    interface VideoFetcher {
        Path fetch(Video video) throws Exception;
    }

    record Video(String fileName, String title, int fileId, long messageId) { }
}
