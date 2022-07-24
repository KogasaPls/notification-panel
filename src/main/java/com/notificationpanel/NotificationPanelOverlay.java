package com.notificationpanel;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Inject;
import lombok.Setter;
import static net.runelite.api.MenuAction.RUNELITE_OVERLAY;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.PanelComponent;

public class NotificationPanelOverlay extends OverlayPanel
{
	static final String CLEAR_ALL = "Clear";
	static final int GAP = 6;
	static final private Dimension DEFAULT_SIZE = new Dimension(250, 60);
	static ConcurrentLinkedQueue<Notification> notificationQueue = new ConcurrentLinkedQueue<>();
	static boolean shouldUpdate;
	static private Dimension preferredSize = new Dimension(250, 60);
	static private Instant lastTimeUpdate = Instant.EPOCH;
	final private NotificationPanelConfig config;

	@Setter
	int maxWordWidth;

	@Setter
	String[] wrapped;

	@Setter
	PanelComponent box;

	@Inject
	private NotificationPanelOverlay(NotificationPanelConfig config,
									 NotificationPanelPlugin plugin)
	{
		this.config = config;

		setResizable(true);
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(OverlayPriority.LOW);

		panelComponent.setWrap(false);
		panelComponent.setBorder(new Rectangle(0, 0, 0, 0));
		panelComponent.setOrientation(ComponentOrientation.VERTICAL);
		panelComponent.setGap(new Point(0, GAP));
		panelComponent.setBackgroundColor(new Color(0, 0, 0, 0));

		getMenuEntries().add(new OverlayMenuEntry(RUNELITE_OVERLAY, CLEAR_ALL,
				"Notification " + "panel"));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (notificationQueue.isEmpty())
		{
			return null;
		}

		graphics.setFont(config.fontType().getFont());

		final Dimension newPreferredSize = getPreferredSize();

		if (newPreferredSize == null)
		{
			System.out.println("newPreferredSize is null");
			preferredSize = DEFAULT_SIZE;
			setPreferredSize(preferredSize);
			shouldUpdate = true;
		}
		// if we just compare the Dimension objects, they will always be different
		// so just look at the widths. we can't manually control the height anyway, so ignore it.
		else if (newPreferredSize.width != preferredSize.width)
		{
			System.out.println("updating preferredSize from " + preferredSize.width + " to " +
					newPreferredSize.width);
			preferredSize = newPreferredSize;
			shouldUpdate = true;
		}

		// only rebuild the panel when necessary
		if (shouldUpdate)
		{
			System.out.println("Updating notification panel " + notificationQueue.size());

			while (notificationQueue.size() > config.numToShow())
			{
				notificationQueue.poll();
			}

			notificationQueue.forEach(s -> s.makeBox(graphics, config.showTime(), preferredSize));

			shouldUpdate = false;
		}

		if (config.showTime() && Instant.now().isAfter(lastTimeUpdate.plus(Duration.ofSeconds(1))))
		{
			lastTimeUpdate = Instant.now();
			notificationQueue.forEach(s -> s.updateTimeString(config.duration()));
			notificationQueue.forEach(s -> panelComponent.getChildren().add(s.box));
		}
		else
		{
			notificationQueue.forEach(s -> panelComponent.getChildren().add(s.box));
		}

		updatePanelSize(preferredSize);
		return super.render(graphics);
	}

	void updatePanelSize(Dimension preferredSize)
	{
		int width = 50;
		int height = 0;
		int minWidth = 75;

		for (Notification notification : notificationQueue)
		{
			width = Math.max(width, notification.getWidth());
			minWidth = Math.min(minWidth, notification.getMaxWordWidth());
			height += notification.getHeight() + GAP;
		}

		preferredSize = new Dimension(width, height);
		setPreferredSize(preferredSize);
		setMinimumSize(minWidth);
	}
}
