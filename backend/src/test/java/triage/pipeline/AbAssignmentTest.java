package triage.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

class AbAssignmentTest {

    @Test
    void shareOfConsecutiveIdsMatchesTheConfiguredShare() {
        long laya = LongStream.rangeClosed(1, 1000).filter(id -> TriagePipeline.inLayaArm(id, 0.3)).count();
        assertThat(laya).isBetween(270L, 330L);
    }

    @Test
    void assignmentIsStableAndRespectsTheBounds() {
        assertThat(TriagePipeline.inLayaArm(42, 0.5)).isEqualTo(TriagePipeline.inLayaArm(42, 0.5));
        assertThat(LongStream.rangeClosed(1, 200).noneMatch(id -> TriagePipeline.inLayaArm(id, 0.0))).isTrue();
        assertThat(LongStream.rangeClosed(1, 200).allMatch(id -> TriagePipeline.inLayaArm(id, 1.0))).isTrue();
    }
}
