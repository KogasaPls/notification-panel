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

import com.notificationpanel.rules.NotificationRule;
import com.notificationpanel.rules.RuleConfigStore;
import com.notificationpanel.rules.RuleSet;
import com.notificationpanel.rules.RuleDocument;
import com.notificationpanel.rules.Visibility;
import com.notificationpanel.state.NotificationState;
import com.notificationpanel.ui.NotificationSidebarPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.TrayIcon;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
	private DefaultVisibilityMigrator defaultVisibilityMigrator;
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
		lenient().when(config.showSidebarButton()).thenReturn(true);
	}

	@Test
	public void delayedNotificationAfterShutdownIsDiscarded() throws Exception
	{
		plugin.startUp();
		plugin.onNotificationFired(new NotificationFired(
			null, "late", TrayIcon.MessageType.NONE));
		ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
		verify(clientThread, atLeastOnce()).invokeLater(tasks.capture());
		Runnable delivery = tasks.getAllValues().get(tasks.getAllValues().size() - 1);

		plugin.shutDown();
		delivery.run();

		verify(state, never()).accept("late");
		flushEdt();
	}

	@Test
	public void notificationDeliveredWhileRunningReachesState() throws Exception
	{
		plugin.startUp();
		plugin.onNotificationFired(new NotificationFired(
			null, "drop", TrayIcon.MessageType.NONE));

		runClientTasks();

		verify(state).accept("drop");
		flushEdt();
	}

	@Test
	public void anAcceptedNotificationReachesTheLogOnTheEventDispatchThread() throws Exception
	{
		NotificationState.Accepted accepted = new NotificationState.Accepted("shark", 0xBF616A,
			Instant.parse("2026-07-25T12:00:00Z"));
		when(state.accept("shark")).thenReturn(accepted);

		plugin.startUp();
		plugin.onNotificationFired(new NotificationFired(null, "shark",
			TrayIcon.MessageType.NONE));
		runClientTasks();
		flushEdt();

		SwingUtilities.invokeAndWait(() -> assertEquals(Collections.singletonList(accepted),
			plugin.notificationLogForTest().getEntries()));
	}

	@Test
	public void aHiddenNotificationIsNotRecorded() throws Exception
	{
		// accept answers null when the rules resolved to HIDE, and nothing may reach the log.
		when(state.accept("spam")).thenReturn(null);

		plugin.startUp();
		plugin.onNotificationFired(new NotificationFired(null, "spam",
			TrayIcon.MessageType.NONE));
		runClientTasks();
		flushEdt();

		SwingUtilities.invokeAndWait(() -> assertTrue(plugin.notificationLogForTest().isEmpty()));
	}

	@Test
	public void shutdownEmptiesTheLog() throws Exception
	{
		NotificationState.Accepted accepted = new NotificationState.Accepted("shark", 0xBF616A,
			Instant.parse("2026-07-25T12:00:00Z"));
		when(state.accept("shark")).thenReturn(accepted);

		plugin.startUp();
		plugin.onNotificationFired(new NotificationFired(null, "shark",
			TrayIcon.MessageType.NONE));
		runClientTasks();
		flushEdt();

		plugin.shutDown();
		runClientTasks();
		flushEdt();

		SwingUtilities.invokeAndWait(() -> assertTrue(plugin.notificationLogForTest().isEmpty()));
	}

	@Test
	public void startupAddsOverlayAndShutdownRemovesAndClears() throws Exception
	{
		plugin.startUp();
		verify(overlayManager).add(overlay);
		// The lifecycle runs on the EDT, so the state must only be touched via the client thread.
		verify(state, never()).clear();

		plugin.shutDown();
		verify(overlayManager).remove(overlay);
		// Still nothing: shutDown runs on the EDT and the overlay may be mid-render on the
		// client thread, which iterates the same collection.
		verify(state, never()).clear();
		runClientTasks();
		verify(state).clear();
		flushEdt();
	}

	@Test
	public void startupCompilesRulesAndUpdatesPolicy() throws Exception
	{
		NotificationState.Policy policy = new NotificationState.Policy(1,
			new NotificationState.Style(0x181818, 75, Visibility.SHOW,
				NotificationPanelConfig.FontStyle.BOLD.getFont()),
			new NotificationState.Lifetime(NotificationState.Unit.SECONDS, 3), true,
			RuleSet.empty());
		when(policyFactory.create(any(), any())).thenReturn(policy);

		plugin.startUp();
		// Nothing may reach the state until the client-thread task runs.
		verify(state, never()).updatePolicy(any());
		runClientTasks();

		verify(ruleConfigStore, atLeastOnce()).load();
		// Carrying the old boolean default across is a load reaching configuration, not a policy
		// value, so nothing else here would notice its absence: without this, deleting the call
		// leaves the suite green while every upgrading allowlist profile silently starts showing
		// everything.
		verify(defaultVisibilityMigrator, atLeastOnce()).adoptLegacyValue();
		// The compiled rules must actually be the ones handed to the policy, not an empty set.
		ArgumentCaptor<RuleSet> rules = ArgumentCaptor.forClass(RuleSet.class);
		verify(policyFactory).create(eq(config), rules.capture());
		assertNotNull(rules.getValue());
		verify(state).updatePolicy(policy);
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
			assertTrue(plugin.sidebarPanelForTest().ruleEditorForTest()
				.isMigrationGateVisibleForTest()));
	}

	@Test
	public void migrationAnnouncedWhileTheSidebarIsDownSurvivesToTheNextStart() throws Exception
	{
		// rulesV1 is written before the announcement is queued, so no later load reports the
		// migration again. Dropping the announcement because the plugin stopped first would leave
		// the user with a batch of switched-off rules and nothing saying why.
		RuleConfigStore.LoadResult migrated = mock(RuleConfigStore.LoadResult.class);
		when(migrated.getDocument()).thenReturn(emptyDocument());
		when(migrated.hasBlockingError()).thenReturn(false);
		when(migrated.wasMigrated()).thenReturn(true);
		when(ruleConfigStore.load()).thenReturn(migrated);

		// Hold the EDT until the whole start/announce/stop sequence has been queued. Without this
		// the EDT is free to run createSidebar while the test thread is still working, and the
		// announcement then finds a live panel instead of the torn-down one this test is about, and
		// the test fails intermittently.
		CountDownLatch release = new CountDownLatch(1);
		SwingUtilities.invokeLater(() ->
		{
			try
			{
				// Bounded, and released in a finally below, so a throw in the sequence this holds
				// back fails the test instead of parking the EDT forever -- an unbounded wait would
				// hang every later flushEdt() in the class rather than reporting anything.
				release.await(5, TimeUnit.SECONDS);
			}
			catch (InterruptedException interrupted)
			{
				Thread.currentThread().interrupt();
			}
		});

		try
		{
			plugin.startUp();
			// The reload runs before the EDT has built the sidebar, so the announcement is queued
			// with nothing to talk to; stopping now runs it after the plugin is already down.
			runClientTasks();
			plugin.shutDown();
		}
		finally
		{
			release.countDown();
		}
		flushEdt();

		when(migrated.wasMigrated()).thenReturn(false);
		plugin.startUp();
		flushEdt();

		SwingUtilities.invokeAndWait(() ->
			assertTrue(plugin.sidebarPanelForTest().ruleEditorForTest()
				.isMigrationGateVisibleForTest()));
	}

	@Test
	public void navigationButtonIsRemovedOnShutdownAndNotLeakedAcrossRestarts() throws Exception
	{
		plugin.startUp();
		flushEdt();
		ArgumentCaptor<NavigationButton> added = ArgumentCaptor.forClass(NavigationButton.class);
		verify(clientToolbar).addNavigation(added.capture());

		plugin.shutDown();
		flushEdt();
		verify(clientToolbar).removeNavigation(added.getValue());

		// Disabling and re-enabling reuses this plugin instance and builds a fresh button, so a
		// button left behind here shows up as a duplicate icon in the client.
		plugin.startUp();
		flushEdt();
		plugin.shutDown();
		flushEdt();
		verify(clientToolbar, times(2)).addNavigation(any(NavigationButton.class));
		verify(clientToolbar, times(2)).removeNavigation(any(NavigationButton.class));
	}

	@Test
	public void hiddenSidebarButtonIsNeverAddedToTheToolbar() throws Exception
	{
		when(config.showSidebarButton()).thenReturn(false);

		plugin.startUp();
		flushEdt();

		verify(clientToolbar, never()).addNavigation(any(NavigationButton.class));
		plugin.shutDown();
		flushEdt();
	}

	@Test
	public void rulesStillMatchAndFormatWhileTheButtonIsHidden() throws Exception
	{
		// Hiding the sidebar hides the editor, not the rules it edits. This is the whole premise of
		// the feature -- a user who never opens the editor must lose nothing by removing it -- so
		// it asserts on the compiled RuleSet actually handed to the policy rather than settling for
		// "updatePolicy was called", which would still pass with the rules silently dropped.
		RuleConfigStore.LoadResult loaded = mock(RuleConfigStore.LoadResult.class);
		when(loaded.getDocument()).thenReturn(new RuleDocument(
			RuleDocument.CURRENT_SCHEMA_VERSION, Collections.emptyList(),
			Collections.singletonList(new NotificationRule(new UUID(0L, 1L), "Rare drops", true,
				"*dragon warhammer*", 0xBF616A, 90, null, null))));
		when(loaded.hasBlockingError()).thenReturn(false);
		when(ruleConfigStore.load()).thenReturn(loaded);
		when(config.showSidebarButton()).thenReturn(false);

		plugin.startUp();
		runClientTasks();

		ArgumentCaptor<RuleSet> rules = ArgumentCaptor.forClass(RuleSet.class);
		verify(policyFactory).create(eq(config), rules.capture());
		RuleSet.Resolution resolution = rules.getValue().resolve("You received a dragon warhammer!");
		assertTrue(resolution.isMatched());
		assertEquals(Integer.valueOf(0xBF616A), resolution.getBackgroundRgb());
		assertEquals(Integer.valueOf(90), resolution.getOpacityPercent());
		verify(clientToolbar, never()).addNavigation(any(NavigationButton.class));
		flushEdt();
	}

	@Test
	public void hidingTheButtonKeepsAnUnacknowledgedImportAlive() throws Exception
	{
		// The gate is written off as shown the moment a panel exists to show it, and rulesV1 is
		// already stored, so nothing reports the migration a second time. Hiding the button before
		// the user clicks through the gate must therefore hand it back, not drop it with the panel.
		plugin.startUp();
		flushEdt();
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
			assertTrue(plugin.sidebarPanelForTest().ruleEditorForTest()
				.isMigrationGateVisibleForTest()));

		// Hidden without the gate ever being acknowledged, and no later load reports it again.
		when(migrated.wasMigrated()).thenReturn(false);
		when(config.showSidebarButton()).thenReturn(false);
		plugin.onConfigChanged(configChanged(GROUP));
		flushEdt();
		assertNull(plugin.sidebarPanelForTest());

		when(config.showSidebarButton()).thenReturn(true);
		plugin.onConfigChanged(configChanged(GROUP));
		flushEdt();

		SwingUtilities.invokeAndWait(() ->
			assertTrue(plugin.sidebarPanelForTest().ruleEditorForTest()
				.isMigrationGateVisibleForTest()));
	}

	@Test
	public void showingTheButtonBuildsTheSidebarWithoutReadingTheStoreTwice() throws Exception
	{
		// The panel reads the store on its way up, so the reload that follows every config change
		// would be a second read and a second render of a list built microseconds earlier. The
		// client-thread task is left unrun here, so every load counted below is the sidebar's.
		when(config.showSidebarButton()).thenReturn(false);
		plugin.startUp();
		flushEdt();
		clearInvocations(ruleConfigStore);

		when(config.showSidebarButton()).thenReturn(true);
		plugin.onConfigChanged(configChanged(GROUP));
		flushEdt();

		verify(ruleConfigStore, times(1)).load();
		plugin.shutDown();
		flushEdt();
	}

	@Test
	public void turningTheSettingOnAddsTheButtonAndOffRemovesIt() throws Exception
	{
		when(config.showSidebarButton()).thenReturn(false);
		plugin.startUp();
		flushEdt();

		when(config.showSidebarButton()).thenReturn(true);
		plugin.onConfigChanged(configChanged(GROUP));
		flushEdt();
		ArgumentCaptor<NavigationButton> added = ArgumentCaptor.forClass(NavigationButton.class);
		verify(clientToolbar).addNavigation(added.capture());

		when(config.showSidebarButton()).thenReturn(false);
		plugin.onConfigChanged(configChanged(GROUP));
		flushEdt();
		verify(clientToolbar).removeNavigation(added.getValue());

		plugin.shutDown();
		flushEdt();
	}

	@Test
	public void aConfigChangeThatLeavesTheButtonShownDoesNotRebuildIt() throws Exception
	{
		// Rebuilding on every config change would throw away an in-progress rule draft, and
		// ConfigChanged fires for every key in the group -- including the ones the sidebar writes.
		plugin.startUp();
		flushEdt();

		plugin.onConfigChanged(configChanged(GROUP));
		flushEdt();

		verify(clientToolbar, times(1)).addNavigation(any(NavigationButton.class));
		verify(clientToolbar, never()).removeNavigation(any(NavigationButton.class));
		plugin.shutDown();
		flushEdt();
	}

	@Test
	public void migrationAnnouncedWhileTheButtonIsHiddenSurvivesUntilItIsShown() throws Exception
	{
		// The gate is the only thing that tells a user why a batch of imported rules arrived
		// switched off, and rulesV1 is already written, so no later load reports the migration
		// again. A hidden button must not swallow it.
		when(config.showSidebarButton()).thenReturn(false);
		RuleConfigStore.LoadResult migrated = mock(RuleConfigStore.LoadResult.class);
		when(migrated.getDocument()).thenReturn(emptyDocument());
		when(migrated.hasBlockingError()).thenReturn(false);
		when(migrated.wasMigrated()).thenReturn(true);
		when(ruleConfigStore.load()).thenReturn(migrated);

		plugin.startUp();
		runClientTasks();
		flushEdt();

		when(migrated.wasMigrated()).thenReturn(false);
		when(config.showSidebarButton()).thenReturn(true);
		plugin.onConfigChanged(configChanged(GROUP));
		flushEdt();

		SwingUtilities.invokeAndWait(() ->
			assertTrue(plugin.sidebarPanelForTest().ruleEditorForTest()
				.isMigrationGateVisibleForTest()));

		plugin.shutDown();
		flushEdt();
	}

	@Test
	public void testNotificationFollowsItsConfigSetting() throws Exception
	{
		// The toggle is a config item so it sits beside the settings it previews, rather than in
		// the sidebar where those settings cannot be reached.
		lenient().when(config.showTestNotification()).thenReturn(true);

		plugin.startUp();
		runClientTasks();
		verify(state).setTestNotificationVisible(true);

		clearInvocations(state);
		lenient().when(config.showTestNotification()).thenReturn(false);
		plugin.onConfigChanged(configChanged(GROUP));
		runClientTasks();
		verify(state).setTestNotificationVisible(false);
		flushEdt();
	}

	@Test
	public void theSidebarClearActionHopsToTheClientThread() throws Exception
	{
		// The sidebar runs on the EDT; this is its only path to the client-thread-confined state.
		plugin.startUp();
		flushEdt();
		runClientTasks();
		NotificationSidebarPanel.Actions actions = plugin.sidebarActionsForTest();

		clearInvocations(state);
		actions.clearNotifications();
		verify(state, never()).clear();
		runClientTasks();
		verify(state).clear();
		flushEdt();
	}

	@Test
	public void overlayGetsAStartingWidthOnlyWhenNoneIsStored() throws Exception
	{
		// Adding an overlay loads its stored geometry and calls setPreferredSize unconditionally,
		// so a width set before the add is discarded and a fresh profile is left at PanelComponent's
		// own 129px default.
		NotificationPanelOverlay real = new NotificationPanelOverlay(plugin, state);
		assertNull(real.getPreferredSize());
		real.applyStartingSize();
		assertEquals(250, real.getPreferredSize().width);

		real.setPreferredSize(new Dimension(400, 0));
		real.applyStartingSize();
		assertEquals("a size the user chose must survive", 400, real.getPreferredSize().width);

		// The minimum is enforced by the drag handler alone, and the pre-2.0 panel could be
		// dragged narrower than this one allows, so a carried-over profile can be below it.
		real.setPreferredSize(new Dimension(24, 0));
		real.applyStartingSize();
		assertEquals(real.getMinimumSize(), real.getPreferredSize().width);
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

		plugin.onConfigChanged(configChanged(GROUP));
		runClientTasks();

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

	/**
	 * Runs everything the plugin has queued onto the client thread. Startup, config changes and
	 * shutdown all defer their state work, because RuneLite runs the lifecycle on the EDT.
	 */
	private void runClientTasks()
	{
		ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
		verify(clientThread, atLeastOnce()).invokeLater(tasks.capture());
		clearInvocations(clientThread);
		for (Runnable task : tasks.getAllValues())
		{
			task.run();
		}
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
