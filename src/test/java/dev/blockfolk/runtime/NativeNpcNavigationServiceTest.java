package dev.blockfolk.runtime;

import org.junit.jupiter.api.Test;

import dev.blockfolk.model.WalkingSpeed;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeNpcNavigationServiceTest {

    @Test
    void normalWalkingUsesTheNavigatorsNormalMovementSpeed() {
        assertEquals(0.25, NativeNpcNavigationService.navigatorMovementSpeed(WalkingSpeed.NORMAL));
    }

    @Test
    void otherWalkingSpeedsRemainProportionalToTheirConfiguredBlocksPerSecond() {
        double expectedFast = 0.25 * WalkingSpeed.FAST.blocksPerSecond() / WalkingSpeed.NORMAL.blocksPerSecond();

        assertEquals(expectedFast, NativeNpcNavigationService.navigatorMovementSpeed(WalkingSpeed.FAST));
    }
}
