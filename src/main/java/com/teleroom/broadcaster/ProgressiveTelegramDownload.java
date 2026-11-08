package com.teleroom.broadcaster;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Streams contiguous bytes from TDLib's in-progress local download. */
final class ProgressiveTelegramDownload {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration METADATA_TIMEOUT = Duration.ofMinutes(2);
    private final SimpleTelegramClient client;
    private final Path downloadDirectory;
    private final ConcurrentHashMap<Integer, DownloadState> downloads = new ConcurrentHashMap<>();

    ProgressiveTelegramDownload(SimpleTelegramClient client, Path downloadDirectory) {
        this.client = client;
        this.downloadDirectory = downloadDirectory.toAbsolutePath().normalize();
        client.addUpdateHandler(TdApi.UpdateFile.class, update -> {
            if (update.file != null) {
                DownloadState state = downloads.get(update.file.id);
                if (state != null) state.update(update.file);
            }
        });
    }

    WebPlaylistServer.VideoSource open(int fileId) throws Exception {
        DownloadState state = downloads.computeIfAbsent(fileId,
                ignored -> new DownloadState(downloadDirectory));
        if (state.beginInitialization()) {
            try {
                TdApi.File current = client.send(new TdApi.GetFile(fileId))
                        .get(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                state.update(current);
                if (state.startDownload()) {
                    CompletableFuture<TdApi.File> download = client.send(
                            new TdApi.DownloadFile(fileId, 32, 0, 0, false));
                    download.whenComplete((file, error) -> {
                        if (error != null) state.fail(error);
                        else if (file != null) state.update(file);
                    });
                }
            } catch (Exception exception) {
                state.fail(exception);
                throw exception;
            }
        }
        state.awaitMetadata();
        return state;
    }

    static final class DownloadState implements WebPlaylistServer.VideoSource {
        private static final Duration BYTE_TIMEOUT = Duration.ofMinutes(10);
        private final Path allowedDirectory;
        private Path path;
        private long size;
        private long downloadedPrefixSize;
        private boolean complete;
        private boolean initialized;
        private boolean downloadStarted;
        private IOException failure;

        DownloadState(Path allowedDirectory) {
            this.allowedDirectory = allowedDirectory.toAbsolutePath().normalize();
        }

        synchronized boolean beginInitialization() {
            if (initialized) return false;
            initialized = true;
            return true;
        }

        synchronized boolean startDownload() {
            if (downloadStarted || complete || failure != null) return false;
            downloadStarted = true;
            return true;
        }

        synchronized void update(TdApi.File file) {
            if (failure != null || file == null) return;
            if (file.size > 0) size = file.size;
            else if (size <= 0 && file.expectedSize > 0) size = file.expectedSize;
            if (file.local != null) {
                if (file.local.path != null && !file.local.path.isBlank()) {
                    Path candidate = Path.of(file.local.path).toAbsolutePath().normalize();
                    if (!candidate.startsWith(allowedDirectory) || candidate.equals(allowedDirectory)) {
                        fail(new IOException("TDLib returned a path outside its configured download directory."));
                        return;
                    }
                    path = candidate;
                }
                downloadedPrefixSize = Math.max(downloadedPrefixSize, file.local.downloadedPrefixSize);
                complete = file.local.isDownloadingCompleted;
            }
            if (complete) {
                if (path != null && size <= 0) {
                    try {
                        size = java.nio.file.Files.size(path);
                    } catch (IOException exception) {
                        fail(exception);
                        return;
                    }
                }
                downloadedPrefixSize = Math.max(downloadedPrefixSize, size);
            }
            notifyAll();
        }

        synchronized void fail(Throwable error) {
            if (failure == null) {
                failure = error instanceof IOException ioException
                        ? ioException : new IOException("Telegram could not download the requested video.", error);
            }
            notifyAll();
        }

        synchronized void awaitMetadata() throws IOException {
            long deadline = System.nanoTime() + METADATA_TIMEOUT.toNanos();
            while ((path == null || size <= 0) && failure == null) waitUntil(deadline);
            throwIfFailed();
        }

        @Override
        public synchronized long size() throws IOException {
            throwIfFailed();
            if (size <= 0) throw new IOException("Telegram has not reported the video size yet.");
            return size;
        }

        @Override
        public InputStream openRange(long start, long length) throws IOException {
            long fileSize = size();
            if (start < 0 || length <= 0 || start >= fileSize || length > fileSize - start) {
                throw new IOException("Requested video range is outside the Telegram file.");
            }
            awaitPrefix(start + 1);
            final FileChannel channel;
            try {
                channel = FileChannel.open(path(), StandardOpenOption.READ);
            } catch (IOException exception) {
                throw new IOException("The downloaded video is not readable yet.", exception);
            }
            return new ProgressiveInputStream(this, channel, start, start + length);
        }

        private synchronized Path path() throws IOException {
            throwIfFailed();
            if (path == null) throw new IOException("TDLib has not created a local video file yet.");
            return path;
        }

        private synchronized long awaitPrefix(long requiredSize) throws IOException {
            long deadline = System.nanoTime() + BYTE_TIMEOUT.toNanos();
            while (downloadedPrefixSize < requiredSize && !complete && failure == null) waitUntil(deadline);
            throwIfFailed();
            if (downloadedPrefixSize < requiredSize) throw new EOFException("Telegram ended the video download early.");
            return downloadedPrefixSize;
        }

        private void waitUntil(long deadline) throws IOException {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new IOException("Timed out waiting for Telegram video data.");
            try {
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for Telegram video data.", exception);
            }
        }

        private void throwIfFailed() throws IOException {
            if (failure != null) throw failure;
        }
    }

    private static final class ProgressiveInputStream extends InputStream {
        private final DownloadState state;
        private final FileChannel channel;
        private final long end;
        private long position;

        private ProgressiveInputStream(DownloadState state, FileChannel channel, long start, long end) {
            this.state = state;
            this.channel = channel;
            this.position = start;
            this.end = end;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int read = read(one, 0, 1);
            return read < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (offset < 0 || length < 0 || length > bytes.length - offset) {
                throw new IndexOutOfBoundsException();
            }
            if (length == 0) return 0;
            if (position >= end) return -1;
            while (true) {
                long prefixSize = state.awaitPrefix(position + 1);
                int available = (int) Math.min(Math.min((long) length, end - position), prefixSize - position);
                ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, available);
                int read = channel.read(buffer, position);
                if (read < 0) throw new EOFException("Downloaded video data is shorter than TDLib reported.");
                if (read == 0) continue;
                position += read;
                return read;
            }
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }
}
