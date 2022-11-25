package com.notificationpanel.Formatting.FormatOptions;

import lombok.Getter;

public class VisibilityOption implements FormatOption {
    public static VisibilityOption Visible = new VisibilityOption(true);
    public static VisibilityOption Hidden = new VisibilityOption(false);
    @Getter
    private final boolean isVisible;

    public VisibilityOption(boolean isVisible) {
        this.isVisible = isVisible;
    }
}

