package dev.blockfolk.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DialogServiceTest {

    @Test
    void lineDurationHasThreeSecondMinimum() {
        assertEquals(3, DialogService.lineDurationSeconds(null));
        assertEquals(3, DialogService.lineDurationSeconds("Short line"));
        assertEquals(3, DialogService.lineDurationSeconds("x".repeat(45)));
    }

    @Test
    void lineDurationGrowsWithCharacterCount() {
        assertEquals(4, DialogService.lineDurationSeconds("x".repeat(46)));
        assertEquals(4, DialogService.lineDurationSeconds("x".repeat(60)));
        assertEquals(5, DialogService.lineDurationSeconds("x".repeat(61)));
    }

    @Test
    void lineDurationCountsUnicodeCodePoints() {
        assertEquals(3, DialogService.lineDurationSeconds("😀".repeat(45)));
        assertEquals(4, DialogService.lineDurationSeconds("😀".repeat(46)));
    }
}
