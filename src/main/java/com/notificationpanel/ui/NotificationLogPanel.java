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
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import net.runelite.client.ui.ColorScheme;

/**
 * The sidebar's record of this session's notifications, newest first.
 *
 * <p>One component per entry in a scrolled column rather than a {@code JList}: a list of wrapped
 * text needs a cell height that depends on the viewport width, and appending here is an insert at
 * the top and a drop off the bottom, with no rebuild and no scroll position to restore.</p>
 *
 * <p>A row is deliberately not painted in the notification's own colours. They are chosen to read
 * over the game, at an opacity that means nothing against a sidebar, and sidebar text over them is
 * often unreadable. The colour appears as a stripe instead, which still says which rule caught the
 * message.</p>
 */
public final class NotificationLogPanel extends JPanel
{
	private static final long serialVersionUID = 1L;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final int STRIPE_WIDTH = 3;
	private static final String EDT_ERROR = "Notification log panel access must run on the EDT.";
	private static final String EMPTY_STATE =
		"No notifications yet. Anything the plugin shows, and anything a rule sends here instead of "
			+ "to the panel, is kept in this list for the rest of the session.";

	/** What the log needs from the rule editor, the boundary between the sidebar's two tabs. */
	public interface RuleActions
	{
		/** Whether "Create rule" would actually open a draft, so the menu item can grey out. */
		boolean canCreateRule();

		/** Switches to the Rules tab and opens a draft prefilled from {@code message}. */
		void createRule(String message);
	}

	private final NotificationLog log;
	private final ZoneId zone;
	private final RuleActions ruleActions;
	private final Clipboard clipboard;
	private final RowColumn rows = new RowColumn();
	private final JTextArea emptyState = new JTextArea(EMPTY_STATE);
	private final JButton clearPanelButton = new JButton("Clear panel");
	private final JButton clearLogButton = new JButton("Clear log");

	public NotificationLogPanel(NotificationLog log, Runnable clearPanelAction,
		RuleActions ruleActions)
	{
		// Not systemClipboard() eagerly here: Toolkit.getSystemClipboard() throws
		// HeadlessException outright under java.awt.headless=true, which is how the whole test
		// suite runs, including tests that build a real NotificationSidebarPanel (and so a real
		// NotificationLogPanel) without ever touching the clipboard. Null defers that call to
		// copyToClipboard(), which only runs it if "Copy text" is actually clicked.
		this(log, clearPanelAction, ruleActions, ZoneId.systemDefault(), null);
	}

	/**
	 * @param zone      which clock the times are read against. A parameter so a test can pin it,
	 *                  for the same reason {@code NotificationState} takes a {@code Clock}.
	 * @param clipboard where "Copy text" writes to, for the same reason as {@code zone}: a test
	 *                  supplies {@code new Clipboard("test")} instead of touching the developer's
	 *                  real clipboard. Null means resolve the system clipboard lazily; see the
	 *                  three-argument constructor for why that resolution can't happen here.
	 */
	public NotificationLogPanel(NotificationLog log, Runnable clearPanelAction,
		RuleActions ruleActions, ZoneId zone, Clipboard clipboard)
	{
		requireEdt();
		this.log = Objects.requireNonNull(log, "log");
		this.zone = Objects.requireNonNull(zone, "zone");
		this.ruleActions = Objects.requireNonNull(ruleActions, "ruleActions");
		this.clipboard = clipboard;
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

		add(new JScrollPane(rows), BorderLayout.CENTER);

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

	/** Adds one entry the log has just taken, without rebuilding the rest. */
	public void entryLogged(NotificationState.Accepted entry)
	{
		requireEdt();
		Objects.requireNonNull(entry, "entry");
		rows.add(row(entry), 0);
		// Trimmed by the same number the log trims by, since this appends rather than re-reading.
		while (rows.getComponentCount() > NotificationLog.CAPACITY)
		{
			rows.remove(rows.getComponentCount() - 1);
		}
		updateEmptyState();
		rows.revalidate();
		rows.repaint();
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
		// The log holds them oldest first and the newest is the one worth seeing without scrolling.
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

	private JPanel row(NotificationState.Accepted entry)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 4));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel stripe = new JPanel();
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

		// Attached to the row rather than its children, with the children opting in via
		// setInheritsPopupMenu: that gets platform-correct trigger handling (press on X11, release
		// on Windows) for a right-click anywhere in the row, instead of hand-rolling isPopupTrigger.
		//
		// Every component between the row and a leaf has to opt in, not just the leaves. The lookup
		// walks up one parent at a time and stops at the first component that has no menu of its
		// own and does not inherit -- so leaving it off this middle panel returned null for the
		// text that covers most of the row, and only the row's own padding answered a right-click.
		row.setComponentPopupMenu(rowMenu(entry.getMessage()));
		stripe.setInheritsPopupMenu(true);
		text.setInheritsPopupMenu(true);
		time.setInheritsPopupMenu(true);
		message.setInheritsPopupMenu(true);
		return row;
	}

	private JPopupMenu rowMenu(String message)
	{
		JMenuItem copyItem = new JMenuItem("Copy text");
		copyItem.addActionListener(event -> copyToClipboard(message));

		JMenuItem createRuleItem = new JMenuItem("Create rule");
		createRuleItem.addActionListener(event -> ruleActions.createRule(message));

		JPopupMenu menu = new JPopupMenu();
		menu.add(copyItem);
		menu.add(createRuleItem);
		// Read when the popup is about to show rather than when the row was built, so a rule
		// deleted, added or filled to MAX_RULES since this row appeared is reflected at the moment
		// the user right-clicks, not frozen at append time. A named class rather than a lambda or an
		// anonymous one so a test can pick this listener back out of the popup's listener list by
		// type -- JPopupMenu always carries Swing's own internal one too, and that one throws if
		// driven with the synthetic event a headless test would have to hand it.
		menu.addPopupMenuListener(new CreateRuleRefreshListener(createRuleItem));
		return menu;
	}

	private void refreshCreateRuleEnabled(JMenuItem createRuleItem)
	{
		createRuleItem.setEnabled(ruleActions.canCreateRule());
	}

	private final class CreateRuleRefreshListener implements PopupMenuListener
	{
		private final JMenuItem createRuleItem;

		private CreateRuleRefreshListener(JMenuItem createRuleItem)
		{
			this.createRuleItem = createRuleItem;
		}

		@Override
		public void popupMenuWillBecomeVisible(PopupMenuEvent event)
		{
			refreshCreateRuleEnabled(createRuleItem);
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
			// The AWT clipboard throws this when another application holds it. The user can simply
			// right-click and copy again, so there is nothing more useful to do here.
		}
	}

	private static Clipboard systemClipboard()
	{
		return Toolkit.getDefaultToolkit().getSystemClipboard();
	}

	private static void requireEdt()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			throw new IllegalStateException(EDT_ERROR);
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

		private RowColumn()
		{
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setBackground(ColorScheme.DARK_GRAY_COLOR);
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
	}

	// Test hooks, package-private, kept beside the behaviour they reach into rather than ahead of
	// it -- matching how RuleEditorPanel ends.
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
		JPanel row = (JPanel) rows.getComponent(index);
		return ((JPanel) ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.WEST))
			.getBackground();
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
		copyItem(index).doClick();
	}

	void clickCreateRuleForTest(int index)
	{
		requireEdt();
		createRuleItem(index).doClick();
	}

	/**
	 * Reads the "Create rule" item's enabled state by driving the row's own registered
	 * {@code CreateRuleRefreshListener}, the way Swing would just before showing the popup --
	 * without actually opening it, since {@code JPopupMenu.show()} needs a realized window a
	 * headless test does not have. This exercises the real registered listener rather than calling
	 * {@code refreshCreateRuleEnabled} directly, so a listener wired to the wrong method, or never
	 * registered at all, fails this the same way it would fail a real right-click.
	 */
	boolean isCreateRuleEnabledForTest(int index)
	{
		requireEdt();
		for (PopupMenuListener listener : rowMenu(index).getListeners(PopupMenuListener.class))
		{
			// JPopupMenu always carries Swing's own internal listener too; only ours tolerates
			// (and ignores) the null event a test has no real popup to build.
			if (listener instanceof CreateRuleRefreshListener)
			{
				listener.popupMenuWillBecomeVisible(null);
			}
		}
		return createRuleItem(index).isEnabled();
	}

	/**
	 * What a right-click resolves to from every component in a row, including the row itself, the
	 * way Swing resolves it: {@code getComponentPopupMenu} walks up one parent at a time and stops
	 * at the first component that neither carries a menu nor inherits one. A null here is a dead
	 * zone -- a patch of the row where right-clicking does nothing -- and the text is most of the
	 * row's area, so a dead zone there is most of the feature.
	 */
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

	private JPopupMenu rowMenu(int index)
	{
		return ((JPanel) rows.getComponent(index)).getComponentPopupMenu();
	}

	private JMenuItem copyItem(int index)
	{
		return (JMenuItem) rowMenu(index).getComponent(0);
	}

	private JMenuItem createRuleItem(int index)
	{
		return (JMenuItem) rowMenu(index).getComponent(1);
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
