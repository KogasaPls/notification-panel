package com.notificationpanel;

import com.google.inject.Provides;
import com.notificationpanel.Formatting.FormatOptions.FormatOptions;
import com.notificationpanel.Formatting.NotificationFormat;
import com.notificationpanel.Formatting.PatternMatching.PatternMatchFormatter;
import com.notificationpanel.NotificationPanelConfig.TimeUnit;
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

import javax.inject.Inject;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@PluginDescriptor(name = "Notification Panel")
public class NotificationPanelPlugin extends Plugin
{
	static PatternMatchFormatter formatter;
	static int expireTime;
	static boolean showTime;
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
		showTime = config.showTime();
		expireTime = config.expireTime();
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
				overlayMenuClicked.getOverlay() == overlay) {
			final String option = overlayMenuClicked.getEntry().getOption();

			if (option.equals(NotificationPanelOverlay.CLEAR_ALL)) {
				NotificationPanelOverlay.notificationQueue.clear();
			}
		}
	}
	void updateFormatterAfterConfigChange() {
		formatter = new PatternMatchFormatter(config);
	}

	@Subscribe
	public void onNotificationFired(NotificationFired event) {
		final String message = event.getMessage();

		final NotificationFormat format = formatter.getFormat(message);
		if (!format.isVisible) {
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

					final int expireTime = notification.getExpireTime();
					if (expireTime != 0 && notification.getElapsed() >= expireTime)
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

	private void formatAllNotifications() {
		for (Notification notification : NotificationPanelOverlay.notificationQueue) {
			NotificationFormat newFormat = formatter.getFormat(notification.getMessage());
			notification.setFormat(newFormat);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!event.getGroup().equals("notificationpanel")) {
			return;
		}

		removeOldNotifications();

		if (event.getKey().equals("regexList") || event.getKey().equals("colorList") || event.getKey().equals("opacity")) {
			updateFormatterAfterConfigChange();
			formatAllNotifications();
		}

		if (event.getKey().equals("showTime"))
		{
			for (Notification notification : NotificationPanelOverlay.notificationQueue)
			{
				notification.setShowTime(config.showTime());
			}
		}

		if (event.getKey().equals("timeUnit")) {
			NotificationPanelOverlay.notificationQueue.clear();
		}

		if (event.getKey().equals("expireTime")) {
			expireTime = config.expireTime();
			NotificationPanelOverlay.notificationQueue.forEach(notification -> notification.setExpireTime(expireTime));
		}

		NotificationPanelOverlay.shouldUpdateBoxes = true;
	}

	@Subscribe
	public void onGameTick(GameTick tick) {
		if (config.timeUnit() != TimeUnit.TICKS) {
			return;
		}

		ConcurrentLinkedQueue<Notification> queue = NotificationPanelOverlay.notificationQueue;
		queue.forEach(notification -> {
			notification.incrementElapsed();
			notification.updateTimeString();
			if (expireTime != 0 && notification.getElapsed() >= expireTime) {
				// prevent concurrent access errors by polling instead of removing a specific
				// notification
				queue.poll();
			}
		});
	}

	void removeOldNotifications()
	{
		if (NotificationPanelOverlay.notificationQueue.isEmpty() || expireTime == 0)
		{
			return;
		}

		NotificationPanelOverlay.notificationQueue.removeIf(notification ->
				notification.getElapsed() >= expireTime);
	}

	@Provides
	NotificationPanelConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NotificationPanelConfig.class);
	}

}
