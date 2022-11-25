package com.notificationpanel.Config;

import com.notificationpanel.Formatting.FormatOptions.ColorOption;
import com.notificationpanel.Formatting.FormatOptions.FormatOption;
import com.notificationpanel.Formatting.FormatOptions.VisibilityOption;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FormatOptionParser {

    public static List<FormatOption> parseOptionsConfig(String optionsConfig) {
        String[] lines = optionsConfig.split("\\n+");
        List<FormatOption> options = new ArrayList<>();
        for (String line : lines) {
            Optional<FormatOption> option = parseOptionLine(line);
            option.ifPresent(options::add);
        }
        return options;
    }

    private static Optional<FormatOption> parseOptionLine(String line) {
        String[] split = line.split("\\s+");
        String optionName = split[0];

        if (split.length == 1) {
            return Optional.ofNullable(parseSingleOption(optionName));
        }

        if (split.length == 2) {
            String value = split[1];
            return Optional.ofNullable(parseDoubleOption(optionName, value));
        }

        return Optional.empty();
    }

    private static FormatOption parseSingleOption(String optionName) {
        if (optionName.startsWith("#")) {
            return new ColorOption(optionName);
        }
        switch (optionName.toLowerCase()) {
            case "hide":
                return VisibilityOption.Hidden;
            case "show":
                return VisibilityOption.Visible;
            default:
                return null;
        }
    }

    private static FormatOption parseDoubleOption(String optionName, String optionValue) {
        if ("color".equalsIgnoreCase(optionName)) {
            return new ColorOption(optionValue);
        }
        return null;
    }
}
