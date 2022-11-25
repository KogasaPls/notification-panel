package com.notificationpanel.Config;

import com.notificationpanel.Formatting.FormatOptions.ColorOption;
import com.notificationpanel.Formatting.FormatOptions.FormatOption;
import com.notificationpanel.Formatting.FormatOptions.OpacityOption;
import com.notificationpanel.Formatting.FormatOptions.VisibilityOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.notificationpanel.Formatting.FormatOptions.FormatOption.tryParseAs;

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
        FormatOption[] possibleOptions = {new ColorOption(), new VisibilityOption(), new OpacityOption()};

        List<FormatOption> containerForOption = new ArrayList<>();
        for (FormatOption option : possibleOptions) {
            tryParseAs(line, option).ifPresent(containerForOption::add);
            if (containerForOption.size() > 0) {
                return Optional.of(containerForOption.get(0));
            }
        }

        return Optional.empty();
    }
}
