package com.teleroom.broadcaster;

import it.tdlight.client.InputParameter;
import it.tdlight.client.ParameterInfoNotifyLink;
import it.tdlight.client.ParameterInfoPasswordHint;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramLoginInteractionTest {
    @Test
    void deviceApprovalNotificationDoesNotPrintOrReturnTheLink() throws Exception {
        String privateLink = "tg://login?token=private-test-token";
        TelegramLoginInteraction interaction = new TelegramLoginInteraction();
        PrintStream originalOut = System.out;
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        try (PrintStream replacement = new PrintStream(capturedOutput, true, StandardCharsets.UTF_8)) {
            System.setOut(replacement);
            String result = interaction.onParameterRequest(InputParameter.NOTIFY_LINK,
                    new ParameterInfoNotifyLink(privateLink)).get();

            assertTrue(result.isEmpty());
            assertTrue(interaction.deviceApprovalRequired().isDone());
        } finally {
            System.setOut(originalOut);
        }

        String output = capturedOutput.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("No code or 2FA password was requested"));
        assertFalse(output.contains(privateLink));
        assertFalse(output.contains("private-test-token"));
    }

    @Test
    void codeAndTwoFactorPromptsAreExplicit() {
        assertTrue(TelegramLoginInteraction.promptFor(InputParameter.ASK_CODE, null)
                .contains("login code sent by Telegram"));
        assertTrue(TelegramLoginInteraction.promptFor(InputParameter.ASK_PASSWORD,
                new ParameterInfoPasswordHint("blue", false, null)).contains("2-step verification password"));
    }
}
