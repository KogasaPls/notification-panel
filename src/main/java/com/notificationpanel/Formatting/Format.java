package com.notificationpanel.Formatting;

import com.notificationpanel.Formatting.FormatOptions.ColorOption;
import com.notificationpanel.Formatting.FormatOptions.OpacityOption;
import com.notificationpanel.Formatting.FormatOptions.VisibilityOption;
import com.notificationpanel.NotificationPanelConfig;
import lombok.Getter;

import java.awt.Color;

public class Format {
    @Getter
    private Integer opacity;
    @Getter
    private Color color;
    @Getter
    private Boolean isVisible;

    public Format withOptions(PartialFormat options) {
        options.getOptionOfType(ColorOption.class).ifPresent(this::setColor);
        options.getOptionOfType(OpacityOption.class).ifPresent(this::setOpacity);
        options.getOptionOfType(VisibilityOption.class).ifPresent(this::setIsVisible);
        return this;
    }

    public static Format getDefault(NotificationPanelConfig config) {
        return new Format().withOptions(PartialFormat.getDefaults(config));
    }
    
    private void setColor(ColorOption option) {
        this.color = option.getColor();
    }

    private void setIsVisible(VisibilityOption option) {
        this.isVisible = option.isVisible();
    }

    private void setOpacity(OpacityOption option) {
        this.opacity = option.getOpacity();
    }


    public Color getColorWithOpacity() {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), opacity);
    }
}
