/*
 * Copyright (c) 2026, KogasaPls
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.notificationpanel;

import com.notificationpanel.rules.Visibility;
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
 * Storage for plugin configuration settings.
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

	int DEFAULT_BACKGROUND_RGB = 0x181818;

	@ConfigItem(position = 6,
		keyName = "bgColor",
		name = "Default Color",
		description = "The background color every notification is drawn with, unless a rule "
			+ "overrides it.")
	default Color bgColor()
	{
		return new Color(DEFAULT_BACKGROUND_RGB);
	}

	@ConfigItem(position = 7,
		keyName = "opacity",
		name = "Default Opacity",
		description = "How opaque the notification background is, unless a rule overrides it. "
			+ "0 is invisible and 100 is solid.")
	@Units(PERCENT)
	@Range(min = 0, max = 100)
	default int opacity()
	{
		return 75;
	}

	@ConfigItem(position = 8,
		keyName = "showTestNotification",
		name = "Show test notification",
		description = "Pin a sample notification to the panel. It never expires, so it previews "
			+ "the color and opacity above and gives you something to grab when moving or "
			+ "resizing the panel.")
	default boolean showTestNotification()
	{
		return false;
	}

	@ConfigItem(position = 9,
		keyName = "defaultVisibility",
		name = "Default visibility",
		description = "Where a notification that matches no rule goes. One that does match follows "
			+ "the first matching rule that sets Visibility, or is shown if none of them do.")
	default DefaultVisibility defaultVisibility()
	{
		return DefaultVisibility.SHOW;
	}

	@ConfigItem(position = 14,
		keyName = "visibility",
		name = "",
		description = "",
		hidden = true)
	default boolean showUnmatchedByDefault()
	{
		return true;
	}

	// Default must remain empty so RuneLite does not write default values before migration runs.
	@ConfigItem(position = 15,
		keyName = "defaultVisibilityAdopted",
		name = "",
		description = "",
		hidden = true)
	default String defaultVisibilityAdopted()
	{
		return "";
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

	@ConfigItem(position = 13,
		keyName = "showSidebarButton",
		name = "Show sidebar button",
		description = "Show the Notification Panel button in the RuneLite toolbar. The rule "
			+ "editor lives there, so turn this back on to reach it.")
	default boolean showSidebarButton()
	{
		return true;
	}

	/**
	 * Returns the configured background color, falling back to default if null.
	 */
	static Color backgroundOrDefault(NotificationPanelConfig config)
	{
		Color stored = config.bgColor();
		return stored == null ? new Color(DEFAULT_BACKGROUND_RGB) : stored;
	}

	/**
	 * Returns the configured default visibility, falling back to SHOW if null.
	 */
	static DefaultVisibility defaultVisibilityOrShow(NotificationPanelConfig config)
	{
		DefaultVisibility stored = config.defaultVisibility();
		return stored == null ? DefaultVisibility.SHOW : stored;
	}

	static FontStyle fontTypeOrDefault(NotificationPanelConfig config)
	{
		FontStyle stored = config.fontType();
		return stored == null ? FontStyle.BOLD : stored;
	}

	static TimeUnit timeUnitOrDefault(NotificationPanelConfig config)
	{
		TimeUnit stored = config.timeUnit();
		return stored == null ? TimeUnit.SECONDS : stored;
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
	 * RuneScape font style options.
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

	/**
	 * Default visibility when no rule matches.
	 */
	enum DefaultVisibility
	{
		SHOW(Visibility.SHOW),
		SIDEBAR(Visibility.SIDEBAR),
		HIDE(Visibility.HIDE);

		private final Visibility core;

		DefaultVisibility(Visibility core)
		{
			this.core = core;
		}

		Visibility core()
		{
			return core;
		}

		@Override
		public String toString()
		{
			return core.label();
		}
	}
}
