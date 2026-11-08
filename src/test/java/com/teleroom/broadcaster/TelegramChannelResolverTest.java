package com.teleroom.broadcaster;

import it.tdlight.jni.TdApi;
import it.tdlight.client.TelegramError;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramChannelResolverTest {
    @Test
    void normalizesSupportedTelegramLinkPrefixes() throws Exception {
        assertEquals("https://t.me/channel?single", invokeString("normalizedTelegramLink", "t.me/channel?single"));
        assertEquals("https://www.t.me/channel", invokeString("normalizedTelegramLink", "www.t.me/channel"));
        assertEquals("https://t.me/channel", invokeString("normalizedTelegramLink", "http://t.me/channel"));
        assertEquals("@channel", invokeString("normalizedTelegramLink", "@channel"));
    }

    @Test
    void extractsPublicUsernamesFromHandlesAndLinks() throws Exception {
        assertEquals("my_channel", invokeString("usernameFromLink", "@my_channel"));
        assertEquals("SomeChannel", invokeString("usernameFromLink",
                "https://t.me/SomeChannel/123?single"));
        assertNull(invokeString("usernameFromLink", "https://t.me/+inviteCode"));
        assertNull(invokeString("usernameFromLink", "tiny"));
    }

    @Test
    void identifiesBothTelegramInviteLinkForms() throws Exception {
        assertTrue(invokeBoolean("isInviteLink", "https://t.me/+inviteCode"));
        assertTrue(invokeBoolean("isInviteLink", "https://t.me/joinchat/inviteCode"));
        assertTrue(!invokeBoolean("isInviteLink", "https://t.me/public_channel"));
    }

    @Test
    void extractsChatReturnedByInviteJoin() throws Exception {
        TdApi.Chat expected = new TdApi.Chat();

        assertSame(expected, invoke("extractChatFromJoinResult", expected));
        assertNull(invoke("extractChatFromJoinResult", "unexpected result"));
    }

    @Test
    void reportsTelegramInviteErrors() throws Exception {
        TdApi.Error error = new TdApi.Error(400, "invite rejected");
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> invoke("extractChatFromJoinResult", error));

        assertTrue(thrown.getCause() instanceof IllegalStateException);
        assertEquals("Telegram rejected the invite link: invite rejected", thrown.getCause().getMessage());
    }

    @Test
    void recognizesOnlyAlreadyJoinedInviteErrors() throws Exception {
        TelegramError alreadyJoined = new TelegramError(new TdApi.Error(400, "USER_ALREADY_PARTICIPANT"));
        TelegramError otherError = new TelegramError(new TdApi.Error(400, "INVITE_HASH_EXPIRED"));

        assertTrue(invokeBoolean("isAlreadyParticipant", alreadyJoined));
        assertTrue(!invokeBoolean("isAlreadyParticipant", new java.util.concurrent.ExecutionException(otherError)));
    }

    private static String invokeString(String methodName, String value) throws Exception {
        return (String) invoke(methodName, value);
    }

    private static boolean invokeBoolean(String methodName, String value) throws Exception {
        return (boolean) invoke(methodName, value);
    }

    private static boolean invokeBoolean(String methodName, Throwable value) throws Exception {
        Method method = TelegramChannelResolver.class.getDeclaredMethod(methodName, Throwable.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, value);
    }

    private static Object invoke(String methodName, Object argument) throws Exception {
        Class<?> argumentType = methodName.equals("extractChatFromJoinResult") ? Object.class
            : argument instanceof String ? String.class : Object.class;
        Method method = TelegramChannelResolver.class.getDeclaredMethod(methodName, argumentType);
        method.setAccessible(true);
        return method.invoke(null, argument);
    }
}
