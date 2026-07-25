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
			"Rare drops", true, "dragon warhammer", 0xBF616A, 90, Boolean.TRUE,
			null);
		NotificationRule second = new NotificationRule(
			UUID.fromString("c1262a25-4938-4d97-a816-54e549008e43"),
			"Imported rule", false, "*rune*", null, null, null,
			"Legacy migration problem.");
		RuleDocument source = new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION,
			Collections.singletonList("warning"), Arrays.asList(first, second));

		String encoded = codec.encode(source);
		RuleCodec.DecodeResult result = codec.decode(encoded);

		assertTrue(result.isSuccess());
		assertEquals(source, result.getDocument());
		assertNull(result.getError());
		assertEquals("{\"schemaVersion\":2,\"migrationWarnings\":[\"warning\"],\"rules\":["
			+ "{\"id\":\"7df65dc5-c46f-450e-9152-a1959767b65f\","
			+ "\"name\":\"Rare drops\",\"enabled\":true,"
			+ "\"pattern\":\"dragon warhammer\",\"backgroundColor\":\"#BF616A\","
			+ "\"opacityPercent\":90,\"visible\":true,\"migrationNote\":null},"
			+ "{\"id\":\"c1262a25-4938-4d97-a816-54e549008e43\","
			+ "\"name\":\"Imported rule\",\"enabled\":false,\"pattern\":\"*rune*\","
			+ "\"backgroundColor\":null,\"opacityPercent\":null,\"visible\":null,"
			+ "\"migrationNote\":\"Legacy migration problem.\"}]}", encoded);
	}

	@Test
	public void roundTripsEveryVisibilityState()
	{
		List<NotificationRule> rules = Arrays.asList(
			visibilityRule("7df65dc5-c46f-450e-9152-a1959767b65f", null),
			visibilityRule("c1262a25-4938-4d97-a816-54e549008e43", Boolean.TRUE),
			visibilityRule("2a9b6f0e-1d4c-4f57-8f0a-6c6b9d1e2f30", Boolean.FALSE));
		RuleDocument source = new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION,
			Collections.emptyList(), rules);

		RuleCodec.DecodeResult result = codec.decode(codec.encode(source));

		assertTrue(result.getError(), result.isSuccess());
		assertEquals(source, result.getDocument());
		assertNull(result.getDocument().getRules().get(0).getVisible());
		assertEquals(Boolean.TRUE, result.getDocument().getRules().get(1).getVisible());
		assertEquals(Boolean.FALSE, result.getDocument().getRules().get(2).getVisible());
	}

	@Test
	public void treatsAnAbsentVisibleFieldAsNoDecision()
	{
		RuleCodec.DecodeResult result = codec.decode(documentJson(
			ruleJson("7df65dc5-c46f-450e-9152-a1959767b65f", "#112233")));

		assertTrue(result.getError(), result.isSuccess());
		assertNull(result.getDocument().getRules().get(0).getVisible());
	}

	@Test
	public void upgradesARuleDisabledOnlyForHidingIntoAHideRule()
	{
		// The 2.0 import disabled these and left a note the user could do nothing useful with. Now
		// that a rule can hide again, the stored note is enough to reconstruct what they meant.
		RuleCodec.DecodeResult result = codec.decode(versionOneDocumentJson(false,
			LegacyRuleMigrator.PROBLEM_NOTE_PREFIX + LegacyRuleMigrator.LEGACY_HIDE_PROBLEM));

		assertTrue(result.getError(), result.isSuccess());
		assertEquals(RuleDocument.CURRENT_SCHEMA_VERSION,
			result.getDocument().getSchemaVersion());
		NotificationRule rule = result.getDocument().getRules().get(0);
		assertTrue(rule.isEnabled());
		assertEquals(Boolean.FALSE, rule.getVisible());
		assertNull(rule.getMigrationNote());
	}

	@Test
	public void leavesARuleWithAnotherProblemDisabledAndKeepsOnlyThatProblem()
	{
		String other = "Pattern uses unsupported syntax; rewrite it with the * wildcard.";
		RuleCodec.DecodeResult result = codec.decode(versionOneDocumentJson(false,
			LegacyRuleMigrator.PROBLEM_NOTE_PREFIX + other + " "
				+ LegacyRuleMigrator.LEGACY_HIDE_PROBLEM));

		assertTrue(result.getError(), result.isSuccess());
		NotificationRule rule = result.getDocument().getRules().get(0);
		assertFalse(rule.isEnabled());
		assertEquals(Boolean.FALSE, rule.getVisible());
		assertEquals(LegacyRuleMigrator.PROBLEM_NOTE_PREFIX + other, rule.getMigrationNote());
	}

	@Test
	public void keepsAProblemRecordedBeforeTheHideSentenceReadable()
	{
		String other = "Pattern is missing.";
		RuleCodec.DecodeResult result = codec.decode(versionOneDocumentJson(false,
			LegacyRuleMigrator.PROBLEM_NOTE_PREFIX + LegacyRuleMigrator.LEGACY_HIDE_PROBLEM
				+ " " + other));

		assertTrue(result.getError(), result.isSuccess());
		assertEquals(LegacyRuleMigrator.PROBLEM_NOTE_PREFIX + other,
			result.getDocument().getRules().get(0).getMigrationNote());
	}

	@Test
	public void joinsTheProblemsEitherSideOfAStrippedHideSentence()
	{
		// The only shape that needs the whitespace collapse: cutting from the middle leaves the
		// separator from both sides behind, so the note would keep a double space forever.
		String before = "Pattern is missing.";
		String after = "Invalid legacy color token: #zzz.";
		RuleCodec.DecodeResult result = codec.decode(versionOneDocumentJson(false,
			LegacyRuleMigrator.PROBLEM_NOTE_PREFIX + before + " "
				+ LegacyRuleMigrator.LEGACY_HIDE_PROBLEM + " " + after));

		assertTrue(result.getError(), result.isSuccess());
		assertEquals(LegacyRuleMigrator.PROBLEM_NOTE_PREFIX + before + " " + after,
			result.getDocument().getRules().get(0).getMigrationNote());
	}

	@Test
	public void doesNotRescueADocumentAlreadyAtThisVersion()
	{
		// Nothing writes that sentence any more, so a current document carrying it was authored
		// outside the plugin. Rescuing it would make the upgrade permanent rather than one-time.
		String note = LegacyRuleMigrator.PROBLEM_NOTE_PREFIX
			+ LegacyRuleMigrator.LEGACY_HIDE_PROBLEM;
		RuleCodec.DecodeResult result = codec.decode(
			versionOneDocumentJson(false, note).replace("\"schemaVersion\":1",
				"\"schemaVersion\":" + RuleDocument.CURRENT_SCHEMA_VERSION));

		assertTrue(result.getError(), result.isSuccess());
		NotificationRule rule = result.getDocument().getRules().get(0);
		assertFalse(rule.isEnabled());
		assertNull(rule.getVisible());
		assertEquals(note, rule.getMigrationNote());
	}

	@Test
	public void writesTheOlderVersionUntilARuleActuallySetsVisibility()
	{
		// An older build rejects a version it does not know and shows the corrupt-data banner, so
		// a profile that uses no visibility override stays readable by one.
		RuleDocument plain = new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION,
			Collections.emptyList(), Collections.singletonList(
				visibilityRule("7df65dc5-c46f-450e-9152-a1959767b65f", null)));

		assertTrue(codec.encode(plain), codec.encode(plain).contains("\"schemaVersion\":1"));

		for (Boolean visible : Arrays.asList(Boolean.TRUE, Boolean.FALSE))
		{
			RuleDocument using = new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION,
				Collections.emptyList(), Collections.singletonList(
					visibilityRule("7df65dc5-c46f-450e-9152-a1959767b65f", visible)));
			assertTrue(codec.encode(using),
				codec.encode(using).contains("\"schemaVersion\":2"));
			// Whichever version was written, reading it back must give the same rules.
			RuleCodec.DecodeResult round = codec.decode(codec.encode(using));
			assertTrue(round.getError(), round.isSuccess());
			assertEquals(visible, round.getDocument().getRules().get(0).getVisible());
		}
	}

	@Test
	public void leavesVersionOneRulesWithoutTheHideSentenceAlone()
	{
		String widened = LegacyRuleMigrator.WIDENED_NOTE_PREFIX
			+ "A \".\" that matched a single character became \"*\", which matches any run of "
			+ "characters. Turn it on if that is what you want.";

		for (String note : Arrays.asList(widened,
			LegacyRuleMigrator.PROBLEM_NOTE_PREFIX + "Pattern is missing."))
		{
			RuleCodec.DecodeResult result = codec.decode(versionOneDocumentJson(false, note));

			assertTrue(result.getError(), result.isSuccess());
			NotificationRule rule = result.getDocument().getRules().get(0);
			assertFalse(rule.isEnabled());
			assertNull(rule.getVisible());
			assertEquals(note, rule.getMigrationNote());
		}

		RuleCodec.DecodeResult plain = codec.decode(versionOneDocumentJson(true, null));

		assertTrue(plain.getError(), plain.isSuccess());
		assertTrue(plain.getDocument().getRules().get(0).isEnabled());
		assertNull(plain.getDocument().getRules().get(0).getVisible());
		assertNull(plain.getDocument().getRules().get(0).getMigrationNote());
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
		assertEquals(document, new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION,
			Collections.singletonList("warning"), document.getRules()));
		assertEquals(document.hashCode(), new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION,
			Collections.singletonList("warning"), document.getRules()).hashCode());
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
		String envelope = "{\"schemaVersion\":2,\"migrationWarnings\":[],\"rules\":[]}";
		String exactLimit = envelope + " ".repeat(262_144 - envelope.length());

		assertEquals(262_144, exactLimit.length());
		RuleCodec.DecodeResult result = codec.decode(exactLimit);
		assertTrue(result.getError(), result.isSuccess());
		assertEquals(new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION, Collections.emptyList(),
			Collections.emptyList()), result.getDocument());
	}

	@Test
	public void rejectsUnsupportedOrMissingSchemaVersions()
	{
		assertFailure("{\"schemaVersion\":3,\"migrationWarnings\":[],\"rules\":[]}",
			"Unsupported structured-rule schema version: 3.");
		assertFailure("{\"migrationWarnings\":[],\"rules\":[]}",
			"Unsupported structured-rule schema version: 0.");
	}

	@Test
	public void readsAVersionOneDocumentAsThisVersion()
	{
		// Rejecting the version every installed profile stores would empty the editor and lose the
		// user's rules until they reset, so the previous version has to stay readable.
		RuleCodec.DecodeResult result = codec.decode(
			"{\"schemaVersion\":1,\"migrationWarnings\":[\"warning\"],\"rules\":[]}");

		assertTrue(result.getError(), result.isSuccess());
		assertEquals(RuleDocument.CURRENT_SCHEMA_VERSION, result.getDocument().getSchemaVersion());
		assertEquals(Collections.singletonList("warning"),
			result.getDocument().getMigrationWarnings());
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
	public void rejectsMoreRulesThanTheCapAllows()
	{
		List<NotificationRule> rules = new ArrayList<>();
		for (int i = 0; i < RuleSet.MAX_RULES + 1; i++)
		{
			rules.add(new NotificationRule(UUID.nameUUIDFromBytes(("rule-" + i).getBytes()),
				"Rule " + i, true, "pattern", i, null, null,
				null));
		}

		RuleCodec.DecodeResult result = codec.decode(codec.encode(
			new RuleDocument(1, Collections.emptyList(), rules)));

		assertFalse(result.isSuccess());
		assertTrue(result.getError(),
			result.getError().contains("at most " + RuleSet.MAX_RULES));
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
		return "{\"schemaVersion\":2,\"migrationWarnings\":[],\"rules\":["
			+ ruleJson + "]}";
	}

	/** A stored document as version 1 wrote it: one rule, no {@code visible} field. */
	private static String versionOneDocumentJson(boolean enabled, String migrationNote)
	{
		return "{\"schemaVersion\":1,\"migrationWarnings\":[],\"rules\":["
			+ "{\"id\":\"7df65dc5-c46f-450e-9152-a1959767b65f\",\"name\":\"Imported rule 1\","
			+ "\"enabled\":" + enabled + ",\"pattern\":\"*screenshot*\","
			+ "\"backgroundColor\":null,\"opacityPercent\":null,\"migrationNote\":"
			+ (migrationNote == null ? "null" : "\"" + migrationNote.replace("\"", "\\\"") + "\"")
			+ "}]}";
	}

	private static NotificationRule visibilityRule(String id, Boolean visible)
	{
		return new NotificationRule(UUID.fromString(id), "Rule", true, "pattern", null, null,
			visible, null);
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
			Integer.parseInt(color.substring(1), 16), 50, null, null);
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
