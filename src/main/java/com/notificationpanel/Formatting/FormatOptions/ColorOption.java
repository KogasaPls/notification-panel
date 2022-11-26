package com.notificationpanel.Formatting.FormatOptions;

import java.awt.*;
import java.util.Optional;

public class ColorOption {
    private Color color;

    public ColorOption(Color color) {
        this.color = color;
    }

    public static Optional<ColorOption> parse(String line) {
        try {
            return Optional.of(new ColorOption(Color.decode(line)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public Color getColor() {
        return color;
    }
}
