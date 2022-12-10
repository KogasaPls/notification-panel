package com.notificationpanel.ConditionalFormatting.FormatOptions;

import java.text.ParseException;
import java.util.Optional;

public class VisibilityOption extends FormatOption {
    public static VisibilityOption Hidden = new VisibilityOption(false);
    public static VisibilityOption Visible = new VisibilityOption(true);
    private boolean isVisible;

    public VisibilityOption() {
    }

    ;

    private VisibilityOption(boolean visible) {
        this.isVisible = visible;
    }

    public static VisibilityOption FromBoolean(boolean visible) {
        return visible ? Visible : Hidden;
    }

    public Optional<VisibilityOption> parseValue(String value) throws ParseException {
        switch (value.trim().toLowerCase()) {
            case "hide":
                return Optional.of(Hidden);
            case "show":
                return Optional.of(Visible);
            default:
                throw new ParseException("Invalid visibility value: " + value, 0);
        }
    }

    public boolean isVisible() {
        return isVisible;
    }
}
