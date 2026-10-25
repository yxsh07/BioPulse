package org.biopulse;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.biopulse.domain.*;
import org.biopulse.simulation.bidmc.ScenarioReplay;
import org.junit.jupiter.api.Test;

class ScenarioReplayTest {
  private static final Instant START = Instant.parse("2000-01-01T00:00:00Z");

  private SimulationSession session(ScenarioMode mode, double from, double to, double magnitude) {
    return new SimulationSession(
        9,
        5,
        1,
        2,
        SimulatorState.RUNNING,
        1,
        START,
        START,
        new ScenarioSpec(mode, from, to, magnitude));
  }

  private SourceRecording original() {
    return new SourceRecording(1, 3, "bidmc_01", START, START.plusSeconds(4));
  }

  private SourceRecording output() {
    return new SourceRecording(2, 4, "Replay bidmc_01", START, START.plusSeconds(4));
  }

  private Measurement hr(double value, QualityFlag quality) {
    return new Measurement(
        11,
        "source-hr",
        5,
        7,
        MeasurementCode.HR,
        value,
        "bpm",
        START.plusMillis(500),
        START.plusSeconds(1),
        3,
        1L,
        quality);
  }

  private WaveformChunk waveform() {
    return new WaveformChunk(
        12,
        "source-wave",
        5,
        8,
        MeasurementCode.ECG,
        "mV",
        START,
        START.plusSeconds(2),
        4,
        List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0),
        3,
        1L,
        QualityFlag.GOOD);
  }

  @Test
  void abnormalScenarioChangesNumericButLeavesWaveform() {
    var result =
        new ScenarioReplay()
            .apply(
                session(ScenarioMode.ABNORMAL_DEMO, 0, 2, 10),
                original(),
                output(),
                List.of(hr(80.0, QualityFlag.GOOD)),
                List.of(waveform()));
    assertEquals(90.0, result.measurements().getFirst().value());
    assertEquals(QualityFlag.POOR, result.measurements().getFirst().quality());
    assertEquals(QualityFlag.GOOD, result.waveforms().getFirst().quality());
    assertEquals(4L, result.measurements().getFirst().sourceId());
    assertEquals(2L, result.measurements().getFirst().recordingId());
  }

  @Test
  void missingScenarioSplitsWaveformAndClearsNumeric() {
    var result =
        new ScenarioReplay()
            .apply(
                session(ScenarioMode.MISSING_DATA, 0.5, 1.5, 0),
                original(),
                output(),
                List.of(hr(80.0, QualityFlag.GOOD)),
                List.of(waveform()));
    assertNull(result.measurements().getFirst().value());
    assertEquals(QualityFlag.MISSING, result.measurements().getFirst().quality());
    assertEquals(2, result.waveforms().size());
    assertNotEquals(result.waveforms().get(0).eventId(), result.waveforms().get(1).eventId());
    assertEquals(List.of(1.0, 2.0), result.waveforms().get(0).samples());
    assertEquals(List.of(7.0, 8.0), result.waveforms().get(1).samples());
  }

  @Test
  void noisyScenarioAddsQualityAndKeepsNumeric() {
    var result =
        new ScenarioReplay()
            .apply(
                session(ScenarioMode.NOISY_SIGNAL, 0, 2, 20),
                original(),
                output(),
                List.of(hr(80.0, QualityFlag.GOOD)),
                List.of(waveform()));
    assertEquals(80.0, result.measurements().getFirst().value());
    assertEquals(QualityFlag.POOR, result.waveforms().getFirst().quality());
    assertNotEquals(2.0, result.waveforms().getFirst().samples().get(1));
  }
}
