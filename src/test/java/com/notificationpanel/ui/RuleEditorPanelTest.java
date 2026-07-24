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
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
			panel.setDraftForTest("Rare drops", "(a)\\1", true, 0xBF616A, null,
				NotificationRule.Visibility.INHERIT);
			assertFalse(panel.isSaveEnabledForTest());
			assertTrue(panel.getValidationTextForTest().contains("regex"));
			panel.setDraftForTest("Rare drops", "dragon warhammer", true, 0xBF616A, 90,
				NotificationRule.Visibility.INHERIT);
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
			panel.setDraftForTest("", "", true, null, null,
				NotificationRule.Visibility.INHERIT);
			assertEquals(
				"Name must contain 1 to 64 Unicode code points. "
					+ "Pattern must contain 1 to 512 Unicode code points. "
					+ "Choose at least one background color, opacity, or visibility override.",
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
			panel.setDraftForTest("Rare drops", "dragon warhammer", true, 0xBF616A, 90,
				NotificationRule.Visibility.SHOW);
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
			panel.setDraftForTest("Discard", "discard", true, 0x112233, 50,
				NotificationRule.Visibility.HIDE);
			panel.clickCancelForTest();
			assertTrue(panel.isShowingListForTest());
			assertEquals(Collections.singletonList(existing), fixture.controller.getRules());
		});

		verify(fixture.configManager, never()).setConfiguration(
			eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY), any());
	}

	@Test
	public void deleteCancelDoesNothingAndOkDelegatesExactlyOnce() throws Exception
	{
		NotificationRule first = rule(1, "First", "first", null);
		NotificationRule second = rule(2, "Second", "second", null);
		Fixture fixture = fixture(document(first, second));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.selectRuleForTest(first.getId());
			panel.handleDeleteAnswerForTest(JOptionPane.CANCEL_OPTION);
			assertEquals(Arrays.asList(first, second), fixture.controller.getRules());
			panel.handleDeleteAnswerForTest(JOptionPane.OK_OPTION);
			assertEquals(Collections.singletonList(second), fixture.controller.getRules());
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
			panel.setDraftForTest("Drops", "dragon", false, null, 80,
				NotificationRule.Visibility.HIDE);
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
			"line one\nline two", 0x112233, 75, NotificationRule.Visibility.HIDE,
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
			assertTrue(text.contains("HIDE"));
			assertTrue(text.contains("Warning"));
			assertFalse(text.contains("line one\nline two"));
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
	public void corruptBannerDisablesEditsAndResetRestoresUsableEmptyList() throws Exception
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn("{broken")
			.thenReturn(null);
		Fixture fixture = new Fixture(configManager, store(configManager));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			assertTrue(panel.isBlockingBannerVisibleForTest());
			assertFalse(panel.isAddEnabledForTest());
			assertTrue(panel.isResetVisibleForTest());
			panel.clickResetForTest();
			assertFalse(panel.isBlockingBannerVisibleForTest());
			assertTrue(panel.isAddEnabledForTest());
			assertTrue(fixture.controller.getRules().isEmpty());
		});

		verify(configManager).unsetConfiguration(RuleConfigStore.GROUP,
			RuleConfigStore.RULES_KEY);
		verify(configManager).unsetConfiguration(RuleConfigStore.GROUP, "regexList");
		verify(configManager).unsetConfiguration(RuleConfigStore.GROUP, "colorList");
	}

	@Test
	public void resetFailureIsShownWithoutDiscardingBlockingState() throws Exception
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn("{broken");
		doThrow(new IllegalStateException("reset unavailable")).when(configManager)
			.unsetConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY);
		Fixture fixture = new Fixture(configManager, store(configManager));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorPanel panel = fixture.panel();
			panel.clickResetForTest();
			assertTrue(panel.isBlockingBannerVisibleForTest());
			assertEquals("reset unavailable", panel.getActionErrorTextForTest());
			assertTrue(fixture.controller.hasBlockingError());
		});
	}

	@Test
	public void navigationIconIsGeneratedInMemoryAtSixteenPixels() throws Exception
	{
		Fixture fixture = fixture(document());

		SwingUtilities.invokeAndWait(() ->
		{
			BufferedImage image = fixture.panel().getNavigationIcon();
			assertNotNull(image);
			assertEquals(16, image.getWidth());
			assertEquals(16, image.getHeight());
			assertNotEquals(0, image.getRGB(4, 4));
			assertNotEquals(0, image.getRGB(4, 8));
			assertNotEquals(0, image.getRGB(4, 12));
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
		assertEdtFailure(panel::getNavigationIcon);
		assertEdtFailure(() -> panel.setDraftForTest("Rule", "pattern", true, 0, null,
			NotificationRule.Visibility.INHERIT));
		assertEdtFailure(panel::isSaveEnabledForTest);
		assertEdtFailure(panel::getValidationTextForTest);
		assertEdtFailure(panel::clickSaveForTest);
		assertEdtFailure(panel::clickCancelForTest);
		assertEdtFailure(panel::isShowingListForTest);
		assertEdtFailure(() -> panel.selectRuleForTest(id(1)));
		assertEdtFailure(panel::showSelectedRuleForTest);
		assertEdtFailure(() -> panel.handleDeleteAnswerForTest(JOptionPane.CANCEL_OPTION));
		assertEdtFailure(panel::getListTextForTest);
		assertEdtFailure(panel::isEditEnabledForTest);
		assertEdtFailure(panel::isUpEnabledForTest);
		assertEdtFailure(panel::isDownEnabledForTest);
		assertEdtFailure(panel::isAddEnabledForTest);
		assertEdtFailure(panel::isBlockingBannerVisibleForTest);
		assertEdtFailure(panel::isResetVisibleForTest);
		assertEdtFailure(panel::clickResetForTest);
		assertEdtFailure(panel::getActionErrorTextForTest);
		IllegalStateException constructorError = assertThrows(IllegalStateException.class,
			() -> new RuleEditorPanel(fixture.controller));
		assertEquals(EDT_ERROR, constructorError.getMessage());
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
		return new NotificationRule(id(id), name, true, pattern, 0xBF616A, 90,
			NotificationRule.Visibility.INHERIT, migrationNote);
	}

	private static UUID id(int value)
	{
		return new UUID(0L, value);
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
