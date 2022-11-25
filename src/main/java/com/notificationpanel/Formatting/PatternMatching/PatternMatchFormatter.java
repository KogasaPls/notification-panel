package com.notificationpanel.Formatting.PatternMatching;
import com.notificationpanel.Formatting.FormatOptions.FormatOption;
import com.notificationpanel.Formatting.NotificationFormat;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class PatternMatchFormatter {
    private final List<PatternFormat> patternFormats = new ArrayList<>();

    private static Color defaultColor = Color.BLACK;

    public void setDefaultColor(Color color) {
        defaultColor = color;
    }

    public PatternMatchFormatter(List<Pattern> patterns, List<FormatOption> options) {
        final int numPairs = Math.min(patterns.size(), options.size());
        for (int i = 0; i < numPairs; i++) {
            final PatternFormat pf = new PatternFormat(patterns.get(i), options.get(i));
            patternFormats.add(pf);
        }
    }

    public NotificationFormat getFormat(String input) {
        NotificationFormat.Builder builder = new NotificationFormat.Builder(defaultColor);
        for (PatternFormat patternFormat : patternFormats) {
            patternFormat.getOptionIfMatches(input).ifPresent(builder::setOption);
        }
        return builder.build();
    }

}
