package com.notificationpanel;

import java.time.Instant;
import net.runelite.api.events.GameTick;

public class Notification
{
	public String message;
	public Instant createdOnInstant;
	public int elapsedGameTicks;
	public int duration;
	public NotificationPanelConfig.TimeUnit timeUnit;

	public Notification(String message, int duration, NotificationPanelConfig.TimeUnit timeUnit)
	{
		this.message = message;
		this.createdOnInstant = Instant.now();
		this.duration = duration;
		this.timeUnit = timeUnit;
	}

	public boolean isExpired()
	{
		if (duration == 0)
		{
			return false;
		}

		if (timeUnit == NotificationPanelConfig.TimeUnit.SECONDS)
		{
			return Instant.now().isAfter(createdOnInstant.plusSeconds(duration));
		}

		if (timeUnit == NotificationPanelConfig.TimeUnit.TICKS)
		{
			return elapsedGameTicks >= duration;
		}

		return false;
	}

	public void onGameTick(GameTick tick)
	{
		elapsedGameTicks += 1;
	}
}
