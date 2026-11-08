package com.teleroom.broadcaster;

import it.tdlight.client.APIToken;

import java.nio.file.Path;

final class BroadcasterConfig {
    final APIToken apiToken;
    final String channelSelector;
    final String ffmpeg;
    final String ffprobe;
    final Path dataDirectory;
    final int maxMessages;
    final String bindAddress;
    final int port;
    final String accessToken;

    private BroadcasterConfig(APIToken apiToken, String channelSelector, String ffmpeg,
            String ffprobe, Path dataDirectory, int maxMessages, String bindAddress,
            int port, String accessToken) {
        this.apiToken = apiToken;
        this.channelSelector = channelSelector;
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
        this.dataDirectory = dataDirectory;
        this.maxMessages = maxMessages;
        this.bindAddress = bindAddress;
        this.port = port;
        this.accessToken = accessToken;
    }

    static BroadcasterConfig fromEnvironment() {
        int apiId;
        try {
            apiId = Integer.parseInt(required("TELEGRAM_API_ID"));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("TELEGRAM_API_ID must be an integer.", exception);
        }
        if (apiId <= 0) throw new IllegalArgumentException("TELEGRAM_API_ID must be positive.");
        String channel = optional("TELEGRAM_CHANNEL");
        if (channel == null) channel = required("TELEGRAM_CHANNEL_ID");
        return new BroadcasterConfig(
                new APIToken(apiId, required("TELEGRAM_API_HASH")),
                channel,
                valueOrDefault("FFMPEG_PATH", "ffmpeg"),
                valueOrDefault("FFPROBE_PATH", "ffprobe"),
                Path.of(valueOrDefault("APP_DATA_DIR", defaultDataDirectory().toString()))
                        .toAbsolutePath().normalize(),
                positiveInteger("TELEGRAM_MAX_MESSAGES", 100_000),
                valueOrDefault("PLAYER_BIND_ADDRESS", "0.0.0.0"),
                positiveInteger("PLAYER_PORT", 8080),
                optional("PLAYER_ACCESS_TOKEN"));
    }

    private static String required(String name) {
        String value = optional(name);
        if (value == null) throw new IllegalArgumentException("Set the required environment variable " + name + ".");
        return value;
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
