package com.notificationpanel.Formatting.FormatOptions;

import lombok.Getter;

public class FlagOption implements FormatOption {
    @Getter
    private final String flag;

    public FlagOption(String flag) {
        this.flag = flag;
    }

    public FlagOption parse(String input) throws Exception {
        String[] split = input.split("\\s+");
        if (split.length != 1) {
            return null;
        }
        String flag = split[0];
        return new FlagOption(flag);
    }
}
