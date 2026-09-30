package com.feesaas.shared.contact;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** India-default E.164 helpers for click-to-chat WhatsApp and native SMS. */
public final class PhoneNumbers {

    private PhoneNumbers() {}

    public static String digits(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\D", "");
    }

    /** Digits with country code, no plus. 10-digit local numbers become 91XXXXXXXXXX. */
    public static String international(String raw) {
        String d = digits(raw);
        if (d.isEmpty()) {
            return "";
        }
        if (d.startsWith("0") && d.length() == 11) {
            d = d.substring(1);
        }
        if (d.length() == 10) {
            return "91" + d;
        }
        return d;
    }

    public static boolean hasNumber(String raw) {
        return international(raw).length() >= 10;
    }

    public static String waLink(String phone, String text) {
        String to = international(phone);
        return "https://wa.me/" + to + "?text=" + encode(text);
    }

    public static String smsLink(String phone, String text) {
        String to = international(phone);
        return "sms:+" + to + "?body=" + encode(text);
    }

    private static String encode(String text) {
        return URLEncoder.encode(text == null ? "" : text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
