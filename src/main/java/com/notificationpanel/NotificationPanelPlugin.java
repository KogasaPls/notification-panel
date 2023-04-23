package com.notificationpanel;

import com.google.inject.Provides;
import static com.notificationpanel.Constants.CLEAR_ALL;
import com.notificationpanel.NotificationPanelConfig.TimeUnit;
import com.notificationpanel.viewmodels.NotificationViewModel;
import java.awt.TrayIcon;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
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
import net.runelite.client.ui.overlay.OverlayMenuEntry;

@Slf4j
@PluginDescriptor(name = "Notification Panel")
public class NotificationPanelPlugin extends Plugin
{
	public static final ConcurrentLinkedQueue<NotificationViewModel> notificationQueue = new ConcurrentLinkedQueue<>();
	@Inject
	private NotificationPanelConfig config;
	@Inject
	private ClientUI clientUI;
	@Inject
	private RuneLiteConfig runeLiteConfig;
	@Inject
	private Client client;
	@Inject
	private NotificationPanelOverlay overlay;
	@Inject
	private OverlayManager overlayManager;

	private static void schedulePopNotificationQueue(long popAfterMs)
	{
		java.util.Timer timer = new java.util.Timer();

		TimerTask task = new TimerTask()
		{
			public void run()
			{
				notificationQueue.poll();
				timer.cancel();
			}
		};

		timer.schedule(task, popAfterMs);
	}

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		notificationQueue.clear();
		overlayManager.remove(overlay);
	}

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked overlayMenuClicked)
	{
		OverlayMenuEntry overlayMenuEntry = overlayMenuClicked.getEntry();
		if (overlayMenuEntry.getMenuAction() == MenuAction.RUNELITE_OVERLAY &&
			overlayMenuClicked.getOverlay() == overlay)
		{
			final String option = overlayMenuClicked.getEntry().getOption();

			if (option.equals(CLEAR_ALL))
			{
				notificationQueue.clear();
			}
		}
	}


	@Subscribe
	public void onNotificationFired(NotificationFired event)
	{
		Notification notification = new Notification(event.getMessage(), new GameTick());
		NotificationViewModel notificationViewModel = new NotificationViewModel(notification, config);
		notificationQueue.add(notificationViewModel);

		if (config.timeUnit() == TimeUnit.SECONDS)
		{
			schedulePopNotificationQueue(config.expireTime() * 1000L);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("notificationpanel"))
		{
			return;
		}

		overlay.shouldUpdateFontMetricsCache = true;
		overlay.setConfig(config);
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (config.timeUnit() != TimeUnit.TICKS)
		{
			return;
		}

		notificationQueue.forEach(notification -> notification.onTick(tick));
	}

	@Provides
	NotificationPanelConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NotificationPanelConfig.class);
	}

}
