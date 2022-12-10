package com.notificationpanel.ConditionalFormatting.FormatOptions;

import java.util.Optional;

public class VisibilityOption {
    public static VisibilityOption Hidden = new VisibilityOption(false);
    public static VisibilityOption Visible = new VisibilityOption(true);
    private final boolean isVisible;

    private VisibilityOption(boolean visible) {
        this.isVisible = visible;
    }

    public static VisibilityOption FromBoolean(boolean visible) {
        return visible ? Visible : Hidden;
    }

    public static Optional<VisibilityOption> parse(String value) {
        switch (value.trim().toLowerCase()) {
            case "hide":
                return Optional.of(Hidden);
            case "show":
                return Optional.of(Visible);
            default:
                return Optional.empty();
        }
    }

    public boolean isVisible() {
        return isVisible;
    }
}
