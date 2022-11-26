package com.notificationpanel.Formatting.PatternMatching;

import com.notificationpanel.Formatting.FormatOptions.FormatOptions;

import java.util.Optional;
import java.util.regex.Pattern;

public class PatternFormat {

    private final Pattern pattern;
    private final FormatOptions options;

    public PatternFormat(Pattern pattern, FormatOptions options) {
        this.pattern = pattern;
        this.options = options;
    }

    private boolean isMatch(String input) {
        return pattern.matcher(input).matches();
    }

    public Optional<FormatOptions> getOptionIfMatches(String input) {
        if (isMatch(input)) {
            return Optional.of(options);
        }
        return Optional.empty();
    }

}
