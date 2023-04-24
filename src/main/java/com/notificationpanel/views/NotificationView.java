package com.notificationpanel.views;

import com.notificationpanel.viewmodels.NotificationViewModel;
import java.awt.Dimension;
import java.awt.Graphics2D;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.PanelComponent;

@Slf4j
public class NotificationView extends PanelComponent
{
	public NotificationViewModel viewModel;

	public NotificationView(NotificationViewModel viewModel)
	{
		this.viewModel = viewModel;
		super.setPreferredSize(viewModel.getPreferredSize());
		super.setPreferredLocation(viewModel.getPreferredLocation());
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (viewModel.isChanged() || getChildren().isEmpty())
		{
			log.debug("Notification view model changed");
			getChildren().clear();
			getChildren().add(buildTextComponent());
		}

		return super.render(graphics);
	}

	@Override
	public void setPreferredLocation(java.awt.Point preferredLocation)
	{
		if (preferredLocation == null)
		{
			return;
		}

		this.viewModel.setPreferredLocation(preferredLocation);
		preferredLocation = this.viewModel.getPreferredLocation();
		super.setPreferredLocation(preferredLocation);
	}


	@Override
	public void setPreferredSize(Dimension preferredSize)
	{
		if (preferredSize == null)
		{
			return;
		}

		this.viewModel.setPreferredSize(preferredSize);
		preferredSize = this.viewModel.getPreferredSize();
		super.setPreferredSize(preferredSize);
	}

	private LayoutableRenderableEntity buildTextComponent()
	{
		log.debug("Building text component");
		return WrappedCenteredTextComponent.builder()
			.text(viewModel.body)
			.font(viewModel.font)
			.color(viewModel.foregroundColor)
			.preferredSize(viewModel.getPreferredSize())
			.preferredLocation(viewModel.getPreferredLocation())
			.build();
	}

}
