package com.notificationpanel.Formatting;

import com.notificationpanel.Formatting.FormatOptions.ColorOption;
import com.notificationpanel.Formatting.FormatOptions.DurationOption;
import com.notificationpanel.Formatting.FormatOptions.OpacityOption;
import com.notificationpanel.Formatting.FormatOptions.ShowTimeOption;
import com.notificationpanel.Formatting.FormatOptions.VisibilityOption;
import com.notificationpanel.NotificationPanelConfig;
import java.awt.Color;
import lombok.Getter;

public class Format
{
	@Getter
	private Integer opacity;
	@Getter
	private Color color;
	@Getter
	private Boolean isVisible;
	@Getter
	private int duration;
	@Getter
	private boolean showTime;

	public static Format getDefault(NotificationPanelConfig config)
	{
		return new Format().withOptions(PartialFormat.getDefaults(config));
	}

	public Format withOptions(PartialFormat options)
	{
		options.getOptionOfType(ColorOption.class).ifPresent(this::setColor);
		options.getOptionOfType(OpacityOption.class).ifPresent(this::setOpacity);
		options.getOptionOfType(VisibilityOption.class).ifPresent(this::setIsVisible);
		options.getOptionOfType(DurationOption.class).ifPresent(this::setDuration);
		options.getOptionOfType(ShowTimeOption.class).ifPresent(this::setShowTime);
		return this;
	}

	private void setColor(ColorOption option)
	{
		this.color = option.getColor();
	}

	private void setIsVisible(VisibilityOption option)
	{
		this.isVisible = option.isVisible();
	}

	private void setOpacity(OpacityOption option)
	{
		this.opacity = option.getOpacity();
	}

	public void setDuration(DurationOption option)
	{
		this.duration = option.getDuration();
	}

	public void setShowTime(ShowTimeOption option)
	{
		this.showTime = option.isShowTime();
	}


	public Color getColorWithOpacity()
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), opacity);
	}
}
