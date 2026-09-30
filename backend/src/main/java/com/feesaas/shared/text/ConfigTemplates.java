package com.feesaas.shared.text;

import java.util.Map;

public final class ConfigTemplates {
    private ConfigTemplates() {}

    public static String apply(String template, Map<String, String> values) {
        if (template == null) {
            return "";
        }
        String out = template;
        for (var e : values.entrySet()) {
            String value = e.getValue() == null ? "" : e.getValue();
            out = out.replace("{{" + e.getKey() + "}}", value);
        }
        return out;
    }
}
