package com.notificationpanel.ConditionalFormatting;

import com.notificationpanel.Formatting.PartialFormat;
import java.util.Optional;
import java.util.regex.Pattern;

public class ConditionalFormat
{

	private final Pattern pattern;
	private final PartialFormat options;

	public ConditionalFormat(Pattern pattern, PartialFormat options)
	{
		this.pattern = pattern;
		this.options = options;
	}

	private boolean isMatch(String input)
	{
		return pattern.matcher(input).matches();
	}

	public Optional<PartialFormat> getFormatIfMatches(String input)
	{
		if (isMatch(input))
		{
			return Optional.of(options);
		}
		return Optional.empty();
	}
}
