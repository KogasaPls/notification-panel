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
import com.notificationpanel.rules.Visibility;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RuleEditorPanelTest
{
	private static final String EDT_ERROR = "Rule editor mutations must run on the EDT.";

	@Test
	public void saveButtonTracksDraftValidityOnEdt() throws Exception
	{
		Fixture fixture = fixture(document());

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.showNewRule();
			panel.setDraftForTest("Rare drops", "", true, 0xBF616A, null, null);
			assertFalse(panel.isSaveEnabledForTest());
			assertTrue(panel.getValidationTextForTest().contains("Pattern must contain"));
			panel.setDraftForTest("Rare drops", "dragon warhammer", true, 0xBF616A, 90, null);
			assertTrue(panel.isSaveEnabledForTest());
			assertTrue(panel.getValidationTextForTest().isEmpty());
		});
	}

	@Test
	public void multipleFieldErrorsAreShownExactlyOnce() throws Exception
	{
		Fixture fixture = fixture(document());

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.showNewRule();
			panel.setDraftForTest("", "", true, null, null, null);
			assertEquals(
				"Name must contain 1 to 64 Unicode code points. "
					+ "Pattern must contain 1 to 512 Unicode code points.",
				panel.getValidationTextForTest());
		});
	}

	@Test
	public void addSaveReturnsToListAndPersistsOnce() throws Exception
	{
		Fixture fixture = fixture(document());

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.showNewRule();
			panel.setDraftForTest("Rare drops", "dragon warhammer", true, 0xBF616A, 90, null);
			panel.clickSaveForTest();
			assertTrue(panel.isShowingListForTest());
			assertEquals(1, fixture.controller.getRules().size());
			assertEquals("Rare drops", fixture.controller.getRules().get(0).getName());
		});

		verify(fixture.configManager, times(1)).setConfiguration(
			eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY), any());
	}

	@Test
	public void cancelRebuildsListWithoutControllerMutation() throws Exception
	{
		NotificationRule existing = rule(1, "Existing", "drop", null);
		Fixture fixture = fixture(document(existing));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.showNewRule();
			panel.setDraftForTest("Discard", "discard", true, 0x112233, 50, null);
			panel.clickCancelForTest();
			assertTrue(panel.isShowingListForTest());
			assertEquals(Collections.singletonList(existing), fixture.controller.getRules());
		});

		verify(fixture.configManager, never()).setConfiguration(
			eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY), any());
	}

	@Test
	public void editCancelRestoresEditedRuleSelection() throws Exception
	{
		NotificationRule first = rule(1, "First", "first", null);
		NotificationRule second = rule(2, "Second", "second", null);
		Fixture fixture = fixture(document(first, second));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.selectRuleForTest(second.getId());
			panel.showSelectedRuleForTest();
			panel.clickCancelForTest();
			assertTrue(panel.isShowingListForTest());
			assertEquals(second.getId(), panel.getSelectedRuleIdForTest());
		});

		verify(fixture.configManager, never()).setConfiguration(
			eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY), any());
	}

	@Test
	public void deleteAnswerUsesConfirmedIdentityEvenWhenSelectionDrifts() throws Exception
	{
		NotificationRule first = rule(1, "First", "first", null);
		NotificationRule second = rule(2, "Second", "second", null);
		Fixture fixture = fixture(document(first, second));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.selectRuleForTest(first.getId());
			panel.handleDeleteAnswerForTest(JOptionPane.CANCEL_OPTION, first.getId());
			assertEquals(Arrays.asList(first, second), fixture.controller.getRules());
			panel.selectRuleForTest(second.getId());
			panel.handleDeleteAnswerForTest(JOptionPane.OK_OPTION, first.getId());
			assertEquals(Collections.singletonList(second), fixture.controller.getRules());
			assertEquals(second.getId(), panel.getSelectedRuleIdForTest());
		});

		verify(fixture.configManager, times(1)).setConfiguration(
			eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY), any());
	}

	@Test
	public void editSaveRetainsUuidAndClearsWarning() throws Exception
	{
		NotificationRule migrated = rule(1, "Imported", "drop", "Review legacy rule.");
		Fixture fixture = fixture(document(migrated));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.selectRuleForTest(migrated.getId());
			panel.showSelectedRuleForTest();
			panel.setDraftForTest("Drops", "dragon", false, null, 80, null);
			panel.clickSaveForTest();
			NotificationRule saved = fixture.controller.getRules().get(0);
			assertEquals(migrated.getId(), saved.getId());
			assertEquals(null, saved.getMigrationNote());
			assertTrue(panel.isShowingListForTest());
		});
	}

	@Test
	public void listTextEscapesPatternsAndShowsStyleAndWarnings() throws Exception
	{
		NotificationRule migrated = new NotificationRule(id(1), "Imported", false,
			"line one\nline two", 0x112233, 75, null,
			"Legacy warning");
		Fixture fixture = fixture(document(migrated));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			String text = panel.getListTextForTest();
			assertTrue(text.contains("Imported"));
			assertTrue(text.contains("line one\\nline two"));
			assertTrue(text.contains("#112233"));
			assertTrue(text.contains("75%"));
			assertTrue(text.contains("Warning"));
			assertFalse(text.contains("line one\nline two"));
		});
	}

	@Test
	public void patternPreviewEscapesAllLineSeparatorsWithoutDanglingEscape() throws Exception
	{
		NotificationRule boundary = new NotificationRule(id(1), "Boundary", false,
			"a".repeat(47) + "\\tail", 0, null, null, null);
		NotificationRule separators = new NotificationRule(id(2), "Separators", false,
			"a\rb\nc\u000Bd\u000Ce\u0085f\u2028g\u2029h\\i", 0, null, null,
			null);
		Fixture fixture = fixture(document(boundary, separators));

		SwingUtilities.invokeAndWait(() ->
		{
			String text = fixture.panel().getListTextForTest();
			assertTrue(text.contains("a".repeat(47) + "…"));
			assertFalse(text.contains("a".repeat(47) + "\\…"));
			assertTrue(text.contains("a\\rb\\nc\\u000Bd\\fe\\u0085f\\u2028g\\u2029h\\\\i"));
			assertFalse(text.contains("\r"));
			assertFalse(text.contains("\u000B"));
			assertFalse(text.contains("\u000C"));
			assertFalse(text.contains("\u0085"));
			assertFalse(text.contains("\u2028"));
			assertFalse(text.contains("\u2029"));
		});
	}

	@Test
	public void selectionAndBoundaryButtonsTrackAvailableActions() throws Exception
	{
		NotificationRule first = rule(1, "First", "first", null);
		NotificationRule second = rule(2, "Second", "second", null);
		Fixture fixture = fixture(document(first, second));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			assertFalse(panel.isEditEnabledForTest());
			panel.selectRuleForTest(first.getId());
			assertTrue(panel.isEditEnabledForTest());
			assertFalse(panel.isUpEnabledForTest());
			assertTrue(panel.isDownEnabledForTest());
			panel.selectRuleForTest(second.getId());
			assertTrue(panel.isUpEnabledForTest());
			assertFalse(panel.isDownEnabledForTest());
		});
	}

	@Test
	public void toggleAndRepeatedMovesPreserveSelectedRule() throws Exception
	{
		NotificationRule first = rule(1, "First", "first", null);
		NotificationRule second = rule(2, "Second", "second", null);
		NotificationRule third = rule(3, "Third", "third", null);
		Fixture fixture = fixture(document(first, second, third));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.selectRuleForTest(first.getId());
			panel.clickToggleForTest();
			assertFalse(fixture.controller.find(first.getId()).isEnabled());
			assertEquals(first.getId(), panel.getSelectedRuleIdForTest());
			panel.clickDownForTest();
			assertEquals(Arrays.asList(second.getId(), first.getId(), third.getId()),
				ids(fixture.controller.getRules()));
			assertEquals(first.getId(), panel.getSelectedRuleIdForTest());
			panel.clickDownForTest();
			assertEquals(Arrays.asList(second.getId(), third.getId(), first.getId()),
				ids(fixture.controller.getRules()));
			assertEquals(first.getId(), panel.getSelectedRuleIdForTest());
			assertFalse(panel.isDownEnabledForTest());
			panel.clickUpForTest();
			assertEquals(Arrays.asList(second.getId(), first.getId(), third.getId()),
				ids(fixture.controller.getRules()));
			assertEquals(first.getId(), panel.getSelectedRuleIdForTest());
		});

		verify(fixture.configManager, times(4)).setConfiguration(
			eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY), any());
	}

	@Test
	public void corruptBannerDisablesEditsAndResetRestoresUsableEmptyList() throws Exception
	{
		ConfigManager configManager = mock(ConfigManager.class);
		String empty = new RuleCodec(new Gson()).encode(document());
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn("{broken")
			.thenReturn(empty);
		Fixture fixture = new Fixture(configManager, store(configManager));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			assertTrue(panel.isBlockingBannerVisibleForTest());
			assertFalse(panel.isAddEnabledForTest());
			assertTrue(panel.isResetVisibleForTest());
			assertTrue(panel.areListErrorsWrappingNonEditableForTest());
			panel.handleResetAnswerForTest(JOptionPane.OK_OPTION);
			assertFalse(panel.isBlockingBannerVisibleForTest());
			assertTrue(panel.isAddEnabledForTest());
			assertTrue(fixture.controller.getRules().isEmpty());
		});

		verify(configManager).setConfiguration(RuleConfigStore.GROUP,
			RuleConfigStore.RULES_KEY, empty);
		// Recovering from corrupt rule data must not take the legacy backup with it.
		verify(configManager, never()).unsetConfiguration(anyString(), anyString());
	}

	@Test
	public void configReloadKeepsAnOpenDraftInsteadOfDiscardingIt() throws Exception
	{
		Fixture fixture = fixture(document());

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.showNewRule();
			panel.setDraftForTest("Half typed", "dragon warhammer", true, 0xBF616A, 90, null);

			// Every change in the plugin's config group reaches reload(), including ordinary
			// settings edited on RuneLite's own config page. That must not throw away a draft.
			panel.reload();

			assertTrue(panel.isEditorScrollableForTest());
			assertFalse(panel.isShowingListForTest());
			panel.clickSaveForTest();
			assertTrue(panel.isShowingListForTest());
			assertEquals(1, fixture.controller.getRules().size());
			assertEquals("Half typed", fixture.controller.getRules().get(0).getName());
			assertEquals("dragon warhammer", fixture.controller.getRules().get(0).getPattern());
		});
	}

	@Test
	public void configReloadStillRefreshesStoredRulesBehindTheOpenEditor() throws Exception
	{
		NotificationRule stored = rule(1, "Stored", "stored", null);
		NotificationRule updated = rule(2, "Arrived later", "later", null);
		ConfigManager configManager = mock(ConfigManager.class);
		RuleCodec codec = new RuleCodec(new Gson());
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn(codec.encode(document(stored)), codec.encode(document(updated)));
		Fixture fixture = new Fixture(configManager, store(configManager));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.showNewRule();
			panel.reload();

			assertEquals(Collections.singletonList(updated), fixture.controller.getRules());
			panel.clickCancelForTest();
			assertTrue(panel.getListTextForTest().contains("Arrived later"));
		});
	}

	@Test
	public void reloadReadsTheControllersCurrentPersistedStore() throws Exception
	{
		NotificationRule first = rule(1, "First", "first", null);
		NotificationRule updatedFirst = rule(1, "Updated first", "updated", null);
		NotificationRule second = rule(2, "Second", "second", null);
		ConfigManager configManager = mock(ConfigManager.class);
		RuleCodec codec = new RuleCodec(new Gson());
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn(codec.encode(document(first)), codec.encode(document(updatedFirst, second)));
		Fixture fixture = new Fixture(configManager, store(configManager));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			assertTrue(panel.getListTextForTest().contains("First"));
			panel.selectRuleForTest(first.getId());
			panel.reload();
			assertFalse(panel.getListTextForTest().contains("First"));
			assertTrue(panel.getListTextForTest().contains("Updated first"));
			assertTrue(panel.getListTextForTest().contains("Second"));
			assertEquals(first.getId(), panel.getSelectedRuleIdForTest());
		});

		verify(configManager, times(1)).getConfiguration(
			RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY);
	}

	@Test
	public void migrationGateSummarizesImportsAndGatesTheRuleListUntilAcknowledged()
		throws Exception
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn(null);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "regexList"))
			.thenReturn("Zulrah|Vorkath\n.*loot.*");
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "colorList"))
			.thenReturn("#ff0000\n#00ff00");
		Fixture fixture = new Fixture(configManager, store(configManager));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			assertTrue(panel.isMigrationGateVisibleForTest());
			assertFalse(panel.isShowingListForTest());
			String text = panel.getMigrationGateTextForTest();
			assertTrue(text, text.contains("became 2 rules"));
			assertTrue(text, text.contains("1 rule could not be imported unchanged"));

			panel.clickMigrationContinueForTest();
			assertFalse(panel.isMigrationGateVisibleForTest());
			assertTrue(panel.isShowingListForTest());
		});
	}

	@Test
	public void theMigrationGateScrollsItsSummaryRatherThanClippingIt() throws Exception
	{
		// The sidebar host is unwrapped, so nothing above this view scrolls, and the gate is the
		// only one of the three with no scroll pane of its own. A clipped summary would take the
		// one explanation of why a batch of imported rules arrived switched off with it.
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn(null);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "regexList"))
			.thenReturn("Zulrah|Vorkath\n.*loot.*");
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "colorList"))
			.thenReturn("#ff0000\n#00ff00");
		Fixture fixture = new Fixture(configManager, store(configManager));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			assertTrue(panel.isMigrationGateVisibleForTest());
			// Structural, never measured: the summary is in a scroll pane, and the button that
			// dismisses the gate is still there to be pressed once it has been read.
			assertTrue(panel.isMigrationGateScrollableForTest());

			panel.clickMigrationContinueForTest();
			assertTrue(panel.isShowingListForTest());
		});
	}

	@Test
	public void migrationGatePersistsAcrossAConfigReloadUntilAcknowledged() throws Exception
	{
		ConfigManager configManager = mock(ConfigManager.class);
		RuleCodec codec = new RuleCodec(new Gson());
		RuleDocument reloaded = document(rule(1, "Kept", "kept", null));
		// First load migrates (rulesV1 absent); a later reload sees a stored rulesV1 and reports
		// wasMigrated=false, which must not dismiss the still-unacknowledged gate.
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn(null, codec.encode(reloaded));
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "regexList"))
			.thenReturn("Zulrah|Vorkath");
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "colorList"))
			.thenReturn("#ff0000");
		Fixture fixture = new Fixture(configManager, store(configManager));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			assertTrue(panel.isMigrationGateVisibleForTest());
			panel.reload();
			assertTrue(panel.isMigrationGateVisibleForTest());
			panel.clickMigrationContinueForTest();
			assertTrue(panel.isShowingListForTest());
		});
	}

	@Test
	public void freshInstallWithNoLegacyConfigShowsTheListNotTheMigrationGate() throws Exception
	{
		// Regression, found by manual testing on a fresh RuneLite profile: rulesV1 is absent so
		// migration runs, but with nothing to import it must not announce an import.
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn(null);
		Fixture fixture = new Fixture(configManager, store(configManager));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			assertFalse(panel.isMigrationGateVisibleForTest());
			assertTrue(panel.isShowingListForTest());
			assertFalse(panel.isBlockingBannerVisibleForTest());
			assertTrue(panel.isAddEnabledForTest());
		});
	}

	@Test
	public void migrationGateNamesTheConversionAndSeparatesRewritesFromWidenings()
		throws Exception
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn(null);
		// Zulrah|Vorkath needs a rewrite; ^Congratulations$ converts cleanly but used to match
		// the whole message. Both arrive off, asking different things of the user. .*loot.*
		// already matched anywhere, so it stays on.
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "regexList"))
			.thenReturn("Zulrah|Vorkath\nlevel .\n.*loot.*");
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "colorList"))
			.thenReturn("#ff0000\n#00ff00\n#0000ff");
		Fixture fixture = new Fixture(configManager, store(configManager));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			assertTrue(panel.isMigrationGateVisibleForTest());
			String text = panel.getMigrationGateTextForTest();

			assertTrue(text, text.contains("became 3 rules"));
			// The behavioral change is stated, not just that something happened.
			assertTrue(text, text.contains("wildcards"));
			assertTrue(text, text.contains("whole message"));
			assertTrue(text, text.contains("ignores case"));
			// Matching also folds case now, which widens every migrated rule a little.
			assertTrue(text, text.contains("ignores case"));
			// Rewrites and widenings are counted separately, because the user action differs.
			assertTrue(text, text.contains("1 rule could not be imported unchanged"));
			assertTrue(text, text.contains("1 rule converted, but would now match more messages"));
			assertTrue(text, text.contains("original lists are kept"));
		});
	}

	@Test
	public void migrationArrivingAfterTheSidebarExistsStillRaisesTheGate() throws Exception
	{
		// Regression, found in a live client: the sidebar was built against an empty profile, then
		// logging in synced account config that carried legacy lists. The migration ran and the
		// rules appeared, but the gate never did, because the panel only checked for a migration
		// in its constructor.
		ConfigManager configManager = mock(ConfigManager.class);
		String empty = new RuleCodec(new Gson()).encode(document());
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn(empty, (String) null);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "regexList"))
			.thenReturn("Zulrah|Vorkath\nloot");
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "colorList"))
			.thenReturn("#ff0000\n#00ff00");
		Fixture fixture = new Fixture(configManager, store(configManager));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			assertFalse(panel.isMigrationGateVisibleForTest());

			panel.reload();

			assertTrue(panel.isMigrationGateVisibleForTest());
			assertTrue(panel.getMigrationGateTextForTest(),
				panel.getMigrationGateTextForTest().contains("became 2 rules"));
			panel.clickMigrationContinueForTest();
			assertTrue(panel.isShowingListForTest());
		});
	}

	@Test
	public void gateStillAppearsWhenThePluginsOwnLoadPerformedTheMigration() throws Exception
	{
		// The plugin and the panel both load the store, and only whichever runs first sees the
		// migration. Here the plugin won the race, so the panel is told about it.
		Fixture fixture = fixture(document(rule(1, "Imported", "loot", "Legacy note")));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			assertFalse(panel.isMigrationGateVisibleForTest());

			panel.reload(true);

			assertTrue(panel.isMigrationGateVisibleForTest());
		});
	}

	@Test
	public void aRaisedGateWaitsForAnOpenEditorInsteadOfDiscardingTheDraft() throws Exception
	{
		Fixture fixture = fixture(document());

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.showNewRule();
			panel.setDraftForTest("Half typed", "dragon", true, null, null, null);

			panel.reload(true);

			// The draft survives and the gate is deferred rather than dropped.
			assertTrue(panel.isEditorScrollableForTest());
			assertFalse(panel.isMigrationGateVisibleForTest());
			panel.clickCancelForTest();
			assertTrue(panel.isMigrationGateVisibleForTest());
		});
	}

	@Test
	public void noMigrationGateWhenRulesLoadedFromStorage() throws Exception
	{
		Fixture fixture = fixture(document(rule(1, "Existing", "existing", null)));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			assertFalse(panel.isMigrationGateVisibleForTest());
			assertTrue(panel.isShowingListForTest());
		});
	}

	@Test
	public void resetFailureIsShownWithoutDiscardingBlockingState() throws Exception
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn("{broken");
		doThrow(new IllegalStateException("<html>reset unavailable</html>")).when(configManager)
			.setConfiguration(eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY),
				anyString());
		Fixture fixture = new Fixture(configManager, store(configManager));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.handleResetAnswerForTest(JOptionPane.OK_OPTION);
			assertTrue(panel.isBlockingBannerVisibleForTest());
			assertEquals("<html>reset unavailable</html>", panel.getActionErrorTextForTest());
			assertTrue(panel.areListErrorsWrappingNonEditableForTest());
			assertTrue(fixture.controller.hasBlockingError());
		});
	}

	@Test
	public void resetKeepsTheLegacyBackupAndDoesNotReimportIt() throws Exception
	{
		ConfigManager configManager = mock(ConfigManager.class);
		String empty = new RuleCodec(new Gson()).encode(document());
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn("{broken")
			.thenReturn(empty);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "regexList"))
			.thenReturn("Zulrah");
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "colorList"))
			.thenReturn("#ff0000");
		Fixture fixture = new Fixture(configManager, store(configManager));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.handleResetAnswerForTest(JOptionPane.OK_OPTION);
			assertFalse(panel.isBlockingBannerVisibleForTest());
			// Reset means "give me an empty list", not "re-run the import".
			assertTrue(fixture.controller.getRules().isEmpty());
		});

		verify(configManager, never()).unsetConfiguration(anyString(), anyString());
		verify(configManager, never()).getConfiguration(RuleConfigStore.GROUP, "regexList");
		verify(configManager, never()).getConfiguration(RuleConfigStore.GROUP, "colorList");
	}

	@Test
	public void editorUsesScrollPaneAndWrappingNonEditableValidationArea() throws Exception
	{
		Fixture fixture = fixture(document());

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.showNewRule();
			assertTrue(panel.isEditorScrollableForTest());
			assertTrue(panel.isValidationWrappingNonEditableForTest());
			panel.setDraftForTest("", "dragon", true, null, null, null);
			assertTrue(panel.getValidationTextForTest().contains("Name must contain"));
			assertFalse(panel.getValidationTextForTest().contains("Pattern must contain"));
		});
	}

	@Test
	public void aLongPatternDoesNotWidenTheEditForm() throws Exception
	{
		// A text field sized by its content has no width of its own to report, so the form took
		// its width from whatever was typed and ran off the side of the sidebar, taking Save and
		// Cancel with it.
		StringBuilder pattern = new StringBuilder("*");
		for (int index = 0; index < 40; index++)
		{
			pattern.append("dragon");
		}

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture(document()).panel();
			panel.showNewRule();
			panel.setDraftForTest("Rare drops", pattern.toString(), true, null, null, null);
			int viewportWidth = PluginPanel.PANEL_WIDTH;
			int formWidth = panel.editorFormWidthForTest(viewportWidth);
			// Bounded both ways: a form laid out to nothing would satisfy the upper bound too.
			assertTrue(formWidth > 0);
			assertTrue(formWidth <= viewportWidth);
		});
	}

	@Test
	public void aLongNameDoesNotWidenTheEditForm() throws Exception
	{
		// The Name field is still a single-line field sized by its content, so it is what proves
		// the form itself is pinned to the viewport rather than merely that the pattern wraps.
		// A name is capped at 64 code points, which is already far wider than the sidebar.
		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture(document()).panel();
			panel.showNewRule();
			panel.setDraftForTest("W".repeat(64), "dragon", true, null, null, null);
			int viewportWidth = PluginPanel.PANEL_WIDTH;
			int formWidth = panel.editorFormWidthForTest(viewportWidth);
			assertTrue(formWidth > 0);
			assertTrue(formWidth <= viewportWidth);
		});
	}

	@Test
	public void thePatternInputWrapsAndStillLetsEnterSave() throws Exception
	{
		// Wrapping is why it is a text area rather than a field, and a text area binds Enter to
		// insert-break in its own input map -- which would quietly break Enter-to-save.
		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture(document()).panel();
			panel.showNewRule();
			assertTrue(panel.isPatternInputWrappingForTest());
			assertTrue(panel.patternInputLetsEnterReachTheFormForTest());
		});
	}

	@Test
	public void aPastedLineBreakCannotGetIntoAPattern() throws Exception
	{
		// A newline is legal in stored data but not typeable now that Enter saves, and it would be
		// invisible in a wrapping box while showing up escaped in the rule list.
		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture(document()).panel();
			panel.showNewRule();
			panel.setDraftForTest("Rare drops", "first\nsecond\r\nthird", true, null, null, null);
			assertEquals("first second third", panel.getDraftPatternForTest());
		});
	}

	@Test
	public void backgroundButtonShowsLoadedAndUpdatedColor() throws Exception
	{
		NotificationRule existing = new NotificationRule(id(1), "Existing", true, "pattern",
			0x112233, null, null, null);
		Fixture fixture = fixture(document(existing));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.selectRuleForTest(existing.getId());
			panel.showSelectedRuleForTest();
			assertEquals("#112233", panel.getBackgroundButtonTextForTest());
			assertEquals(Integer.valueOf(0x112233), panel.getBackgroundButtonRgbForTest());
			panel.setDraftForTest("Existing", "pattern", true, 0xAABBCC, null, null);
			assertEquals("#AABBCC", panel.getBackgroundButtonTextForTest());
			assertEquals(Integer.valueOf(0xAABBCC), panel.getBackgroundButtonRgbForTest());
		});
	}

	@Test
	public void theDraftRoundTripsEveryVisibilityState() throws Exception
	{
		Fixture fixture = fixture(document());

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.showNewRule();
			// A new rule must not decide visibility, or adding one to colour a message would
			// silently start hiding or force-showing everything it matches.
			assertNull(panel.getDraftVisibleForTest());
			panel.setDraftForTest("Rare drops", "*dragon*", true, null, null, Visibility.HIDE);
			assertEquals(Visibility.HIDE, panel.getDraftVisibleForTest());
			panel.setDraftForTest("Rare drops", "*dragon*", true, null, null, Visibility.SHOW);
			assertEquals(Visibility.SHOW, panel.getDraftVisibleForTest());
			panel.setDraftForTest("Rare drops", "*dragon*", true, null, null, null);
			assertNull(panel.getDraftVisibleForTest());
		});
	}

	@Test
	public void savingAHideRulePersistsThatItHides() throws Exception
	{
		Fixture fixture = fixture(document());

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.showNewRule();
			panel.setDraftForTest("Screenshots", "*screenshot*", true, null, null, Visibility.HIDE);
			panel.clickSaveForTest();
			assertTrue(panel.isShowingListForTest());
			assertEquals(Visibility.HIDE, fixture.controller.getRules().get(0).getVisibility());
		});

		verify(fixture.configManager, times(1)).setConfiguration(
			eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY), any());
	}

	@Test
	public void editingAStoredRuleKeepsTheVisibilityItWasSavedWith() throws Exception
	{
		NotificationRule hiding = new NotificationRule(id(1), "Screenshots", true, "*screenshot*",
			null, null, Visibility.HIDE, null);
		Fixture fixture = fixture(document(hiding));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.selectRuleForTest(hiding.getId());
			panel.showSelectedRuleForTest();
			assertEquals(Visibility.HIDE, panel.getDraftVisibleForTest());
			panel.clickSaveForTest();
			assertEquals(Visibility.HIDE, fixture.controller.getRules().get(0).getVisibility());
		});
	}

	@Test
	public void theListSummaryReportsWhatARuleDoesToVisibility() throws Exception
	{
		NotificationRule hiding = new NotificationRule(id(1), "Screenshots", true, "*screenshot*",
			null, null, Visibility.HIDE, null);
		NotificationRule showing = new NotificationRule(id(2), "Drops", true, "*drop*",
			0x112233, null, Visibility.SHOW, null);
		NotificationRule plain = new NotificationRule(id(3), "Plain", true, "*plain*",
			null, null, null, null);
		Fixture fixture = fixture(document(hiding, showing, plain));

		SwingUtilities.invokeAndWait(() ->
		{
			String text = fixture.panel().getListTextForTest();
			assertTrue(text, text.contains("Style: hidden"));
			// "shown", not "always shown": a Hide rule above this one still wins, so the stronger
			// word would promise something the resolver does not deliver.
			assertTrue(text, text.contains("Style: #112233, shown"));
			assertTrue(text, text.contains("Style: default formatting"));
			// A rule whose only effect is hiding must not read as doing nothing.
			assertEquals(1, countOccurrences(text, "default formatting"));
		});
	}

	@Test
	public void aRuleCanBeAuthoredToSendItsMatchesToTheSidebarOnly() throws Exception
	{
		Fixture fixture = fixture(document());

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.showNewRule();
			panel.setDraftForTest("Quiet drops", "*shark*", true, null, null, Visibility.SIDEBAR);
			panel.clickSaveForTest();

			assertEquals(Visibility.SIDEBAR,
				fixture.controller.getRules().get(0).getVisibility());
			assertTrue(panel.getListTextForTest().contains("sidebar only"));
		});
	}

	@Test
	public void panelConstructionActionsAndTestAccessRequireEdt() throws Exception
	{
		Fixture fixture = fixture(document());
		AtomicReference<RuleEditorPanel> reference = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> reference.set(fixture.panel()));
		RuleEditorPanel panel = reference.get();

		assertEdtFailure(panel::showNewRule);
		assertEdtFailure(panel::reload);
		assertEdtFailure(panel::hasPendingMigration);
		assertEdtFailure(() -> panel.setDraftForTest("Rule", "pattern", true, 0, null, null));
		assertEdtFailure(panel::isSaveEnabledForTest);
		assertEdtFailure(panel::getValidationTextForTest);
		assertEdtFailure(panel::clickSaveForTest);
		assertEdtFailure(panel::clickCancelForTest);
		assertEdtFailure(panel::isShowingListForTest);
		assertEdtFailure(() -> panel.selectRuleForTest(id(1)));
		assertEdtFailure(panel::getSelectedRuleIdForTest);
		assertEdtFailure(panel::clickToggleForTest);
		assertEdtFailure(panel::clickUpForTest);
		assertEdtFailure(panel::clickDownForTest);
		assertEdtFailure(panel::showSelectedRuleForTest);
		assertEdtFailure(() -> panel.handleDeleteAnswerForTest(
			JOptionPane.CANCEL_OPTION, id(1)));
		assertEdtFailure(panel::getListTextForTest);
		assertEdtFailure(panel::isEditEnabledForTest);
		assertEdtFailure(panel::isUpEnabledForTest);
		assertEdtFailure(panel::isDownEnabledForTest);
		assertEdtFailure(panel::isAddEnabledForTest);
		assertEdtFailure(panel::isBlockingBannerVisibleForTest);
		assertEdtFailure(panel::isMigrationGateVisibleForTest);
		assertEdtFailure(panel::isMigrationGateScrollableForTest);
		assertEdtFailure(panel::getMigrationGateTextForTest);
		assertEdtFailure(panel::clickMigrationContinueForTest);
		assertEdtFailure(panel::isResetVisibleForTest);
		assertEdtFailure(() -> panel.handleResetAnswerForTest(JOptionPane.OK_OPTION));
		assertEdtFailure(panel::getActionErrorTextForTest);
		assertEdtFailure(panel::areListErrorsWrappingNonEditableForTest);
		assertEdtFailure(panel::isEditorScrollableForTest);
		assertEdtFailure(panel::isValidationWrappingNonEditableForTest);
		assertEdtFailure(panel::getBackgroundButtonTextForTest);
		assertEdtFailure(panel::getBackgroundButtonRgbForTest);
		assertEdtFailure(panel::getDraftVisibleForTest);
		IllegalStateException constructorError = assertThrows(IllegalStateException.class,
			() -> new RuleEditorPanel(fixture.controller));
		assertEquals(EDT_ERROR, constructorError.getMessage());
	}

	@Test
	public void anEmptyListExplainsItselfAndAPopulatedOneDoesNot() throws Exception
	{
		Fixture empty = fixture(document());
		Fixture populated = fixture(document(rule(1, "Existing", "drop", null)));

		SwingUtilities.invokeAndWait(() ->
		{
			assertTrue(empty.panel().getEmptyStateTextForTest().contains("No rules yet"));
			assertEquals("", populated.panel().getEmptyStateTextForTest());
		});
	}

	@Test
	public void theEditorSaysPatternsMatchTheWholeMessage() throws Exception
	{
		// Anchoring is the one thing a user cannot infer from the field: "dragon" saves cleanly,
		// looks healthy in the list, and never matches anything.
		Fixture fixture = fixture(document());

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.showNewRule();
			String hint = panel.getPatternHintTextForTest();
			assertTrue(hint, hint.contains("entire message"));
			assertTrue(hint, hint.contains("*dragon*"));
		});
	}

	@Test
	public void resetKeepsTheStoredRulesUntilTheConfirmationIsAccepted() throws Exception
	{
		// The stored value is usually unreadable by the time Reset appears, but not always: an
		// unsupported schema version is intact data written by a newer release.
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn("{broken");
		Fixture fixture = new Fixture(configManager, store(configManager));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			assertTrue(panel.isResetVisibleForTest());
			panel.handleResetAnswerForTest(JOptionPane.CANCEL_OPTION);
		});
		verify(configManager, never()).setConfiguration(
			eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY), any());

		SwingUtilities.invokeAndWait(() ->
			fixture.panel().handleResetAnswerForTest(JOptionPane.OK_OPTION));
		verify(configManager).setConfiguration(
			eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY), any());
	}

	@Test
	public void aLongPatternStaysInsideTheRuleList() throws Exception
	{
		// Issue #8: the list took its width from the widest cell, so a long pattern widened the
		// list past the viewport and raised a horizontal scrollbar instead of being clipped.
		StringBuilder pattern = new StringBuilder("*");
		for (int index = 0; index < 40; index++)
		{
			pattern.append("dragon");
		}
		Fixture fixture = fixture(document(
			rule(1, "Rare drops", pattern.toString(), null)));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			int viewportWidth = PluginPanel.PANEL_WIDTH;
			int cellWidth = panel.ruleListCellWidthForTest(0, viewportWidth);
			// Bounded below as well: a list that laid out to nothing would satisfy the upper bound
			// just as well as a correctly clipped one, and this is meant to catch a regression in
			// either direction. Both sides are measured or RuneLite constants, never pixel counts
			// derived from a font, so the comparison holds wherever the suite runs.
			assertTrue(cellWidth > 0);
			assertTrue(cellWidth <= viewportWidth);
		});
	}

	@Test
	public void theRowTooltipCarriesThePattern() throws Exception
	{
		Fixture fixture = fixture(document(
			rule(1, "Rare drops", "*dragon warhammer*", null)));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			String tooltip = panel.ruleListTooltipForTest(4, 4);
			assertNotNull(tooltip);
			assertTrue(tooltip, tooltipText(tooltip).contains("Pattern:"));
			assertTrue(tooltip, tooltipText(tooltip).contains("*dragonwarhammer*"));
			// Short enough to need no wrapping, so it must be one line.
			assertFalse(tooltip, tooltip.contains("<br>"));
			// And it must carry no CSS width. In Swing's HTML a width is a fixed width rather
			// than a maximum, so it pads a short tooltip out to that width and leaves a broad
			// empty margin down the right -- the whole reason the wrapping moved into Java.
			assertFalse(tooltip, tooltip.contains("width"));
		});
	}

	@Test
	public void theRowTooltipCarriesAnImportWarningTheRowIsTooNarrowToShow() throws Exception
	{
		// The note is the only thing that says why an imported rule arrived switched off, and the
		// migration gate sends the user to read it. Clipping every row label to the panel width
		// left it unreadable, so the tooltip is now where it stays reachable.
		Fixture fixture = fixture(document(rule(1, "Imported", "*drop*",
			"Pattern uses unsupported syntax; rewrite it with the wildcard matcher.")));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			String tooltip = panel.ruleListTooltipForTest(4, 4);
			assertNotNull(tooltip);
			assertTrue(tooltip, tooltipText(tooltip).contains("Warning:"));
			assertTrue(tooltip,
				tooltipText(tooltip).contains("rewriteitwiththewildcardmatcher."));
		});
	}

	@Test
	public void aRuleCannotPutMarkupIntoItsOwnTooltip() throws Exception
	{
		// The tooltip is rendered as HTML so it can wrap, which makes the pattern and the note
		// untrusted input. Both are escaped, so a rule that looks like markup reads as text.
		Fixture fixture = fixture(document(
			rule(1, "Sneaky", "*<b>bold</b>&*", "<i>note</i>")));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			String tooltip = panel.ruleListTooltipForTest(4, 4);
			assertNotNull(tooltip);
			assertTrue(tooltip, tooltip.replaceAll("<br>", "")
				.contains("&lt;b&gt;bold&lt;/b&gt;&amp;"));
			assertTrue(tooltip, tooltip.replaceAll("<br>", "")
				.contains("&lt;i&gt;note&lt;/i&gt;"));
			assertFalse(tooltip, tooltip.contains("<b>"));
			assertFalse(tooltip, tooltip.contains("<i>"));
		});
	}

	@Test
	public void theRowTooltipTruncatesAPatternPastTheBound() throws Exception
	{
		// TOOLTIP_PREVIEW_LIMIT exists because a stored pattern can be 262144 code points; a
		// pattern that actually crosses 200 code points is what proves the bound is wired into
		// the tooltip rather than just declared and unused. Mirrors the boundary technique in
		// patternPreviewEscapesAllLineSeparatorsWithoutDanglingEscape: 199 plain code points then
		// a two-code-point escape that cannot fit pushes the break before it, so the truncated
		// text is exactly 199 a's followed by the ellipsis, never a dangling "\".
		Fixture fixture = fixture(document(
			rule(1, "Rare drops", "a".repeat(199) + "\\tail", null)));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			String tooltip = panel.ruleListTooltipForTest(4, 4);
			assertNotNull(tooltip);
			assertTrue(tooltip, tooltipText(tooltip).contains("a".repeat(199) + "…"));
			assertFalse(tooltip, tooltipText(tooltip).contains("a".repeat(199) + "\\…"));
			// Long enough that it must have wrapped rather than run off the screen as one line.
			assertTrue(tooltip, tooltip.contains("<br>"));
		});
	}

	@Test
	public void aPointBelowTheLastRowHasNoTooltip() throws Exception
	{
		// locationToIndex answers with the nearest row for a point past the end, so without a
		// bounds check the last rule's tooltip would follow the cursor down the empty list.
		Fixture fixture = fixture(document(
			rule(1, "Rare drops", "*dragon warhammer*", null)));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			assertNull(panel.ruleListTooltipForTest(4, 10_000));
		});
	}

	/**
	 * The tooltip's visible text, with markup and every space removed.
	 *
	 * <p>Whitespace goes because wrapping decides where the breaks fall: a run with no spaces is
	 * split mid-token, while ordinary text loses the space it broke at. Comparing without spaces
	 * is true of both, and none of these tests are about where the breaks land.</p>
	 */
	private static String tooltipText(String tooltip)
	{
		return tooltip.replaceAll("<[^>]*>", "").replaceAll("\\s+", "");
	}

	private static void assertEdtFailure(Runnable operation)
	{
		IllegalStateException exception = assertThrows(IllegalStateException.class, operation::run);
		assertEquals(EDT_ERROR, exception.getMessage());
	}

	private static Fixture fixture(RuleDocument document)
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn(new RuleCodec(new Gson()).encode(document));
		return new Fixture(configManager, store(configManager));
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

	private static NotificationRule rule(int id, String name, String pattern, String migrationNote)
	{
		return new NotificationRule(id(id), name, true, pattern, 0xBF616A, 90, null,
			migrationNote);
	}

	private static int countOccurrences(String text, String needle)
	{
		int count = 0;
		for (int index = text.indexOf(needle); index >= 0;
			index = text.indexOf(needle, index + needle.length()))
		{
			count++;
		}
		return count;
	}

	private static UUID id(int value)
	{
		return new UUID(0L, value);
	}

	private static List<UUID> ids(List<NotificationRule> rules)
	{
		List<UUID> ids = new ArrayList<>();
		for (NotificationRule rule : rules)
		{
			ids.add(rule.getId());
		}
		return ids;
	}

	private static final class Fixture
	{
		private final ConfigManager configManager;
		private final RuleConfigStore store;
		private RuleEditorController controller;

		private Fixture(ConfigManager configManager, RuleConfigStore store)
		{
			this.configManager = configManager;
			this.store = store;
		}

		private RuleEditorPanel panel()
		{
			controller = new RuleEditorController(store);
			clearInvocations(configManager);
			return new RuleEditorPanel(controller);
		}
	}
}
