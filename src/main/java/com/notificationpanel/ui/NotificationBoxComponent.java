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

import com.notificationpanel.layout.NotificationText;
import com.notificationpanel.state.NotificationState;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

/**
 * Renders a single immutable notification snapshot as one box.
 *
 * <p>The component holds no state of its own between renders; geometry comes from the snapshot
 * and the caller-supplied preferred size. Wrapping is looked up in a cache the caller owns,
 * keyed by text, width and font, so a repeated frame costs a map lookup rather than the wrap.</p>
 */
public final class NotificationBoxComponent implements LayoutableRenderableEntity
{
	private static final int VERTICAL_PADDING = 6;
	/** Wider than the vertical padding, so wrapped text keeps clear of the border. */
	private static final int HORIZONTAL_PADDING = 12;

	private final NotificationState.Snapshot snapshot;
	private final NotificationText.Cache wrapCache;
	private Point preferredLocation = new Point();
	private Dimension preferredSize = new Dimension();
	private final Rectangle bounds = new Rectangle();

	public NotificationBoxComponent(NotificationState.Snapshot snapshot)
	{
		this(snapshot, new NotificationText.Cache());
	}

	public NotificationBoxComponent(NotificationState.Snapshot snapshot,
		NotificationText.Cache wrapCache)
	{
		this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
		this.wrapCache = Objects.requireNonNull(wrapCache, "wrapCache");
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Font originalFont = graphics.getFont();
		Color originalColor = graphics.getColor();
		try
		{
			graphics.setFont(snapshot.getFont());
			FontMetrics metrics = graphics.getFontMetrics();

			// Every box spans the panel width. Sizing each one to its own text made consecutive
			// short notifications differ by a few pixels, which reads as ragged; a shared width
			// is what the published plugin showed and looks settled even though it means a box's
			// width is not derived from its own content.
			int width = Math.max(1, preferredSize.width);
			int contentWidth = Math.max(1, width - 2 * HORIZONTAL_PADDING);
			List<String> lines = wrapCache.wrap(
				snapshot.getMessage(), contentWidth, snapshot.getFont(), metrics::stringWidth);

			// The label is wrapped on the same terms as the message. An hour-scale label such as
			// "1h 2m 3s ago" is wider than a narrow panel, and drawing it unwrapped would paint
			// it outside the box.
			String timeLabel = snapshot.getTimeLabel();
			List<String> timeLines = timeLabel == null
				? Collections.emptyList()
				: wrapCache.wrap(timeLabel, contentWidth, snapshot.getFont(),
					metrics::stringWidth);
			int lineHeight = metrics.getHeight();
			int lineCount = lines.size() + timeLines.size();
			int height = 2 * VERTICAL_PADDING + lineCount * lineHeight;

			int x = preferredLocation.x;
			int y = preferredLocation.y;

			int alpha = (snapshot.getOpacityPercent() * 255 + 50) / 100;
			Color background = new Color(snapshot.getBackgroundRgb() | (alpha << 24), true);
			paintBackground(graphics, background, x, y, width, height);

			int baseline = y + VERTICAL_PADDING + metrics.getAscent();
			for (String line : lines)
			{
				drawShadowed(graphics, line, centeredX(metrics, line, x, width), baseline);
				baseline += lineHeight;
			}
			for (String line : timeLines)
			{
				drawShadowed(graphics, line, centeredX(metrics, line, x, width), baseline);
				baseline += lineHeight;
			}

			bounds.setBounds(x, y, width, height);
			return new Dimension(width, height);
		}
		finally
		{
			graphics.setFont(originalFont);
			graphics.setColor(originalColor);
		}
	}

	@Override
	public Rectangle getBounds()
	{
		return bounds;
	}

	@Override
	public void setPreferredLocation(Point location)
	{
		this.preferredLocation = location;
	}

	@Override
	public void setPreferredSize(Dimension dimension)
	{
		this.preferredSize = dimension;
	}

	/**
	 * Paints the box the way the published plugin did. Each notification used to be a RuneLite
	 * {@code PanelComponent}, whose {@code BackgroundComponent} fills a square rectangle and then
	 * outlines it twice: a darker pass at 80% of the background and a lighter one at 120%, both at
	 * 140% of its alpha. Reproduced here so the box keeps the shape it had. The opacity it is
	 * filled with does change: the configured percentage is now scaled to an 8-bit alpha, where
	 * it used to be applied as an alpha directly, making the default markedly more opaque.
	 */
	private static void paintBackground(Graphics2D graphics, Color background, int x, int y,
		int width, int height)
	{
		graphics.setColor(background);
		graphics.fillRect(x, y, width, height);

		int borderAlpha = scale(background.getAlpha(), 1.4f);
		graphics.setColor(new Color(scale(background.getRed(), 0.8f),
			scale(background.getGreen(), 0.8f), scale(background.getBlue(), 0.8f), borderAlpha));
		graphics.drawRect(x, y, width - 1, height - 1);
		graphics.setColor(new Color(scale(background.getRed(), 1.2f),
			scale(background.getGreen(), 1.2f), scale(background.getBlue(), 1.2f), borderAlpha));
		graphics.drawRect(x + 1, y + 1, width - 3, height - 3);
	}

	private static int scale(int component, float factor)
	{
		return Math.max(0, Math.min(255, (int) (component * factor)));
	}

	/**
	 * Centers a line in the box. The published plugin built each line as a {@code TitleComponent}
	 * specifically for its centering, so the rewrite matches that rather than left-aligning.
	 */
	private static int centeredX(FontMetrics metrics, String line, int x, int width)
	{
		return x + Math.max(HORIZONTAL_PADDING, (width - metrics.stringWidth(line)) / 2);
	}

	private static void drawShadowed(Graphics2D graphics, String text, int x, int baseline)
	{
		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 1, baseline + 1);
		graphics.setColor(Color.WHITE);
		graphics.drawString(text, x, baseline);
	}
}
