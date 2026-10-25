package org.biopulse.simulation.bidmc;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.biopulse.dao.MeasurementDao;
import org.biopulse.dao.ProvenanceDao;
import org.biopulse.dao.RegistryDao;
import org.biopulse.domain.AdmissionStatus;
import org.biopulse.domain.DeviceAdapterFactory;
import org.biopulse.domain.Measurement;
import org.biopulse.domain.MeasurementCode;
import org.biopulse.domain.SourceType;
import org.biopulse.domain.WaveformChunk;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores a historical BIDMC source through shared DAOs; live ingestion occurs during replay. */
@Service
public class BidmcImportService {
  public static final Instant RELATIVE_ANCHOR = Instant.parse("2000-01-01T00:00:00Z");
  private final BidmcCsvImporter importer;
  private final RegistryDao registry;
  private final ProvenanceDao provenance;
  private final MeasurementDao measurements;
  private final DeviceAdapterFactory adapters;

  public BidmcImportService(BidmcCsvImporter importer, RegistryDao registry, ProvenanceDao provenance,
      MeasurementDao measurements, DeviceAdapterFactory adapters) {
    this.importer = importer;
    this.registry = registry;
    this.provenance = provenance;
    this.measurements = measurements;
    this.adapters = adapters;
  }

  /** Caller must be an authorized administrator; device IDs must be assigned to this admission. */
  @Transactional
  public ImportResult importRecording(Path directory, String id, String ecgLead,
      long admissionId, Map<MeasurementCode, Long> devices) throws IOException {
    BidmcRecording data = importer.importDirectory(directory, id, ecgLead);
    if (registry.admission(admissionId).orElseThrow().status() != AdmissionStatus.ACTIVE)
      throw new IllegalArgumentException("Import requires an active admission");
    for (var frame : data.waveforms()) requireDevice(admissionId, devices, frame.metric());
    for (var value : data.numerics()) requireDevice(admissionId, devices, value.metric());
    String provenance = "BIDMC PPG and Respiration Dataset 1.0.0; https://physionet.org/content/bidmc/1.0.0/; "
        + "recording=" + id + "; original subject=" + data.subjectPseudonym()
        + "; ECG lead=" + ecgLead + "; signal rate=125 Hz; numeric rate=1 Hz"
        + "; CSV offsets anchored to 2000-01-01 solely for storage; admission association is artificial";
    long sourceId = this.provenance.createSource(new ProvenanceDao.SourceInput(
        "BIDMC " + id, SourceType.DATASET, provenance, "ORIGINAL_RESEARCH", "PhysioNet",
        "BIDMC PPG and Respiration Dataset", "1.0.0",
        "PhysioNet modified data distribution terms", "Pimentel et al., 2016",
        "https://physionet.org/content/bidmc/1.0.0/"));
    long recordingId = this.provenance.createRecording(new ProvenanceDao.RecordingInput(
        sourceId, id, id, data.subjectPseudonym(), id,
        List.of("ECG " + ecgLead, "PLETH", "RESP", "HR", "PULSE", "SpO2", "RR"),
        BigDecimal.valueOf(125), RELATIVE_ANCHOR, at(data.durationSeconds())));
    int waveformCount = 0;
    for (var frame : data.waveforms()) {
      measurements.insertWaveform(new WaveformChunk(0,
          id + ":" + frame.metric() + ":" + Math.round(frame.offsetSeconds() * 125),
          admissionId, devices.get(frame.metric()), frame.metric(), frame.metric().unit(),
          at(frame.offsetSeconds()), Instant.now(), frame.sampleRateHz(), frame.samples(),
          sourceId, recordingId, frame.quality()));
      waveformCount++;
    }
    List<Measurement> batch = new ArrayList<>(500);
    int numericCount = 0;
    for (var value : data.numerics()) {
      batch.add(new Measurement(0,
          id + ":" + value.metric() + ":" + (long) value.offsetSeconds(),
          admissionId, devices.get(value.metric()), value.metric(), value.value(),
          value.metric().unit(), at(value.offsetSeconds()), Instant.now(), sourceId,
          recordingId, value.quality()));
      if (batch.size() == 500) {
        measurements.insertBatch(batch);
        numericCount += batch.size();
        batch.clear();
      }
    }
    if (!batch.isEmpty()) {
      measurements.insertBatch(batch);
      numericCount += batch.size();
    }
    return new ImportResult(sourceId, recordingId, id, data.subjectPseudonym(),
        waveformCount, numericCount, data.durationSeconds());
  }

  private void requireDevice(long admissionId, Map<MeasurementCode, Long> devices,
      MeasurementCode metric) {
    if (devices.get(metric) == null || devices.get(metric) <= 0)
      throw new IllegalArgumentException("Missing assigned device for " + metric);
    long deviceId = devices.get(metric);
    var device = registry.device(deviceId).orElseThrow();
    if (!registry.assigned(admissionId, deviceId, Instant.now())
        || !adapters.create(device.adapterType()).supports(metric))
      throw new IllegalArgumentException("Device is not assigned or does not support " + metric);
  }

  private static Instant at(double seconds) {
    return RELATIVE_ANCHOR.plusNanos(Math.round(seconds * 1_000_000_000L));
  }

  public record ImportResult(long sourceId, long recordingId, String sourceRecordingId,
      String originalSubjectPseudonym, int waveformChunks, int numericObservations,
      double durationSeconds) {}
}
