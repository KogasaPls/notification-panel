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
 * is literal. Matching is anchored: the pattern has to describe the whole text.
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

	private static boolean equalsIgnoreCase(char a, char b)
	{
		return a == b
			|| Character.toLowerCase(a) == Character.toLowerCase(b)
			|| Character.toUpperCase(a) == Character.toUpperCase(b);
	}
}
