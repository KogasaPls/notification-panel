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
import com.notificationpanel.rules.NotificationRule;
import com.notificationpanel.rules.RuleSet;
import java.awt.Font;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class NotificationStateTest
{
	private static final Instant NOW = Instant.parse("2026-07-23T12:34:56Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
	private static final Font FONT = new Font("Dialog", Font.PLAIN, 14);

	@Test
	public void hasExactDefaults()
	{
		NotificationState.Policy defaults = NotificationState.Policy.defaults();

		assertEquals(1, defaults.getMaximum());
		assertEquals(0x181818, defaults.getDefaultStyle().getBackgroundRgb());
		assertEquals(75, defaults.getDefaultStyle().getOpacityPercent());
		assertTrue(defaults.getDefaultStyle().isVisible());
		assertEquals(new Font("Dialog", Font.BOLD, 12), defaults.getDefaultStyle().getFont());
		assertEquals(NotificationState.Unit.SECONDS, defaults.getLifetime().getUnit());
		assertEquals(3, defaults.getLifetime().getDuration());
		assertTrue(defaults.isShowTime());
		assertSame(RuleSet.empty(), defaults.getRules());

		NotificationState state = new NotificationState(CLOCK);
		state.accept("one");
		state.accept("two");
		assertEquals(Collections.singletonList("two"), messages(state.snapshot()));
	}

	@Test
	public void validatesClockAndValueObjectBoundaries()
	{
		assertNullPointer(() -> new NotificationState(null));
		assertIllegalArgument(() -> policy(0, style(0, 0, true), seconds(0), false,
			RuleSet.empty()));
		assertIllegalArgument(() -> policy(6, style(0, 0, true), seconds(0), false,
			RuleSet.empty()));
		assertNullPointer(() -> new NotificationState.Policy(1, null, seconds(0), false,
			RuleSet.empty()));
		assertNullPointer(() -> new NotificationState.Policy(1, style(0, 0, true), null, false,
			RuleSet.empty()));
		assertNullPointer(() -> new NotificationState.Policy(1, style(0, 0, true), seconds(0),
			false, null));

		assertIllegalArgument(() -> style(-1, 0, true));
		assertIllegalArgument(() -> style(0x1000000, 0, true));
		assertIllegalArgument(() -> style(0, -1, true));
		assertIllegalArgument(() -> style(0, 101, true));
		assertNullPointer(() -> new NotificationState.Style(0, 0, true, null));
		assertEquals(0, style(0, 0, true).getBackgroundRgb());
		assertEquals(0xFFFFFF, style(0xFFFFFF, 100, true).getBackgroundRgb());
		assertEquals(100, style(0xFFFFFF, 100, true).getOpacityPercent());

		assertNullPointer(() -> new NotificationState.Lifetime(null, 0));
		assertIllegalArgument(() -> seconds(-1));
		assertEquals(0, seconds(0).getDuration());
	}

	@Test
	public void acceptsInOrderAndEvictsOldest()
	{
		NotificationState state = new NotificationState(CLOCK);
		state.updatePolicy(policy(2, style(0x111111, 75, true), seconds(3), true,
			RuleSet.empty()));

		state.accept("one");
		state.accept("two");
		state.accept("three");

		assertEquals(Arrays.asList("two", "three"), messages(state.snapshot()));
	}

	@Test
	public void acceptsMaximumBoundsAndRejectsValuesOutsideThem()
	{
		NotificationState state = new NotificationState(CLOCK);
		state.updatePolicy(policy(5, style(0x111111, 75, true), seconds(3), true,
			RuleSet.empty()));
		for (int i = 1; i <= 6; i++)
		{
			state.accept(Integer.toString(i));
		}
		assertEquals(Arrays.asList("2", "3", "4", "5", "6"), messages(state.snapshot()));

		state.updatePolicy(policy(1, style(0x111111, 75, true), seconds(3), true,
			RuleSet.empty()));
		assertEquals(Collections.singletonList("6"), messages(state.snapshot()));
	}

	@Test
	public void rejectsResolvedHiddenNotifications()
	{
		NotificationRule hide = rule("hide", "secret", null, null,
			NotificationRule.Visibility.HIDE);
		NotificationState state = new NotificationState(CLOCK);
		state.updatePolicy(policy(5, style(0x111111, 75, true), seconds(3), true,
			rules(hide)));

		state.accept("secret drop");

		assertTrue(state.snapshot().isEmpty());
	}

	@Test
	public void ruleCanShowNotificationHiddenByDefault()
	{
		NotificationRule show = rule("show", "important", null, null,
			NotificationRule.Visibility.SHOW);
		NotificationState state = new NotificationState(CLOCK);
		state.updatePolicy(policy(5, style(0x111111, 75, false), seconds(3), true,
			rules(show)));

		state.accept("ordinary");
		state.accept("important");

		assertEquals(Collections.singletonList("important"), messages(state.snapshot()));
	}

	@Test
	public void combinesFirstAttributeMatchesWithStyleDefaults()
	{
		NotificationRule color = rule("color", "drop", 0x112233, null,
			NotificationRule.Visibility.INHERIT);
		NotificationRule remaining = rule("remaining", "drop", 0xFFFFFF, 25,
			NotificationRule.Visibility.SHOW);
		NotificationRule later = rule("later", "drop", null, 90,
			NotificationRule.Visibility.HIDE);
		NotificationState state = new NotificationState(CLOCK);
		state.updatePolicy(policy(5, style(0x999999, 70, false), seconds(3), true,
			rules(color, remaining, later)));

		state.accept("drop");

		NotificationState.Snapshot snapshot = state.snapshot().get(0);
		assertEquals(0x112233, snapshot.getBackgroundRgb());
		assertEquals(25, snapshot.getOpacityPercent());
		assertSame(FONT, snapshot.getFont());
	}

	@Test
	public void acceptsNullAndEmptyMessagesAsEmptyBoundedMessages()
	{
		NotificationState state = new NotificationState(CLOCK);
		state.updatePolicy(policy(5, style(0x111111, 75, true), seconds(3), true,
			RuleSet.empty()));

		state.accept(null);
		state.accept("");

		assertEquals(Arrays.asList("", ""), messages(state.snapshot()));
	}

	@Test
	public void resolvesRulesAgainstCodePointBoundedMessage()
	{
		String prefix = repeatCodePoint(0x1F642, NotificationText.MAX_CODE_POINTS);
		NotificationRule ellipsis = rule("ellipsis", "\u2026$", 0x123456, null,
			NotificationRule.Visibility.INHERIT);
		NotificationRule removedSuffix = rule("removed", "secret", null, null,
			NotificationRule.Visibility.HIDE);
		NotificationState state = new NotificationState(CLOCK);
		state.updatePolicy(policy(5, style(0x111111, 75, true), seconds(3), true,
			rules(ellipsis, removedSuffix)));

		state.accept(prefix + "secret");

		NotificationState.Snapshot snapshot = state.snapshot().get(0);
		assertEquals(NotificationText.MAX_CODE_POINTS,
			snapshot.getMessage().codePointCount(0, snapshot.getMessage().length()));
		assertTrue(snapshot.getMessage().endsWith("\u2026"));
		assertFalse(snapshot.getMessage().contains("secret"));
		assertEquals(0x123456, snapshot.getBackgroundRgb());
	}

	@Test
	public void preservesOpacityEndpointsInAcceptedSnapshots()
	{
		NotificationState state = new NotificationState(CLOCK);
		state.updatePolicy(policy(2, style(0x111111, 0, true), seconds(3), true,
			RuleSet.empty()));
		state.accept("transparent");
		state.updatePolicy(policy(2, style(0x111111, 100, true), seconds(3), true,
			RuleSet.empty()));
		state.accept("opaque");

		List<NotificationState.Snapshot> snapshots = state.snapshot();
		assertEquals(0, snapshots.get(0).getOpacityPercent());
		assertEquals(100, snapshots.get(1).getOpacityPercent());
	}

	@Test
	public void policyChangesOnlyFutureNotificationsAndTrimsImmediately()
	{
		NotificationState state = new NotificationState(CLOCK);
		NotificationState.Policy oldPolicy = policy(5, style(0x111111, 10, true),
			seconds(3), true, RuleSet.empty());
		NotificationRule recolor = rule("new color", "new", 0x333333, null,
			NotificationRule.Visibility.INHERIT);
		NotificationState.Policy newPolicy = policy(1, style(0x222222, 90, true),
			new NotificationState.Lifetime(NotificationState.Unit.TICKS, 9), false,
			rules(recolor));
		state.updatePolicy(oldPolicy);
		state.accept("old");

		NotificationState.Snapshot before = state.snapshot().get(0);
		state.updatePolicy(newPolicy);
		NotificationState.Snapshot after = state.snapshot().get(0);

		assertEquals("old", after.getMessage());
		assertEquals(before.getBackgroundRgb(), after.getBackgroundRgb());
		assertEquals(before.getOpacityPercent(), after.getOpacityPercent());
		assertSame(before.getFont(), after.getFont());
		assertNull(after.getTimeLabel());

		state.accept("new");
		List<NotificationState.Snapshot> snapshots = state.snapshot();
		assertEquals(1, snapshots.size());
		assertEquals("new", snapshots.get(0).getMessage());
		assertEquals(0x333333, snapshots.get(0).getBackgroundRgb());
		assertEquals(90, snapshots.get(0).getOpacityPercent());
		assertNull(snapshots.get(0).getTimeLabel());
	}

	@Test
	public void loweringMaximumImmediatelyRemovesOnlyOldestEntries()
	{
		NotificationState state = new NotificationState(CLOCK);
		state.updatePolicy(policy(5, style(0x111111, 75, true), seconds(3), true,
			RuleSet.empty()));
		state.accept("one");
		state.accept("two");
		state.accept("three");

		state.updatePolicy(policy(2, style(0x222222, 75, true), seconds(3), true,
			RuleSet.empty()));

		assertEquals(Arrays.asList("two", "three"), messages(state.snapshot()));
		assertEquals(0x111111, state.snapshot().get(0).getBackgroundRgb());
		assertEquals(0x111111, state.snapshot().get(1).getBackgroundRgb());
	}

	@Test
	public void rejectsNullPolicyWithoutChangingState()
	{
		NotificationState state = new NotificationState(CLOCK);
		state.accept("kept");

		assertNullPointer(() -> state.updatePolicy(null));

		assertEquals(Collections.singletonList("kept"), messages(state.snapshot()));
	}

	@Test
	public void clearIsSafeWhenEmptyAndWhenRepeated()
	{
		NotificationState state = new NotificationState(CLOCK);
		state.clear();
		state.accept("one");
		state.clear();
		state.clear();

		assertTrue(state.snapshot().isEmpty());
	}

	@Test
	public void gameTicksDoNotCorruptOrderingOrSnapshots()
	{
		NotificationState state = new NotificationState(CLOCK);
		state.updatePolicy(policy(5, style(0x111111, 75, true),
			new NotificationState.Lifetime(NotificationState.Unit.TICKS, 2), true,
			RuleSet.empty()));
		state.accept("before");

		state.onGameTick();
		state.accept("after");
		state.onGameTick();

		assertEquals(Arrays.asList("before", "after"), messages(state.snapshot()));
	}

	@Test
	public void wallClockExpirationOverflowRejectsAcceptWithoutMutation()
	{
		Clock endOfTime = Clock.fixed(Instant.MAX, ZoneOffset.UTC);
		NotificationState state = new NotificationState(endOfTime);
		state.updatePolicy(policy(1, style(0x111111, 75, true), seconds(1), true,
			RuleSet.empty()));

		assertDateTime(() -> state.accept("overflow"));

		assertTrue(state.snapshot().isEmpty());
	}

	@Test
	public void tickExpirationOverflowRejectsAcceptWithoutMutation()
	{
		NotificationState state = new NotificationState(CLOCK, Long.MAX_VALUE);
		state.updatePolicy(policy(1, style(0x111111, 75, true),
			new NotificationState.Lifetime(NotificationState.Unit.TICKS, 1), true,
			RuleSet.empty()));

		assertArithmetic(() -> state.accept("overflow"));

		assertTrue(state.snapshot().isEmpty());
	}

	@Test
	public void zeroDurationDoesNotPerformExpirationArithmetic()
	{
		NotificationState seconds = new NotificationState(
			Clock.fixed(Instant.MAX, ZoneOffset.UTC), Long.MAX_VALUE);
		seconds.updatePolicy(policy(1, style(0x111111, 75, true), seconds(0), true,
			RuleSet.empty()));
		seconds.accept("seconds");

		NotificationState ticks = new NotificationState(CLOCK, Long.MAX_VALUE);
		ticks.updatePolicy(policy(1, style(0x111111, 75, true),
			new NotificationState.Lifetime(NotificationState.Unit.TICKS, 0), true,
			RuleSet.empty()));
		ticks.accept("ticks");

		assertEquals(Collections.singletonList("seconds"), messages(seconds.snapshot()));
		assertEquals(Collections.singletonList("ticks"), messages(ticks.snapshot()));
	}

	@Test
	public void snapshotsAreOrderedFrozenAndDoNotExposeActiveState()
	{
		NotificationState state = new NotificationState(CLOCK);
		state.updatePolicy(policy(5, style(0x111111, 75, true), seconds(3), true,
			RuleSet.empty()));
		state.accept("one");
		List<NotificationState.Snapshot> first = state.snapshot();

		state.accept("two");

		assertEquals(Collections.singletonList("one"), messages(first));
		assertEquals(Arrays.asList("one", "two"), messages(state.snapshot()));
		try
		{
			first.clear();
			fail("Expected snapshots to be immutable");
		}
		catch (UnsupportedOperationException expected)
		{
			assertEquals(Collections.singletonList("one"), messages(first));
		}
	}

	@Test
	public void instancesDoNotSharePolicyOrActiveNotifications()
	{
		NotificationState first = new NotificationState(CLOCK);
		NotificationState second = new NotificationState(CLOCK);
		first.updatePolicy(policy(1, style(0x111111, 75, true), seconds(3), true,
			RuleSet.empty()));
		second.updatePolicy(policy(1, style(0x222222, 75, true), seconds(3), true,
			RuleSet.empty()));

		first.accept("first");
		second.accept("second");

		assertEquals(Collections.singletonList("first"), messages(first.snapshot()));
		assertEquals(0x111111, first.snapshot().get(0).getBackgroundRgb());
		assertEquals(Collections.singletonList("second"), messages(second.snapshot()));
		assertEquals(0x222222, second.snapshot().get(0).getBackgroundRgb());
	}

	private static NotificationState.Policy policy(int maximum, NotificationState.Style style,
		NotificationState.Lifetime lifetime, boolean showTime, RuleSet rules)
	{
		return new NotificationState.Policy(maximum, style, lifetime, showTime, rules);
	}

	private static NotificationState.Style style(int rgb, int opacity, boolean visible)
	{
		return new NotificationState.Style(rgb, opacity, visible, FONT);
	}

	private static NotificationState.Lifetime seconds(int duration)
	{
		return new NotificationState.Lifetime(NotificationState.Unit.SECONDS, duration);
	}

	private static RuleSet rules(NotificationRule... rules)
	{
		RuleSet.CompileResult result = RuleSet.compile(Arrays.asList(rules));
		assertTrue(result.getErrors().toString(), result.getErrors().isEmpty());
		return result.getRuleSet();
	}

	private static NotificationRule rule(String name, String pattern, Integer rgb, Integer opacity,
		NotificationRule.Visibility visibility)
	{
		return new NotificationRule(UUID.randomUUID(), name, true, pattern, rgb, opacity,
			visibility, null);
	}

	private static List<String> messages(List<NotificationState.Snapshot> snapshots)
	{
		List<String> messages = new ArrayList<>();
		for (NotificationState.Snapshot snapshot : snapshots)
		{
			messages.add(snapshot.getMessage());
		}
		return messages;
	}

	private static String repeatCodePoint(int codePoint, int count)
	{
		StringBuilder value = new StringBuilder();
		for (int i = 0; i < count; i++)
		{
			value.appendCodePoint(codePoint);
		}
		return value.toString();
	}

	private static void assertIllegalArgument(Runnable action)
	{
		try
		{
			action.run();
			fail("Expected IllegalArgumentException");
		}
		catch (IllegalArgumentException expected)
		{
			assertTrue(true);
		}
	}

	private static void assertNullPointer(Runnable action)
	{
		try
		{
			action.run();
			fail("Expected NullPointerException");
		}
		catch (NullPointerException expected)
		{
			assertTrue(true);
		}
	}

	private static void assertDateTime(Runnable action)
	{
		try
		{
			action.run();
			fail("Expected DateTimeException");
		}
		catch (DateTimeException expected)
		{
			assertTrue(true);
		}
	}

	private static void assertArithmetic(Runnable action)
	{
		try
		{
			action.run();
			fail("Expected ArithmeticException");
		}
		catch (ArithmeticException expected)
		{
			assertTrue(true);
		}
	}
}
