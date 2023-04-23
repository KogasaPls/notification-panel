package com.notificationpanel.utils;

import java.awt.Font;
import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.Arrays;

public final class StringWrapper
{
	private StringWrapper()
	{
	}

	public static ArrayList<String> wrapString(String string, Font font, int maxWidth)
	{
		FontMetrics fontMetrics = FontMetricsCache.GetFontMetrics(font);

		int spaceWidth = fontMetrics.stringWidth(" ");
		String[] words = string.split(" ");
		int[] wordWidths = getWordWidths(words, fontMetrics, maxWidth);
		int[] lineBreaks = Arrays.stream(getMaxDensityCenteredWrapping(wordWidths, maxWidth, spaceWidth)).distinct().toArray();

		ArrayList<String> lines = new ArrayList<>();

		if (lineBreaks.length <= 1)
		{
			lines.add(string);
			return lines;
		}

		for (int i = 0; i < lineBreaks.length - 1; i++)
		{
			String[] wordsInLine = Arrays.copyOfRange(words, lineBreaks[i], lineBreaks[i + 1]);
			lines.add(String.join(" ", wordsInLine));
		}

		return lines;


	}


	private static int[] getWordWidths(String[] words, FontMetrics fontMetrics, int maxWidth)
	{
		int[] wordWidths = new int[words.length];

		for (int i = 0; i < words.length; i++)
		{
			String word = words[i];
			int width = fontMetrics.stringWidth(word);
			if (width > maxWidth)
			{
				word = ellipsize(word, fontMetrics, maxWidth);
				wordWidths[i] = fontMetrics.stringWidth(word);
			}
		}

		return wordWidths;
	}

	private static String ellipsize(String word, FontMetrics fontMetrics, int maxWidth)
	{
		int width = fontMetrics.stringWidth(word);
		if (width <= maxWidth)
		{
			return word;
		}

		final String ellipsis = "...";
		int ellipsisWidth = fontMetrics.stringWidth(ellipsis);
		while (width + ellipsisWidth > maxWidth)
		{
			word = word.substring(0, word.length() - 1);
			width = fontMetrics.stringWidth(word);
		}

		return word + ellipsis;
	}

	private static int[] getMaxDensityCenteredWrapping(int[] wordWidths, int maxWidth, int spaceWidth)
	{
		int currentFirstWordInLine, newFirstWordInLine;
		int numWords = wordWidths.length;

		int costIfNextWordIsAddedToLine;

		int[] costOfLineContainingWord = new int[numWords];
		int[] firstWordInLineContainingWord = new int[numWords];

		costOfLineContainingWord[numWords - 1] = 0;
		firstWordInLineContainingWord[numWords - 1] = numWords - 1;

		for (currentFirstWordInLine = numWords - 2;
			 currentFirstWordInLine >= 0;
			 currentFirstWordInLine--)
		{
			int remainingLineLength = maxWidth + 1;

			costOfLineContainingWord[currentFirstWordInLine] = Integer.MAX_VALUE;
			for (newFirstWordInLine = currentFirstWordInLine;
				 newFirstWordInLine < numWords;
				 newFirstWordInLine++)
			{
				remainingLineLength -= (wordWidths[newFirstWordInLine] + spaceWidth);

				// We can't add any more words to this line, start a new one.
				if (remainingLineLength < 0)
				{
					break;
				}

				// The last word in the string is always free.
				if (newFirstWordInLine == numWords - 1)
				{
					costIfNextWordIsAddedToLine = 0;
				}
				else
				{
					costIfNextWordIsAddedToLine = remainingLineLength * remainingLineLength + costOfLineContainingWord[newFirstWordInLine + 1];
				}

				if (costIfNextWordIsAddedToLine < costOfLineContainingWord[currentFirstWordInLine])
				{
					costOfLineContainingWord[currentFirstWordInLine] = costIfNextWordIsAddedToLine;
					firstWordInLineContainingWord[currentFirstWordInLine] = newFirstWordInLine;
				}

			}
		}

		return firstWordInLineContainingWord;
	}
}
