package com.notificationpanel.Formatting.FormatOptions;

import com.notificationpanel.Formatting.FormatOption;
import java.awt.Color;
import java.util.Optional;
import lombok.Getter;

public class ColorOption extends FormatOption
{
	@Getter
	private Color color;

	public ColorOption()
	{
		optionName = "color";
	}

	public ColorOption(Color color)
	{
		this.color = color;
	}


	public Optional<ColorOption> parseValue(String value) throws NumberFormatException
	{
		Color color = Color.decode(value);
		ColorOption option = new ColorOption(color);
		return Optional.of(option);
	}

}
