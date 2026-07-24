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
package com.notificationpanel.rules;

import java.util.List;
import java.util.Objects;

public final class RuleDocument
{
	public static final int CURRENT_SCHEMA_VERSION = 1;

	private final int schemaVersion;
	private final List<String> migrationWarnings;
	private final List<NotificationRule> rules;

	public RuleDocument(int schemaVersion, List<String> migrationWarnings,
		List<NotificationRule> rules)
	{
		this.schemaVersion = schemaVersion;
		this.migrationWarnings = List.copyOf(migrationWarnings);
		this.rules = List.copyOf(rules);
	}

	public int getSchemaVersion()
	{
		return schemaVersion;
	}

	public List<String> getMigrationWarnings()
	{
		return migrationWarnings;
	}

	public List<NotificationRule> getRules()
	{
		return rules;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof RuleDocument))
		{
			return false;
		}
		RuleDocument document = (RuleDocument) other;
		return schemaVersion == document.schemaVersion
			&& migrationWarnings.equals(document.migrationWarnings)
			&& rules.equals(document.rules);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(schemaVersion, migrationWarnings, rules);
	}
}
