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

import com.notificationpanel.MutableClock;
import com.notificationpanel.layout.NotificationText;
import com.notificationpanel.rules.NotificationRule;
import com.notificationpanel.rules.RuleSet;
import java.awt.Font;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
	public void mutableClockFollowsClockSemanticsAndValidatesInputs()
	{
		MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
		assertEquals(NOW, clock.instant());
		assertEquals(NOW.toEpochMilli(), clock.millis());
		assertEquals(ZoneOffset.UTC, clock.getZone());

		clock.advance(Duration.ofMillis(1250));
		assertEquals(NOW.plusMillis(1250), clock.instant());

		Clock zoned = clock.withZone(ZoneOffset.ofHours(2));
		assertEquals(NOW.plusMillis(1250), zoned.instant());
		assertEquals(ZoneOffset.ofHours(2), zoned.getZone());
		clock.advance(Duration.ofSeconds(1));
		assertEquals(NOW.plusMillis(1250), zoned.instant());

		assertNullPointer(() -> new MutableClock(null, ZoneOffset.UTC));
		assertNullPointer(() -> new MutableClock(NOW, null));
		assertNullPointer(() -> clock.advance(null));
		assertNullPointer(() -> clock.withZone(null));
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
	public void hidesUnmatchedWhenDefaultHiddenButAlwaysShowsMatched()
	{
		NotificationRule keep = rule("keep", "*keep*", 0x222222, null);
		NotificationState state = new NotificationState(CLOCK);
		state.updatePolicy(policy(5, style(0x111111, 75, false), seconds(3), true,
			rules(keep)));

		state.accept("drop this");
		state.accept("please keep");

		java.util.List<NotificationState.Snapshot> snapshots = state.snapshot();
		assertEquals(1, snapshots.size());
		assertEquals("please keep", snapshots.get(0).getMessage());
	}

	@Test
	public void ruleCanShowNotificationHiddenByDefault()
	{
		NotificationRule show = rule("show", "*important*", null, null);
		NotificationState state = new NotificationState(CLOCK);
		state.updatePolicy(policy(5, style(0x111111, 75, false), seconds(3), true,
			rules(show)));

		state.accept("ordinary");
		state.accept("important");

		assertEquals(Collections.singletonList("important"), messages(state.snapshot()));
	}

	@Test
	public void hideRuleDropsTheMessagesItMatches()
	{
		NotificationRule hide = rule("hide", "*screenshot*", null, null, Boolean.FALSE);
		NotificationState state = new NotificationState(CLOCK);
		state.updatePolicy(policy(5, style(0x111111, 75, true), seconds(3), true, rules(hide)));

		state.accept("Screenshot saved");
		state.accept("A dragon warhammer");

		assertEquals(Collections.singletonList("A dragon warhammer"), messages(state.snapshot()));
	}

	@Test
	public void hideRuleStillHidesWhenItSitsBelowAColourRule()
	{
		// Resolution stops once nothing later can change the answer, and a rule above that settles
		// both colour and opacity is what makes a naive stop skip the rule below it. Hiding must not
		// depend on where in the list the hide rule happens to sit.
		NotificationRule formatting = rule("formatting", "*screenshot*", 0x112233, 40, null);
		NotificationRule hide = rule("hide", "*screenshot*", null, null, Boolean.FALSE);
		NotificationState state = new NotificationState(CLOCK);
		state.updatePolicy(policy(5, style(0x111111, 75, true), seconds(3), true,
			rules(formatting, hide)));

		state.accept("Screenshot saved");

		assertTrue(state.snapshot().isEmpty());
	}

	@Test
	public void showRuleBeatsTheDefaultWhenNotificationsAreHiddenByDefault()
	{
		NotificationRule show = rule("show", "*important*", null, null, Boolean.TRUE);
		NotificationState state = new NotificationState(CLOCK);
		state.updatePolicy(policy(5, style(0x111111, 75, false), seconds(3), true, rules(show)));

		state.accept("something important");
		state.accept("ordinary");

		assertEquals(Collections.singletonList("something important"), messages(state.snapshot()));
	}

	@Test
	public void unmatchedMessagesFollowTheDefaultVisibility()
	{
		NotificationRule hide = rule("hide", "*screenshot*", null, null, Boolean.FALSE);
		NotificationState shown = new NotificationState(CLOCK);
		shown.updatePolicy(policy(5, style(0x111111, 75, true), seconds(3), true, rules(hide)));
		NotificationState hidden = new NotificationState(CLOCK);
		hidden.updatePolicy(policy(5, style(0x111111, 75, false), seconds(3), true, rules(hide)));

		shown.accept("unrelated");
		hidden.accept("unrelated");

		assertEquals(Collections.singletonList("unrelated"), messages(shown.snapshot()));
		assertTrue(hidden.snapshot().isEmpty());
	}

	@Test
	public void disabledHideRuleDoesNotHide()
	{
		NotificationRule hide = disabledRule("hide", "*screenshot*", Boolean.FALSE);
		NotificationState state = new NotificationState(CLOCK);
		state.updatePolicy(policy(5, style(0x111111, 75, true), seconds(3), true, rules(hide)));

		state.accept("Screenshot saved");

		assertEquals(Collections.singletonList("Screenshot saved"), messages(state.snapshot()));
	}

	@Test
	public void combinesFirstAttributeMatchesWithStyleDefaults()
	{
		NotificationRule color = rule("color", "*drop*", 0x112233, null);
		NotificationRule remaining = rule("remaining", "*drop*", 0xFFFFFF, 25);
		NotificationRule later = rule("later", "*drop*", null, 90);
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
		NotificationRule ellipsis = rule("ellipsis", "*\u2026*", 0x123456, null);
		NotificationRule removedSuffix = rule("removed", "*secret*", null, null);
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
		NotificationRule recolor = rule("new color", "*new*", 0x333333, null);
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
		assertEquals("3s", after.getTimeLabel());

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

		assertEquals(Collections.singletonList("after"), messages(state.snapshot()));
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
	public void zeroDurationDoesNotPerformExpirationArithmetic()
	{
		NotificationState seconds =
			new NotificationState(Clock.fixed(Instant.MAX, ZoneOffset.UTC));
		seconds.updatePolicy(policy(1, style(0x111111, 75, true), seconds(0), true,
			RuleSet.empty()));
		seconds.accept("seconds");

		NotificationState ticks = new NotificationState(CLOCK);
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
		MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
		NotificationState state = new NotificationState(clock);
		state.updatePolicy(policy(5, style(0x111111, 75, true), seconds(5), true,
			RuleSet.empty()));
		state.accept("one");
		List<NotificationState.Snapshot> first = state.snapshot();

		clock.advance(Duration.ofSeconds(2));
		state.accept("two");

		assertEquals(Collections.singletonList("one"), messages(first));
		assertEquals("5s", first.get(0).getTimeLabel());
		assertEquals(Arrays.asList("one", "two"), messages(state.snapshot()));
		assertEquals("3s", state.snapshot().get(0).getTimeLabel());
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
	public void expiresSecondsByIdentityWhenShorterEntryIsLater()
	{
		MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
		NotificationState state = new NotificationState(clock);
		state.updatePolicy(secondsPolicy(5, 10, true));
		state.accept("long");
		clock.advance(Duration.ofSeconds(1));
		state.updatePolicy(secondsPolicy(5, 2, true));
		state.accept("short");

		clock.advance(Duration.ofSeconds(2));

		assertEquals(Collections.singletonList("long"), messages(state.snapshot()));
		assertEquals("7s", state.snapshot().get(0).getTimeLabel());
	}

	@Test
	public void expiresSecondsByIdentityWhenShorterEntryIsEarlier()
	{
		MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
		NotificationState state = new NotificationState(clock);
		state.updatePolicy(secondsPolicy(5, 2, true));
		state.accept("short");
		clock.advance(Duration.ofSeconds(1));
		state.updatePolicy(secondsPolicy(5, 10, true));
		state.accept("long");

		clock.advance(Duration.ofSeconds(1));

		assertEquals(Collections.singletonList("long"), messages(state.snapshot()));
		assertEquals("9s", state.snapshot().get(0).getTimeLabel());
	}

	@Test
	public void expiresSecondsAtExactBoundaryButNotBefore()
	{
		MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
		NotificationState state = new NotificationState(clock);
		state.updatePolicy(secondsPolicy(5, 2, true));
		state.accept("boundary");

		clock.advance(Duration.ofMillis(1999));
		assertEquals(Collections.singletonList("boundary"), messages(state.snapshot()));
		// One millisecond still remains, so the countdown rounds up to "1s" rather than
		// showing "0s" for a notification that has not expired.
		assertEquals("1s", state.snapshot().get(0).getTimeLabel());

		clock.advance(Duration.ofMillis(1));
		assertTrue(state.snapshot().isEmpty());
	}

	@Test
	public void removesAllNotificationsExpiringAtTheSameInstant()
	{
		MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
		NotificationState state = new NotificationState(clock);
		state.updatePolicy(secondsPolicy(5, 3, true));
		state.accept("one");
		state.accept("two");
		state.accept("three");

		clock.advance(Duration.ofSeconds(3));

		assertTrue(state.snapshot().isEmpty());
	}

	@Test
	public void formatsFiniteSecondsWithoutWrappingHours()
	{
		MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
		NotificationState state = new NotificationState(clock);
		state.updatePolicy(secondsPolicy(5, 3723, true));
		state.accept("hours");
		state.updatePolicy(secondsPolicy(5, 123, true));
		state.accept("minutes");
		state.updatePolicy(secondsPolicy(5, 3, true));
		state.accept("seconds");

		List<NotificationState.Snapshot> snapshots = state.snapshot();
		assertEquals("1h 2m 3s", snapshots.get(0).getTimeLabel());
		assertEquals("2m 3s", snapshots.get(1).getTimeLabel());
		assertEquals("3s", snapshots.get(2).getTimeLabel());
	}

	@Test
	public void zeroSecondDurationShowsNonnegativeElapsedAgeAndNeverExpires()
	{
		MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
		NotificationState state = new NotificationState(clock);
		state.updatePolicy(secondsPolicy(5, 0, true));
		state.accept("forever");
		assertEquals("0s ago", state.snapshot().get(0).getTimeLabel());

		clock.advance(Duration.ofSeconds(3723));
		assertEquals("1h 2m 3s ago", state.snapshot().get(0).getTimeLabel());

		clock.advance(Duration.ofSeconds(-4000));
		assertEquals("0s ago", state.snapshot().get(0).getTimeLabel());
	}

	@Test
	public void hiddenSecondsLabelStillExpiresNormally()
	{
		MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
		NotificationState state = new NotificationState(clock);
		state.updatePolicy(secondsPolicy(5, 1, false));
		state.accept("hidden label");

		assertNull(state.snapshot().get(0).getTimeLabel());
		clock.advance(Duration.ofSeconds(1));
		assertTrue(state.snapshot().isEmpty());
	}

	@Test
	public void policyChangesAffectOnlyFutureLifetimeAndTimeLabels()
	{
		MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
		NotificationState state = new NotificationState(clock);
		state.updatePolicy(secondsPolicy(5, 10, true));
		state.accept("old");
		clock.advance(Duration.ofSeconds(1));
		state.updatePolicy(secondsPolicy(5, 2, false));
		state.accept("new");

		List<NotificationState.Snapshot> initial = state.snapshot();
		assertEquals("9s", initial.get(0).getTimeLabel());
		assertNull(initial.get(1).getTimeLabel());

		clock.advance(Duration.ofSeconds(2));
		List<NotificationState.Snapshot> afterNewExpires = state.snapshot();
		assertEquals(Collections.singletonList("old"), messages(afterNewExpires));
		assertEquals("7s", afterNewExpires.get(0).getTimeLabel());
	}

	@Test
	public void testNotificationNeverExpiresIsNeverEvictedAndTracksCurrentDefaults()
	{
		MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
		NotificationState state = new NotificationState(clock);
		state.updatePolicy(policy(1, style(0x111111, 75, true), seconds(3), true,
			RuleSet.empty()));
		state.setTestNotificationVisible(true);
		assertTrue(state.isTestNotificationVisible());

		state.accept("real");
		List<NotificationState.Snapshot> both = state.snapshot();
		// Maximum is 1, but the test notification is derived rather than stored, so it does not
		// evict the real one or count against the limit.
		assertEquals(2, both.size());
		assertEquals("real", both.get(0).getMessage());
		assertTrue(both.get(1).getMessage().startsWith("Test notification"));
		assertEquals(0x111111, both.get(1).getBackgroundRgb());

		// Real notifications keep the style they arrived with; the test one follows the defaults.
		state.updatePolicy(policy(1, style(0x222222, 40, true), seconds(3), true,
			RuleSet.empty()));
		clock.advance(Duration.ofSeconds(30));
		List<NotificationState.Snapshot> later = state.snapshot();
		assertEquals(1, later.size());
		assertTrue(later.get(0).getMessage().startsWith("Test notification"));
		assertEquals(0x222222, later.get(0).getBackgroundRgb());
		assertEquals(40, later.get(0).getOpacityPercent());

		state.setTestNotificationVisible(false);
		assertTrue(state.snapshot().isEmpty());
	}

	@Test
	public void clearingLeavesTheTestNotificationInPlace()
	{
		NotificationState state = new NotificationState(CLOCK);
		state.updatePolicy(policy(5, style(0x111111, 75, true), seconds(3), true,
			RuleSet.empty()));
		state.accept("real");
		state.setTestNotificationVisible(true);

		state.clear();

		// Clearing dismisses notifications; it must not remove the anchor being used to position
		// the overlay.
		List<NotificationState.Snapshot> remaining = state.snapshot();
		assertEquals(1, remaining.size());
		assertTrue(remaining.get(0).getMessage().startsWith("Test notification"));
	}

	@Test
	public void expiresTicksAtBoundaryAndLabelsThemAsABareCount()
	{
		MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
		NotificationState state = new NotificationState(clock);
		state.updatePolicy(tickPolicy(5, 2, true));
		state.accept("ticks");

		// The published plugin rendered ticks as just the number; the unit is already set in
		// config, and a "ticks" suffix on a value changing every 600ms only adds noise.
		assertEquals("2", state.snapshot().get(0).getTimeLabel());
		state.onGameTick();
		assertEquals("1", state.snapshot().get(0).getTimeLabel());
		state.onGameTick();
		assertTrue(state.snapshot().isEmpty());
	}

	@Test
	public void zeroTickDurationCountsUpAndNeverExpires()
	{
		MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
		NotificationState state = new NotificationState(clock);
		state.updatePolicy(tickPolicy(5, 0, true));
		state.accept("ticks");

		assertEquals("0", state.snapshot().get(0).getTimeLabel());
		for (int i = 0; i < 42; i++)
		{
			state.onGameTick();
		}
		// Also bare, matching the published plugin, which appended "ago" only for seconds.
		assertEquals("42", state.snapshot().get(0).getTimeLabel());
	}

	@Test
	public void expiresTicksByIdentityInBothStaggeredOrders()
	{
		MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
		NotificationState laterShort = new NotificationState(clock);
		laterShort.updatePolicy(tickPolicy(5, 5, true));
		laterShort.accept("long");
		laterShort.onGameTick();
		laterShort.updatePolicy(tickPolicy(5, 2, true));
		laterShort.accept("short");
		laterShort.onGameTick();
		laterShort.onGameTick();
		assertEquals(Collections.singletonList("long"), messages(laterShort.snapshot()));

		NotificationState earlierShort = new NotificationState(clock);
		earlierShort.updatePolicy(tickPolicy(5, 2, true));
		earlierShort.accept("short");
		earlierShort.onGameTick();
		earlierShort.updatePolicy(tickPolicy(5, 5, true));
		earlierShort.accept("long");
		earlierShort.onGameTick();
		assertEquals(Collections.singletonList("long"), messages(earlierShort.snapshot()));
	}

	@Test
	public void snapshotReadsClockExactlyOnceEvenWhenEmptyOrTickBased()
	{
		AtomicInteger instantCalls = new AtomicInteger();
		NotificationState state = new NotificationState(countingClock(instantCalls));

		state.snapshot();
		assertEquals(1, instantCalls.get());

		state.updatePolicy(tickPolicy(5, 3, true));
		state.accept("one");
		state.accept("two");
		instantCalls.set(0);
		state.snapshot();
		assertEquals(1, instantCalls.get());
	}

	@Test
	public void gameTickDoesNotReadClockPruneOrMutateReturnedSnapshots()
	{
		AtomicInteger instantCalls = new AtomicInteger();
		NotificationState state = new NotificationState(countingClock(instantCalls));
		state.updatePolicy(tickPolicy(5, 1, true));
		state.accept("expires");
		List<NotificationState.Snapshot> beforeTick = state.snapshot();
		assertEquals("1", beforeTick.get(0).getTimeLabel());
		instantCalls.set(0);

		state.onGameTick();

		assertEquals(0, instantCalls.get());
		assertEquals(Collections.singletonList("expires"), messages(beforeTick));
		assertEquals("1", beforeTick.get(0).getTimeLabel());
		assertTrue(state.snapshot().isEmpty());
	}

	@Test
	public void repeatedClearDoesNotAffectLaterNotifications()
	{
		MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
		NotificationState state = new NotificationState(clock);
		state.updatePolicy(secondsPolicy(5, 1, true));
		state.accept("old");
		clock.advance(Duration.ofMillis(900));
		state.clear();
		state.clear();
		state.accept("new");

		clock.advance(Duration.ofMillis(100));
		assertEquals(Collections.singletonList("new"), messages(state.snapshot()));
		clock.advance(Duration.ofMillis(900));
		assertTrue(state.snapshot().isEmpty());
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

	private static NotificationState.Policy secondsPolicy(int maximum, int duration,
		boolean showTime)
	{
		return policy(maximum, style(0x111111, 75, true), seconds(duration), showTime,
			RuleSet.empty());
	}

	private static NotificationState.Policy tickPolicy(int maximum, int duration,
		boolean showTime)
	{
		return policy(maximum, style(0x111111, 75, true),
			new NotificationState.Lifetime(NotificationState.Unit.TICKS, duration), showTime,
			RuleSet.empty());
	}

	private static Clock countingClock(AtomicInteger instantCalls)
	{
		return new Clock()
		{
			@Override
			public ZoneId getZone()
			{
				return ZoneOffset.UTC;
			}

			@Override
			public Clock withZone(ZoneId zone)
			{
				return this;
			}

			@Override
			public Instant instant()
			{
				instantCalls.incrementAndGet();
				return NOW;
			}
		};
	}

	private static RuleSet rules(NotificationRule... rules)
	{
		RuleSet.CompileResult result = RuleSet.compile(Arrays.asList(rules));
		assertTrue(result.getErrors().toString(), result.getErrors().isEmpty());
		return result.getRuleSet();
	}

	private static NotificationRule rule(String name, String pattern, Integer rgb, Integer opacity)
	{
		return rule(name, pattern, rgb, opacity, null);
	}

	private static NotificationRule rule(String name, String pattern, Integer rgb, Integer opacity,
		Boolean visible)
	{
		return new NotificationRule(UUID.randomUUID(), name, true, pattern, rgb, opacity, visible,
			null);
	}

	private static NotificationRule disabledRule(String name, String pattern, Boolean visible)
	{
		return new NotificationRule(UUID.randomUUID(), name, false, pattern, null, null, visible,
			null);
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
}
