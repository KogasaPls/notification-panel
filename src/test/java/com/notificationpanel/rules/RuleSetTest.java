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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RuleSetTest
{
	@Test
	public void findsSubstringsAndUsesFirstMatchPerAttribute()
	{
		NotificationRule color = rule("color", "*dragon*", 0x112233, null);
		NotificationRule opacity = rule("opacity", "*warhammer*", null, 40);
		NotificationRule later = rule("later", "*warhammer*", 0xFFFFFF, 80);

		RuleSet.CompileResult compiled = RuleSet.compile(Arrays.asList(color, opacity, later));
		RuleSet.Resolution result = compiled.getRuleSet()
			.resolve("You received a dragon warhammer.");

		assertTrue(compiled.getErrors().isEmpty());
		assertEquals(Integer.valueOf(0x112233), result.getBackgroundRgb());
		assertEquals(Integer.valueOf(40), result.getOpacityPercent());
		assertTrue(result.isMatched());
	}

	@Test
	public void matchesAnchoredWildcardsCaseInsensitively()
	{
		NotificationRule gap = rule("gap", "Your*thrall*grave.", 0x111111, null);
		// Matching is anchored, so covering the rest of the message is the pattern's job.
		NotificationRule literal = rule("literal", "*antifire*", null, 40);

		RuleSet ruleSet = RuleSet.compile(Arrays.asList(gap, literal)).getRuleSet();

		assertEquals(Integer.valueOf(0x111111),
			ruleSet.resolve("Your lesser thrall returns to the grave.").getBackgroundRgb());

		RuleSet.Resolution antifire = ruleSet.resolve("You feel ANTIFIRE coursing.");
		assertEquals(Integer.valueOf(40), antifire.getOpacityPercent());
		assertTrue(antifire.isMatched());

		RuleSet.Resolution none = ruleSet.resolve("nothing relevant");
		assertNull(none.getBackgroundRgb());
		assertNull(none.getOpacityPercent());
		assertFalse(none.isMatched());
	}

	@Test
	public void excludesDisabledAndInvalidEnabledRules()
	{
		NotificationRule disabled = disabledRule("dragon");
		NotificationRule disabledOverride = new NotificationRule(UUID.randomUUID(), "disabled", false,
			"dragon", 0x112233, 40, null);
		NotificationRule invalid = rule("", "dragon", null, null);

		RuleSet.CompileResult disabledResult = RuleSet.compile(Arrays.asList(disabled,
			disabledOverride));
		assertTrue(disabledResult.getErrors().isEmpty());
		RuleSet.Resolution disabledOverrides = disabledResult.getRuleSet().resolve("dragon");
		assertNull(disabledOverrides.getBackgroundRgb());
		assertNull(disabledOverrides.getOpacityPercent());
		assertFalse(disabledOverrides.isMatched());
		assertFalse(RuleSet.compile(Collections.singletonList(invalid)).getErrors().isEmpty());
	}

	@Test
	public void recordsFieldValidationDiagnosticsWithoutAddingInvalidRules()
	{
		UUID fieldId = UUID.randomUUID();
		NotificationRule fieldInvalid = new NotificationRule(fieldId, "", true, null, 0x1000000, 101,
			null);

		RuleSet.CompileResult result = RuleSet.compile(Collections.singletonList(fieldInvalid));

		assertTrue(result.getErrors().containsKey(fieldId));
		assertEquals("Name must contain 1 to 64 Unicode code points. Pattern must contain 1 to "
			+ "512 Unicode code points. Background color must be a 24-bit RGB value. Opacity "
			+ "must be between 0 and 100.", result.getErrors().get(fieldId));
		RuleSet.Resolution resultOverrides = result.getRuleSet().resolve("aaaa");
		assertNull(resultOverrides.getBackgroundRgb());
		assertNull(resultOverrides.getOpacityPercent());
		assertFalse(resultOverrides.isMatched());
	}

	@Test
	public void rejectsInvalidRuleListsAndMakesDiagnosticsImmutable()
	{
		assertIllegalArgument(() -> RuleSet.compile(null));
		assertIllegalArgument(() -> RuleSet.compile(Collections.<NotificationRule>singletonList(null)));
		assertNullPointer(() -> new NotificationRule(null, "rule", true, "pattern", 0, null,
			null));

		UUID id = UUID.randomUUID();
		NotificationRule first = new NotificationRule(id, "one", true, "one", 0, null,
			null);
		NotificationRule duplicate = new NotificationRule(id, "two", true, "two", 1, null,
			null);
		assertIllegalArgument(() -> RuleSet.compile(Arrays.asList(first, duplicate)));

		List<NotificationRule> tooMany = new ArrayList<>();
		for (int i = 0; i < 101; i++)
		{
			tooMany.add(rule("rule " + i, "pattern", i, null));
		}
		assertTrue(RuleSet.compile(tooMany.subList(0, 100)).getErrors().isEmpty());
		assertIllegalArgument(() -> RuleSet.compile(tooMany));

		Map<UUID, String> errors = RuleSet.compile(Collections.singletonList(
			rule("", "pattern", 0, null))).getErrors();
		try
		{
			errors.clear();
			fail("Expected errors to be immutable");
		}
		catch (UnsupportedOperationException expected)
		{
			assertFalse(errors.isEmpty());
		}
	}

	@Test
	public void resolvesNullMessageAsAnEmptyString()
	{
		RuleSet.CompileResult compiled = RuleSet.compile(Collections.singletonList(
			rule("any", "*", 0x112233, 40)));
		assertTrue(compiled.getErrors().isEmpty());

		RuleSet.Resolution overrides = compiled.getRuleSet().resolve(null);
		assertEquals(Integer.valueOf(0x112233), overrides.getBackgroundRgb());
		assertEquals(Integer.valueOf(40), overrides.getOpacityPercent());
		assertTrue(overrides.isMatched());
	}

	@Test
	public void resolvesEmptyRuleSetWithoutOverrides()
	{
		RuleSet empty = RuleSet.empty();
		assertSame(empty, RuleSet.empty());

		RuleSet.Resolution overrides = empty.resolve("message");
		assertNull(overrides.getBackgroundRgb());
		assertNull(overrides.getOpacityPercent());
		assertFalse(overrides.isMatched());
	}

	private static void assertIllegalArgument(Runnable action)
	{
		try
		{
			action.run();
			fail("Expected IllegalArgumentException");
		}
		catch (IllegalArgumentException expected)
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

	private static NotificationRule disabledRule(String pattern)
	{
		return new NotificationRule(UUID.randomUUID(), "disabled", false, pattern, 0, null,
			null);
	}

	private static NotificationRule rule(String name, String pattern, Integer rgb, Integer opacity)
	{
		return new NotificationRule(UUID.randomUUID(), name, true, pattern, rgb, opacity,
			null);
	}
}
