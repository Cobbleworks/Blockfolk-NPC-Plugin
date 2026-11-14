package dev.blockfolk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class HealthBarTest {

    @Test
    void hidesAtFullHealthAndWhenDead() {
        assertEquals(HealthBar.HIDDEN, HealthBar.filledSegments(20, 20));
        assertEquals(HealthBar.HIDDEN, HealthBar.filledSegments(0, 20));
        assertEquals(HealthBar.HIDDEN, HealthBar.filledSegments(5, 0));
    }

    @Test
    void fillsSegmentsInProportionToHealth() {
        assertEquals(4, HealthBar.filledSegments(8, 20));
        assertEquals(5, HealthBar.filledSegments(50, 100));
    }

    @Test
    void neverLooksFullWhenDamagedOrEmptyWhenAlive() {
        assertEquals(9, HealthBar.filledSegments(19.9, 20));
        assertEquals(1, HealthBar.filledSegments(0.1, 20));
    }

    @Test
    void drawsFilledAndEmptySegmentsInBrackets() {
        assertEquals("[■■■■□□□□□□]", PlainTextComponentSerializer.plainText().serialize(HealthBar.text(4)));
    }
}
