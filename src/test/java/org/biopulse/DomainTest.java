package org.biopulse;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.biopulse.domain.*;
import org.junit.jupiter.api.Test;

class DomainTest {
  @Test
  void capabilitiesAreStronglyTyped() {
    var f = new DeviceAdapterFactory();
    assertTrue(f.create("OXIMETER").supports(MeasurementCode.SPO2));
    assertFalse(f.create("ECG").supports(MeasurementCode.TEMP));
    assertEquals("mmHg", MeasurementCode.SBP.unit());
    assertTrue(MeasurementCode.ECG.waveform());
  }

  @Test
  void waveformDefensivelyCopiesSamples() {
    var samples = new ArrayList<>(List.of(1d, 2d));
    var c =
        new WaveformChunk(
            1,
            "e",
            1,
            1,
            MeasurementCode.ECG,
            "mV",
            java.time.Instant.EPOCH,
            java.time.Instant.EPOCH,
            125,
            samples,
            1,
            null,
            QualityFlag.GOOD);
    samples.clear();
    assertEquals(2, c.samples().size());
    assertThrows(UnsupportedOperationException.class, () -> c.samples().add(3d));
  }
}
