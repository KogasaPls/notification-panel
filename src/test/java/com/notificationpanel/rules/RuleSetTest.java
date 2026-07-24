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
		NotificationRule color = rule("color", "dragon", 0x112233, null,
			NotificationRule.Visibility.INHERIT);
		NotificationRule opacity = rule("opacity", "warhammer", null, 40,
			NotificationRule.Visibility.HIDE);
		NotificationRule later = rule("later", "warhammer", 0xFFFFFF, 80,
			NotificationRule.Visibility.SHOW);

		RuleSet.CompileResult compiled = RuleSet.compile(Arrays.asList(color, opacity, later));
		RuleSet.Overrides result = compiled.getRuleSet()
			.resolve("You received a dragon warhammer.");

		assertTrue(compiled.getErrors().isEmpty());
		assertEquals(Integer.valueOf(0x112233), result.getBackgroundRgb());
		assertEquals(Integer.valueOf(40), result.getOpacityPercent());
		assertEquals(Boolean.FALSE, result.getVisible());
	}

	@Test
	public void excludesDisabledAndInvalidEnabledRules()
	{
		NotificationRule disabled = disabledRule("(a)\\1");
		NotificationRule invalid = rule("invalid", "(a)\\1", 0x112233, null,
			NotificationRule.Visibility.INHERIT);

		assertTrue(RuleSet.compile(Collections.singletonList(disabled)).getErrors().isEmpty());
		assertFalse(RuleSet.compile(Collections.singletonList(invalid)).getErrors().isEmpty());
	}

	@Test
	public void recordsValidationAndRegexDiagnosticsWithoutAddingInvalidRules()
	{
		UUID fieldId = UUID.randomUUID();
		UUID regexId = UUID.randomUUID();
		NotificationRule fieldInvalid = new NotificationRule(fieldId, "", true, null, 0x1000000, 101,
			NotificationRule.Visibility.INHERIT, null);
		NotificationRule regexInvalid = new NotificationRule(regexId, "regex", true, "(a)\\1",
			0, null, NotificationRule.Visibility.INHERIT, null);

		RuleSet.CompileResult result = RuleSet.compile(Arrays.asList(fieldInvalid, regexInvalid));

		assertEquals("Name must contain 1 to 64 Unicode code points. Pattern must contain 1 to "
			+ "512 Unicode code points. Background color must be a 24-bit RGB value. Opacity "
			+ "must be between 0 and 100.", result.getErrors().get(fieldId));
		assertFalse(result.getErrors().get(regexId).isEmpty());
		RuleSet.Overrides resultOverrides = result.getRuleSet().resolve("aaaa");
		assertNull(resultOverrides.getBackgroundRgb());
	}

	@Test
	public void rejectsInvalidRuleListsAndMakesDiagnosticsImmutable()
	{
		assertIllegalArgument(() -> RuleSet.compile(null));

		UUID id = UUID.randomUUID();
		NotificationRule first = new NotificationRule(id, "one", true, "one", 0, null,
			NotificationRule.Visibility.INHERIT, null);
		NotificationRule duplicate = new NotificationRule(id, "two", true, "two", 1, null,
			NotificationRule.Visibility.INHERIT, null);
		assertIllegalArgument(() -> RuleSet.compile(Arrays.asList(first, duplicate)));

		List<NotificationRule> tooMany = new ArrayList<>();
		for (int i = 0; i < 101; i++)
		{
			tooMany.add(rule("rule " + i, "pattern", i, null,
				NotificationRule.Visibility.INHERIT));
		}
		assertIllegalArgument(() -> RuleSet.compile(tooMany));

		Map<UUID, String> errors = RuleSet.compile(Collections.singletonList(
			rule("", "pattern", 0, null, NotificationRule.Visibility.INHERIT))).getErrors();
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
	public void resolvesEmptyRulesAndNullMessageToNoOverrides()
	{
		RuleSet empty = RuleSet.empty();

		assertSame(empty, RuleSet.empty());
		RuleSet.Overrides overrides = empty.resolve(null);
		assertNull(overrides.getBackgroundRgb());
		assertNull(overrides.getOpacityPercent());
		assertNull(overrides.getVisible());
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

	private static NotificationRule disabledRule(String pattern)
	{
		return new NotificationRule(UUID.randomUUID(), "disabled", false, pattern, 0, null,
			NotificationRule.Visibility.INHERIT, null);
	}

	private static NotificationRule rule(String name, String pattern, Integer rgb, Integer opacity,
		NotificationRule.Visibility visibility)
	{
		return new NotificationRule(UUID.randomUUID(), name, true, pattern, rgb, opacity,
			visibility, null);
	}
}
