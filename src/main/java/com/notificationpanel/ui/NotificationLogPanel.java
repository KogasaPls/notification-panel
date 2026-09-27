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

import com.notificationpanel.rules.NotificationRule;
import com.notificationpanel.state.NotificationState;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Displays this session's notifications in the sidebar, newest first.
 */
final class NotificationLogPanel extends JPanel
{
	private static final long serialVersionUID = 1L;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final int STRIPE_WIDTH = 3;
	private static final int FIXED_MENU_ITEMS = 2;
	private static final int MATCHED_RULES_SHOWN = 3;
	private static final int MENU_NAME_LIMIT = 40;
	private static final String EDT_SUBJECT = "Notification log panel access";
	private static final String EMPTY_STATE =
		"No notifications yet. New notifications will appear here.";

	interface RuleActions
	{
		boolean canCreateRule();

		void createRule(String message);

		List<NotificationRule> matchingRules(String message);

		void openRule(UUID id);
	}

	private final NotificationLog log;
	private final ZoneId zone;
	private final RuleActions ruleActions;
	private final Clipboard clipboard;
	private final RowColumn rows = new RowColumn();
	private final JPopupMenu rowMenu;
	private final JScrollPane scrollPane = new JScrollPane(rows);
	private final JTextArea emptyState = new JTextArea(EMPTY_STATE);
	private final JButton clearPanelButton = new JButton("Clear overlay");
	private final JButton clearLogButton = new JButton("Clear log");
	private String menuMessage = "";

	NotificationLogPanel(NotificationLog log, Runnable clearPanelAction, RuleActions ruleActions)
	{
		// Defer systemClipboard() to avoid HeadlessException when running tests under java.awt.headless=true.
		this(log, clearPanelAction, ruleActions, ZoneId.systemDefault(), null);
	}

	NotificationLogPanel(NotificationLog log, Runnable clearPanelAction, RuleActions ruleActions,
		ZoneId zone, Clipboard clipboard)
	{
		requireEdt();
		this.log = Objects.requireNonNull(log, "log");
		this.zone = Objects.requireNonNull(zone, "zone");
		this.ruleActions = Objects.requireNonNull(ruleActions, "ruleActions");
		this.clipboard = clipboard;
		this.rowMenu = buildRowMenu();
		Objects.requireNonNull(clearPanelAction, "clearPanelAction");

		setLayout(new BorderLayout(0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// No title: the tab above this one already says Notifications, and the sidebar is 225px
		// wide, so a second copy of the word costs a line of the list for nothing.
		emptyState.setEditable(false);
		emptyState.setFocusable(false);
		emptyState.setLineWrap(true);
		emptyState.setWrapStyleWord(true);
		emptyState.setOpaque(false);
		emptyState.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(emptyState, BorderLayout.NORTH);

		add(scrollPane, BorderLayout.CENTER);

		JPanel actions = new JPanel(new GridLayout(2, 1, 4, 4));
		actions.setOpaque(false);
		clearPanelButton.setToolTipText("Remove every notification currently on screen.");
		clearPanelButton.addActionListener(event -> clearPanelAction.run());
		actions.add(clearPanelButton);
		clearLogButton.setToolTipText("Empty this list. What is on screen is left alone.");
		clearLogButton.addActionListener(event -> clearLog());
		actions.add(clearLogButton);
		add(actions, BorderLayout.SOUTH);

		render();
	}

	void entryLogged(NotificationState.Accepted entry)
	{
		requireEdt();
		Objects.requireNonNull(entry, "entry");
		JScrollBar scrollBar = scrollPane.getVerticalScrollBar();
		int scrolled = scrollBar.getValue();
		Component anchor = scrolled > 0 ? anchorRow(scrolled) : null;
		int anchorTop = anchor == null ? 0 : anchor.getY();
		rows.add(row(entry), 0);
		while (rows.getComponentCount() > NotificationLog.CAPACITY)
		{
			rows.remove(rows.getComponentCount() - 1);
		}
		updateEmptyState();
		rows.revalidate();
		rows.repaint();
		if (anchor != null)
		{
			SwingUtilities.invokeLater(
				() -> scrollBar.setValue(anchoredScroll(scrolled, anchorTop, anchor.getY())));
		}
	}

	static int anchoredScroll(int scrolled, int anchorTopBefore, int anchorTopAfter)
	{
		return scrolled <= 0 ? 0 : Math.max(0, scrolled + (anchorTopAfter - anchorTopBefore));
	}

	private Component anchorRow(int scrolled)
	{
		for (Component row : rows.getComponents())
		{
			if (scrolled >= row.getY() && scrolled < row.getY() + row.getHeight())
			{
				return row;
			}
		}
		return null;
	}

	private void clearLog()
	{
		log.clear();
		render();
	}

	private void render()
	{
		rows.removeAll();
		List<NotificationState.Accepted> entries = log.getEntries();
		for (int index = entries.size() - 1; index >= 0; index--)
		{
			rows.add(row(entries.get(index)));
		}
		updateEmptyState();
		revalidate();
		repaint();
	}

	private void updateEmptyState()
	{
		boolean empty = rows.getComponentCount() == 0;
		emptyState.setText(empty ? EMPTY_STATE : "");
		emptyState.setVisible(empty);
	}

	private Row row(NotificationState.Accepted entry)
	{
		Row row = new Row(entry);
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 4));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel stripe = row.stripe;
		stripe.setBackground(new Color(entry.getBackgroundRgb()));
		stripe.setPreferredSize(new Dimension(STRIPE_WIDTH, 1));
		row.add(stripe, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		JLabel time = new JLabel(TIME.withZone(zone).format(entry.getArrivedAt()));
		time.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		time.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(time);
		JTextArea message = new JTextArea(entry.getMessage());
		message.setEditable(false);
		message.setFocusable(false);
		message.setLineWrap(true);
		message.setWrapStyleWord(true);
		message.setOpaque(false);
		message.setForeground(ColorScheme.TEXT_COLOR);
		message.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(message);
		row.add(text, BorderLayout.CENTER);

		row.setComponentPopupMenu(rowMenu);
		stripe.setInheritsPopupMenu(true);
		text.setInheritsPopupMenu(true);
		time.setInheritsPopupMenu(true);
		message.setInheritsPopupMenu(true);
		return row;
	}

	private JPopupMenu buildRowMenu()
	{
		JMenuItem copyItem = new JMenuItem("Copy text");
		copyItem.addActionListener(event -> copyToClipboard(menuMessage));

		JMenuItem createRuleItem = new JMenuItem("Create rule");
		createRuleItem.addActionListener(event -> ruleActions.createRule(menuMessage));

		JPopupMenu menu = new JPopupMenu();
		menu.add(copyItem);
		menu.add(createRuleItem);
		menu.addPopupMenuListener(new MenuRefreshListener());
		return menu;
	}

	private Row invokedRow()
	{
		Component invoker = rowMenu.getInvoker();
		if (invoker instanceof Row)
		{
			return (Row) invoker;
		}
		return (Row) SwingUtilities.getAncestorOfClass(Row.class, invoker);
	}

	private void refreshMenu()
	{
		Row row = invokedRow();
		if (row == null)
		{
			return;
		}
		menuMessage = row.entry.getMessage();
		createRuleItem().setEnabled(ruleActions.canCreateRule());

		while (rowMenu.getComponentCount() > FIXED_MENU_ITEMS)
		{
			rowMenu.remove(rowMenu.getComponentCount() - 1);
		}

		List<NotificationRule> matched = ruleActions.matchingRules(menuMessage);
		if (matched.isEmpty())
		{
			return;
		}

		rowMenu.addSeparator();
		rowMenu.add(disabledItem("Matched by"));
		for (NotificationRule rule : matched.subList(0, Math.min(MATCHED_RULES_SHOWN, matched.size())))
		{
			UUID id = rule.getId();
			JMenuItem item = new JMenuItem(namePreview(rule.getName()));
			item.addActionListener(event -> ruleActions.openRule(id));
			rowMenu.add(item);
		}
		int hidden = matched.size() - MATCHED_RULES_SHOWN;
		if (hidden > 0)
		{
			rowMenu.add(disabledItem("and " + hidden + " more"));
		}
	}

	private static JMenuItem disabledItem(String text)
	{
		JMenuItem item = new JMenuItem(text);
		item.setEnabled(false);
		return item;
	}

	private static String namePreview(String name)
	{
		String safe = name == null ? "" : name;
		if (safe.codePointCount(0, safe.length()) <= MENU_NAME_LIMIT)
		{
			return safe;
		}
		return safe.substring(0, safe.offsetByCodePoints(0, MENU_NAME_LIMIT)) + "...";
	}

	private final class MenuRefreshListener implements PopupMenuListener
	{
		@Override
		public void popupMenuWillBecomeVisible(PopupMenuEvent event)
		{
			refreshMenu();
		}

		@Override
		public void popupMenuWillBecomeInvisible(PopupMenuEvent event)
		{
		}

		@Override
		public void popupMenuCanceled(PopupMenuEvent event)
		{
		}
	}

	private void copyToClipboard(String message)
	{
		try
		{
			(clipboard != null ? clipboard : systemClipboard())
				.setContents(new StringSelection(message), null);
		}
		catch (IllegalStateException exception)
		{
		}
	}

	private static Clipboard systemClipboard()
	{
		return Toolkit.getDefaultToolkit().getSystemClipboard();
	}

	private static void requireEdt()
	{
		Edt.require(EDT_SUBJECT);
	}

	private static final class Row extends JPanel
	{
		private static final long serialVersionUID = 1L;

		private final NotificationState.Accepted entry;
		/** Held rather than looked up, so nothing has to walk the layout to find it again. */
		private final JPanel stripe = new JPanel();

		private Row(NotificationState.Accepted entry)
		{
			super(new BorderLayout(6, 0));
			this.entry = entry;
		}
	}

	/**
	 * The column of rows.
	 *
	 * <p>Implements {@link Scrollable} only to pin itself to the viewport width, for the reason
	 * {@code RuleEditView} does: a BoxLayout column reports its widest child, and a wrapped text
	 * area has no width of its own to report, so one long message would otherwise widen the whole
	 * sidebar instead of wrapping.</p>
	 */
	private static final class RowColumn extends JPanel implements Scrollable
	{
		private static final long serialVersionUID = 1L;
		private static final int SCROLL_UNIT = 16;
		/** Only a default for the viewport to start from; the sidebar's real height wins. */
		private static final int DEFAULT_VIEWPORT_HEIGHT = 240;

		private RowColumn()
		{
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setBackground(ColorScheme.DARK_GRAY_COLOR);
		}

		/**
		 * How tall the viewport should be, which is not how tall the rows are.
		 *
		 * <p>Returning {@code getPreferredSize()} here asks the scroll pane to be as tall as
		 * everything it holds -- two hundred rows of it -- which is the opposite of what a scroll
		 * pane is for and made the sidebar demand a window taller than the screen. A fixed value
		 * says the viewport has no opinion beyond a sensible default and takes the height it is
		 * given.</p>
		 */
		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return new Dimension(PluginPanel.PANEL_WIDTH, DEFAULT_VIEWPORT_HEIGHT);
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
	}

	int scrollValueForTest()
	{
		requireEdt();
		return scrollPane.getVerticalScrollBar().getValue();
	}

	int getRowCountForTest()
	{
		requireEdt();
		return rows.getComponentCount();
	}

	List<String> getRowTextsForTest()
	{
		requireEdt();
		List<String> texts = new ArrayList<>();
		for (Component row : rows.getComponents())
		{
			StringBuilder text = new StringBuilder();
			appendText(row, text);
			texts.add(text.toString());
		}
		return texts;
	}

	Color getStripeColorForTest(int index)
	{
		requireEdt();
		return ((Row) rows.getComponent(index)).stripe.getBackground();
	}

	String getEmptyStateTextForTest()
	{
		requireEdt();
		return emptyState.getText();
	}

	void clickClearLogForTest()
	{
		requireEdt();
		clearLogButton.doClick();
	}

	void clickClearPanelForTest()
	{
		requireEdt();
		clearPanelButton.doClick();
	}

	void clickCopyTextForTest(int index)
	{
		requireEdt();
		openRowMenuForTest(index);
		copyItem().doClick();
	}

	void clickCreateRuleForTest(int index)
	{
		requireEdt();
		openRowMenuForTest(index);
		createRuleItem().doClick();
	}

	boolean isCreateRuleEnabledForTest(int index)
	{
		requireEdt();
		openRowMenuForTest(index);
		return createRuleItem().isEnabled();
	}

	List<String> rowMenuItemsForTest(int index)
	{
		requireEdt();
		openRowMenuForTest(index);
		List<String> items = new ArrayList<>();
		for (Component component : rowMenu.getComponents())
		{
			items.add(component instanceof JMenuItem
				? ((JMenuItem) component).getText() : "---");
		}
		return items;
	}

	boolean isRowMenuItemEnabledForTest(int index, int item)
	{
		requireEdt();
		openRowMenuForTest(index);
		return rowMenu.getComponent(item).isEnabled();
	}

	void clickRowMenuItemForTest(int index, int item)
	{
		requireEdt();
		openRowMenuForTest(index);
		((JMenuItem) rowMenu.getComponent(item)).doClick();
	}

	private void openRowMenuForTest(int index)
	{
		rowMenu.setInvoker(rows.getComponent(index));
		for (PopupMenuListener listener : rowMenu.getListeners(PopupMenuListener.class))
		{
			if (listener instanceof MenuRefreshListener)
			{
				listener.popupMenuWillBecomeVisible(null);
			}
		}
	}

	List<JPopupMenu> resolvedRowPopupsForTest(int index)
	{
		requireEdt();
		List<JPopupMenu> resolved = new ArrayList<>();
		collectResolvedPopups((JComponent) rows.getComponent(index), resolved);
		return resolved;
	}

	private static void collectResolvedPopups(JComponent component, List<JPopupMenu> resolved)
	{
		resolved.add(component.getComponentPopupMenu());
		for (Component child : component.getComponents())
		{
			if (child instanceof JComponent)
			{
				collectResolvedPopups((JComponent) child, resolved);
			}
		}
	}

	private JMenuItem copyItem()
	{
		return (JMenuItem) rowMenu.getComponent(0);
	}

	private JMenuItem createRuleItem()
	{
		return (JMenuItem) rowMenu.getComponent(1);
	}

	private static void appendText(Component component, StringBuilder text)
	{
		if (component instanceof JLabel)
		{
			text.append(((JLabel) component).getText()).append(' ');
		}
		else if (component instanceof JTextArea)
		{
			text.append(((JTextArea) component).getText()).append(' ');
		}
		else if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				appendText(child, text);
			}
		}
	}
}
