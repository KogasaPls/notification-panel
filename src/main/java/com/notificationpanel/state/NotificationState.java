/*
 * Copyright (c) 2026, KogasaPls
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.notificationpanel.state;

import com.notificationpanel.layout.NotificationText;
import com.notificationpanel.rules.RuleSet;
import com.notificationpanel.rules.Visibility;
import java.awt.Font;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Core notification queue and expiration state engine.
 */
public final class NotificationState
{
	private static final String TEST_MESSAGE = "Test notification";

	private final Clock clock;
	private final Deque<ActiveNotification> active = new ArrayDeque<>();
	private Policy policy = Policy.defaults();
	private long tickSequence;
	private boolean testNotificationVisible;

	/**
	 * Creates a notification state engine with the specified time source.
	 */
	public NotificationState(Clock clock)
	{
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * Updates the active display policy and trims the queue to the new maximum size.
	 */
	public void updatePolicy(Policy policy)
	{
		this.policy = Objects.requireNonNull(policy, "policy");
		trimTo(policy.getMaximum(), clock.instant());
	}

	/**
	 * Resolves and records a notification unless rules hide it.
	 *
	 * @return accepted notification details, or null if hidden
	 */
	public Accepted accept(String rawMessage)
	{
		String message = NotificationText.clean(rawMessage);
		RuleSet.Resolution resolution = policy.getRules().resolve(message);
		Style resolved = policy.getDefaultStyle().withOverrides(resolution);
		if (resolved.getVisibility() == Visibility.HIDE)
		{
			return null;
		}

		Instant arrivedAt = clock.instant();
		if (resolved.getVisibility() == Visibility.SHOW)
		{
			ActiveNotification notification = ActiveNotification.create(message, resolved,
				policy.isShowTime(), policy.getLifetime(), arrivedAt, tickSequence);
			active.addLast(notification);
			trimTo(policy.getMaximum(), arrivedAt);
		}
		return new Accepted(message, resolved.getBackgroundRgb(), arrivedAt);
	}

	/**
	 * Shows or hides a persistent test notification.
	 */
	public void setTestNotificationVisible(boolean visible)
	{
		this.testNotificationVisible = visible;
	}

	/**
	 * Returns whether the persistent test notification preview is enabled.
	 */
	public boolean isTestNotificationVisible()
	{
		return testNotificationVisible;
	}

	/**
	 * Advances the state engine by one game tick for tick-based expiration.
	 */
	public void onGameTick()
	{
		tickSequence = Math.incrementExact(tickSequence);
	}

	/**
	 * Clears all currently active notifications from the display queue.
	 */
	public void clear()
	{
		active.clear();
	}

	/**
	 * Returns an unmodifiable snapshot list of active notifications for overlay rendering.
	 */
	public List<Snapshot> snapshot()
	{
		Instant now = clock.instant();
		removeExpired(now);
		List<Snapshot> snapshots = new ArrayList<>(active.size() + 1);
		for (ActiveNotification notification : active)
		{
			snapshots.add(notification.snapshot(now, tickSequence));
		}
		if (testNotificationVisible)
		{
			snapshots.add(ActiveNotification
				.create(TEST_MESSAGE, policy.getDefaultStyle(), policy.isShowTime(),
					policy.getLifetime(), now, tickSequence)
				.snapshot(now, tickSequence));
		}
		return Collections.unmodifiableList(snapshots);
	}

	private void trimTo(int maximum, Instant now)
	{
		removeExpired(now);
		while (active.size() > maximum)
		{
			active.removeFirst();
		}
	}

	private void removeExpired(Instant now)
	{
		active.removeIf(notification -> notification.isExpired(now, tickSequence));
	}

	public enum Unit
	{
		SECONDS,
		TICKS
	}

	public static final class Style
	{
		private static final int MAX_RGB = 0xFFFFFF;
		private static final int MAX_OPACITY = 100;

		private final int backgroundRgb;
		private final int opacityPercent;
		private final Visibility visibility;
		private final Font font;

		public Style(int backgroundRgb, int opacityPercent, Visibility visibility, Font font)
		{
			if (backgroundRgb < 0 || backgroundRgb > MAX_RGB)
			{
				throw new IllegalArgumentException("Background color must be a 24-bit RGB value.");
			}
			if (opacityPercent < 0 || opacityPercent > MAX_OPACITY)
			{
				throw new IllegalArgumentException("Opacity must be between 0 and 100.");
			}
			this.backgroundRgb = backgroundRgb;
			this.opacityPercent = opacityPercent;
			this.visibility = Objects.requireNonNull(visibility, "visibility");
			this.font = Objects.requireNonNull(font, "font");
		}

		public int getBackgroundRgb()
		{
			return backgroundRgb;
		}

		public int getOpacityPercent()
		{
			return opacityPercent;
		}

		public Visibility getVisibility()
		{
			return visibility;
		}

		public Font getFont()
		{
			return font;
		}

		public Style withOverrides(RuleSet.Resolution resolution)
		{
			Objects.requireNonNull(resolution, "resolution");
			int resolvedRgb = resolution.getBackgroundRgb() == null
				? backgroundRgb : resolution.getBackgroundRgb();
			int resolvedOpacity = resolution.getOpacityPercent() == null
				? opacityPercent : resolution.getOpacityPercent();
			Visibility ruled = resolution.getVisibility();
			Visibility resolvedVisibility = ruled != null ? ruled
				: (resolution.isMatched() ? Visibility.SHOW : visibility);
			if (resolvedRgb == backgroundRgb
				&& resolvedOpacity == opacityPercent
				&& resolvedVisibility == visibility)
			{
				return this;
			}
			return new Style(resolvedRgb, resolvedOpacity, resolvedVisibility, font);
		}
	}

	public static final class Lifetime
	{
		private final Unit unit;
		private final int duration;

		public Lifetime(Unit unit, int duration)
		{
			this.unit = Objects.requireNonNull(unit, "unit");
			if (duration < 0)
			{
				throw new IllegalArgumentException("Duration must not be negative.");
			}
			this.duration = duration;
		}

		public Unit getUnit()
		{
			return unit;
		}

		public int getDuration()
		{
			return duration;
		}
	}

	public static final class Policy
	{
		private static final int MINIMUM = 1;
		private static final int MAXIMUM = 5;

		private final int maximum;
		private final Style defaultStyle;
		private final Lifetime lifetime;
		private final boolean showTime;
		private final RuleSet rules;

		public Policy(int maximum, Style defaultStyle, Lifetime lifetime, boolean showTime,
			RuleSet rules)
		{
			if (maximum < MINIMUM || maximum > MAXIMUM)
			{
				throw new IllegalArgumentException("Maximum must be between 1 and 5.");
			}
			this.maximum = maximum;
			this.defaultStyle = Objects.requireNonNull(defaultStyle, "defaultStyle");
			this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
			this.showTime = showTime;
			this.rules = Objects.requireNonNull(rules, "rules");
		}

		public static Policy defaults()
		{
			return new Policy(1,
				new Style(0x181818, 75, Visibility.SHOW, new Font("Dialog", Font.BOLD, 12)),
				new Lifetime(Unit.SECONDS, 3), true, RuleSet.empty());
		}

		public int getMaximum()
		{
			return maximum;
		}

		public Style getDefaultStyle()
		{
			return defaultStyle;
		}

		public Lifetime getLifetime()
		{
			return lifetime;
		}

		public boolean isShowTime()
		{
			return showTime;
		}

		public RuleSet getRules()
		{
			return rules;
		}
	}

	/**
	 * Notification details accepted for display or logging.
	 */
	public static final class Accepted
	{
		private final String message;
		private final int backgroundRgb;
		private final Instant arrivedAt;

		public Accepted(String message, int backgroundRgb, Instant arrivedAt)
		{
			this.message = Objects.requireNonNull(message, "message");
			this.backgroundRgb = backgroundRgb;
			this.arrivedAt = Objects.requireNonNull(arrivedAt, "arrivedAt");
		}

		public String getMessage()
		{
			return message;
		}

		public int getBackgroundRgb()
		{
			return backgroundRgb;
		}

		public Instant getArrivedAt()
		{
			return arrivedAt;
		}
	}

	public static final class Snapshot
	{
		private final String message;
		private final int backgroundRgb;
		private final int opacityPercent;
		private final Font font;
		private final String timeLabel;

		public Snapshot(String message, int backgroundRgb, int opacityPercent, Font font,
			String timeLabel)
		{
			this.message = Objects.requireNonNull(message, "message");
			this.backgroundRgb = backgroundRgb;
			this.opacityPercent = opacityPercent;
			this.font = Objects.requireNonNull(font, "font");
			this.timeLabel = timeLabel;
		}

		public String getMessage()
		{
			return message;
		}

		public int getBackgroundRgb()
		{
			return backgroundRgb;
		}

		public int getOpacityPercent()
		{
			return opacityPercent;
		}

		public Font getFont()
		{
			return font;
		}

		public String getTimeLabel()
		{
			return timeLabel;
		}
	}

	private static final class ActiveNotification
	{
		private final String message;
		private final Style style;
		private final boolean showTime;
		private final Lifetime lifetime;
		private final Instant createdInstant;
		private final long createdTick;
		private final Instant expirationInstant;
		private final Long expirationTick;

		private ActiveNotification(String message, Style style, boolean showTime, Lifetime lifetime,
			Instant createdInstant, long createdTick, Instant expirationInstant,
			Long expirationTick)
		{
			this.message = message;
			this.style = style;
			this.showTime = showTime;
			this.lifetime = lifetime;
			this.createdInstant = createdInstant;
			this.createdTick = createdTick;
			this.expirationInstant = expirationInstant;
			this.expirationTick = expirationTick;
		}

		private static ActiveNotification create(String message, Style style, boolean showTime,
			Lifetime lifetime, Instant createdInstant, long createdTick)
		{
			Instant expirationInstant = null;
			Long expirationTick = null;
			if (lifetime.getDuration() > 0)
			{
				if (lifetime.getUnit() == Unit.SECONDS)
				{
					expirationInstant = createdInstant.plusSeconds(lifetime.getDuration());
				}
				else
				{
					expirationTick = Math.addExact(createdTick, (long) lifetime.getDuration());
				}
			}
			return new ActiveNotification(message, style, showTime, lifetime, createdInstant,
				createdTick, expirationInstant, expirationTick);
		}

		private boolean isExpired(Instant now, long tickSequence)
		{
			return (expirationInstant != null && !now.isBefore(expirationInstant))
				|| (expirationTick != null && tickSequence >= expirationTick);
		}

		private Snapshot snapshot(Instant now, long tickSequence)
		{
			String timeLabel = showTime ? timeLabel(now, tickSequence) : null;
			return new Snapshot(message, style.getBackgroundRgb(), style.getOpacityPercent(),
				style.getFont(), timeLabel);
		}

		private String timeLabel(Instant now, long tickSequence)
		{
			boolean elapsed = lifetime.getDuration() == 0;
			if (lifetime.getUnit() == Unit.SECONDS)
			{
				long seconds;
				if (elapsed)
				{
					seconds = Duration.between(createdInstant, now).getSeconds();
				}
				else
				{
					Duration remaining = Duration.between(now, expirationInstant);
					seconds = remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0);
				}
				return formatSeconds(seconds) + (elapsed ? " ago" : "");
			}

			long ticks = elapsed ? tickSequence - createdTick : expirationTick - tickSequence;
			return Long.toString(Math.max(0, ticks));
		}

		private static String formatSeconds(long seconds)
		{
			long nonnegative = Math.max(0, seconds);
			long hours = nonnegative / 3600;
			long minutes = nonnegative % 3600 / 60;
			long remainder = nonnegative % 60;
			StringBuilder label = new StringBuilder();
			if (hours > 0)
			{
				label.append(hours).append("h ");
			}
			if (hours > 0 || minutes > 0)
			{
				label.append(minutes).append("m ");
			}
			return label.append(remainder).append('s').toString();
		}
	}
}
