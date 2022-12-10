package com.notificationpanel.ConditionalFormatting;

import com.notificationpanel.NotificationPanelConfig;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.notificationpanel.ConditionalFormatting.ConditionalFormat.parseConditionalFormats;
import static java.util.stream.Collectors.toList;

public class ConditionalFormatParser {
    private final List<ConditionalFormat> conditionalFormats;

    private final NotificationPanelConfig config;

    public ConditionalFormatParser(NotificationPanelConfig config) {
        this.config = config;

        List<Pattern> patterns = PatternParser.parsePatternsConfig(config.regexList());
        String[] formatStrings = config.colorList().split("\\n");
        List<PartialFormat> formats = Stream
                .of(formatStrings)
                .map(PartialFormat::parseLine)
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
