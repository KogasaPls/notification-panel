package com.notificationpanel.Formatting.FormatOptions;

import com.notificationpanel.NotificationPanelConfig;
import lombok.Getter;

import java.awt.*;

public class FormatOptions {
    @Getter
    private Color color;
    @Getter
    private Integer opacity;
    @Getter
    private Boolean isVisible;

    public static FormatOptions getDefaultOptions(NotificationPanelConfig config)
    {
        FormatOptions options = new FormatOptions();
        options.color = config.bgColor();
        options.opacity = config.opacity();
        options.isVisible = true;
        return options;
    }

    public FormatOptions() {
    }

    private void setColor(ColorOption color) {
        if (this.color == null) {
            this.color = color.getColor();
        }
    }

    private void setOpacity(OpacityOption opacity) {
        if (this.opacity == null) {
            this.opacity = opacity.getOpacity();
        }
    }

    private void setVisibility(VisibilityOption visibility) {
        if (this.isVisible == null) {
            this.isVisible = visibility.isVisible();
        }
    }

    public static FormatOptions parseLine(String line) {
        FormatOptions options = new FormatOptions();
        String[] words = line.split("(,|\\s)+");
        for (String word : words) {
            options.parseKeyValuePair(word);
        }
        return options;
    }

    public static FormatOptions merge(FormatOptions first, FormatOptions second) {
        FormatOptions options = new FormatOptions();
        options.color = first.color != null ? first.color : second.color;
        options.opacity = first.opacity != null ? first.opacity : second.opacity;
        options.isVisible = first.isVisible != null ? first.isVisible : second.isVisible;
        return options;
    }

    public void parseKeyValuePair(String line) {
        String[] split = line.split("=");
        try {
            if (split[0].startsWith("#"))
            {
                ColorOption.parse(split[0]).ifPresent(this::setColor);
            }

            switch (split[0].toLowerCase()) {
                case "color":
                    ColorOption.parse(split[1]).ifPresent(this::setColor);
                    break;
                case "opacity":
                    OpacityOption.parse(split[1]).ifPresent(this::setOpacity);
                    break;
                case "hide":
                case "show":
                    VisibilityOption.parse(split[1]).ifPresent(this::setVisibility);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            System.out.println("Error parsing option: " + line);
        }
    }
}
