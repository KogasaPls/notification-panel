package com.notificationpanel.Formatting.FormatOptions;

import java.util.Optional;

public class VisibilityOption {
    public static VisibilityOption Hidden = new VisibilityOption(false);
    public static VisibilityOption Visible = new VisibilityOption(true);
    private final boolean visible;

    public VisibilityOption(boolean visible) {
        this.visible = visible;
    }

    public static Optional<VisibilityOption> parse(String line) {
        switch (line.trim().toLowerCase()) {
            case "hide":
                return Optional.of(Hidden);
            case "show":
                return Optional.of(Visible);
            default:
                return Optional.empty();
        }
    }

    public boolean isVisible() {
        return visible;
    }
}
