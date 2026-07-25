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
	private static final String MIGRATION_TOO_LARGE_WARNING =
		"The rules imported from your pre-2.0 configuration were too large to store, so none were "
			+ "imported. Your Regex and Options lists are unchanged.";

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
		// A blank value carries no rules and cannot be decoded, so treat it as "never written"
		// rather than stranding the user behind a corrupt-data banner.
		if (structured != null && !structured.trim().isEmpty())
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
		// Migration runs on every install that has no rulesV1 yet, including a brand new one with
		// nothing to import. Nothing imported means nothing to report and nothing to write:
		// writing would mark the profile migrated, so legacy lists arriving later -- restored,
		// synced, or switched to -- would never be imported at all.
		if (document.getRules().isEmpty() && document.getMigrationWarnings().isEmpty())
		{
			return LoadResult.loaded(document);
		}
		try
		{
			write(document);
		}
		catch (IllegalArgumentException exception)
		{
			// The import encoded to more than configuration can hold. Letting that escape would
			// throw out of the only call that builds the sidebar, so the user would get no editor
			// and no way to recover -- every session, since nothing would have been written.
			// Storing a warning-only document instead reports the failure and settles the profile,
			// and the legacy lists it was built from are still there to import by hand.
			document = new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION,
				Collections.singletonList(MIGRATION_TOO_LARGE_WARNING), Collections.emptyList());
			write(document);
		}
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
		write(document);
	}

	/**
	 * Replaces the structured rules with an empty document, discarding a corrupt value.
	 *
	 * <p>The legacy {@code regexList} and {@code colorList} values are deliberately left alone:
	 * they are the user's only remaining record of their pre-2.0 configuration, and recovering
	 * from corrupt rule data must not destroy it. Writing a valid empty document rather than
	 * unsetting the key also keeps migration from running a second time.</p>
	 */
	public void resetStructuredRules()
	{
		write(emptyDocument());
	}

	/**
	 * Writes a document, refusing one this store could not read back.
	 *
	 * <p>Decoding rejects anything over the length cap, so writing past it would store rules that
	 * load as corrupt from then on. The cap is checked here rather than only on read so that
	 * whatever reaches configuration is always loadable.</p>
	 */
	private void write(RuleDocument document)
	{
		String encoded = codec.encode(document);
		if (encoded.length() > RuleCodec.MAX_CONFIG_LENGTH)
		{
			// Surfaced to the user by the editor, so it says what to do about it. Reachable now
			// that the rule cap is high enough for stored length to be the limit that binds first.
			throw new IllegalArgumentException("These rules are too large to store, at over "
				+ RuleCodec.MAX_CONFIG_LENGTH + " characters. Remove a rule or shorten some "
				+ "patterns.");
		}
		configManager.setConfiguration(GROUP, RULES_KEY, encoded);
	}

	private static RuleDocument emptyDocument()
	{
		return new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION, Collections.emptyList(),
			Collections.emptyList());
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
