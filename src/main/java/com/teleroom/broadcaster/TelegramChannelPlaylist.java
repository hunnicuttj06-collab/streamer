package com.teleroom.broadcaster;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

final class TelegramChannelPlaylist {
    private static final int PAGE_SIZE = 100;
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofHours(6);

    private TelegramChannelPlaylist() { }

    /** Lists message metadata only; media is fetched when the player requests a specific video. */
    static List<VideoReference> listVideos(SimpleTelegramClient client, long chatId, int maxMessages)
            throws Exception {
        List<VideoReference> references = findVideos(client, chatId, maxMessages);
        if (references.isEmpty()) {
            throw new IllegalStateException("No video posts were found in the channel history.");
        }
        return List.copyOf(references);
    }

    /** Downloads just the requested video through TDLib and returns its completed local file. */
    static Path fetch(SimpleTelegramClient client, int fileId, long messageId) throws Exception {
        TdApi.File downloaded = client.send(new TdApi.DownloadFile(fileId, 32, 0, 0, true))
                .get(DOWNLOAD_TIMEOUT.toMinutes(), TimeUnit.MINUTES);
        if (downloaded.local == null || !downloaded.local.isDownloadingCompleted
                || downloaded.local.path == null || downloaded.local.path.isBlank()) {
            throw new IllegalStateException("Telegram did not complete the download for message "
                    + messageId + ".");
        }
        Path path = Path.of(downloaded.local.path).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || Files.size(path) == 0) {
            throw new IllegalStateException("The downloaded media file for message "
                    + messageId + " is missing or empty.");
        }
        return path;
    }

    private static List<VideoReference> findVideos(SimpleTelegramClient client, long chatId, int maxMessages)
            throws Exception {
        List<VideoReference> videos = new ArrayList<>();
        Set<Long> seenMessageIds = new HashSet<>();
        long fromMessageId = 0;
        int fetchedMessages = 0;
        while (true) {
            int remaining = maxMessages - fetchedMessages;
            if (remaining <= 0) {
                if (hasOlderMessages(client, chatId, fromMessageId)) {
                    throw new IllegalStateException("Channel history reached TELEGRAM_MAX_MESSAGES before its end. "
                            + "Increase the limit and restart.");
                }
                break;
            }
            int limit = Math.min(PAGE_SIZE, remaining + (fromMessageId == 0 ? 0 : 1));
            TdApi.Messages page = client.send(new TdApi.GetChatHistory(chatId, fromMessageId, 0, limit, false))
                    .get(REQUEST_TIMEOUT.toMinutes(), TimeUnit.MINUTES);
            if (page.messages == null || page.messages.length == 0) break;
            int newMessages = 0;
            long oldestMessageId = Long.MAX_VALUE;
            for (TdApi.Message message : page.messages) {
                if (message == null) continue;
                oldestMessageId = Math.min(oldestMessageId, message.id);
                if (!seenMessageIds.add(message.id)) continue;
                newMessages++;
                TdApi.File file = videoFile(message.content);
                if (file != null) videos.add(new VideoReference(message.id, file));
            }
            if (newMessages == 0) {
                if (hasOlderMessages(client, chatId, fromMessageId)) {
                    throw new IllegalStateException("Telegram returned a history page without advancing; "
                            + "stopping rather than skipping posts.");
                }
                break;
            }
            fetchedMessages += newMessages;
            if (oldestMessageId == Long.MAX_VALUE) break;
            if (fromMessageId != 0 && oldestMessageId > fromMessageId) {
                throw new IllegalStateException("Telegram returned a non-advancing history page; stopping "
                        + "to avoid a duplicate loop.");
            }
            fromMessageId = oldestMessageId;
        }
        videos.sort(Comparator.comparingLong(VideoReference::messageId));
        System.out.printf("Found %d video posts in %d channel messages.%n", videos.size(), fetchedMessages);
        return videos;
    }

    private static boolean hasOlderMessages(SimpleTelegramClient client, long chatId, long fromMessageId)
            throws Exception {
        if (fromMessageId == 0) return false;
        TdApi.Messages next = client.send(new TdApi.GetChatHistory(chatId, fromMessageId, 0, 2, false))
                .get(REQUEST_TIMEOUT.toMinutes(), TimeUnit.MINUTES);
        if (next.messages == null) return false;
        for (TdApi.Message message : next.messages) {
            if (message != null && message.id < fromMessageId) return true;
        }
        return false;
    }

    private static TdApi.File videoFile(TdApi.MessageContent content) {
        if (content instanceof TdApi.MessageVideo video && video.video != null) return video.video.video;
        if (content instanceof TdApi.MessageDocument document && document.document != null) {
            String mimeType = document.document.mimeType;
            String fileName = document.document.fileName;
            boolean videoMimeType = mimeType != null
                    && mimeType.toLowerCase(java.util.Locale.ROOT).startsWith("video/");
            boolean videoExtension = fileName != null && List.of(
                    ".mp4", ".mov", ".m4v", ".mkv", ".webm", ".avi", ".mpeg", ".mpg", ".3gp")
                    .stream().anyMatch(ext -> fileName.toLowerCase(java.util.Locale.ROOT).endsWith(ext));
            if (videoMimeType || videoExtension) return document.document.document;
        }
        return null;
    }

    record VideoReference(long messageId, TdApi.File file) { }
}
