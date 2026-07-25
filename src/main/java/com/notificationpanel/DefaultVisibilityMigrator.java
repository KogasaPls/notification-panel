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

import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;

/**
 * Carries the boolean default this replaced over to the enum that replaced it.
 *
 * <p>The old setting is stored as {@code "true"}/{@code "false"}, which cannot parse as an enum, so
 * reusing the key would have silently turned every allowlist profile -- default off, one rule per
 * message worth seeing -- into one that shows everything. A new key avoids that, and this carries
 * the answer across.</p>
 *
 * <p>The trigger is a hidden mark of its own, not absence of the new key. RuneLite writes an item's
 * interface default into the profile before any plugin starts, so {@code defaultVisibility} already
 * holds {@code "SHOW"} by the time this runs and testing it would skip every profile there is. The
 * mark defaults to the empty string, the one default RuneLite's pass leaves alone. Presence of the
 * old key is not the trigger either: it survives migration, so it is set forever after.</p>
 *
 * <p>The value is written rather than read through on every load, so that RuneLite's own config
 * panel shows what the plugin actually does.</p>
 */
public final class DefaultVisibilityMigrator
{
	private static final String GROUP = "notificationpanel";
	private static final String KEY = "defaultVisibility";
	private static final String LEGACY_KEY = "visibility";
	private static final String MARK_KEY = "defaultVisibilityAdopted";

	/**
	 * What the mark is set to. Numbered so a later adoption can tell profiles this one has already
	 * touched from ones it has not; any non-empty value stops this one.
	 */
	private static final String MARK = "1";

	private final ConfigManager configManager;

	@Inject
	DefaultVisibilityMigrator(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	public void adoptLegacyValue()
	{
		String mark = configManager.getConfiguration(GROUP, MARK_KEY);
		if (mark != null && !mark.isEmpty())
		{
			return;
		}
		String legacy = configManager.getConfiguration(GROUP, LEGACY_KEY);
		if (legacy != null)
		{
			configManager.setConfiguration(GROUP, KEY, "false".equalsIgnoreCase(legacy.trim())
				? NotificationPanelConfig.DefaultVisibility.HIDE
				: NotificationPanelConfig.DefaultVisibility.SHOW);
		}
		// After the value, so that a client killed between the two writes retries the adoption
		// instead of losing it. A profile that never set the old key is marked too: it has no
		// preference to carry over, and the interface default already says what this would write.
		configManager.setConfiguration(GROUP, MARK_KEY, MARK);
	}
}
