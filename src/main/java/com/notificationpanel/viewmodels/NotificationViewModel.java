package com.notificationpanel.viewmodels;

import com.notificationpanel.Notification;
import com.notificationpanel.NotificationPanelConfig;
import com.notificationpanel.utils.FontMetricsCache;
import com.notificationpanel.utils.StringWrapper;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import lombok.Getter;
import net.runelite.api.events.GameTick;

public class NotificationViewModel implements ViewModel
{
	@Getter
	private final Notification notification;
	public Font font;
	public boolean ShouldRender;
	public Rectangle outerBounds;
	private Color backgroundColor;
	private Color foregroundColor;
	private Color borderColor;
	private Rectangle innerBounds;
	private ArrayList<String> bodyLines;
	private boolean isDisplayed;

	public NotificationViewModel(Notification notification, NotificationPanelConfig config)
	{
		this.notification = notification;
		setConfig(config);
	}

	@Override
	public void setConfig(NotificationPanelConfig config)
	{

		this.font = config.fontType().getFont();
		this.isDisplayed = config.visibility();
		this.backgroundColor = config.bgColor();
		this.foregroundColor = config.fgColor();
		this.borderColor = config.borderColor();
		this.outerBounds = new Rectangle(0, 0, config.width(), config.height());
		this.innerBounds = new Rectangle(config.padding(), config.padding(), config.width() - 2 * config.padding(), config.height() - 2 * config.padding());
		this.bodyLines = StringWrapper.wrapString(notification.message, font, innerBounds.width);
	}


	public Rectangle getBounds()
	{
		return outerBounds;
	}

	public int getWidth()
	{
		return outerBounds.width;
	}

	public int getHeight()
	{
		return outerBounds.height;
	}

	public int getMinWidth()
	{
		return innerBounds.width;
	}

	@Override
	public void onTick(GameTick tick)
	{

	}

	@Override
	public void onBeforeRender(Graphics2D graphics)
	{
		if (!isDisplayed)
		{
			return;
		}

		// TODO: Update time strings
	}

	@Override
	public boolean shouldRender()
	{
		return false;
	}

	public void setPreferredLocation(Point position)
	{
		outerBounds.setLocation(position);
	}

	public void setPreferredSize(Dimension dimension)
	{
		outerBounds.setSize(dimension);
	}
}
