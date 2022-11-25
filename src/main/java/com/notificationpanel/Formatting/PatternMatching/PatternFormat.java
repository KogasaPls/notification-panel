package com.notificationpanel.Formatting.PatternMatching;

import com.notificationpanel.Formatting.FormatOptions.FormatOption;

import java.util.Optional;
import java.util.regex.Pattern;

public class PatternFormat {

    private final Pattern pattern;
    private final FormatOption option;

    public PatternFormat(Pattern pattern, FormatOption option) {
        this.pattern = pattern;
        this.option = option;
    }

    private boolean isMatch(String input) {
        return pattern.matcher(input).matches();
    }

    public Optional<FormatOption> getOptionIfMatches(String input) {
        if (isMatch(input)) {
            return Optional.of(option);
        }
        return Optional.empty();
    }

}
