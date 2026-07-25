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
 * <p>Every setting is edited here in RuneLite's config panel; the sidebar holds only the rules. The
 * background and opacity are the two a rule can override, so they read as defaults rather than as
 * absolutes. Keys are unchanged throughout, so existing configurations carry over untouched.</p>
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

	// The older form of the setting above, stored as "true"/"false" and so unreadable as an enum.
	// Kept, hidden and never destroyed, exactly like regexList and colorList: it is what
	// DefaultVisibilityMigrator carries over, once, on the first load that finds no adoption mark.
	@ConfigItem(position = 14,
		keyName = "visibility",
		name = "",
		description = "",
		hidden = true)
	default boolean showUnmatchedByDefault()
	{
		return true;
	}

	// Records that DefaultVisibilityMigrator has run, so it runs exactly once per profile.
	//
	// This default must stay empty. Before any plugin starts, and again on every profile change,
	// RuneLite calls ConfigManager.setDefaultConfiguration, which writes an item's interface
	// default into the profile whenever the key is unset -- but skips the key when stored and
	// default are both empty. So an empty default is the only kind RuneLite cannot pre-set behind
	// the plugin's back, which is what makes "unset" mean "the migration has not run" rather than
	// "the client has not written the default yet". defaultVisibility itself cannot serve as the
	// mark for exactly that reason: its default, SHOW, is non-empty and is already in the profile
	// by the time the plugin looks. rulesV1 is only safe as its own mark by the same property.
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

	// Position 13 rather than beside the other visible items because the highest position among
	// the visible items is 9 (defaultVisibility), so this still lands last in the panel without
	// renumbering them.
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
	 * The stored default background, or the built-in one when RuneLite could not read what was
	 * stored.
	 *
	 * <p>Colour is the one setting whose deserialiser answers an unparseable value with null
	 * instead of throwing, and the config proxy only falls back to the interface default when a
	 * deserialiser throws. So a profile edited by hand or written by another tool can make
	 * {@link #bgColor()} return null, and dereferencing that would take down policy loading, now
	 * the only thing that reads it. Read the key through here.</p>
	 */
	static Color backgroundOrDefault(NotificationPanelConfig config)
	{
		Color stored = config.bgColor();
		return stored == null ? new Color(DEFAULT_BACKGROUND_RGB) : stored;
	}

	/**
	 * The stored default visibility, or {@link DefaultVisibility#SHOW} when nothing readable is
	 * stored.
	 *
	 * <p>RuneLite answers an unparseable value with this interface's default, but a profile edited
	 * by hand or written by another tool can still yield null, and dereferencing that would take
	 * down policy loading. Read the key through here, as {@link #backgroundOrDefault} is read.</p>
	 */
	static DefaultVisibility defaultVisibilityOrShow(NotificationPanelConfig config)
	{
		DefaultVisibility stored = config.defaultVisibility();
		return stored == null ? DefaultVisibility.SHOW : stored;
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
	 * unchanged. The names match the ones {@code FontTypeSerializer} still reads for those presets,
	 * so a setting stored back when {@code FontType} was an enum carries over. It does not write
	 * them any more -- it serialises every {@code FontType}, presets included, as a JSON font
	 * descriptor -- so a font chosen on a recent client no longer parses: RuneLite logs a warning
	 * and falls back to this interface's default of {@link FontStyle#BOLD}. That is one visible
	 * reset for a setting that had grown into a picker over every font on the system, and
	 * {@code README.md} says so under upgrading.</p>
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
	 * Where a notification goes when no enabled rule decides.
	 *
	 * <p>Declared here rather than reusing the core {@code Visibility} because this is a stored
	 * setting with its own labels, and {@code NotificationPolicyFactory} is the seam that maps
	 * configuration to core values -- the same arrangement as {@link TimeUnit}.</p>
	 */
	enum DefaultVisibility
	{
		SHOW("Show"),
		SIDEBAR("Sidebar"),
		HIDE("Hide");

		private final String label;

		DefaultVisibility(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}
}
