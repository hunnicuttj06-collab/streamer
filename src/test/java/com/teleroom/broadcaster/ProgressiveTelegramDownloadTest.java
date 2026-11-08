package com.teleroom.broadcaster;

import it.tdlight.jni.TdApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressiveTelegramDownloadTest {
    @TempDir
    Path tempDirectory;

    @Test
    void servesAvailablePrefixThenContinuesWhenDownloadGrows() throws Exception {
        Path video = tempDirectory.resolve("telegram-video.mp4");
        Files.writeString(video, "abcd");
        ProgressiveTelegramDownload.DownloadState state =
                new ProgressiveTelegramDownload.DownloadState(tempDirectory);
        state.update(file(video, 8, 4, false));

        try (InputStream stream = state.openRange(0, 8)) {
            assertArrayEquals("abcd".getBytes(java.nio.charset.StandardCharsets.UTF_8), stream.readNBytes(4));
            CountDownLatch enteringRead = new CountDownLatch(1);
            CompletableFuture<byte[]> remaining = CompletableFuture.supplyAsync(() -> {
                enteringRead.countDown();
                try {
                    return stream.readNBytes(4);
                } catch (java.io.IOException exception) {
                    throw new java.util.concurrent.CompletionException(exception);
                }
            });

            assertTrue(enteringRead.await(2, TimeUnit.SECONDS));
            assertFalse(remaining.isDone());
            Files.writeString(video, "efgh", StandardOpenOption.APPEND);
            state.update(file(video, 8, 8, true));

            assertArrayEquals("efgh".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    remaining.get(2, TimeUnit.SECONDS));
        }
    }

    private static TdApi.File file(Path path, long size, long downloadedPrefixSize, boolean complete) {
        TdApi.LocalFile local = new TdApi.LocalFile();
        local.path = path.toString();
        local.downloadedPrefixSize = downloadedPrefixSize;
        local.downloadedSize = downloadedPrefixSize;
        local.isDownloadingCompleted = complete;
        TdApi.File file = new TdApi.File();
        file.size = size;
        file.expectedSize = size;
        file.local = local;
        return file;
    }
}
