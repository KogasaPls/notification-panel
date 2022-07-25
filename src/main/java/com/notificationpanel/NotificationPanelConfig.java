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
				keyName = "duration",
				name = "Duration",
				description =
						"The number of units to show each notification. Set to 0" +
								" to never expire.")
	@Range(min = 0)
	default int duration()
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
				keyName = "opacity",
				name = "Opacity",
				description = "The level of opacity/transparency of the notification background.")
	@Units(PERCENT)
	@Range(min = 0, max = 100)
	default int opacity()
	{
		return 75;
	}

	@ConfigItem(position = 8,
				keyName = "regexList",
				name = "Regex",
				description =
						"List of regular expressions, one per line. Matching notifications get " +
								"the" +
								" color on the corresponding line.")
	default String regexList()
	{
		return "";
	}

	@ConfigItem(position = 9,
				keyName = "colorList",
				name = "Colors",
				description = "List of hex colors (e.g. #FF0000), one per line.")
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
