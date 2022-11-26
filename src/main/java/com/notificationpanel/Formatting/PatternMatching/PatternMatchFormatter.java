package com.notificationpanel.Formatting.PatternMatching;

import com.notificationpanel.Config.PatternParser;
import com.notificationpanel.Formatting.FormatOptions.FormatOptions;
import com.notificationpanel.Formatting.NotificationFormat;
import com.notificationpanel.NotificationPanelConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class PatternMatchFormatter {
    private final List<PatternFormat> patternFormats;

    private final NotificationPanelConfig config;

    public PatternMatchFormatter(NotificationPanelConfig config) {
        this.config = config;

        List<Pattern> patterns = PatternParser.parsePatternsConfig(config.regexList());
        List<FormatOptions> formats = Stream.of(config.colorList().split("\\n")).map(FormatOptions::parseLine).collect(toList());

        patternFormats = parsePatternFormats(patterns, formats);
    }

    public static List<PatternFormat> parsePatternFormats(List<Pattern> patterns, List<FormatOptions> formats) {
        List<PatternFormat> patternFormats = new ArrayList<>();
        final int numPairs = Math.min(patterns.size(), formats.size());
        for (int i = 0; i < numPairs; i++) {
            final Pattern pattern = patterns.get(i);
            final FormatOptions format = formats.get(i);
            final PatternFormat pf = new PatternFormat(pattern, format);
            patternFormats.add(pf);
        }

        return patternFormats;
    }
    public NotificationFormat getFormat(String input) {
        List<FormatOptions> matchedFormats = patternFormats.stream()
                .map(pf -> pf.getOptionIfMatches(input))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toList());

        FormatOptions defaults = FormatOptions.getDefaultOptions(config);
        FormatOptions accumulatedOptions = matchedFormats.stream()
                .reduce(FormatOptions::merge).orElse(defaults);

        FormatOptions finalOptions = FormatOptions.merge(accumulatedOptions, defaults);

        return new NotificationFormat(finalOptions);
    }

}
