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
import java.util.regex.Pattern;
import javax.swing.AbstractListModel;

public final class RuleEditorController extends AbstractListModel<NotificationRule>
{
	private static final long serialVersionUID = 1L;
	private static final String EDT_SUBJECT = "Rule editor mutations";
	private static final Pattern LINE_BREAKS =
		Pattern.compile("[\\r\\n\\u000B\\f\\u0085\\u2028\\u2029]+");

	public enum ViewMode
	{
		LIST,
		EDITING
	}

	public interface Listener
	{
		default void onModeChanged(ViewMode mode, NotificationRule draft)
		{
		}

		default void onSelectionChanged(UUID selectedId)
		{
		}

		default void onActionError(String error)
		{
		}
	}

	private final RuleConfigStore store;
	private final List<Listener> listeners = new ArrayList<>();
	private RuleDocument document;
	/**
	 * The compiled view of {@link #document}: its enabled, valid rules, ready to match.
	 *
	 * <p>Assigned only where the document is, and by the same call, so the two cannot disagree and
	 * there is nothing to invalidate. Compiling is what drops the disabled and the invalid, so this
	 * is the set the resolver walks, arrived at the same way.</p>
	 */
	private RuleSet ruleSet;
	private boolean wasMigrated;
	private String blockingError;
	private ViewMode mode = ViewMode.LIST;
	private NotificationRule activeDraft;
	private UUID selectedId;
	private boolean suppressSelectionUpdates;

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

	@Override
	public int getSize()
	{
		requireEdt();
		return document.getRules().size();
	}

	@Override
	public NotificationRule getElementAt(int index)
	{
		requireEdt();
		return document.getRules().get(index);
	}

	public void addListener(Listener listener)
	{
		requireEdt();
		Objects.requireNonNull(listener, "listener");
		if (!listeners.contains(listener))
		{
			listeners.add(listener);
		}
	}

	public void removeListener(Listener listener)
	{
		requireEdt();
		listeners.remove(listener);
	}

	public ViewMode getMode()
	{
		requireEdt();
		return mode;
	}

	public NotificationRule getActiveDraft()
	{
		requireEdt();
		return activeDraft;
	}

	public UUID getSelectedId()
	{
		requireEdt();
		return selectedId;
	}

	public NotificationRule getSelectedRule()
	{
		requireEdt();
		if (selectedId == null)
		{
			return null;
		}
		int index = indexOf(selectedId);
		return index >= 0 ? document.getRules().get(index) : null;
	}

	public void select(UUID id)
	{
		requireEdt();
		if (suppressSelectionUpdates)
		{
			return;
		}
		if (id != null && !contains(id))
		{
			id = null;
		}
		if (!Objects.equals(this.selectedId, id))
		{
			this.selectedId = id;
			fireSelectionChanged(this.selectedId);
		}
	}

	public void clearSelection()
	{
		select(null);
	}

	public void openNewDraft()
	{
		requireEdt();
		this.mode = ViewMode.EDITING;
		this.activeDraft = newDraft();
		fireModeChanged(this.mode, this.activeDraft);
	}

	public void openDraftFor(String message)
	{
		requireEdt();
		this.mode = ViewMode.EDITING;
		this.activeDraft = newDraftFor(message);
		fireModeChanged(this.mode, this.activeDraft);
	}

	public void openSelected()
	{
		requireEdt();
		if (selectedId == null)
		{
			fireActionError("No rule selected to edit.");
			return;
		}
		openRule(selectedId);
	}

	public void openRule(UUID id)
	{
		requireEdt();
		NotificationRule rule = find(id);
		select(id);
		this.mode = ViewMode.EDITING;
		this.activeDraft = rule;
		fireModeChanged(this.mode, this.activeDraft);
	}

	public void cancelEdit()
	{
		requireEdt();
		this.mode = ViewMode.LIST;
		this.activeDraft = null;
		fireModeChanged(this.mode, null);
	}

	public SaveResult saveCurrentDraft(NotificationRule draft)
	{
		requireEdt();
		if (draft == null)
		{
			SaveResult result = SaveResult.failure("Rule draft must not be null.");
			fireActionError(result.getErrors().get(0));
			return result;
		}
		SaveResult result = contains(draft.getId())
			? edit(draft.getId(), draft)
			: add(draft);
		if (result.isSuccess())
		{
			select(draft.getId());
			this.mode = ViewMode.LIST;
			this.activeDraft = null;
			fireModeChanged(this.mode, null);
		}
		else
		{
			this.activeDraft = draft;
			fireActionError(result.getErrors().get(0));
		}
		return result;
	}

	public SaveResult moveSelectedUp()
	{
		requireEdt();
		if (selectedId == null)
		{
			SaveResult result = SaveResult.failure("No rule selected to move.");
			fireActionError(result.getErrors().get(0));
			return result;
		}
		if (!canMoveUp())
		{
			SaveResult result = SaveResult.failure("Rule is already first.");
			fireActionError(result.getErrors().get(0));
			return result;
		}
		SaveResult result = moveUp(selectedId);
		if (!result.isSuccess())
		{
			fireActionError(result.getErrors().get(0));
		}
		return result;
	}

	public SaveResult moveSelectedDown()
	{
		requireEdt();
		if (selectedId == null)
		{
			SaveResult result = SaveResult.failure("No rule selected to move.");
			fireActionError(result.getErrors().get(0));
			return result;
		}
		if (!canMoveDown())
		{
			SaveResult result = SaveResult.failure("Rule is already last.");
			fireActionError(result.getErrors().get(0));
			return result;
		}
		SaveResult result = moveDown(selectedId);
		if (!result.isSuccess())
		{
			fireActionError(result.getErrors().get(0));
		}
		return result;
	}

	public SaveResult toggleSelected()
	{
		requireEdt();
		if (selectedId == null)
		{
			SaveResult result = SaveResult.failure("No rule selected to toggle.");
			fireActionError(result.getErrors().get(0));
			return result;
		}
		NotificationRule rule = getSelectedRule();
		if (rule == null)
		{
			SaveResult result = unknown(selectedId);
			fireActionError(result.getErrors().get(0));
			return result;
		}
		SaveResult result = setEnabled(selectedId, !rule.isEnabled());
		if (!result.isSuccess())
		{
			fireActionError(result.getErrors().get(0));
		}
		return result;
	}

	public SaveResult deleteSelected()
	{
		requireEdt();
		if (selectedId == null)
		{
			SaveResult result = SaveResult.failure("No rule selected to delete.");
			fireActionError(result.getErrors().get(0));
			return result;
		}
		int index = indexOf(selectedId);
		if (index < 0)
		{
			select(null);
			SaveResult result = unknown(selectedId);
			fireActionError(result.getErrors().get(0));
			return result;
		}
		UUID target = selectedId;
		List<NotificationRule> rules = document.getRules();
		UUID nextSelectedId = null;
		if (rules.size() > 1)
		{
			int nextIndex = (index == rules.size() - 1) ? index - 1 : index + 1;
			nextSelectedId = rules.get(nextIndex).getId();
		}
		selectedId = nextSelectedId;
		SaveResult result = delete(target);
		if (result.isSuccess())
		{
			fireSelectionChanged(selectedId);
		}
		else
		{
			selectedId = target;
			fireActionError(result.getErrors().get(0));
		}
		return result;
	}

	public boolean canMoveUp()
	{
		requireEdt();
		return selectedId != null && indexOf(selectedId) > 0;
	}

	public boolean canMoveDown()
	{
		requireEdt();
		return selectedId != null && indexOf(selectedId) >= 0 && indexOf(selectedId) < getSize() - 1;
	}

	public boolean canDelete()
	{
		requireEdt();
		return selectedId != null && contains(selectedId);
	}

	public boolean canEdit()
	{
		requireEdt();
		return selectedId != null && contains(selectedId);
	}

	public boolean canAdd()
	{
		requireEdt();
		return !hasBlockingError() && getSize() < RuleSet.MAX_RULES;
	}

	/** The enabled rules that already match a message, topmost first. */
	public List<NotificationRule> matchingRules(String message)
	{
		requireEdt();
		if (message == null)
		{
			return List.of();
		}
		return ruleSet.matching(message);
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
	 * <p>The pattern is the bare message, so the rule starts as narrow as the notification the user
	 * right-clicked and they widen it themselves -- guessing which part they meant is worse than an
	 * edit they were going to make anyway. Not quite an exact match: a message containing a literal
	 * {@code *} yields a wildcard, because this matcher has no escape syntax. It still matches the
	 * message it came from, and anything else that lines up. A logged message can run to
	 * {@link com.notificationpanel.layout.NotificationText#MAX_CODE_POINTS}, four times what a
	 * pattern allows, so an over-long one keeps as much of its start as leaves room for a trailing
	 * {@code *}: that draft is broader than the message but still matches it, which a bare
	 * truncation would not. The name is truncated separately to the shorter cap that field
	 * enforces. A null, empty or blank message has nothing to prefill, so it falls back to a
	 * plain {@link #newDraft()} rather than producing a blank pattern and name.</p>
	 */
	public NotificationRule newDraftFor(String message)
	{
		requireEdt();
		if (message == null || message.trim().isEmpty())
		{
			return newDraft();
		}
		String pattern = patternFor(message);
		String flattened = LINE_BREAKS.matcher(message.trim()).replaceAll(" ");
		String name = truncateToCodePoints(flattened, NotificationRule.MAX_NAME_CODE_POINTS);
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
		int newIndex = document.getRules().size();
		List<NotificationRule> rules = new ArrayList<>(document.getRules());
		rules.add(draft);
		SaveResult result = save(rules);
		if (result.isSuccess())
		{
			fireIntervalAddedSafely(newIndex, newIndex);
		}
		return result;
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
		SaveResult result = save(rules);
		if (result.isSuccess())
		{
			fireContentsChangedSafely(index, index);
		}
		return result;
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
		SaveResult result = save(rules);
		if (result.isSuccess())
		{
			fireContentsChangedSafely(index, index);
		}
		return result;
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
		SaveResult result = save(rules);
		if (result.isSuccess())
		{
			UUID preservedSelection = Objects.equals(selectedId, id) ? null : selectedId;
			selectedId = preservedSelection;
			if (preservedSelection == null)
			{
				fireSelectionChanged(null);
			}
			fireIntervalRemovedSafely(index, index);
			if (preservedSelection != null)
			{
				fireSelectionChanged(preservedSelection);
			}
		}
		return result;
	}

	public SaveResult reset()
	{
		requireEdt();
		int prevSize = document.getRules().size();
		try
		{
			store.resetStructuredRules();
			RuleConfigStore.LoadResult result = store.load();
			applyLoadResult(result);
			if (result.hasBlockingError())
			{
				if (prevSize > 0)
				{
					clearSelection();
					fireContentsChangedSafely(0, prevSize - 1);
				}
				return SaveResult.failure(result.getBlockingError());
			}
			if (!document.getRules().isEmpty() || !document.getMigrationWarnings().isEmpty())
			{
				blockingError = "Reset did not produce an empty rule document.";
				setDocument(emptyDocument());
				wasMigrated = false;
				if (prevSize > 0)
				{
					clearSelection();
					fireContentsChangedSafely(0, prevSize - 1);
				}
				return SaveResult.failure(blockingError);
			}
			// Belt and braces: reset writes a valid empty document, so the reload above reports
			// no migration. Clearing rules is not a user-facing import and must never re-open
			// the editor's one-time migration gate.
			wasMigrated = false;
			if (prevSize > 0)
			{
				clearSelection();
				fireContentsChangedSafely(0, prevSize - 1);
			}
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
		int prevSize = document.getRules().size();
		applyLoadResult(store.load());
		if (selectedId != null && !contains(selectedId))
		{
			clearSelection();
		}
		int maxIndex = Math.max(prevSize, document.getRules().size()) - 1;
		if (maxIndex >= 0)
		{
			fireContentsChangedSafely(0, maxIndex);
		}
	}

	public List<String> validateForEditor(NotificationRule draft)
	{
		requireEdt();
		return validateDraft(draft);
	}

	private void fireModeChanged(ViewMode mode, NotificationRule draft)
	{
		for (Listener listener : new ArrayList<>(listeners))
		{
			listener.onModeChanged(mode, draft);
		}
	}

	private void fireSelectionChanged(UUID selectedId)
	{
		for (Listener listener : new ArrayList<>(listeners))
		{
			listener.onSelectionChanged(selectedId);
		}
	}

	private void fireActionError(String error)
	{
		for (Listener listener : new ArrayList<>(listeners))
		{
			listener.onActionError(error);
		}
	}

	private void fireIntervalAddedSafely(int index0, int index1)
	{
		suppressSelectionUpdates = true;
		try
		{
			fireIntervalAdded(this, index0, index1);
		}
		finally
		{
			suppressSelectionUpdates = false;
		}
	}

	private void fireIntervalRemovedSafely(int index0, int index1)
	{
		suppressSelectionUpdates = true;
		try
		{
			fireIntervalRemoved(this, index0, index1);
		}
		finally
		{
			suppressSelectionUpdates = false;
		}
	}

	private void fireContentsChangedSafely(int index0, int index1)
	{
		suppressSelectionUpdates = true;
		try
		{
			fireContentsChanged(this, index0, index1);
		}
		finally
		{
			suppressSelectionUpdates = false;
		}
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
		SaveResult result = save(rules);
		if (result.isSuccess())
		{
			fireContentsChangedSafely(Math.min(from, to), Math.max(from, to));
		}
		return result;
	}

	private SaveResult save(List<NotificationRule> rules)
	{
		if (blockingError != null)
		{
			return SaveResult.failure(blockingError);
		}
		RuleDocument candidate = new RuleDocument(document.getSchemaVersion(),
			document.getMigrationWarnings(), rules);
		RuleSet.CompileResult compiled;
		try
		{
			// The compile that validates is also the one that is kept: every rule this accepts is
			// a rule the menu will ask about later, and compiling it twice would be compiling the
			// same list for two answers.
			compiled = RuleSet.compile(candidate.getRules());
		}
		catch (IllegalArgumentException exception)
		{
			return SaveResult.failure(exceptionMessage(exception));
		}
		if (!compiled.getErrors().isEmpty())
		{
			return SaveResult.failure(List.copyOf(compiled.getErrors().values()));
		}
		try
		{
			store.save(candidate);
		}
		catch (RuntimeException exception)
		{
			return SaveResult.failure(exceptionMessage(exception));
		}
		setDocument(candidate, compiled.getRuleSet());
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

	private static String patternFor(String message)
	{
		if (message.codePointCount(0, message.length())
			<= NotificationRule.MAX_PATTERN_CODE_POINTS)
		{
			return message;
		}
		return truncateToCodePoints(message, NotificationRule.MAX_PATTERN_CODE_POINTS - 1) + "*";
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
		setDocument(Objects.requireNonNull(result.getDocument(), "loadResult.document"));
		wasMigrated = result.wasMigrated();
		blockingError = result.hasBlockingError()
			? Objects.requireNonNull(result.getBlockingError(), "loadResult.blockingError") : null;
	}

	/**
	 * The only way the rules held here change: a document and the compiled form of it, together.
	 *
	 * <p>Compiling belongs to this moment rather than to whoever asks a question about the rules --
	 * a document that has not changed compiles to the same set every time, and the context menu
	 * that asks which rules already match a message should not be paying for it.</p>
	 */
	private void setDocument(RuleDocument next)
	{
		setDocument(next, compile(next));
	}

	/** For a caller that has already compiled what it is storing, so it is not compiled twice. */
	private void setDocument(RuleDocument next, RuleSet compiled)
	{
		document = next;
		ruleSet = compiled;
	}

	private static RuleSet compile(RuleDocument document)
	{
		try
		{
			return RuleSet.compile(document.getRules()).getRuleSet();
		}
		catch (IllegalArgumentException exception)
		{
			// A stored document the codec read back but the compiler refuses whole -- too many
			// rules, or two sharing an id. The editor still lists it so the user can repair it,
			// and until they do nothing matches, which is what an unusable rule set means.
			return RuleSet.empty();
		}
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
		Edt.require(EDT_SUBJECT);
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
