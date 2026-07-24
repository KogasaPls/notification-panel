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

			assertTrue(rendered.width <= 80);
			assertTrue(rendered.height
				> graphics.getFontMetrics(snapshot.getFont()).getHeight());
			assertEquals((75 * 255 + 50) / 100,
				new Color(image.getRGB(10, 10), true).getAlpha());
		}
		finally
		{
			graphics.dispose();
		}
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
	public void rendersHourScaleLabelWithoutOverflowingWidth()
	{
		Dimension rendered = renderedSizeAtWidth(
			snapshot("drop", 0x123456, 75, FONT, "1h 2m 3s ago"), 120);
		assertTrue(rendered.width <= 120);
	}

	@Test
	public void narrowWidthClampsToAtLeastOnePixelContent()
	{
		Dimension rendered = renderedSizeAtWidth(snapshot("wide", 0x123456, 75, FONT, null), 1);
		assertTrue(rendered.width >= 1);
		assertTrue(rendered.height > 0);
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
