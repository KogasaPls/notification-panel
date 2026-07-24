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

import com.notificationpanel.NotificationPanelConfig.FontStyle;
import com.notificationpanel.rules.RuleSet;
import com.notificationpanel.state.NotificationState;
import java.awt.Color;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NotificationPolicyFactoryTest
{
	@Test
	public void mapsSecondsMinimumAndZeroDurationWithoutChangingValues()
	{
		RuleSet rules = RuleSet.empty();
		NotificationState.Policy policy = new NotificationPolicyFactory().create(
			config(0, NotificationPanelConfig.TimeUnit.SECONDS, 1, true, FontStyle.BOLD,
				new Color(0x7f123456, true), 0, true), rules);

		assertEquals(1, policy.getMaximum());
		assertEquals(NotificationState.Unit.SECONDS, policy.getLifetime().getUnit());
		assertEquals(0, policy.getLifetime().getDuration());
		assertTrue(policy.isShowTime());
		assertEquals(0x123456, policy.getDefaultStyle().getBackgroundRgb());
		assertEquals(0, policy.getDefaultStyle().getOpacityPercent());
		assertTrue(policy.getDefaultStyle().isVisible());
		assertEquals(FontStyle.BOLD.getFont(), policy.getDefaultStyle().getFont());
		assertSame(rules, policy.getRules());
	}

	@Test
	public void mapsTicksMaximumAndDisabledSettingsWithoutChangingValues()
	{
		NotificationState.Policy policy = new NotificationPolicyFactory().create(
			config(9, NotificationPanelConfig.TimeUnit.TICKS, 5, false, FontStyle.SMALL,
				new Color(0x40112233, true), 100, false), RuleSet.empty());

		assertEquals(5, policy.getMaximum());
		assertEquals(NotificationState.Unit.TICKS, policy.getLifetime().getUnit());
		assertEquals(9, policy.getLifetime().getDuration());
		assertFalse(policy.isShowTime());
		assertEquals(0x112233, policy.getDefaultStyle().getBackgroundRgb());
		assertEquals(100, policy.getDefaultStyle().getOpacityPercent());
		assertFalse(policy.getDefaultStyle().isVisible());
		assertEquals(FontStyle.SMALL.getFont(), policy.getDefaultStyle().getFont());
	}

	@Test
	public void passesThroughMidpointOpacityAndFont()
	{
		NotificationState.Policy policy = new NotificationPolicyFactory().create(
			config(3, NotificationPanelConfig.TimeUnit.SECONDS, 3, true, FontStyle.REGULAR,
				new Color(0xabcdef), 75, false), RuleSet.empty());

		assertEquals(75, policy.getDefaultStyle().getOpacityPercent());
		assertEquals(FontStyle.REGULAR.getFont(), policy.getDefaultStyle().getFont());
	}

	@Test
	public void rejectsNullInputsWithTheirParameterNames()
	{
		NotificationPolicyFactory factory = new NotificationPolicyFactory();
		NullPointerException configException = assertThrows(NullPointerException.class,
			() -> factory.create(null, RuleSet.empty()));
		NullPointerException rulesException = assertThrows(NullPointerException.class,
			() -> factory.create(defaultConfig(), null));

		assertEquals("config", configException.getMessage());
		assertEquals("rules", rulesException.getMessage());
	}

	@Test
	public void clampsStoredValuesBelowTheirRangeInsteadOfFailingStartup()
	{
		// @Range constrains only the config panel, so a hand-edited, synced, or older profile can
		// hold anything. Throwing here would take the whole plugin down during startUp.
		NotificationState.Policy policy = new NotificationPolicyFactory().create(
			config(-1, NotificationPanelConfig.TimeUnit.SECONDS, 0, true, FontStyle.BOLD,
				new Color(0x181818), -5, true), RuleSet.empty());

		assertEquals(1, policy.getMaximum());
		assertEquals(0, policy.getLifetime().getDuration());
		assertEquals(0, policy.getDefaultStyle().getOpacityPercent());
	}

	@Test
	public void clampsStoredValuesAboveTheirRangeInsteadOfFailingStartup()
	{
		NotificationState.Policy policy = new NotificationPolicyFactory().create(
			config(3, NotificationPanelConfig.TimeUnit.TICKS, 9, true, FontStyle.BOLD,
				new Color(0x181818), 101, true), RuleSet.empty());

		assertEquals(5, policy.getMaximum());
		assertEquals(100, policy.getDefaultStyle().getOpacityPercent());
		// Duration has no upper bound, so a large value is honoured rather than clamped.
		assertEquals(3, policy.getLifetime().getDuration());
	}

	@Test
	public void leavesInRangeValuesExactlyAsStored()
	{
		NotificationState.Policy policy = new NotificationPolicyFactory().create(
			config(3, NotificationPanelConfig.TimeUnit.SECONDS, 3, true, FontStyle.BOLD,
				new Color(0x181818), 75, true), RuleSet.empty());

		assertEquals(3, policy.getMaximum());
		assertEquals(3, policy.getLifetime().getDuration());
		assertEquals(75, policy.getDefaultStyle().getOpacityPercent());
	}

	@Test
	public void fallsBackToTheDefaultBackgroundWhenTheStoredColourCannotBeRead()
	{
		// RuneLite answers an unparseable colour with null rather than by throwing, so the config
		// proxy never falls back to the interface default the way it does for every other type.
		// Dereferencing that null would leave the plugin running on Policy.defaults() -- no rules
		// at all -- for the rest of the session.
		NotificationState.Policy policy = new NotificationPolicyFactory().create(
			config(3, NotificationPanelConfig.TimeUnit.SECONDS, 3, true, FontStyle.BOLD,
				null, 75, true), RuleSet.empty());

		assertEquals(NotificationPanelConfig.DEFAULT_BACKGROUND_RGB,
			policy.getDefaultStyle().getBackgroundRgb());
	}

	private static NotificationPanelConfig defaultConfig()
	{
		return config(3, NotificationPanelConfig.TimeUnit.SECONDS, 1, true, FontStyle.BOLD,
			new Color(0x181818), 75, true);
	}

	private static NotificationPanelConfig config(int duration, NotificationPanelConfig.TimeUnit unit,
		int maximum, boolean showTime, FontStyle font, Color background, int opacity,
		boolean visible)
	{
		return new NotificationPanelConfig()
		{
			@Override
			public int expireTime()
			{
				return duration;
			}

			@Override
			public TimeUnit timeUnit()
			{
				return unit;
			}

			@Override
			public int numToShow()
			{
				return maximum;
			}

			@Override
			public boolean showTime()
			{
				return showTime;
			}

			@Override
			public FontStyle fontType()
			{
				return font;
			}

			@Override
			public Color bgColor()
			{
				return background;
			}

			@Override
			public int opacity()
			{
				return opacity;
			}

			@Override
			public boolean showUnmatchedByDefault()
			{
				return visible;
			}
		};
	}
}
