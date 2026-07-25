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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class LegacyRuleMigrator
{
	private static final String OVERSIZED_WARNING =
		"Legacy rule configuration exceeded " + RuleCodec.MAX_CONFIG_LENGTH
			+ " characters and was not migrated.";
	private static final String CAPPED_WARNING =
		"Only the first " + RuleSet.MAX_RULES + " legacy rules were migrated.";
	private static final String UNPAIRED_WARNING =
		"The Regex and Options lists had different numbers of rows. The rows past the end of the "
			+ "shorter list never applied and were turned off.";
	/** Prefixes the editor uses to tell the two disabling outcomes apart. */
	public static final String PROBLEM_NOTE_PREFIX = "Legacy migration problems: ";
	/**
	 * The problem the 2.0 import recorded for a legacy {@code hide} token, back when a rule could
	 * not hide anything.
	 *
	 * <p>Nothing in this class produces it any more — {@code hide} now sets {@code visible} on the
	 * imported rule instead of disabling it. The constant survives because {@link RuleCodec} still
	 * reads it out of stored notes when it upgrades a schema version 1 document written by that
	 * older import: a rule disabled only for this is re-enabled with {@code visible} set to false.
	 * Producer and consumer shared the constant so the two could not drift apart, and the consumer
	 * still needs it, so do not delete this as unused.</p>
	 */
	public static final String LEGACY_HIDE_PROBLEM =
		"Per-rule hide is no longer supported; remove this rule or turn "
			+ "off \"Show notifications by default\".";
	public static final String WIDENED_NOTE_PREFIX =
		"Turned off because it now matches more than it used to: ";

	public RuleDocument migrate(String patternValue, String formatValue)
	{
		if (isOversized(patternValue) || isOversized(formatValue))
		{
			return new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION,
				Collections.singletonList(OVERSIZED_WARNING), Collections.emptyList());
		}

		// The old plugin paired the two lists by index, but it collapsed runs of blank lines in the
		// Regex list and only there, then stopped at the shorter of the two. Splitting both lists
		// the same way would slide every colour after a blank line onto the following pattern, so
		// the asymmetry is reproduced deliberately instead of being tidied up.
		String[] patterns = (patternValue == null ? "" : patternValue).split("\\R+", -1);
		String[] formats = (formatValue == null ? "" : formatValue).split("\\R", -1);
		int pairedCount = Math.min(patterns.length, formats.length);
		int rowCount = Math.max(patterns.length, formats.length);
		List<NotificationRule> rules = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		boolean importedUnpairedRow = false;
		for (int row = 0; row < rowCount; row++)
		{
			String pattern = row < patterns.length ? patterns[row] : "";
			String format = row < formats.length ? formats[row] : "";
			if (pattern.trim().isEmpty() && format.trim().isEmpty())
			{
				continue;
			}
			if (rules.size() == RuleSet.MAX_RULES)
			{
				warnings.add(CAPPED_WARNING);
				break;
			}
			// A row past the end of the shorter list never applied, so it must not arrive enabled.
			// Only a row that still has a pattern needs telling: one without a pattern is already
			// disabled for that reason.
			boolean unpaired = row >= pairedCount && !pattern.trim().isEmpty();
			importedUnpairedRow |= unpaired;
			rules.add(migrateRow(row, pattern, format, unpaired));
		}
		if (importedUnpairedRow)
		{
			warnings.add(UNPAIRED_WARNING);
		}
		return new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION, warnings, rules);
	}

	private static NotificationRule migrateRow(int row, String pattern, String format,
		boolean unpaired)
	{
		// Two kinds of failure, both disabling. A problem has no working conversion and keeps the
		// original text for the user to rewrite. A widening converted cleanly but matches a larger
		// set of messages than the regex did, so it keeps the converted text and only needs the
		// user to agree to it.
		List<String> problems = new ArrayList<>();
		List<String> widenings = new ArrayList<>();
		if (unpaired)
		{
			problems.add("The Regex list had more rows than the Options list, so this row never "
				+ "applied.");
		}
		String glob = pattern;
		if (pattern.trim().isEmpty())
		{
			problems.add("Pattern is missing.");
		}
		else
		{
			if (pattern.codePointCount(0, pattern.length()) > 512)
			{
				problems.add("Pattern exceeds 512 Unicode code points.");
			}
			Conversion converted = regexToWildcard(pattern);
			if (converted == null)
			{
				if (pattern.indexOf('|') >= 0)
				{
					problems.add("Wildcards can't combine alternatives; split each option "
						+ "into its own rule.");
				}
				else
				{
					problems.add("Pattern uses unsupported syntax; rewrite it with the "
						+ "* wildcard.");
				}
			}
			else if (converted.wildcard.isEmpty())
			{
				problems.add("Pattern reduced to an empty wildcard; rewrite it with the "
					+ "* wildcard.");
			}
			else if (converted.widenedLoneDot && "*".equals(converted.wildcard))
			{
				// "." matched exactly one character, so a pattern built only from lone dots
				// collapses to a wildcard that matches every notification. Importing that
				// enabled would silently format everything, so leave it for the user to fix.
				problems.add("Pattern reduced to \"*\", which would match every notification; "
					+ "rewrite it to match only the messages you want.");
			}
			else
			{
				glob = converted.wildcard;
				if (converted.widenedLoneDot)
				{
					widenings.add("A \".\" that matched a single character became \"*\", which "
						+ "matches any run of characters.");
				}
			}
		}

		ParsedFormat parsed = parseFormat(format, problems);
		boolean enabled = problems.isEmpty() && widenings.isEmpty();
		UUID id = UUID.nameUUIDFromBytes(
			("notificationpanel-legacy-" + row + "\n" + pattern + "\n" + format)
				.getBytes(StandardCharsets.UTF_8));
		return new NotificationRule(id, "Imported rule " + (row + 1), enabled, glob,
			parsed.backgroundRgb, parsed.opacityPercent, parsed.visibility,
			migrationNote(problems, widenings));
	}

	/**
	 * Builds the stored note. Both kinds of failure disable the rule, but they need different
	 * things from the user, so they get different prefixes and the editor counts them separately.
	 */
	private static String migrationNote(List<String> problems, List<String> widenings)
	{
		if (!problems.isEmpty())
		{
			List<String> combined = new ArrayList<>(problems);
			combined.addAll(widenings);
			return PROBLEM_NOTE_PREFIX + String.join(" ", combined);
		}
		if (!widenings.isEmpty())
		{
			return WIDENED_NOTE_PREFIX + String.join(" ", widenings)
				+ " Turn it on if that is what you want.";
		}
		return null;
	}

	/**
	 * Converts a legacy regular-expression pattern to the wildcard syntax matched
	 * by {@link Wildcards}, whose only metacharacter is {@code *}. The common
	 * cases translate cleanly: {@code .*}, {@code .+}, and a lone {@code .} all
	 * become {@code *}, and anchors ({@code ^}, {@code $}) are dropped. A pattern
	 * that relies on any other regex construct (character classes, groups,
	 * alternation, quantifiers, escapes) has no faithful wildcard equivalent and
	 * is left for the user to rewrite; this returns {@code null} for those.
	 *
	 * <p>Both are matched against the whole message, so {@code .*foo.*} becomes
	 * {@code *foo*} and {@code ^foo$} becomes {@code foo} with no change in meaning.
	 * The one lossy step is a lone {@code .}, which matched exactly one character
	 * where {@code *} matches any run; the result records whether that happened.</p>
	 */
	private static Conversion regexToWildcard(String regex)
	{
		int start = 0;
		int end = regex.length();
		if (end > 0 && regex.charAt(0) == '^')
		{
			start = 1;
		}
		if (end > start && regex.charAt(end - 1) == '$')
		{
			end--;
		}

		StringBuilder wildcard = new StringBuilder();
		boolean widenedLoneDot = false;
		int index = start;
		while (index < end)
		{
			char character = regex.charAt(index);
			if (character == '.')
			{
				char next = index + 1 < end ? regex.charAt(index + 1) : '\0';
				if (next == '*' || next == '+')
				{
					index++;
				}
				else
				{
					widenedLoneDot = true;
				}
				appendStar(wildcard);
				index++;
			}
			else if (isRegexMetacharacter(character))
			{
				return null;
			}
			else
			{
				wildcard.append(character);
				index++;
			}
		}
		return new Conversion(wildcard.toString(), widenedLoneDot);
	}

	private static void appendStar(StringBuilder wildcard)
	{
		if (wildcard.length() == 0 || wildcard.charAt(wildcard.length() - 1) != '*')
		{
			wildcard.append('*');
		}
	}

	private static boolean isRegexMetacharacter(char character)
	{
		switch (character)
		{
			case '\\':
			case '[':
			case ']':
			case '(':
			case ')':
			case '{':
			case '}':
			case '|':
			case '+':
			case '*':
			case '?':
			case '^':
			case '$':
				return true;
			default:
				return false;
		}
	}

	private static ParsedFormat parseFormat(String format, List<String> problems)
	{
		ParsedFormat parsed = new ParsedFormat();
		if (format.trim().isEmpty())
		{
			return parsed;
		}

		for (String rawToken : format.split("(,|\\s+)", -1))
		{
			String token = rawToken.trim();
			if (token.isEmpty())
			{
				// Splitting on comma or whitespace yields empty tokens for padded or doubled
				// separators. Unparseable options were skipped, so these are not a problem.
				continue;
			}
			if (looksLikeColor(token))
			{
				Integer rgb = decodeColor(token);
				if (rgb == null)
				{
					problems.add("Invalid legacy color token: " + token + ".");
				}
				else if (parsed.backgroundRgb == null)
				{
					parsed.backgroundRgb = rgb;
				}
			}
			else if (token.startsWith("opacity="))
			{
				parseOpacity(token, parsed, problems);
			}
			else if ("hide".equals(token))
			{
				// First token wins, the same as colour and opacity above: whichever the user
				// listed first is the one that used to take effect.
				if (parsed.visibility == null)
				{
					parsed.visibility = Visibility.HIDE;
				}
			}
			else if ("show".equals(token))
			{
				// Deliberately a no-op. A matching enabled rule is shown anyway, so importing this
				// as an explicit override would buy nothing and can cost something: visibility is
				// first-match-wins, so a broad `.*, show` row above a narrow `*screenshot*, hide`
				// row would settle visibility first and stop the hide ever being reached -- the
				// exact complaint this feature exists to fix, reintroduced by the importer.
			}
			else if (token.startsWith("duration=") || token.startsWith("showTime="))
			{
				problems.add("Unsupported legacy token: " + token + ".");
			}
			else
			{
				problems.add("Invalid legacy token: " + token + ".");
			}
		}
		return parsed;
	}

	private static void parseOpacity(String token, ParsedFormat parsed, List<String> problems)
	{
		try
		{
			// Out-of-range values were clamped rather than rejected, so a row using one still
			// worked and must keep working.
			int opacity = Math.max(0, Math.min(100,
				Integer.parseInt(token.substring("opacity=".length()))));
			if (parsed.opacityPercent == null)
			{
				parsed.opacityPercent = opacity;
			}
		}
		catch (NumberFormatException exception)
		{
			problems.add("Invalid legacy opacity token: " + token + ".");
		}
	}

	private static boolean isOversized(String value)
	{
		return value != null && value.length() > RuleCodec.MAX_CONFIG_LENGTH;
	}

	/**
	 * Whether a token was meant as a colour. Colours were parsed with {@code Color.decode}, which
	 * accepts more than {@code #RRGGBB}, so the same forms are accepted here: rows using them
	 * worked before and would otherwise import broken.
	 */
	private static boolean looksLikeColor(String value)
	{
		char first = value.charAt(0);
		return first == '#' || first >= '0' && first <= '9'
			|| value.startsWith("0x") || value.startsWith("0X");
	}

	private static Integer decodeColor(String value)
	{
		try
		{
			return java.awt.Color.decode(value).getRGB() & 0xFFFFFF;
		}
		catch (NumberFormatException exception)
		{
			return null;
		}
	}

	private static final class ParsedFormat
	{
		private Integer backgroundRgb;
		private Integer opacityPercent;
		private Visibility visibility;
	}

	/** A converted wildcard pattern and which lossy translations produced it. */
	private static final class Conversion
	{
		private final String wildcard;
		private final boolean widenedLoneDot;

		private Conversion(String wildcard, boolean widenedLoneDot)
		{
			this.wildcard = wildcard;
			this.widenedLoneDot = widenedLoneDot;
		}

	}
}
