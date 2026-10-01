package com.FishHelper;

/** Screen-bounded popup geometry shared by painting and hit testing. */
public record DropdownLayout(int x, int y, int width, int height, int visibleRows) {
    public static final int ROW_HEIGHT = 22;
    public static final int PADDING = 3;

    public static DropdownLayout place(int screenWidth, int screenHeight, int anchorX, int anchorY,
                                       int anchorWidth, int anchorHeight, int options) {
        int margin = 4;
        int width = Math.min(Math.max(128, anchorWidth), Math.max(1, screenWidth - margin * 2));
        int below = screenHeight - margin - anchorY - anchorHeight - margin;
        int above = anchorY - margin * 2;
        boolean openBelow = below >= above;
        int available = Math.max(above, below);
        int rows = Math.max(1, Math.min(options, (available - PADDING * 2) / ROW_HEIGHT));
        int height = rows * ROW_HEIGHT + PADDING * 2;
        int x = Math.clamp(anchorX, margin, Math.max(margin, screenWidth - width - margin));
        int requestedY = openBelow ? anchorY + anchorHeight + margin : anchorY - margin - height;
        int y = Math.clamp(requestedY, margin, Math.max(margin, screenHeight - height - margin));
        return new DropdownLayout(x, y, width, height, rows);
    }

    public int rowAt(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y + PADDING
                || mouseY >= y + height - PADDING) return -1;
        return (int) ((mouseY - y - PADDING) / ROW_HEIGHT);
    }
}
