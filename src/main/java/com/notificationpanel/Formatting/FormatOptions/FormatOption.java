package com.notificationpanel.Formatting.FormatOptions;

import javax.annotation.Nullable;
import java.util.Optional;

public interface FormatOption {
    @Nullable
    FormatOption parse(String input);

    static <T extends FormatOption> Optional<FormatOption> tryParseAs(String line, T option) {
        try {
            return Optional.ofNullable(option.parse(line));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
