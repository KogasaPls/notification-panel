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
package com.notificationpanel.ui;

import com.google.gson.Gson;
import com.google.inject.Guice;
import com.notificationpanel.rules.NotificationRule;
import com.notificationpanel.rules.RuleCodec;
import com.notificationpanel.rules.RuleConfigStore;
import com.notificationpanel.rules.RuleDocument;
import com.notificationpanel.state.NotificationState;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NotificationSidebarPanelTest
{
	private static final String EDT_ERROR = "Sidebar mutations must run on the EDT.";
	private static final Instant NOON = Instant.parse("2026-07-25T12:00:00Z");

	@Test
	public void opensOnNotificationsAndSwitchesToRules() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			NotificationSidebarPanel sidebar = sidebar(document(), new NotificationLog());

			assertTrue(sidebar.isShowingLogForTest());
			sidebar.selectRulesTabForTest();
			assertFalse(sidebar.isShowingLogForTest());
			// Positively, not just "the log is gone": clearing the display without putting the rule
			// editor in its place would satisfy the negative on its own.
			assertTrue(sidebar.isShowingRulesForTest());
		});
	}

	@Test
	public void opensOnRulesWhileAnImportStillHasToBeAcknowledged() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			// The migration gate is the only thing that says why a batch of rules arrived switched
			// off, so it keeps first sight of the sidebar.
			NotificationSidebarPanel sidebar = migratedSidebar(new NotificationLog());

			assertFalse(sidebar.isShowingLogForTest());
			assertTrue(sidebar.isShowingRulesForTest());
			assertTrue(sidebar.ruleEditorForTest().isMigrationGateVisibleForTest());
		});
	}

	@Test
	public void navigationIconIsGeneratedInMemoryAtSixteenPixels() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			BufferedImage image = sidebar(document(), new NotificationLog()).getNavigationIcon();
			assertNotNull(image);
			assertEquals(16, image.getWidth());
			assertEquals(16, image.getHeight());
			assertNotEquals(0, image.getRGB(4, 4));
			assertNotEquals(0, image.getRGB(4, 8));
			assertNotEquals(0, image.getRGB(4, 12));
		});
	}

	@Test
	public void aLoggedNotificationReachesTheLogTab() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			NotificationLog log = new NotificationLog();
			NotificationSidebarPanel sidebar = sidebar(document(), log);

			NotificationState.Accepted entry = new NotificationState.Accepted("You catch a shark.",
				0x181818, NOON);
			log.add(entry);
			sidebar.notificationLogged(entry);

			assertEquals(1, sidebar.logPanelForTest().getRowCountForTest());
		});
	}

	@Test
	public void reportsNoHeightSoAFullLogCannotStretchTheClientWindow() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			NotificationLog log = new NotificationLog();
			NotificationSidebarPanel sidebar = sidebar(document(), log);

			// An unwrapped PluginPanel is the component RuneLite puts in the sidebar itself, so any
			// height it reports is a height the client's window has to find room for. A full log
			// used to report some seven thousand pixels and the window grew to match it.
			for (int index = 0; index < NotificationLog.CAPACITY; index++)
			{
				NotificationState.Accepted entry = new NotificationState.Accepted(
					"You catch a shark number " + index + ".", 0x181818, NOON);
				log.add(entry);
				sidebar.notificationLogged(entry);
			}

			assertEquals(0, sidebar.getWrappedPanel().getPreferredSize().height);
			assertEquals(0, sidebar.getWrappedPanel().getMinimumSize().height);

			// The rule editor reports a large minimum of its own -- wrapped text areas do, at their
			// minimum width -- so check the tab that is not the log as well.
			sidebar.selectRulesTabForTest();
			assertEquals(0, sidebar.getWrappedPanel().getPreferredSize().height);
			assertEquals(0, sidebar.getWrappedPanel().getMinimumSize().height);

			// The width is PluginPanel's own and has to survive: it is what the sidebar is sized to.
			assertEquals(PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH,
				sidebar.getWrappedPanel().getPreferredSize().width);
		});
	}

	@Test
	public void anUnacknowledgedGateDoesNotDragTheUserBackOnEveryConfigChange() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			NotificationSidebarPanel sidebar = migratedSidebar(new NotificationLog());
			assertTrue(sidebar.isShowingRulesForTest());

			// The user reads the gate, decides to look at the log first, and then nudges a setting
			// in RuneLite's own config panel -- which reloads this panel. Being thrown back to Rules
			// on every such change, until the gate is acknowledged, is what this guards against.
			sidebar.selectNotificationsTabForTest();
			sidebar.reload();
			assertTrue(sidebar.isShowingLogForTest());
			sidebar.reload();
			assertTrue(sidebar.isShowingLogForTest());

			// The gate is still up and unacknowledged; only where it is shown has been left alone.
			assertTrue(sidebar.hasPendingMigration());
		});
	}

	@Test
	public void aGateRaisedWhileTheLogIsOpenStillTakesTheUserToIt() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			NotificationSidebarPanel sidebar = sidebar(document(), new NotificationLog());
			assertTrue(sidebar.isShowingLogForTest());
			assertFalse(sidebar.hasPendingMigration());

			// Config synced on login, or a profile switch, hands legacy lists to an install that had
			// none. The gate going up is the one thing worth interrupting the log for.
			sidebar.reload(true);

			assertTrue(sidebar.isShowingRulesForTest());
			assertTrue(sidebar.hasPendingMigration());
		});
	}

	@Test
	public void openingAMatchedRuleSwitchesToRulesAndShowsThatRule() throws Exception
	{
		NotificationRule stored = new NotificationRule(UUID.randomUUID(), "Sharks", true,
			"*shark*", 0xBF616A, null, null, null);

		SwingUtilities.invokeAndWait(() ->
		{
			NotificationSidebarPanel sidebar = sidebar(document(stored), new NotificationLog());

			// The rules that already match are what the log's menu offers to open, so that a user
			// warned "this one shadows you" can go straight to it.
			NotificationLogPanel.RuleActions actions = sidebar.ruleActionsForTest();
			assertEquals(List.of("Sharks"), actions.matchingRules("You catch a shark.").stream()
				.map(NotificationRule::getName).collect(Collectors.toList()));
			assertEquals(List.of(), actions.matchingRules("Nothing like it."));

			actions.openRule(stored.getId());

			assertTrue(sidebar.isShowingRulesForTest());
			assertEquals("*shark*", sidebar.ruleEditorForTest().getDraftPatternForTest());
		});
	}

	@Test
	public void openingARuleThatHasSinceBeenDeletedStillLandsOnTheRulesTab() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			NotificationSidebarPanel sidebar = sidebar(document(), new NotificationLog());

			// A menu can be built from rules that are gone by the time it is picked; the tab switch
			// is still what was asked for, and a stale id is not worth an error to dismiss.
			sidebar.ruleActionsForTest().openRule(UUID.randomUUID());

			assertTrue(sidebar.isShowingRulesForTest());
			assertTrue(sidebar.ruleEditorForTest().isShowingListForTest());
		});
	}

	@Test
	public void creatingARuleFromAMessageSwitchesToRulesAndPrefillsTheDraft() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			NotificationSidebarPanel sidebar = sidebar(document(), new NotificationLog());

			sidebar.ruleActionsForTest().createRule("You catch a shark.");

			assertTrue(sidebar.isShowingRulesForTest());
			assertEquals("You catch a shark.",
				sidebar.ruleEditorForTest().getDraftPatternForTest());
		});
	}

	@Test
	public void hostConstructionAndTestAccessRequireEdt() throws Exception
	{
		// The host is built, reloaded and told about notifications from the plugin's EDT tasks, and
		// everything under it -- the controller, the rule editor, the log -- is EDT-confined too.
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn(new RuleCodec(new Gson()).encode(document()));
		RuleConfigStore store = store(configManager);
		NotificationLog log = new NotificationLog();
		AtomicReference<RuleEditorController> controller = new AtomicReference<>();
		AtomicReference<NotificationSidebarPanel> reference = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() ->
		{
			controller.set(new RuleEditorController(store));
			reference.set(new NotificationSidebarPanel(controller.get(), log, () ->
			{
			}));
		});
		NotificationSidebarPanel sidebar = reference.get();

		assertEdtFailure(sidebar::getNavigationIcon);
		assertEdtFailure(sidebar::reload);
		assertEdtFailure(() -> sidebar.reload(true));
		assertEdtFailure(sidebar::hasPendingMigration);
		assertEdtFailure(() -> sidebar.notificationLogged(
			new NotificationState.Accepted("You catch a shark.", 0x181818, NOON)));
		assertEdtFailure(sidebar::ruleEditorForTest);
		assertEdtFailure(sidebar::logPanelForTest);
		assertEdtFailure(sidebar::isShowingLogForTest);
		assertEdtFailure(sidebar::isShowingRulesForTest);
		assertEdtFailure(sidebar::selectRulesTabForTest);
		assertEdtFailure(sidebar::selectNotificationsTabForTest);
		assertEdtFailure(() -> sidebar.ruleActionsForTest().canCreateRule());
		assertEdtFailure(() -> sidebar.ruleActionsForTest().createRule("You catch a shark."));
		IllegalStateException constructorError = assertThrows(IllegalStateException.class,
			() -> new NotificationSidebarPanel(controller.get(), log, () ->
			{
			}));
		assertEquals(EDT_ERROR, constructorError.getMessage());
	}

	private static void assertEdtFailure(Runnable operation)
	{
		IllegalStateException exception = assertThrows(IllegalStateException.class, operation::run);
		assertEquals(EDT_ERROR, exception.getMessage());
	}

	private static NotificationSidebarPanel sidebar(RuleDocument document, NotificationLog log)
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn(new RuleCodec(new Gson()).encode(document));
		return sidebar(configManager, log);
	}

	/** A profile with legacy lists and no rulesV1, so loading it migrates and raises the gate. */
	private static NotificationSidebarPanel migratedSidebar(NotificationLog log)
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn(null);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "regexList"))
			.thenReturn("Zulrah|Vorkath\n.*loot.*");
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "colorList"))
			.thenReturn("#ff0000\n#00ff00");
		return sidebar(configManager, log);
	}

	private static NotificationSidebarPanel sidebar(ConfigManager configManager,
		NotificationLog log)
	{
		return new NotificationSidebarPanel(new RuleEditorController(store(configManager)), log,
			() ->
			{
			});
	}

	private static RuleConfigStore store(ConfigManager configManager)
	{
		return Guice.createInjector(binder ->
		{
			binder.bind(ConfigManager.class).toInstance(configManager);
			binder.bind(Gson.class).toInstance(new Gson());
		}).getInstance(RuleConfigStore.class);
	}

	private static RuleDocument document(NotificationRule... rules)
	{
		return new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION, Collections.emptyList(),
			Arrays.asList(rules));
	}
}
