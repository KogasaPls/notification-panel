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

/**
 * In-memory notification history for the sidebar log. Confined to the EDT.
 */
public final class NotificationLog
{
	static final int CAPACITY = 200;
	private static final String EDT_SUBJECT = "Notification log access";

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

	/** Returns the entries, oldest first. */
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
		Edt.require(EDT_SUBJECT);
	}
}
