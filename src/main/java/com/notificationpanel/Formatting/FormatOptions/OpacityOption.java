package com.notificationpanel.Formatting.FormatOptions;

import lombok.Getter;
import lombok.Setter;

public class OpacityOption implements FormatOption {
    @Getter
    private int opacity;

    @Getter
    @Setter
    private static int defaultOpacity = 100;

    public OpacityOption() {
    }

    public OpacityOption(int opacity) {
        this.opacity = opacity;
    }

    public OpacityOption parse(String input) {
        int opacity = Integer.parseInt(input);
        return new OpacityOption(opacity);
    }
}

