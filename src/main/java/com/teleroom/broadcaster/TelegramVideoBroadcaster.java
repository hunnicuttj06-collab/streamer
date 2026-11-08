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
import java.util.ArrayList;
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
        Path publicVideos = config.dataDirectory.resolve("web-videos");
        Files.createDirectories(sessionDirectory);
        Files.createDirectories(downloadDirectory);
        Files.createDirectories(publicVideos);
        TDLibSettings settings = TDLibSettings.create(config.apiToken);
        settings.setDatabaseDirectoryPath(sessionDirectory.resolve("database"));
        settings.setDownloadedFilesDirectoryPath(downloadDirectory);

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
                System.out.printf("Loading video playlist from '%s' (ID %d).%n", channel.title, channel.id);
                List<TelegramChannelPlaylist.VideoReference> references =
                        TelegramChannelPlaylist.listVideos(client, channel.id, config.maxMessages);
                List<WebPlaylistServer.Video> playlist = new ArrayList<>(references.size());
                for (int i = 0; i < references.size(); i++) {
                    var reference = references.get(i);
                    playlist.add(new WebPlaylistServer.Video(
                            String.format("video-%06d.mp4", i + 1), "Video " + (i + 1),
                            reference.file().id, reference.messageId()));
                }
                WebPlaylistServer.VideoFetcher fetcher = video -> {
                    Path downloaded = TelegramChannelPlaylist.fetch(client, video.fileId(), video.messageId());
                    Path output = publicVideos.resolve(video.fileName());
                    pipeline.prepareVideo(downloaded, output, video.messageId());
                    return output;
                };
                WebPlaylistServer server = new WebPlaylistServer(
                        config.bindAddress, config.port, publicVideos, playlist, channel.title,
                        config.dataDirectory.resolve("player-token.txt"), config.accessToken, fetcher);
                server.start();
            }
        }
    }
}
