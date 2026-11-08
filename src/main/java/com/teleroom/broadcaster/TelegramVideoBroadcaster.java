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
        System.out.println("Welcome. The app will request any missing setup information one item at a time.");
        BroadcasterConfig config = BroadcasterConfig.fromEnvironment();
        Files.createDirectories(config.dataDirectory);
        MediaPipeline pipeline = new MediaPipeline(config.ffmpeg, config.ffprobe,
                new MediaPipeline.ProcessRunner());
        pipeline.verifyTools();

        Init.init();
        Path sessionDirectory = config.dataDirectory.resolve("tdlib-session");
        Path downloadDirectory = config.dataDirectory.resolve("telegram-downloads");
        Files.createDirectories(sessionDirectory);
        Files.createDirectories(downloadDirectory);
        TDLibSettings settings = TDLibSettings.create(config.apiToken);
        settings.setDatabaseDirectoryPath(sessionDirectory.resolve("database"));
        settings.setDownloadedFilesDirectoryPath(downloadDirectory);

        List<TelegramChannelPlaylist.DownloadedVideo> videos;
        String channelTitle;
        try (SimpleTelegramClientFactory factory = new SimpleTelegramClientFactory()) {
            SimpleTelegramClient client = factory.builder(settings)
                    .build(AuthenticationSupplier.consoleLogin());
            try (client) {
                System.out.println("Telegram sign-in: follow the next prompts for your phone number, login code, "
                        + "and two-step password if requested. These are handled by TDLib.");
                client.getMeAsync().get(TELEGRAM_TIMEOUT.toMinutes(), TimeUnit.MINUTES);
                TdApi.Chat channel = TelegramChannelResolver.resolve(
                        client, config.channelSelector, TELEGRAM_TIMEOUT);
                if (!(channel.type instanceof TdApi.ChatTypeSupergroup type) || !type.isChannel) {
                    throw new IllegalArgumentException("The selected chat is not a Telegram channel.");
                }
                channelTitle = channel.title;
                System.out.printf("Loading videos from '%s' (ID %d).%n", channel.title, channel.id);
                videos = TelegramChannelPlaylist.download(client, channel.id,
                        downloadDirectory, config.maxMessages);
            }
        }

        Path publicVideos = config.dataDirectory.resolve("web-videos");
        List<WebPlaylistServer.Video> playlist = pipeline.prepareForWeb(videos, publicVideos);
        WebPlaylistServer server = new WebPlaylistServer(
                config.bindAddress, config.port, publicVideos, playlist, channelTitle,
                config.dataDirectory.resolve("player-token.txt"), config.accessToken);
        server.start();
    }
}
