package com.notificationpanel.views;

import static com.notificationpanel.NotificationPanelPlugin.notificationQueue;
import com.notificationpanel.viewmodels.NotificationPanelViewModel;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.PanelComponent;

public class NotificationPanelView extends BaseView
{
	private final NotificationPanelViewModel viewModel;
	private final PanelComponent panelComponent;

	public NotificationPanelView(NotificationPanelViewModel viewModel, PanelComponent panelComponent)
	{
		super(viewModel);
		this.viewModel = viewModel;

		panelComponent.setWrap(viewModel.Wrap);
		panelComponent.setBorder(viewModel.Border);
		panelComponent.setOrientation(viewModel.Orientation);
		panelComponent.setGap(viewModel.Gap);
		panelComponent.setBackgroundColor(viewModel.BackgroundColor);

		this.panelComponent = panelComponent;
	}


	@Override
	Dimension renderImpl(Graphics2D graphics)
	{
		updatePanelSize();

		List<LayoutableRenderableEntity> panelComponentChildren = panelComponent.getChildren();
		panelComponentChildren.clear();
		notificationQueue.forEach(s -> panelComponentChildren.add(new NotificationView(s)));
		updatePanelSize();

		return panelComponent.render(graphics);
	}


	void updatePanelSize()
	{
		final Dimension preferredSize = panelComponent.getPreferredSize();

		if (preferredSize != null && !preferredSize.equals(viewModel.Border.getSize()))
		{
			viewModel.setPreferredSize(preferredSize);
		}

		setPreferredSize(preferredSize);
	}

	@Override
	public Rectangle getBounds()
	{
		return viewModel.Border.getBounds();
	}

	@Override
	public void setPreferredLocation(Point position)
	{
		viewModel.setPreferredLocation(position);
		panelComponent.setPreferredLocation(position);
	}

	@Override
	public void setPreferredSize(Dimension dimension)
	{
		viewModel.setPreferredSize(dimension);
		panelComponent.setPreferredSize(dimension);
	}
}
