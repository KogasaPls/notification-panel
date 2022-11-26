package com.notificationpanel.Formatting;

import com.notificationpanel.Formatting.FormatOptions.FormatOptions;
import lombok.Getter;

import java.awt.*;

public class NotificationFormat {
    public Color color;
    public Boolean isVisible;
    public Integer opacity;

    public NotificationFormat(FormatOptions options) {
        if (options.getColor() != null) {
            this.color = options.getColor();
        }
        if (options.getOpacity() != null) {
            this.opacity = options.getOpacity();
        }
        if (options.getIsVisible() != null) {
            this.isVisible = options.getIsVisible();
        }
    }
}
