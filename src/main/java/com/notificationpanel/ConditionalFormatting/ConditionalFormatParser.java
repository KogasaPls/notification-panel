package com.notificationpanel.ConditionalFormatting;

import com.notificationpanel.Formatting.Format;
import com.notificationpanel.Formatting.PartialFormat;
import com.notificationpanel.NotificationPanelConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class ConditionalFormatParser {
    private final List<ConditionalFormat> conditionalFormats;

    private final NotificationPanelConfig config;

    public ConditionalFormatParser(NotificationPanelConfig config) {
        this.config = config;

        List<Pattern> patterns = parsePatterns(config.regexList());
        List<PartialFormat> formats = parseFormats(config.colorList());
        conditionalFormats = parseConditionalFormats(patterns, formats);
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

    private static List<Pattern> parsePatterns(String patternsConfig) {
        String[] lines = patternsConfig.split("\\n+");
        List<Pattern> patterns = new ArrayList<>();
        for (String line : lines) {
            Pattern pattern = Pattern.compile(line);
            patterns.add(pattern);
        }
        return patterns;
    }

    private static List<PartialFormat> parseFormats(String formatsConfig) {
        String[] formatStrings = formatsConfig.split("\\n");

        return Stream
                .of(formatStrings)
                .map(PartialFormat::parseLine)
                .collect(toList());
    }

    public Format getFormat(String input) {
        PartialFormat defaults = PartialFormat.getDefaults(config);

        PartialFormat options = conditionalFormats
                .stream()
                .map(pf -> pf.getFormatIfMatches(input))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .reduce(PartialFormat::merge)
                .orElse(defaults);

        return new Format(options, config);
    }
}
