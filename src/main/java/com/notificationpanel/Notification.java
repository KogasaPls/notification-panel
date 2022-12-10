package com.notificationpanel;

import com.notificationpanel.Formatting.NotificationFormat;
import com.notificationpanel.NotificationPanelConfig.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Timer;

public class Notification {
	@Getter
	private final String message;
	private final String[] words;
	private final TimeUnit unit;
	@Setter
	private NotificationFormat format;
	@Getter
	private final Instant time = Instant.now();
	@Getter
	private final PanelComponent box = new PanelComponent();
	@Getter
	@Setter
	private int expireTime = NotificationPanelPlugin.expireTime;
	@Setter
	private boolean showTime = NotificationPanelPlugin.showTime;
	@Getter
	private int elapsed = 0;
	@Getter
	private int width = 0;
	@Getter
	private int height = 0;
	private int numLines = 0;
	@Getter
	private int maxWordWidth;
	@Getter
	@Setter
	private Timer timer;

	Notification(final String message, NotificationFormat format, NotificationPanelConfig config) {
		this.message = message;
		this.format = format;
		// snapshot the time unit in case it changes
		this.unit = config.timeUnit();

		box.setWrap(false);

		final String[] splitMessage = splitMessage(message);
		words = ellipsize(splitMessage);
	}

	/**
	 * Fancy (TeX-like) word wrapping for minimal raggedness, based on
	 * <a href="https://geeksforgeeks.org/word-wrap-problem-space-optimized-solution/">...</a>
	 */
	private static ArrayList<String> wrapString(String[] str, int[] arr, int k, int spaceWidth) {
		int i, j;
		int n = str.length;
		int currlen;
		int cost;
		int[] dp = new int[n];
		int[] ans = new int[n];

		dp[n - 1] = 0;
		ans[n - 1] = n - 1;
		for (i = n - 2;
			 i >= 0;
			 i--)
		{
			currlen = -1;
			dp[i] = Integer.MAX_VALUE;
			for (j = i;
				 j < n;
				 j++)
			{
				currlen += (arr[j] + spaceWidth);
				if (currlen > k)
				{
					break;
				}
				if (j == n - 1)
				{
					cost = 0;
				}
				else
				{
					cost = (k - currlen) * (k - currlen) + dp[j + 1];
				}
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
			for (j = i; j <= ans[i]; j++) {
				final String word = str[j];
				sb.append(word);
			}
			out.add(sb.toString().trim());
			i = ans[i] + 1;
		}

		return out;
	}

	/**
	 * Split on spaces and slashes (to break up screenshot notifications)
	 *
	 * @param message, e.g. "hello world/there"
	 * @return an array of words, ["hello", " ", "world", "/", "there"]
	 */
	private String[] splitMessage(String message) {
		return message.split("(?<=[ \\\\/])|(?=[ \\\\/])+", -1);

	}

	void makeBox(Graphics2D graphics, Dimension preferredSize) {
		if (!format.getIsVisible()) {
			return;
		}

		box.getChildren().clear();
		box.setBorder(new Rectangle(0, 0, 0, 0));
		box.setBackgroundColor(format.getColorWithOpacity());

		FontMetrics metrics = graphics.getFontMetrics();
		final int[] wordWidths = Arrays
				.stream(words)
				.map(metrics::stringWidth)
				.mapToInt(i -> i)
				.toArray();
		final int spaceWidth = metrics.charWidth(' ');

		// compute width
		maxWordWidth = maxOrZero(wordWidths);
		width = Math.max(this.maxWordWidth + 4, preferredSize.width);

		final ArrayList<String> wrappedLines = wrapString(words, wordWidths, width, spaceWidth);
		numLines = wrappedLines.size();

		//compute height, including age string + 1/2 line of vertical padding on top and bottom
		final int lineHeight = metrics.getHeight();
		height = (lineHeight * (numLines + (showTime ? 1 : 0) + 1));

		// Add ~1 total line of vertical padding to the notification box
		final Rectangle border = new Rectangle(0, lineHeight / 2 - 1, 0, lineHeight / 2);
		box.setBorder(border);

		// we take advantage of the built-in centering and lack of
		// wrapping of TitleComponent as opposed to LineComponent
		for (String s : wrappedLines) {
			box.getChildren().add(TitleComponent.builder().text(s).build());
		}

		if (showTime) {
			addTimeString();
		}
	}

	void updateTimeString() {
		removeTimeStringIfExists();
		addTimeString();
	}

	private void removeTimeStringIfExists() {
		final int timeStringIndex = this.numLines;
		if (this.box.getChildren().size() > timeStringIndex) {
			this.box.getChildren().remove(timeStringIndex);
		}
	}

	private void addTimeString() {
		final String timeString = getTimeString();
		final TitleComponent timeStringComponent = TitleComponent.builder().text(timeString).build();
		this.box.getChildren().add(timeStringComponent);
	}

	private int maxOrZero(int[] arr) {
		try {
			return Arrays.stream(arr).max().getAsInt();
		} catch (NoSuchElementException ex) {
			return 0;
		}
	}

	private String[] ellipsize(String[] arr) {
		for (int i = 0; i < arr.length; i++) {
			if (arr[i].length() > 32) {
				arr[i] = arr[i].substring(0, 29) + "...";
			}
		}
		return arr;
	}

	private String getTimeString() {
		int timeLeft = Math.abs(expireTime - this.elapsed);
		switch (this.unit) {
			case TICKS:
				return String.valueOf(Math.abs(timeLeft));
			case SECONDS:
				int minutes = ((timeLeft) % 3600) / 60;
				int secs = (timeLeft) % 60;

				StringBuilder sb = new StringBuilder();

				if (minutes > 0)
				{
					sb.append(minutes).append("m");
				}

				if (minutes == 0 || secs > 0)
				{
					sb.append(secs).append("s");
				}

				if (expireTime == 0)
				{
					sb.append(" ago");
				}
				return sb.toString();
		}
		return "";
	}

	void incrementElapsed()
	{
		this.elapsed++;
	}
}
