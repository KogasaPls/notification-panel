package com.notificationpanel.Formatting.FormatOptions;

import lombok.Getter;
import lombok.Setter;

import java.awt.*;

public class ColorOption implements FormatOption {
    @Getter
    @Setter
    private static Color defaultColor = Color.BLACK;
    @Getter
    private Color color;

    public ColorOption()
    {

    }

    public ColorOption(Color color) {
        this.color = color;
    }

    public ColorOption parse(String input) {
        if (input.startsWith("#")) {
            return new ColorOption(Color.decode(input));
        }
        return null;
    }

}
