package com.teleroom.broadcaster;

import java.awt.GraphicsEnvironment;
import java.io.Console;
import java.util.concurrent.CompletableFuture;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JPanel;

import it.tdlight.client.ClientInteraction;
import it.tdlight.client.InputParameter;
import it.tdlight.client.ParameterInfo;
import it.tdlight.client.ParameterInfoPasswordHint;
import it.tdlight.client.ParameterInfoTermsOfService;
import it.tdlight.util.ScannerUtils;

/** Handles Telegram's requested login inputs without exposing device-approval links. */
final class TelegramLoginInteraction implements ClientInteraction {
    private final CompletableFuture<Void> deviceApprovalRequired = new CompletableFuture<>();

    @Override
    public CompletableFuture<String> onParameterRequest(InputParameter parameter, ParameterInfo info) {
        if (parameter == InputParameter.NOTIFY_LINK) {
            System.out.println("Telegram requires approval on an already signed-in device instead of sending a login code.");
            System.out.println("No code or 2FA password was requested. This app will not display the approval link.");
            deviceApprovalRequired.complete(null);
            return CompletableFuture.completedFuture("");
        }
        return CompletableFuture.supplyAsync(() -> readAnswer(parameter, info));
    }

    CompletableFuture<Void> deviceApprovalRequired() {
        return deviceApprovalRequired;
    }

    static String promptFor(InputParameter parameter, ParameterInfo info) {
        return switch (parameter) {
            case ASK_CODE -> "Enter the login code sent by Telegram";
            case ASK_PASSWORD -> {
                String hint = info instanceof ParameterInfoPasswordHint passwordHint
                        ? passwordHint.getHint() : null;
                yield hint == null || hint.isBlank()
                        ? "Enter your Telegram 2-step verification password"
                        : "Enter your Telegram 2-step verification password (hint: " + hint + ")";
            }
            case ASK_EMAIL_ADDRESS -> "Enter the email address requested by Telegram";
            case ASK_EMAIL_CODE -> "Enter the email verification code sent by Telegram";
            case ASK_FIRST_NAME -> "Enter your first name";
            case ASK_LAST_NAME -> "Enter your last name";
            case TERMS_OF_SERVICE -> "Press Enter after reviewing Telegram's terms of service";
            case NOTIFY_LINK -> throw new IllegalArgumentException("Approval links are not displayed.");
        };
    }

    private static String readAnswer(InputParameter parameter, ParameterInfo info) {
        if (parameter == InputParameter.TERMS_OF_SERVICE && info instanceof ParameterInfoTermsOfService terms) {
            System.out.println(terms.getTermsOfService().text.text);
        }

        String prompt = promptFor(parameter, info);
        Console console = System.console();
        if (parameter == InputParameter.ASK_PASSWORD && console != null) {
            while (true) {
                char[] chars = console.readPassword("[login] %s: ", prompt);
                String answer = chars == null ? "" : new String(chars).trim();
                if (chars != null) java.util.Arrays.fill(chars, '\0');
                if (!answer.isBlank()) return answer;
                System.out.println("This value is required. Please try again.");
            }
        }

        if (console == null && !GraphicsEnvironment.isHeadless()) {
            while (true) {
                if (parameter == InputParameter.ASK_PASSWORD) {
                    JPasswordField passwordField = new JPasswordField(24);
                    JPanel panel = new JPanel();
                    panel.add(new JLabel(prompt + ":"));
                    panel.add(passwordField);
                    int result = JOptionPane.showConfirmDialog(null, panel, "Telegram sign-in",
                            JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (result != JOptionPane.OK_OPTION) {
                        throw new IllegalStateException("Telegram sign-in was cancelled.");
                    }
                    char[] chars = passwordField.getPassword();
                    try {
                        String answer = new String(chars).trim();
                        if (!answer.isBlank()) return answer;
                    } finally {
                        java.util.Arrays.fill(chars, '\0');
                    }
                } else {
                    String answer = JOptionPane.showInputDialog(null, prompt, "Telegram sign-in",
                            JOptionPane.QUESTION_MESSAGE);
                    if (answer == null) {
                        throw new IllegalStateException("Telegram sign-in was cancelled.");
                    }
                    if (!answer.isBlank()) return answer.trim();
                }
                System.out.println("This value is required. Please try again.");
            }
        }

        if (parameter == InputParameter.ASK_PASSWORD) {
            System.out.println("Note: input cannot be hidden in this terminal.");
        }
        while (true) {
            String answer = ScannerUtils.askParameter("login", prompt);
            if (answer != null && !answer.isBlank()) return answer.trim();
            System.out.println("This value is required. Please try again.");
        }
    }
}
