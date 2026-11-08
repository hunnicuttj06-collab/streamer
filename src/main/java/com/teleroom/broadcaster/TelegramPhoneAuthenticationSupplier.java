package com.teleroom.broadcaster;

import it.tdlight.client.AuthenticationData;
import it.tdlight.client.AuthenticationSupplier;
import it.tdlight.util.ScannerUtils;

import java.util.concurrent.CompletableFuture;

/** Supplies only a Telegram user phone login; code and 2FA are handled by TDLib authorization states. */
final class TelegramPhoneAuthenticationSupplier implements AuthenticationSupplier<AuthenticationData> {
    private final String configuredPhoneNumber;
    private CompletableFuture<AuthenticationData> authenticationData;

    TelegramPhoneAuthenticationSupplier(String configuredPhoneNumber) {
        this.configuredPhoneNumber = configuredPhoneNumber;
    }

    @Override
    public synchronized CompletableFuture<AuthenticationData> get() {
        if (authenticationData == null) {
            authenticationData = CompletableFuture.supplyAsync(this::createAuthenticationData);
        }
        return authenticationData;
    }

    private AuthenticationData createAuthenticationData() {
        String phoneNumber = configuredPhoneNumber;
        if (phoneNumber == null || phoneNumber.isBlank()) {
            phoneNumber = ScannerUtils.askParameter("login", "Enter your phone number in international format");
        }
        phoneNumber = normalizePhoneNumber(phoneNumber);
        return (AuthenticationData) AuthenticationSupplier.user(phoneNumber).get().join();
    }

    static String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) throw new IllegalArgumentException("A Telegram phone number is required.");
        String normalized = phoneNumber.trim().replaceAll("[\\s()-]", "");
        if (normalized.startsWith("00")) normalized = "+" + normalized.substring(2);
        if (!normalized.startsWith("+")) normalized = "+" + normalized;
        if (!normalized.matches("\\+[1-9][0-9]{6,14}")) {
            throw new IllegalArgumentException("Enter a valid phone number in international format, such as +15551234567.");
        }
        return normalized;
    }
}
