package com.notificationpanel.Formatting.FormatOptions;

import lombok.Getter;

public class OpacityOption implements FormatOption {
    @Getter
    private final int opacity;

    public OpacityOption(int opacity) {
        this.opacity = opacity;
    }
}

