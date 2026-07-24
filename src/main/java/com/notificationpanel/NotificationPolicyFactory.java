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
package com.notificationpanel;

import com.notificationpanel.rules.RuleSet;
import com.notificationpanel.state.NotificationState;
import java.util.Objects;

/**
 * Turns RuneLite configuration into a {@link NotificationState.Policy}.
 *
 * <p>This is the boundary between stored configuration and the strict core values, so it clamps
 * rather than propagates out-of-range numbers. RuneLite's {@code @Range} annotations constrain
 * only the config panel; a profile edited by hand, synced from another install, or written by an
 * older version can hold anything. Passing such a value straight through would throw out of
 * {@code startUp}, and the plugin would fail to load with nothing but a stack trace in the log.
 * Clamping to the documented range keeps the plugin usable and matches what the config panel
 * shows the user anyway.</p>
 */
public final class NotificationPolicyFactory
{
	private static final int MIN_SHOWN = 1;
	private static final int MAX_SHOWN = 5;
	private static final int MIN_OPACITY = 0;
	private static final int MAX_OPACITY = 100;

	public NotificationState.Policy create(NotificationPanelConfig config, RuleSet rules)
	{
		Objects.requireNonNull(config, "config");
		Objects.requireNonNull(rules, "rules");
		NotificationState.Style style = new NotificationState.Style(
			NotificationPanelConfig.backgroundOrDefault(config).getRGB() & 0xFFFFFF,
			clamp(config.opacity(), MIN_OPACITY, MAX_OPACITY),
			config.showUnmatchedByDefault(),
			config.fontType().getFont());
		NotificationState.Lifetime lifetime = new NotificationState.Lifetime(
			mapTimeUnit(config.timeUnit()), Math.max(0, config.expireTime()));
		return new NotificationState.Policy(clamp(config.numToShow(), MIN_SHOWN, MAX_SHOWN), style,
			lifetime, config.showTime(), rules);
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static NotificationState.Unit mapTimeUnit(NotificationPanelConfig.TimeUnit timeUnit)
	{
		switch (timeUnit)
		{
			case SECONDS:
				return NotificationState.Unit.SECONDS;
			case TICKS:
				return NotificationState.Unit.TICKS;
		}
		throw new IllegalArgumentException("Unsupported time unit: " + timeUnit);
	}
}
