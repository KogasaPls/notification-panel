package com.notificationpanel;

import net.runelite.api.events.GameTick;

public class Notification
{
	public String message;
	public GameTick createdOn;

	public Notification(String message, GameTick createdOn)
	{
		this.message = message;
		this.createdOn = createdOn;
	}
}
