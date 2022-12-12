package com.notificationpanel.Formatting.FormatOptions;

import com.notificationpanel.Formatting.FormatOption;
import java.util.Optional;
import lombok.Getter;

public class ShowTimeOption extends FormatOption
{
	@Getter
	private boolean showTime;

	public ShowTimeOption()
	{
		optionName = "showTime";
	}

	public ShowTimeOption(boolean showTime)
	{
		this.showTime = showTime;
	}

	public Optional<ShowTimeOption> parseValue(String value)
	{
		boolean showTime = Boolean.parseBoolean(value);
		ShowTimeOption option = new ShowTimeOption(showTime);
		return Optional.of(option);
	}
}
