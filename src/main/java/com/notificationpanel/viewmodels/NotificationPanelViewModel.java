package com.notificationpanel.viewmodels;

import static com.notificationpanel.Constants.CLEAR_ALL;
import static com.notificationpanel.Constants.TRANSPARENT;
import com.notificationpanel.Notification;
import com.notificationpanel.NotificationPanelConfig;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Queue;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.components.ComponentOrientation;

@Slf4j
public class NotificationPanelViewModel
{
	private final AtomicBoolean changed = new AtomicBoolean(true);
	public Rectangle Border = new Rectangle(0, 0, 0, 0);
	public Rectangle preferredBounds = new Rectangle(0, 0, 0, 0);
	public ComponentOrientation Orientation = ComponentOrientation.VERTICAL;
	public Point Gap = new Point(0, 6);
	public Color BackgroundColor = TRANSPARENT;
	public Queue<NotificationViewModel> queue = new ConcurrentLinkedQueue<>();
	private NotificationPanelConfig config;

	public NotificationPanelViewModel(NotificationPanelConfig config)
	{
		setConfig(config);
	}

	public void setConfig(NotificationPanelConfig config)
	{
		this.config = config;
		for (NotificationViewModel notification : queue)
		{
			notification.setConfig(config);
		}

		changed.set(true);
	}

	public Dimension getPreferredSize()
	{
		return preferredBounds.getSize();
	}

	public void setPreferredSize(Dimension size)
	{
		if (size == null)
		{
			return;
		}

		if (!queue.isEmpty())
		{
			int minWidth = Integer.MAX_VALUE;
			int minHeight = 0;

			for (NotificationViewModel notificationViewModel : queue)
			{
				minWidth = Math.min(minWidth, notificationViewModel.bounds.width);
				minHeight += notificationViewModel.bounds.height;
			}

			final int height = Math.max(size.height, minHeight);
			final int width = Math.max(size.width, minWidth);
			size = new Dimension(width, height);
		}


		if (size.equals(getPreferredSize()))
		{
			return;
		}

		log.debug("Changing size from {} to {}", getPreferredSize(), size);
		preferredBounds.setSize(size);
		changed.set(true);
	}

	public void clearAll()
	{
		log.debug("Clearing all notifications ...");
		queue.clear();
		changed.set(true);
	}

	public Point getPreferredLocation()
	{
		return preferredBounds.getLocation();
	}

	public void setPreferredLocation(Point location)
	{
		if (location == null)
		{
			return;
		}

		Point currentLocation = preferredBounds.getLocation();
		if (currentLocation.equals(location))
		{
			return;
		}

		preferredBounds.setLocation(location);
		changed.set(true);
	}

	public void onGameTick(GameTick tick)
	{
		if (config.expireTime() == 0)
		{
			return;
		}

		queue.parallelStream().forEach(n -> {
			n.onGameTick(tick);
			if (!n.isDisplayed())
			{
				queue.poll();
				changed.set(true);
			}
		});
	}

	public void onNotificationFired(NotificationFired notificationFired)
	{
		log.debug("Notification fired: {}", notificationFired.getMessage());
		Notification notification = new Notification(notificationFired.getMessage(), config.expireTime(), config.timeUnit());
		NotificationViewModel notificationViewModel = new NotificationViewModel(notification, config);
		queue.add(notificationViewModel);

		while (queue.size() > config.numToShow())
		{
			queue.poll();
			log.debug("Dismissing notification because the queue is full.");
		}

		if (config.expireTime() != 0 && config.timeUnit() == NotificationPanelConfig.TimeUnit.SECONDS)
		{
			schedulePopAfterMs(config.expireTime() * 1000L);
		}

		changed.set(true);
	}

	private void schedulePopAfterMs(long popAfterMs)
	{
		java.util.Timer timer = new java.util.Timer();

		TimerTask task = new TimerTask()
		{
			public void run()
			{
				log.debug("Dismissing 1 of {} notifications ...", queue.size());
				queue.poll();
				changed.set(true);
				timer.cancel();
			}
		};

		timer.schedule(task, popAfterMs);
	}

	public void onOverlayMenuEntryClicked(OverlayMenuEntry overlayMenuEntry)
	{
		String option = overlayMenuEntry.getOption();
		if (option.equals(CLEAR_ALL))
		{
			clearAll();
			changed.set(true);
		}
	}

	public boolean isChanged()
	{
		return changed.getAndSet(false);
	}
}
