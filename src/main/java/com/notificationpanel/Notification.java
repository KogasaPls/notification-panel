package com.notificationpanel;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.NoSuchElementException;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.events.GameTick;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class Notification
{
	final String message;
	final Instant time;
	final long duration;
	final String[] words;
	final GameTick tick;
	@Setter
	Color color;
	@Getter
	int maxWordWidth;
	@Getter
	@Setter
	int lineHeight = 18;
	@Setter
	PanelComponent box = new PanelComponent();
	@Getter
	int width = 50;
	@Getter
	int height = 10;
	int numLines = 0;

	Notification(final String message, long duration, Color color)
	{
		this.message = message;
		this.duration = duration;
		this.tick = new GameTick();
		this.time = Instant.now();
		this.color = color;

		this.box.setWrap(false);
		// split on spaces and slashes (to break up screenshot notifications)
		// message = "hello world/there"
		// words = ["hello", " ", "world", "/", "there"]
		final String[] splitMessage = message.split("(?<=[ \\\\/])|(?=[ \\\\/])+", -1);

		// ellipsize any word which is longer than 32 characters to prevent
		// the notification from growing too much
		this.words = ellipsize(splitMessage);
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

	void makeBox(Graphics2D graphics, boolean showTime, Dimension preferredSize)
	{
		this.box.getChildren().clear();
		this.box.setBorder(new Rectangle(0, 0, 0, 0));
		this.box.setBackgroundColor(this.color);

		FontMetrics metrics = graphics.getFontMetrics();
		final int[] wordWidths = Arrays.stream(words).map(metrics::stringWidth).mapToInt(i -> i)
				.toArray();
		final int spaceWidth = metrics.charWidth(' ');

		// compute width
		this.maxWordWidth = maxOrZero(wordWidths);
		this.width = Math.max(this.maxWordWidth + 4, preferredSize.width);

		final ArrayList<String> wrappedLines = wrapString(words, wordWidths, width, spaceWidth);
		this.numLines = wrappedLines.size();

		//compute height, including age string + 1/2 line of vertical padding on top and bottom
		final int lineHeight = metrics.getHeight();
		this.height = (lineHeight * (numLines + (showTime ? 1 : 0) + 1));

		// Add ~1 total line of vertical padding to the notification box
		final Rectangle border = new Rectangle(0, lineHeight / 2 - 1, 0, lineHeight / 2);
		this.box.setBorder(border);

		// we take advantage of the built-in centering and lack of
		// wrapping of TitleComponent as opposed to LineComponent
		for (String s : wrappedLines)
		{
			this.box.getChildren().add(TitleComponent.builder().text(s).build());
		}
	}

	// prefer to update the last line in the notification, rather than
	// rebuild the entire thing every client tick
	void updateTimeString(int duration)
	{
		// time string already exists; remove it
		if (this.box.getChildren().size() > this.numLines)
		{
			// numLines is the index of the time string
			this.box.getChildren().remove(this.numLines);
		}
		this.box.getChildren().add(TitleComponent.builder().text(timeString(duration)).build());
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

	private String[] ellipsize(String[] arr)
	{
		for (int i = 0; i < arr.length; i++)
		{
			if (arr[i].length() > 32)
			{
				arr[i] = arr[i].substring(0, 29) + "...";
			}
		}
		return arr;
	}

	private String timeString(int duration)
	{
		Instant endTime = time.plusMillis(duration);
		Duration timeDiff = Duration.between(Instant.now(), endTime).abs();

		int seconds = (int) (timeDiff.toMillis() / 1000L);
		int minutes = (seconds % 3600) / 60;
		int secs = seconds % 60;

		// countdowns look like "3s, 2s, 1s"
		// age looks like "0s ago, 1s ago, 2s ago"
		boolean isCountDown = Instant.now().isBefore(endTime);
		if (isCountDown)
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

		if (!isCountDown)
		{
			sb.append(" ago");
		}

		return sb.toString();
	}
}
