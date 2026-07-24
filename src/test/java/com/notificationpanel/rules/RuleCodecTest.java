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
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RuleCodecTest
{
	private final RuleCodec codec = new RuleCodec(new Gson());

	@Test
	public void roundTripsStableOrderNullsAndNotes()
	{
		NotificationRule first = new NotificationRule(
			UUID.fromString("7df65dc5-c46f-450e-9152-a1959767b65f"),
			"Rare drops", true, "dragon warhammer", 0xBF616A, 90,
			null);
		NotificationRule second = new NotificationRule(
			UUID.fromString("c1262a25-4938-4d97-a816-54e549008e43"),
			"Imported rule", false, "*rune*", null, null,
			"Legacy migration problem.");
		RuleDocument source = new RuleDocument(1, Collections.singletonList("warning"),
			Arrays.asList(first, second));

		String encoded = codec.encode(source);
		RuleCodec.DecodeResult result = codec.decode(encoded);

		assertTrue(result.isSuccess());
		assertEquals(source, result.getDocument());
		assertNull(result.getError());
		assertEquals("{\"schemaVersion\":1,\"migrationWarnings\":[\"warning\"],\"rules\":["
			+ "{\"id\":\"7df65dc5-c46f-450e-9152-a1959767b65f\","
			+ "\"name\":\"Rare drops\",\"enabled\":true,"
			+ "\"pattern\":\"dragon warhammer\",\"backgroundColor\":\"#BF616A\","
			+ "\"opacityPercent\":90,\"migrationNote\":null},"
			+ "{\"id\":\"c1262a25-4938-4d97-a816-54e549008e43\","
			+ "\"name\":\"Imported rule\",\"enabled\":false,\"pattern\":\"*rune*\","
			+ "\"backgroundColor\":null,\"opacityPercent\":null,"
			+ "\"migrationNote\":\"Legacy migration problem.\"}]}", encoded);
	}

	@Test
	public void ruleDocumentDefensivelyCopiesItsLists()
	{
		List<String> warnings = new ArrayList<>(Collections.singletonList("warning"));
		List<NotificationRule> rules = new ArrayList<>(Collections.singletonList(rule(
			"7df65dc5-c46f-450e-9152-a1959767b65f", "#112233")));
		RuleDocument document = new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION, warnings, rules);

		warnings.clear();
		rules.clear();

		assertEquals(Collections.singletonList("warning"), document.getMigrationWarnings());
		assertEquals(1, document.getRules().size());
		assertUnsupported(() -> document.getMigrationWarnings().clear());
		assertUnsupported(() -> document.getRules().clear());
		assertEquals(document, new RuleDocument(1, Collections.singletonList("warning"),
			document.getRules()));
		assertEquals(document.hashCode(), new RuleDocument(1, Collections.singletonList("warning"),
			document.getRules()).hashCode());
	}

	@Test
	public void ruleDocumentRejectsNullListsAndElements()
	{
		NotificationRule rule = rule("7df65dc5-c46f-450e-9152-a1959767b65f", "#112233");

		assertNullPointer(() -> new RuleDocument(1, null, Collections.emptyList()));
		assertNullPointer(() -> new RuleDocument(1, Collections.emptyList(), null));
		assertNullPointer(() -> new RuleDocument(1, Collections.singletonList(null),
			Collections.emptyList()));
		assertNullPointer(() -> new RuleDocument(1, Collections.emptyList(),
			Collections.singletonList(null)));
		assertEquals(1, new RuleDocument(1, Collections.emptyList(),
			Collections.singletonList(rule)).getRules().size());
	}

	@Test
	public void reportsInvalidJsonAndOversizedInputWithoutThrowing()
	{
		assertFailure("{broken", "Structured rules are not valid JSON.");
		assertFailure(null, "Structured rules are not valid JSON.");
		assertFailure("x".repeat(262_145),
			"Structured rule data exceeds 262144 characters.");
	}

	@Test
	public void acceptsStructuredJsonAtExactLengthLimit()
	{
		String envelope = "{\"schemaVersion\":1,\"migrationWarnings\":[],\"rules\":[]}";
		String exactLimit = envelope + " ".repeat(262_144 - envelope.length());

		assertEquals(262_144, exactLimit.length());
		RuleCodec.DecodeResult result = codec.decode(exactLimit);
		assertTrue(result.getError(), result.isSuccess());
		assertEquals(new RuleDocument(1, Collections.emptyList(), Collections.emptyList()),
			result.getDocument());
	}

	@Test
	public void rejectsUnsupportedOrMissingSchemaVersions()
	{
		assertFailure("{\"schemaVersion\":2,\"migrationWarnings\":[],\"rules\":[]}",
			"Unsupported structured-rule schema version: 2.");
		assertFailure("{\"migrationWarnings\":[],\"rules\":[]}",
			"Unsupported structured-rule schema version: 0.");
	}

	@Test
	public void rejectsMissingOrNullArraysAndEntries()
	{
		assertMalformed("{\"schemaVersion\":1,\"rules\":[]}", "migration warnings");
		assertMalformed("{\"schemaVersion\":1,\"migrationWarnings\":null,\"rules\":[]}",
			"migration warnings");
		assertMalformed("{\"schemaVersion\":1,\"migrationWarnings\":[],\"rules\":null}", "rules");
		assertMalformed("{\"schemaVersion\":1,\"migrationWarnings\":[null],\"rules\":[]}",
			"migration warning");
		assertMalformed("{\"schemaVersion\":1,\"migrationWarnings\":[],\"rules\":[null]}",
			"null rule");
	}

	@Test
	public void rejectsInvalidAndDuplicateRuleIdentifiers()
	{
		assertMalformed(documentJson(ruleJson("not-a-uuid", "#112233")), "UUID");
		assertMalformed(documentJson(ruleJson("1-1-1-1-1", "#112233")), "UUID");
		String duplicate = ruleJson("7df65dc5-c46f-450e-9152-a1959767b65f", "#112233");
		assertMalformed("{\"schemaVersion\":1,\"migrationWarnings\":[],\"rules\":["
			+ duplicate + "," + duplicate + "]}", "unique");
	}

	@Test
	public void rejectsMoreThanOneHundredRules()
	{
		List<NotificationRule> rules = new ArrayList<>();
		for (int i = 0; i < 101; i++)
		{
			rules.add(new NotificationRule(UUID.nameUUIDFromBytes(("rule-" + i).getBytes()),
				"Rule " + i, true, "pattern", i, null,
				null));
		}

		RuleCodec.DecodeResult result = codec.decode(codec.encode(
			new RuleDocument(1, Collections.emptyList(), rules)));

		assertFalse(result.isSuccess());
		assertTrue(result.getError().contains("at most 100"));
	}

	@Test
	public void rejectsMalformedColorsAndOpacity()
	{
		for (String color : Arrays.asList("#12345", "#1234567", "123456", "#12345G"))
		{
			assertMalformed(documentJson(ruleJson("7df65dc5-c46f-450e-9152-a1959767b65f", color)),
				"background color");
		}
		assertMalformed(documentJson(ruleJsonWithOpacity(-1)), "opacity");
		assertMalformed(documentJson(ruleJsonWithOpacity(101)), "opacity");
	}

	private void assertFailure(String json, String expectedError)
	{
		RuleCodec.DecodeResult result = codec.decode(json);
		assertFalse(result.isSuccess());
		assertNull(result.getDocument());
		assertEquals(expectedError, result.getError());
	}

	private void assertMalformed(String json, String expectedReason)
	{
		RuleCodec.DecodeResult result = codec.decode(json);
		assertFalse(result.isSuccess());
		assertNull(result.getDocument());
		assertTrue(result.getError(), result.getError().startsWith(
			"Structured rule data is malformed: "));
		assertTrue(result.getError(), result.getError().contains(expectedReason));
	}

	private static String documentJson(String ruleJson)
	{
		return "{\"schemaVersion\":1,\"migrationWarnings\":[],\"rules\":["
			+ ruleJson + "]}";
	}

	private static String ruleJson(String id, String color)
	{
		return "{\"id\":\"" + id + "\",\"name\":\"Rule\",\"enabled\":true,"
			+ "\"pattern\":\"pattern\",\"backgroundColor\":\"" + color + "\","
			+ "\"opacityPercent\":50,\"migrationNote\":null}";
	}

	private static String ruleJsonWithOpacity(int opacity)
	{
		return "{\"id\":\"7df65dc5-c46f-450e-9152-a1959767b65f\","
			+ "\"name\":\"Rule\",\"enabled\":true,\"pattern\":\"pattern\","
			+ "\"backgroundColor\":null,\"opacityPercent\":" + opacity
			+ ",\"migrationNote\":null}";
	}

	private static NotificationRule rule(String id, String color)
	{
		return new NotificationRule(UUID.fromString(id), "Rule", true, "pattern",
			Integer.parseInt(color.substring(1), 16), 50, null);
	}

	private static void assertUnsupported(Runnable action)
	{
		try
		{
			action.run();
			fail("Expected UnsupportedOperationException");
		}
		catch (UnsupportedOperationException expected)
		{
			assertTrue(true);
		}
	}

	private static void assertNullPointer(Runnable action)
	{
		try
		{
			action.run();
			fail("Expected NullPointerException");
		}
		catch (NullPointerException expected)
		{
			assertTrue(true);
		}
	}
}
