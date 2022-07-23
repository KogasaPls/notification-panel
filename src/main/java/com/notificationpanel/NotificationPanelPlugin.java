package com.notificationpanel;

import com.google.common.base.Splitter;
import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayList;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;

@Slf4j
@PluginDescriptor(name = "Notification Overlay")
public class NotificationPanelPlugin extends Plugin
{
	private static final Splitter NEWLINE_SPLITTER = Splitter.on("\n").omitEmptyStrings()
			.trimResults();
	public boolean shouldUpdate;
	public ConcurrentLinkedQueue<Notification> notificationQueue = new ConcurrentLinkedQueue<>();
	static ArrayList<Color> colorList = new ArrayList<>();
	static ArrayList<Pattern> patternList = new ArrayList<>();
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
			return null;
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
			return null;
		}
	}

	@Override
	protected void startUp() throws Exception
	{
		updateRegexLists();
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

			if (option.equals(NotificationPanelOverlay.CLEAR_ALL))
			{
				notificationQueue.clear();
			}
		}
	}

	void updateRegexLists()
	{
		patternList.clear();
		colorList.clear();

		NEWLINE_SPLITTER.splitToList(config.regexList()).stream()
				.map(NotificationPanelPlugin::compilePattern).forEach(patternList::add);

		NEWLINE_SPLITTER.splitToList(config.colorList()).stream()
				.map(NotificationPanelPlugin::compileColor).forEach(colorList::add);
	}

	@Subscribe
	public void onNotificationFired(NotificationFired event)
	{
		if (!runeLiteConfig.sendNotificationsWhenFocused() && clientUI.isFocused())
		{
			return;
		}
		final int duration = config.duration();

		final Notification notification = new Notification(event.getMessage(), duration);
		notificationQueue.add(notification);
		shouldUpdate = true;

		if (duration > 0)
		{
			java.util.Timer timer = new java.util.Timer();
			TimerTask task = new TimerTask()
			{
				public void run()
				{
					notificationQueue.remove(notification);
					shouldUpdate = true;
				}
			};
			timer.schedule(task, duration);
		}

	}

	@Provides
	NotificationPanelConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NotificationPanelConfig.class);
	}

}
