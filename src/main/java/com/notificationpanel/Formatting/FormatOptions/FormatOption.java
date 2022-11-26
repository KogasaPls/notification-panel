package com.notificationpanel.Formatting.FormatOptions;

import java.util.Optional;

public interface FormatOption {
    FormatOption parse(String input) throws Exception;

    static <T extends FormatOption> Optional<FormatOption> tryParseAs(String line, T option) {
        try {
            return Optional.ofNullable(option.parse(line));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
