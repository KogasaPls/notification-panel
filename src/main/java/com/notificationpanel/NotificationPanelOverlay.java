package com.notificationpanel;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Inject;
import lombok.Getter;
import lombok.Setter;
import static net.runelite.api.MenuAction.RUNELITE_OVERLAY;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.ComponentOrientation;

public class NotificationPanelOverlay extends OverlayPanel
{
	static final String CLEAR_ALL = "Clear";
	static final int GAP = 6;
	static final Color TRANSPARENT = new Color(0, 0, 0, 0);
	static final private Dimension DEFAULT_SIZE = new Dimension(250, 60);
	@Getter
	@Setter
	static ConcurrentLinkedQueue<Notification> notificationQueue =
			new ConcurrentLinkedQueue<>();
	@Setter
	static boolean shouldUpdateBoxes;
	@Setter
	static boolean shouldUpdateTimers;
	static private Dimension preferredSize = DEFAULT_SIZE;
	final private NotificationPanelConfig config;

	@Inject
	private NotificationPanelOverlay(NotificationPanelConfig config)
	{
		this.config = config;

		setResizable(true);
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(OverlayPriority.LOW);
		setClearChildren(false);

		panelComponent.setWrap(false);
		panelComponent.setBorder(new Rectangle(0, 0, 0, 0));
		panelComponent.setOrientation(ComponentOrientation.VERTICAL);
		panelComponent.setGap(new Point(0, GAP));
		panelComponent.setBackgroundColor(TRANSPARENT);

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
			preferredSize = DEFAULT_SIZE;
			setPreferredSize(preferredSize);
			shouldUpdateBoxes = true;
			shouldUpdateTimers = true;
		}
		// if we just compare the Dimension objects, they will always be different
		// so just look at the widths. we can't manually control the height anyway, so ignore it.
		else if (newPreferredSize.width != preferredSize.width)
		{
			preferredSize = newPreferredSize;
			shouldUpdateBoxes = true;
			shouldUpdateTimers = true;
		}

		// only rebuild the panel when necessary
		if (shouldUpdateBoxes)
		{
			while (notificationQueue.size() > config.numToShow())
			{
				notificationQueue.poll();
			}

			notificationQueue.forEach(s -> s.makeBox(graphics, preferredSize));
		}

		// true after each game tick, or after a notification's 1s timer triggers
		if (config.showTime() && shouldUpdateTimers)
		{
			notificationQueue.forEach(Notification::updateTimeString);
		}

		if (shouldUpdateBoxes || shouldUpdateTimers)
		{
			panelComponent.getChildren().clear();
			notificationQueue.forEach(s -> panelComponent.getChildren().add(s.getBox()));
			updatePanelSize();

			shouldUpdateBoxes = false;
			shouldUpdateTimers = false;
		}

		return super.render(graphics);
	}

	void updatePanelSize()
	{
		int width = 2;
		int minWidth = 500;
		int height = 0;

		for (Notification notification : notificationQueue)
		{
			width = Math.max(width, notification.getWidth());
			minWidth = Math.min(minWidth, notification.getMaxWordWidth());
			height = Math.max(height, notification.getHeight());
		}

		setPreferredSize(new Dimension(width, height));
		setMinimumSize(minWidth);
	}

}
