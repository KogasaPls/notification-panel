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
import java.awt.Font;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public final class NotificationState
{
	private static final String TEST_MESSAGE = "Test notification";

	private final Clock clock;
	private final Deque<ActiveNotification> active = new ArrayDeque<>();
	private Policy policy = Policy.defaults();
	private long tickSequence;
	private boolean testNotificationVisible;

	public NotificationState(Clock clock)
	{
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	public void updatePolicy(Policy policy)
	{
		this.policy = Objects.requireNonNull(policy, "policy");
		trimTo(policy.getMaximum());
	}

	public void accept(String rawMessage)
	{
		// Rules see the capped message, not the one that arrived. The cap is there for rendering
		// and storage rather than for matching, which is linear either way, but it does mean a
		// message past the cap ends in an ellipsis -- so a pattern anchored to the end of one stops
		// matching at exactly that length. Patterns wrapped in '*', which is nearly all of them,
		// are unaffected.
		String message = NotificationText.limit(rawMessage);
		RuleSet.Resolution resolution = policy.getRules().resolve(message);
		Style resolved = policy.getDefaultStyle().withOverrides(resolution);
		if (!resolved.isVisible())
		{
			return;
		}

		ActiveNotification notification = ActiveNotification.create(message, resolved,
			policy.isShowTime(), policy.getLifetime(), clock.instant(), tickSequence);
		active.addLast(notification);
		trimTo(policy.getMaximum());
	}

	/**
	 * Shows or hides a standing test notification.
	 *
	 * <p>It is derived at snapshot time rather than stored, so unlike a real notification it
	 * never expires, is never evicted, and always reflects the current defaults instead of the
	 * ones captured when it arrived. That makes it both a live preview of those defaults and
	 * something to grab while positioning and resizing the overlay, which is otherwise invisible
	 * whenever no notification happens to be on screen. In every other respect it renders exactly
	 * as a real notification does.</p>
	 */
	public void setTestNotificationVisible(boolean visible)
	{
		this.testNotificationVisible = visible;
	}

	public boolean isTestNotificationVisible()
	{
		return testNotificationVisible;
	}

	public void onGameTick()
	{
		tickSequence = Math.incrementExact(tickSequence);
	}

	public void clear()
	{
		active.clear();
	}

	public List<Snapshot> snapshot()
	{
		Instant now = clock.instant();
		List<Snapshot> snapshots = new ArrayList<>(active.size());
		Iterator<ActiveNotification> iterator = active.iterator();
		while (iterator.hasNext())
		{
			ActiveNotification notification = iterator.next();
			if (notification.isExpired(now, tickSequence))
			{
				iterator.remove();
				continue;
			}
			snapshots.add(notification.snapshot(now, tickSequence));
		}
		if (testNotificationVisible)
		{
			// Built from the current policy every frame, so editing the default colour, opacity,
			// font or duration is reflected immediately -- unlike real notifications, which keep
			// the style they were accepted with. Appended last, where a new arrival would sit.
			snapshots.add(ActiveNotification
				.create(TEST_MESSAGE, policy.getDefaultStyle(), policy.isShowTime(),
					policy.getLifetime(), now, tickSequence)
				.snapshot(now, tickSequence));
		}
		return Collections.unmodifiableList(snapshots);
	}

	private void trimTo(int maximum)
	{
		while (active.size() > maximum)
		{
			active.removeFirst();
		}
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
		private final boolean visible;
		private final Font font;

		public Style(int backgroundRgb, int opacityPercent, boolean visible, Font font)
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
			this.visible = visible;
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

		public boolean isVisible()
		{
			return visible;
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
			// A matched enabled rule always shows the notification; the default visibility only
			// governs notifications that match no rule.
			boolean resolvedVisible = resolution.isMatched() || visible;
			if (resolvedRgb == backgroundRgb
				&& resolvedOpacity == opacityPercent
				&& resolvedVisible == visible)
			{
				return this;
			}
			return new Style(resolvedRgb, resolvedOpacity, resolvedVisible, font);
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
				new Style(0x181818, 75, true, new Font("Dialog", Font.BOLD, 12)),
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
					// Round the remaining time up so a countdown shows "3s" until under
					// two seconds remain, rather than flooring to "2s" almost immediately.
					Duration remaining = Duration.between(now, expirationInstant);
					seconds = remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0);
				}
				return formatSeconds(seconds) + (elapsed ? " ago" : "");
			}

			// Ticks render as a bare count, the way the published plugin did. The unit is already
			// obvious from the Time unit setting, and a suffix on a number that changes every
			// 600ms is just noise.
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
