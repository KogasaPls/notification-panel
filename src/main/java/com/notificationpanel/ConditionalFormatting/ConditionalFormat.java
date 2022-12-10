package com.notificationpanel.ConditionalFormatting;

import com.notificationpanel.Formatting.PartialFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class ConditionalFormat {

    private final Pattern pattern;
    private final PartialFormat options;

    public ConditionalFormat(Pattern pattern, PartialFormat options) {
        this.pattern = pattern;
        this.options = options;
    }

    public static List<ConditionalFormat> parseConditionalFormats(List<Pattern> patterns, List<PartialFormat> optionsList) {
        List<ConditionalFormat> formats = new ArrayList<>();
        final int numPairs = Math.min(patterns.size(), optionsList.size());
        for (int i = 0; i < numPairs; i++) {
            final Pattern pattern = patterns.get(i);
            final PartialFormat options = optionsList.get(i);
            final ConditionalFormat format = new ConditionalFormat(pattern, options);
            formats.add(format);
        }

        return formats;
    }

    private boolean isMatch(String input) {
        return pattern.matcher(input).matches();
    }

    public Optional<PartialFormat> getFormatIfMatches(String input) {
        if (isMatch(input)) {
            return Optional.of(options);
        }
        return Optional.empty();
    }
}
