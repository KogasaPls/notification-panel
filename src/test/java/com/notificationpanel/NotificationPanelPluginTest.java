package com.notificationpanel;

import java.awt.TrayIcon;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import net.runelite.client.RuneLite;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.PluginManager;

public class NotificationPanelPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(NotificationPanelPlugin.class);
		RuneLite.main(args);

		PluginManager pluginManager = RuneLite.getInjector().getInstance(PluginManager.class);
		Optional<NotificationPanelPlugin> notificationPanelPluginOption = pluginManager.getPlugins().stream()
			.filter(plugin -> plugin instanceof NotificationPanelPlugin)
			.map(plugin -> (NotificationPanelPlugin) plugin)
			.findFirst();

		if (notificationPanelPluginOption.isPresent())
		{
			NotificationPanelPlugin notificationPanelPlugin = notificationPanelPluginOption.get();
			//ScheduleNotification(notificationPanelPlugin);
		}
		else
		{
			System.out.println("NotificationPanelPlugin not found");
		}

	}

	private static void ScheduleNotification(NotificationPanelPlugin plugin)
	{
		NotificationFired event = new NotificationFired("Test", TrayIcon.MessageType.INFO);

		Timer timer = new Timer();
		timer.schedule(new TimerTask()
		{
			@Override
			public void run()
			{
				plugin.onNotificationFired(event);
			}
		}, 5000, 5000);
	}
}
