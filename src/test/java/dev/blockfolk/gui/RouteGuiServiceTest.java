package dev.blockfolk.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RouteGuiServiceTest {

    @Test
    void parsesSupportedWaitIntervals() {
        assertEquals(10_000L, RouteGuiService.parseWaitInterval("10s").orElseThrow());
        assertEquals(500L, RouteGuiService.parseWaitInterval("500ms").orElseThrow());
        assertEquals(120_000L, RouteGuiService.parseWaitInterval("2m").orElseThrow());
        assertEquals(5_400_000L, RouteGuiService.parseWaitInterval("1.5h").orElseThrow());
    }

    @Test
    void rejectsMissingUnitsAndNonPositiveIntervals() {
        assertTrue(RouteGuiService.parseWaitInterval("10").isEmpty());
        assertTrue(RouteGuiService.parseWaitInterval("0s").isEmpty());
        assertTrue(RouteGuiService.parseWaitInterval("later").isEmpty());
    }
}
