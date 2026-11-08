package com.teleroom.broadcaster;

import it.tdlight.jni.TdApi;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramChannelPlaylistTest {
    @Test
    void recognizesNativeTelegramVideo() throws Exception {
        TdApi.File expected = new TdApi.File();
        TdApi.Video video = new TdApi.Video();
        video.video = expected;
        TdApi.MessageVideo content = new TdApi.MessageVideo();
        content.video = video;

        assertSame(expected, extractVideoFile(content));
    }

    @Test
    void enablesProgressivePlaybackOnlyForTelegramStreamableMp4Videos() throws Exception {
        TdApi.Video video = new TdApi.Video();
        video.supportsStreaming = true;
        video.mimeType = "video/mp4";
        TdApi.MessageVideo content = new TdApi.MessageVideo();
        content.video = video;

        assertTrue(isProgressivelyStreamable(content));
        video.mimeType = "video/quicktime";
        assertFalse(isProgressivelyStreamable(content));
        video.mimeType = "video/mp4";
        video.supportsStreaming = false;
        assertFalse(isProgressivelyStreamable(content));
    }

    @Test
    void recognizesVideoDocumentByMimeTypeIgnoringCase() throws Exception {
        TdApi.File expected = new TdApi.File();
        TdApi.Document document = new TdApi.Document();
        document.mimeType = "Video/quicktime";
        document.fileName = "clip.data";
        document.document = expected;
        TdApi.MessageDocument content = new TdApi.MessageDocument();
        content.document = document;

        assertSame(expected, extractVideoFile(content));
    }

    @Test
    void recognizesVideoDocumentByCaseInsensitiveExtension() throws Exception {
        TdApi.File expected = new TdApi.File();
        TdApi.Document document = new TdApi.Document();
        document.mimeType = "application/octet-stream";
        document.fileName = "CLIP.MP4";
        document.document = expected;
        TdApi.MessageDocument content = new TdApi.MessageDocument();
        content.document = document;

        assertSame(expected, extractVideoFile(content));
    }

    @Test
    void ignoresNonVideoDocumentsAndOtherMessageTypes() throws Exception {
        TdApi.Document document = new TdApi.Document();
        document.mimeType = "application/pdf";
        document.fileName = "document.pdf";
        TdApi.MessageDocument content = new TdApi.MessageDocument();
        content.document = document;

        assertNull(extractVideoFile(content));
        assertNull(extractVideoFile(new TdApi.MessageText()));
        assertNull(extractVideoFile(null));
    }

    @Test
    void ignoresVideoMessagesWithoutAFile() throws Exception {
        TdApi.MessageVideo content = new TdApi.MessageVideo();
        content.video = new TdApi.Video();

        assertNull(extractVideoFile(content));
    }

    private static TdApi.File extractVideoFile(TdApi.MessageContent content) throws Exception {
        Method method = TelegramChannelPlaylist.class.getDeclaredMethod("videoFile", TdApi.MessageContent.class);
        method.setAccessible(true);
        return (TdApi.File) method.invoke(null, content);
    }

    private static boolean isProgressivelyStreamable(TdApi.MessageContent content) throws Exception {
        Method method = TelegramChannelPlaylist.class
                .getDeclaredMethod("isProgressivelyStreamable", TdApi.MessageContent.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, content);
    }
}
