package com.teleroom.broadcaster;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.jni.TdApi;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Resolves a channel identifier supplied as an ID, Telegram link, username, or known chat title. */
final class TelegramChannelResolver {
    private TelegramChannelResolver() { }

    static TdApi.Chat resolve(SimpleTelegramClient client, String selector, Duration timeout) throws Exception {
        String value = selector.trim();
        if (value.matches("-?\\d+")) {
            long chatId;
            try {
                chatId = Long.parseLong(value);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("TELEGRAM_CHANNEL is not a valid chat ID.", exception);
            }
            return getChat(client, chatId, timeout);
        }

        String link = normalizedTelegramLink(value);
        if (isInviteLink(link)) {
            // JoinChatByInviteLink returns a ChatJoinResult subtype, not a chatId field.
            // Only the Chat subtype directly contains the resolved chat.
            TdApi.ChatJoinResult result = client.send(new TdApi.JoinChatByInviteLink(link))
                    .get(timeout.toMinutes(), TimeUnit.MINUTES);
            if (result instanceof TdApi.ChatJoinResult.Chat joined && joined.chat != null) {
                return joined.chat;
            }
            throw new IllegalStateException("Telegram did not return a chat for this invite link. "
                    + "If you already joined the channel, use its chat ID, public link, or exact title.");
        }

        String username = usernameFromLink(link);
        if (username != null) {
            return client.send(new TdApi.SearchPublicChat(username))
                    .get(timeout.toMinutes(), TimeUnit.MINUTES);
        }

        return findKnownChatByTitle(client, value, timeout);
    }

    private static TdApi.Chat getChat(SimpleTelegramClient client, long chatId, Duration timeout)
            throws Exception {
        return client.send(new TdApi.GetChat(chatId)).get(timeout.toMinutes(), TimeUnit.MINUTES);
    }

    private static TdApi.Chat findKnownChatByTitle(
            SimpleTelegramClient client, String title, Duration timeout) throws Exception {
        TdApi.Chats results = client.send(new TdApi.SearchChats(title, null, 100))
                .get(timeout.toMinutes(), TimeUnit.MINUTES);
        List<TdApi.Chat> matches = new ArrayList<>();
        if (results.chatIds != null) {
            for (long chatId : results.chatIds) {
                TdApi.Chat chat = getChat(client, chatId, timeout);
                if (chat.title != null && chat.title.trim().equalsIgnoreCase(title.trim())) {
                    matches.add(chat);
                }
            }
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("No known Telegram chat exactly matches '" + title
                    + "'. For a private channel, use its ID, an authorized invite link, or an exact title "
                    + "already present in this account's chat list.");
        }
        if (matches.size() > 1) {
            String choices = matches.stream()
                    .map(chat -> chat.title + " (ID " + chat.id + ")")
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            throw new IllegalArgumentException("More than one known chat matches '" + title
                    + "': " + choices + ". Use a chat ID or username instead.");
        }
        return matches.get(0);
    }

    private static String normalizedTelegramLink(String value) {
        String link = value.trim();
        String lower = link.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://t.me/")) {
            return "https://t.me/" + link.substring("http://t.me/".length());
        }
        if (lower.startsWith("www.t.me/")) {
            return "https://" + link;
        }
        if (lower.startsWith("t.me/")) {
            return "https://" + link;
        }
        return link;
    }

    private static boolean isInviteLink(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://t.me/+") || lower.startsWith("https://t.me/joinchat/");
    }

    private static String usernameFromLink(String value) {
        String candidate = value;
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://t.me/")) {
            candidate = candidate.substring("https://t.me/".length());
            int slash = candidate.indexOf('/');
            if (slash >= 0) {
                candidate = candidate.substring(0, slash);
            }
            int query = candidate.indexOf('?');
            if (query >= 0) {
                candidate = candidate.substring(0, query);
            }
        }
        if (candidate.startsWith("@")) {
            candidate = candidate.substring(1);
        }
        if (candidate.matches("[A-Za-z0-9_]{5,}")) {
            return candidate;
        }
        return null;
    }
}
