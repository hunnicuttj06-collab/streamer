package com.teleroom.broadcaster;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaPipelineTest {
    @TempDir
    Path tempDirectory;

    @Test
    void prepareVideoReusesMatchingCacheWithoutStartingExternalTools() throws Exception {
        Path input = inputFile();
        Path output = tempDirectory.resolve("prepared/video.mp4");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "prepared media");
        Files.writeString(output.resolveSibling("video.mp4.meta"), cacheKey(input));

        new MediaPipeline("missing-ffmpeg", "missing-ffprobe", new MediaPipeline.ProcessRunner())
                .prepareVideo(input, output, 42);

        assertEquals("prepared media", Files.readString(output));
    }

    @Test
    void prepareVideoDoesNotReuseCacheWhenInputMetadataChanges() throws Exception {
        Path input = inputFile();
        Path output = tempDirectory.resolve("prepared/video.mp4");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "old prepared media");
        Files.writeString(output.resolveSibling("video.mp4.meta"), "outdated metadata");
        String missingExecutable = tempDirectory.resolve("missing-ffprobe.exe").toString();

        assertThrows(IOException.class, () -> new MediaPipeline("missing-ffmpeg", missingExecutable,
                new MediaPipeline.ProcessRunner()).prepareVideo(input, output, 43));
        assertEquals("old prepared media", Files.readString(output));
    }

    @Test
    void parseDimensionReturnsNegativeOneForMalformedAndMissingValues() throws Exception {
        assertEquals(1920, parseDimension("1920"));
        assertEquals(-1, parseDimension("unknown"));
        assertEquals(-1, parseDimension(null));
    }

    @Test
    void cacheKeyChangesWhenInputContentsChange() throws Exception {
        Path input = inputFile();
        String originalKey = cacheKey(input);
        Files.writeString(input, "different source media");

        assertFalse(originalKey.equals(cacheKey(input)));
    }

    private Path inputFile() throws IOException {
        Path input = tempDirectory.resolve("input.bin");
        Files.writeString(input, "source media");
        return input;
    }

    private static int parseDimension(String value) throws Exception {
        Method method = MediaPipeline.class.getDeclaredMethod("parseDimension", String.class);
        method.setAccessible(true);
        return (int) method.invoke(null, new Object[]{value});
    }

    private static String cacheKey(Path input) throws Exception {
        Method method = MediaPipeline.class.getDeclaredMethod("cacheKey", Path.class);
        method.setAccessible(true);
        return (String) method.invoke(null, input);
    }
}
