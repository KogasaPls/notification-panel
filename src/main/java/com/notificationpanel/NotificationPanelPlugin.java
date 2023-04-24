package com.notificationpanel;

import com.google.inject.Provides;
import com.notificationpanel.NotificationPanelConfig.TimeUnit;
import com.notificationpanel.views.NotificationPanelView;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(name = "Notification Panel")
public class NotificationPanelPlugin extends Plugin
{
	@Inject
	private NotificationPanelConfig config;
	@Inject
	private ClientUI clientUI;
	@Inject
	private RuneLiteConfig runeLiteConfig;
	@Inject
	private Client client;
	@Inject
	private NotificationPanelView overlay;
	@Inject
	private OverlayManager overlayManager;


	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(overlay);
		overlay.setConfig(config);
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlay.onShutDown();
		overlayManager.remove(overlay);
	}

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked overlayMenuClicked)
	{
		overlay.onOverlayMenuClicked(overlayMenuClicked);
	}


	@Subscribe
	public void onNotificationFired(NotificationFired event)
	{
		overlay.onNotificationFired(event);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("notificationpanel"))
		{
			return;
		}

		overlay.setConfig(config);
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (config.timeUnit() != TimeUnit.TICKS)
		{
			return;
		}

		overlay.onGameTick(tick);
	}

	@Provides
	NotificationPanelConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NotificationPanelConfig.class);
	}

}
