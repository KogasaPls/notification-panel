package com.notificationpanel.Formatting.FormatOptions;

import java.util.Optional;

public class OpacityOption {
    private final int opacity;
    public OpacityOption(int opacity) {
        this.opacity = opacity;
    }

    public int getOpacity() {
        return opacity;
    }

    public static Optional<OpacityOption> parse(String line) {
        try {
            return Optional.of(new OpacityOption(Integer.parseInt(line)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
