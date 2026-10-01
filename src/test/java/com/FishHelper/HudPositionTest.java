package com.FishHelper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudPositionTest {
    @Test void dragClampsToEdgesAndRemainsReachableAfterResize() {
        var p = HudPosition.fromPixels(10_000, -100, 1920, 1080, 250, 55);
        assertEquals(1, p.x()); assertEquals(0, p.y());
        for (int width : new int[] {320, 640, 1920}) assertEquals(width - 250, p.pixelX(width, 250));
        assertEquals(0, p.pixelY(240, 55));
    }
    @Test void badSavedValuesFallBackAndCoordinatesRoundTrip() {
        assertEquals(new HudPosition(.5, .12), new HudPosition(Double.NaN, Double.POSITIVE_INFINITY));
        var p = new HudPosition(.3, .7);
        var restored = HudPosition.fromPixels(p.pixelX(850, 250), p.pixelY(655, 55), 850, 655, 250, 55);
        assertEquals(p, restored);
    }
}
