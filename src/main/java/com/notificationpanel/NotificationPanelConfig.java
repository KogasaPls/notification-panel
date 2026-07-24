package com.notificationpanel;

import java.awt.Color;
import java.awt.Font;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.FontType;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;
import static net.runelite.client.config.Units.PERCENT;

/**
 * Storage for the plugin's settings.
 *
 * <p>Most are edited here in RuneLite's config panel. The background and opacity are not: a rule
 * can override those two, so they are edited in the sidebar next to the rules that do. Keys are
 * unchanged throughout, so existing configurations carry over untouched.</p>
 */
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
	default FontStyle fontType()
	{
		return FontStyle.BOLD;
	}

	// Edited in the sidebar, alongside the per-rule overrides of these same two attributes.
	@ConfigItem(position = 6,
		keyName = "bgColor",
		name = "Default Color",
		description = "The default background color of the notification window.",
		hidden = true)
	default Color bgColor()
	{
		return new Color(0x181818);
	}

	@ConfigItem(position = 7,
		keyName = "opacity",
		name = "Opacity",
		description = "The level of opacity/transparency of the notification background.",
		hidden = true)
	@Units(PERCENT)
	@Range(min = 0, max = 100)
	default int opacity()
	{
		return 75;
	}

	@ConfigItem(position = 8,
		keyName = "visibility",
		name = "Show notifications by default",
		description = "Whether a notification that matches no rule is shown. Notifications that "
			+ "match an enabled rule are always shown.")
	default boolean showUnmatchedByDefault()
	{
		return true;
	}

	// Toggled from the sidebar, next to the default formatting it previews. Stored rather than
	// held in the panel so it survives the sidebar being rebuilt.
	@ConfigItem(position = 9,
		keyName = "showTestNotification",
		name = "Show test notification",
		description = "Pin a sample notification to the panel.",
		hidden = true)
	default boolean showTestNotification()
	{
		return false;
	}

	@ConfigItem(position = 10,
		keyName = "regexList",
		name = "Regex",
		description =
			"List of regular expressions, one per line."
				+ " Matching notifications are formatted with the options in"
				+ " the corresponding line below.",
		hidden = true)
	default String regexList()
	{
		return "";
	}

	// keyName should be changed to "formatList," but this would break existing configs
	@ConfigItem(position = 11,
		keyName = "colorList",
		name = "Options",
		description = "List of format strings to apply to matching"
			+ " notifications, one comma-separated list of options per line."
			+ " Options can be a color (e.g. \"#bf616a\"), opacity"
			+ "(\"opacity=n\" where n is an integer in [0, 100]), 'hide' or 'show'.",
		hidden = true)
	default String colorList()
	{
		return "";
	}

	@ConfigItem(
		position = 12,
		keyName = "rulesV1",
		name = "",
		description = "",
		hidden = true
	)
	default String rulesV1()
	{
		return "";
	}

	enum TimeUnit
	{
		SECONDS("Seconds"), TICKS("Ticks");
		private final String value;

		TimeUnit(String value)
		{
			this.value = value;
		}

		@Override
		public String toString()
		{
			return value;
		}
	}

	/**
	 * The three RuneScape fonts this plugin offers.
	 *
	 * <p>RuneLite's own {@code FontType} used to be an enum of exactly these three. It is now an
	 * arbitrary font descriptor of family, size, bold and italic, and RuneLite's config panel
	 * renders any {@code FontType} item as a picker listing every font installed on the system.
	 * That is far more than this plugin wants to support, so the choice is a real enum again and
	 * RuneLite falls back to rendering a plain dropdown.</p>
	 *
	 * <p>Each constant delegates to the matching {@code FontType} preset, so the rendered text is
	 * unchanged. The names match what {@code FontTypeSerializer} stored for those presets, so an
	 * existing {@code fontType} setting still reads correctly. A value naming some other system
	 * font no longer parses; RuneLite logs a warning and falls back to this interface's default.
	 * </p>
	 */
	enum FontStyle
	{
		SMALL("Small", FontType.SMALL),
		REGULAR("Regular", FontType.REGULAR),
		BOLD("Bold", FontType.BOLD);

		private final String label;
		private final FontType fontType;

		FontStyle(String label, FontType fontType)
		{
			this.label = label;
			this.fontType = fontType;
		}

		public Font getFont()
		{
			return fontType.getFont();
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

}
