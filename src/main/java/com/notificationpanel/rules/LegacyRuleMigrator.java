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

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class LegacyRuleMigrator
{
	private static final int MAX_CONFIG_LENGTH = 262_144;
	private static final int MAX_RULES = 100;
	private static final String OVERSIZED_WARNING =
		"Legacy rule configuration exceeded 262144 characters and was not migrated.";
	private static final String CAPPED_WARNING =
		"Only the first 100 legacy rules were migrated.";

	public RuleDocument migrate(String patternValue, String formatValue)
	{
		if (isOversized(patternValue) || isOversized(formatValue))
		{
			return new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION,
				Collections.singletonList(OVERSIZED_WARNING), Collections.emptyList());
		}

		String[] patterns = (patternValue == null ? "" : patternValue).split("\\R", -1);
		String[] formats = (formatValue == null ? "" : formatValue).split("\\R", -1);
		int rowCount = Math.max(patterns.length, formats.length);
		List<NotificationRule> rules = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		for (int row = 0; row < rowCount; row++)
		{
			String pattern = row < patterns.length ? patterns[row] : "";
			String format = row < formats.length ? formats[row] : "";
			if (pattern.trim().isEmpty() && format.trim().isEmpty())
			{
				continue;
			}
			if (rules.size() == MAX_RULES)
			{
				warnings.add(CAPPED_WARNING);
				break;
			}
			rules.add(migrateRow(row, pattern, format));
		}
		return new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION, warnings, rules);
	}

	private static NotificationRule migrateRow(int row, String pattern, String format)
	{
		List<String> problems = new ArrayList<>();
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
			try
			{
				Pattern.compile(pattern);
			}
			catch (PatternSyntaxException exception)
			{
				problems.add("Pattern is not a valid regular expression: " + exception.getMessage());
			}
		}

		ParsedFormat parsed = parseFormat(format, problems);
		if (parsed.recognizedAttributes == 0)
		{
			problems.add("No recognized formatting attributes.");
		}
		String migrationNote = problems.isEmpty()
			? null : "Legacy migration problems: " + String.join(" ", problems);
		UUID id = UUID.nameUUIDFromBytes(
			("notificationpanel-legacy-" + row + "\n" + pattern + "\n" + format)
				.getBytes(StandardCharsets.UTF_8));
		return new NotificationRule(id, "Imported rule " + (row + 1), problems.isEmpty(), pattern,
			parsed.backgroundRgb, parsed.opacityPercent, parsed.visibility, migrationNote);
	}

	private static ParsedFormat parseFormat(String format, List<String> problems)
	{
		ParsedFormat parsed = new ParsedFormat();
		if (format.trim().isEmpty())
		{
			return parsed;
		}

		for (String rawToken : format.split(",", -1))
		{
			String token = rawToken.trim();
			if (isRgbColor(token))
			{
				parsed.recognizedAttributes++;
				if (parsed.backgroundRgb == null)
				{
					parsed.backgroundRgb = Integer.parseInt(token.substring(1), 16);
				}
			}
			else if (token.startsWith("#"))
			{
				problems.add("Invalid legacy color token: " + token + ".");
			}
			else if (token.startsWith("opacity="))
			{
				parseOpacity(token, parsed, problems);
			}
			else if ("hide".equals(token))
			{
				parsed.recognizedAttributes++;
				if (!parsed.hasVisibility)
				{
					parsed.visibility = NotificationRule.Visibility.HIDE;
					parsed.hasVisibility = true;
				}
			}
			else if ("show".equals(token))
			{
				parsed.recognizedAttributes++;
				if (!parsed.hasVisibility)
				{
					parsed.visibility = NotificationRule.Visibility.SHOW;
					parsed.hasVisibility = true;
				}
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
			int opacity = Integer.parseInt(token.substring("opacity=".length()));
			if (opacity < 0 || opacity > 100)
			{
				problems.add("Invalid legacy opacity token: " + token + ".");
				return;
			}
			parsed.recognizedAttributes++;
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
		return value != null && value.length() > MAX_CONFIG_LENGTH;
	}

	private static boolean isRgbColor(String value)
	{
		if (value.length() != 7 || value.charAt(0) != '#')
		{
			return false;
		}
		for (int index = 1; index < value.length(); index++)
		{
			char character = value.charAt(index);
			if (!((character >= '0' && character <= '9')
				|| (character >= 'A' && character <= 'F')
				|| (character >= 'a' && character <= 'f')))
			{
				return false;
			}
		}
		return true;
	}

	private static final class ParsedFormat
	{
		private Integer backgroundRgb;
		private Integer opacityPercent;
		private NotificationRule.Visibility visibility = NotificationRule.Visibility.INHERIT;
		private boolean hasVisibility;
		private int recognizedAttributes;
	}
}
