package com.notificationpanel.Formatting;

import com.notificationpanel.Formatting.FormatOptions.ColorOption;
import com.notificationpanel.Formatting.FormatOptions.FormatOption;
import com.notificationpanel.Formatting.FormatOptions.OpacityOption;
import com.notificationpanel.Formatting.FormatOptions.VisibilityOption;
import lombok.Getter;

import java.awt.*;

public class NotificationFormat {

    @Getter
    private final Color color;
    @Getter
    private final int opacity;
    @Getter
    private final boolean isVisible;

    public NotificationFormat(Builder builder) {
        this.color = builder.color;
        this.isVisible = builder.isVisible;
        this.opacity = builder.opacity;
    }


    public static class Builder {
        private Color color = Color.BLACK;
        private boolean isVisible = true;
        private int opacity = 100;

        public Builder() {

        }

        public Builder(Color defaultColor) {
            this.color = defaultColor;
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
