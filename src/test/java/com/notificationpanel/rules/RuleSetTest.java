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
	public void keepsScanningForAnAttributeALaterRuleStillSupplies()
	{
		// Resolution stops once no remaining rule can change the answer. A set whose colour comes
		// from the first match and whose opacity comes from a much later one is exactly what a
		// too-eager stop would break, and the gap is what makes the failure visible.
		List<NotificationRule> rules = new ArrayList<>();
		rules.add(rule("colour", "*drop*", 0x112233, null));
		for (int index = 0; index < 50; index++)
		{
			rules.add(rule("filler" + index, "*drop*", null, null));
		}
		rules.add(rule("opacity", "*drop*", 0xFFFFFF, 40));

		RuleSet.Resolution result = RuleSet.compile(rules).getRuleSet().resolve("a drop here");

		assertEquals(Integer.valueOf(0x112233), result.getBackgroundRgb());
		assertEquals(Integer.valueOf(40), result.getOpacityPercent());
		assertTrue(result.isMatched());
	}

	@Test
	public void resolvesTheSameWhenNoRuleOverridesAnAttribute()
	{
		// Nothing in this set can supply an opacity, so resolution stops at the first match rather
		// than running every rule waiting for one. The answer must be what it always was.
		RuleSet colourOnly = RuleSet.compile(Arrays.asList(
			rule("first", "*drop*", 0x112233, null),
			rule("second", "*drop*", 0x445566, null))).getRuleSet();

		RuleSet.Resolution result = colourOnly.resolve("a drop here");

		assertEquals(Integer.valueOf(0x112233), result.getBackgroundRgb());
		assertNull(result.getOpacityPercent());
		assertTrue(result.isMatched());

		// A set that overrides nothing at all stops on the first match too, and still has to report
		// it -- that flag is what an allowlist configuration runs on.
		RuleSet.Resolution bare = RuleSet.compile(Collections.singletonList(
			rule("bare", "*drop*", null, null))).getRuleSet().resolve("a drop here");

		assertNull(bare.getBackgroundRgb());
		assertNull(bare.getOpacityPercent());
		assertTrue(bare.isMatched());
	}

	@Test
	public void takesVisibilityFromTheFirstRuleThatSetsIt()
	{
		NotificationRule undecided = rule("undecided", "*drop*", 0x112233, null, null);
		NotificationRule hide = rule("hide", "*drop*", null, null, Visibility.HIDE);
		NotificationRule show = rule("show", "*drop*", null, null, Visibility.SHOW);

		RuleSet.Resolution result = RuleSet.compile(Arrays.asList(undecided, hide, show))
			.getRuleSet().resolve("a drop here");

		assertEquals(Visibility.HIDE, result.getVisibility());
		assertEquals(Integer.valueOf(0x112233), result.getBackgroundRgb());
		assertTrue(result.isMatched());
	}

	@Test
	public void reachesAHideRuleBelowARuleThatSettlesColourAndOpacity()
	{
		// The regression the early exit invites: colour and opacity are both taken from the first
		// rule, so a stop that only counts those two never looks at the rule that hides. The bug is
		// silent -- the notification simply shows -- and appears only in this ordering.
		NotificationRule formatting = rule("formatting", "*drop*", 0x112233, 40, null);
		NotificationRule hide = rule("hide", "*drop*", null, null, Visibility.HIDE);

		RuleSet.Resolution result = RuleSet.compile(Arrays.asList(formatting, hide))
			.getRuleSet().resolve("a drop here");

		assertEquals(Visibility.HIDE, result.getVisibility());
	}

	@Test
	public void resolvesVisibilityAsUndecidedWhenNothingSetsIt()
	{
		// A set that cannot supply a visibility stops on the first match, and reports the attribute
		// as undecided rather than guessing -- deciding is the caller's job, from the global default.
		RuleSet.Resolution matched = RuleSet.compile(Arrays.asList(
			rule("first", "*drop*", 0x112233, 40, null),
			rule("second", "*drop*", 0x445566, 50, null))).getRuleSet().resolve("a drop here");

		assertNull(matched.getVisibility());
		assertTrue(matched.isMatched());

		RuleSet.Resolution unmatched = RuleSet.compile(Collections.singletonList(
			rule("hide", "*drop*", null, null, Visibility.HIDE))).getRuleSet().resolve("nothing here");

		assertNull(unmatched.getVisibility());
		assertFalse(unmatched.isMatched());
		assertNull(RuleSet.empty().resolve("anything").getVisibility());
	}

	@Test
	public void resolvesEachVisibilityValueFromTheFirstRuleThatSetsIt()
	{
		RuleSet rules = RuleSet.compile(Arrays.asList(
			rule("colour only", "*shark*", 0xBF616A, null),
			rule("sidebar", "*shark*", null, null, Visibility.SIDEBAR),
			rule("hide", "*shark*", null, null, Visibility.HIDE))).getRuleSet();

		RuleSet.Resolution resolution = rules.resolve("You catch a shark.");

		assertEquals(Visibility.SIDEBAR, resolution.getVisibility());
		assertEquals(Integer.valueOf(0xBF616A), resolution.getBackgroundRgb());
		assertTrue(resolution.isMatched());
	}

	@Test
	public void ignoresVisibilityFromDisabledRules()
	{
		NotificationRule disabledHide = new NotificationRule(UUID.randomUUID(), "disabled", false,
			"*drop*", null, null, Visibility.HIDE, null);
		NotificationRule colour = rule("colour", "*drop*", 0x112233, null, null);

		RuleSet.Resolution result = RuleSet.compile(Arrays.asList(disabledHide, colour))
			.getRuleSet().resolve("a drop here");

		assertNull(result.getVisibility());
		assertEquals(Integer.valueOf(0x112233), result.getBackgroundRgb());
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
			"dragon", 0x112233, 40, null, null);
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
			null, null);

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
		assertNullPointer(() -> new NotificationRule(null, "rule", true, "pattern", 0, null, null,
			null));

		UUID id = UUID.randomUUID();
		NotificationRule first = new NotificationRule(id, "one", true, "one", 0, null, null,
			null);
		NotificationRule duplicate = new NotificationRule(id, "two", true, "two", 1, null, null,
			null);
		assertIllegalArgument(() -> RuleSet.compile(Arrays.asList(first, duplicate)));

		List<NotificationRule> tooMany = new ArrayList<>();
		for (int i = 0; i < RuleSet.MAX_RULES + 1; i++)
		{
			tooMany.add(rule("rule " + i, "pattern", i, null));
		}
		assertTrue(RuleSet.compile(tooMany.subList(0, RuleSet.MAX_RULES)).getErrors().isEmpty());
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
		return new NotificationRule(UUID.randomUUID(), "disabled", false, pattern, 0, null, null,
			null);
	}

	private static NotificationRule rule(String name, String pattern, Integer rgb, Integer opacity)
	{
		return rule(name, pattern, rgb, opacity, null);
	}

	private static NotificationRule rule(String name, String pattern, Integer rgb, Integer opacity,
		Visibility visibility)
	{
		return new NotificationRule(UUID.randomUUID(), name, true, pattern, rgb, opacity,
			visibility, null);
	}
}
