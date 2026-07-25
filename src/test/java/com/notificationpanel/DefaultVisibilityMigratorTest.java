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
	private static final String MARK_KEY = "defaultVisibilityAdopted";

	@Test
	public void carriesAnAllowlistProfileOverToHide()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(GROUP, MARK_KEY)).thenReturn(null);
		when(configManager.getConfiguration(GROUP, "visibility")).thenReturn("false");

		new DefaultVisibilityMigrator(configManager).adoptLegacyValue();

		verify(configManager).setConfiguration(GROUP, "defaultVisibility",
			NotificationPanelConfig.DefaultVisibility.HIDE);
		verify(configManager).setConfiguration(GROUP, MARK_KEY, "1");
	}

	@Test
	public void carriesAnOrdinaryProfileOverToShow()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(GROUP, MARK_KEY)).thenReturn(null);
		when(configManager.getConfiguration(GROUP, "visibility")).thenReturn("true");

		new DefaultVisibilityMigrator(configManager).adoptLegacyValue();

		verify(configManager).setConfiguration(GROUP, "defaultVisibility",
			NotificationPanelConfig.DefaultVisibility.SHOW);
		verify(configManager).setConfiguration(GROUP, MARK_KEY, "1");
	}

	@Test
	public void carriesAnAllowlistProfileOverAfterRuneLiteHasWrittenTheInterfaceDefault()
	{
		// What a real upgrade looks like. RuneLite calls setDefaultConfiguration before it starts
		// any plugin, and that writes defaultVisibility=SHOW because the key is unset and SHOW is
		// a non-empty default. A migrator that took an unset defaultVisibility as its trigger would
		// therefore never fire on any client, and this allowlist profile -- nothing shown unless a
		// rule says so -- would silently become one that shows everything.
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(GROUP, MARK_KEY)).thenReturn(null);
		when(configManager.getConfiguration(GROUP, "defaultVisibility")).thenReturn("SHOW");
		when(configManager.getConfiguration(GROUP, "visibility")).thenReturn("false");

		new DefaultVisibilityMigrator(configManager).adoptLegacyValue();

		verify(configManager).setConfiguration(GROUP, "defaultVisibility",
			NotificationPanelConfig.DefaultVisibility.HIDE);
	}

	@Test
	public void writesNothingOnceTheAdoptionIsMarked()
	{
		// The pass that follows the migrating write: it reloads the policy, so it runs the migrator
		// again, and has to stop. The old value is still there, since migration never destroys it.
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(GROUP, MARK_KEY)).thenReturn("1");
		when(configManager.getConfiguration(GROUP, "visibility")).thenReturn("false");

		new DefaultVisibilityMigrator(configManager).adoptLegacyValue();

		// Matched against the enum overload specifically. ConfigManager declares both
		// setConfiguration(String, String, String) and a generic setConfiguration(String, String, T),
		// and a bare any() resolves to the more specific String one, so an unqualified never()
		// would say nothing about the enum write this is really about.
		verify(configManager, never()).setConfiguration(anyString(), anyString(),
			any(NotificationPanelConfig.DefaultVisibility.class));
		verify(configManager, never()).setConfiguration(anyString(), anyString(), anyString());
	}

	@Test
	public void carriesNothingOverWhenThereIsNoOldPreference()
	{
		// A fresh install has no old key. Writing SHOW here would settle a profile that has never
		// expressed a preference, for no gain: the interface default already says SHOW. The mark is
		// still written, so this profile is not reconsidered on every load forever.
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(GROUP, MARK_KEY)).thenReturn(null);
		when(configManager.getConfiguration(GROUP, "visibility")).thenReturn(null);

		new DefaultVisibilityMigrator(configManager).adoptLegacyValue();

		// Matched against the enum overload specifically. ConfigManager declares both
		// setConfiguration(String, String, String) and a generic setConfiguration(String, String, T),
		// and a bare any() resolves to the more specific String one, so an unqualified never()
		// would say nothing about the enum write this is really about.
		verify(configManager, never()).setConfiguration(anyString(), anyString(),
			any(NotificationPanelConfig.DefaultVisibility.class));
		verify(configManager).setConfiguration(GROUP, MARK_KEY, "1");
	}
}
