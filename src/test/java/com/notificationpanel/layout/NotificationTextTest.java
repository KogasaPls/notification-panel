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
package com.notificationpanel.layout;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class NotificationTextTest
{
	private static final NotificationText.Measurer CODE_POINTS =
		text -> text.codePointCount(0, text.length());

	@Test
	public void limitsByCodePointAndAppendsOneEllipsis()
	{
		assertEquals("", NotificationText.limit(null));
		String exact = "😀".repeat(2048);
		assertEquals(exact, NotificationText.limit(exact));

		String limited = NotificationText.limit("😀".repeat(2049));

		assertEquals(2048, limited.codePointCount(0, limited.length()));
		assertEquals("😀".repeat(2047) + "…", limited);
	}

	@Test
	public void returnsOneEmptyLineForNullAndEmptyText()
	{
		assertEquals(Collections.singletonList(""),
			NotificationText.wrap(null, 10, CODE_POINTS));
		assertEquals(Collections.singletonList(""),
			NotificationText.wrap("", 10, CODE_POINTS));
	}

	@Test
	public void rejectsNullMeasurerEvenForEmptyText()
	{
		assertThrows(NullPointerException.class,
			() -> NotificationText.wrap("", 10, null));
	}

	@Test
	public void balancesNormalTextAndExcludesLastLineCost()
	{
		assertEquals(Arrays.asList("aa bb", "cc dd"),
			NotificationText.wrap("aa bb cc dd", 5, CODE_POINTS));
		assertEquals(Arrays.asList("aaaa", "bb ccc", "ddddddd"),
			NotificationText.wrap("aaaa bb ccc ddddddd", 10, CODE_POINTS));
	}

	@Test
	public void trimsOnlyWhitespaceAtLineEdges()
	{
		assertEquals(Collections.singletonList("aa  bb"),
			NotificationText.wrap(" \u2003aa  bb\u2003 ", 6, CODE_POINTS));
		assertEquals(Arrays.asList("aa", "bb"),
			NotificationText.wrap(" \u2003aa  bb\u2003 ", 4, CODE_POINTS));
		assertEquals(Collections.singletonList(""),
			NotificationText.wrap(" \u2003 ", 4, CODE_POINTS));
	}

	@Test
	public void breaksAtOrdinaryWhitespaceAndAtExactFit()
	{
		assertEquals(Arrays.asList("abc", "def"),
			NotificationText.wrap("abc def", 3, CODE_POINTS));
		assertEquals(Collections.singletonList("abc def"),
			NotificationText.wrap("abc def", 7, CODE_POINTS));
	}

	@Test
	public void breaksAfterSlashAndBackslash()
	{
		assertEquals(Arrays.asList("abc/", "def"),
			NotificationText.wrap("abc/def", 4, CODE_POINTS));
		assertEquals(Arrays.asList("abc\\", "def"),
			NotificationText.wrap("abc\\def", 4, CODE_POINTS));
	}

	@Test
	public void treatsNonPositiveWidthAsOne()
	{
		List<String> expected = Arrays.asList("a", "b");
		assertEquals(expected, NotificationText.wrap("ab", 0, CODE_POINTS));
		assertEquals(expected, NotificationText.wrap("ab", -20, CODE_POINTS));
	}

	@Test
	public void hardWrapsWithoutSplittingSurrogatePairs()
	{
		assertEquals(Arrays.asList("😀😀", "😀"),
			NotificationText.wrap("😀😀😀", 2, CODE_POINTS));
	}

	@Test
	public void forcesOneOverWideCodePointOntoItsOwnLine()
	{
		NotificationText.Measurer wideEmoji = text ->
			text.codePoints().map(codePoint -> codePoint == 0x1F600 ? 2 : 1).sum();

		assertEquals(Arrays.asList("😀", "a"),
			NotificationText.wrap("😀a", 1, wideEmoji));
	}

	@Test
	public void makesProgressWithNonMonotonicMeasurer()
	{
		NotificationText.Measurer nonMonotonic = text ->
		{
			switch (text.codePointCount(0, text.length()))
			{
				case 1:
					return 1;
				case 2:
					return 3;
				case 3:
					return 2;
				default:
					return 4;
			}
		};

		assertEquals(Arrays.asList("a", "bcd"),
			NotificationText.wrap("abcd", 2, nonMonotonic));
	}

	@Test
	public void hardWrapsMaximumLengthUnbreakableText()
	{
		String text = "😀".repeat(NotificationText.MAX_CODE_POINTS);

		List<String> lines = NotificationText.wrap(text, 1, CODE_POINTS);

		assertEquals(NotificationText.MAX_CODE_POINTS, lines.size());
		assertEquals(text, String.join("", lines));
	}

	@Test
	public void limitsTextBeforeWrapping()
	{
		String text = "😀".repeat(NotificationText.MAX_CODE_POINTS + 1);

		List<String> lines = NotificationText.wrap(text, 4096, CODE_POINTS);

		assertEquals(Collections.singletonList(
			"😀".repeat(NotificationText.MAX_CODE_POINTS - 1) + "…"), lines);
	}

	@Test
	public void selectsBalancedAt256PostSplitTokensAndGreedyAt257()
	{
		assertEquals(NotificationText.WrapMode.BALANCED,
			NotificationText.chooseMode(256));
		assertEquals(NotificationText.WrapMode.GREEDY,
			NotificationText.chooseMode(257));

		assertEquals(256, NotificationText.wrap("a ".repeat(256), 1, CODE_POINTS).size());
		assertEquals(257, NotificationText.wrap("a ".repeat(257), 1, CODE_POINTS).size());
	}

	@Test
	public void avoidsSquaredSlackOverflow()
	{
		assertEquals(Collections.singletonList("a b"),
			NotificationText.wrap("a b", Integer.MAX_VALUE, text -> Integer.MIN_VALUE));
	}

	@Test
	public void returnsImmutableLines()
	{
		List<String> lines = NotificationText.wrap("a b", 1, CODE_POINTS);

		assertThrows(UnsupportedOperationException.class, () -> lines.add("c"));
		assertThrows(UnsupportedOperationException.class, () -> lines.set(0, "c"));
	}
}
