package com.notificationpanel.Formatting.FormatOptions;

import com.notificationpanel.Formatting.FormatOption;

import java.util.Optional;

public class OpacityOption extends FormatOption {
    private int opacity;

    public OpacityOption() {
        optionName = "opacity";
    }

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

    public Optional<OpacityOption> parseValue(String value) {
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
