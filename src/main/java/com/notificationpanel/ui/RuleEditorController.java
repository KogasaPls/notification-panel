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

import com.notificationpanel.rules.LegacyRuleMigrator;
import com.notificationpanel.rules.NotificationRule;
import com.notificationpanel.rules.RuleConfigStore;
import com.notificationpanel.rules.RuleDocument;
import com.notificationpanel.rules.RuleSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.swing.SwingUtilities;

public final class RuleEditorController
{
	private static final String EDT_ERROR = "Rule editor mutations must run on the EDT.";
	// NotificationRule enforces these caps itself but keeps them private, matching how
	// LegacyRuleMigrator already hardcodes its own copy of the pattern limit rather than reaching
	// into that class for a constant.
	private static final int MAX_RULE_PATTERN_CODE_POINTS = 512;
	private static final int MAX_RULE_NAME_CODE_POINTS = 64;
	// The two wildcards a prefilled pattern is wrapped in are load-bearing, not cosmetic: a
	// truncated *prefix* wrapped in wildcards still matches the original message as a substring,
	// where an anchored (untruncated-looking) pattern would not. Leave room for both.
	private static final int MAX_PREFILL_MESSAGE_CODE_POINTS = MAX_RULE_PATTERN_CODE_POINTS - 2;

	private final RuleConfigStore store;
	private RuleDocument document;
	private boolean wasMigrated;
	private String blockingError;

	public RuleEditorController(RuleConfigStore store)
	{
		requireEdt();
		this.store = Objects.requireNonNull(store, "store");
		applyLoadResult(store.load());
	}

	public List<NotificationRule> getRules()
	{
		requireEdt();
		return document.getRules();
	}

	public RuleDocument getDocument()
	{
		requireEdt();
		return document;
	}

	public boolean hasBlockingError()
	{
		requireEdt();
		return blockingError != null;
	}

	public String getBlockingError()
	{
		requireEdt();
		return blockingError == null ? "" : blockingError;
	}

	public boolean wasMigrated()
	{
		requireEdt();
		return wasMigrated;
	}

	/**
	 * Marks this session's rules as freshly migrated even though the editor's own load did not
	 * perform the migration. The plugin's policy load migrates and writes {@code rulesV1} before
	 * the editor is created, so without this the one-time migration banner would never show.
	 */
	public void markMigrated()
	{
		requireEdt();
		wasMigrated = true;
	}

	public NotificationRule newDraft()
	{
		requireEdt();
		return new NotificationRule(uniqueId(), "Rule " + (document.getRules().size() + 1), true,
			"", null, null, null, null);
	}

	/**
	 * The context menu's version of {@link #newDraft()}: a draft prefilled from a logged message
	 * instead of starting blank, for "Create rule" on the Notifications tab.
	 *
	 * <p>The pattern is the message wrapped in {@code *...*} rather than the bare message, because a
	 * pattern must describe the whole notification and a wrapped one still matches it as a
	 * substring. A logged message can run to {@link com.notificationpanel.layout.NotificationText
	 * #MAX_CODE_POINTS}, four times what a pattern allows, so the message is truncated to leave room
	 * for both wildcards; the name is truncated separately to the shorter cap that field enforces. A
	 * null, empty or blank message has nothing to prefill, so it falls back to a plain
	 * {@link #newDraft()} instead of producing a pattern of just {@code **} and a blank name.</p>
	 */
	public NotificationRule newDraftFor(String message)
	{
		requireEdt();
		if (message == null || message.trim().isEmpty())
		{
			return newDraft();
		}
		String pattern =
			"*" + truncateToCodePoints(message, MAX_PREFILL_MESSAGE_CODE_POINTS) + "*";
		String name = truncateToCodePoints(message, MAX_RULE_NAME_CODE_POINTS);
		return new NotificationRule(uniqueId(), name, true, pattern, null, null, null, null);
	}

	public NotificationRule find(UUID id)
	{
		requireEdt();
		int index = indexOf(id);
		if (index < 0)
		{
			throw new IllegalArgumentException("Unknown notification rule: " + id + ".");
		}
		return document.getRules().get(index);
	}

	public SaveResult add(NotificationRule draft)
	{
		requireEdt();
		if (document.getRules().size() >= RuleSet.MAX_RULES)
		{
			return SaveResult.failure(
				"A rule set may contain at most " + RuleSet.MAX_RULES + " rules.");
		}
		List<String> errors = validateDraft(draft);
		if (!errors.isEmpty())
		{
			return SaveResult.failure(errors);
		}
		List<NotificationRule> rules = new ArrayList<>(document.getRules());
		rules.add(draft);
		return save(rules);
	}

	public SaveResult edit(UUID id, NotificationRule draft)
	{
		requireEdt();
		int index = indexOf(id);
		if (index < 0)
		{
			return unknown(id);
		}
		if (draft == null)
		{
			return SaveResult.failure("Rule draft must not be null.");
		}
		NotificationRule edited = new NotificationRule(id, draft.getName(), draft.isEnabled(),
			draft.getPattern(), draft.getBackgroundRgb(), draft.getOpacityPercent(),
			draft.getVisibility(), null);
		List<String> errors = validateDraft(edited);
		if (!errors.isEmpty())
		{
			return SaveResult.failure(errors);
		}
		List<NotificationRule> rules = new ArrayList<>(document.getRules());
		rules.set(index, edited);
		return save(rules);
	}

	public SaveResult setEnabled(UUID id, boolean enabled)
	{
		requireEdt();
		int index = indexOf(id);
		if (index < 0)
		{
			return unknown(id);
		}
		NotificationRule existing = document.getRules().get(index);
		if (existing.isEnabled() == enabled)
		{
			return SaveResult.failure("Rule is already "
				+ (enabled ? "enabled." : "disabled."));
		}
		NotificationRule updated = existing.withEnabled(enabled);
		if (enabled && isWidening(existing.getMigrationNote()))
		{
			// The note asks the user to agree to a pattern that now matches more than it used to,
			// and switching the rule on is that agreement. Carrying it forward would leave an
			// error-coloured warning on a rule the user has already dealt with, with nothing on
			// screen offering to clear it.
			updated = updated.withMigrationNote(null);
		}
		List<NotificationRule> rules = new ArrayList<>(document.getRules());
		rules.set(index, updated);
		return save(rules);
	}

	public SaveResult moveUp(UUID id)
	{
		requireEdt();
		int index = indexOf(id);
		if (index < 0)
		{
			return unknown(id);
		}
		if (index == 0)
		{
			return SaveResult.failure("Rule is already first.");
		}
		return move(index, index - 1);
	}

	public SaveResult moveDown(UUID id)
	{
		requireEdt();
		int index = indexOf(id);
		if (index < 0)
		{
			return unknown(id);
		}
		if (index == document.getRules().size() - 1)
		{
			return SaveResult.failure("Rule is already last.");
		}
		return move(index, index + 1);
	}

	public SaveResult delete(UUID id)
	{
		requireEdt();
		int index = indexOf(id);
		if (index < 0)
		{
			return unknown(id);
		}
		List<NotificationRule> rules = new ArrayList<>(document.getRules());
		rules.remove(index);
		return save(rules);
	}

	public SaveResult reset()
	{
		requireEdt();
		try
		{
			store.resetStructuredRules();
			RuleConfigStore.LoadResult result = store.load();
			applyLoadResult(result);
			if (result.hasBlockingError())
			{
				return SaveResult.failure(result.getBlockingError());
			}
			if (!document.getRules().isEmpty() || !document.getMigrationWarnings().isEmpty())
			{
				blockingError = "Reset did not produce an empty rule document.";
				document = emptyDocument();
				wasMigrated = false;
				return SaveResult.failure(blockingError);
			}
			// Belt and braces: reset writes a valid empty document, so the reload above reports
			// no migration. Clearing rules is not a user-facing import and must never re-open
			// the editor's one-time migration gate.
			wasMigrated = false;
			return SaveResult.success();
		}
		catch (RuntimeException exception)
		{
			return SaveResult.failure(exceptionMessage(exception));
		}
	}

	public void reload()
	{
		requireEdt();
		applyLoadResult(store.load());
	}

	List<String> validateForEditor(NotificationRule draft)
	{
		requireEdt();
		return validateDraft(draft);
	}

	private static boolean isWidening(String migrationNote)
	{
		return migrationNote != null
			&& migrationNote.startsWith(LegacyRuleMigrator.WIDENED_NOTE_PREFIX);
	}

	private SaveResult move(int from, int to)
	{
		List<NotificationRule> rules = new ArrayList<>(document.getRules());
		Collections.swap(rules, from, to);
		return save(rules);
	}

	private SaveResult save(List<NotificationRule> rules)
	{
		if (blockingError != null)
		{
			return SaveResult.failure(blockingError);
		}
		RuleDocument candidate = new RuleDocument(document.getSchemaVersion(),
			document.getMigrationWarnings(), rules);
		List<String> errors = validateDocument(candidate);
		if (!errors.isEmpty())
		{
			return SaveResult.failure(errors);
		}
		try
		{
			store.save(candidate);
		}
		catch (RuntimeException exception)
		{
			return SaveResult.failure(exceptionMessage(exception));
		}
		document = candidate;
		return SaveResult.success();
	}

	private static List<String> validateDraft(NotificationRule draft)
	{
		if (draft == null)
		{
			return Collections.singletonList("Rule draft must not be null.");
		}
		return List.copyOf(draft.validationErrors());
	}

	private static List<String> validateDocument(RuleDocument candidate)
	{
		try
		{
			RuleSet.CompileResult compiled = RuleSet.compile(candidate.getRules());
			return List.copyOf(compiled.getErrors().values());
		}
		catch (IllegalArgumentException exception)
		{
			return Collections.singletonList(exceptionMessage(exception));
		}
	}

	private int indexOf(UUID id)
	{
		if (id == null)
		{
			return -1;
		}
		List<NotificationRule> rules = document.getRules();
		for (int index = 0; index < rules.size(); index++)
		{
			if (rules.get(index).getId().equals(id))
			{
				return index;
			}
		}
		return -1;
	}

	private boolean contains(UUID id)
	{
		return indexOf(id) >= 0;
	}

	private UUID uniqueId()
	{
		UUID id;
		do
		{
			id = UUID.randomUUID();
		}
		while (contains(id));
		return id;
	}

	/** Truncates by code points, not chars, so a supplementary character is never cut mid-pair. */
	private static String truncateToCodePoints(String value, int maxCodePoints)
	{
		if (value.codePointCount(0, value.length()) <= maxCodePoints)
		{
			return value;
		}
		return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
	}

	private static SaveResult unknown(UUID id)
	{
		return SaveResult.failure("Unknown notification rule: " + id + ".");
	}

	private void applyLoadResult(RuleConfigStore.LoadResult result)
	{
		document = Objects.requireNonNull(result.getDocument(), "loadResult.document");
		wasMigrated = result.wasMigrated();
		blockingError = result.hasBlockingError()
			? Objects.requireNonNull(result.getBlockingError(), "loadResult.blockingError") : null;
	}

	private static RuleDocument emptyDocument()
	{
		return new RuleDocument(RuleDocument.CURRENT_SCHEMA_VERSION, Collections.emptyList(),
			Collections.emptyList());
	}

	private static String exceptionMessage(RuntimeException exception)
	{
		String message = exception.getMessage();
		return message == null || message.isEmpty()
			? "Unable to save notification rules." : message;
	}

	private static void requireEdt()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			throw new IllegalStateException(EDT_ERROR);
		}
	}

	public static final class SaveResult
	{
		private static final SaveResult SUCCESS = new SaveResult(true, Collections.emptyList());

		private final boolean success;
		private final List<String> errors;

		private SaveResult(boolean success, List<String> errors)
		{
			this.success = success;
			this.errors = List.copyOf(errors);
		}

		private static SaveResult success()
		{
			return SUCCESS;
		}

		private static SaveResult failure(String error)
		{
			return failure(Collections.singletonList(Objects.requireNonNull(error, "error")));
		}

		private static SaveResult failure(List<String> errors)
		{
			Objects.requireNonNull(errors, "errors");
			if (errors.isEmpty())
			{
				return failure("Unable to save notification rules.");
			}
			return new SaveResult(false, errors);
		}

		public boolean isSuccess()
		{
			return success;
		}

		public List<String> getErrors()
		{
			return errors;
		}
	}
}
