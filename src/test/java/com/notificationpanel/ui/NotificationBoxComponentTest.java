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
package com.notificationpanel.ui;

import com.notificationpanel.state.NotificationState;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NotificationBoxComponentTest
{
	private static final Font FONT = new Font("Dialog", Font.PLAIN, 12);
	/** Mirror NotificationBoxComponent's padding so sizes can be asserted exactly. */
	private static final int PADDING = 6;

	@Test
	public void convertsPercentageToAlphaExactlyOnceAndWrapsToWidth()
	{
		BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			NotificationState.Snapshot snapshot = snapshot(
				"one two three four", 0x123456, 75, FONT, null);
			NotificationBoxComponent component = new NotificationBoxComponent(snapshot);
			component.setPreferredLocation(new Point(0, 0));
			component.setPreferredSize(new Dimension(80, 0));
			Dimension rendered = component.render(graphics);

			// "one two three four" cannot fit 80px at 12pt, so it must have wrapped onto
			// several lines rather than run past the edge of the box.
			int lineHeight = graphics.getFontMetrics(snapshot.getFont()).getHeight();
			assertTrue(rendered.height + " should hold >1 line of " + lineHeight,
				rendered.height - 2 * PADDING >= 2 * lineHeight);
			assertEquals(80, rendered.width);
			assertEquals((75 * 255 + 50) / 100,
				new Color(image.getRGB(10, 10), true).getAlpha());
		}
		finally
		{
			graphics.dispose();
		}
	}

	@Test
	public void keepsThePublishedSquareBoxWithItsDoubleBorder()
	{
		// The published plugin drew each notification as a RuneLite PanelComponent, whose
		// BackgroundComponent fills a square rectangle and outlines it twice. Losing the corners
		// and the border changed the plugin's look on upgrade.
		BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			NotificationBoxComponent component = new NotificationBoxComponent(
				snapshot("hi", 0x808080, 100, FONT, null));
			component.setPreferredLocation(new Point(0, 0));
			component.setPreferredSize(new Dimension(80, 0));
			Dimension rendered = component.render(graphics);

			// Square: the top-left pixel is painted, which a rounded corner would have left clear.
			assertEquals(255, new Color(image.getRGB(0, 0), true).getAlpha());
			// Outer border is the background darkened to 80%, inner is brightened to 120%.
			assertEquals(new Color(102, 102, 102), stripAlpha(image.getRGB(0, 0)));
			assertEquals(new Color(153, 153, 153), stripAlpha(image.getRGB(1, 1)));
			// Interior keeps the configured background.
			assertEquals(new Color(128, 128, 128), stripAlpha(image.getRGB(4, 4)));
			// And the border tracks the far edges too.
			assertEquals(new Color(102, 102, 102),
				stripAlpha(image.getRGB(rendered.width - 1, rendered.height - 1)));
		}
		finally
		{
			graphics.dispose();
		}
	}

	@Test
	public void centersEachLineInTheBox()
	{
		BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			// The short time label under a wide message should sit centred in the box, the way
			// TitleComponent laid the published plugin out, not flush against the padding.
			NotificationBoxComponent component = new NotificationBoxComponent(
				snapshot("aaaaaaaaaaaaaaaaaaaa", 0x000000, 0, FONT, "3s"));
			component.setPreferredLocation(new Point(0, 0));
			component.setPreferredSize(new Dimension(280, 0));
			Dimension rendered = component.render(graphics);

			int expectedLeft = (rendered.width - labelWidth("3s")) / 2;
			int firstInk = firstInkColumnInRows(image, rendered.height - PADDING - lineHeight(),
				rendered.height - PADDING);
			assertTrue("expected the label near " + expectedLeft + " but found ink at " + firstInk,
				Math.abs(firstInk - expectedLeft) <= 2);
			assertTrue("a centred label should not start at the left padding",
				firstInk > 2 * PADDING);
		}
		finally
		{
			graphics.dispose();
		}
	}

	@Test
	public void everyNotificationSharesThePanelWidth()
	{
		// Sizing each box to its own text made consecutive short notifications differ by a few
		// pixels, which looks ragged. A shared width is visually settled.
		Dimension shortBox = renderedSizeAtWidth(snapshot("hi", 0x123456, 75, FONT, null), 240);
		Dimension slightlyLonger = renderedSizeAtWidth(
			snapshot("hi there", 0x123456, 75, FONT, null), 240);
		Dimension wrapping = renderedSizeAtWidth(
			snapshot("a considerably longer notification message", 0x123456, 75, FONT, null), 240);

		assertEquals(240, shortBox.width);
		assertEquals(240, slightlyLonger.width);
		assertEquals(240, wrapping.width);
		// The long one still wraps rather than overflowing.
		assertTrue(wrapping.height > shortBox.height);
	}

	private static Color stripAlpha(int rgb)
	{
		return new Color(rgb & 0xFFFFFF);
	}

	private static int firstInkColumnInRows(BufferedImage image, int fromRow, int toRow)
	{
		for (int x = 0; x < image.getWidth(); x++)
		{
			for (int y = Math.max(0, fromRow); y < Math.min(image.getHeight(), toRow); y++)
			{
				if ((image.getRGB(x, y) & 0xFFFFFF) != 0 && (image.getRGB(x, y) >>> 24) != 0)
				{
					return x;
				}
			}
		}
		return -1;
	}

	@Test
	public void opacityZeroAndHundredMapToTransparentAndOpaqueBackground()
	{
		assertEquals(0, backgroundAlphaAt(snapshot("hi", 0x101010, 0, FONT, null)));
		assertEquals(255, backgroundAlphaAt(snapshot("hi", 0x101010, 100, FONT, null)));
	}

	@Test
	public void emptyMessageWithTimeLabelStillReservesTwoLines()
	{
		Dimension withLabel = renderedSize(snapshot("", 0x222222, 75, FONT, "3s"));
		Dimension withoutLabel = renderedSize(snapshot("", 0x222222, 75, FONT, null));
		int lineHeight = lineHeight();
		assertEquals(withoutLabel.height + lineHeight, withLabel.height);
	}

	@Test
	public void nullTimeLabelOmitsTheTimeLine()
	{
		Dimension oneLine = renderedSize(snapshot("word", 0x333333, 75, FONT, null));
		Dimension oneLinePlusTime = renderedSize(snapshot("word", 0x333333, 75, FONT, "1h 2m 3s"));
		assertEquals(oneLine.height + lineHeight(), oneLinePlusTime.height);
	}

	@Test
	public void wrapsAnHourScaleLabelThatCannotFitTheBoxWidth()
	{
		NotificationState.Snapshot snapshot = snapshot("drop", 0x123456, 75, FONT, "1h 2m 3s ago");
		int lineHeight = lineHeight();
		assertTrue("label should be too wide for this test to mean anything",
			labelWidth("1h 2m 3s ago") > 70 - 2 * PADDING);

		int wide = renderedSizeAtWidth(snapshot, 400).height;
		int narrow = renderedSizeAtWidth(snapshot, 70).height;

		// Wide enough for one line each; narrow forces the label itself onto a second line
		// instead of painting it past the edge of the rounded box.
		assertEquals(2 * PADDING + 2 * lineHeight, wide);
		assertEquals(2 * PADDING + 3 * lineHeight, narrow);
	}

	@Test
	public void narrowWidthHardWrapsRatherThanOverflowing()
	{
		int lineHeight = lineHeight();
		Dimension rendered = renderedSizeAtWidth(snapshot("wide", 0x123456, 75, FONT, null), 1);

		assertEquals(1, rendered.width);
		// One code point per line: four lines for "wide", never a single overflowing line.
		assertEquals(2 * PADDING + 4 * lineHeight, rendered.height);
	}

	private static int labelWidth(String text)
	{
		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			return graphics.getFontMetrics(FONT).stringWidth(text);
		}
		finally
		{
			graphics.dispose();
		}
	}

	@Test
	public void boundsMatchTheReturnedDimensionAndLocation()
	{
		BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			NotificationBoxComponent component = new NotificationBoxComponent(
				snapshot("one two three", 0x123456, 75, FONT, "3s"));
			component.setPreferredLocation(new Point(5, 7));
			component.setPreferredSize(new Dimension(90, 0));
			Dimension rendered = component.render(graphics);
			Rectangle bounds = component.getBounds();

			assertEquals(5, bounds.x);
			assertEquals(7, bounds.y);
			assertEquals(rendered.width, bounds.width);
			assertEquals(rendered.height, bounds.height);
		}
		finally
		{
			graphics.dispose();
		}
	}

	@Test
	public void restoresGraphicsFontAndColor()
	{
		BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			Font original = new Font("Serif", Font.ITALIC, 20);
			Color originalColor = Color.RED;
			graphics.setFont(original);
			graphics.setColor(originalColor);
			NotificationBoxComponent component = new NotificationBoxComponent(
				snapshot("text", 0x123456, 75, FONT, null));
			component.setPreferredLocation(new Point(0, 0));
			component.setPreferredSize(new Dimension(80, 0));
			component.render(graphics);

			assertEquals(original, graphics.getFont());
			assertEquals(originalColor, graphics.getColor());
		}
		finally
		{
			graphics.dispose();
		}
	}

	private static int backgroundAlphaAt(NotificationState.Snapshot snapshot)
	{
		BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			NotificationBoxComponent component = new NotificationBoxComponent(snapshot);
			component.setPreferredLocation(new Point(0, 0));
			component.setPreferredSize(new Dimension(80, 0));
			component.render(graphics);
			return new Color(image.getRGB(3, 3), true).getAlpha();
		}
		finally
		{
			graphics.dispose();
		}
	}

	private static Dimension renderedSize(NotificationState.Snapshot snapshot)
	{
		return renderedSizeAtWidth(snapshot, 80);
	}

	private static Dimension renderedSizeAtWidth(NotificationState.Snapshot snapshot, int width)
	{
		BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			NotificationBoxComponent component = new NotificationBoxComponent(snapshot);
			component.setPreferredLocation(new Point(0, 0));
			component.setPreferredSize(new Dimension(width, 0));
			return component.render(graphics);
		}
		finally
		{
			graphics.dispose();
		}
	}

	private static int lineHeight()
	{
		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			FontMetrics metrics = graphics.getFontMetrics(FONT);
			return metrics.getHeight();
		}
		finally
		{
			graphics.dispose();
		}
	}

	private static NotificationState.Snapshot snapshot(String message, int backgroundRgb,
		int opacityPercent, Font font, String timeLabel)
	{
		return new NotificationState.Snapshot(message, backgroundRgb, opacityPercent, font,
			timeLabel);
	}
}
