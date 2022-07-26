package com.notificationpanel.Formatting;

import static com.notificationpanel.Formatting.FormatOption.tryParseAsAny;
import com.notificationpanel.Formatting.FormatOptions.ColorOption;
import com.notificationpanel.Formatting.FormatOptions.DurationOption;
import com.notificationpanel.Formatting.FormatOptions.OpacityOption;
import com.notificationpanel.Formatting.FormatOptions.ShowTimeOption;
import com.notificationpanel.Formatting.FormatOptions.VisibilityOption;
import com.notificationpanel.NotificationPanelConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public class PartialFormat
{
	private static final String REGEX_COMMA_OR_SPACES = "(,|\\s+)";

	private final static List<FormatOption> possibleOptions = new ArrayList<>();

	static
	{
		possibleOptions.add(new ColorOption());
		possibleOptions.add(new OpacityOption());
		possibleOptions.add(new VisibilityOption());
		possibleOptions.add(new ShowTimeOption());
		possibleOptions.add(new DurationOption());
	}

	public final List<FormatOption> options = new ArrayList<>();

	public PartialFormat(List<FormatOption> options)
	{
		for (FormatOption option : options)
		{
			mergeOption(option);
		}
	}

	public static PartialFormat parseLine(String line)
	{
		final List<FormatOption> options = new ArrayList<>();
		final String[] words = line.split(REGEX_COMMA_OR_SPACES);
		for (String word : words)
		{
			tryParseAsAny(word, possibleOptions).ifPresent(options::add);
		}
		return new PartialFormat(options);
	}

	/**
	 * Combines the options in first and second, giving priority to the options in first.
	 */
	public static PartialFormat merge(PartialFormat first, PartialFormat second)
	{
		final PartialFormat merged = new PartialFormat(first.options);
		for (FormatOption option : second.options)
		{
			merged.mergeOption(option);
		}
		return merged;
	}

	public static PartialFormat getDefaults(NotificationPanelConfig config)
	{
		final List<FormatOption> options = new ArrayList<>();
		options.add(new ColorOption(config.bgColor()));
		options.add(new OpacityOption(config.opacity()));
		options.add(VisibilityOption.FromBoolean(config.visibility()));
		options.add(new ShowTimeOption(config.showTime()));
		options.add(new DurationOption(config.expireTime()));
		return new PartialFormat(options);
	}

	private void mergeOption(FormatOption option)
	{
		if (!hasOptionOfSameTypeAs(option))
		{
			options.add(option);
		}
	}

	private boolean hasOptionOfSameTypeAs(FormatOption option)
	{
		return options
			.stream()
			.anyMatch(o -> o.getClass().equals(option.getClass()));
	}

	public <T extends FormatOption> Optional<T> getOptionOfType(Class<T> type)
	{
		try
		{
			return options
				.stream()
				.filter(o -> o.getClass().equals(type))
				.map(o -> (T) o)
				.findFirst();
		}
		catch (ClassCastException e)
		{
			throw new RuntimeException("Tried to get option of type " + type.getSimpleName() + " but it was not of that type.", e);
		}
	}
}
