package com.notificationpanel;

import com.google.inject.Provides;
import com.notificationpanel.ConditionalFormatting.ConditionalFormatParser;
import com.notificationpanel.Formatting.Format;
import com.notificationpanel.Formatting.FormatOptions.DurationOption;
import com.notificationpanel.Formatting.FormatOptions.ShowTimeOption;
import com.notificationpanel.Formatting.PartialFormat;
import com.notificationpanel.NotificationPanelConfig.TimeUnit;
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
	static ConditionalFormatParser formatter;
	private static Format defaultFormat;
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

	@Override
	protected void startUp() throws Exception
	{
		updateFormatterAfterConfigChange();
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		NotificationPanelOverlay.notificationQueue.clear();
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

			if (option.equals(NotificationPanelOverlay.CLEAR_ALL))
			{
				NotificationPanelOverlay.notificationQueue.clear();
			}
		}
	}

	void updateFormatterAfterConfigChange()
	{
		formatter = new ConditionalFormatParser(config);
		defaultFormat = Format.getDefault(config);
	}

	@Subscribe
	public void onNotificationFired(NotificationFired event)
	{
		final String message = event.getMessage();
		final PartialFormat options = formatter.getOptions(message);
		final Format format = defaultFormat.withOptions(options);

		if (!format.getIsVisible())
		{
			return;
		}

		final Notification notification = new Notification(message, format, config);

		NotificationPanelOverlay.notificationQueue.add(notification);
		NotificationPanelOverlay.setShouldUpdateBoxes(true);

		if (config.timeUnit() == TimeUnit.SECONDS)
		{
			java.util.Timer timer = new java.util.Timer();
			TimerTask task = new TimerTask()
			{
				public void run()
				{
					notification.incrementElapsed();
					notification.updateTimeString();

					final int duration = notification.format.getDuration();
					if (duration != 0 && notification.getElapsed() >= duration)
					{
						NotificationPanelOverlay.notificationQueue.poll();
						timer.cancel();
					}
				}
			};
			notification.setTimer(timer);
			timer.schedule(task, 1000L, 1000L);
		}
	}

	private void formatAllNotifications()
	{
		for (Notification notification : NotificationPanelOverlay.notificationQueue)
		{
			PartialFormat options = formatter.getOptions(notification.getMessage());
			notification.format = defaultFormat.withOptions(options);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("notificationpanel"))
		{
			return;
		}

		removeOldNotifications();

		switch (event.getKey())
		{
			case "showTime":
				for (Notification notification : NotificationPanelOverlay.notificationQueue)
				{
					notification.format.setShowTime(new ShowTimeOption(config.showTime()));
				}
				break;
			case "timeUnit":
				NotificationPanelOverlay.notificationQueue.clear();
				break;
			case "expireTime":
				NotificationPanelOverlay.notificationQueue.forEach(notification -> notification.format.setDuration(new DurationOption(config.expireTime())));
				break;
		}
		updateFormatterAfterConfigChange();
		formatAllNotifications();
		NotificationPanelOverlay.shouldUpdateBoxes = true;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		System.out.println(config.expireTime());
		if (config.timeUnit() != TimeUnit.TICKS)
		{
			return;
		}

		ConcurrentLinkedQueue<Notification> queue = NotificationPanelOverlay.notificationQueue;
		queue.forEach(notification -> {
			notification.incrementElapsed();
			notification.updateTimeString();
			final int duration = notification.format.getDuration();
			if (duration != 0 && notification.getElapsed() >= duration)
			{
				// prevent concurrent access errors by polling instead of removing a specific
				// notification
				queue.poll();
			}
		});
	}

	void removeOldNotifications()
	{
		NotificationPanelOverlay.notificationQueue.removeIf(Notification::isNotificationExpired);
	}


	@Provides
	NotificationPanelConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NotificationPanelConfig.class);
	}

}
