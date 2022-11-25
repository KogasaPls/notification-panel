package com.notificationpanel.Formatting.FormatOptions;

import lombok.Getter;

import java.awt.*;

public class ColorOption implements FormatOption {
    @Getter
    private final Color color;

    public ColorOption(String color) {
        this.color = Color.decode(color);
    }

}
