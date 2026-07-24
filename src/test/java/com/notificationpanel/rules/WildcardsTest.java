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

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WildcardsTest
{
	@Test
	public void starMatchesAnyRunAndIsAnchored()
	{
		assertTrue(Wildcards.matches("*dragon*", "You got a dragon"));
		assertTrue(Wildcards.matches("Your*thrall*grave", "Your lesser thrall reaches the grave"));
		assertTrue(Wildcards.matches("a*b", "ab"));
		assertFalse(Wildcards.matches("dragon", "You got a dragon"));
		assertTrue(Wildcards.matches("dragon", "dragon"));
		assertTrue(Wildcards.matches("*", ""));
	}

	@Test
	public void matchingIgnoresCase()
	{
		assertTrue(Wildcards.matches("*antifire*", "You feel ANTIFIRE"));
		assertTrue(Wildcards.matches("*ANTIFIRE*", "you feel antifire"));
	}

	@Test
	public void questionMarkIsLiteralNotAWildcard()
	{
		assertFalse(Wildcards.matches("*feel?*", "you feel x"));
		assertTrue(Wildcards.matches("*feel?*", "you feel? really"));
	}

	@Test
	public void nullPatternOrTextIsTreatedAsEmpty()
	{
		assertTrue(Wildcards.matches("*", null));
		assertFalse(Wildcards.matches("x", null));
		assertFalse(Wildcards.matches(null, "x"));
		assertTrue(Wildcards.matches(null, ""));
	}

	@Test
	public void emptyPatternMatchesOnlyEmptyText()
	{
		assertTrue(Wildcards.matches("", ""));
		assertFalse(Wildcards.matches("", "x"));
	}

	@Test
	public void caseFoldingHandlesAccentedCharacters()
	{
		// Escaped rather than literal so the assertion does not depend on the encoding the file
		// happens to be compiled with.
		assertTrue(Wildcards.matches("*caf\u00E9*", "a CAF\u00C9 here"));
		assertFalse(Wildcards.matches("*caf\u00E9*", "a latte here"));
	}

	@Test
	public void foldsCaseTheWayStringEqualsIgnoreCaseDoes()
	{
		// Folding each direction from the originals misses these: their only shared form is
		// reached by uppercasing first. They are the only two such pairs in the BMP.
		assertTrue(Wildcards.matches("\u0131", "\u0130"));
		assertTrue(Wildcards.matches("\u03D1", "\u03F4"));
		// The Turkish I, which locale-sensitive String.toLowerCase would get wrong.
		assertTrue(Wildcards.matches("*\u0131*", "AIB"));
	}

	@Test
	public void foldsCaseWithinTheBasicMultilingualPlaneOnly()
	{
		// Iterating by char means a supplementary code point is two surrogates, which have no case
		// mapping, so matching there is case-sensitive. Deliberate, and documented on the class.
		assertTrue(Wildcards.matches("\uD801\uDC28", "\uD801\uDC28"));
		assertFalse(Wildcards.matches("\uD801\uDC28", "\uD801\uDC00"));
		// Exact and wildcard matching of supplementary characters is otherwise unaffected.
		assertTrue(Wildcards.matches("*\uD83D\uDE00*", "you got \uD83D\uDE00 here"));
		assertFalse(Wildcards.matches("\uD83D\uDE00", "\uD83D\uDE01"));
	}

	@Test
	public void consecutiveAndAllStarPatternsCollapse()
	{
		assertTrue(Wildcards.matches("a**b", "ab"));
		assertTrue(Wildcards.matches("a**b", "a long way to b"));
		assertTrue(Wildcards.matches("*****a*****", "a"));
		assertTrue(Wildcards.matches("*****a*****", "well, a, then"));
		for (String allStars : new String[]{"*", "**", "***", "****"})
		{
			assertTrue(allStars, Wildcards.matches(allStars, ""));
			assertTrue(allStars, Wildcards.matches(allStars, "anything at all"));
		}
	}

	@Test
	public void starCrossesLineBreaks()
	{
		// A deliberate divergence from RuneLite's WildcardMatcher, whose '.' stops at a line
		// terminator because it compiles without DOTALL.
		assertTrue(Wildcards.matches("*a*b*", "a\nb"));
		assertTrue(Wildcards.matches("Level up!*Attack*", "Level up!\nAttack is now 70."));
	}

	@Test(timeout = 5000)
	public void doesNotBacktrackExponentiallyOnPathologicalPatterns()
	{
		// A regex-backed matcher hangs on this shape; the two-pointer scan cannot.
		String text = "a".repeat(100_000) + "c";
		assertFalse(Wildcards.matches("*a*a*a*a*a*a*a*a*a*a*b", text));
	}
}
