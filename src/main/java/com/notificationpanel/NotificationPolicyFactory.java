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

public final class NotificationPolicyFactory
{
	public NotificationState.Policy create(NotificationPanelConfig config, RuleSet rules)
	{
		Objects.requireNonNull(config, "config");
		Objects.requireNonNull(rules, "rules");
		NotificationState.Style style = new NotificationState.Style(
			config.bgColor().getRGB() & 0xFFFFFF, config.opacity(), config.showUnmatchedByDefault(),
			config.fontType().getFont());
		NotificationState.Lifetime lifetime = new NotificationState.Lifetime(
			mapTimeUnit(config.timeUnit()), config.expireTime());
		return new NotificationState.Policy(config.numToShow(), style, lifetime, config.showTime(),
			rules);
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
