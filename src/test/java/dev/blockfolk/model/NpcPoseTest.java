package dev.blockfolk.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NpcPoseTest {
    @Test
    void legacyAndUnknownPosesLoadAsStanding() {
        assertEquals(NpcPose.STANDING, NpcPose.fromStored(null));
        assertEquals(NpcPose.STANDING, NpcPose.fromStored("unknown-future-pose"));
        assertEquals(NpcPose.CROUCHING, NpcPose.fromStored(" crouching "));
    }

    @Test
    void duplicatingPresetsPreservesEveryPose() {
        NpcDefinition definition = NpcDefinition.create("Guard");
        assertEquals(NpcPose.STANDING, definition.getPose());
        for (NpcPose pose : NpcPose.values()) {
            definition.setPose(pose);
            assertEquals(pose, definition.copyAs("Second Guard").getPose());
        }
    }
}
