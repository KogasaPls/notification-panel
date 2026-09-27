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
import com.notificationpanel.rules.Visibility;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
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
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

final class RuleEditorPanel extends JPanel
{
	private static final long serialVersionUID = 1L;
	private static final String EDT_SUBJECT = "Rule editor mutations";

	private static final String CARD_LIST = "LIST";
	private static final String CARD_EDIT = "EDIT";
	private static final String CARD_GATE = "GATE";

	private final RuleEditorController controller;
	private final CardLayout cards = new CardLayout();
	private final JPanel cardPanel = new JPanel(cards);
	private final RuleListView listView;
	private final JPanel migrationGate;
	private final JButton migrationContinueButton;
	private final JTextArea migrationGateText;
	private final JScrollPane migrationGateScrollPane;
	private boolean migrationPending;
	private UUID editingId;
	private RuleEditView editView;
	private JScrollPane editorScrollPane;
	private String currentCard;

	RuleEditorPanel(RuleEditorController controller)
	{
		requireEdt();
		this.controller = Objects.requireNonNull(controller, "controller");
		this.migrationPending = controller.wasMigrated();
		setLayout(new BorderLayout());

		this.listView = new RuleListView(this, controller);
		this.cardPanel.add(listView, CARD_LIST);

		this.migrationGate = new JPanel(new BorderLayout(0, 8));
		this.migrationGate.setBackground(ColorScheme.DARK_GRAY_COLOR);
		this.migrationGate.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JLabel heading = new JLabel("Rules imported");
		heading.setForeground(ColorScheme.BRAND_ORANGE);
		this.migrationGate.add(heading, BorderLayout.NORTH);

		this.migrationGateText = new JTextArea(migrationSummary(controller));
		this.migrationGateText.setEditable(false);
		this.migrationGateText.setFocusable(false);
		this.migrationGateText.setLineWrap(true);
		this.migrationGateText.setWrapStyleWord(true);
		this.migrationGateText.setOpaque(false);
		this.migrationGateText.setForeground(ColorScheme.TEXT_COLOR);
		this.migrationGateScrollPane = new JScrollPane(migrationGateText);
		this.migrationGateScrollPane.setHorizontalScrollBarPolicy(
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		this.migrationGateScrollPane.setOpaque(false);
		this.migrationGateScrollPane.getViewport().setOpaque(false);
		this.migrationGateScrollPane.setBorder(null);
		this.migrationGate.add(migrationGateScrollPane, BorderLayout.CENTER);

		this.migrationContinueButton = new JButton("Continue to rules");
		this.migrationContinueButton.addActionListener(event ->
		{
			migrationPending = false;
			showCard(CARD_LIST);
		});
		JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		south.setOpaque(false);
		south.add(migrationContinueButton);
		this.migrationGate.add(south, BorderLayout.SOUTH);

		this.cardPanel.add(migrationGate, CARD_GATE);
		add(cardPanel, BorderLayout.CENTER);

		if (migrationPending)
		{
			showCard(CARD_GATE);
		}
		else
		{
			showCard(CARD_LIST);
		}

		controller.addListener(new RuleEditorController.Listener()
		{
			@Override
			public void onModeChanged(RuleEditorController.ViewMode mode, NotificationRule draft)
			{
				if (mode == RuleEditorController.ViewMode.EDITING)
				{
					showEditor(draft);
				}
				else
				{
					hideEditor();
				}
			}

			@Override
			public void onSelectionChanged(UUID selectedId)
			{
				listView.select(selectedId);
			}

			@Override
			public void onActionError(String error)
			{
				listView.showActionErrors(List.of(error));
			}
		});
	}

	void showNewRule()
	{
		requireEdt();
		if (!canCreateRule())
		{
			return;
		}
		controller.openNewDraft();
	}

	void showNewRuleFor(String message)
	{
		requireEdt();
		if (!canCreateRule())
		{
			return;
		}
		controller.openDraftFor(message);
	}

	void showRule(UUID id)
	{
		requireEdt();
		if (controller.hasBlockingError() || indexOfRule(id) < 0)
		{
			return;
		}
		controller.openRule(id);
	}

	boolean canCreateRule()
	{
		requireEdt();
		return controller.canAdd();
	}

	void reload()
	{
		reload(false);
	}

	boolean hasPendingMigration()
	{
		requireEdt();
		return migrationPending;
	}

	void reload(boolean migratedElsewhere)
	{
		requireEdt();
		controller.reload();
		if (migratedElsewhere || controller.wasMigrated())
		{
			migrationPending = true;
			migrationGateText.setText(migrationSummary(controller));
		}
		if (listView != null)
		{
			listView.updateBlockingBanner();
		}
		if (CARD_EDIT.equals(currentCard))
		{
			validateEditor();
			return;
		}
		if (migrationPending)
		{
			showCard(CARD_GATE);
		}
		else
		{
			showCard(CARD_LIST);
		}
	}

	private void showEditor(NotificationRule draft)
	{
		if (editorScrollPane != null)
		{
			cardPanel.remove(editorScrollPane);
		}
		editingId = draft.getId();
		editView = new RuleEditView(this, draft);
		editorScrollPane = new JScrollPane(editView);
		editorScrollPane.setHorizontalScrollBarPolicy(
			JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		cardPanel.add(editorScrollPane, CARD_EDIT);
		showCard(CARD_EDIT);
		validateEditor();
	}

	private void hideEditor()
	{
		if (editorScrollPane != null)
		{
			cardPanel.remove(editorScrollPane);
			editorScrollPane = null;
		}
		editView = null;
		editingId = null;
		if (migrationPending)
		{
			showCard(CARD_GATE);
		}
		else
		{
			showCard(CARD_LIST);
		}
	}

	private void showCard(String card)
	{
		currentCard = card;
		cards.show(cardPanel, card);
		revalidate();
		repaint();
	}

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
					+ "the whole message, so * is how you match part of one (*dragon* rather "
					+ "than dragon).\n\n");
			if (needRewrite > 0)
			{
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

	private void showSelectedRule()
	{
		controller.openSelected();
	}

	private void saveDraft()
	{
		RuleEditView editor = requireEditor();
		NotificationRule draft = editor.buildDraft();
		RuleEditorController.SaveResult result = controller.saveCurrentDraft(draft);
		if (!result.isSuccess())
		{
			editor.showErrors(result.getErrors());
		}
	}

	private void cancelDraft()
	{
		controller.cancelEdit();
	}

	private void validateEditor()
	{
		if (editView == null)
		{
			return;
		}
		editView.showErrors(controller.validateForEditor(editView.buildDraft()));
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
		RuleEditorController.SaveResult result = Objects.equals(controller.getSelectedId(), confirmedId)
			? controller.deleteSelected() : controller.delete(confirmedId);
		if (!result.isSuccess() && listView != null)
		{
			listView.showActionErrors(result.getErrors());
		}
	}

	private void confirmReset()
	{
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
		if (migrationPending)
		{
			migrationPending = false;
			showCard(CARD_LIST);
		}
		if (listView != null)
		{
			listView.updateBlockingBanner();
		}
		if (!result.isSuccess())
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
		return controller.getSelectedRule();
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

	private static void setWrappedText(JTextArea area, String text)
	{
		area.setText(text);
		int width = area.getWidth() > 0 ? area.getWidth() : PluginPanel.PANEL_WIDTH - 16;
		int textWidth = area.getFontMetrics(area.getFont()).stringWidth(text);
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
		Edt.require(EDT_SUBJECT);
	}

	private static final class RuleListView extends JPanel
	{
		private static final long serialVersionUID = 1L;
		private static final int LIST_PREVIEW_LIMIT = 48;
		private static final int TOOLTIP_PREVIEW_LIMIT = 200;
		private static final int TOOLTIP_WRAP_WIDTH = 320;

		private final RuleEditorPanel owner;
		private final RuleEditorController controller;
		private final PatternList ruleList;
		private final JScrollPane listScrollPane;
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

		private RuleListView(RuleEditorPanel owner, RuleEditorController controller)
		{
			this.owner = owner;
			this.controller = controller;
			this.ruleList = new PatternList(controller);
			this.listScrollPane = new JScrollPane(ruleList);
			setLayout(new BorderLayout(0, 6));
			setBackground(ColorScheme.DARK_GRAY_COLOR);

			JPanel heading = new JPanel();
			heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
			heading.setOpaque(false);
			blockingBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
			heading.add(blockingBanner);
			resetButton.setAlignmentX(Component.LEFT_ALIGNMENT);
			resetButton.addActionListener(event -> owner.confirmReset());
			heading.add(resetButton);
			actionError.setAlignmentX(Component.LEFT_ALIGNMENT);
			actionError.setVisible(false);
			heading.add(actionError);

			emptyState.setAlignmentX(Component.LEFT_ALIGNMENT);
			emptyState.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			emptyState.setText("No rules yet. Add one to give the notifications it matches their "
				+ "own background or opacity, or to hide them (everything else uses the default "
				+ "color and opacity from the plugin's settings).");
			heading.add(emptyState);
			add(heading, BorderLayout.NORTH);
			ruleList.setCellRenderer(renderer());
			ruleList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
			ruleList.addListSelectionListener(event ->
			{
				if (!event.getValueIsAdjusting())
				{
					int index = ruleList.getSelectedIndex();
					UUID selectedId = (index >= 0 && index < controller.getSize())
						? controller.getElementAt(index).getId() : null;
					controller.select(selectedId);
					updateButtons();
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
			add(ruleActions, BorderLayout.SOUTH);

			addButton.addActionListener(event -> controller.openNewDraft());
			editButton.addActionListener(event -> controller.openSelected());
			toggleButton.addActionListener(event -> controller.toggleSelected());
			upButton.addActionListener(event -> controller.moveSelectedUp());
			downButton.addActionListener(event -> controller.moveSelectedDown());
			deleteButton.addActionListener(event -> owner.confirmDelete());
			updateBlockingBanner();

			controller.addListDataListener(new ListDataListener()
			{
				@Override
				public void intervalAdded(ListDataEvent event)
				{
					updateBlockingBanner();
					select(controller.getSelectedId());
				}

				@Override
				public void intervalRemoved(ListDataEvent event)
				{
					updateBlockingBanner();
					select(controller.getSelectedId());
				}

				@Override
				public void contentsChanged(ListDataEvent event)
				{
					updateBlockingBanner();
					select(controller.getSelectedId());
				}
			});
		}

		private void updateBlockingBanner()
		{
			boolean blocked = controller.hasBlockingError();
			blockingBanner.setText(blocked ? controller.getBlockingError() : "");
			blockingBanner.setVisible(blocked);
			resetButton.setVisible(blocked);
			updateButtons();
			updateEmptyState();
		}

		private void updateEmptyState()
		{
			emptyState.setVisible(controller.getSize() == 0 && !controller.hasBlockingError());
		}

		private void select(UUID id)
		{
			if (id == null)
			{
				ruleList.clearSelection();
				return;
			}
			for (int index = 0; index < controller.getSize(); index++)
			{
				if (controller.getElementAt(index).getId().equals(id))
				{
					if (ruleList.getSelectedIndex() != index)
					{
						ruleList.setSelectedIndex(index);
						ruleList.ensureIndexIsVisible(index);
					}
					return;
				}
			}
			ruleList.clearSelection();
		}

		private void updateButtons()
		{
			addButton.setEnabled(controller.canAdd());
			editButton.setEnabled(controller.canEdit());
			toggleButton.setEnabled(controller.canEdit());
			upButton.setEnabled(controller.canMoveUp());
			downButton.setEnabled(controller.canMoveDown());
			deleteButton.setEnabled(controller.canDelete());
			NotificationRule selected = controller.getSelectedRule();
			if (selected != null)
			{
				toggleButton.setText(selected.isEnabled() ? "Disable" : "Enable");
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
			for (int index = 0; index < controller.getSize(); index++)
			{
				Component component = cellRenderer.getListCellRendererComponent(ruleList,
					controller.getElementAt(index), index, false, false);
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

		private static final class PatternList extends JList<NotificationRule>
		{
			private static final long serialVersionUID = 1L;

			private PatternList(ListModel<NotificationRule> model)
			{
				super(model);
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
					lines.add(null);
					lines.addAll(wrapForTooltip("Warning: " + safe(rule.getMigrationNote())));
				}
				return tooltipHtml(lines);
			}

			private List<String> wrapForTooltip(String paragraph)
			{
				Font font = UIManager.getFont("ToolTip.font");
				FontMetrics metrics = getFontMetrics(font == null ? getFont() : font);
				return NotificationText.wrap(paragraph, TOOLTIP_WRAP_WIDTH, metrics::stringWidth);
			}

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
			if (rule.getVisibility() != null)
			{
				appendSeparator(summary);
				switch (rule.getVisibility())
				{
					case HIDE:
						summary.append("hidden");
						break;
					case SIDEBAR:
						summary.append("sidebar only");
						break;
					default:
						summary.append("shown");
						break;
				}
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

	private static final class RuleEditView extends JPanel implements Scrollable
	{
		private static final long serialVersionUID = 1L;
		private static final Visibility[] VISIBILITY_CHOICES = Visibility.values();
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
		private final JComboBox<Visibility> visibilityChoice = visibilityCombo();
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
				+ "Wildcards (*) match any run of characters.");
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
			loadPattern(safe(draft.getPattern()));
			backgroundCheckBox.setSelected(draft.getBackgroundRgb() != null);
			if (draft.getBackgroundRgb() != null)
			{
				backgroundColor = new Color(draft.getBackgroundRgb());
			}
			opacityCheckBox.setSelected(draft.getOpacityPercent() != null);
			opacitySpinner.setValue(draft.getOpacityPercent() == null
				? 100 : draft.getOpacityPercent());
			visibilityCheckBox.setSelected(draft.getVisibility() != null);
			visibilityChoice.setSelectedItem(selectionFor(draft.getVisibility()));
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
			cancelButton.addActionListener(event -> owner.cancelDraft());

			bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "saveDraft", () ->
			{
				if (saveButton.isEnabled())
				{
					owner.saveDraft();
				}
			});
			bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelDraft",
				() -> owner.cancelDraft());
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
			return false;
		}

		private static JTextArea patternArea()
		{
			JTextArea area = new JTextArea()
			{
				private static final long serialVersionUID = 1L;

				@Override
				public Dimension getMaximumSize()
				{
					return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
				}
			};
			area.setLineWrap(true);
			area.setWrapStyleWord(false);
			area.setRows(1);
			area.setBorder(new JTextField().getBorder());
			area.setFont(new JTextField().getFont());
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

		private void loadPattern(String pattern)
		{
			AbstractDocument document = (AbstractDocument) patternField.getDocument();
			DocumentFilter filter = document.getDocumentFilter();
			document.setDocumentFilter(null);
			try
			{
				patternField.setText(pattern);
			}
			finally
			{
				document.setDocumentFilter(filter);
			}
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
			try
			{
				opacitySpinner.commitEdit();
			}
			catch (ParseException ignored)
			{
			}
			Visibility visibility = selectedVisibility();
			return new NotificationRule(draftId, nameField.getText(), enabledCheckBox.isSelected(),
				patternField.getText(),
				backgroundCheckBox.isSelected() ? backgroundColor.getRGB() & 0xFFFFFF : null,
				opacityCheckBox.isSelected() ? (Integer) opacitySpinner.getValue() : null,
				visibility, null);
		}

		private static Visibility selectionFor(Visibility visibility)
		{
			return visibility == null ? Visibility.SHOW : visibility;
		}

		private Visibility selectedVisibility()
		{
			if (!visibilityCheckBox.isSelected())
			{
				return null;
			}
			return (Visibility) visibilityChoice.getSelectedItem();
		}

		private static JComboBox<Visibility> visibilityCombo()
		{
			JComboBox<Visibility> combo = new JComboBox<>(VISIBILITY_CHOICES);
			combo.setRenderer(new VisibilityRenderer());
			return combo;
		}

		private static final class VisibilityRenderer extends DefaultListCellRenderer
		{
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean selected, boolean focused)
			{
				super.getListCellRendererComponent(list, value, index, selected, focused);
				setText(value instanceof Visibility ? ((Visibility) value).label() : "");
				return this;
			}
		}

		private void setDraft(String name, String pattern, boolean enabled, Integer backgroundRgb,
			Integer opacityPercent, Visibility visibility)
		{
			nameField.setText(safe(name));
			loadPattern(safe(pattern));
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
			visibilityCheckBox.setSelected(visibility != null);
			visibilityChoice.setSelectedItem(selectionFor(visibility));
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

	void setDraftForTest(String name, String pattern, boolean enabled, Integer backgroundRgb,
		Integer opacityPercent, Visibility visibility)
	{
		requireEdt();
		requireEditor().setDraft(name, pattern, enabled, backgroundRgb, opacityPercent, visibility);
	}

	void typeOpacityTextForTest(String text)
	{
		requireEdt();
		((JSpinner.DefaultEditor) requireEditor().opacitySpinner.getEditor()).getTextField().setText(text);
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
		return CARD_LIST.equals(currentCard);
	}

	boolean isShowingListViewForTest()
	{
		requireEdt();
		return isShowingListForTest();
	}

	boolean isShowingEditViewForTest()
	{
		requireEdt();
		return CARD_EDIT.equals(currentCard);
	}

	Component getListViewComponentForTest()
	{
		requireEdt();
		return listView;
	}

	int ruleListRowCountForTest()
	{
		requireEdt();
		return requireList().ruleList.getModel().getSize();
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

	boolean isMigrationGateVisibleForTest()
	{
		requireEdt();
		return CARD_GATE.equals(currentCard);
	}

	boolean isMigrationGateScrollableForTest()
	{
		requireEdt();
		return migrationGate != null && migrationGateScrollPane != null
			&& migrationGateScrollPane.getViewport().getView() == migrationGateText
			&& migrationContinueButton.isVisible() && migrationContinueButton.isEnabled();
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

	boolean isResetVisibleForTest()
	{
		requireEdt();
		return requireList().resetButton.isVisible();
	}

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

	List<String> getVisibilityChoiceLabelsForTest()
	{
		requireEdt();
		JComboBox<Visibility> choice = requireEditor().visibilityChoice;
		ListCellRenderer<? super Visibility> renderer = choice.getRenderer();
		JList<Visibility> list = new JList<>();
		List<String> labels = new ArrayList<>();
		for (int index = 0; index < choice.getItemCount(); index++)
		{
			Component cell = renderer.getListCellRendererComponent(list, choice.getItemAt(index),
				index, false, false);
			labels.add(((JLabel) cell).getText());
		}
		return labels;
	}

	Visibility getDraftVisibilityForTest()
	{
		requireEdt();
		return requireEditor().buildDraft().getVisibility();
	}

	String getDraftPatternForTest()
	{
		requireEdt();
		return requireEditor().buildDraft().getPattern();
	}

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

	void pasteIntoPatternForTest(String text)
	{
		requireEdt();
		requireEditor().patternField.replaceSelection(text);
	}

	boolean isPatternInputWrappingForTest()
	{
		requireEdt();
		RuleEditView editor = requireEditor();
		return editor.patternField.getLineWrap() && editor.patternField.isEditable();
	}

	boolean patternInputLetsEnterReachTheFormForTest()
	{
		requireEdt();
		JTextArea pattern = requireEditor().patternField;
		Object binding = pattern.getInputMap(JComponent.WHEN_FOCUSED)
			.get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
		return binding != null && pattern.getActionMap().get(binding) == null;
	}
}
