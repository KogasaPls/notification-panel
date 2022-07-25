package com.notificationpanel;

import com.google.common.base.Splitter;
import com.google.inject.Provides;
import com.notificationpanel.NotificationPanelConfig.TimeUnit;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Objects;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
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
import net.runelite.client.util.ColorUtil;

@Slf4j
@PluginDescriptor(name = "Notification Panel")
public class NotificationPanelPlugin extends Plugin
{
	private static final Splitter NEWLINE_SPLITTER = Splitter.on("\n").omitEmptyStrings()
			.trimResults();
	static ArrayList<Color> colorList = new ArrayList<>();
	static ArrayList<Pattern> patternList = new ArrayList<>();
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

	private static Pattern compilePattern(String pattern)
	{
		try
		{
			return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
		}
		catch (PatternSyntaxException ex)
		{
			// match nothing
			return Pattern.compile("a\\^");
		}
	}

	private static Color compileColor(String color)
	{
		try
		{
			return Color.decode(color);
		}
		catch (NumberFormatException ex)
		{
			return new Color(0, 0, 0, 0);
		}
	}

	@Override
	protected void startUp() throws Exception
	{
		updateRegexLists();
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
				overlayMenuClicked.getOverlay() == overlay)
		{
			final String option = overlayMenuClicked.getEntry().getOption();

			if (option.equals(NotificationPanelOverlay.CLEAR_ALL))
			{
				NotificationPanelOverlay.notificationQueue.clear();
			}
		}
	}

	void updateRegexLists()
	{
		patternList.clear();
		colorList.clear();

		NEWLINE_SPLITTER.splitToList(config.regexList()).stream().filter(Objects::nonNull)
				.map(NotificationPanelPlugin::compilePattern).forEach(patternList::add);

		NEWLINE_SPLITTER.splitToList(config.colorList()).stream().filter(Objects::nonNull)
				.map(NotificationPanelPlugin::compileColor).forEach(colorList::add);
	}

	@Subscribe
	public void onNotificationFired(NotificationFired event)
	{
		final String message = event.getMessage();

		final Notification notification = new Notification(message, matchColor(message),
				config.timeUnit());

		NotificationPanelOverlay.notificationQueue.add(notification);

		NotificationPanelOverlay.setShouldUpdateBoxes(true);
		NotificationPanelOverlay.setShouldUpdateTimers(true);

		if (config.timeUnit() == TimeUnit.SECONDS)
		{
			java.util.Timer timer = new java.util.Timer();
			TimerTask task = new TimerTask()
			{
				public void run()
				{
					notification.incrementElapsed();

					final int expireTime = notification.getExpireTime();
					if (expireTime != 0 && notification.getElapsed() >= expireTime)
					{
						NotificationPanelOverlay.notificationQueue.poll();
						timer.cancel();
					}

					NotificationPanelOverlay.shouldUpdateTimers = true;
				}
			};
			notification.setTimer(timer);
			timer.schedule(task, 1000L, 1000L);
		}
	}

	private Color matchColor(String message)
	{
		Color color = null;
		for (int i = 0;
			 i < patternList.size();
			 i++)
		{
			Pattern pattern = patternList.get(i);
			if (pattern == null)
			{
				return null;
			}
			else if (pattern.matcher(message).matches())
			{
				if (colorList.size() > i)
				{
					color = colorList.get(i);
				}
				else
				{
					return null;
				}
			}
		}

		// fallback to default color if no match
		color = (color != null) ? color : config.bgColor();
		// apply transparency
		return ColorUtil.colorWithAlpha(color, config.opacity() * 255 / 100);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("notificationpanel"))
		{
			return;
		}

		removeOldNotifications();

		if (event.getKey().equals("regexList") || event.getKey().equals("colorList"))
		{
			updateRegexLists();
			for (Notification notification : NotificationPanelOverlay.notificationQueue)
			{
				notification.setColor(matchColor(notification.getMessage()));
			}
		}

		if (event.getKey().equals("showTime"))
		{
			for (Notification notification : NotificationPanelOverlay.notificationQueue)
			{
				notification.setShowTime(config.showTime());
			}
		}

		if (event.getKey().equals("timeUnit"))
		{
			NotificationPanelOverlay.notificationQueue.clear();
		}

		if (event.getKey().equals("expireTime"))
		{
			expireTime = config.expireTime();
			NotificationPanelOverlay.notificationQueue.forEach(notification -> notification.setExpireTime(expireTime));
		}

		NotificationPanelOverlay.shouldUpdateBoxes = true;
		NotificationPanelOverlay.shouldUpdateTimers = true;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		ConcurrentLinkedQueue<Notification> queue = NotificationPanelOverlay.notificationQueue;

		if (config.timeUnit() != TimeUnit.TICKS)
		{
			return;
		}

		queue.forEach(notification -> {
			notification.incrementElapsed();
			if (expireTime != 0 && notification.getElapsed() >= expireTime)
			{
				// prevent concurrent access errors by polling instead of removing a specific
				// notification
				queue.poll();
			}
		});
		NotificationPanelOverlay.shouldUpdateTimers = true;
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
