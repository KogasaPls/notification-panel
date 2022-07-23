package com.notificationpanel;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class NotificationPanelPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(NotificationPanelPlugin.class);
		RuneLite.main(args);
	}
}
