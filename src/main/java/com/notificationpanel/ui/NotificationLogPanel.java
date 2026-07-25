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
 * The sidebar's record of this session's notifications, newest first.
 *
 * <p>One component per entry in a scrolled column rather than a {@code JList}: a list of wrapped
 * text needs a cell height that depends on the viewport width, and appending here is an insert at
 * the top and a drop off the bottom, with no rebuild. The scroll position moves with the insert
 * rather than being restored from a snapshot -- see {@link #anchoredScroll}.</p>
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
	/** "Copy text" and "Create rule"; everything below them is rebuilt when the menu opens. */
	private static final int FIXED_MENU_ITEMS = 2;
	/** Enough to see what is in the way without the menu becoming the thing in the way. */
	private static final int MATCHED_RULES_SHOWN = 3;
	private static final int MENU_NAME_LIMIT = 40;
	private static final String EDT_SUBJECT = "Notification log panel access";
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

		/**
		 * The enabled rules already matching {@code message}, topmost first, or empty.
		 *
		 * <p>A rule added now goes to the bottom of the list and each attribute is taken from the
		 * topmost matching rule that sets it, so anything returned here can quietly render a new
		 * rule inert. The menu names them for that reason.</p>
		 */
		List<NotificationRule> matchingRules(String message);

		/** Switches to the Rules tab and opens a stored rule for editing. */
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

	/** Adds one entry the log has just taken, without rebuilding the rest. */
	public void entryLogged(NotificationState.Accepted entry)
	{
		requireEdt();
		Objects.requireNonNull(entry, "entry");
		JScrollBar scrollBar = scrollPane.getVerticalScrollBar();
		int scrolled = scrollBar.getValue();
		// The row the top edge of the viewport is currently inside. Everything the reader can see
		// moves with it, so putting it back where it was is the whole job -- and it needs no
		// measurement of the arriving row, whose height is not knowable yet.
		//
		// Known and accepted: at capacity, with the viewport at the very bottom, the anchor can be
		// the row the trim below removes. Its position then no longer changes, the adjustment comes
		// to nothing, and the list slips by one row -- for a reader who is watching the oldest
		// entries at the moment they are being discarded anyway.
		Component anchor = scrolled > 0 ? anchorRow(scrolled) : null;
		int anchorTop = anchor == null ? 0 : anchor.getY();
		rows.add(row(entry), 0);
		// Trimmed by the same number the log trims by, since this appends rather than re-reading.
		// Trimming takes from the bottom, below anything the reader is looking at, so it is not
		// something the scroll position has to be compensated for -- only the row added above is.
		while (rows.getComponentCount() > NotificationLog.CAPACITY)
		{
			rows.remove(rows.getComponentCount() - 1);
		}
		updateEmptyState();
		rows.revalidate();
		rows.repaint();
		if (anchor != null)
		{
			// Applied after the layout this just scheduled, never during it. A row that has not been
			// laid out reports the height its text would need at zero width -- over a thousand
			// pixels for a message that really occupies sixty-eight -- so adjusting the position
			// now, by any measurement taken now, throws the list to the top. RuneLite's own devtools
			// trackers hook the scrollbar's adjustment for the same reason: the numbers are only
			// true once the model has caught up. Reading the anchor's new position at that point
			// needs no measurement of the arriving row at all, and repeats harmlessly if several
			// notifications arrive before the layout runs.
			SwingUtilities.invokeLater(
				() -> scrollBar.setValue(anchoredScroll(scrolled, anchorTop, anchor.getY())));
		}
	}

	/**
	 * Where the scroll position goes once the row above has moved.
	 *
	 * <p>A scroll position is an offset in pixels from the top of the list, and rows arrive above
	 * it, so leaving the offset alone is what makes the message someone scrolled down to find walk
	 * away from them. Moving it by however far its own row moved leaves it under their eyes.</p>
	 *
	 * <p>At the very top there is no anchor and the list follows new arrivals instead, which is what
	 * someone watching the newest notifications wants.</p>
	 */
	static int anchoredScroll(int scrolled, int anchorTopBefore, int anchorTopAfter)
	{
		return scrolled <= 0 ? 0 : Math.max(0, scrolled + (anchorTopAfter - anchorTopBefore));
	}

	/** The row the top edge of the viewport is inside, or null if no row is there to anchor to. */
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

	private Row row(NotificationState.Accepted entry)
	{
		Row row = new Row(entry);
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
		row.setComponentPopupMenu(rowMenu);
		stripe.setInheritsPopupMenu(true);
		text.setInheritsPopupMenu(true);
		time.setInheritsPopupMenu(true);
		message.setInheritsPopupMenu(true);
		return row;
	}

	/**
	 * The one menu every row shares.
	 *
	 * <p>Built once instead of per row. A menu per row cost 200 {@code JPopupMenu}s and 400
	 * {@code JMenuItem}s at capacity -- around 1600 Swing components and 55ms of the client thread's
	 * EDT time to fill the list -- to show at most one of them. Swing shows a popup by invoking it
	 * on the component that was clicked, so the row can be recovered from
	 * {@link JPopupMenu#getInvoker()} when it opens, which is also the moment the contents have to
	 * be rebuilt anyway.</p>
	 */
	private JPopupMenu buildRowMenu()
	{
		JMenuItem copyItem = new JMenuItem("Copy text");
		copyItem.addActionListener(event -> copyToClipboard(menuMessage));

		JMenuItem createRuleItem = new JMenuItem("Create rule");
		createRuleItem.addActionListener(event -> ruleActions.createRule(menuMessage));

		JPopupMenu menu = new JPopupMenu();
		menu.add(copyItem);
		menu.add(createRuleItem);
		// Read when the popup is about to show rather than when a row was built, so rules deleted,
		// added, reordered or filled to MAX_RULES since then are reflected at the moment the user
		// right-clicks. A named class rather than a lambda or an anonymous one so a test can pick
		// this listener back out of the popup's listener list by type -- JPopupMenu always carries
		// Swing's own internal one too, and that one throws if driven with the synthetic event a
		// headless test would have to hand it.
		menu.addPopupMenuListener(new MenuRefreshListener());
		return menu;
	}

	/**
	 * The message of the row the menu was opened on, held while it is open.
	 *
	 * <p>The two fixed items outlive every row, so they cannot close over a message the way they
	 * did when each row had its own menu. This is set as the popup opens and read when an item is
	 * picked, which can only happen while it is still open.</p>
	 */
	private String menuMessage = "";

	/** The row a popup was invoked on, or null if it was invoked on nothing that belongs to one. */
	private Row invokedRow()
	{
		Component invoker = rowMenu.getInvoker();
		if (invoker instanceof Row)
		{
			return (Row) invoker;
		}
		// Children opt in with setInheritsPopupMenu, and Swing invokes the popup on the component
		// the click reached, so the invoker is usually a label or the message area inside the row.
		return (Row) SwingUtilities.getAncestorOfClass(Row.class, invoker);
	}

	/**
	 * Brings a row's menu up to date with the rules as they stand at the moment it opens.
	 *
	 * <p>The matching rules are named because a rule created from here goes to the bottom of the
	 * list, and each attribute is taken from the topmost matching rule that sets it -- so "Create
	 * rule" on a message three rules already match can produce a rule that saves cleanly and does
	 * nothing. Naming them, and opening one on click, turns that from a surprise into the next
	 * step.</p>
	 */
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
			// Nothing matched, so no separator and no heading either: a menu should not reserve
			// space to say nothing.
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
			// A count rather than the rest of them: a pattern like * matches everything, and the
			// warning is just as clear without a menu taller than the screen.
			rowMenu.add(disabledItem("and " + hidden + " more"));
		}
	}

	private static JMenuItem disabledItem(String text)
	{
		JMenuItem item = new JMenuItem(text);
		item.setEnabled(false);
		return item;
	}

	/** A rule name short enough that the menu stays near the sidebar's width. */
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
		Edt.require(EDT_SUBJECT);
	}

	/** One entry's row, named so the shared menu can tell which row it was opened on. */
	private static final class Row extends JPanel
	{
		private static final long serialVersionUID = 1L;

		private final NotificationState.Accepted entry;

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

	// Test hooks, package-private, kept beside the behaviour they reach into rather than ahead of
	// it -- matching how RuleEditorPanel ends.
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

	/** A row's menu as the user would see it, separators included as {@code "---"}. */
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

	/**
	 * Drives the row's own registered listener, the way Swing would just before showing the popup --
	 * without actually opening it, since {@code JPopupMenu.show()} needs a realized window a
	 * headless test does not have. Going through the registered listener rather than calling
	 * {@code refreshMenu} directly is what makes a listener wired to the wrong method, or never
	 * registered at all, fail these hooks the same way it would fail a real right-click.
	 */
	private void openRowMenuForTest(int index)
	{
		// Invoking it on the row is how Swing tells the menu which row it belongs to, so a hook
		// that skipped this would be testing a menu with no row.
		rowMenu.setInvoker(rows.getComponent(index));
		for (PopupMenuListener listener : rowMenu.getListeners(PopupMenuListener.class))
		{
			// JPopupMenu always carries Swing's own internal listener too; only ours tolerates
			// (and ignores) the null event a test has no real popup to build.
			if (listener instanceof MenuRefreshListener)
			{
				listener.popupMenuWillBecomeVisible(null);
			}
		}
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
