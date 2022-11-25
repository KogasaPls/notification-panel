package com.notificationpanel.Formatting;

import com.notificationpanel.Formatting.FormatOptions.ColorOption;
import com.notificationpanel.Formatting.FormatOptions.FormatOption;
import com.notificationpanel.Formatting.FormatOptions.OpacityOption;
import com.notificationpanel.Formatting.FormatOptions.VisibilityOption;
import com.notificationpanel.NotificationPanelConfig;
import lombok.Getter;
import net.runelite.client.util.ColorUtil;

import java.awt.*;

public class NotificationFormat {

    @Getter
    private final Color color;
    @Getter
    private final boolean isVisible;

    public NotificationFormat(Builder builder) {
        this.color = ColorUtil.colorWithAlpha(builder.color, builder.opacity * 255 / 100);
        this.isVisible = builder.isVisible;
    }


    public static class Builder {
        private Color color;
        private boolean isVisible = true;
        private int opacity;

        public Builder(NotificationPanelConfig config) {
            color = config.bgColor();
            opacity = config.opacity();
        }

        public Builder setColor(Color color) {
            this.color = color;
            return this;
        }

        public Builder setVisible(boolean isVisible) {
            this.isVisible = isVisible;
            return this;
        }

        public Builder setOpacity(int opacity) {
            this.opacity = opacity;
            return this;
        }

        public Builder setOption(FormatOption option) {
            if (option instanceof ColorOption) {
                Color color = ((ColorOption) option).getColor();
                setColor(color);
            } else if (option instanceof VisibilityOption) {
                boolean isVisible = ((VisibilityOption) option).isVisible();
                setVisible(isVisible);
            } else if (option instanceof OpacityOption) {
                int opacity = ((OpacityOption) option).getOpacity();
                setOpacity(opacity);
            }
            return this;
        }

        public NotificationFormat build() {
            return new NotificationFormat(this);
        }
    }
}
