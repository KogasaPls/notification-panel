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

import com.google.gson.Gson;
import java.util.Collections;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;

public final class RuleConfigStore
{
	public static final String GROUP = "notificationpanel";
	public static final String RULES_KEY = "rulesV1";
	private static final String REGEX_KEY = "regexList";
	private static final String OPTIONS_KEY = "colorList";

	private final ConfigManager configManager;
	private final RuleCodec codec;
	private final LegacyRuleMigrator migrator;

	@Inject
	RuleConfigStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.codec = new RuleCodec(gson);
		this.migrator = new LegacyRuleMigrator();
	}

	public LoadResult load()
	{
		String structured = configManager.getConfiguration(GROUP, RULES_KEY);
		if (structured != null)
		{
			RuleCodec.DecodeResult decoded = codec.decode(structured);
			if (decoded.isSuccess())
			{
				return LoadResult.loaded(decoded.getDocument());
			}
			return LoadResult.blocked(decoded.getError());
		}

		RuleDocument document = migrator.migrate(
			configManager.getConfiguration(GROUP, REGEX_KEY),
			configManager.getConfiguration(GROUP, OPTIONS_KEY));
		configManager.setConfiguration(GROUP, RULES_KEY, codec.encode(document));
		return LoadResult.migrated(document);
	}

	public void save(RuleDocument document)
	{
		if (document == null)
		{
			throw new IllegalArgumentException("Rule document must not be null.");
		}
		if (document.getSchemaVersion() != RuleDocument.CURRENT_SCHEMA_VERSION)
		{
			throw new IllegalArgumentException("Unsupported rule schema version: "
				+ document.getSchemaVersion() + ".");
		}

		RuleSet.CompileResult compiled = RuleSet.compile(document.getRules());
		if (!compiled.getErrors().isEmpty())
		{
			throw new IllegalArgumentException("Enabled rules are invalid: "
				+ String.join(" ", compiled.getErrors().values()));
		}
		configManager.setConfiguration(GROUP, RULES_KEY, codec.encode(document));
	}

	public void reset()
	{
		configManager.unsetConfiguration(GROUP, RULES_KEY);
		configManager.unsetConfiguration(GROUP, REGEX_KEY);
		configManager.unsetConfiguration(GROUP, OPTIONS_KEY);
	}

	public static final class LoadResult
	{
		private final RuleDocument document;
		private final boolean wasMigrated;
		private final String blockingError;

		private LoadResult(RuleDocument document, boolean wasMigrated, String blockingError)
		{
			this.document = document;
			this.wasMigrated = wasMigrated;
			this.blockingError = blockingError;
		}

		private static LoadResult loaded(RuleDocument document)
		{
			return new LoadResult(document, false, null);
		}

		private static LoadResult migrated(RuleDocument document)
		{
			return new LoadResult(document, true, null);
		}

		private static LoadResult blocked(String error)
		{
			return new LoadResult(new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION,
				Collections.emptyList(), Collections.emptyList()), false, error);
		}

		public RuleDocument getDocument()
		{
			return document;
		}

		public boolean wasMigrated()
		{
			return wasMigrated;
		}

		public boolean hasBlockingError()
		{
			return blockingError != null;
		}

		public String getBlockingError()
		{
			return blockingError;
		}
	}
}
