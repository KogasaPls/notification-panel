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
 * characters (including empty) and all other characters match literally.
 *
 * <p>Matching is anchored to the full text and executes in O(pattern + text)
 * time using Knuth-Morris-Pratt for segment search.</p>
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
	 * Folds nullable text to canonical case for matching.
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
			return pattern.length == text.length && regionMatches(pattern, 0, text, 0,
				pattern.length);
		}

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
