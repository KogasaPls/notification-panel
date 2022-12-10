package com.notificationpanel.ConditionalFormatting;

import com.notificationpanel.NotificationPanelConfig;
import lombok.Getter;

import java.awt.Color;

public class NotificationFormat {
    @Getter
    private final Color color;
    @Getter
    private final Boolean isVisible;
    @Getter
    private final Integer opacity;

    public NotificationFormat(PartialFormat options, NotificationPanelConfig config) {
        PartialFormat optionsWithDefaults = options.mergeWithDefaults(config);
        this.color = optionsWithDefaults.color.getColor();
        this.isVisible = optionsWithDefaults.isVisible.isVisible();
        this.opacity = optionsWithDefaults.opacity.getOpacity();
    }

    public Color getColorWithOpacity() {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), opacity);
    }
}
