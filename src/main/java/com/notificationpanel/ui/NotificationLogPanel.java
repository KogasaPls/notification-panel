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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
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

	private final NotificationLog log;
	private final ZoneId zone;
	private final RowColumn rows = new RowColumn();
	private final JTextArea emptyState = new JTextArea(EMPTY_STATE);
	private final JButton clearPanelButton = new JButton("Clear panel");
	private final JButton clearLogButton = new JButton("Clear log");

	public NotificationLogPanel(NotificationLog log, Runnable clearPanelAction)
	{
		this(log, clearPanelAction, ZoneId.systemDefault());
	}

	/**
	 * @param zone which clock the times are read against. A parameter so a test can pin it, for the
	 *             same reason {@code NotificationState} takes a {@code Clock}.
	 */
	public NotificationLogPanel(NotificationLog log, Runnable clearPanelAction, ZoneId zone)
	{
		requireEdt();
		this.log = Objects.requireNonNull(log, "log");
		this.zone = Objects.requireNonNull(zone, "zone");
		Objects.requireNonNull(clearPanelAction, "clearPanelAction");

		setLayout(new BorderLayout(0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel heading = new JPanel(new BorderLayout(0, 4));
		heading.setOpaque(false);
		JLabel title = new JLabel("Notifications");
		title.setForeground(ColorScheme.TEXT_COLOR);
		heading.add(title, BorderLayout.NORTH);
		emptyState.setEditable(false);
		emptyState.setFocusable(false);
		emptyState.setLineWrap(true);
		emptyState.setWrapStyleWord(true);
		emptyState.setOpaque(false);
		emptyState.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		heading.add(emptyState, BorderLayout.CENTER);
		add(heading, BorderLayout.NORTH);

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
		return row;
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
