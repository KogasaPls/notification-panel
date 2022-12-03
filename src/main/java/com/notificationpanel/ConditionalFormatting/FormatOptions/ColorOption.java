package com.notificationpanel.ConditionalFormatting.FormatOptions;

import java.awt.Color;
import java.util.Optional;

public class ColorOption {
    private final Color color;

    public ColorOption(Color color) {
        this.color = color;
    }

    public static Optional<ColorOption> parse(String value) {
        try {
            Color color = Color.decode(value);
            ColorOption option = new ColorOption(color);
            return Optional.of(option);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public Color getColor() {
        return color;
    }
}
