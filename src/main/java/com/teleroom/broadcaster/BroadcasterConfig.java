package com.teleroom.broadcaster;

import it.tdlight.client.APIToken;

import java.net.URI;
import java.nio.file.Path;

final class BroadcasterConfig {
    private static final String DEFAULT_RTMP_BASE_URL = "rtmps://dc1-1.rtmp.t.me/s/";
    final APIToken apiToken;
    final String channelSelector;
    final String streamUrl;
    final String streamKey;
    final String ffmpeg;
    final String ffprobe;
    final Path dataDirectory;
    final int maxMessages;

    private BroadcasterConfig(APIToken apiToken, String channelSelector, String streamUrl, String streamKey,
            String ffmpeg, String ffprobe, Path dataDirectory, int maxMessages) {
        this.apiToken = apiToken;
        this.channelSelector = channelSelector;
        this.streamUrl = streamUrl;
        this.streamKey = streamKey;
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
        this.dataDirectory = dataDirectory;
        this.maxMessages = maxMessages;
    }

    static BroadcasterConfig fromEnvironment() {
        int apiId;
        try {
            apiId = Integer.parseInt(required("TELEGRAM_API_ID"));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("TELEGRAM_API_ID must be an integer.", exception);
        }
        if (apiId <= 0) throw new IllegalArgumentException("TELEGRAM_API_ID must be positive.");
        String apiHash = required("TELEGRAM_API_HASH");
        String channelSelector = optional("TELEGRAM_CHANNEL");
        if (channelSelector == null) channelSelector = required("TELEGRAM_CHANNEL_ID");
        String streamKey = required("TELEGRAM_STREAM_KEY");
        if (!streamKey.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("TELEGRAM_STREAM_KEY contains unsupported characters; "
                    + "provide the key only, not a URL.");
        }
        String baseUrl = valueOrDefault("TELEGRAM_RTMP_BASE_URL", DEFAULT_RTMP_BASE_URL);
        if (!baseUrl.startsWith("rtmps://") || !baseUrl.endsWith("/")) {
            throw new IllegalArgumentException("TELEGRAM_RTMP_BASE_URL must be an RTMPS base URL ending in '/'.");
        }
        String streamUrl = baseUrl + streamKey;
        URI uri;
        try {
            uri = URI.create(streamUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("The RTMPS server URL or stream key is invalid.", exception);
        }
        if (uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("The RTMPS server URL or stream key is invalid.");
        }
        return new BroadcasterConfig(new APIToken(apiId, apiHash), channelSelector, streamUrl, streamKey,
                valueOrDefault("FFMPEG_PATH", "ffmpeg"), valueOrDefault("FFPROBE_PATH", "ffprobe"),
                defaultDataDirectory(), positiveInteger("TELEGRAM_MAX_MESSAGES", 100_000));
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

    private static String valueOrDefault(String name, String defaultValue) {
        String value = optional(name);
        return value == null ? defaultValue : value;
    }

    private static int positiveInteger(String name, int defaultValue) {
        String value = optional(name);
        if (value == null) return defaultValue;
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
        return Path.of(System.getProperty("user.home"), ".local", "share", "telegram-video-broadcaster");
    }
}
