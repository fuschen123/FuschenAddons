package com.FishHelper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DropdownLayoutTest {
    @Test void popupFitsSmallAndLargeScreensAtEveryEdge() {
        for (int width : new int[]{320, 427, 800}) for (int height : new int[]{240, 360, 600}) {
            for (int x : new int[]{0, width / 2, width - 80}) for (int y : new int[]{0, height / 2, height - 26}) {
                var box = DropdownLayout.place(width, height, x, y, 128, 26, 7);
                assertTrue(box.x() >= 0 && box.x() + box.width() <= width);
                assertTrue(box.y() >= 0 && box.y() + box.height() <= height);
                assertTrue(box.visibleRows() >= 1 && box.visibleRows() <= 7);
                assertEquals(0, box.rowAt(box.x() + 4, box.y() + 4));
                assertEquals(-1, box.rowAt(box.x() - 1, box.y() + 4));
                assertEquals(-1, box.rowAt(box.x() + 4, box.y() + box.height()));
            }
        }
    }

    @Test void bottomControlOpensUpwardsAndLimitsVisibleOptions() {
        var box = DropdownLayout.place(320, 240, 160, 190, 128, 26, 12);
        assertTrue(box.y() + box.height() < 190);
        assertTrue(box.visibleRows() < 12);
    }
}
