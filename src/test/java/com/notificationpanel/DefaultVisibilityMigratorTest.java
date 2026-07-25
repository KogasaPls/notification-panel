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

import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DefaultVisibilityMigratorTest
{
	private static final String GROUP = "notificationpanel";

	@Test
	public void carriesAnAllowlistProfileOverToHide()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(GROUP, "defaultVisibility")).thenReturn(null);
		when(configManager.getConfiguration(GROUP, "visibility")).thenReturn("false");

		new DefaultVisibilityMigrator(configManager).adoptLegacyValue();

		verify(configManager).setConfiguration(GROUP, "defaultVisibility",
			NotificationPanelConfig.DefaultVisibility.HIDE);
	}

	@Test
	public void carriesAnOrdinaryProfileOverToShow()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(GROUP, "defaultVisibility")).thenReturn(null);
		when(configManager.getConfiguration(GROUP, "visibility")).thenReturn("true");

		new DefaultVisibilityMigrator(configManager).adoptLegacyValue();

		verify(configManager).setConfiguration(GROUP, "defaultVisibility",
			NotificationPanelConfig.DefaultVisibility.SHOW);
	}

	@Test
	public void writesNothingWhenTheNewKeyIsAlreadySet()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(GROUP, "defaultVisibility")).thenReturn("SIDEBAR");
		when(configManager.getConfiguration(GROUP, "visibility")).thenReturn("false");

		new DefaultVisibilityMigrator(configManager).adoptLegacyValue();

		// Matched against the enum overload specifically. ConfigManager declares both
		// setConfiguration(String, String, String) and a generic setConfiguration(String, String, T),
		// and a bare any() resolves to the more specific String one -- which the migrator never
		// calls, so the assertion would pass no matter what it did.
		verify(configManager, never()).setConfiguration(anyString(), anyString(),
			any(NotificationPanelConfig.DefaultVisibility.class));
	}

	@Test
	public void writesNothingWhenThereIsNothingToCarryOver()
	{
		// A fresh install has neither key. Writing SHOW here would settle a profile that has never
		// expressed a preference, for no gain: the interface default already says SHOW.
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(GROUP, "defaultVisibility")).thenReturn(null);
		when(configManager.getConfiguration(GROUP, "visibility")).thenReturn(null);

		new DefaultVisibilityMigrator(configManager).adoptLegacyValue();

		// Matched against the enum overload specifically. ConfigManager declares both
		// setConfiguration(String, String, String) and a generic setConfiguration(String, String, T),
		// and a bare any() resolves to the more specific String one -- which the migrator never
		// calls, so the assertion would pass no matter what it did.
		verify(configManager, never()).setConfiguration(anyString(), anyString(),
			any(NotificationPanelConfig.DefaultVisibility.class));
	}
}
