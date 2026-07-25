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
package com.notificationpanel.ui;

import com.notificationpanel.state.NotificationState;
import java.awt.Color;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class NotificationLogPanelTest
{
	private static final Instant NOON = Instant.parse("2026-07-25T12:00:00Z");

	@Test
	public void showsSeededEntriesNewestFirstWithTheirTimeAndColour() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			NotificationLog log = new NotificationLog();
			log.add(new NotificationState.Accepted("first", 0x181818, NOON));
			log.add(new NotificationState.Accepted("second", 0xBF616A, NOON.plusSeconds(61)));

			NotificationLogPanel panel = panel(log, () ->
			{
			}, ZoneOffset.UTC);

			assertEquals(2, panel.getRowCountForTest());
			assertTrue(panel.getRowTextsForTest().get(0).contains("second"));
			assertTrue(panel.getRowTextsForTest().get(0).contains("12:01:01"));
			assertTrue(panel.getRowTextsForTest().get(1).contains("first"));
			assertEquals(new Color(0xBF616A), panel.getStripeColorForTest(0));
			assertEquals(new Color(0x181818), panel.getStripeColorForTest(1));
		});
	}

	@Test
	public void anEmptyLogExplainsItselfUntilSomethingArrives() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			NotificationLog log = new NotificationLog();
			NotificationLogPanel panel = panel(log, () ->
			{
			}, ZoneOffset.UTC);

			assertEquals(0, panel.getRowCountForTest());
			assertTrue(panel.getEmptyStateTextForTest().contains("No notifications yet"));

			NotificationState.Accepted arrived =
				new NotificationState.Accepted("first", 0x181818, NOON);
			log.add(arrived);
			panel.entryLogged(arrived);

			assertEquals(1, panel.getRowCountForTest());
			assertTrue(panel.getEmptyStateTextForTest().isEmpty());
		});
	}

	@Test
	public void appendingPastCapacityDropsTheOldestRow() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			NotificationLog log = new NotificationLog();
			NotificationLogPanel panel = panel(log, () ->
			{
			}, ZoneOffset.UTC);

			for (int index = 0; index < NotificationLog.CAPACITY + 3; index++)
			{
				NotificationState.Accepted entry =
					new NotificationState.Accepted("message " + index, 0x181818, NOON);
				log.add(entry);
				panel.entryLogged(entry);
			}

			assertEquals(NotificationLog.CAPACITY, panel.getRowCountForTest());
			assertTrue(panel.getRowTextsForTest().get(0)
				.contains("message " + (NotificationLog.CAPACITY + 2)));
			assertTrue(panel.getRowTextsForTest().get(NotificationLog.CAPACITY - 1)
				.contains("message 3"));
		});
	}

	@Test
	public void clearLogEmptiesTheLogAndClearPanelDelegates() throws Exception
	{
		AtomicInteger cleared = new AtomicInteger();

		SwingUtilities.invokeAndWait(() ->
		{
			NotificationLog log = new NotificationLog();
			log.add(new NotificationState.Accepted("first", 0x181818, NOON));
			NotificationLogPanel panel =
				panel(log, cleared::incrementAndGet, ZoneOffset.UTC);

			panel.clickClearPanelForTest();
			assertEquals(1, cleared.get());
			// Clearing the panel is not clearing the record: the row is still there.
			assertEquals(1, panel.getRowCountForTest());

			panel.clickClearLogForTest();
			assertEquals(0, panel.getRowCountForTest());
			assertTrue(log.isEmpty());
			assertTrue(panel.getEmptyStateTextForTest().contains("No notifications yet"));
		});
	}

	@Test
	public void copyTextPutsOnlyTheMessageOnTheClipboard() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			NotificationLog log = new NotificationLog();
			log.add(new NotificationState.Accepted("You catch a shark.", 0x181818, NOON));
			Clipboard clipboard = new Clipboard("test");
			NotificationLogPanel panel = new NotificationLogPanel(log, () ->
			{
			}, new FakeRuleActions(), ZoneOffset.UTC, clipboard);

			panel.clickCopyTextForTest(0);

			assertEquals("You catch a shark.", clipboardText(clipboard));
		});
	}

	@Test
	public void arrivingNotificationsDoNotPushAwayWhatSomeoneScrolledDownToRead()
	{
		// A scroll position is an offset in pixels from the top, and rows are inserted above it, so
		// leaving the offset alone is what makes the message someone found walk away from them.
		assertEquals(138, NotificationLogPanel.anchoredScroll(100, 38));
		assertEquals(176, NotificationLogPanel.anchoredScroll(138, 38));
		// Two lines of message rather than one: whatever the row's height, the offset moves by it.
		assertEquals(157, NotificationLogPanel.anchoredScroll(100, 57));

		// At the top the list follows new arrivals, which is what someone watching the newest
		// notifications wants. A negative value cannot come from a scrollbar, but clamping beats
		// propagating one into setValue.
		assertEquals(0, NotificationLogPanel.anchoredScroll(0, 38));
		assertEquals(0, NotificationLogPanel.anchoredScroll(-5, 38));
	}

	@Test
	public void anEntryLoggedAtTheTopOfTheListLeavesTheScrollPositionAlone() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			NotificationLog log = new NotificationLog();
			NotificationLogPanel panel = panel(log, () ->
			{
			}, new FakeRuleActions());

			NotificationState.Accepted entry =
				new NotificationState.Accepted("You catch a shark.", 0x181818, NOON);
			log.add(entry);
			panel.entryLogged(entry);

			assertEquals(0, panel.scrollValueForTest());
		});
	}

	@Test
	public void aFullLogDoesNotAskToBeAsTallAsAllOfIt() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			NotificationLog log = new NotificationLog();
			NotificationLogPanel panel = panel(log, () ->
			{
			}, new FakeRuleActions());
			for (int index = 0; index < NotificationLog.CAPACITY; index++)
			{
				NotificationState.Accepted entry = new NotificationState.Accepted(
					"You catch a shark number " + index + ".", 0x181818, NOON);
				log.add(entry);
				panel.entryLogged(entry);
			}

			// A Scrollable that answers getPreferredScrollableViewportSize with the size of all its
			// rows asks the scroll pane to be as tall as its own contents, which is the opposite of
			// scrolling: 200 rows came to roughly 7700px and the sidebar passed that on to the
			// window. The bound is deliberately loose -- row heights depend on the font, and the
			// bug it guards against is off by an order of magnitude, not a few pixels.
			assertTrue("preferred height was " + panel.getPreferredSize().height,
				panel.getPreferredSize().height < 1000);
		});
	}

	@Test
	public void everyPartOfARowResolvesToItsMenuRatherThanLeavingADeadZone() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			NotificationLog log = new NotificationLog();
			log.add(new NotificationState.Accepted("You catch a shark.", 0x181818, NOON));
			NotificationLogPanel panel = panel(log, () ->
			{
			}, new FakeRuleActions());

			List<JPopupMenu> resolved = panel.resolvedRowPopupsForTest(0);

			// The row, the stripe, the text column, the timestamp and the message. Swing's popup
			// lookup stops at the first ancestor that neither has a menu nor inherits one, so a
			// component missed here -- the text column especially, which is most of the row's
			// area -- silently answers a right-click with nothing. If a row grows a component,
			// this failing is the point: the new one has to opt in too.
			assertEquals(5, resolved.size());
			for (JPopupMenu menu : resolved)
			{
				assertNotNull(menu);
				assertSame(resolved.get(0), menu);
			}
		});
	}

	@Test
	public void createRuleItemPassesTheRowsMessageToRuleActions() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			NotificationLog log = new NotificationLog();
			log.add(new NotificationState.Accepted("You catch a shark.", 0x181818, NOON));
			FakeRuleActions ruleActions = new FakeRuleActions();
			NotificationLogPanel panel = panel(log, () ->
			{
			}, ruleActions);

			panel.clickCreateRuleForTest(0);

			assertEquals(1, ruleActions.createCount);
			assertEquals("You catch a shark.", ruleActions.createdMessage);
		});
	}

	@Test
	public void createRuleItemIsEnabledOnlyWhenRuleActionsAllowsIt() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			NotificationLog log = new NotificationLog();
			log.add(new NotificationState.Accepted("first", 0x181818, NOON));
			FakeRuleActions ruleActions = new FakeRuleActions();
			NotificationLogPanel panel = panel(log, () ->
			{
			}, ruleActions);

			ruleActions.canCreate = false;
			assertFalse(panel.isCreateRuleEnabledForTest(0));

			ruleActions.canCreate = true;
			assertTrue(panel.isCreateRuleEnabledForTest(0));
		});
	}

	private static String clipboardText(Clipboard clipboard)
	{
		try
		{
			return (String) clipboard.getData(DataFlavor.stringFlavor);
		}
		catch (UnsupportedFlavorException | IOException exception)
		{
			throw new AssertionError(exception);
		}
	}

	private static NotificationLogPanel panel(NotificationLog log, Runnable clearPanelAction,
		ZoneId zone)
	{
		return panel(log, clearPanelAction, zone, new FakeRuleActions());
	}

	private static NotificationLogPanel panel(NotificationLog log, Runnable clearPanelAction,
		NotificationLogPanel.RuleActions ruleActions)
	{
		return panel(log, clearPanelAction, ZoneOffset.UTC, ruleActions);
	}

	private static NotificationLogPanel panel(NotificationLog log, Runnable clearPanelAction,
		ZoneId zone, NotificationLogPanel.RuleActions ruleActions)
	{
		return new NotificationLogPanel(log, clearPanelAction, ruleActions, zone,
			new Clipboard("test"));
	}

	/** A minimal double: records what "Create rule" was asked to do and lets a test veto it. */
	private static final class FakeRuleActions implements NotificationLogPanel.RuleActions
	{
		private boolean canCreate = true;
		private String createdMessage;
		private int createCount;

		@Override
		public boolean canCreateRule()
		{
			return canCreate;
		}

		@Override
		public void createRule(String message)
		{
			createdMessage = message;
			createCount++;
		}
	}
}
