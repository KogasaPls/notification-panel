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

import java.awt.Font;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class NotificationText
{
	public static final int MAX_CODE_POINTS = 2048;
	private static final int MAX_BALANCED_TOKENS = 256;

	private NotificationText()
	{
	}

	/**
	 * Measures the rendered width of nonnull text.
	 *
	 * <p>Implementations must return nonnegative widths. They must also be
	 * monotonically nondecreasing as Unicode code points are appended to the
	 * measured text.</p>
	 */
	@FunctionalInterface
	public interface Measurer
	{
		int width(String text);
	}

	enum WrapMode
	{
		BALANCED,
		GREEDY
	}

	public static String limit(String input)
	{
		String value = input == null ? "" : input;
		if (value.codePointCount(0, value.length()) <= MAX_CODE_POINTS)
		{
			return value;
		}
		int end = value.offsetByCodePoints(0, MAX_CODE_POINTS - 1);
		return value.substring(0, end) + "\u2026";
	}

	/**
	 * Remembers recent wrap results so the overlay does not redo the work every frame.
	 *
	 * <p>The key carries everything a result depends on -- the text, the width it was wrapped to,
	 * and the font it was measured in -- so an entry can never be stale; a change in any of them
	 * is a different key rather than an invalidation to remember to perform. That is the
	 * difference between this and the dirty flag the rewrite set out to remove.</p>
	 *
	 * <p>Not thread safe. The overlay owns one and renders on the client thread.</p>
	 */
	public static final class Cache
	{
		private static final int MAX_ENTRIES = 32;

		private final Map<List<Object>, List<String>> entries =
			new LinkedHashMap<List<Object>, List<String>>(16, 0.75f, true)
			{
				private static final long serialVersionUID = 1L;

				@Override
				protected boolean removeEldestEntry(Map.Entry<List<Object>, List<String>> eldest)
				{
					return size() > MAX_ENTRIES;
				}
			};

		public List<String> wrap(String text, int width, Font font, Measurer measurer)
		{
			List<Object> key = Arrays.asList(text, width, font);
			List<String> cached = entries.get(key);
			if (cached != null)
			{
				return cached;
			}
			List<String> lines = NotificationText.wrap(text, width, measurer);
			entries.put(key, lines);
			return lines;
		}
	}

	public static List<String> wrap(String text, int width, Measurer measurer)
	{
		Objects.requireNonNull(measurer, "measurer");
		String value = normaliseWhitespace(limit(text));
		if (value.isEmpty())
		{
			return Collections.singletonList("");
		}

		value = stripLineWhitespace(value);
		if (value.isEmpty())
		{
			return Collections.singletonList("");
		}

		int effectiveWidth = Math.max(1, width);
		List<String> tokens = splitWideTokens(tokenize(value), effectiveWidth, measurer);
		if (chooseMode(tokens.size()) == WrapMode.BALANCED)
		{
			return balancedWrap(tokens, effectiveWidth, measurer);
		}
		return greedyWrap(tokens, effectiveWidth, measurer);
	}

	static WrapMode chooseMode(int tokenCount)
	{
		return tokenCount <= MAX_BALANCED_TOKENS ? WrapMode.BALANCED : WrapMode.GREEDY;
	}

	private static List<String> tokenize(String text)
	{
		List<String> tokens = new ArrayList<>();
		int start = 0;
		for (int index = 0; index < text.length();)
		{
			int codePoint = text.codePointAt(index);
			index += Character.charCount(codePoint);
			if (Character.isWhitespace(codePoint) || codePoint == '/' || codePoint == '\\')
			{
				appendToken(tokens, text.substring(start, index));
				start = index;
			}
		}
		if (start < text.length())
		{
			appendToken(tokens, text.substring(start));
		}
		return tokens;
	}

	private static List<String> splitWideTokens(List<String> tokens, int width, Measurer measurer)
	{
		List<String> split = new ArrayList<>();
		for (String token : tokens)
		{
			int start = 0;
			while (start < token.length())
			{
				String remaining = token.substring(start);
				String rendered = stripLineWhitespace(remaining);
				if (rendered.isEmpty() || measurer.width(rendered) <= width)
				{
					appendToken(split, remaining);
					break;
				}

				int splitEnd = largestFittingPrefixEnd(remaining, width, measurer);
				appendToken(split, remaining.substring(0, splitEnd));
				start += splitEnd;
			}
		}
		return split;
	}

	/*
	 * Binary search finds the largest fitting prefix under Measurer's monotonic
	 * contract. The one-code-point fallback still guarantees progress for a
	 * pathological callback which violates it.
	 */
	private static int largestFittingPrefixEnd(String text, int width, Measurer measurer)
	{
		int codePointCount = text.codePointCount(0, text.length());
		int low = 1;
		int high = codePointCount;
		int fittingCodePoints = 0;
		while (low <= high)
		{
			int middle = low + (high - low) / 2;
			int end = text.offsetByCodePoints(0, middle);
			String rendered = stripLineWhitespace(text.substring(0, end));
			if (rendered.isEmpty() || measurer.width(rendered) <= width)
			{
				fittingCodePoints = middle;
				low = middle + 1;
			}
			else
			{
				high = middle - 1;
			}
		}

		int selectedCodePoints = fittingCodePoints == 0 ? 1 : fittingCodePoints;
		return text.offsetByCodePoints(0, selectedCodePoints);
	}

	private static List<String> balancedWrap(List<String> tokens, int width, Measurer measurer)
	{
		BigInteger[] cost = new BigInteger[tokens.size() + 1];
		int[] next = new int[tokens.size()];
		cost[tokens.size()] = BigInteger.ZERO;

		for (int start = tokens.size() - 1; start >= 0; start--)
		{
			StringBuilder candidateLine = new StringBuilder();
			for (int end = start; end < tokens.size(); end++)
			{
				candidateLine.append(tokens.get(end));
				String rendered = stripLineWhitespace(candidateLine.toString());
				if (rendered.isEmpty() && end < tokens.size() - 1)
				{
					continue;
				}

				int used = rendered.isEmpty() ? 0 : measurer.width(rendered);
				boolean forcedSingleCodePoint =
					end == start
					&& rendered.codePointCount(0, rendered.length()) == 1
					&& used > width;
				if (used > width && !forcedSingleCodePoint)
				{
					break;
				}

				if (cost[end + 1] == null)
				{
					continue;
				}
				BigInteger candidate = end == tokens.size() - 1
					? BigInteger.ZERO
					: squaredSlack(width, used, forcedSingleCodePoint).add(cost[end + 1]);
				if (cost[start] == null || candidate.compareTo(cost[start]) < 0)
				{
					cost[start] = candidate;
					next[start] = end + 1;
				}
			}
		}
		return reconstruct(tokens, next);
	}

	private static BigInteger squaredSlack(int width, int used, boolean forcedSingleCodePoint)
	{
		if (forcedSingleCodePoint)
		{
			return BigInteger.ZERO;
		}
		BigInteger slack = BigInteger.valueOf((long) width - used);
		return slack.multiply(slack);
	}

	private static List<String> reconstruct(List<String> tokens, int[] next)
	{
		List<String> lines = new ArrayList<>();
		for (int start = 0; start < tokens.size();)
		{
			int end = next[start] > start ? next[start] : start + 1;
			lines.add(stripLineWhitespace(join(tokens, start, end)));
			start = end;
		}
		return Collections.unmodifiableList(lines);
	}

	private static List<String> greedyWrap(List<String> tokens, int width, Measurer measurer)
	{
		List<String> lines = new ArrayList<>();
		for (int start = 0; start < tokens.size();)
		{
			StringBuilder candidateLine = new StringBuilder();
			String accepted = null;
			int acceptedEnd = start;
			for (int end = start; end < tokens.size(); end++)
			{
				candidateLine.append(tokens.get(end));
				String rendered = stripLineWhitespace(candidateLine.toString());
				int used = rendered.isEmpty() ? 0 : measurer.width(rendered);
				boolean forcedSingleCodePoint =
					end == start
					&& rendered.codePointCount(0, rendered.length()) == 1
					&& used > width;
				if (used > width && !forcedSingleCodePoint)
				{
					break;
				}
				accepted = rendered;
				acceptedEnd = end + 1;
			}

			if (accepted == null)
			{
				accepted = stripLineWhitespace(tokens.get(start));
				acceptedEnd = start + 1;
			}
			lines.add(accepted);
			start = acceptedEnd;
		}
		return Collections.unmodifiableList(lines);
	}

	private static void appendToken(List<String> tokens, String token)
	{
		if (isWhitespaceOnly(token) && !tokens.isEmpty())
		{
			int previous = tokens.size() - 1;
			tokens.set(previous, tokens.get(previous) + token);
			return;
		}
		tokens.add(token);
	}

	private static boolean isWhitespaceOnly(String text)
	{
		for (int index = 0; index < text.length();)
		{
			int codePoint = text.codePointAt(index);
			if (!Character.isWhitespace(codePoint))
			{
				return false;
			}
			index += Character.charCount(codePoint);
		}
		return !text.isEmpty();
	}

	private static String join(List<String> tokens, int start, int end)
	{
		StringBuilder joined = new StringBuilder();
		for (int index = start; index < end; index++)
		{
			joined.append(tokens.get(index));
		}
		return joined.toString();
	}

	/**
	 * Replaces every whitespace code point that is not a plain space with one.
	 *
	 * <p>Tokenising breaks on whitespace but keeps the character in the token it ends, and a tab or
	 * a line break paints as nothing, so "Level up!\nAttack" would be drawn as one run-together
	 * word. Only the drawn form changes -- the message the rules matched against is untouched.</p>
	 */
	private static String normaliseWhitespace(String text)
	{
		StringBuilder normalised = null;
		for (int index = 0; index < text.length();)
		{
			int codePoint = text.codePointAt(index);
			int charCount = Character.charCount(codePoint);
			if (codePoint != ' ' && Character.isWhitespace(codePoint))
			{
				if (normalised == null)
				{
					normalised = new StringBuilder(text.length()).append(text, 0, index);
				}
				normalised.append(' ');
			}
			else if (normalised != null)
			{
				normalised.append(text, index, index + charCount);
			}
			index += charCount;
		}
		return normalised == null ? text : normalised.toString();
	}

	private static String stripLineWhitespace(String text)
	{
		int start = 0;
		while (start < text.length())
		{
			int codePoint = text.codePointAt(start);
			if (!Character.isWhitespace(codePoint))
			{
				break;
			}
			start += Character.charCount(codePoint);
		}

		int end = text.length();
		while (end > start)
		{
			int codePoint = text.codePointBefore(end);
			if (!Character.isWhitespace(codePoint))
			{
				break;
			}
			end -= Character.charCount(codePoint);
		}
		return text.substring(start, end);
	}
}
