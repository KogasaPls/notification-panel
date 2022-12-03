package com.notificationpanel.ConditionalFormatting.FormatOptions;

import com.notificationpanel.NotificationPanelConfig;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public class PartialFormat {
    private static final String REGEX_COMMA_OR_SPACES = "(,|\\s+)";
    @Getter
    public ColorOption color;
    @Getter
    public OpacityOption opacity;
    @Getter
    public VisibilityOption isVisible;

    public PartialFormat() {
    }

    public static Optional<PartialFormat> parseLine(String line) {
        final List<PartialFormat> options = new ArrayList<>();
        final String[] words = line.split(REGEX_COMMA_OR_SPACES);
        for (String word : words) {
            PartialFormat parsedOptions = parseWord(word);
            options.add(parsedOptions);
        }
        return options.stream().reduce(PartialFormat::merge);
    }

    public static PartialFormat parseWord(String word) {
        PartialFormat options = new PartialFormat();

        final String[] split = word.split("=", 2);
        final String key = split[0];

        if (split.length == 1) {
            options.parseKey(key);
        } else {
            final String value = split[1];
            options.parseKeyValuePair(key, value);
        }

        return options;
    }

    /**
     * @return a new FormatOptions containing all the options that first has,
     * plus any options that second has but first does not.
     */
    public static PartialFormat merge(PartialFormat first, PartialFormat second) {
        final PartialFormat options = new PartialFormat();
        options.color = first.color != null ? first.color : second.color;
        options.opacity = first.opacity != null ? first.opacity : second.opacity;
        options.isVisible = first.isVisible != null ? first.isVisible : second.isVisible;
        return options;
    }

    public static PartialFormat getDefaults(NotificationPanelConfig config) {
        final PartialFormat format = new PartialFormat();
        format.color = new ColorOption(config.bgColor());
        format.opacity = new OpacityOption(config.opacity());
        format.isVisible = VisibilityOption.GetOptionForVisibility(config.visibility());
        return format;
    }

    private void setColor(ColorOption color) {
        if (this.color == null) {
            this.color = color;
        }
    }

    private void setOpacity(OpacityOption opacity) {
        if (this.opacity == null) {
            this.opacity = opacity;
        }
    }

    private void setVisibility(VisibilityOption visibility) {
        if (this.isVisible == null) {
            this.isVisible = visibility;
        }
    }

    public void parseKey(String key) {
        if (key.startsWith("#")) {
            ColorOption.parse(key).ifPresent(this::setColor);
        }
        switch (key.toLowerCase()) {
            case "hide":
            case "show":
                VisibilityOption.parse(key).ifPresent(this::setVisibility);
                break;
            default:
                break;
        }
    }

    public void parseKeyValuePair(String key, String value) {
        switch (key.toLowerCase()) {
            case "color":
                ColorOption.parse(value).ifPresent(this::setColor);
                break;
            case "opacity":
                OpacityOption.parse(value).ifPresent(this::setOpacity);
                break;
            default:
                break;
        }
    }

    public PartialFormat mergeWithDefaults(NotificationPanelConfig config) {
        final PartialFormat defaults = getDefaults(config);
        return PartialFormat.merge(this, defaults);
    }
}
