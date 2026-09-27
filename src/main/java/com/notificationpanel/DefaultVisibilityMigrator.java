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
 * Migrates legacy boolean visibility configuration to the enum setting.
 */
public final class DefaultVisibilityMigrator
{
	private static final String GROUP = "notificationpanel";
	private static final String KEY = "defaultVisibility";
	private static final String LEGACY_KEY = "visibility";
	private static final String MARK_KEY = "defaultVisibilityAdopted";
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
		configManager.setConfiguration(GROUP, MARK_KEY, MARK);
	}
}
