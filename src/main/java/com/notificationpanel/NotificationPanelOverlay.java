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
package com.notificationpanel;

import com.notificationpanel.layout.NotificationText;
import com.notificationpanel.state.NotificationState;
import com.notificationpanel.ui.NotificationBoxComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;

public class NotificationPanelOverlay extends OverlayPanel
{
	public static final String CLEAR_ALL = "Clear";
	private static final int DEFAULT_WIDTH = 250;

	private final NotificationState state;
	// Owned here so it outlives the per-frame components and is only touched while rendering.
	private final NotificationText.Cache wrapCache = new NotificationText.Cache();

	@Inject
	NotificationPanelOverlay(NotificationPanelPlugin plugin, NotificationState state)
	{
		super(plugin);
		this.state = state;
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(PRIORITY_LOW);
		setResizable(true);
		setMinimumSize(50);
		panelComponent.setOrientation(ComponentOrientation.VERTICAL);
		panelComponent.setGap(new Point(0, 6));
		panelComponent.setBorder(new Rectangle());
		panelComponent.setBackgroundColor(new Color(0, 0, 0, 0));
		getMenuEntries().add(new OverlayMenuEntry(
			MenuAction.RUNELITE_OVERLAY, CLEAR_ALL, "Notification panel"));
	}

	/**
	 * Gives the panel a starting width when the user has never sized it.
	 *
	 * <p>Must be called after {@code OverlayManager.add}, not from the constructor: adding an
	 * overlay loads its stored geometry and calls {@code setPreferredSize} unconditionally, so a
	 * width set here beforehand is replaced by the stored one -- or by null when nothing is
	 * stored, which leaves {@code PanelComponent} on its own 129px default. RuneLite only
	 * persists a size the user has dragged, so this runs on every start until they do.</p>
	 */
	void applyDefaultSizeIfUnset()
	{
		if (getPreferredSize() == null)
		{
			setPreferredSize(new Dimension(DEFAULT_WIDTH, 0));
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<NotificationState.Snapshot> snapshots = state.snapshot();
		if (snapshots.isEmpty())
		{
			return null;
		}
		for (NotificationState.Snapshot snapshot : snapshots)
		{
			panelComponent.getChildren().add(new NotificationBoxComponent(snapshot, wrapCache));
		}
		return super.render(graphics);
	}
}
