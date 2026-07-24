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

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LegacyRuleMigratorTest
{
	private final LegacyRuleMigrator migrator = new LegacyRuleMigrator();

	@Test
	public void preservesRowsAndValidAttributesWhileAnnotatingProblems()
	{
		RuleDocument result = migrator.migrate(
			"first\r\n\r\n(a)\\1\r\n",
			"#112233\r\nopacity=50\r\nhide\r\nshow");

		assertEquals(4, result.getRules().size());
		assertEquals("first", result.getRules().get(0).getPattern());
		assertTrue(result.getRules().get(0).isEnabled());
		assertFalse(result.getRules().get(1).isEnabled());
		assertEquals(Integer.valueOf(50), result.getRules().get(1).getOpacityPercent());
		assertFalse(result.getRules().get(2).isEnabled());
		assertEquals(NotificationRule.Visibility.HIDE,
			result.getRules().get(2).getVisibility());
		assertFalse(result.getRules().get(3).isEnabled());
		assertEquals(NotificationRule.Visibility.SHOW,
			result.getRules().get(3).getVisibility());
	}

	@Test
	public void keepsInteriorAndTrailingRowsAlignedAndSkipsOnlyBothEmptyRows()
	{
		RuleDocument result = migrator.migrate(
			"one\n\nthree\n",
			"#010203\n\nopacity=100\nshow\nhide");

		assertEquals(4, result.getRules().size());
		assertEquals("Imported rule 1", result.getRules().get(0).getName());
		assertEquals(Integer.valueOf(0x010203), result.getRules().get(0).getBackgroundRgb());
		assertEquals("Imported rule 3", result.getRules().get(1).getName());
		assertEquals("three", result.getRules().get(1).getPattern());
		assertEquals(Integer.valueOf(100), result.getRules().get(1).getOpacityPercent());
		assertEquals("Imported rule 4", result.getRules().get(2).getName());
		assertEquals(NotificationRule.Visibility.SHOW,
			result.getRules().get(2).getVisibility());
		assertEquals("Imported rule 5", result.getRules().get(3).getName());
		assertEquals(NotificationRule.Visibility.HIDE,
			result.getRules().get(3).getVisibility());
	}

	@Test
	public void parsesOnlyDocumentedAttributesIncludingOpacityEndpoints()
	{
		RuleDocument result = migrator.migrate(
			"zero\nhundred",
			"#aBcDeF, opacity=0, hide\nopacity=100, show");

		NotificationRule zero = result.getRules().get(0);
		assertEquals(Integer.valueOf(0xABCDEF), zero.getBackgroundRgb());
		assertEquals(Integer.valueOf(0), zero.getOpacityPercent());
		assertEquals(NotificationRule.Visibility.HIDE, zero.getVisibility());
		assertTrue(zero.isEnabled());

		NotificationRule hundred = result.getRules().get(1);
		assertNull(hundred.getBackgroundRgb());
		assertEquals(Integer.valueOf(100), hundred.getOpacityPercent());
		assertEquals(NotificationRule.Visibility.SHOW, hundred.getVisibility());
		assertTrue(hundred.isEnabled());
	}

	@Test
	public void keepsTheFirstDuplicateOfEachAttribute()
	{
		NotificationRule rule = migrator.migrate("drop",
			"#112233, #445566, opacity=25, opacity=75, hide, show").getRules().get(0);

		assertTrue(rule.isEnabled());
		assertEquals(Integer.valueOf(0x112233), rule.getBackgroundRgb());
		assertEquals(Integer.valueOf(25), rule.getOpacityPercent());
		assertEquals(NotificationRule.Visibility.HIDE, rule.getVisibility());
	}

	@Test
	public void trimsTokenWhitespaceButTreatsTokenNamesAsCaseSensitive()
	{
		RuleDocument trimmed = migrator.migrate("drop",
			"  #ABCDEF  ,  opacity=25  ,  show  ");
		NotificationRule valid = trimmed.getRules().get(0);
		assertTrue(valid.isEnabled());
		assertEquals(Integer.valueOf(0xABCDEF), valid.getBackgroundRgb());
		assertEquals(Integer.valueOf(25), valid.getOpacityPercent());
		assertEquals(NotificationRule.Visibility.SHOW, valid.getVisibility());

		NotificationRule caseMismatch = migrator.migrate("drop",
			"SHOW, Opacity=50, #abcdef").getRules().get(0);
		assertFalse(caseMismatch.isEnabled());
		assertEquals(Integer.valueOf(0xABCDEF), caseMismatch.getBackgroundRgb());
		assertNull(caseMismatch.getOpacityPercent());
		assertEquals(NotificationRule.Visibility.INHERIT, caseMismatch.getVisibility());
		assertTrue(caseMismatch.getMigrationNote().contains("SHOW"));
		assertTrue(caseMismatch.getMigrationNote().contains("Opacity=50"));
	}

	@Test
	public void embeddedEmptyTokenDisablesRowWithoutDiscardingValidAttributes()
	{
		NotificationRule rule = migrator.migrate("drop", "show,,#112233").getRules().get(0);

		assertFalse(rule.isEnabled());
		assertEquals(Integer.valueOf(0x112233), rule.getBackgroundRgb());
		assertEquals(NotificationRule.Visibility.SHOW, rule.getVisibility());
		assertTrue(rule.getMigrationNote().contains("Invalid legacy token: ."));
	}

	@Test
	public void retainsValidAttributesButDisablesAndAnnotatesEveryInvalidToken()
	{
		RuleDocument result = migrator.migrate("drop",
			"#112233, opacity=101, duration=3, showTime=false, mystery, hide");
		NotificationRule rule = result.getRules().get(0);

		assertFalse(rule.isEnabled());
		assertEquals(Integer.valueOf(0x112233), rule.getBackgroundRgb());
		assertNull(rule.getOpacityPercent());
		assertEquals(NotificationRule.Visibility.HIDE, rule.getVisibility());
		assertTrue(rule.getMigrationNote(), rule.getMigrationNote().contains("opacity=101"));
		assertTrue(rule.getMigrationNote(), rule.getMigrationNote().contains("duration=3"));
		assertTrue(rule.getMigrationNote(), rule.getMigrationNote().contains("showTime=false"));
		assertTrue(rule.getMigrationNote(), rule.getMigrationNote().contains("mystery"));
	}

	@Test
	public void annotatesInvalidColorAndOpacityWhileRetainingOtherAttributes()
	{
		RuleDocument result = migrator.migrate("color\nopacity",
			"#12345G, opacity=50\nopacity=no, show");

		NotificationRule color = result.getRules().get(0);
		assertFalse(color.isEnabled());
		assertNull(color.getBackgroundRgb());
		assertEquals(Integer.valueOf(50), color.getOpacityPercent());
		assertTrue(color.getMigrationNote().contains("#12345G"));

		NotificationRule opacity = result.getRules().get(1);
		assertFalse(opacity.isEnabled());
		assertNull(opacity.getOpacityPercent());
		assertEquals(NotificationRule.Visibility.SHOW, opacity.getVisibility());
		assertTrue(opacity.getMigrationNote().contains("opacity=no"));
	}

	@Test
	public void disablesMissingPatternsAndRowsWithoutRecognizedAttributes()
	{
		RuleDocument result = migrator.migrate("\npattern\nother", "show\n\nunknown");

		assertEquals(3, result.getRules().size());
		assertFalse(result.getRules().get(0).isEnabled());
		assertTrue(result.getRules().get(0).getMigrationNote().contains("missing"));
		assertFalse(result.getRules().get(1).isEnabled());
		assertTrue(result.getRules().get(1).getMigrationNote().contains(
			"No recognized formatting attributes"));
		assertFalse(result.getRules().get(2).isEnabled());
		assertTrue(result.getRules().get(2).getMigrationNote().contains("unknown"));
	}

	@Test
	public void checksPatternsWithRegexAndPreservesOriginalPatternText()
	{
		RuleDocument result = migrator.migrate("  valid.*  \n(a)\\1", "show\n#112233");

		assertEquals("  valid.*  ", result.getRules().get(0).getPattern());
		assertTrue(result.getRules().get(0).isEnabled());
		assertFalse(result.getRules().get(1).isEnabled());
		assertTrue(result.getRules().get(1).getMigrationNote().contains("regex"));
		assertEquals(Integer.valueOf(0x112233), result.getRules().get(1).getBackgroundRgb());
	}

	@Test
	public void assignsDeterministicIdentityFromSourceRowAndValues()
	{
		String pattern = "dragon";
		String format = "#112233";
		UUID expected = UUID.nameUUIDFromBytes(
			("notificationpanel-legacy-0\n" + pattern + "\n" + format)
				.getBytes(StandardCharsets.UTF_8));

		RuleDocument first = migrator.migrate(pattern, format);
		RuleDocument second = migrator.migrate(pattern, format);

		assertEquals(expected, first.getRules().get(0).getId());
		assertEquals(first, second);
	}

	@Test
	public void capsMigrationAtOneHundredRows()
	{
		String rows = String.join("\n", Collections.nCopies(101, "drop"));
		String formats = String.join("\n", Collections.nCopies(101, "hide"));
		RuleDocument result = migrator.migrate(rows, formats);

		assertEquals(100, result.getRules().size());
		assertEquals(Collections.singletonList(
			"Only the first 100 legacy rules were migrated."), result.getMigrationWarnings());
	}

	@Test
	public void countsCapAfterSkippingBothEmptyRows()
	{
		String rows = "\n" + String.join("\n", Collections.nCopies(101, "drop"));
		String formats = "\n" + String.join("\n", Collections.nCopies(101, "hide"));
		RuleDocument result = migrator.migrate(rows, formats);

		assertEquals(100, result.getRules().size());
		assertEquals("Imported rule 2", result.getRules().get(0).getName());
		assertEquals("Imported rule 101", result.getRules().get(99).getName());
		assertEquals(Collections.singletonList(
			"Only the first 100 legacy rules were migrated."), result.getMigrationWarnings());
	}

	@Test
	public void rejectsEitherOversizedLegacyValueBeforeSplitting()
	{
		List<String> expectedWarning = Collections.singletonList(
			"Legacy rule configuration exceeded 262144 characters and was not migrated.");

		RuleDocument oversizedPatterns = migrator.migrate("x".repeat(262_145), "show");
		RuleDocument oversizedFormats = migrator.migrate("pattern", "x".repeat(262_145));

		assertTrue(oversizedPatterns.getRules().isEmpty());
		assertEquals(expectedWarning, oversizedPatterns.getMigrationWarnings());
		assertTrue(oversizedFormats.getRules().isEmpty());
		assertEquals(expectedWarning, oversizedFormats.getMigrationWarnings());
	}

	@Test
	public void processesEachLegacyValueAtExactLengthLimit()
	{
		String exactPatterns = "pattern\n".repeat(100)
			+ "x".repeat(262_144 - "pattern\n".length() * 100);
		assertEquals(262_144, exactPatterns.length());
		RuleDocument patternResult = migrator.migrate(exactPatterns, "show");
		assertEquals(100, patternResult.getRules().size());
		assertFalse(patternResult.getMigrationWarnings().contains(
			"Legacy rule configuration exceeded 262144 characters and was not migrated."));

		String exactFormats = "show" + " ".repeat(262_144 - "show".length());
		assertEquals(262_144, exactFormats.length());
		RuleDocument formatResult = migrator.migrate("pattern", exactFormats);
		assertEquals(1, formatResult.getRules().size());
		assertTrue(formatResult.getRules().get(0).isEnabled());
		assertEquals(NotificationRule.Visibility.SHOW,
			formatResult.getRules().get(0).getVisibility());
		assertFalse(formatResult.getMigrationWarnings().contains(
			"Legacy rule configuration exceeded 262144 characters and was not migrated."));
	}

	@Test
	public void treatsNullLegacyValuesAsEmptyLists()
	{
		assertTrue(migrator.migrate(null, null).getRules().isEmpty());
		assertEquals(1, migrator.migrate("pattern", null).getRules().size());
		assertEquals(1, migrator.migrate(null, "show").getRules().size());
	}
}
