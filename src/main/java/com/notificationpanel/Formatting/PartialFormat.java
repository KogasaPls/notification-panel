package com.notificationpanel.Formatting;

import com.notificationpanel.Formatting.FormatOptions.ColorOption;
import com.notificationpanel.Formatting.FormatOptions.OpacityOption;
import com.notificationpanel.Formatting.FormatOptions.VisibilityOption;
import com.notificationpanel.NotificationPanelConfig;

import java.util.ArrayList;
import java.util.List;

import static com.notificationpanel.Formatting.FormatOption.tryParseAsAny;


public class PartialFormat {
    private static final String REGEX_COMMA_OR_SPACES = "(,|\\s+)";

    private final static List<FormatOption> POSSIBLE_OPTIONS = new ArrayList<>();

    static {
        POSSIBLE_OPTIONS.add(new ColorOption());
        POSSIBLE_OPTIONS.add(new OpacityOption());
        POSSIBLE_OPTIONS.add(new VisibilityOption());
    }

    public final List<FormatOption> options = new ArrayList<>();

    private PartialFormat() {
    }

    public PartialFormat(List<FormatOption> options) {
        for (FormatOption option : options) {
            mergeOption(option);
        }
    }

    public static PartialFormat parseLine(String line) {
        final List<FormatOption> options = new ArrayList<>();
        final String[] words = line.split(REGEX_COMMA_OR_SPACES);
        for (String word : words) {
            tryParseAsAny(word, POSSIBLE_OPTIONS).ifPresent(options::add);
        }
        return new PartialFormat(options);
    }

    /**
     * @return a new FormatOptions containing all the options that first has,
     * plus any options that second has but first does not.
     */
    public static PartialFormat merge(PartialFormat first, PartialFormat second) {
        final PartialFormat merged = new PartialFormat(first.options);
        for (FormatOption option : second.options) {
            merged.mergeOption(option);
        }
        return merged;
    }

    public static PartialFormat getDefaults(NotificationPanelConfig config) {
        final List<FormatOption> options = new ArrayList<>();
        options.add(new ColorOption(config.bgColor()));
        options.add(new OpacityOption(config.opacity()));
        options.add(VisibilityOption.FromBoolean(config.visibility()));
        return new PartialFormat(options);
    }

    public void mergeOption(FormatOption option) {
        if (!hasOptionOfSameTypeAs(option)) {
            options.add(option);
        }
    }

    public boolean hasOptionOfSameTypeAs(FormatOption option) {
        return options
                .stream()
                .anyMatch(o -> o.getClass().equals(option.getClass()));
    }

    public PartialFormat mergeWithDefaults(NotificationPanelConfig config) {
        final PartialFormat defaults = getDefaults(config);
        return PartialFormat.merge(this, defaults);
    }

    public <T extends FormatOption> T getOptionOfType(Class<T> type) {
        try {
            return options.stream()
                          .filter(o -> o.getClass().equals(type))
                          .map(o -> (T) o)
                          .findFirst()
                          .orElse(null);
        } catch (ClassCastException e) {
            throw new RuntimeException("Tried to get option of type " + type.getSimpleName() + " but it was not of that type.");
        }
    }
}
