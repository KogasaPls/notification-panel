package com.notificationpanel.Formatting.FormatOptions;

import com.notificationpanel.Formatting.FormatOption;
import lombok.Getter;
import lombok.Setter;

import java.awt.Color;
import java.util.Optional;

public class ColorOption extends FormatOption {
    @Getter
    @Setter
    private Color color;

    public ColorOption() {

    }

    public ColorOption(Color color) {
        this.color = color;
    }


    public Optional<ColorOption> parseValue(String value) throws NumberFormatException {
        Color color = Color.decode(value);
        ColorOption option = new ColorOption(color);
        return Optional.of(option);
    }

}
