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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class NotificationText
{
	public static final int MAX_CODE_POINTS = 2048;
	private static final int MAX_BALANCED_TOKENS = 256;
	private static final long MAX_COST = Long.MAX_VALUE - 1L;

	private NotificationText()
	{
	}

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

	public static List<String> wrap(String text, int width, Measurer measurer)
	{
		Objects.requireNonNull(measurer, "measurer");
		String value = limit(text);
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
				tokens.add(text.substring(start, index));
				start = index;
			}
		}
		if (start < text.length())
		{
			tokens.add(text.substring(start));
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
					split.add(remaining);
					break;
				}

				int splitEnd = largestFittingPrefixEnd(remaining, width, measurer);
				split.add(remaining.substring(0, splitEnd));
				start += splitEnd;
			}
		}
		return split;
	}

	/*
	 * Font metrics are expected to be monotonic as code points are appended.
	 * Binary search finds the largest fitting prefix under that contract. The
	 * one-code-point fallback still guarantees progress for a pathological
	 * callback which violates it.
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
		long[] cost = new long[tokens.size() + 1];
		int[] next = new int[tokens.size()];
		Arrays.fill(cost, Long.MAX_VALUE);
		cost[tokens.size()] = 0L;

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
					rendered.codePointCount(0, rendered.length()) == 1 && used > width;
				if (used > width && !forcedSingleCodePoint)
				{
					continue;
				}

				long candidate = end == tokens.size() - 1
					? 0L
					: addCosts(squaredSlack(width, used, forcedSingleCodePoint), cost[end + 1]);
				if (candidate < cost[start])
				{
					cost[start] = candidate;
					next[start] = end + 1;
				}
			}
		}
		return reconstruct(tokens, next);
	}

	private static long squaredSlack(int width, int used, boolean forcedSingleCodePoint)
	{
		if (forcedSingleCodePoint)
		{
			return 0L;
		}
		long slack = (long) width - used;
		if (slack > 3_037_000_499L)
		{
			return MAX_COST;
		}
		return slack * slack;
	}

	private static long addCosts(long left, long right)
	{
		if (left >= MAX_COST || right >= MAX_COST || left > MAX_COST - right)
		{
			return MAX_COST;
		}
		return left + right;
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
					rendered.codePointCount(0, rendered.length()) == 1 && used > width;
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

	private static String join(List<String> tokens, int start, int end)
	{
		StringBuilder joined = new StringBuilder();
		for (int index = start; index < end; index++)
		{
			joined.append(tokens.get(index));
		}
		return joined.toString();
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
