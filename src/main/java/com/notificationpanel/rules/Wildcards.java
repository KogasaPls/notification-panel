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

/**
 * Case-insensitive wildcard matching where {@code *} matches any run of
 * characters (including none) and every other character, including {@code ?},
 * is literal. Case folding is per {@code char}, so it covers the Basic
 * Multilingual Plane; a character outside it compares case-sensitively, which
 * no RuneScape notification is expected to contain. Matching is anchored: the
 * pattern has to describe the whole text. Matching part of a message is what a
 * leading or trailing {@code *} is for, and is left to the author of the
 * pattern rather than applied on their behalf.
 *
 * <p>A pattern is literal segments separated by stars. The first segment has to
 * sit at the start of the text unless the pattern opens with a star, the last
 * has to sit at the end unless it closes with one, and the segments between them
 * may sit anywhere as long as they appear in order. Taking each of those middle
 * segments at its <em>earliest</em> remaining occurrence is optimal: placing one
 * earlier leaves a superset of the text for everything that follows, so it can
 * never turn a match into a miss. That is what lets the match be a single
 * forward pass which never reconsiders a decision.</p>
 *
 * <p>Each segment is located with Knuth-Morris-Pratt, whose cost is its own
 * length plus the distance it scans, and the scans only ever move forward, so
 * the whole match is O(pattern + text). The bound matters because patterns are
 * user-authored and are matched, on the client thread, against whole
 * notification messages. The obvious alternatives are both quadratic in the
 * worst case: a backtracking two-pointer scan re-matches a literal segment at
 * every position it slides through, and a regex engine does worse still --
 * RuneLite's own {@code WildcardMatcher} compiles to a regex and does not
 * finish within ten seconds on {@code *a*a*...*b} against a couple of hundred
 * characters.</p>
 *
 * <p>Case-insensitivity is folding rather than comparison. {@link #fold} maps a
 * character to the canonical form that {@link String#equalsIgnoreCase} treats as
 * equal -- uppercase then lowercase, which is one relation rather than two
 * independent ones -- so once both sides are folded, matching compares
 * characters directly. Beyond being faster, that is what makes the algorithm
 * sound: Knuth-Morris-Pratt needs a real equivalence relation, and folding
 * independently in each direction is not one. It accepts {@code U+0131} against
 * {@code I} and {@code I} against {@code U+0130} while rejecting the two ends
 * against each other.</p>
 */
final class Wildcards
{
	private Wildcards()
	{
	}

	static boolean matches(String pattern, String text)
	{
		return matches(fold(pattern), fold(text));
	}

	/**
	 * Folds every character of nullable text to its canonical case.
	 *
	 * <p>Exposed so a caller matching one message against many patterns can fold it once instead of
	 * once per pattern.</p>
	 */
	static char[] fold(String value)
	{
		String source = value == null ? "" : value;
		char[] folded = new char[source.length()];
		for (int index = 0; index < source.length(); index++)
		{
			folded[index] = fold(source.charAt(index));
		}
		return folded;
	}

	static char fold(char character)
	{
		// Below 128 the general form is just the ASCII lowercase, and skipping the two table
		// lookups matters: folding is the per-character cost of every match.
		if (character < 0x80)
		{
			return character >= 'A' && character <= 'Z' ? (char) (character + 32) : character;
		}
		return Character.toLowerCase(Character.toUpperCase(character));
	}

	static boolean matches(char[] pattern, char[] text)
	{
		int firstStar = indexOfStar(pattern, 0);
		if (firstStar < 0)
		{
			// No star at all, so the pattern is the whole text or it is nothing.
			return pattern.length == text.length && regionMatches(pattern, 0, text, 0,
				pattern.length);
		}

		// Everything before the first star is anchored to the start, everything after the last star
		// to the end. They must both fit, and must not have to share the same characters.
		if (firstStar > text.length || !regionMatches(pattern, 0, text, 0, firstStar))
		{
			return false;
		}
		int lastStar = lastIndexOfStar(pattern, firstStar);
		int suffix = pattern.length - lastStar - 1;
		int end = text.length - suffix;
		if (end < firstStar || !regionMatches(pattern, lastStar + 1, text, end, suffix))
		{
			return false;
		}

		int from = firstStar;
		int at = firstStar;
		while (at < lastStar)
		{
			int start = at + 1;
			int stop = indexOfStar(pattern, start);
			if (stop == start)
			{
				// Consecutive stars: the empty segment between them constrains nothing.
				at = start;
				continue;
			}
			int found = indexOf(text, from, end, pattern, start, stop - start);
			if (found < 0)
			{
				return false;
			}
			from = found + stop - start;
			at = stop;
		}
		return true;
	}

	private static int indexOfStar(char[] pattern, int from)
	{
		for (int index = from; index < pattern.length; index++)
		{
			if (pattern[index] == '*')
			{
				return index;
			}
		}
		return -1;
	}

	/**
	 * The last star at or after {@code firstStar}, which callers already know to be one.
	 *
	 * <p>Scanning down to that floor rather than to zero is what lets this return an index
	 * unconditionally: a pattern with a single star answers with the star the caller passed in.
	 * A plain mirror of {@link #indexOfStar} would need a "no star found" result that no call site
	 * can reach.</p>
	 */
	private static int lastIndexOfStar(char[] pattern, int firstStar)
	{
		for (int index = pattern.length - 1; index > firstStar; index--)
		{
			if (pattern[index] == '*')
			{
				return index;
			}
		}
		return firstStar;
	}

	private static boolean regionMatches(char[] pattern, int patternOffset, char[] text,
		int textOffset, int length)
	{
		if (textOffset < 0 || textOffset + length > text.length)
		{
			return false;
		}
		for (int index = 0; index < length; index++)
		{
			if (pattern[patternOffset + index] != text[textOffset + index])
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * The first occurrence of a pattern segment in {@code text} within {@code [from, end)}, or -1.
	 *
	 * <p>Knuth-Morris-Pratt rather than a nested loop, because a nested loop is what makes the
	 * naive matcher quadratic: it would rescan the text from each failed start.</p>
	 */
	private static int indexOf(char[] text, int from, int end, char[] pattern, int offset,
		int length)
	{
		if (length > end - from)
		{
			return -1;
		}
		int[] border = borders(pattern, offset, length);
		int matched = 0;
		for (int index = from; index < end; index++)
		{
			while (matched > 0 && text[index] != pattern[offset + matched])
			{
				matched = border[matched - 1];
			}
			if (text[index] == pattern[offset + matched])
			{
				matched++;
			}
			if (matched == length)
			{
				return index - length + 1;
			}
		}
		return -1;
	}

	/** For each prefix of the segment, the length of its longest proper prefix that is also a suffix. */
	private static int[] borders(char[] pattern, int offset, int length)
	{
		int[] border = new int[length];
		int matched = 0;
		for (int index = 1; index < length; index++)
		{
			while (matched > 0 && pattern[offset + index] != pattern[offset + matched])
			{
				matched = border[matched - 1];
			}
			if (pattern[offset + index] == pattern[offset + matched])
			{
				matched++;
			}
			border[index] = matched;
		}
		return border;
	}
}
