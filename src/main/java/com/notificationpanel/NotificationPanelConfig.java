package com.notificationpanel;

import java.awt.Color;
import lombok.AllArgsConstructor;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.FontType;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;
import static net.runelite.client.config.Units.PERCENT;

@ConfigGroup("notificationpanel")
public interface NotificationPanelConfig extends Config
{

	@ConfigItem(position = 1,
		keyName = "expireTime",
		name = "Duration",
		description =
			"The number of units to show each notification. Set to 0" +
				" to never expire.")
	@Range(min = 0)
	default int expireTime()
	{
		return 3;
	}

	@ConfigItem(position = 2,
		keyName = "timeUnit",
		name = "Time Unit",
		description = "The unit in which to measure the notification duration.")
	default TimeUnit timeUnit()
	{
		return TimeUnit.SECONDS;
	}

	@ConfigItem(position = 3,
		keyName = "numToShow",
		name = "Number shown",
		description = "The maximum number of notifications which should be displayed at " +
			"once.")

	@Range(min = 1, max = 5)
	default int numToShow()
	{
		return 1;
	}

	@ConfigItem(position = 4,
		keyName = "showTime",
		name = "Show time",
		description =
			"Show the time remaining on the notification, or the age if it won't" +
				" expire")
	default boolean showTime()
	{
		return true;
	}

	@ConfigItem(position = 5,
		keyName = "fontType",
		name = "Font Style",
		description = "The font style of the notification text.")
	default FontType fontType()
	{
		return FontType.BOLD;
	}

	@ConfigItem(position = 6,
		keyName = "bgColor",
		name = "Default Color",
		description = "The default background color of the notification window.")
	default Color bgColor()
	{
		return new Color(0x181818);
	}

	@ConfigItem(position = 7,
		keyName = "fgColor",
		name = "Default Text Color",
		description = "The default text color of the notification window.")
	default Color fgColor()
	{
		return new Color(0xFFFFFF);
	}

	@ConfigItem(position = 8,
		keyName = "borderColor",
		name = "Default Border Color",
		description = "The default border color of the notification window.")
	default Color borderColor()
	{
		return new Color(0x000000);
	}

	@ConfigItem(position = 9,
		keyName = "width",
		name = "Max Width",
		description = "The maximum width of the notification window in pixels.")
	default int width()
	{
		return 300;
	}

	@ConfigItem(position = 10,
		keyName = "height",
		name = "Max Height",
		description = "The maximum height of the notification window in pixels.")
	default int height()
	{
		return 100;
	}

	@ConfigItem(position = 11,
		keyName = "padding",
		name = "Padding",
		description = "The padding between the notification window and the text in pixels.")
	default int padding()
	{
		return 10;
	}


	@ConfigItem(position = 12,
		keyName = "opacity",
		name = "Opacity",
		description = "The level of opacity/transparency of the notification background.")
	@Units(PERCENT)
	@Range(min = 0, max = 100)
	default int opacity()
	{
		return 75;
	}

	@ConfigItem(position = 13,
		keyName = "visibility",
		name = "Visibility",
		description = "Whether or not notifications are visible by default.")
	default boolean visibility()
	{
		return true;
	}

	@ConfigItem(position = 14,
		keyName = "regexList",
		name = "Regex",
		description =
			"List of regular expressions, one per line."
				+ " Matching notifications are formatted with the options in"
				+ " the corresponding line below.")
	default String regexList()
	{
		return "";
	}

	// keyName should be changed to "formatList," but this would break existing configs
	@ConfigItem(position = 15,
		keyName = "colorList",
		name = "Options",
		description = "List of format strings to apply to matching"
			+ " notifications, one comma-separated list of options per line."
			+ " Options can be a color (e.g. \"#bf616a\"), opacity"
			+ "(\"opacity=n\" where n is an integer in [0, 100]), 'hide' or 'show'.")
	default String colorList()
	{
		return "";
	}

	@AllArgsConstructor
	enum TimeUnit
	{
		SECONDS("Seconds"), TICKS("Ticks");
		private final String value;

		@Override
		public String toString()
		{
			return value;
		}
	}

}
