package com.teleroom.broadcaster;

import it.tdlight.Init;
import it.tdlight.client.AuthenticationSupplier;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.client.SimpleTelegramClientFactory;
import it.tdlight.client.TDLibSettings;
import it.tdlight.jni.TdApi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class TelegramVideoBroadcaster {
    private static final Duration TELEGRAM_TIMEOUT = Duration.ofMinutes(5);

    private TelegramVideoBroadcaster() { }

    public static void main(String[] args) throws Exception {
        BroadcasterConfig config = BroadcasterConfig.fromEnvironment();
        Files.createDirectories(config.dataDirectory);
        MediaPipeline.ProcessRunner processRunner = new MediaPipeline.ProcessRunner();
        MediaPipeline mediaPipeline = new MediaPipeline(config.ffmpeg, config.ffprobe, processRunner);
        mediaPipeline.verifyTools();

        Init.init();
        Path sessionDirectory = config.dataDirectory.resolve("tdlib-session");
        Path downloadDirectory = config.dataDirectory.resolve("telegram-downloads");
        Files.createDirectories(sessionDirectory);
        Files.createDirectories(downloadDirectory);
        TDLibSettings settings = TDLibSettings.create(config.apiToken);
        settings.setDatabaseDirectoryPath(sessionDirectory.resolve("database"));
        settings.setDownloadedFilesDirectoryPath(downloadDirectory);

        try (SimpleTelegramClientFactory factory = new SimpleTelegramClientFactory()) {
            SimpleTelegramClient client = factory.builder(settings)
                    .build(AuthenticationSupplier.consoleLogin());
            try (client) {
                client.getMeAsync().get(TELEGRAM_TIMEOUT.toMinutes(), TimeUnit.MINUTES);
                TdApi.Chat channel = TelegramChannelResolver.resolve(
                        client, config.channelSelector, TELEGRAM_TIMEOUT);
                if (!(channel.type instanceof TdApi.ChatTypeSupergroup supergroupType)
                        || !supergroupType.isChannel) {
                    throw new IllegalArgumentException("The selected chat is not a Telegram channel.");
                }
                System.out.printf("Reading video posts from channel '%s' (ID %d).%n",
                        channel.title, channel.id);
                List<TelegramChannelPlaylist.DownloadedVideo> videos = TelegramChannelPlaylist.download(
                        client, channel.id, downloadDirectory, config.maxMessages);
                Path playlist = mediaPipeline.prepare(videos, config.dataDirectory);
                mediaPipeline.publish(playlist, config.streamUrl, config.streamKey);
            }
        }
    }
}
