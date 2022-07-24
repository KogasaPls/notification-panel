package com.notificationpanel;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.Setter;
import static net.runelite.api.MenuAction.RUNELITE_OVERLAY;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.ColorUtil;

public class NotificationPanelOverlay extends OverlayPanel
{

	static final String CLEAR_ALL = "Clear";
	static final private Dimension DEFAULT_SIZE = new Dimension(250, 60);
	static private int panelHeight = 0;
	static private int panelWidth = 0;
	final private NotificationPanelPlugin plugin;
	final private NotificationPanelConfig config;

	@Setter
	int maxWordWidth;

	@Setter
	String[] wrapped;

	@Inject
	private NotificationPanelOverlay(NotificationPanelConfig config,
									 NotificationPanelPlugin plugin)
	{
		this.plugin = plugin;
		this.config = config;

		setResizable(true);
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(OverlayPriority.LOW);

		panelComponent.setWrap(false);
		panelComponent.setBorder(new Rectangle(0, 0, 0, 0));
		panelComponent.setOrientation(ComponentOrientation.VERTICAL);
		panelComponent.setGap(new Point(0, 6));
		panelComponent.setBackgroundColor(new Color(0, 0, 0, 0));

		getMenuEntries().add(new OverlayMenuEntry(RUNELITE_OVERLAY, CLEAR_ALL,
				"Notification " + "panel"));
	}

	/*
	 * Fancy (TeX-like) word wrapping for minimal raggedness, based on
	 * https://geeksforgeeks.org/word-wrap-problem-space-optimized-solution/
	 */
	private static ArrayList<String> wrapString(String[] str, int[] arr, int k, int spaceWidth)
	{
		int i, j;
		int n = str.length;
		int currlen;
		int cost;
		int[] dp = new int[n];
		int[] ans = new int[n];

		dp[n - 1] = 0;
		ans[n - 1] = n - 1;
		for (i = n - 2; i >= 0; i--)
		{
			currlen = -1;
			dp[i] = Integer.MAX_VALUE;
			for (j = i; j < n; j++)
			{
				currlen += (arr[j] + spaceWidth);
				if (currlen > k) break;
				if (j == n - 1) cost = 0;
				else cost = (k - currlen) * (k - currlen) + dp[j + 1];
				if (cost < dp[i])
				{
					dp[i] = cost;
					ans[i] = j;
				}
			}
		}

		ArrayList<String> out = new ArrayList<>();
		i = 0;

		while (i < n)
		{
			StringBuilder sb = new StringBuilder();
			for (j = i; j <= ans[i]; j++)
			{
				final String word = str[j];
				sb.append(word);
			}
			out.add(sb.toString().trim());
			i = ans[i] + 1;
		}

		return out;
	}

	private static TitleComponent ageString(Instant time)
	{
		Duration timeLeft = Duration.between(Instant.now(), time).abs();

		int seconds = (int) (timeLeft.toMillis() / 1000L);
		int minutes = (seconds % 3600) / 60;
		int secs = seconds % 60;

		boolean isNegative = Instant.now().isAfter(time);

		// e.g. 3s duration looks like "3, 2, 1" instead of "2, 1, 0"
		if (!isNegative)
		{
			secs++;
		}

		StringBuilder sb = new StringBuilder();

		if (minutes > 0)
		{
			sb.append(minutes).append("m");
		}

		if (secs < 60)
		{
			sb.append(secs).append("s");
		}

		if (isNegative)
		{
			sb.append(" ago");
		}

		return TitleComponent.builder().text(sb.toString()).build();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (plugin.notificationQueue.isEmpty())
		{
			return null;
		}

		graphics.setFont(config.fontType().getFont());
		Dimension preferredSize = getPreferredSize();

		if (preferredSize == null)
		{
			preferredSize = DEFAULT_SIZE;
			setPreferredSize(preferredSize);
		}

		if (plugin.shouldUpdate)
		{
			plugin.notificationQueue.forEach(s -> setWrapped(s, graphics));
			plugin.shouldUpdate = false;
		}

		while (plugin.notificationQueue.size() > config.numToShow())
		{
			plugin.notificationQueue.poll();
		}

		panelHeight = 0;
		panelWidth = 0;
		plugin.notificationQueue.forEach((s) -> makeBox(s, graphics));
		return super.render(graphics);
	}

	public void makeBox(Notification notification, Graphics2D graphics)
	{
		PanelComponent panel = new PanelComponent();
		panel.setWrap(false);
		Dimension preferredSize = getPreferredSize();

		final Color matchColor = matchColor(notification);
		Color colorOpaque = (matchColor != null) ? matchColor : config.bgColor();

		final Color colorWithAlpha = ColorUtil.colorWithAlpha(colorOpaque,
				config.opacity() * 255 / 100);
		panel.setBackgroundColor(colorWithAlpha);

		final ArrayList<String> wrapped = notification.wrapped;
		for (String s : wrapped)
		{
			panel.getChildren().add(TitleComponent.builder().text(s).build());
		}

		int lineHeight = graphics.getFontMetrics().getHeight() + 2;
		int numLines = wrapped.size() + (config.showTime() ? 1 : 0);

		final int boxWidth = Math.max(notification.getMaxWordWidth(), preferredSize.width);
		final int boxHeight = lineHeight * numLines;

		// If we just add the notification components to the (invisible) main panel, it won't be
		// resized. We resize it manually by setting its border dimensions. That way we can still
		// drag, anchor, and resize the notification group.
		panel.setBorder(new Rectangle(boxWidth / 2, 4, boxWidth / 2, boxHeight));
		panelWidth = Math.max(panelWidth, boxWidth);
		panelHeight += boxHeight;
		setMinimumSize(boxWidth);

		if (config.showTime())
		{
			panel.getChildren().add(ageString(notification.time.plusMillis(notification.duration)));
		}

		setPreferredSize(new Dimension(panelWidth, panelHeight));
		panelComponent.getChildren().add(panel);
	}

	private void setWrapped(Notification notification, Graphics2D graphics)
	{
		String[] words = notification.words;

		FontMetrics metrics = graphics.getFontMetrics();
		final int spaceWidth = metrics.charWidth(' ');

		// don't allow the box to be smaller than the widest word
		final int[] wordWidths = Arrays.stream(words).map(metrics::stringWidth).mapToInt(i -> i)
				.toArray();
		maxWordWidth = maxOrZero(wordWidths);

		Dimension preferredSize = getPreferredSize();
		final int boxWidth = Math.max(maxWordWidth + 10, preferredSize.width);
		setPreferredSize(new Dimension(boxWidth, preferredSize.height));

		// we take advantage of the built-in centering and lack of
		// wrapping of TitleComponent as opposed to LineComponent
		final ArrayList<String> wrappedMessage = wrapString(words, wordWidths, boxWidth,
				spaceWidth);

		notification.setWrapped(wrappedMessage);
		notification.setMaxWordWidth(maxWordWidth);
		notification.setLineHeight(metrics.getHeight());
	}

	private int maxOrZero(int[] arr)
	{
		try
		{
			return Arrays.stream(arr).max().getAsInt();
		}
		catch (NoSuchElementException ex)
		{
			return 0;
		}
	}

	private Color matchColor(Notification notification)
	{
		final String message = notification.message;
		for (int i = 0; i < NotificationPanelPlugin.patternList.size(); i++)
		{
			Pattern pattern = NotificationPanelPlugin.patternList.get(i);
			if (pattern == null)
			{
				return null;
			}
			if (pattern.matcher(message).matches())
			{
				return NotificationPanelPlugin.colorList.get(i);
			}
		}
		return null;
	}
}


