package org.biopulse.simulation.bidmc;

import java.util.List;
import java.util.Map;
import org.biopulse.domain.MeasurementCode;
import org.biopulse.domain.QualityFlag;

/** BIDMC CSV values are relative to recording start; no acquisition wall time is asserted. */
public record BidmcRecording(
    String recordingId,
    String subjectPseudonym,
    String ecgLead,
    Map<String, String> fixedMetadata,
    List<WaveformFrame> waveforms,
    List<NumericObservation> numerics,
    List<BreathAnnotation> breaths,
    double durationSeconds) {
  public BidmcRecording {
    fixedMetadata = Map.copyOf(fixedMetadata);
    waveforms = List.copyOf(waveforms);
    numerics = List.copyOf(numerics);
    breaths = List.copyOf(breaths);
  }

  public record WaveformFrame(
      MeasurementCode metric,
      String originalColumn,
      String sourceUnit,
      double offsetSeconds,
      double sampleRateHz,
      List<Double> samples,
      QualityFlag quality) {
    public WaveformFrame { samples = List.copyOf(samples); }
  }

  public record NumericObservation(
      MeasurementCode metric,
      String originalColumn,
      double offsetSeconds,
      Double value,
      QualityFlag quality) {}

  public record BreathAnnotation(int firstAnnotatorSample, int secondAnnotatorSample) {}
}
