package com.notificationpanel.Formatting.PatternMatching;

import com.notificationpanel.Config.FormatOptionParser;
import com.notificationpanel.Config.PatternParser;
import com.notificationpanel.Formatting.FormatOptions.FormatOption;
import com.notificationpanel.Formatting.NotificationFormat;
import com.notificationpanel.NotificationPanelConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class PatternMatchFormatter {
    private final List<PatternFormat> patternFormats = new ArrayList<>();

    private final NotificationPanelConfig config;

    public PatternMatchFormatter(NotificationPanelConfig config) {
        this.config = config;

        List<Pattern> patterns = PatternParser.parsePatternsConfig(config.regexList());
        List<FormatOption> options = FormatOptionParser.parseOptionsConfig(config.colorList());

        final int numPairs = Math.min(patterns.size(), options.size());
        for (int i = 0; i < numPairs; i++) {
            final PatternFormat pf = new PatternFormat(patterns.get(i), options.get(i));
            patternFormats.add(pf);
        }
    }
    public NotificationFormat getFormat(String input) {
        NotificationFormat.Builder builder = new NotificationFormat.Builder(config);
        for (PatternFormat patternFormat : patternFormats) {
            patternFormat.getOptionIfMatches(input).ifPresent(builder::setOption);
        }
        return builder.build();
    }

}
