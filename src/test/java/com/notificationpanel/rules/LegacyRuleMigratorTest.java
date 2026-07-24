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
			".*first.*\r\n\r\n(\r\n",
			"#112233\r\nopacity=50\r\nhide\r\nshow");

		assertEquals(4, result.getRules().size());
		assertEquals("*first*", result.getRules().get(0).getPattern());
		assertTrue(result.getRules().get(0).isEnabled());
		assertFalse(result.getRules().get(1).isEnabled());
		assertEquals(Integer.valueOf(50), result.getRules().get(1).getOpacityPercent());
		assertFalse(result.getRules().get(2).isEnabled());
		assertFalse(result.getRules().get(3).isEnabled());
	}

	@Test
	public void keepsInteriorAndTrailingRowsAlignedAndSkipsOnlyBothEmptyRows()
	{
		RuleDocument result = migrator.migrate(
			".*one.*\n\n.*three.*\n",
			"#010203\n\nopacity=100\nshow\nhide");

		assertEquals(4, result.getRules().size());
		assertEquals("Imported rule 1", result.getRules().get(0).getName());
		assertEquals(Integer.valueOf(0x010203), result.getRules().get(0).getBackgroundRgb());
		assertEquals("Imported rule 3", result.getRules().get(1).getName());
		assertEquals("*three*", result.getRules().get(1).getPattern());
		assertEquals(Integer.valueOf(100), result.getRules().get(1).getOpacityPercent());
		assertEquals("Imported rule 4", result.getRules().get(2).getName());
		assertEquals("Imported rule 5", result.getRules().get(3).getName());
	}

	@Test
	public void parsesOnlyDocumentedAttributesIncludingOpacityEndpoints()
	{
		RuleDocument result = migrator.migrate(
			".*zero.*\n.*hundred.*",
			"#aBcDeF, opacity=0, hide\nopacity=100, show");

		NotificationRule zero = result.getRules().get(0);
		assertEquals(Integer.valueOf(0xABCDEF), zero.getBackgroundRgb());
		assertEquals(Integer.valueOf(0), zero.getOpacityPercent());
		assertFalse(zero.isEnabled());
		assertTrue(zero.getMigrationNote().contains("hide"));

		NotificationRule hundred = result.getRules().get(1);
		assertNull(hundred.getBackgroundRgb());
		assertEquals(Integer.valueOf(100), hundred.getOpacityPercent());
		assertTrue(hundred.isEnabled());
	}

	@Test
	public void keepsTheFirstDuplicateOfEachAttribute()
	{
		NotificationRule rule = migrator.migrate(".*drop.*",
			"#112233, #445566, opacity=25, opacity=75, hide, show").getRules().get(0);

		assertFalse(rule.isEnabled());
		assertEquals(Integer.valueOf(0x112233), rule.getBackgroundRgb());
		assertEquals(Integer.valueOf(25), rule.getOpacityPercent());
		assertTrue(rule.getMigrationNote().contains("hide"));
	}

	@Test
	public void trimsTokenWhitespaceButTreatsTokenNamesAsCaseSensitive()
	{
		RuleDocument trimmed = migrator.migrate(".*drop.*",
			"  #ABCDEF  ,  opacity=25  ,  show  ");
		NotificationRule valid = trimmed.getRules().get(0);
		assertTrue(valid.isEnabled());
		assertEquals(Integer.valueOf(0xABCDEF), valid.getBackgroundRgb());
		assertEquals(Integer.valueOf(25), valid.getOpacityPercent());

		NotificationRule caseMismatch = migrator.migrate(".*drop.*",
			"SHOW, Opacity=50, #abcdef").getRules().get(0);
		assertFalse(caseMismatch.isEnabled());
		assertEquals(Integer.valueOf(0xABCDEF), caseMismatch.getBackgroundRgb());
		assertNull(caseMismatch.getOpacityPercent());
		assertTrue(caseMismatch.getMigrationNote().contains("SHOW"));
		assertTrue(caseMismatch.getMigrationNote().contains("Opacity=50"));
	}

	@Test
	public void separatorNoiseIsIgnoredTheWayItUsedToBe()
	{
		// Options were split on comma or whitespace and anything unparseable was skipped, so a
		// doubled separator or padding never cost the user their rule.
		for (String format : new String[]{"show,,#112233", "  #112233  ,  show  ",
			"#112233 opacity=40", "show\t#112233"})
		{
			NotificationRule rule = migrator.migrate(".*drop.*", format).getRules().get(0);
			assertTrue(format, rule.isEnabled());
			assertEquals(format, Integer.valueOf(0x112233), rule.getBackgroundRgb());
		}
		assertEquals(Integer.valueOf(40),
			migrator.migrate(".*drop.*", "#112233 opacity=40").getRules().get(0)
				.getOpacityPercent());
	}

	@Test
	public void acceptsTheColourAndOpacityFormsThatUsedToWork()
	{
		// Colours were decoded with Color.decode and opacity was clamped, so these all worked.
		assertEquals(Integer.valueOf(0xBF616A), migrator.migrate(".*drop.*", "0xbf616a")
			.getRules().get(0).getBackgroundRgb());
		assertEquals(Integer.valueOf(0x0000FF), migrator.migrate(".*drop.*", "255")
			.getRules().get(0).getBackgroundRgb());
		assertEquals(Integer.valueOf(100), migrator.migrate(".*drop.*", "opacity=150")
			.getRules().get(0).getOpacityPercent());
		assertTrue(migrator.migrate(".*drop.*", "opacity=150").getRules().get(0).isEnabled());
	}

	@Test
	public void retainsValidAttributesButDisablesAndAnnotatesEveryInvalidToken()
	{
		RuleDocument result = migrator.migrate(".*drop.*",
			"#112233, opacity=101, duration=3, showTime=false, mystery, hide");
		NotificationRule rule = result.getRules().get(0);

		assertFalse(rule.isEnabled());
		assertEquals(Integer.valueOf(0x112233), rule.getBackgroundRgb());
		// Out-of-range opacity was clamped rather than rejected, so it is not a problem.
		assertEquals(Integer.valueOf(100), rule.getOpacityPercent());
		assertTrue(rule.getMigrationNote(), rule.getMigrationNote().contains("duration=3"));
		assertTrue(rule.getMigrationNote(), rule.getMigrationNote().contains("showTime=false"));
		assertTrue(rule.getMigrationNote(), rule.getMigrationNote().contains("mystery"));
	}

	@Test
	public void annotatesInvalidColorAndOpacityWhileRetainingOtherAttributes()
	{
		RuleDocument result = migrator.migrate(".*color.*\n.*opacity.*",
			"#12345G, opacity=50\nopacity=no, show");

		NotificationRule color = result.getRules().get(0);
		assertFalse(color.isEnabled());
		assertNull(color.getBackgroundRgb());
		assertEquals(Integer.valueOf(50), color.getOpacityPercent());
		assertTrue(color.getMigrationNote().contains("#12345G"));

		NotificationRule opacity = result.getRules().get(1);
		assertFalse(opacity.isEnabled());
		assertNull(opacity.getOpacityPercent());
		assertTrue(opacity.getMigrationNote().contains("opacity=no"));
	}

	@Test
	public void disablesMissingPatternsAndInvalidTokensButKeepsPatternOnlyRules()
	{
		RuleDocument result = migrator.migrate("\n.*pattern.*\n.*other.*", "show\n\nunknown");

		assertEquals(3, result.getRules().size());
		assertFalse(result.getRules().get(0).isEnabled());
		assertTrue(result.getRules().get(0).getMigrationNote().contains("missing"));
		assertTrue(result.getRules().get(1).isEnabled());
		assertEquals("*pattern*", result.getRules().get(1).getPattern());
		assertFalse(result.getRules().get(2).isEnabled());
		assertTrue(result.getRules().get(2).getMigrationNote().contains("unknown"));
	}

	@Test
	public void convertsRegexWildcardsToGlobsAndDisablesUnconvertiblePatterns()
	{
		RuleDocument result = migrator.migrate("  valid.*  \n(", "show\n#112233");

		assertEquals("  valid*  ", result.getRules().get(0).getPattern());
		assertFalse(result.getRules().get(1).isEnabled());
		assertTrue(result.getRules().get(1).getMigrationNote().contains("wildcard"));
		assertEquals(Integer.valueOf(0x112233), result.getRules().get(1).getBackgroundRgb());
	}

	@Test
	public void convertsWildcardDialectsStripsAnchorsAndFlagsEmptyResults()
	{
		RuleDocument result = migrator.migrate(
			"^exact$\n.+drop.+\nlevel .\n^", "show\nshow\nshow\nshow");

		// Converted correctly, but only the one that already matched anywhere stays on.
		assertEquals("exact", result.getRules().get(0).getPattern());
		assertTrue(result.getRules().get(0).isEnabled());
		assertEquals("*drop*", result.getRules().get(1).getPattern());
		assertTrue(result.getRules().get(1).isEnabled());
		assertEquals("level *", result.getRules().get(2).getPattern());
		assertFalse(result.getRules().get(2).isEnabled());
		assertFalse(result.getRules().get(3).isEnabled());
		assertTrue(result.getRules().get(3).getMigrationNote().contains("empty wildcard"));
	}

	@Test
	public void advisesSplittingAlternationPatterns()
	{
		NotificationRule rule = migrator.migrate("Zulrah|Vorkath", "#ff0000").getRules().get(0);

		assertFalse(rule.isEnabled());
		assertTrue(rule.getMigrationNote().contains("split"));
	}

	@Test
	public void collapsesConsecutiveWildcardsFromRepeatedDots()
	{
		assertEquals("say*done",
			migrator.migrate("say...done", "#ff0000").getRules().get(0).getPattern());
		assertEquals("gazes upon you*",
			migrator.migrate("gazes upon you...", "#ff0000").getRules().get(0).getPattern());
	}

	@Test
	public void disablesPatternsThatLoneDotsCollapseIntoAMatchEverythingWildcard()
	{
		// "." matched exactly one character, so importing these enabled would silently format
		// every notification and defeat an allowlist built on "Show notifications by default".
		for (String catchAll : new String[]{".", "..", "...", "^...$"})
		{
			NotificationRule rule = migrator.migrate(catchAll, "#ff0000").getRules().get(0);
			assertFalse(catchAll, rule.isEnabled());
			assertTrue(catchAll, rule.getMigrationNote().contains("match every notification"));
			// The original text is kept so the user can see what they wrote and rewrite it.
			assertEquals(catchAll, catchAll, rule.getPattern());
		}
	}

	@Test
	public void keepsAFaithfulCatchAllEnabledBecauseItAlreadyMatchedEverything()
	{
		for (String catchAll : new String[]{".*", ".+", ".*.*"})
		{
			NotificationRule rule = migrator.migrate(catchAll, "#ff0000").getRules().get(0);
			assertTrue(catchAll, rule.isEnabled());
			assertEquals(catchAll, "*", rule.getPattern());
			assertNull(catchAll, rule.getMigrationNote());
		}
	}

	@Test
	public void convertsPatternsExactlyWhereverItCan()
	{
		// Both engines match against the whole message, so anchors are noise and a leading or
		// trailing .* is exactly what a * means. These all keep their meaning and stay on.
		for (String[] pair : new String[][]{
			{"Slayer", "Slayer"}, {"^Slayer$", "Slayer"}, {".*Slayer.*", "*Slayer*"},
			{".*Slayer", "*Slayer"}, {"Slayer.*", "Slayer*"}, {".*", "*"}})
		{
			NotificationRule rule = migrator.migrate(pair[0], "#ff0000").getRules().get(0);
			assertTrue(pair[0], rule.isEnabled());
			assertEquals(pair[0], pair[1], rule.getPattern());
			assertNull(pair[0], rule.getMigrationNote());
		}
	}

	@Test
	public void turnsOffTheOneTranslationThatCannotBeExact()
	{
		// "." matched a single character and "*" matches any run, so this is the only conversion
		// that changes which messages match. It keeps its converted text, so turning it on is
		// all that is needed once the user has seen it.
		NotificationRule loneDot = migrator.migrate("level .", "#ff0000").getRules().get(0);
		assertFalse(loneDot.isEnabled());
		assertEquals("level *", loneDot.getPattern());
		assertTrue(loneDot.getMigrationNote(), loneDot.getMigrationNote()
			.startsWith(LegacyRuleMigrator.WIDENED_NOTE_PREFIX));
	}

	@Test
	public void aRewritableProblemOutranksAWideningInTheNote()
	{
		NotificationRule rule = migrator.migrate("^level .$", "mystery").getRules().get(0);

		assertFalse(rule.isEnabled());
		// Needing a rewrite is the more demanding outcome, so it sets the prefix the editor
		// counts on.
		assertTrue(rule.getMigrationNote(), rule.getMigrationNote()
			.startsWith(LegacyRuleMigrator.PROBLEM_NOTE_PREFIX));
		assertTrue(rule.getMigrationNote(), rule.getMigrationNote().contains("mystery"));
		assertTrue(rule.getMigrationNote(), rule.getMigrationNote().contains("single character"));
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
		String exactPatterns = ".*pattern.*\n".repeat(100)
			+ "x".repeat(262_144 - ".*pattern.*\n".length() * 100);
		assertEquals(262_144, exactPatterns.length());
		RuleDocument patternResult = migrator.migrate(exactPatterns, "show");
		assertEquals(100, patternResult.getRules().size());
		assertFalse(patternResult.getMigrationWarnings().contains(
			"Legacy rule configuration exceeded 262144 characters and was not migrated."));

		String exactFormats = "show" + " ".repeat(262_144 - "show".length());
		assertEquals(262_144, exactFormats.length());
		RuleDocument formatResult = migrator.migrate(".*pattern.*", exactFormats);
		assertEquals(1, formatResult.getRules().size());
		assertTrue(formatResult.getRules().get(0).isEnabled());
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
