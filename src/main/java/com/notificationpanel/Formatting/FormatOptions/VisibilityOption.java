package com.notificationpanel.Formatting.FormatOptions;

import lombok.Getter;

public class VisibilityOption implements FormatOption {
    public static VisibilityOption Visible = new VisibilityOption(true);
    public static VisibilityOption Hidden = new VisibilityOption(false);
    @Getter
    private boolean isVisible;

    public VisibilityOption() {
    }
    public VisibilityOption(boolean isVisible) {
        this.isVisible = isVisible;
    }

    public VisibilityOption parse(String input) throws Exception {
            String[] split = input.split("=");
            if (split.length == 1) {
                String value = split[0];
                if (value.equals("true")) {
                    return Visible;
                } else if (value.equals("false")) {
                    return Hidden;
                }
            }
            else if (split.length == 2) {
                String key = split[0];
                String value = split[1];
                if (key.equals("visible")) {
                    if (value.equals("true")) {
                        return Visible;
                    } else if (value.equals("false")) {
                        return Hidden;
                    }
                }
            }
            return null;
    }
}

