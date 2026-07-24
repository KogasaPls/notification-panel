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

import com.notificationpanel.rules.RuleConfigStore;
import com.notificationpanel.rules.RuleDocument;
import com.notificationpanel.state.NotificationState;
import java.awt.Color;
import java.awt.TrayIcon;
import java.util.Collections;
import javax.swing.SwingUtilities;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NotificationPanelPluginAdapterTest
{
	private static final String GROUP = "notificationpanel";

	@Rule
	public final MockitoRule mockito = MockitoJUnit.rule();

	@Mock
	private NotificationPanelConfig config;
	@Mock
	private NotificationPanelOverlay overlay;
	@Mock
	private NotificationState state;
	@Mock
	private NotificationPolicyFactory policyFactory;
	@Mock
	private RuleConfigStore ruleConfigStore;
	@Mock
	private OverlayManager overlayManager;
	@Mock
	private ClientToolbar clientToolbar;
	@Mock
	private ClientThread clientThread;

	@InjectMocks
	private NotificationPanelPlugin plugin;

	@Before
	public void setUp()
	{
		RuleConfigStore.LoadResult loadResult = mock(RuleConfigStore.LoadResult.class);
		when(loadResult.getDocument()).thenReturn(emptyDocument());
		when(loadResult.hasBlockingError()).thenReturn(false);
		when(ruleConfigStore.load()).thenReturn(loadResult);
		// Startup builds a policy from the config, so it has to answer with real values.
		// Lenient because not every test reaches the code that reads them.
		lenient().when(config.bgColor()).thenReturn(new Color(0x181818));
		lenient().when(config.opacity()).thenReturn(75);
		lenient().when(config.showTime()).thenReturn(true);
		lenient().when(config.fontType())
			.thenReturn(NotificationPanelConfig.FontStyle.BOLD);
	}

	@Test
	public void delayedNotificationAfterShutdownIsDiscarded() throws Exception
	{
		plugin.startUp();
		plugin.onNotificationFired(new NotificationFired(
			null, "late", TrayIcon.MessageType.NONE));
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(clientThread).invokeLater(task.capture());

		plugin.shutDown();
		task.getValue().run();

		verify(state, never()).accept("late");
		flushEdt();
	}

	@Test
	public void notificationDeliveredWhileRunningReachesState() throws Exception
	{
		plugin.startUp();
		plugin.onNotificationFired(new NotificationFired(
			null, "drop", TrayIcon.MessageType.NONE));
		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(clientThread).invokeLater(task.capture());

		task.getValue().run();

		verify(state).accept("drop");
		flushEdt();
	}

	@Test
	public void startupAddsOverlayAndShutdownRemovesAndClears() throws Exception
	{
		plugin.startUp();
		verify(overlayManager).add(overlay);

		plugin.shutDown();
		verify(overlayManager).remove(overlay);
		verify(state).clear();
		flushEdt();
	}

	@Test
	public void startupCompilesRulesAndUpdatesPolicy() throws Exception
	{
		plugin.startUp();
		// updatePolicy is reached only through reloadPolicy, never the sidebar EDT task,
		// so its count is deterministic regardless of when createSidebar runs.
		verify(ruleConfigStore, atLeastOnce()).load();
		verify(state).updatePolicy(any());
		flushEdt();
	}

	@Test
	public void clearRequiresActionOverlayAndOption() throws Exception
	{
		plugin.startUp();
		Overlay otherOverlay = mock(Overlay.class);

		OverlayMenuClicked wrongOverlay = new OverlayMenuClicked(
			new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY,
				NotificationPanelOverlay.CLEAR_ALL, "Notification panel"),
			otherOverlay);
		plugin.onOverlayMenuClicked(wrongOverlay);
		verify(state, never()).clear();

		OverlayMenuClicked wrongOption = new OverlayMenuClicked(
			new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, "Other", "Notification panel"),
			overlay);
		plugin.onOverlayMenuClicked(wrongOption);
		verify(state, never()).clear();

		OverlayMenuClicked clear = new OverlayMenuClicked(
			new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY,
				NotificationPanelOverlay.CLEAR_ALL, "Notification panel"),
			overlay);
		plugin.onOverlayMenuClicked(clear);
		verify(state).clear();
		flushEdt();
	}

	@Test
	public void migrationSeenOnAConfigReloadReachesTheSidebar() throws Exception
	{
		// Regression, found in a live client: the sidebar was built against an empty profile, then
		// logging in switched RuneLite to another profile whose legacy lists triggered a
		// migration. The gate was dropped because only createSidebar ever consumed the flag.
		plugin.startUp();
		flushEdt();
		// Run the client-thread task inline so the flag is set before the EDT task reads it.
		doAnswer(invocation ->
		{
			invocation.getArgument(0, Runnable.class).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));
		RuleConfigStore.LoadResult migrated = mock(RuleConfigStore.LoadResult.class);
		when(migrated.getDocument()).thenReturn(emptyDocument());
		when(migrated.hasBlockingError()).thenReturn(false);
		when(migrated.wasMigrated()).thenReturn(true);
		when(ruleConfigStore.load()).thenReturn(migrated);

		plugin.onConfigChanged(configChanged(GROUP));
		flushEdt();

		SwingUtilities.invokeAndWait(() ->
			assertTrue(plugin.ruleEditorPanelForTest().isMigrationGateVisibleForTest()));
	}

	@Test
	public void testNotificationFollowsItsConfigSetting() throws Exception
	{
		// The toggle is a config item so it sits beside the settings it previews, rather than in
		// the sidebar where those settings cannot be reached.
		lenient().when(config.showTestNotification()).thenReturn(true);

		plugin.startUp();

		verify(state).setTestNotificationVisible(true);
		flushEdt();
	}

	@Test
	public void gameTickForwardsToState()
	{
		plugin.onGameTick(new GameTick());
		verify(state).onGameTick();
	}

	@Test
	public void ignoresConfigChangesFromOtherGroups() throws Exception
	{
		plugin.startUp();
		flushEdt();
		clearInvocations(clientThread, state);

		plugin.onConfigChanged(configChanged("someOtherGroup"));

		verify(clientThread, never()).invokeLater(any(Runnable.class));
		verify(state, never()).updatePolicy(any());
		flushEdt();
	}

	@Test
	public void configChangeInGroupReloadsPolicyOnClientThread() throws Exception
	{
		plugin.startUp();
		flushEdt();
		clearInvocations(clientThread, state);

		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		plugin.onConfigChanged(configChanged(GROUP));
		verify(clientThread).invokeLater(task.capture());
		task.getValue().run();

		// updatePolicy is reached only through the client-thread reload task, so it is a
		// deterministic signal that the config change scheduled a policy reload; the editor
		// reload on the EDT touches only the store, never the state.
		verify(state).updatePolicy(any());
		flushEdt();
	}

	private static ConfigChanged configChanged(String group)
	{
		ConfigChanged event = new ConfigChanged();
		event.setGroup(group);
		event.setKey("rulesV1");
		return event;
	}

	private static RuleDocument emptyDocument()
	{
		return new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION,
			Collections.emptyList(), Collections.emptyList());
	}

	private static void flushEdt() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
		});
	}

	private static <T> T mock(Class<T> type)
	{
		return org.mockito.Mockito.mock(type);
	}
}
