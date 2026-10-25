package org.biopulse;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.biopulse.domain.MeasurementCode;
import org.biopulse.domain.QualityFlag;
import org.biopulse.simulation.bidmc.BidmcCsvImporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

class BidmcCsvImporterTest {
  @TempDir Path folder;
  private final BidmcCsvImporter importer = new BidmcCsvImporter();

  private void files(boolean missing) throws IOException {
    Files.writeString(folder.resolve("bidmc_01_Fix.txt"),
        "bidmc_01\nSignals sampling frequency: 125 Hz\nNumerics sampling frequency: 1 Hz\n"
            + "MIMIC II matched wdb ID: s01182\n");
    var signal = new StringBuilder("Time [s], RESP, PLETH, V, AVR, II\n");
    for (int i = 0; i < 250; i++)
      signal.append(i / 125.0).append(',').append(i / 1000.0).append(',')
          .append(missing && i == 126 ? "" : i / 500.0)
          .append(",0.1,0.2,").append(i / 250.0).append('\n');
    Files.writeString(folder.resolve("bidmc_01_Signals.csv"), signal);
    Files.writeString(folder.resolve("bidmc_01_Numerics.csv"),
        "Time [s], HR, PULSE, RESP, SpO2\n0,94,93,25,97\n1,,92,26,96\n");
  }

  @Test void preservesSampleTimingAndMissingValues() throws Exception {
    files(true);
    var recording = importer.importDirectory(folder, "bidmc_01", "II");
    assertEquals("s01182", recording.subjectPseudonym());
    assertEquals(2, recording.durationSeconds());
    assertEquals(250, recording.waveforms().stream()
        .filter(f -> f.metric() == MeasurementCode.ECG).mapToInt(f -> f.samples().size()).sum());
    assertEquals(249, recording.waveforms().stream()
        .filter(f -> f.metric() == MeasurementCode.PPG).mapToInt(f -> f.samples().size()).sum());
    assertEquals(1.016, recording.waveforms().stream()
        .filter(f -> f.metric() == MeasurementCode.PPG && f.offsetSeconds() > 1)
        .findFirst().orElseThrow().offsetSeconds(), 1e-9);
    assertEquals(8, recording.numerics().size());
    var missing = recording.numerics().stream()
        .filter(n -> n.metric() == MeasurementCode.HR && n.offsetSeconds() == 1)
        .findFirst().orElseThrow();
    assertNull(missing.value());
    assertEquals(QualityFlag.MISSING, missing.quality());
  }

  @Test void rejectsMismatchedHeaderAndTime() throws Exception {
    files(false);
    var signal = folder.resolve("bidmc_01_Signals.csv");
    Files.writeString(signal, Files.readString(signal).replace("PLETH", "OTHER"));
    assertThrows(IOException.class, () -> importer.importDirectory(folder, "bidmc_01", "II"));
    files(false);
    Files.writeString(signal, Files.readString(signal).replaceFirst("0.008", "0.016"));
    assertThrows(IOException.class, () -> importer.importDirectory(folder, "bidmc_01", "II"));
  }

  @Test void publishedBidmc01CountsWhenDatasetIsAvailable() throws Exception {
    String root = System.getenv("BIDMC_CSV_DIR");
    Assumptions.assumeTrue(root != null && Files.exists(Path.of(root, "bidmc_01_Signals.csv")));
    var recording = importer.importDirectory(Path.of(root), "bidmc_01", "II");
    assertEquals(60001, recording.waveforms().stream()
        .filter(f -> f.metric() == MeasurementCode.ECG).mapToInt(f -> f.samples().size()).sum());
    assertEquals(481 * 4, recording.numerics().size());
    assertEquals(480.008, recording.durationSeconds(), 1e-9);
  }
}
