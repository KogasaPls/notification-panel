package com.notificationpanel.Formatting.FormatOptions;

import com.notificationpanel.Formatting.FormatOption;
import com.notificationpanel.NotificationPanelConfig;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;

public class DurationOption extends FormatOption
{
	@Getter
	@Setter
	private static NotificationPanelConfig.TimeUnit timeUnit = NotificationPanelConfig.TimeUnit.SECONDS;
	@Getter
	private int duration;

	public DurationOption()
	{
		optionName = "duration";
	}

	public DurationOption(int duration)
	{
		this.duration = duration;
	}

	public Optional<DurationOption> parseValue(String value)
	{

		int newDuration = Integer.parseInt(value);

		final char lastChar = value.charAt(value.length() - 1);

		if (lastChar == 's' || lastChar == 't')
		{
			newDuration = Integer.parseInt(value.substring(0, value.length() - 1));
			switch (lastChar)
			{
				case 's':
					timeUnit = NotificationPanelConfig.TimeUnit.SECONDS;
					break;
				case 't':
					timeUnit = NotificationPanelConfig.TimeUnit.TICKS;
					break;
			}
		}


		DurationOption option = new DurationOption(newDuration);
		return Optional.of(option);
	}
}

