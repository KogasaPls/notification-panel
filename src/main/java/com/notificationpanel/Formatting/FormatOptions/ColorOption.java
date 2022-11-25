package com.notificationpanel.Formatting.FormatOptions;

import lombok.Getter;

import java.awt.*;

public class ColorOption implements FormatOption {
    public static ColorOption defaultColor = new ColorOption(Color.BLACK);
    @Getter
    private final Color color;

    public ColorOption(Color color) {
        this.color = color;
    }

    public ColorOption(String color) {
        this.color = Color.decode(color);
    }

    public static void setDefaultColor(Color color) {
        defaultColor = new ColorOption(color);
    }

}
