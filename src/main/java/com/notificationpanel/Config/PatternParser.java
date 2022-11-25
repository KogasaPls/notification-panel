package com.notificationpanel.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class PatternParser {
    public static Pattern parseLine(String pattern) {
        return Pattern.compile(pattern);
    }

    public static List<Pattern> parsePatternsConfig(String patternsConfig) {
        String[] lines = patternsConfig.split("\\n+");
        List<Pattern> patterns = new ArrayList<>();
        for (String line : lines) {
            Pattern pattern = parseLine(line);
            patterns.add(pattern);
        }
        return patterns;
    }
}
