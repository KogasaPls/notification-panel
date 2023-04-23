package com.notificationpanel.viewmodels;

import static com.notificationpanel.Constants.TRANSPARENT;
import com.notificationpanel.NotificationPanelConfig;
import static com.notificationpanel.NotificationPanelPlugin.notificationQueue;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import net.runelite.api.events.GameTick;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.ComponentOrientation;

public class NotificationPanelViewModel implements ViewModel
{
	public boolean Wrap;
	public Rectangle Border;
	public ComponentOrientation Orientation;
	public Point Gap;
	public Color BackgroundColor;
	public boolean Resizable;
	public boolean ClearChildren;
	public OverlayPosition Position;
	public OverlayPriority Priority;
	public int MinWidth;
	public int MaxNotificationsToShow;
	public boolean ShouldRender;

	public NotificationPanelViewModel(NotificationPanelConfig config)
	{
		setConfig(config);

		Wrap = false;
		Border = new Rectangle(0, 0, 0, 0);
		MinWidth = 0;
		Gap = new Point(0, 6);
		Orientation = ComponentOrientation.VERTICAL;
		BackgroundColor = TRANSPARENT;
		Resizable = true;
		ClearChildren = false;
		Position = OverlayPosition.TOP_LEFT;
		Priority = OverlayPriority.LOW;
		MaxNotificationsToShow = config.numToShow();

		updatePreferredSize();
	}

	@Override
	public void setConfig(NotificationPanelConfig config)
	{
		for (NotificationViewModel notification : notificationQueue)
		{
			notification.setConfig(config);
		}
	}

	public void updatePreferredSize()
	{
		int width = 2;
		int minWidth = 500;
		int height = 0;

		for (NotificationViewModel notificationViewModel : notificationQueue)
		{
			width = Math.max(width, notificationViewModel.getWidth());
			minWidth = Math.min(minWidth, notificationViewModel.getMinWidth());
			height = Math.max(height, notificationViewModel.getHeight());
		}

		Border.setSize(new Dimension(width, height));
		MinWidth = minWidth;
	}

	@Override
	public void onTick(GameTick tick)
	{

	}

	@Override
	public void onBeforeRender(Graphics2D _graphics)
	{
		while (notificationQueue.size() > MaxNotificationsToShow)
		{
			notificationQueue.poll();
		}
	}

	@Override
	public boolean shouldRender()
	{
		return false;
	}

	@Override
	public void setPreferredLocation(Point position)
	{
		Border.setLocation(position);
	}

	@Override
	public void setPreferredSize(Dimension dimension)
	{
		Border.setSize(dimension);
	}
}
