package com.notificationpanel.Formatting.FormatOptions;

public class KeyValueOption implements FormatOption {
    private String key;
    private String value;

    public KeyValueOption(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public KeyValueOption parse(String input) throws Exception {
        String[] split = input.split("\\s+");
        if (split.length != 2) {
            return null;
        }
        String key = split[0];
        String value = split[1];
        return new KeyValueOption(key, value);
    }
}
