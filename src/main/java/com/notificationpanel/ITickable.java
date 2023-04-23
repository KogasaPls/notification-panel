package com.notificationpanel;

import net.runelite.api.events.GameTick;

public interface ITickable
{
	void onTick(GameTick tick);
}


