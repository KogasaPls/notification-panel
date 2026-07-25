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
import java.time.Instant;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NotificationLogTest
{
	private static final Instant NOW = Instant.parse("2026-07-25T12:34:56Z");

	@Test
	public void keepsEntriesOldestFirstAndDropsTheOldestPastCapacity() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			NotificationLog log = new NotificationLog();
			assertTrue(log.isEmpty());

			for (int index = 0; index < NotificationLog.CAPACITY + 5; index++)
			{
				log.add(entry("message " + index));
			}

			List<NotificationState.Accepted> entries = log.getEntries();
			assertFalse(log.isEmpty());
			assertEquals(NotificationLog.CAPACITY, entries.size());
			assertEquals("message 5", entries.get(0).getMessage());
			assertEquals("message " + (NotificationLog.CAPACITY + 4),
				entries.get(entries.size() - 1).getMessage());
		});
	}

	@Test
	public void clearEmptiesIt() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			NotificationLog log = new NotificationLog();
			log.add(entry("one"));
			log.clear();
			assertTrue(log.isEmpty());
			assertEquals(0, log.getEntries().size());
		});
	}

	@Test
	public void refusesEveryAccessOffTheEventDispatchThread()
	{
		NotificationLog log = new NotificationLog();

		assertEquals(EDT_ERROR,
			assertThrows(IllegalStateException.class, () -> log.add(entry("one"))).getMessage());
		assertEquals(EDT_ERROR,
			assertThrows(IllegalStateException.class, log::getEntries).getMessage());
		assertEquals(EDT_ERROR,
			assertThrows(IllegalStateException.class, log::isEmpty).getMessage());
		assertEquals(EDT_ERROR,
			assertThrows(IllegalStateException.class, log::clear).getMessage());
	}

	@Test
	public void returnsAnUnmodifiableView() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			NotificationLog log = new NotificationLog();
			log.add(entry("one"));
			assertThrows(UnsupportedOperationException.class, () -> log.getEntries().clear());
		});
	}

	private static final String EDT_ERROR = "Notification log access must run on the EDT.";

	private static NotificationState.Accepted entry(String message)
	{
		return new NotificationState.Accepted(message, 0x181818, NOW);
	}
}
