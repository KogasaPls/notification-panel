package com.notificationpanel.Formatting.FormatOptions;

import lombok.Getter;
import lombok.Setter;

public class OpacityOption implements FormatOption {
    @Getter
    private final int opacity;

    @Getter
    @Setter
    private static int defaultOpacity = 100;

    public OpacityOption(int opacity) {
        this.opacity = opacity;
    }
}

