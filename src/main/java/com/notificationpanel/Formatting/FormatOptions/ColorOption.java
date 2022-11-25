package com.notificationpanel.Formatting.FormatOptions;

import lombok.Getter;
import lombok.Setter;

import java.awt.*;

public class ColorOption extends FlagOption {
    @Getter
    @Setter
    private static Color defaultColor = Color.BLACK;
    @Getter
    private Color color;

    public ColorOption() {
        super(String.valueOf(defaultColor.getRGB()));
    }

    public ColorOption(Color color) {
        super(String.valueOf(color.getRGB()));
        this.color = color;
    }

    public ColorOption parse(String input) {
        if (!input.startsWith("#"))
        {
            return null;
        }
        FlagOption option = super.parse(input);
        return new ColorOption(Color.decode(option.getFlag()));
    }

}
