package com.notificationpanel.views;

import static com.notificationpanel.Constants.CLEAR_ALL;
import com.notificationpanel.NotificationPanelConfig;
import com.notificationpanel.viewmodels.NotificationPanelViewModel;
import com.notificationpanel.viewmodels.NotificationViewModel;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import static net.runelite.api.MenuAction.RUNELITE_OVERLAY;
import net.runelite.api.events.GameTick;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

@Slf4j
public class NotificationPanelView extends OverlayPanel
{
	private final NotificationPanelViewModel viewModel;

	@Inject
	private NotificationPanelView(final NotificationPanelConfig config)
	{
		viewModel = new NotificationPanelViewModel(config);

		panelComponent.setBorder(viewModel.Border);
		panelComponent.setOrientation(viewModel.Orientation);
		panelComponent.setGap(viewModel.Gap);
		panelComponent.setBackgroundColor(viewModel.BackgroundColor);

		panelComponent.setWrap(false);
		setResizable(true);
		setClearChildren(false);
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(OverlayPriority.HIGH);

		getMenuEntries().add(new OverlayMenuEntry(RUNELITE_OVERLAY, CLEAR_ALL,
			"Notification " + "panel"));
	}

	@Override
	public Dimension render(final Graphics2D graphics)
	{
		if (viewModel.isChanged())
		{
			log.debug("Rendering {} notification views", viewModel.queue.size());

			panelComponent.getChildren().clear();
			for (NotificationViewModel notification : viewModel.queue)
			{
				panelComponent.getChildren().add(new NotificationView(notification));
			}

			updatePreferredSize();
		}

		return panelComponent.render(graphics);
	}

	void updatePreferredSize()
	{
		Dimension preferredSize = panelComponent.getPreferredSize();
		if (preferredSize == null)
		{
			return;
		}

		setPreferredSize(preferredSize);
	}

	@Override
	public void setPreferredLocation(java.awt.Point preferredLocation)
	{
		this.viewModel.setPreferredLocation(preferredLocation);
		super.setPreferredLocation(viewModel.getPreferredLocation());
	}

	@Override
	public void setPreferredSize(Dimension preferredSize)
	{
		viewModel.setPreferredSize(preferredSize);
		super.setPreferredSize(viewModel.getPreferredSize());
	}

	public void setConfig(final NotificationPanelConfig config)
	{
		this.viewModel.setConfig(config);
	}

	public void onOverlayMenuClicked(OverlayMenuClicked overlayMenuClicked)
	{
		OverlayMenuEntry overlayMenuEntry = overlayMenuClicked.getEntry();
		if (overlayMenuEntry.getMenuAction() == MenuAction.RUNELITE_OVERLAY &&
			overlayMenuClicked.getOverlay() == this)
		{
			viewModel.onOverlayMenuEntryClicked(overlayMenuEntry);
		}
	}

	public void onShutDown()
	{
		viewModel.clearAll();
	}

	public void onGameTick(GameTick tick)
	{
		viewModel.onGameTick(tick);
	}

	public void onNotificationFired(NotificationFired notificationFired)
	{
		viewModel.onNotificationFired(notificationFired);
	}

}
