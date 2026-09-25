package dev.blockfolk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class ChatAddresseeTest {

    @Test
    void namedNpcKeepsTheTurnEvenWhenAnotherNpcIsCloser() {
        assertEquals(1, ChatAddressee.select("Mr. Mario, can you help?", List.of("Mira", "Mr. Mario")));
        assertEquals(0, ChatAddressee.select("Can somebody help?", List.of("Mira", "Mr. Mario")));
    }

    @Test
    void usesWholeNamesAndPrefersTheLongestMatch() {
        assertEquals(2, ChatAddressee.select("Hello Captain Mario", List.of("Mari", "Mario", "Captain Mario")));
        assertEquals(0, ChatAddressee.select("Marigold is here", List.of("Mari", "Mario")));
    }

    @Test
    void canAddressAnNpcBeyondTheFirstFiveNearby() {
        assertEquals(5,
                ChatAddressee.select("Nora, please come here", List.of("One", "Two", "Three", "Four", "Five", "Nora")));
    }

    @Test
    void ignoresDisplayColorCodesWhenMatchingAName() {
        assertEquals(1, ChatAddressee.select("Hello Eloise", List.of("Mira", "§aEloise")));
    }
}
