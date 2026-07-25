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
 * Carries the pre-2.1 boolean default over to the enum that replaced it.
 *
 * <p>The old setting is stored as {@code "true"}/{@code "false"}, which cannot parse as an enum, so
 * reusing the key would have silently turned every allowlist profile -- default off, one rule per
 * message worth seeing -- into one that shows everything. A new key avoids that, and this carries
 * the answer across.</p>
 *
 * <p>Absence of the new key is the trigger, not presence of the old one, which is the same rule the
 * rulesV1 migration follows. The value is written rather than read through on every load, so that
 * RuneLite's own config panel shows what the plugin actually does.</p>
 */
public final class DefaultVisibilityMigrator
{
	private static final String GROUP = "notificationpanel";
	private static final String KEY = "defaultVisibility";
	private static final String LEGACY_KEY = "visibility";

	private final ConfigManager configManager;

	@Inject
	DefaultVisibilityMigrator(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	public void adoptLegacyValue()
	{
		if (configManager.getConfiguration(GROUP, KEY) != null)
		{
			return;
		}
		String legacy = configManager.getConfiguration(GROUP, LEGACY_KEY);
		if (legacy == null)
		{
			// A profile that never set the old key has expressed no preference to carry over, and
			// the interface default already says the same thing this would write.
			return;
		}
		configManager.setConfiguration(GROUP, KEY, "false".equalsIgnoreCase(legacy.trim())
			? NotificationPanelConfig.DefaultVisibility.HIDE
			: NotificationPanelConfig.DefaultVisibility.SHOW);
	}
}
