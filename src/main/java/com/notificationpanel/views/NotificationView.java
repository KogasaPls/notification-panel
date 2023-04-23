package com.notificationpanel.views;

import com.notificationpanel.viewmodels.NotificationViewModel;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.PanelComponent;

public class NotificationView extends BaseView
{
	public final LayoutableRenderableEntity panelComponent;
	public NotificationViewModel viewModel;

	public NotificationView(NotificationViewModel viewModel)
	{
		super(viewModel);
		this.viewModel = viewModel;
		this.panelComponent = getBox(viewModel);
	}

	private static PanelComponent getBox(NotificationViewModel viewModel)
	{
		PanelComponent box = new PanelComponent();
		box.setPreferredSize(viewModel.outerBounds.getSize());
		box.setPreferredLocation(viewModel.outerBounds.getLocation());
		box.setWrap(false);
		return box;
	}

	@Override
	Dimension renderImpl(Graphics2D graphics)
	{
		graphics.setFont(viewModel.font);
		return panelComponent.render(graphics);
	}

	@Override
	public Rectangle getBounds()
	{
		return viewModel.getBounds();
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
