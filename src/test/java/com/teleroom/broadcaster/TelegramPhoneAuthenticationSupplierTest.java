package com.teleroom.broadcaster;

import it.tdlight.client.AuthenticationData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TelegramPhoneAuthenticationSupplierTest {
    @Test
    void normalizesPhoneNumberToInternationalFormat() {
        assertEquals("+15551234567",
                TelegramPhoneAuthenticationSupplier.normalizePhoneNumber("1 (555) 123-4567"));
        assertEquals("+15551234567",
                TelegramPhoneAuthenticationSupplier.normalizePhoneNumber("0015551234567"));
    }

    @Test
    void rejectsMalformedPhoneNumbers() {
        assertThrows(IllegalArgumentException.class,
                () -> TelegramPhoneAuthenticationSupplier.normalizePhoneNumber("not-a-phone"));
    }

    @Test
    void suppliesTelegramUserAuthenticationRatherThanBotOrQr() throws Exception {
        AuthenticationData authenticationData =
                new TelegramPhoneAuthenticationSupplier("+15551234567").get().get();

        assertEquals("+15551234567", authenticationData.getUserPhoneNumber());
        assertFalse(authenticationData.isBot());
        assertFalse(authenticationData.isQrCode());
    }
}
