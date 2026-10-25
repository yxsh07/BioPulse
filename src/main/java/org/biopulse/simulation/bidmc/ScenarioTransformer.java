package org.biopulse.simulation.bidmc;

import java.util.ArrayList;
import java.util.List;
import org.biopulse.domain.MeasurementCode;
import org.biopulse.domain.QualityFlag;
import org.biopulse.simulation.bidmc.BidmcRecording.NumericObservation;
import org.biopulse.simulation.bidmc.BidmcRecording.WaveformFrame;

/** Deterministic, explicitly synthetic replay variants. Source imports remain untouched. */
public final class ScenarioTransformer {
  public Result apply(BidmcRecording original, ScenarioMode mode, double fromSeconds,
      double toSeconds, double magnitude) {
    if (original == null || mode == null || fromSeconds < 0 || toSeconds <= fromSeconds
        || toSeconds > original.durationSeconds() || !Double.isFinite(magnitude)
        || magnitude < 0 || magnitude > 100)
      throw new IllegalArgumentException("Invalid scenario range or magnitude");
    if (mode == ScenarioMode.NORMAL) return new Result(original, mode, false, 0, List.of());
    List<WaveformFrame> frames = new ArrayList<>();
    List<NumericObservation> numerics = new ArrayList<>();
    int affected = 0;
    for (var frame : original.waveforms()) {
      double end = frame.offsetSeconds() + frame.samples().size() / frame.sampleRateHz();
      if (end <= fromSeconds || frame.offsetSeconds() >= toSeconds
          || mode == ScenarioMode.ABNORMAL_DEMO || mode == ScenarioMode.DELAYED_TELEMETRY) {
        frames.add(frame);
        continue;
      }
      List<Double> segment = new ArrayList<>();
      double segmentStart = -1;
      QualityFlag segmentQuality = frame.quality();
      for (int i = 0; i < frame.samples().size(); i++) {
        double t = frame.offsetSeconds() + i / frame.sampleRateHz();
        boolean inRange = t >= fromSeconds && t < toSeconds;
        if (inRange && (mode == ScenarioMode.SENSOR_OFFLINE || mode == ScenarioMode.MISSING_DATA)) {
          flush(frames, frame, segmentStart, segment, segmentQuality);
          segmentStart = -1;
          affected++;
          continue;
        }
        QualityFlag nextQuality = inRange && mode == ScenarioMode.NOISY_SIGNAL
            ? QualityFlag.POOR : frame.quality();
        if (!segment.isEmpty() && nextQuality != segmentQuality) {
          flush(frames, frame, segmentStart, segment, segmentQuality);
          segmentStart = -1;
        }
        if (segment.isEmpty()) segmentStart = t;
        segmentQuality = nextQuality;
        double value = frame.samples().get(i);
        if (inRange && mode == ScenarioMode.NOISY_SIGNAL) {
          value += magnitude / 100.0 * Math.sin(2 * Math.PI * i * 17 / frame.sampleRateHz());
          affected++;
        }
        segment.add(value);
      }
      flush(frames, frame, segmentStart, segment, segmentQuality);
    }
    for (var value : original.numerics()) {
      boolean inRange = value.offsetSeconds() >= fromSeconds && value.offsetSeconds() < toSeconds;
      if (!inRange || mode == ScenarioMode.NOISY_SIGNAL || mode == ScenarioMode.DELAYED_TELEMETRY) {
        numerics.add(value);
      } else if (mode == ScenarioMode.SENSOR_OFFLINE || mode == ScenarioMode.MISSING_DATA) {
        numerics.add(new NumericObservation(value.metric(), value.originalColumn(),
            value.offsetSeconds(), null, QualityFlag.MISSING));
        affected++;
      } else {
        Double changed = value.value();
        if (changed != null) {
          changed = value.metric() == MeasurementCode.SPO2
              ? Math.max(0, changed - magnitude) : changed + magnitude;
          affected++;
        }
        numerics.add(new NumericObservation(value.metric(), value.originalColumn(),
            value.offsetSeconds(), changed,
            changed == null ? QualityFlag.MISSING : QualityFlag.POOR));
      }
    }
    int delay = mode == ScenarioMode.DELAYED_TELEMETRY ? (int) Math.round(magnitude) : 0;
    String log = "SYNTHETIC " + mode + " recording=" + original.recordingId()
        + " range=[" + fromSeconds + "," + toSeconds + ")s magnitude=" + magnitude
        + " affected=" + affected + " delaySeconds=" + delay;
    var changed = new BidmcRecording(original.recordingId(), original.subjectPseudonym(),
        original.ecgLead(), original.fixedMetadata(), frames, numerics, original.breaths(),
        original.durationSeconds());
    return new Result(changed, mode, true, delay, List.of(log));
  }

  private void flush(List<WaveformFrame> out, WaveformFrame original, double start,
      List<Double> samples, QualityFlag quality) {
    if (!samples.isEmpty()) {
      out.add(new WaveformFrame(original.metric(), original.originalColumn(),
          original.sourceUnit(), start, original.sampleRateHz(), samples, quality));
      samples.clear();
    }
  }

  public record Result(BidmcRecording recording, ScenarioMode mode, boolean synthetic,
      int telemetryDelaySeconds, List<String> modificationLog) {
    public Result { modificationLog = List.copyOf(modificationLog); }
  }
}
