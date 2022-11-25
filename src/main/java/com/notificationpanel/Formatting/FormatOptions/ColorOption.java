package com.notificationpanel.Formatting.FormatOptions;

import lombok.Getter;
import lombok.Setter;

import java.awt.*;

public class ColorOption implements FormatOption {
    @Getter
    @Setter
    private static Color defaultColor = Color.BLACK;
    @Getter
    private final Color color;

    public ColorOption(Color color) {
        this.color = color;
    }

    public ColorOption(String color) {
        this.color = Color.decode(color);
    }

}
