package com.notificationpanel.ConditionalFormatting.FormatOptions;

import java.util.List;
import java.util.Optional;

public abstract class FormatOption {
    public static Optional<? extends FormatOption> tryParseAsAny(String value, List<FormatOption> options) {
        for (FormatOption option : options) {
            Optional<? extends FormatOption> parsed = option.tryParseWord(value);
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        return Optional.empty();
    }

    public abstract Optional<? extends FormatOption> parseValue(String value) throws Exception;

    public Optional<FormatOption> tryParseWord(String word) {
        final String[] split = word.split("=", 2);
        final String key = split[0];

        FormatOption option = null;

        if (split.length == 1) {
            option = tryParseValue(key).orElse(null);
        } else if (key.equals(this.getClass().getSimpleName())) {
            final String value = split[1];
            option = tryParseValue(value).orElse(null);
        }

        return Optional.ofNullable(option);
    }


    public Optional<? extends FormatOption> tryParseValue(String value) {
        try {
            return parseValue(value);
        } catch (Exception e) {
            return Optional.empty();
        }
    }


}
