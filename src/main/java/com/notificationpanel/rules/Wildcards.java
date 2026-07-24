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
 * pattern has to describe the whole text.
 * Matching part of a message is what a leading or trailing {@code *} is for, and
 * is left to the author of the pattern rather than applied on their behalf.
 *
 * <p>The match is a greedy two-pointer scan whose backtracking is bounded by the
 * most recent {@code *}, so it runs in O(pattern x text) time and cannot
 * backtrack exponentially. This matters because rule patterns are user-authored
 * and are matched, on the client thread, against messages far longer than the
 * short item and NPC names that RuneLite's own {@code WildcardMatcher} was built
 * for. A regex-backed matcher can hang on a pattern like {@code *a*a*...*b}
 * against a long non-matching message; this cannot.</p>
 *
 * <p>Bounded is not the same as cheap, and the bound is worth stating in
 * milliseconds rather than in notation. At the configured caps -- a 512 code
 * point pattern against a 2048 code point message -- O(pattern x text) is close
 * to a million character comparisons for one rule, and a set may hold a hundred
 * of them. A single notification measured 46ms of plain ASCII and 494ms where
 * case folding left {@code CharacterDataLatin1}, against a 20ms frame. Realistic
 * patterns against real messages cost well under a microsecond, and reaching the
 * ceiling takes a maximum length message that is a near uniform run of one
 * character together with patterns tuned to it.</p>
 *
 * <p>Rejecting a pattern that has more literal characters than the text has is
 * not the fix it looks like: that test can only fire when the pattern is the
 * longer of the two, which is the opposite of the expensive case. Measured, it
 * left the ceiling unchanged and cost the common path a few percent.</p>
 */
final class Wildcards
{
	private Wildcards()
	{
	}

	static boolean matches(String pattern, String text)
	{
		String p = pattern == null ? "" : pattern;
		String t = text == null ? "" : text;
		int pi = 0;
		int ti = 0;
		int star = -1;
		int mark = 0;
		int pn = p.length();
		int tn = t.length();
		while (ti < tn)
		{
			if (pi < pn && p.charAt(pi) == '*')
			{
				star = pi;
				mark = ti;
				pi++;
			}
			else if (pi < pn && equalsIgnoreCase(p.charAt(pi), t.charAt(ti)))
			{
				pi++;
				ti++;
			}
			else if (star != -1)
			{
				pi = star + 1;
				mark++;
				ti = mark;
			}
			else
			{
				return false;
			}
		}
		while (pi < pn && p.charAt(pi) == '*')
		{
			pi++;
		}
		return pi == pn;
	}

	/**
	 * Folds case the way {@link String#equalsIgnoreCase} does: compare the characters, then their
	 * uppercase forms, then the lowercase of <em>those</em>.
	 *
	 * <p>Folding each direction from the originals instead -- the obvious way to write this -- is
	 * not the same relation. It misses pairs whose only shared form is reached by uppercasing
	 * first, of which the Basic Multilingual Plane holds exactly two: U+0130/U+0131, the Turkish
	 * dotted and dotless I, and U+03D1/U+03F4. Chaining costs the same two lookups per side and
	 * makes the relation one a reader can look up rather than one they have to derive.</p>
	 *
	 * <p>The {@code char} overloads are locale-independent, unlike {@link String#toLowerCase()},
	 * which is what would break this for a client running under a Turkish locale.</p>
	 */
	private static boolean equalsIgnoreCase(char a, char b)
	{
		if (a == b)
		{
			return true;
		}
		char upperA = Character.toUpperCase(a);
		char upperB = Character.toUpperCase(b);
		return upperA == upperB
			|| Character.toLowerCase(upperA) == Character.toLowerCase(upperB);
	}
}
