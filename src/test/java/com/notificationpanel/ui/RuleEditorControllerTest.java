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
import com.notificationpanel.rules.LegacyRuleMigrator;
import com.notificationpanel.rules.NotificationRule;
import com.notificationpanel.rules.RuleCodec;
import com.notificationpanel.rules.RuleConfigStore;
import com.notificationpanel.rules.RuleDocument;
import com.notificationpanel.rules.RuleSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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

public class RuleEditorControllerTest
{
	private static final String EDT_ERROR = "Rule editor mutations must run on the EDT.";

	@Test
	public void reorderToggleAndDeleteSaveOneDocumentEach() throws Exception
	{
		NotificationRule first = rule(1, "First", true, "first", null);
		NotificationRule second = rule(2, "Second", true, "second", null);
		Fixture fixture = fixture(document(first, second));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = fixture.controller();
			assertTrue(controller.moveDown(first.getId()).isSuccess());
			assertEquals(Arrays.asList(second, first), controller.getRules());
			assertTrue(controller.setEnabled(first.getId(), false).isSuccess());
			assertFalse(controller.find(first.getId()).isEnabled());
			assertTrue(controller.delete(second.getId()).isSuccess());
			assertEquals(Collections.singletonList(first.getId()), ids(controller.getRules()));
		});

		verify(fixture.configManager, times(3)).setConfiguration(
			eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY), any());
	}

	@Test
	public void invalidDraftAndBoundaryMovesDoNotSave() throws Exception
	{
		NotificationRule first = rule(1, "First", true, "first", null);
		NotificationRule second = rule(2, "Second", true, "second", null);
		Fixture fixture = fixture(document(first, second));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = fixture.controller();
			NotificationRule invalid = new NotificationRule(id(3), "", true, "bad",
				0xBF616A, 90, null, null);
			assertFalse(controller.add(invalid).isSuccess());
			assertFalse(controller.moveUp(first.getId()).isSuccess());
			assertFalse(controller.moveDown(second.getId()).isSuccess());
			assertEquals(Arrays.asList(first, second), controller.getRules());
		});

		verify(fixture.configManager, never()).setConfiguration(
			eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY), any());
	}

	@Test
	public void rejectsTheRuleAfterTheCapWithoutSaving() throws Exception
	{
		Fixture fixture = fixture(new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION,
			Collections.emptyList(), rules(RuleSet.MAX_RULES)));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = fixture.controller();
			RuleEditorController.SaveResult result = controller.add(
				rule(RuleSet.MAX_RULES + 1, "Overflow", true, "overflow", null));
			assertFalse(result.isSuccess());
			assertTrue(result.getErrors().get(0),
				result.getErrors().get(0).contains(String.valueOf(RuleSet.MAX_RULES)));
			assertEquals(RuleSet.MAX_RULES, controller.getRules().size());
		});

		verify(fixture.configManager, never()).setConfiguration(
			eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY), any());
	}

	@Test
	public void editRetainsUuidClearsMigrationNoteAndPreservesEnvelope() throws Exception
	{
		NotificationRule migrated = rule(1, "Imported", true, "drop", "Review this rule.");
		RuleDocument source = new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION,
			Collections.singletonList("Only the first 100 rules were migrated."),
			Collections.singletonList(migrated));
		Fixture fixture = fixture(source);

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = fixture.controller();
			NotificationRule draft = new NotificationRule(UUID.randomUUID(), "Drops", false,
				"dragon", 0xBF616A, 90, null, "discard me");
			assertTrue(controller.edit(migrated.getId(), draft).isSuccess());
			NotificationRule saved = controller.find(migrated.getId());
			assertEquals(migrated.getId(), saved.getId());
			assertEquals("Drops", saved.getName());
			assertEquals(null, saved.getMigrationNote());
			assertEquals(source.getSchemaVersion(), controller.getDocument().getSchemaVersion());
			assertEquals(source.getMigrationWarnings(),
				controller.getDocument().getMigrationWarnings());
		});

		verify(fixture.configManager, times(1)).setConfiguration(
			eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY), any());
	}

	@Test
	public void enablingInvalidMigratedRuleFailsWithoutSaving() throws Exception
	{
		NotificationRule invalid = new NotificationRule(id(1), "", false, "(", null, null, null,
			"Unsupported legacy pattern.");
		Fixture fixture = fixture(document(invalid));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = fixture.controller();
			RuleEditorController.SaveResult result = controller.setEnabled(invalid.getId(), true);
			assertFalse(result.isSuccess());
			assertTrue(result.getErrors().toString().contains("Name"));
			assertFalse(controller.find(invalid.getId()).isEnabled());
		});

		verify(fixture.configManager, never()).setConfiguration(
			eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY), any());
	}

	@Test
	public void enablingAWidenedRuleClearsTheNoteThatAskedForThatDecision() throws Exception
	{
		// The note says the pattern now matches more than it used to and to turn the rule on if
		// that is what you want. Doing so is the answer, and nothing else on screen clears it.
		NotificationRule widened = rule(1, "Widened", false, "level *",
			LegacyRuleMigrator.WIDENED_NOTE_PREFIX + "A \".\" became \"*\".");
		NotificationRule broken = rule(2, "Broken", false, "Zulrah|Vorkath",
			LegacyRuleMigrator.PROBLEM_NOTE_PREFIX + "Wildcards can't combine alternatives.");
		Fixture fixture = fixture(document(widened, broken));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = fixture.controller();
			assertTrue(controller.setEnabled(widened.getId(), true).isSuccess());
			assertNull(controller.find(widened.getId()).getMigrationNote());

			// A rule that could not be converted is still wrong once enabled, so its note stays.
			assertTrue(controller.setEnabled(broken.getId(), true).isSuccess());
			assertNotNull(controller.find(broken.getId()).getMigrationNote());

			// Turning the widened rule back off does not resurrect the note.
			assertTrue(controller.setEnabled(widened.getId(), false).isSuccess());
			assertNull(controller.find(widened.getId()).getMigrationNote());
		});
	}

	@Test
	public void unknownOperationsFailExplicitlyWithoutSaving() throws Exception
	{
		NotificationRule existing = rule(1, "Existing", true, "drop", null);
		Fixture fixture = fixture(document(existing));
		UUID missing = id(99);

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = fixture.controller();
			assertThrows(IllegalArgumentException.class, () -> controller.find(missing));
			assertFalse(controller.edit(missing, existing).isSuccess());
			assertFalse(controller.setEnabled(missing, false).isSuccess());
			assertFalse(controller.moveUp(missing).isSuccess());
			assertFalse(controller.moveDown(missing).isSuccess());
			assertFalse(controller.delete(missing).isSuccess());
			assertEquals(Collections.singletonList(existing), controller.getRules());
		});

		verify(fixture.configManager, never()).setConfiguration(
			eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY), any());
	}

	@Test
	public void saveExceptionLeavesMemoryUnchanged() throws Exception
	{
		NotificationRule existing = rule(1, "Existing", true, "drop", null);
		Fixture fixture = fixture(document(existing));
		doThrow(new IllegalStateException("configuration unavailable"))
			.when(fixture.configManager).setConfiguration(
				eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY), any());

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = fixture.controller();
			RuleEditorController.SaveResult result = controller.setEnabled(existing.getId(), false);
			assertFalse(result.isSuccess());
			assertTrue(result.getErrors().get(0).contains("configuration unavailable"));
			assertTrue(controller.find(existing.getId()).isEnabled());
		});

		verify(fixture.configManager, times(1)).setConfiguration(
			eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY), any());
	}

	@Test
	public void newDraftHasConventionalNameUniqueUuidAndNoMutation() throws Exception
	{
		Fixture fixture = fixture(document(rule(1, "Existing", true, "drop", null)));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = fixture.controller();
			NotificationRule first = controller.newDraft();
			NotificationRule second = controller.newDraft();
			assertEquals("Rule 2", first.getName());
			assertNotEquals(first.getId(), second.getId());
			assertTrue(first.isEnabled());
			assertEquals(1, controller.getRules().size());
		});

		verify(fixture.configManager, never()).setConfiguration(
			eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY), any());
	}

	@Test
	public void newDraftForBuildsAWildcardWrappedPatternAndTheMessageAsName() throws Exception
	{
		Fixture fixture = fixture(document());

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = fixture.controller();
			NotificationRule draft = controller.newDraftFor("You catch a shark.");

			assertEquals("*You catch a shark.*", draft.getPattern());
			assertEquals("You catch a shark.", draft.getName());
			assertTrue(draft.isEnabled());
		});
	}

	@Test
	public void newDraftForTruncatesALongMessageToExactlyTheFieldLimits() throws Exception
	{
		Fixture fixture = fixture(document());
		String message = "a".repeat(3000);

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = fixture.controller();
			NotificationRule draft = controller.newDraftFor(message);

			// 512 is the pattern field's own cap; 510 leaves room for the two wildcards it is
			// wrapped in, so the wrapped result still fits it exactly.
			assertEquals(512,
				draft.getPattern().codePointCount(0, draft.getPattern().length()));
			assertEquals("*" + "a".repeat(510) + "*", draft.getPattern());
			assertEquals(64, draft.getName().codePointCount(0, draft.getName().length()));
			assertEquals("a".repeat(64), draft.getName());
		});
	}

	@Test
	public void newDraftForTruncatesSupplementaryCharactersOnACodePointBoundary() throws Exception
	{
		Fixture fixture = fixture(document());
		String shark = "🦈"; // U+1F988 SHARK: a surrogate pair, one code point.
		String message = shark.repeat(600);

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = fixture.controller();
			NotificationRule draft = controller.newDraftFor(message);

			// Truncating by chars instead of code points would cut a pair in half and leave a lone
			// surrogate at the boundary; asserting the exact repeated string is what would catch
			// that, codePointCount alone would not.
			String patternBody =
				draft.getPattern().substring(1, draft.getPattern().length() - 1);
			assertEquals(510, patternBody.codePointCount(0, patternBody.length()));
			assertEquals(shark.repeat(510), patternBody);
			assertEquals(64, draft.getName().codePointCount(0, draft.getName().length()));
			assertEquals(shark.repeat(64), draft.getName());
		});
	}

	@Test
	public void newDraftForFallsBackToNewDraftWhenTheMessageIsBlank() throws Exception
	{
		Fixture fixture = fixture(document());

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = fixture.controller();
			NotificationRule blank = controller.newDraftFor("   ");
			NotificationRule empty = controller.newDraftFor("");
			NotificationRule missing = controller.newDraftFor(null);

			assertEquals("Rule 1", blank.getName());
			assertEquals("", blank.getPattern());
			assertEquals("Rule 1", empty.getName());
			assertEquals("Rule 1", missing.getName());
		});
	}

	@Test
	public void corruptStoreBlocksMutationsAndResetClearsOnlyTheStructuredRules() throws Exception
	{
		ConfigManager configManager = mock(ConfigManager.class);
		RuleCodec codec = new RuleCodec(new Gson());
		String empty = codec.encode(new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION,
			Collections.emptyList(), Collections.emptyList()));
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn("{broken")
			.thenReturn(empty);
		RuleConfigStore store = store(configManager);

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = new RuleEditorController(store);
			assertTrue(controller.hasBlockingError());
			assertFalse(controller.add(rule(1, "Blocked", true, "drop", null)).isSuccess());
			assertTrue(controller.reset().isSuccess());
			assertFalse(controller.hasBlockingError());
			assertTrue(controller.getRules().isEmpty());
		});

		verify(configManager).setConfiguration(RuleConfigStore.GROUP,
			RuleConfigStore.RULES_KEY, empty);
		// The legacy lists are the user's only copy of their pre-2.0 setup; recovering from
		// corrupt rule data must leave them intact.
		verify(configManager, never()).unsetConfiguration(anyString(), anyString());
	}

	@Test
	public void resetSurfacesUnexpectedBlockingReload() throws Exception
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn("{broken");
		RuleConfigStore store = store(configManager);

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = new RuleEditorController(store);
			RuleEditorController.SaveResult result = controller.reset();
			assertFalse(result.isSuccess());
			assertTrue(controller.hasBlockingError());
			assertFalse(controller.getBlockingError().isEmpty());
			assertTrue(controller.getRules().isEmpty());
		});
	}

	@Test
	public void resetWriteFailureKeepsStructuredBlockingState() throws Exception
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn("{broken");
		doThrow(new IllegalStateException("reset write failed")).when(configManager)
			.setConfiguration(eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY),
				anyString());
		RuleConfigStore store = store(configManager);

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = new RuleEditorController(store);
			RuleEditorController.SaveResult result = controller.reset();
			assertFalse(result.isSuccess());
			assertEquals(Collections.singletonList("reset write failed"), result.getErrors());
			assertTrue(controller.hasBlockingError());
			assertTrue(controller.getRules().isEmpty());
		});

		verify(configManager, never()).unsetConfiguration(anyString(), anyString());
	}

	@Test
	public void reloadReadsCurrentPersistedDocumentInsteadOfCallerState() throws Exception
	{
		NotificationRule first = rule(1, "First", true, "first", null);
		NotificationRule second = rule(2, "Second", true, "second", null);
		ConfigManager configManager = mock(ConfigManager.class);
		RuleCodec codec = new RuleCodec(new Gson());
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn(codec.encode(document(first)), codec.encode(document(second)));
		RuleConfigStore store = store(configManager);

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = new RuleEditorController(store);
			assertEquals(Collections.singletonList(first), controller.getRules());
			controller.reload();
			assertEquals(Collections.singletonList(second), controller.getRules());
		});

		verify(configManager, times(2)).getConfiguration(
			RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY);
	}

	@Test
	public void reloadCannotClearBlockingStateWhileStoreRemainsCorrupt() throws Exception
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY))
			.thenReturn("{broken");
		RuleConfigStore store = store(configManager);

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = new RuleEditorController(store);
			String blockingError = controller.getBlockingError();
			controller.reload();
			assertTrue(controller.hasBlockingError());
			assertEquals(blockingError, controller.getBlockingError());
			assertTrue(controller.getRules().isEmpty());
		});

		verify(configManager, times(2)).getConfiguration(
			RuleConfigStore.GROUP, RuleConfigStore.RULES_KEY);
	}

	@Test
	public void fieldErrorsAreOrderedAndNotDuplicated() throws Exception
	{
		Fixture fixture = fixture(document());
		NotificationRule draft = new NotificationRule(id(1), "", true, "*loot*", null, null, null,
			null);

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = fixture.controller();
			assertEquals(Collections.singletonList(
				"Name must contain 1 to 64 Unicode code points."),
				controller.validateForEditor(draft));
		});
	}

	@Test
	public void nullAndDuplicateDraftsFailWithoutSaving() throws Exception
	{
		NotificationRule existing = rule(1, "Existing", true, "existing", null);
		Fixture fixture = fixture(document(existing));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = fixture.controller();
			assertFalse(controller.add(null).isSuccess());
			assertFalse(controller.edit(existing.getId(), null).isSuccess());
			NotificationRule duplicate = rule(1, "Duplicate", true, "duplicate", null);
			assertFalse(controller.add(duplicate).isSuccess());
			assertEquals(Collections.singletonList(existing), controller.getRules());
			NullPointerException exception = assertThrows(NullPointerException.class,
				() -> new RuleEditorController(null));
			assertEquals("store", exception.getMessage());
		});

		verify(fixture.configManager, never()).setConfiguration(
			eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY), any());
	}

	@Test
	public void persistedControllerDocumentContainsCompleteEnvelopeAndRules() throws Exception
	{
		NotificationRule existing = rule(1, "Existing", true, "existing", null);
		NotificationRule added = rule(2, "Added", false, "added", null);
		RuleDocument source = new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION,
			Collections.singletonList("Migration warning."), Collections.singletonList(existing));
		Fixture fixture = fixture(source);

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = fixture.controller();
			assertTrue(controller.add(added).isSuccess());
		});

		ArgumentCaptor<String> encoded = ArgumentCaptor.forClass(String.class);
		verify(fixture.configManager).setConfiguration(
			eq(RuleConfigStore.GROUP), eq(RuleConfigStore.RULES_KEY), encoded.capture());
		RuleCodec.DecodeResult decoded = new RuleCodec(new Gson()).decode(encoded.getValue());
		assertTrue(decoded.isSuccess());
		assertEquals(new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION,
			source.getMigrationWarnings(), Arrays.asList(existing, added)), decoded.getDocument());
	}

	@Test
	public void listsAndErrorsAreImmutableAndNeverNull() throws Exception
	{
		Fixture fixture = fixture(document(rule(1, "Existing", true, "drop", null)));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = fixture.controller();
			assertThrows(UnsupportedOperationException.class,
				() -> controller.getRules().clear());
			RuleEditorController.SaveResult failed = controller.delete(id(99));
			assertNotNull(failed.getErrors());
			assertThrows(UnsupportedOperationException.class,
				() -> failed.getErrors().add("changed"));
		});
	}

	@Test
	public void everyPublicControllerOperationRejectsTheWrongThread() throws Exception
	{
		Fixture fixture = fixture(document(rule(1, "Existing", true, "drop", null)));
		AtomicReference<RuleEditorController> reference = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> reference.set(fixture.controller()));
		RuleEditorController controller = reference.get();

		assertEdtFailure(controller::getRules);
		assertEdtFailure(controller::getDocument);
		assertEdtFailure(controller::hasBlockingError);
		assertEdtFailure(controller::getBlockingError);
		assertEdtFailure(controller::wasMigrated);
		assertEdtFailure(controller::markMigrated);
		assertEdtFailure(controller::newDraft);
		assertEdtFailure(() -> controller.newDraftFor("You catch a shark."));
		assertEdtFailure(() -> controller.find(id(1)));
		assertEdtFailure(() -> controller.add(rule(2, "Added", true, "add", null)));
		assertEdtFailure(() -> controller.edit(id(1), rule(3, "Edit", true, "edit", null)));
		assertEdtFailure(() -> controller.setEnabled(id(1), false));
		assertEdtFailure(() -> controller.moveUp(id(1)));
		assertEdtFailure(() -> controller.moveDown(id(1)));
		assertEdtFailure(() -> controller.delete(id(1)));
		assertEdtFailure(controller::reset);
		assertEdtFailure(controller::reload);
		IllegalStateException constructorError = assertThrows(IllegalStateException.class,
			() -> new RuleEditorController(fixture.store));
		assertEquals(EDT_ERROR, constructorError.getMessage());
	}

	@Test
	public void markMigratedForcesWasMigratedForTheEditorBanner() throws Exception
	{
		Fixture fixture = fixture(document(rule(1, "Existing", true, "existing", null)));

		SwingUtilities.invokeAndWait(() ->
		{
			RuleEditorController controller = fixture.controller();
			assertFalse(controller.wasMigrated());
			controller.markMigrated();
			assertTrue(controller.wasMigrated());
		});
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

	private static NotificationRule rule(int id, String name, boolean enabled, String pattern,
		String migrationNote)
	{
		return new NotificationRule(id(id), name, enabled, pattern, 0xBF616A, 90, null,
			migrationNote);
	}

	private static UUID id(int value)
	{
		return new UUID(0L, value);
	}

	private static List<NotificationRule> rules(int count)
	{
		List<NotificationRule> rules = new ArrayList<>();
		for (int index = 1; index <= count; index++)
		{
			rules.add(rule(index, "Rule " + index, true, "pattern " + index, null));
		}
		return rules;
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

		private Fixture(ConfigManager configManager, RuleConfigStore store)
		{
			this.configManager = configManager;
			this.store = store;
		}

		private RuleEditorController controller()
		{
			RuleEditorController controller = new RuleEditorController(store);
			clearInvocations(configManager);
			return controller;
		}
	}
}
