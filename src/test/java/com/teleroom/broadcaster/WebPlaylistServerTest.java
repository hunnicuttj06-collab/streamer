package com.teleroom.broadcaster;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WebPlaylistServerTest {
    @TempDir
    Path tempDirectory;

    @Test
    void parsesClosedOpenEndedAndSuffixByteRanges() throws Exception {
        assertArrayEquals(new long[]{2, 5}, parseRange("bytes=2-5", 10));
        assertArrayEquals(new long[]{5, 9}, parseRange("bytes=5-", 10));
        assertArrayEquals(new long[]{7, 9}, parseRange("bytes=-3", 10));
        assertArrayEquals(new long[]{0, 9}, parseRange("bytes=-99", 10));
    }

    @Test
    void rejectsMalformedUnsatisfiableAndMultipleByteRanges() throws Exception {
        assertNull(parseRange("items=0-1", 10));
        assertNull(parseRange("bytes=0-1,4-5", 10));
        assertNull(parseRange("bytes=-0", 10));
        assertNull(parseRange("bytes=10-", 10));
        assertNull(parseRange("bytes=5-4", 10));
        assertNull(parseRange("bytes=0-", 0));
        assertNull(parseRange("bytes=not-a-number", 10));
    }

    @Test
    void decodesQueryParametersAndReturnsNullWhenMissing() throws Exception {
        assertEquals("Alice Smith&Co", invokeQueryParameter("name=Alice+Smith%26Co&token=secret", "name"));
        assertEquals("secret", invokeQueryParameter("name=Alice&token=secret", "token"));
        assertNull(invokeQueryParameter("name=Alice", "token"));
        assertNull(invokeQueryParameter(null, "token"));
    }

    @Test
    void escapesJsonControlCharactersAndQuotes() throws Exception {
        assertEquals("a\\\\b\\\"c\\n\\r", invokeJsonEscape("a\\b\"c\n\r"));
    }

    @Test
    void fileVideoSourceServesOnlyTheRequestedRange() throws Exception {
        Path videoDirectory = Files.createDirectory(tempDirectory.resolve("videos"));
        Path video = videoDirectory.resolve("video.mp4");
        Files.writeString(video, "0123456789");

        WebPlaylistServer.VideoSource source = WebPlaylistServer.fileSource(video, videoDirectory);
        try (InputStream range = source.openRange(3, 4)) {
            assertEquals(10, source.size());
            assertEquals("3456", new String(range.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Test
    void mediaPlayerPlaylistUsesConfiguredBaseAndTokenizedDirectMp4Urls() {
        String playlist = WebPlaylistServer.mediaPlaylistM3u("https://media.example.com",
                java.util.List.of(new WebPlaylistServer.Video("video-000001.mp4", "Episode 1", 7, 42, true)),
                "private token");

        assertEquals("#EXTM3U\n#EXTINF:-1,Episode 1\n"
                + "https://media.example.com/videos/video-000001.mp4?token=private+token\n", playlist);
    }

    private static long[] parseRange(String range, long size) throws Exception {
        Method method = WebPlaylistServer.class.getDeclaredMethod("parseRange", String.class, long.class);
        method.setAccessible(true);
        return (long[]) method.invoke(null, range, size);
    }

    private static String invokeQueryParameter(String query, String key) throws Exception {
        Method method = WebPlaylistServer.class.getDeclaredMethod("queryParameter", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, query, key);
    }

    private static String invokeJsonEscape(String value) throws Exception {
        Method method = WebPlaylistServer.class.getDeclaredMethod("jsonEscape", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, value);
    }
}
