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
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(GROUP, MARK_KEY)).thenReturn("1");
		when(configManager.getConfiguration(GROUP, "visibility")).thenReturn("false");

		new DefaultVisibilityMigrator(configManager).adoptLegacyValue();

		verify(configManager, never()).setConfiguration(anyString(), anyString(),
			any(NotificationPanelConfig.DefaultVisibility.class));
		verify(configManager, never()).setConfiguration(anyString(), anyString(), anyString());
	}

	@Test
	public void carriesNothingOverWhenThereIsNoOldPreference()
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(GROUP, MARK_KEY)).thenReturn(null);
		when(configManager.getConfiguration(GROUP, "visibility")).thenReturn(null);

		new DefaultVisibilityMigrator(configManager).adoptLegacyValue();

		verify(configManager, never()).setConfiguration(anyString(), anyString(),
			any(NotificationPanelConfig.DefaultVisibility.class));
		verify(configManager).setConfiguration(GROUP, MARK_KEY, "1");
	}
}
