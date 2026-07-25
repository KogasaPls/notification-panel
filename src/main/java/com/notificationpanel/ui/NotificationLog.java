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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import javax.swing.SwingUtilities;

/**
 * What this session's notifications were, for the sidebar to show.
 *
 * <p>EDT-confined, like {@link RuleEditorController}: the client thread resolves a notification and
 * the plugin hands the result over with {@code SwingUtilities.invokeLater}, so nothing here is
 * shared between threads and there is no lock on the client thread's path. Every method says so by
 * throwing.</p>
 *
 * <p>Held for the session and no longer. Persisting it would put game chatter into RuneLite's
 * synced configuration and add a write per notification; the cap is what stops a long session
 * growing without bound.</p>
 */
public final class NotificationLog
{
	/**
	 * How many notifications are kept.
	 *
	 * <p>Public because the panel appends rather than re-reading, and so has to trim by the same
	 * number. Deep enough to cover a raid or a long trip, shallow enough that the panel can hold one
	 * component per entry.</p>
	 */
	public static final int CAPACITY = 200;
	private static final String EDT_ERROR = "Notification log access must run on the EDT.";

	private final Deque<NotificationState.Accepted> entries = new ArrayDeque<>();

	public void add(NotificationState.Accepted entry)
	{
		requireEdt();
		Objects.requireNonNull(entry, "entry");
		entries.addLast(entry);
		while (entries.size() > CAPACITY)
		{
			entries.removeFirst();
		}
	}

	/** The entries, oldest first. The panel shows them the other way up. */
	public List<NotificationState.Accepted> getEntries()
	{
		requireEdt();
		return Collections.unmodifiableList(new ArrayList<>(entries));
	}

	public boolean isEmpty()
	{
		requireEdt();
		return entries.isEmpty();
	}

	public void clear()
	{
		requireEdt();
		entries.clear();
	}

	private static void requireEdt()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			throw new IllegalStateException(EDT_ERROR);
		}
	}
}
