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

import com.notificationpanel.layout.NotificationText;
import com.notificationpanel.rules.LegacyRuleMigrator;
import com.notificationpanel.rules.NotificationRule;
import com.notificationpanel.rules.RuleSet;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public final class RuleEditorPanel extends PluginPanel
{
	private static final long serialVersionUID = 1L;
	private static final String EDT_ERROR = "Rule editor mutations must run on the EDT.";

	private final RuleEditorController controller;
	private final Actions actions;
	private final BufferedImage navigationIcon;
	private RuleListView listView;
	private RuleEditView editView;
	private JScrollPane editorScrollPane;
	private UUID editingId;
	// Whether the one-time migration gate still needs to be shown. Seeded once from the
	// controller at construction and cleared only by acknowledging the gate, so a later
	// controller.reload() (which reports wasMigrated=false) cannot dismiss an unseen import.
	private boolean migrationPending;
	private JPanel migrationGate;
	private JButton migrationContinueButton;
	private JTextArea migrationGateText;

	/** What the sidebar needs from the plugin, which owns the config and the client thread. */
	public interface Actions
	{
		void clearNotifications();
	}

	public RuleEditorPanel(RuleEditorController controller, Actions actions)
	{
		requireEdt();
		this.controller = Objects.requireNonNull(controller, "controller");
		this.actions = Objects.requireNonNull(actions, "actions");
		this.migrationPending = controller.wasMigrated();
		navigationIcon = createNavigationIcon();
		setLayout(new BorderLayout());
		renderList();
	}

	public BufferedImage getNavigationIcon()
	{
		requireEdt();
		return navigationIcon;
	}

	public void showNewRule()
	{
		requireEdt();
		if (controller.hasBlockingError() || controller.getRules().size() >= RuleSet.MAX_RULES)
		{
			return;
		}
		editingId = null;
		renderEditor(controller.newDraft());
	}

	public void reload()
	{
		reload(false);
	}

	/**
	 * Whether an imported batch of rules still has to be acknowledged.
	 *
	 * <p>The plugin asks before dropping this panel, because the gate is the only thing that says
	 * why a batch of rules arrived switched off and {@code rulesV1} is written before it is shown:
	 * no later load reports the migration again, so an unseen one discarded here is lost for
	 * good.</p>
	 */
	public boolean hasPendingMigration()
	{
		requireEdt();
		return migrationPending;
	}

	/**
	 * Reloads the stored rules.
	 *
	 * @param migratedElsewhere whether the caller's own load performed a legacy migration. The
	 *                          plugin and this panel both load the store, and only whichever runs
	 *                          first sees the migration, so the winner passes it in here.
	 */
	public void reload(boolean migratedElsewhere)
	{
		requireEdt();
		NotificationRule selected = selectedRule();
		controller.reload();
		if (migratedElsewhere || controller.wasMigrated())
		{
			// Migration is not confined to startup: config synced on login, a profile switch, or
			// an imported profile can all hand legacy lists to an install that had none. Raise
			// the gate whenever one happens, but never lower it here -- an unacknowledged import
			// has to survive the reloads that ordinary config edits trigger.
			migrationPending = true;
		}
		if (editView != null)
		{
			// Any change in the plugin's config group reaches this method, including ordinary
			// settings edited on RuneLite's own config page. Rebuilding the list here would
			// silently discard whatever the user is part-way through typing, so leave the open
			// form alone and revalidate the draft in place. A gate raised above still waits in
			// migrationPending and appears once the user leaves the form.
			validateEditor();
			return;
		}
		renderList(selected == null ? null : selected.getId());
	}

	private void renderList()
	{
		renderList(null);
	}

	private void renderList(UUID selectedId)
	{
		if (migrationPending)
		{
			renderMigrationGate();
			return;
		}
		removeAll();
		editingId = null;
		editView = null;
		editorScrollPane = null;
		migrationGate = null;
		migrationGateText = null;
		migrationContinueButton = null;
		listView = new RuleListView(this, controller);
		add(listView, BorderLayout.CENTER);
		if (selectedId != null)
		{
			listView.select(selectedId);
		}
		revalidate();
		repaint();
	}

	// A one-time confirmation shown after a migration, before the rule list, so the user notices
	// that their old configuration was imported and that some rules may need review.
	private void renderMigrationGate()
	{
		removeAll();
		listView = null;
		editView = null;
		editorScrollPane = null;
		migrationGate = new JPanel(new BorderLayout(0, 8));
		migrationGate.setBackground(ColorScheme.DARK_GRAY_COLOR);
		migrationGate.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JLabel heading = new JLabel("Rules imported");
		heading.setForeground(ColorScheme.BRAND_ORANGE);
		migrationGate.add(heading, BorderLayout.NORTH);

		migrationGateText = new JTextArea(migrationSummary(controller));
		migrationGateText.setEditable(false);
		migrationGateText.setFocusable(false);
		migrationGateText.setLineWrap(true);
		migrationGateText.setWrapStyleWord(true);
		migrationGateText.setOpaque(false);
		migrationGateText.setForeground(ColorScheme.TEXT_COLOR);
		migrationGate.add(migrationGateText, BorderLayout.CENTER);

		migrationContinueButton = new JButton("Continue to rules");
		migrationContinueButton.addActionListener(event ->
		{
			migrationPending = false;
			renderList(null);
		});
		JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		south.setOpaque(false);
		south.add(migrationContinueButton);
		migrationGate.add(south, BorderLayout.SOUTH);

		add(migrationGate, BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	/**
	 * Explains what the one-time import did. This is the only place a user is told that matching
	 * semantics changed in 2.0, so it names the conversion, the behavioral change that can
	 * surprise them, and how many rules they need to look at.
	 */
	private static String migrationSummary(RuleEditorController controller)
	{
		int imported = controller.getRules().size();
		int needRewrite = 0;
		int needChecking = 0;
		for (NotificationRule rule : controller.getRules())
		{
			String note = rule.getMigrationNote();
			if (note == null)
			{
				continue;
			}
			// Both are turned off, but one needs the pattern rewritten and the other only needs
			// the user to agree that a broader match is acceptable.
			if (note.startsWith(LegacyRuleMigrator.WIDENED_NOTE_PREFIX))
			{
				needChecking++;
			}
			else
			{
				needRewrite++;
			}
		}

		StringBuilder summary = new StringBuilder();
		if (imported == 0)
		{
			summary.append("No rules could be imported from your old Regex/Options lists.");
		}
		else
		{
			summary.append("Your old Regex/Options lists became ")
				.append(imported == 1 ? "1 rule" : imported + " rules")
				.append(", in the same order.\n\n")
				.append("Patterns are now wildcards, not regular expressions: * matches any run "
					+ "of characters and matching ignores case. A pattern still has to describe "
					+ "the whole message, so * is how you match part of one -- *dragon* rather "
					+ "than dragon.\n\n");
			if (needRewrite > 0)
			{
				// This bucket is everything that is not a widening, which is more than unconvertible
				// patterns: a dropped per-rule option, an unreadable colour, a missing pattern. The
				// per-rule lines below say which, so the summary must not guess.
				summary.append(needRewrite == 1
					? "1 rule could not be imported unchanged. It is turned off, and the line "
						+ "under it says what to fix."
					: needRewrite + " rules could not be imported unchanged. They are turned off, "
						+ "and the line under each one says what to fix.")
					.append("\n\n");
			}
			if (needChecking > 0)
			{
				summary.append(needChecking == 1
					? "1 rule converted, but would now match more messages than it used to, so "
						+ "it is turned off. Check it below and turn it on if that is what you "
						+ "want."
					: needChecking + " rules converted, but would now match more messages than "
						+ "they used to, so they are turned off. Check them below and turn them "
						+ "on if that is what you want.")
					.append("\n\n");
			}
			if (needRewrite == 0 && needChecking == 0)
			{
				summary.append("Everything converted cleanly.\n\n");
			}
			summary.setLength(summary.length() - 2);
		}

		for (String warning : controller.getDocument().getMigrationWarnings())
		{
			summary.append("\n\n").append(warning);
		}
		summary.append("\n\nYour original lists are kept, so nothing was lost.");
		return summary.toString();
	}


	private void renderEditor(NotificationRule draft)
	{
		removeAll();
		listView = null;
		migrationGate = null;
		migrationGateText = null;
		migrationContinueButton = null;
		editView = new RuleEditView(this, draft);
		editorScrollPane = new JScrollPane(editView);
		editorScrollPane.setHorizontalScrollBarPolicy(
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		add(editorScrollPane, BorderLayout.CENTER);
		validateEditor();
		revalidate();
		repaint();
	}

	private void showSelectedRule()
	{
		NotificationRule selected = selectedRule();
		if (selected == null)
		{
			return;
		}
		editingId = selected.getId();
		renderEditor(selected);
	}

	private void saveDraft()
	{
		RuleEditView editor = requireEditor();
		NotificationRule draft = editor.buildDraft();
		UUID savedId = editingId == null ? draft.getId() : editingId;
		RuleEditorController.SaveResult result = editingId == null
			? controller.add(draft) : controller.edit(editingId, draft);
		if (result.isSuccess())
		{
			renderList(savedId);
		}
		else
		{
			editor.showErrors(result.getErrors());
		}
	}

	private void validateEditor()
	{
		if (editView == null)
		{
			return;
		}
		editView.showErrors(controller.validateForEditor(editView.buildDraft()));
	}

	private void toggleSelected()
	{
		NotificationRule selected = selectedRule();
		if (selected != null)
		{
			UUID id = selected.getId();
			afterMutation(controller.setEnabled(id, !selected.isEnabled()), id);
		}
	}

	private void moveSelectedUp()
	{
		NotificationRule selected = selectedRule();
		if (selected != null)
		{
			UUID id = selected.getId();
			afterMutation(controller.moveUp(id), id);
		}
	}

	private void moveSelectedDown()
	{
		NotificationRule selected = selectedRule();
		if (selected != null)
		{
			UUID id = selected.getId();
			afterMutation(controller.moveDown(id), id);
		}
	}

	private void confirmDelete()
	{
		NotificationRule rule = selectedRule();
		if (rule == null)
		{
			return;
		}
		UUID confirmedId = rule.getId();
		int answer = JOptionPane.showConfirmDialog(
			this,
			"Delete rule \"" + rule.getName() + "\"?",
			"Delete notification rule",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.WARNING_MESSAGE);
		handleDeleteAnswer(answer, confirmedId);
	}

	private void handleDeleteAnswer(int answer, UUID confirmedId)
	{
		if (answer != JOptionPane.OK_OPTION)
		{
			return;
		}
		Objects.requireNonNull(confirmedId, "confirmedId");
		int deletedIndex = indexOfRule(confirmedId);
		RuleEditorController.SaveResult result = controller.delete(confirmedId);
		if (!result.isSuccess())
		{
			requireList().showActionErrors(result.getErrors());
			return;
		}
		List<NotificationRule> rules = controller.getRules();
		UUID selectedId = rules.isEmpty() ? null
			: rules.get(Math.min(Math.max(0, deletedIndex), rules.size() - 1)).getId();
		renderList(selectedId);
	}

	private void confirmReset()
	{
		// Deleting one rule asks first, and this discards every one of them. The stored value is
		// usually already unreadable, but not always: an unsupported schema version means intact
		// rules written by a newer release, which a downgrade would otherwise destroy silently.
		int answer = JOptionPane.showConfirmDialog(
			this,
			"Discard the stored notification rules and start from an empty list?\n"
				+ "Whatever is stored now cannot be recovered. Your pre-2.0 Regex and Options "
				+ "lists are kept either way.",
			"Reset notification rules",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.WARNING_MESSAGE);
		handleResetAnswer(answer);
	}

	private void handleResetAnswer(int answer)
	{
		if (answer != JOptionPane.OK_OPTION)
		{
			return;
		}
		RuleEditorController.SaveResult result = controller.reset();
		renderList();
		if (!result.isSuccess())
		{
			requireList().showActionErrors(result.getErrors());
		}
	}

	private void afterMutation(RuleEditorController.SaveResult result, UUID selectedId)
	{
		if (result.isSuccess())
		{
			renderList(selectedId);
		}
		else
		{
			requireList().showActionErrors(result.getErrors());
		}
	}

	private int indexOfRule(UUID id)
	{
		List<NotificationRule> rules = controller.getRules();
		for (int index = 0; index < rules.size(); index++)
		{
			if (rules.get(index).getId().equals(id))
			{
				return index;
			}
		}
		return -1;
	}

	private NotificationRule selectedRule()
	{
		return listView == null ? null : listView.ruleList.getSelectedValue();
	}

	private RuleListView requireList()
	{
		if (listView == null)
		{
			throw new IllegalStateException("The rule list is not visible.");
		}
		return listView;
	}

	private RuleEditView requireEditor()
	{
		if (editView == null)
		{
			throw new IllegalStateException("The rule editor is not visible.");
		}
		return editView;
	}

	private static BufferedImage createNavigationIcon()
	{
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = icon.createGraphics();
		try
		{
			graphics.setColor(Color.WHITE);
			for (int y : new int[]{4, 8, 12})
			{
				graphics.fillRect(2, y, 12, 1);
				graphics.fillRect(2, y - 1, 2, 3);
			}
		}
		finally
		{
			graphics.dispose();
		}
		return icon;
	}

	/**
	 * Sets text and sizes the area to hold it.
	 *
	 * <p>A wrapping text area reports its height from its width, which BoxLayout does not know
	 * when it asks. Left alone it either stretches over all the spare height or clips the text,
	 * depending on what the maximum size says, so the row count is computed here instead.</p>
	 */
	private static void setWrappedText(JTextArea area, String text)
	{
		area.setText(text);
		int width = area.getWidth() > 0 ? area.getWidth() : PluginPanel.PANEL_WIDTH - 16;
		int textWidth = area.getFontMetrics(area.getFont()).stringWidth(text);
		// Word wrapping never fits more than this per line, and usually a little less, so round
		// up and allow one more line once the text spills past a single one.
		int rows = (int) Math.ceil((double) textWidth / Math.max(1, width));
		area.setRows(Math.max(1, rows > 1 ? rows + 1 : rows));
	}

	private static JTextArea errorArea()
	{
		JTextArea area = new JTextArea()
		{
			private static final long serialVersionUID = 1L;

			@Override
			public Dimension getMaximumSize()
			{
				// A text area reports an unbounded maximum, so BoxLayout stretches it over all
				// the leftover vertical space instead of leaving it the height of its text.
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		area.setEditable(false);
		area.setFocusable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setOpaque(false);
		area.setRows(2);
		area.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
		area.setBorder(null);
		return area;
	}

	private static boolean isSafeErrorArea(JTextArea area)
	{
		return !area.isEditable() && area.getLineWrap() && area.getWrapStyleWord()
			&& !area.isOpaque();
	}

	private static void requireEdt()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			throw new IllegalStateException(EDT_ERROR);
		}
	}

	private static final class RuleListView extends JPanel
	{
		private static final long serialVersionUID = 1L;
		/** Long enough to tell two rules apart in the list, short enough to lay out cheaply. */
		private static final int LIST_PREVIEW_LIMIT = 48;
		/**
		 * The editor accepts 512 code points and stored config can hold far more, so the tooltip
		 * bounds what it lays out rather than trusting either. Wide enough to read a realistic
		 * pattern whole, since the tooltip wraps.
		 */
		private static final int TOOLTIP_PREVIEW_LIMIT = 200;
		/**
		 * How wide the tooltip is allowed to get before it wraps. Comfortably wider than the
		 * 225px sidebar, since a tooltip floats free of it, but far short of a screen edge.
		 */
		private static final int TOOLTIP_WRAP_WIDTH = 320;

		private final RuleEditorPanel owner;
		private final DefaultListModel<NotificationRule> model = new DefaultListModel<>();
		private final PatternList ruleList = new PatternList(model);
		private final JScrollPane listScrollPane = new JScrollPane(ruleList);
		private final JButton addButton = new JButton("Add");
		private final JButton editButton = new JButton("Edit");
		private final JButton toggleButton = new JButton("Enable");
		private final JButton upButton = new JButton("Move Up");
		private final JButton downButton = new JButton("Move Down");
		private final JButton deleteButton = new JButton("Delete");
		private final JTextArea blockingBanner = errorArea();
		private final JButton resetButton = new JButton("Reset rules");
		private final JTextArea actionError = errorArea();
		private final JTextArea emptyState = errorArea();
		private final JButton clearButton = new JButton("Clear notifications");

		private RuleListView(RuleEditorPanel owner, RuleEditorController controller)
		{
			this.owner = owner;
			setLayout(new BorderLayout(0, 6));
			setBackground(ColorScheme.DARK_GRAY_COLOR);

			JPanel heading = new JPanel();
			heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
			heading.setOpaque(false);
			JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			titleRow.setOpaque(false);
			titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
			JLabel title = new JLabel("Notification Panel Rules");
			title.setForeground(ColorScheme.TEXT_COLOR);
			titleRow.add(title);
			JLabel help = new JLabel("(?)");
			help.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			help.setToolTipText("<html>Rules format the notifications shown by the plugin."
				+ "<br>Each rule matches messages by a wildcard pattern and overrides the"
				+ " background color, the opacity, or whether the message is shown at all."
				+ "<br><b>*</b> stands for any run of characters. A pattern must match the entire"
				+ " message, so to match a word anywhere in one, put <b>*</b> on both sides of it:"
				+ " <b>*dragon*</b>"
				+ "<br>Matching ignores case."
				+ "<br>When a notification matches several rules, each setting comes from the"
				+ " first enabled matching rule that specifies it.</html>");
			titleRow.add(help);
			heading.add(titleRow);
			blockingBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
			blockingBanner.setText(controller.hasBlockingError()
				? controller.getBlockingError() : "");
			blockingBanner.setVisible(controller.hasBlockingError());
			heading.add(blockingBanner);
			resetButton.setAlignmentX(Component.LEFT_ALIGNMENT);
			resetButton.setVisible(controller.hasBlockingError());
			resetButton.addActionListener(event -> owner.confirmReset());
			heading.add(resetButton);
			actionError.setAlignmentX(Component.LEFT_ALIGNMENT);
			actionError.setVisible(false);
			heading.add(actionError);

			for (NotificationRule rule : controller.getRules())
			{
				model.addElement(rule);
			}

			// Without this a first run is a blank scroll area over five greyed-out buttons, with
			// nothing saying what a rule is for or that Add is the way in. The list view is rebuilt
			// on every mutation, so deciding visibility here is always current.
			emptyState.setAlignmentX(Component.LEFT_ALIGNMENT);
			emptyState.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			emptyState.setText("No rules yet. Add one to give the notifications it matches their "
				+ "own background or opacity, or to hide them -- everything else uses the default "
				+ "color and opacity from the plugin's settings.");
			emptyState.setVisible(model.isEmpty() && !controller.hasBlockingError());
			heading.add(emptyState);
			add(heading, BorderLayout.NORTH);
			ruleList.setCellRenderer(renderer());
			ruleList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
			ruleList.addListSelectionListener(event ->
			{
				if (!event.getValueIsAdjusting())
				{
					updateButtons(controller.hasBlockingError());
				}
			});
			add(listScrollPane, BorderLayout.CENTER);

			JPanel ruleActions = new JPanel(new GridLayout(3, 2, 4, 4));
			ruleActions.setOpaque(false);
			ruleActions.add(addButton);
			ruleActions.add(editButton);
			ruleActions.add(upButton);
			ruleActions.add(downButton);
			ruleActions.add(toggleButton);
			ruleActions.add(deleteButton);

			// Acts on what is on screen rather than on rules, so it sits apart from the rule
			// buttons.
			JPanel panelActions = new JPanel(new GridLayout(1, 1, 4, 4));
			panelActions.setOpaque(false);
			clearButton.setToolTipText("Remove every notification currently on screen.");
			clearButton.addActionListener(event -> owner.actions.clearNotifications());
			panelActions.add(clearButton);

			JPanel south = new JPanel(new BorderLayout(0, 6));
			south.setOpaque(false);
			south.add(ruleActions, BorderLayout.NORTH);
			south.add(panelActions, BorderLayout.SOUTH);
			add(south, BorderLayout.SOUTH);

			addButton.addActionListener(event -> owner.showNewRule());
			editButton.addActionListener(event -> owner.showSelectedRule());
			toggleButton.addActionListener(event -> owner.toggleSelected());
			upButton.addActionListener(event -> owner.moveSelectedUp());
			downButton.addActionListener(event -> owner.moveSelectedDown());
			deleteButton.addActionListener(event -> owner.confirmDelete());
			addButton.setEnabled(!controller.hasBlockingError() && model.size() < RuleSet.MAX_RULES);
			updateButtons(controller.hasBlockingError());
		}

		private void select(UUID id)
		{
			for (int index = 0; index < model.size(); index++)
			{
				if (model.get(index).getId().equals(id))
				{
					ruleList.setSelectedIndex(index);
					return;
				}
			}
			ruleList.clearSelection();
		}

		private void updateButtons(boolean blocked)
		{
			int index = ruleList.getSelectedIndex();
			boolean selected = !blocked && index >= 0;
			editButton.setEnabled(selected);
			toggleButton.setEnabled(selected);
			upButton.setEnabled(selected && index > 0);
			downButton.setEnabled(selected && index < model.size() - 1);
			deleteButton.setEnabled(selected);
			if (selected)
			{
				toggleButton.setText(model.get(index).isEnabled() ? "Disable" : "Enable");
			}
			else
			{
				toggleButton.setText("Enable");
			}
		}

		private void showActionErrors(List<String> errors)
		{
			setWrappedText(actionError, String.join(" ", errors));
			actionError.setVisible(true);
			revalidate();
		}

		private String visibleText()
		{
			StringBuilder text = new StringBuilder();
			ListCellRenderer<? super NotificationRule> cellRenderer = ruleList.getCellRenderer();
			for (int index = 0; index < model.size(); index++)
			{
				Component component = cellRenderer.getListCellRendererComponent(ruleList,
					model.get(index), index, false, false);
				appendLabelText(component, text);
			}
			return text.toString();
		}

		private static ListCellRenderer<NotificationRule> renderer()
		{
			return (list, rule, index, selected, focused) ->
			{
				JPanel row = new JPanel();
				row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
				row.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
				row.setBackground(selected
					? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
				JLabel name = new JLabel((rule.isEnabled() ? "Enabled: " : "Disabled: ")
					+ safe(rule.getName()));
				name.setForeground(ColorScheme.TEXT_COLOR);
				row.add(name);
				JLabel pattern = new JLabel(
					"Pattern: " + patternPreview(rule.getPattern(), LIST_PREVIEW_LIMIT));
				pattern.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				row.add(pattern);
				JLabel style = new JLabel("Style: " + styleSummary(rule));
				style.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				row.add(style);
				if (rule.getMigrationNote() != null)
				{
					JLabel warning = new JLabel("Warning: " + safe(rule.getMigrationNote()));
					warning.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
					row.add(warning);
				}
				return row;
			};
		}

		/**
		 * The rule list, sized to its viewport rather than to its widest row.
		 *
		 * <p>A JList reports the widest cell as its preferred width and does not track the
		 * viewport, so one long pattern pushed the whole list out from under the panel and raised
		 * a horizontal scrollbar. Tracking the viewport pins every cell to the visible width, and
		 * the row's BoxLayout then hands each label that width -- it shrinks a label below its
		 * minimum size on the cross axis -- so BasicLabelUI clips the text with a trailing "..."
		 * without any per-label sizing.</p>
		 */
		private static final class PatternList extends JList<NotificationRule>
		{
			private static final long serialVersionUID = 1L;

			private PatternList(ListModel<NotificationRule> model)
			{
				super(model);
				// Registers the list with ToolTipManager. The text comes from the override below:
				// a cell renderer is painted rather than added, so it never sees a mouse event and
				// setting a tooltip on the row would do nothing.
				setToolTipText("");
			}

			@Override
			public boolean getScrollableTracksViewportWidth()
			{
				return true;
			}

			@Override
			public String getToolTipText(MouseEvent event)
			{
				return tooltipAt(event.getPoint());
			}

			private String tooltipAt(Point point)
			{
				int index = locationToIndex(point);
				if (index < 0)
				{
					return null;
				}
				// locationToIndex answers with the nearest row for a point past the last one, so
				// the bounds check is what stops a tooltip trailing down the empty list.
				Rectangle cell = getCellBounds(index, index);
				if (cell == null || !cell.contains(point))
				{
					return null;
				}
				NotificationRule rule = getModel().getElementAt(index);
				List<String> lines = new ArrayList<>(wrapForTooltip(
					"Pattern: " + patternPreview(rule.getPattern(), TOOLTIP_PREVIEW_LIMIT)));
				if (rule.getMigrationNote() != null)
				{
					// The note is the only thing that says why an imported rule arrived switched
					// off, the migration gate tells the user to go and read it, and clipping left
					// it unreadable in the row. This is where it stays reachable.
					lines.add(null);
					lines.addAll(wrapForTooltip("Warning: " + safe(rule.getMigrationNote())));
				}
				return tooltipHtml(lines);
			}

			/**
			 * Breaks one paragraph into lines that fit the tooltip.
			 *
			 * <p>Wrapped here rather than by a CSS width on the body, because in Swing's HTML a
			 * width is a fixed width and not a maximum: a short pattern rendered into a 260px body
			 * padded itself out to 260px and left an empty margin down the right. Wrapping the
			 * lines ourselves lets the tooltip size to its own widest line.</p>
			 *
			 * <p>Measured with the tooltip's own font rather than the list's, since that is what
			 * the text will be drawn in.</p>
			 */
			private List<String> wrapForTooltip(String paragraph)
			{
				Font font = UIManager.getFont("ToolTip.font");
				FontMetrics metrics = getFontMetrics(font == null ? getFont() : font);
				return NotificationText.wrap(paragraph, TOOLTIP_WRAP_WIDTH, metrics::stringWidth);
			}

			/**
			 * Joins wrapped lines into a tooltip.
			 *
			 * <p>A null entry is a paragraph break. Escaping happens here, after wrapping, because
			 * escaping changes the text's rendered length and would throw the measurements off.
			 * It is what stops a user's pattern or an imported note contributing markup.</p>
			 */
			private static String tooltipHtml(List<String> lines)
			{
				StringBuilder tooltip = new StringBuilder("<html>");
				for (int index = 0; index < lines.size(); index++)
				{
					if (index > 0)
					{
						tooltip.append("<br>");
					}
					String line = lines.get(index);
					if (line != null)
					{
						tooltip.append(escapeHtml(line));
					}
				}
				return tooltip.append("</html>").toString();
			}

			/**
			 * Escapes the three characters that would otherwise be read as markup.
			 *
			 * <p>The text reaching here has already been through {@link #patternPreview}, which
			 * escapes control characters and bounds the length; this covers what HTML rendering
			 * adds on top of that.</p>
			 */
			private static String escapeHtml(String text)
			{
				StringBuilder escaped = new StringBuilder(text.length());
				for (int index = 0; index < text.length(); index++)
				{
					char character = text.charAt(index);
					switch (character)
					{
						case '&':
							escaped.append("&amp;");
							break;
						case '<':
							escaped.append("&lt;");
							break;
						case '>':
							escaped.append("&gt;");
							break;
						default:
							escaped.append(character);
							break;
					}
				}
				return escaped.toString();
			}
		}

		private static void appendLabelText(Component component, StringBuilder text)
		{
			if (component instanceof JLabel)
			{
				text.append(((JLabel) component).getText()).append('\n');
			}
			if (component instanceof JPanel)
			{
				for (Component child : ((JPanel) component).getComponents())
				{
					appendLabelText(child, text);
				}
			}
		}

		private static String patternPreview(String pattern, int limit)
		{
			String source = safe(pattern);
			StringBuilder escaped = new StringBuilder();
			int sourceIndex = 0;
			int previewCodePoints = 0;
			while (sourceIndex < source.length())
			{
				int codePoint = source.codePointAt(sourceIndex);
				String replacement = escapeCodePoint(codePoint);
				int replacementCodePoints = replacement.codePointCount(0, replacement.length());
				if (previewCodePoints + replacementCodePoints > limit)
				{
					break;
				}
				escaped.append(replacement);
				previewCodePoints += replacementCodePoints;
				sourceIndex += Character.charCount(codePoint);
			}
			if (sourceIndex < source.length())
			{
				escaped.append('…');
			}
			return escaped.toString();
		}

		private static String escapeCodePoint(int codePoint)
		{
			switch (codePoint)
			{
				case '\\':
					return "\\\\";
				case '\r':
					return "\\r";
				case '\n':
					return "\\n";
				case 0x000B:
					return "\\u000B";
				case '\f':
					return "\\f";
				case 0x0085:
					return "\\u0085";
				case 0x2028:
					return "\\u2028";
				case 0x2029:
					return "\\u2029";
				default:
					return new String(Character.toChars(codePoint));
			}
		}

		private static String styleSummary(NotificationRule rule)
		{
			StringBuilder summary = new StringBuilder();
			if (rule.getBackgroundRgb() != null)
			{
				summary.append(String.format("#%06X", rule.getBackgroundRgb()));
			}
			if (rule.getOpacityPercent() != null)
			{
				appendSeparator(summary);
				summary.append(rule.getOpacityPercent()).append('%');
			}
			// Reported here rather than left to the colour and opacity, because a rule that only
			// decides visibility overrides no formatting at all and would otherwise be summarised
			// as "default formatting" -- which reads as a rule that does nothing.
			if (rule.getVisible() != null)
			{
				appendSeparator(summary);
				// "shown", not "always shown": visibility is first-match-wins like the other two
				// attributes, so a Hide rule above this one still wins and the stronger word
				// would be a promise the resolver does not keep.
				summary.append(rule.getVisible() ? "shown" : "hidden");
			}
			return summary.length() == 0 ? "default formatting" : summary.toString();
		}

		private static void appendSeparator(StringBuilder summary)
		{
			if (summary.length() > 0)
			{
				summary.append(", ");
			}
		}

		private static String safe(String value)
		{
			return value == null ? "" : value;
		}
	}

	/**
	 * The add/edit form.
	 *
	 * <p>Implements {@link Scrollable} purely to pin itself to the viewport width. A BoxLayout
	 * panel reports the widest child as its preferred width, and a text component sized by its
	 * content has no width of its own to report -- so one long pattern made the whole form wider
	 * than the sidebar and pushed the buttons off the edge. Tracking the viewport means no control
	 * in here can do that, whatever it contains.</p>
	 */
	private static final class RuleEditView extends JPanel implements Scrollable
	{
		private static final long serialVersionUID = 1L;
		private static final String SHOW_CHOICE = "Show";
		private static final String HIDE_CHOICE = "Hide";
		private static final int SCROLL_UNIT = 16;

		private final RuleEditorPanel owner;
		private final UUID draftId;
		private final JTextField nameField = new JTextField();
		private final JCheckBox enabledCheckBox = new JCheckBox("Enabled");
		private final JTextArea patternField = patternArea();
		private final JTextArea patternHint = errorArea();
		private final JCheckBox backgroundCheckBox = new JCheckBox("Background");
		private final JButton backgroundButton = new JButton("Choose color");
		private final JCheckBox opacityCheckBox = new JCheckBox("Opacity");
		private final JSpinner opacitySpinner =
			new JSpinner(new SpinnerNumberModel(100, 0, 100, 1));
		private final JCheckBox visibilityCheckBox = new JCheckBox("Visibility");
		private final JComboBox<String> visibilityChoice =
			new JComboBox<>(new String[]{SHOW_CHOICE, HIDE_CHOICE});
		private final JTextArea validationArea = errorArea();
		private final JButton saveButton = new JButton("Save");
		private final JButton cancelButton = new JButton("Cancel");
		private Color backgroundColor = Color.BLACK;

		private RuleEditView(RuleEditorPanel owner, NotificationRule draft)
		{
			this.owner = owner;
			draftId = draft.getId();
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setBackground(ColorScheme.DARK_GRAY_COLOR);

			nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
			patternField.setAlignmentX(Component.LEFT_ALIGNMENT);
			// A text area binds Enter to insert-break in its own input map, and a component's own
			// binding beats the form's ancestor one, so Enter would stop saving from this box
			// alone. Mapping it to an action name nothing provides lets the key fall through.
			patternField.getInputMap(WHEN_FOCUSED)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "none");
			enabledCheckBox.setOpaque(false);
			enabledCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);

			add(label("Name"));
			add(nameField);
			add(enabledCheckBox);
			add(label("Pattern"));
			add(patternField);
			patternHint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			patternHint.setText("The pattern must match the entire message, ignoring case. "
				+ "* stands for any run of characters. To match a word anywhere in a message, put "
				+ "* on both sides of it: *dragon*");
			patternHint.setAlignmentX(Component.LEFT_ALIGNMENT);
			add(patternHint);

			JPanel backgroundRow = row();
			backgroundRow.add(backgroundCheckBox);
			backgroundRow.add(backgroundButton);
			add(backgroundRow);
			JPanel opacityRow = row();
			opacityRow.add(opacityCheckBox);
			opacityRow.add(opacitySpinner);
			add(opacityRow);
			JPanel visibilityRow = row();
			visibilityRow.add(visibilityCheckBox);
			visibilityRow.add(visibilityChoice);
			add(visibilityRow);
			validationArea.setAlignmentX(Component.LEFT_ALIGNMENT);
			add(validationArea);
			JPanel actions = row();
			actions.add(saveButton);
			actions.add(cancelButton);
			add(actions);

			nameField.setText(safe(draft.getName()));
			enabledCheckBox.setSelected(draft.isEnabled());
			patternField.setText(safe(draft.getPattern()));
			backgroundCheckBox.setSelected(draft.getBackgroundRgb() != null);
			if (draft.getBackgroundRgb() != null)
			{
				backgroundColor = new Color(draft.getBackgroundRgb());
			}
			opacityCheckBox.setSelected(draft.getOpacityPercent() != null);
			opacitySpinner.setValue(draft.getOpacityPercent() == null
				? 100 : draft.getOpacityPercent());
			visibilityCheckBox.setSelected(draft.getVisible() != null);
			visibilityChoice.setSelectedItem(selectionFor(draft.getVisible()));
			updateOptionalControls();

			DocumentListener documentListener = new DocumentListener()
			{
				@Override
				public void insertUpdate(DocumentEvent event)
				{
					owner.validateEditor();
				}

				@Override
				public void removeUpdate(DocumentEvent event)
				{
					owner.validateEditor();
				}

				@Override
				public void changedUpdate(DocumentEvent event)
				{
					owner.validateEditor();
				}
			};
			nameField.getDocument().addDocumentListener(documentListener);
			patternField.getDocument().addDocumentListener(documentListener);
			enabledCheckBox.addChangeListener(event -> owner.validateEditor());
			backgroundCheckBox.addChangeListener(event ->
			{
				updateOptionalControls();
				owner.validateEditor();
			});
			opacityCheckBox.addChangeListener(event ->
			{
				updateOptionalControls();
				owner.validateEditor();
			});
			opacitySpinner.addChangeListener(event -> owner.validateEditor());
			visibilityCheckBox.addChangeListener(event ->
			{
				updateOptionalControls();
				owner.validateEditor();
			});
			visibilityChoice.addActionListener(event -> owner.validateEditor());
			backgroundButton.addActionListener(event -> chooseBackground());
			saveButton.addActionListener(event -> owner.saveDraft());
			cancelButton.addActionListener(event -> owner.renderList(owner.editingId));

			// Scoped to this view rather than taken as the root pane's default button: the sidebar
			// shares a root pane with the rest of the client, so claiming Enter there would fire
			// Save from anywhere in the window.
			bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "saveDraft", () ->
			{
				if (saveButton.isEnabled())
				{
					owner.saveDraft();
				}
			});
			bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelDraft",
				() -> owner.renderList(owner.editingId));
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction)
		{
			return SCROLL_UNIT;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction)
		{
			return visible.height;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			// The form is taller than the sidebar once the hint and errors are showing, and the
			// scroll pane has to be free to scroll it.
			return false;
		}

		/**
		 * The pattern input: an editable text area that wraps rather than a single-line field.
		 *
		 * <p>Patterns run long -- up to 512 code points -- and a field shows one window onto them,
		 * so the only way to read one back was to scrub through it. Wrapping at any character
		 * rather than at word boundaries, because a pattern is usually one unbroken run with no
		 * spaces to break at.</p>
		 */
		private static JTextArea patternArea()
		{
			JTextArea area = new JTextArea()
			{
				private static final long serialVersionUID = 1L;

				@Override
				public Dimension getMaximumSize()
				{
					// Same trap errorArea() documents: a text area reports an unbounded maximum,
					// so BoxLayout hands it every spare pixel of height instead of the height of
					// its own text.
					return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
				}
			};
			area.setLineWrap(true);
			area.setWrapStyleWord(false);
			area.setRows(1);
			// Dressed as the text field it replaces, so the form still reads as a form: a text
			// area is transparent and borderless by default, which would leave the pattern
			// looking like a label.
			area.setBorder(new JTextField().getBorder());
			area.setFont(new JTextField().getFont());
			// Newlines can never be part of a pattern the editor produces: Enter is bound to Save
			// below, and this stops a multi-line paste smuggling one in, where it would be
			// invisible in the box and only show up escaped in the rule list.
			((AbstractDocument) area.getDocument()).setDocumentFilter(new DocumentFilter()
			{
				@Override
				public void insertString(FilterBypass bypass, int offset, String text,
					AttributeSet attributes) throws BadLocationException
				{
					super.insertString(bypass, offset, flatten(text), attributes);
				}

				@Override
				public void replace(FilterBypass bypass, int offset, int length, String text,
					AttributeSet attributes) throws BadLocationException
				{
					super.replace(bypass, offset, length, flatten(text), attributes);
				}

				private String flatten(String text)
				{
					return text == null ? null : text.replaceAll("[\\r\\n\\u000B\\f\\u0085"
						+ "\\u2028\\u2029]+", " ");
				}
			});
			return area;
		}

		private void bindKey(KeyStroke stroke, String name, Runnable action)
		{
			getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(stroke, name);
			getActionMap().put(name, new AbstractAction()
			{
				private static final long serialVersionUID = 1L;

				@Override
				public void actionPerformed(ActionEvent event)
				{
					action.run();
				}
			});
		}

		private NotificationRule buildDraft()
		{
			Boolean visible = visibilityCheckBox.isSelected()
				? !HIDE_CHOICE.equals(visibilityChoice.getSelectedItem()) : null;
			return new NotificationRule(draftId, nameField.getText(), enabledCheckBox.isSelected(),
				patternField.getText(),
				backgroundCheckBox.isSelected() ? backgroundColor.getRGB() & 0xFFFFFF : null,
				opacityCheckBox.isSelected() ? (Integer) opacitySpinner.getValue() : null, visible,
				null);
		}

		/** Which entry stands for a rule's stored visibility, including the one it left cleared. */
		private static String selectionFor(Boolean visible)
		{
			return Boolean.FALSE.equals(visible) ? HIDE_CHOICE : SHOW_CHOICE;
		}

		private void setDraft(String name, String pattern, boolean enabled, Integer backgroundRgb,
			Integer opacityPercent, Boolean visible)
		{
			nameField.setText(safe(name));
			patternField.setText(safe(pattern));
			enabledCheckBox.setSelected(enabled);
			backgroundCheckBox.setSelected(backgroundRgb != null);
			if (backgroundRgb != null)
			{
				backgroundColor = new Color(backgroundRgb);
			}
			opacityCheckBox.setSelected(opacityPercent != null);
			if (opacityPercent != null)
			{
				opacitySpinner.setValue(opacityPercent);
			}
			visibilityCheckBox.setSelected(visible != null);
			visibilityChoice.setSelectedItem(selectionFor(visible));
			updateOptionalControls();
			owner.validateEditor();
		}

		private void showErrors(List<String> errors)
		{
			setWrappedText(validationArea, String.join(" ", errors));
			validationArea.setVisible(!errors.isEmpty());
			saveButton.setEnabled(errors.isEmpty());
			revalidate();
		}

		private void chooseBackground()
		{
			Color chosen = JColorChooser.showDialog(this, "Choose rule background",
				backgroundColor);
			if (chosen != null)
			{
				backgroundColor = chosen;
				backgroundCheckBox.setSelected(true);
				updateBackgroundButton();
				owner.validateEditor();
			}
		}

		private void updateOptionalControls()
		{
			backgroundButton.setEnabled(backgroundCheckBox.isSelected());
			opacitySpinner.setEnabled(opacityCheckBox.isSelected());
			visibilityChoice.setEnabled(visibilityCheckBox.isSelected());
			updateBackgroundButton();
		}

		private void updateBackgroundButton()
		{
			int rgb = backgroundColor.getRGB() & 0xFFFFFF;
			backgroundButton.setText(String.format("#%06X", rgb));
			backgroundButton.setBackground(backgroundColor);
			int luminance = backgroundColor.getRed() * 299
				+ backgroundColor.getGreen() * 587 + backgroundColor.getBlue() * 114;
			backgroundButton.setForeground(luminance >= 128_000 ? Color.BLACK : Color.WHITE);
			backgroundButton.setOpaque(true);
		}

		private static JLabel label(String text)
		{
			JLabel label = new JLabel(text);
			label.setForeground(ColorScheme.TEXT_COLOR);
			label.setAlignmentX(Component.LEFT_ALIGNMENT);
			return label;
		}

		private static JPanel row()
		{
			JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
			row.setOpaque(false);
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
			return row;
		}

		private static String safe(String value)
		{
			return value == null ? "" : value;
		}
	}

	// Test hooks. Grouped at the end rather than ahead of the behaviour they reach into, so
	// reading this class top to bottom is reading what it does. Package-private except where a
	// test in the parent package needs one.
	void setDraftForTest(String name, String pattern, boolean enabled, Integer backgroundRgb,
		Integer opacityPercent, Boolean visible)
	{
		requireEdt();
		requireEditor().setDraft(name, pattern, enabled, backgroundRgb, opacityPercent, visible);
	}

	boolean isSaveEnabledForTest()
	{
		requireEdt();
		return requireEditor().saveButton.isEnabled();
	}

	String getValidationTextForTest()
	{
		requireEdt();
		return requireEditor().validationArea.getText();
	}

	void clickSaveForTest()
	{
		requireEdt();
		requireEditor().saveButton.doClick();
	}

	void clickCancelForTest()
	{
		requireEdt();
		requireEditor().cancelButton.doClick();
	}

	boolean isShowingListForTest()
	{
		requireEdt();
		return listView != null && editView == null;
	}

	void selectRuleForTest(UUID id)
	{
		requireEdt();
		requireList().select(id);
	}

	UUID getSelectedRuleIdForTest()
	{
		requireEdt();
		NotificationRule selected = selectedRule();
		return selected == null ? null : selected.getId();
	}

	void clickToggleForTest()
	{
		requireEdt();
		requireList().toggleButton.doClick();
	}

	void clickUpForTest()
	{
		requireEdt();
		requireList().upButton.doClick();
	}

	void clickDownForTest()
	{
		requireEdt();
		requireList().downButton.doClick();
	}

	void showSelectedRuleForTest()
	{
		requireEdt();
		showSelectedRule();
	}

	void handleDeleteAnswerForTest(int answer, UUID confirmedId)
	{
		requireEdt();
		handleDeleteAnswer(answer, confirmedId);
	}

	String getListTextForTest()
	{
		requireEdt();
		return requireList().visibleText();
	}

	/**
	 * The width of a rendered rule row once the list has been laid out at the given viewport width.
	 *
	 * <p>Lays the scroll pane out by hand because a panel that was never shown has no size, and a
	 * cell width is only meaningful against one. Returns a measured width for the caller to compare
	 * with another measured width -- never assert a pixel constant, the fonts differ per host.</p>
	 */
	int ruleListCellWidthForTest(int index, int viewportWidth)
	{
		requireEdt();
		RuleListView list = requireList();
		list.listScrollPane.setSize(viewportWidth, 200);
		list.listScrollPane.doLayout();
		list.listScrollPane.getViewport().doLayout();
		list.ruleList.doLayout();
		Rectangle cell = list.ruleList.getCellBounds(index, index);
		if (cell == null)
		{
			throw new IllegalArgumentException("No rendered row at index " + index + ".");
		}
		return cell.width;
	}

	String ruleListTooltipForTest(int x, int y)
	{
		requireEdt();
		RuleListView list = requireList();
		list.listScrollPane.setSize(PluginPanel.PANEL_WIDTH, 200);
		list.listScrollPane.doLayout();
		list.listScrollPane.getViewport().doLayout();
		list.ruleList.doLayout();
		return list.ruleList.tooltipAt(new Point(x, y));
	}

	boolean isEditEnabledForTest()
	{
		requireEdt();
		return requireList().editButton.isEnabled();
	}

	boolean isUpEnabledForTest()
	{
		requireEdt();
		return requireList().upButton.isEnabled();
	}

	boolean isDownEnabledForTest()
	{
		requireEdt();
		return requireList().downButton.isEnabled();
	}

	boolean isAddEnabledForTest()
	{
		requireEdt();
		return requireList().addButton.isEnabled();
	}

	boolean isBlockingBannerVisibleForTest()
	{
		requireEdt();
		return requireList().blockingBanner.isVisible();
	}

	/**
	 * Public, unlike the other test hooks, so {@code NotificationPanelPlugin}'s own tests can
	 * check that a migration discovered on the client thread reaches the sidebar. That handoff
	 * is the seam that dropped the gate when config arrived after startup.
	 */
	public boolean isMigrationGateVisibleForTest()
	{
		requireEdt();
		return migrationGate != null && listView == null && editView == null;
	}

	String getMigrationGateTextForTest()
	{
		requireEdt();
		return migrationGateText == null ? "" : migrationGateText.getText();
	}

	void clickMigrationContinueForTest()
	{
		requireEdt();
		migrationContinueButton.doClick();
	}

	void clickClearNotificationsForTest()
	{
		requireEdt();
		requireList().clearButton.doClick();
	}

	boolean isResetVisibleForTest()
	{
		requireEdt();
		return requireList().resetButton.isVisible();
	}

	/** Stands in for the confirmation dialog the button opens, which a test cannot dismiss. */
	void handleResetAnswerForTest(int answer)
	{
		requireEdt();
		handleResetAnswer(answer);
	}

	String getActionErrorTextForTest()
	{
		requireEdt();
		return requireList().actionError.getText();
	}

	String getEmptyStateTextForTest()
	{
		requireEdt();
		RuleListView list = requireList();
		return list.emptyState.isVisible() ? list.emptyState.getText() : "";
	}

	String getPatternHintTextForTest()
	{
		requireEdt();
		return requireEditor().patternHint.getText();
	}

	boolean areListErrorsWrappingNonEditableForTest()
	{
		requireEdt();
		RuleListView view = requireList();
		return isSafeErrorArea(view.blockingBanner) && isSafeErrorArea(view.actionError);
	}

	boolean isEditorScrollableForTest()
	{
		requireEdt();
		return editView != null && editorScrollPane != null
			&& editorScrollPane.getViewport().getView() == editView;
	}

	boolean isValidationWrappingNonEditableForTest()
	{
		requireEdt();
		return isSafeErrorArea(requireEditor().validationArea);
	}

	String getBackgroundButtonTextForTest()
	{
		requireEdt();
		return requireEditor().backgroundButton.getText();
	}

	Integer getBackgroundButtonRgbForTest()
	{
		requireEdt();
		return requireEditor().backgroundButton.getBackground().getRGB() & 0xFFFFFF;
	}

	/** What the open form would save for visibility, read back through the draft it builds. */
	Boolean getDraftVisibleForTest()
	{
		requireEdt();
		return requireEditor().buildDraft().getVisible();
	}

	String getDraftPatternForTest()
	{
		requireEdt();
		return requireEditor().buildDraft().getPattern();
	}

	/**
	 * The width of the laid-out edit form at the given viewport width.
	 *
	 * <p>Lays the scroll pane out by hand, because a panel that was never shown has no size and a
	 * form width only means anything against one. Returns a measured width for the caller to
	 * compare with another measured width -- never assert a pixel constant, host fonts differ.</p>
	 */
	int editorFormWidthForTest(int viewportWidth)
	{
		requireEdt();
		requireEditor();
		editorScrollPane.setSize(viewportWidth, 400);
		editorScrollPane.doLayout();
		editorScrollPane.getViewport().doLayout();
		editView.doLayout();
		return editView.getWidth();
	}

	boolean isPatternInputWrappingForTest()
	{
		requireEdt();
		RuleEditView editor = requireEditor();
		return editor.patternField.getLineWrap() && editor.patternField.isEditable();
	}

	/**
	 * Whether Enter typed in the pattern box still reaches the form's Save binding.
	 *
	 * <p>A text area binds Enter to insert-break in its own input map, and that beats the form's
	 * ancestor binding. Resolving to an action name the action map does not provide is what lets
	 * the key fall through, so this checks the name resolves and the action does not.</p>
	 */
	boolean patternInputLetsEnterReachTheFormForTest()
	{
		requireEdt();
		JTextArea pattern = requireEditor().patternField;
		Object binding = pattern.getInputMap(JComponent.WHEN_FOCUSED)
			.get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
		return binding != null && pattern.getActionMap().get(binding) == null;
	}
}
