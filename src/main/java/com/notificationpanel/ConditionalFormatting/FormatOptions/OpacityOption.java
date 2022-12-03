package com.notificationpanel.ConditionalFormatting.FormatOptions;

import java.util.Optional;

public class OpacityOption {
    private final int opacity;

    public OpacityOption(int opacity) {
        this.opacity = opacity;
    }

    private static int rescaleAndClamp(int value) {
        if (value < 0) {
            return 0;
        }

        if (value > 100) {
            return 255;
        }

        return value * 255 / 100;
    }

    public static Optional<OpacityOption> parse(String value) {
        try {
            int opacity = rescaleAndClamp(Integer.parseInt(value));
            OpacityOption option = new OpacityOption(opacity);
            return Optional.of(option);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public int getOpacity() {
        return opacity;
    }
}
