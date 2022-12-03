package com.notificationpanel.ConditionalFormatting;

import com.notificationpanel.ConditionalFormatting.FormatOptions.PartialFormat;
import com.notificationpanel.NotificationPanelConfig;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.notificationpanel.ConditionalFormatting.ConditionalFormat.parseConditionalFormats;
import static java.util.stream.Collectors.toList;

public class PatternMatchFormatter {
    private final List<ConditionalFormat> conditionalFormats;

    private final NotificationPanelConfig config;

    public PatternMatchFormatter(NotificationPanelConfig config) {
        this.config = config;

        List<Pattern> patterns = PatternParser.parsePatternsConfig(config.regexList());
        String[] formatStrings = config.colorList().split("\\n");
        List<PartialFormat> formats = Stream
                .of(formatStrings)
                .map(PartialFormat::parseLine)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toList());

        conditionalFormats = parseConditionalFormats(patterns, formats);
    }


    public NotificationFormat getFormat(String input) {
        PartialFormat defaults = PartialFormat.getDefaults(config);

        PartialFormat options = conditionalFormats
                .stream()
                .map(pf -> pf.getFormatIfMatches(input))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .reduce(PartialFormat::merge)
                .orElse(defaults);

        return new NotificationFormat(options, config);
    }

}
