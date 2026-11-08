package com.teleroom.broadcaster;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BroadcasterConfigTest {
    @Test
    void positiveIntegerUsesFallbackWhenEnvironmentValueIsAbsent() throws Exception {
        String variable = missingEnvironmentVariable();
        Method method = BroadcasterConfig.class.getDeclaredMethod("positiveInteger", String.class, int.class);
        method.setAccessible(true);

        assertEquals(37, method.invoke(null, variable, 37));
    }

    @Test
    void stringSettingUsesFallbackWhenEnvironmentValueIsAbsent() throws Exception {
        String variable = missingEnvironmentVariable();
        Method method = BroadcasterConfig.class.getDeclaredMethod("valueOrDefault", String.class, String.class);
        method.setAccessible(true);

        assertEquals("default-value", method.invoke(null, variable, "default-value"));
    }

        @Test
        void validatesPublicBaseUrlAsAnHttpOrigin() {
        assertEquals("https://media.example.com",
            BroadcasterConfig.validatePublicBaseUrl(" https://media.example.com/ "));
        assertNull(BroadcasterConfig.validatePublicBaseUrl(null));
        assertThrows(IllegalArgumentException.class,
            () -> BroadcasterConfig.validatePublicBaseUrl("file:///tmp/player"));
        assertThrows(IllegalArgumentException.class,
            () -> BroadcasterConfig.validatePublicBaseUrl("https://media.example.com/path"));
        }

    private static String missingEnvironmentVariable() {
        String variable;
        do {
            variable = "COPILOT_TEST_" + UUID.randomUUID().toString().replace("-", "");
        } while (System.getenv(variable) != null);
        assertNull(System.getenv(variable));
        return variable;
    }
}
