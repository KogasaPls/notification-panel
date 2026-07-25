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
import java.util.List;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Differential fuzz of {@link Wildcards} against an obviously correct reference.
 *
 * <p>The matcher is a single forward pass with no backtracking, which is fast and is not
 * self-evidently right. The reference below is the opposite: a dynamic-programming table that
 * states the definition of the match directly and could not be made faster without becoming
 * something worth testing. Keeping it here, rather than diffing against a previous
 * implementation, is what stops this test rotting into a tautology the next time the matcher
 * changes.</p>
 */
public class WildcardsFuzzTest
{
	@Test
	public void agreesWithTheReferenceOnEveryShortPatternAndText()
	{
		// Exhaustive over the alphabet that exercises the algorithm's structure: two literals so a
		// segment can fail to match, and the star that creates the segments.
		int cases = 0;
		for (String pattern : allStrings(new char[]{'a', 'b', '*'}, 6))
		{
			for (String text : allStrings(new char[]{'a', 'b'}, 6))
			{
				assertAgrees(pattern, text);
				cases++;
			}
		}
		assertEquals(1093 * 127, cases);
	}

	@Test
	public void agreesWithTheReferenceOnStarHeavyRandomInput()
	{
		Random random = new Random(20260724L);
		char[] patternAlphabet = {'a', 'A', 'b', 'B', 'c', '*', '*'};
		char[] textAlphabet = {'a', 'A', 'b', 'B', 'c', 'C'};
		for (int i = 0; i < 200_000; i++)
		{
			assertAgrees(random(random, patternAlphabet, random.nextInt(16)),
				random(random, textAlphabet, random.nextInt(20)));
		}
	}

	@Test
	public void agreesWithTheReferenceWhereCaseFoldingIsAwkward()
	{
		// The dotted and dotless I, sharp s, final sigma and the Kelvin sign: characters whose
		// folding is asymmetric, which is what would break an algorithm assuming a naive equality.
		Random random = new Random(9001L);
		char[] alphabet = {'I', 'i', '\u0131', '\u0130', '\u00DF', '\u1E9E', '\u03C2', '\u03C3',
			'\u03A3', '\u212A', 'k', '*'};
		char[] textAlphabet = {'I', 'i', '\u0131', '\u0130', '\u00DF', '\u1E9E', '\u03C2',
			'\u03C3', '\u03A3', '\u212A', 'k'};
		for (int i = 0; i < 200_000; i++)
		{
			assertAgrees(random(random, alphabet, random.nextInt(10)),
				random(random, textAlphabet, random.nextInt(12)));
		}
	}

	@Test
	public void agreesWithTheReferenceOnTheAwkwardShapes()
	{
		String[] patterns = {null, "", "*", "**", "*****", "*****a*****", "a*", "*a", "a**b",
			"*a*b*", "a*a", "abc*abc", "*ab*b", "\uD83D\uDE00", "*\uD83D\uDE00*", "*\uDE00",
			"\uD83D*", "\u0131*\u0130"};
		String[] texts = {null, "", "a", "aa", "ab", "abb", "abcabc", "abcabcabc", "abc",
			"\uD83D\uDE00", "you got \uD83D\uDE00 here", "a\nb", "\u00DF", "\u0130\u0131"};
		for (String pattern : patterns)
		{
			for (String text : texts)
			{
				assertAgrees(pattern, text);
			}
		}
	}

	private static void assertAgrees(String pattern, String text)
	{
		boolean expected = reference(pattern, text);
		boolean actual = Wildcards.matches(pattern, text);
		if (expected != actual)
		{
			throw new AssertionError(String.format(
				"pattern %s against text %s: reference %b, matcher %b",
				quote(pattern), quote(text), expected, actual));
		}
	}

	/**
	 * The definition of the match, as a table. Cell (i, j) is whether the first i characters of the
	 * pattern match the first j of the text.
	 */
	private static boolean reference(String pattern, String text)
	{
		String p = pattern == null ? "" : pattern;
		String t = text == null ? "" : text;
		boolean[][] matches = new boolean[p.length() + 1][t.length() + 1];
		matches[0][0] = true;
		for (int i = 1; i <= p.length(); i++)
		{
			matches[i][0] = matches[i - 1][0] && p.charAt(i - 1) == '*';
		}
		for (int i = 1; i <= p.length(); i++)
		{
			for (int j = 1; j <= t.length(); j++)
			{
				if (p.charAt(i - 1) == '*')
				{
					matches[i][j] = matches[i - 1][j] || matches[i][j - 1];
				}
				else
				{
					matches[i][j] = matches[i - 1][j - 1]
						&& sameIgnoringCase(p.charAt(i - 1), t.charAt(j - 1));
				}
			}
		}
		return matches[p.length()][t.length()];
	}

	/** Stated independently of the matcher, as {@link String#equalsIgnoreCase} defines it. */
	private static boolean sameIgnoringCase(char a, char b)
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

	private static List<String> allStrings(char[] alphabet, int maxLength)
	{
		List<String> all = new ArrayList<>();
		for (int length = 0; length <= maxLength; length++)
		{
			int combinations = 1;
			for (int i = 0; i < length; i++)
			{
				combinations *= alphabet.length;
			}
			for (int n = 0; n < combinations; n++)
			{
				StringBuilder value = new StringBuilder();
				int remaining = n;
				for (int i = 0; i < length; i++)
				{
					value.append(alphabet[remaining % alphabet.length]);
					remaining /= alphabet.length;
				}
				all.add(value.toString());
			}
		}
		return all;
	}

	private static String random(Random random, char[] alphabet, int length)
	{
		StringBuilder value = new StringBuilder();
		for (int i = 0; i < length; i++)
		{
			value.append(alphabet[random.nextInt(alphabet.length)]);
		}
		return value.toString();
	}

	private static String quote(String value)
	{
		if (value == null)
		{
			return "null";
		}
		StringBuilder quoted = new StringBuilder("\"");
		for (char c : value.toCharArray())
		{
			quoted.append(c < 0x80 ? String.valueOf(c) : String.format("\\u%04X", (int) c));
		}
		return quoted.append('"').toString();
	}
}
