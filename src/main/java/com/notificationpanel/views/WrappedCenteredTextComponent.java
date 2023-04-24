package com.notificationpanel.views;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.TextComponent;

@Setter
@Builder
public class WrappedCenteredTextComponent implements LayoutableRenderableEntity
{
	private final String text;
	@Builder.Default
	@Getter
	private final Rectangle bounds = new Rectangle();
	private Font font;
	@Builder.Default
	private Color color = Color.WHITE;
	@Builder.Default
	private Point preferredLocation = new Point();
	@Builder.Default
	private Dimension preferredSize = new Dimension(ComponentConstants.STANDARD_WIDTH, 0);
	@Builder.Default
	private int lineGap = 2;
	private List<String> lines;

	private static String[] splitString(String string)
	{
		return string.split(" ");
	}

	private static int[] getWordWidths(String[] words, FontMetrics fontMetrics)
	{
		int[] wordWidths = new int[words.length];

		for (int i = 0; i < words.length; i++)
		{
			String word = words[i];
			wordWidths[i] = fontMetrics.stringWidth(word);
		}

		return wordWidths;
	}

	private List<String> wrapString(String string, FontMetrics fontMetrics)
	{
		String[] words = splitString(string);
		final int spaceWidth = fontMetrics.stringWidth(" ");
		final int[] wordWidths = getWordWidths(words, fontMetrics);
		final int[] lineBreaks = Arrays.stream(getMaxDensityCenteredWrapping(wordWidths, spaceWidth)).distinct().toArray();

		List<String> lines = new ArrayList<>();

		if (lineBreaks.length <= 1)
		{
			lines.add(string);
			return lines;
		}

		System.out.println("String: " + string);
		System.out.println("Lines: " + (lineBreaks.length - 1));

		if (lineBreaks[0] != 0)
		{
			String[] wordsInLine = Arrays.copyOfRange(words, 0, lineBreaks[0]);
			lines.add(String.join(" ", wordsInLine));
			System.out.println(String.join(" ", wordsInLine));
		}
		for (int i = 0; i < lineBreaks.length - 1; i++)
		{
			String[] wordsInLine = Arrays.copyOfRange(words, lineBreaks[i], lineBreaks[i + 1]);
			lines.add(String.join(" ", wordsInLine));
			System.out.println(String.join(" ", wordsInLine));
		}
		if (lineBreaks[lineBreaks.length - 1] != words.length)
		{
			String[] wordsInLine = Arrays.copyOfRange(words, lineBreaks[lineBreaks.length - 1], words.length);
			lines.add(String.join(" ", wordsInLine));
			System.out.println(String.join(" ", wordsInLine));
		}

		return lines;
	}

	private int[] getMaxDensityCenteredWrapping(int[] wordWidths, int spaceWidth)
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
			int remainingLineLength = preferredSize.width + 1;

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

		System.out.println("Line breaks: " + Arrays.toString(firstWordInLineContainingWord));
		return firstWordInLineContainingWord;
	}

	private Dimension renderLine(Graphics2D graphics, String text, Point position, FontMetrics metrics)
	{
		final int baseX = position.x;
		final int baseY = position.y;
		final TextComponent textComponent = new TextComponent();
		textComponent.setText(text);
		textComponent.setColor(color);
		textComponent.setPosition(new Point(
			baseX + ((preferredSize.width - metrics.stringWidth(text)) / 2),
			baseY + metrics.getHeight() + lineGap));
		return textComponent.render(graphics);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		FontMetrics metrics = graphics.getFontMetrics(font);
		if (lines == null)
		{
			lines = wrapString(text, metrics);
		}

		Point linePosition = new Point(preferredLocation.x, preferredLocation.y);
		Dimension lineDimension;
		for (String line : lines)
		{
			lineDimension = renderLine(graphics, line, linePosition, metrics);
			linePosition.y += lineDimension.height;
		}

		final Dimension dimension = new Dimension(preferredSize.width, linePosition.y - preferredLocation.y);
		bounds.setLocation(preferredLocation);
		bounds.setSize(dimension);
		return dimension;
	}


}
