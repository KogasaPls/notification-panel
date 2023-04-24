package com.notificationpanel.viewmodels;

import com.notificationpanel.Notification;
import com.notificationpanel.NotificationPanelConfig;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;

@Slf4j
public class NotificationViewModel
{
	private final Rectangle preferredBounds = new Rectangle(0, 0, 250, 60);
	@Getter
	private final Notification notification;
	private final AtomicBoolean changed = new AtomicBoolean(true);
	public Font font;
	public Color backgroundColor;
	public Color foregroundColor;
	public Color borderColor;
	public String body;
	public String time;
	public int padding;
	public NotificationPanelConfig.TimeUnit timeUnit;
	public Rectangle bounds = preferredBounds;
	@Getter
	private boolean displayed;

	public NotificationViewModel(Notification notification, NotificationPanelConfig config)
	{
		this.notification = notification;
		setConfig(config);
	}

	public void setConfig(NotificationPanelConfig config)
	{
		this.font = config.fontType().getFont();
		this.displayed = config.visibility();
		this.backgroundColor = config.bgColor();
		this.foregroundColor = config.fgColor();
		this.borderColor = config.borderColor();
		this.body = notification.message;
		this.timeUnit = config.timeUnit();

		log.debug("Config updated");
		setChanged(true);
	}

	public Dimension getPreferredSize()
	{
		return preferredBounds.getSize();
	}

	public void setPreferredSize(Dimension preferredSize)
	{
		final Dimension currentSize = getPreferredSize();
		if (currentSize.equals(preferredSize))
		{
			return;
		}

		log.debug("Preferred size: {} -> {}", currentSize, preferredSize);
		preferredBounds.setSize(preferredSize);
		setChanged(true);

	}

	public Point getPreferredLocation()
	{
		return preferredBounds.getLocation();
	}

	public void setPreferredLocation(Point preferredLocation)
	{
		final Point currentLocation = getPreferredLocation();
		if (currentLocation.equals(preferredLocation))
		{
			return;
		}

		log.debug("Preferred location: {} -> {}", currentLocation, preferredLocation);
		preferredBounds.setLocation(preferredLocation);
		setChanged(true);
	}

	public void onGameTick(GameTick tick)
	{
		notification.onGameTick(tick);
		displayed = !notification.isExpired();
		time = String.format("%d", notification.elapsedGameTicks);
	}

	public boolean isChanged()
	{
		return changed.getAndSet(false);
	}

	public void setChanged(boolean changed)
	{
		log.debug("Changed: {} -> {}", this.changed.get(), changed);
		this.changed.getAndSet(changed);
	}
}
