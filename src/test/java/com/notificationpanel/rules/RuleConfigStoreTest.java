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
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
		verify(configManager).setConfiguration(eq(RuleConfigStore.GROUP), eq("rulesV1"),
			anyString());
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
		assertRejected(documentWith(enabledInvalidRule("Rule", "(a)\\1")));
		verify(configManager, never()).setConfiguration(anyString(), anyString(), any());
	}

	@Test
	public void savesDisabledInvalidMigrationRows()
	{
		NotificationRule disabledInvalid = new NotificationRule(
			UUID.fromString("00000000-0000-0000-0000-000000000001"), "", false, "(a)\\1",
			null, null, NotificationRule.Visibility.INHERIT, "Legacy migration problem.");
		RuleDocument document = documentWith(disabledInvalid);

		store.save(document);

		verify(configManager).setConfiguration(eq(RuleConfigStore.GROUP), eq("rulesV1"),
			anyString());
	}

	@Test
	public void resetUnsetsStructuredAndLegacyKeys()
	{
		store.reset();

		verify(configManager).unsetConfiguration(RuleConfigStore.GROUP, "rulesV1");
		verify(configManager).unsetConfiguration(RuleConfigStore.GROUP, "regexList");
		verify(configManager).unsetConfiguration(RuleConfigStore.GROUP, "colorList");
		verifyNoMoreInteractions(configManager);
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
			NotificationRule.Visibility.SHOW, null);
	}

	private static NotificationRule enabledInvalidRule(String name, String pattern)
	{
		return new NotificationRule(UUID.fromString("00000000-0000-0000-0000-000000000001"), name,
			true, pattern, null, 50, NotificationRule.Visibility.SHOW, null);
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
				NotificationRule.Visibility.SHOW, null));
		}
		return rules;
	}
}
