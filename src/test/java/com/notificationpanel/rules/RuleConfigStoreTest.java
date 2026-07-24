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
package com.notificationpanel.rules;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class RuleConfigStoreTest
{
	@Rule
	public final MockitoRule mockito = MockitoJUnit.rule();

	@Mock
	private ConfigManager configManager;

	private RuleConfigStore store;

	@Before
	public void setUp()
	{
		store = new RuleConfigStore(configManager, new Gson());
	}

	@Test
	public void loadsValidStructuredRulesWithoutReadingMigratingOrWritingLegacyData()
	{
		RuleDocument document = validDocument();
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "rulesV1"))
			.thenReturn(new RuleCodec(new Gson()).encode(document));

		RuleConfigStore.LoadResult result = store.load();

		assertEquals(document, result.getDocument());
		assertFalse(result.wasMigrated());
		assertFalse(result.hasBlockingError());
		verify(configManager).getConfiguration(RuleConfigStore.GROUP, "rulesV1");
		verifyNoMoreInteractions(configManager);
	}

	@Test
	public void migratesOnlyWhenStructuredRulesAreAbsentAndLeavesLegacyKeys()
	{
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "rulesV1")).thenReturn(null);
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "regexList")).thenReturn("drop");
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "colorList")).thenReturn("hide");

		RuleConfigStore.LoadResult result = store.load();

		assertTrue(result.wasMigrated());
		assertFalse(result.hasBlockingError());
		assertEquals(1, result.getDocument().getRules().size());
		ArgumentCaptor<String> encoded = ArgumentCaptor.forClass(String.class);
		verify(configManager).setConfiguration(eq(RuleConfigStore.GROUP), eq("rulesV1"),
			encoded.capture());
		RuleCodec.DecodeResult decoded = new RuleCodec(new Gson()).decode(encoded.getValue());
		assertTrue(decoded.isSuccess());
		assertEquals(result.getDocument(), decoded.getDocument());
		verify(configManager, never()).unsetConfiguration(anyString(), anyString());
	}

	@Test
	public void loadsStoredMigrationWithoutMigratingAgain()
	{
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "rulesV1")).thenReturn(null,
			new RuleCodec(new Gson()).encode(validDocument()));
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "regexList")).thenReturn("drop");
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "colorList")).thenReturn("hide");

		RuleConfigStore.LoadResult first = store.load();
		RuleConfigStore.LoadResult second = store.load();

		assertTrue(first.wasMigrated());
		assertFalse(second.wasMigrated());
		verify(configManager, times(1)).getConfiguration(RuleConfigStore.GROUP, "regexList");
		verify(configManager, times(1)).getConfiguration(RuleConfigStore.GROUP, "colorList");
		verify(configManager, times(1)).setConfiguration(eq(RuleConfigStore.GROUP), eq("rulesV1"),
			anyString());
	}

	@Test
	public void retainsCorruptOversizedAndUnsupportedStructuredValuesWithoutWriting()
	{
		for (String stored : Arrays.asList("{broken", "x".repeat(262_145),
			"{\"schemaVersion\":2,\"migrationWarnings\":[],\"rules\":[]}"))
		{
			when(configManager.getConfiguration(RuleConfigStore.GROUP, "rulesV1")).thenReturn(stored);

			RuleConfigStore.LoadResult result = store.load();

			assertFalse(result.wasMigrated());
			assertTrue(result.hasBlockingError());
			assertNotNull(result.getBlockingError());
			assertEquals(emptyDocument(), result.getDocument());
		}
		verify(configManager, times(3)).getConfiguration(RuleConfigStore.GROUP, "rulesV1");
		verify(configManager, never()).getConfiguration(RuleConfigStore.GROUP, "regexList");
		verify(configManager, never()).getConfiguration(RuleConfigStore.GROUP, "colorList");
		verify(configManager, never()).setConfiguration(anyString(), anyString(), any());
		verify(configManager, never()).unsetConfiguration(anyString(), anyString());
	}

	@Test
	public void savesValidSchemaOneDocumentWithAtMostOneHundredRulesOnce()
	{
		RuleDocument document = new RuleDocument(1, Collections.emptyList(), oneHundredRules());

		store.save(document);

		ArgumentCaptor<String> encoded = ArgumentCaptor.forClass(String.class);
		verify(configManager, times(1)).setConfiguration(eq(RuleConfigStore.GROUP), eq("rulesV1"),
			encoded.capture());
		RuleCodec.DecodeResult decoded = new RuleCodec(new Gson()).decode(encoded.getValue());
		assertTrue(decoded.isSuccess());
		assertEquals(document, decoded.getDocument());
		verifyNoMoreInteractions(configManager);
	}

	@Test
	public void rejectsInvalidDocumentsWithoutWriting()
	{
		assertRejected(null);
		assertRejected(new RuleDocument(2, Collections.emptyList(), Collections.emptyList()));
		assertRejected(new RuleDocument(1, Collections.emptyList(), oneHundredAndOneRules()));
		NotificationRule duplicate = validRule("00000000-0000-0000-0000-000000000001");
		assertRejected(new RuleDocument(1, Collections.emptyList(), Arrays.asList(duplicate, duplicate)));
		assertRejected(documentWith(enabledInvalidRule("", "pattern")));
		assertRejected(documentWith(enabledInvalidRule("Rule", "x".repeat(513))));
		verifyNoInteractions(configManager);
	}

	@Test
	public void savesDisabledInvalidMigrationRows()
	{
		NotificationRule disabledInvalid = new NotificationRule(
			UUID.fromString("00000000-0000-0000-0000-000000000001"), "", false, "(",
			null, null, "Legacy migration problem.");
		RuleDocument document = documentWith(disabledInvalid);

		store.save(document);

		verify(configManager).setConfiguration(eq(RuleConfigStore.GROUP), eq("rulesV1"),
			anyString());
	}

	@Test
	public void freshInstallWithNothingToImportIsNotReportedAsAMigration()
	{
		// Regression: migration runs on any install without rulesV1, including a brand new one.
		// Reporting that as a migration made the editor greet first-time users with a gate
		// announcing an import of configuration they never had.
		RuleConfigStore.LoadResult loaded = store.load();

		assertFalse(loaded.wasMigrated());
		assertFalse(loaded.hasBlockingError());
		assertTrue(loaded.getDocument().getRules().isEmpty());
		// Nothing was imported, so nothing is written. Writing here would mark the profile as
		// migrated, and legacy lists arriving later -- restored, synced, or switched to -- would
		// then never be imported at all.
		verify(configManager, never()).setConfiguration(anyString(), anyString(), any());
	}

	@Test
	public void legacyListsArrivingAfterAnEmptyLoadAreStillImported()
	{
		// A profile can be enabled before its legacy lists exist, then receive them.
		assertFalse(store.load().wasMigrated());
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "regexList"))
			.thenReturn(".*dragon.*");
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "colorList"))
			.thenReturn("#112233");

		RuleConfigStore.LoadResult loaded = store.load();

		assertTrue(loaded.wasMigrated());
		assertEquals(1, loaded.getDocument().getRules().size());
	}

	@Test
	public void legacyRowsAreReportedAsAMigration()
	{
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "regexList"))
			.thenReturn(".*dragon.*");
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "colorList"))
			.thenReturn("#112233");

		RuleConfigStore.LoadResult loaded = store.load();

		assertTrue(loaded.wasMigrated());
		assertEquals(1, loaded.getDocument().getRules().size());
	}

	@Test
	public void aWarningWithNoImportedRulesStillCountsAsAMigration()
	{
		// An oversized legacy value produces no rules but a warning the user must see.
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "regexList"))
			.thenReturn("x".repeat(262_145));

		RuleConfigStore.LoadResult loaded = store.load();

		assertTrue(loaded.wasMigrated());
		assertTrue(loaded.getDocument().getRules().isEmpty());
		assertFalse(loaded.getDocument().getMigrationWarnings().isEmpty());
	}

	@Test
	public void resetWritesAnEmptyDocumentAndKeepsTheLegacyBackup()
	{
		store.resetStructuredRules();

		ArgumentCaptor<String> encoded = ArgumentCaptor.forClass(String.class);
		verify(configManager).setConfiguration(eq(RuleConfigStore.GROUP), eq("rulesV1"),
			encoded.capture());
		RuleCodec.DecodeResult decoded = new RuleCodec(new Gson()).decode(encoded.getValue());
		assertTrue(decoded.isSuccess());
		assertTrue(decoded.getDocument().getRules().isEmpty());
		assertTrue(decoded.getDocument().getMigrationWarnings().isEmpty());
		// Recovering from corrupt rule data must not destroy the user's only record of their
		// pre-2.0 configuration.
		verify(configManager, never()).unsetConfiguration(anyString(), anyString());
		verifyNoMoreInteractions(configManager);
	}

	@Test
	public void resetLeavesCorruptDataInPlaceWhenTheWriteFails()
	{
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "rulesV1"))
			.thenReturn("{broken");
		doThrow(new IllegalStateException("write failed")).when(configManager)
			.setConfiguration(eq(RuleConfigStore.GROUP), eq("rulesV1"), anyString());

		IllegalStateException exception = assertThrows(IllegalStateException.class,
			store::resetStructuredRules);
		RuleConfigStore.LoadResult loaded = store.load();

		assertEquals("write failed", exception.getMessage());
		// The corrupt value survives, so the store stays blocked rather than re-migrating.
		assertTrue(loaded.hasBlockingError());
		assertFalse(loaded.wasMigrated());
		verify(configManager, never()).getConfiguration(RuleConfigStore.GROUP, "regexList");
		verify(configManager, never()).getConfiguration(RuleConfigStore.GROUP, "colorList");
	}

	@Test
	public void writingAnEmptyDocumentStopsMigrationFromRunningAgain()
	{
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "regexList"))
			.thenReturn("dragon");
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "colorList"))
			.thenReturn("#112233");
		ArgumentCaptor<String> encoded = ArgumentCaptor.forClass(String.class);

		store.resetStructuredRules();
		verify(configManager).setConfiguration(eq(RuleConfigStore.GROUP), eq("rulesV1"),
			encoded.capture());
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "rulesV1"))
			.thenReturn(encoded.getValue());
		RuleConfigStore.LoadResult loaded = store.load();

		assertFalse(loaded.wasMigrated());
		assertTrue(loaded.getDocument().getRules().isEmpty());
	}

	@Test
	public void treatsABlankStructuredValueAsNeverWritten()
	{
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "rulesV1")).thenReturn("   ");
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "regexList"))
			.thenReturn("dragon");
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "colorList"))
			.thenReturn("#112233");

		RuleConfigStore.LoadResult loaded = store.load();

		// A blank value carries no rules, so migrating beats stranding the user behind a banner.
		assertFalse(loaded.hasBlockingError());
		assertTrue(loaded.wasMigrated());
		assertEquals(1, loaded.getDocument().getRules().size());
		assertEquals("dragon", loaded.getDocument().getRules().get(0).getPattern());
	}

	@Test
	public void resetKeepsValidRulesRecoverableWhenTheWriteFails()
	{
		RuleDocument document = validDocument();
		when(configManager.getConfiguration(RuleConfigStore.GROUP, "rulesV1"))
			.thenReturn(new RuleCodec(new Gson()).encode(document));
		doThrow(new IllegalStateException("structured reset failed")).when(configManager)
			.setConfiguration(eq(RuleConfigStore.GROUP), eq("rulesV1"), anyString());

		IllegalStateException exception = assertThrows(IllegalStateException.class,
			store::resetStructuredRules);
		RuleConfigStore.LoadResult loaded = store.load();

		assertEquals("structured reset failed", exception.getMessage());
		assertEquals(document, loaded.getDocument());
		assertFalse(loaded.wasMigrated());
		verify(configManager, never()).unsetConfiguration(anyString(), anyString());
		verify(configManager, never()).getConfiguration(RuleConfigStore.GROUP, "regexList");
		verify(configManager, never()).getConfiguration(RuleConfigStore.GROUP, "colorList");
	}

	private void assertRejected(RuleDocument document)
	{
		try
		{
			store.save(document);
			fail("Expected save to reject the document.");
		}
		catch (IllegalArgumentException expected)
		{
			assertNotNull(expected.getMessage());
		}
	}

	private static RuleDocument validDocument()
	{
		return documentWith(validRule("00000000-0000-0000-0000-000000000001"));
	}

	private static RuleDocument emptyDocument()
	{
		return new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION, Collections.emptyList(),
			Collections.emptyList());
	}

	private static RuleDocument documentWith(NotificationRule rule)
	{
		return new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION, Collections.emptyList(),
			Collections.singletonList(rule));
	}

	private static NotificationRule validRule(String id)
	{
		return new NotificationRule(UUID.fromString(id), "Rule", true, "pattern", 0x112233, 50,
			null);
	}

	private static NotificationRule enabledInvalidRule(String name, String pattern)
	{
		return new NotificationRule(UUID.fromString("00000000-0000-0000-0000-000000000001"), name,
			true, pattern, null, 50, null);
	}

	private static List<NotificationRule> oneHundredRules()
	{
		return rules(100);
	}

	private static List<NotificationRule> oneHundredAndOneRules()
	{
		return rules(101);
	}

	private static List<NotificationRule> rules(int count)
	{
		List<NotificationRule> rules = new ArrayList<>();
		for (int index = 0; index < count; index++)
		{
			rules.add(new NotificationRule(UUID.nameUUIDFromBytes(("rule-" + index).getBytes()),
				"Rule " + index, true, "pattern", 0x112233, 50,
				null));
		}
		return rules;
	}
}
