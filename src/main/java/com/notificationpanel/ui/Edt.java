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

import javax.swing.SwingUtilities;

/**
 * The EDT confinement every class in this package asserts, in one place.
 *
 * <p>Five of them are confined -- the log, its panel, the sidebar hosting both tabs, and the rule
 * editor's panel and controller -- and each carried its own copy of this check. Each still names
 * what it is confining, since the sentence a test reads back has to say which contract was broken,
 * but the check itself is written once.</p>
 */
final class Edt
{
	private Edt()
	{
	}

	/**
	 * @param subject what is confined, as the sentence's subject: "Sidebar mutations",
	 *                "Notification log access".
	 */
	static void require(String subject)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			throw new IllegalStateException(subject + " must run on the EDT.");
		}
	}
}
