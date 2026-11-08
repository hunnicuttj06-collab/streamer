package com.teleroom.broadcaster;

import it.tdlight.client.APIToken;
import it.tdlight.util.ScannerUtils;

import java.io.Console;
import java.net.URI;
import java.nio.file.Path;

final class BroadcasterConfig {
    final APIToken apiToken;
    final String channelSelector;
    final String phoneNumber;
    final String ffmpeg;
    final String ffprobe;
    final Path dataDirectory;
    final int maxMessages;
    final String bindAddress;
    final int port;
    final String publicBaseUrl;
    final String accessToken;

    private BroadcasterConfig(APIToken apiToken, String channelSelector, String phoneNumber, String ffmpeg,
            String ffprobe, Path dataDirectory, int maxMessages, String bindAddress,
            int port, String publicBaseUrl, String accessToken) {
        this.apiToken = apiToken;
        this.channelSelector = channelSelector;
        this.phoneNumber = phoneNumber;
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
        this.dataDirectory = dataDirectory;
        this.maxMessages = maxMessages;
        this.bindAddress = bindAddress;
        this.port = port;
        this.publicBaseUrl = publicBaseUrl;
        this.accessToken = accessToken;
    }

    static BroadcasterConfig fromEnvironment() throws Exception {
        int apiId = readApiId();
        String apiHash = requiredOrPrompt("TELEGRAM_API_HASH",
                "Step 2 of 3 - Enter your Telegram API hash from my.telegram.org:", true);
        String channel = optional("TELEGRAM_CHANNEL");
        if (channel == null) channel = optional("TELEGRAM_CHANNEL_ID");
        if (channel == null) {
            channel = requiredOrPrompt("TELEGRAM_CHANNEL",
                    "Step 3 of 3 - Enter your channel ID, public link, invite link, username, or exact chat title:", false);
        }

        return new BroadcasterConfig(
                new APIToken(apiId, apiHash),
                channel,
                optional("TELEGRAM_PHONE_NUMBER"),
                valueOrDefault("FFMPEG_PATH", "ffmpeg"),
                valueOrDefault("FFPROBE_PATH", "ffprobe"),
                Path.of(valueOrDefault("APP_DATA_DIR", defaultDataDirectory().toString()))
                        .toAbsolutePath().normalize(),
                positiveInteger("TELEGRAM_MAX_MESSAGES", 100_000),
                valueOrDefault("PLAYER_BIND_ADDRESS", "0.0.0.0"),
                positiveInteger("PLAYER_PORT", 8080),
                validatePublicBaseUrl(optional("PLAYER_PUBLIC_BASE_URL")),
                optional("PLAYER_ACCESS_TOKEN"));
    }

    static String validatePublicBaseUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().replaceAll("/+$", "");
        try {
            URI uri = URI.create(normalized);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("PLAYER_PUBLIC_BASE_URL must be an http(s) origin, "
                    + "for example https://player.example.com.", exception);
        }
        return normalized;
    }

    private static int readApiId() throws Exception {
        String configured = optional("TELEGRAM_API_ID");
        while (true) {
            String value = configured;
            if (value == null) {
                value = prompt("Step 1 of 3 - Enter your Telegram API ID from my.telegram.org:", false);
            }
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed > 0) return parsed;
            } catch (NumberFormatException ignored) {
                // Re-prompt below with a simple explanation.
            }
            System.out.println("The API ID must be a positive whole number. Please try again.");
            configured = null;
        }
    }

    private static String requiredOrPrompt(String name, String message, boolean secret) throws Exception {
        String value = optional(name);
        if (value == null) value = prompt(message, secret);
        return value;
    }

    private static String prompt(String message, boolean secret) throws Exception {
        Console console = System.console();
        while (true) {
            String answer;
            if (console != null && secret) {
                char[] chars = console.readPassword("%s ", message);
                answer = chars == null ? "" : new String(chars).trim();
                if (chars != null) java.util.Arrays.fill(chars, '\0');
            } else if (console != null) {
                answer = console.readLine("%s ", message);
                answer = answer == null ? "" : answer.trim();
            } else {
                if (secret) System.out.println("Note: input cannot be hidden in this terminal.");
                answer = ScannerUtils.askParameter("setup", message);
                answer = answer == null ? "" : answer.trim();
            }
            if (!answer.isBlank()) return answer;
            System.out.println("This value is required. Please try again.");
        }
    }

    private static String optional(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String valueOrDefault(String name, String fallback) {
        String value = optional(name);
        return value == null ? fallback : value;
    }

    private static int positiveInteger(String name, int fallback) {
        String value = optional(name);
        if (value == null) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) throw new IllegalArgumentException(name + " must be positive.");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer.", exception);
        }
    }

    private static Path defaultDataDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "Teleroom", "TelegramVideoBroadcaster");
        }
        return Path.of(System.getProperty("user.home"), ".local", "share",
                "telegram-video-broadcaster");
    }
}
