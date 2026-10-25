package org.biopulse.simulation.bidmc;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.biopulse.domain.MeasurementCode;
import org.biopulse.domain.QualityFlag;
import org.biopulse.simulation.bidmc.BidmcRecording.BreathAnnotation;
import org.biopulse.simulation.bidmc.BidmcRecording.NumericObservation;
import org.biopulse.simulation.bidmc.BidmcRecording.WaveformFrame;
import org.springframework.stereotype.Component;

/** Parses the published bidmc_csv layout by header name, keeping source-relative time. */
@Component
public class BidmcCsvImporter {
  private static final Pattern ID = Pattern.compile("bidmc_\\d{2}");
  private static final double SIGNAL_RATE = 125.0;
  // Published files print signal time to 2 decimal places once it reaches 100 s.
  // The row cadence remains 125 Hz, so accept rounding up to half a printed step.
  private static final double SIGNAL_TIME_TOLERANCE = 0.006;

  public BidmcRecording importDirectory(Path directory, String recordingId, String ecgLead)
      throws IOException {
    if (!ID.matcher(recordingId).matches()) throw new IOException("Invalid BIDMC recording ID");
    if (!List.of("II", "V", "AVR").contains(ecgLead))
      throw new IOException("ECG lead must be II, V or AVR");
    var fix = fixed(directory.resolve(recordingId + "_Fix.txt"), recordingId);
    var waveforms = new ArrayList<WaveformFrame>();
    double duration = signals(directory.resolve(recordingId + "_Signals.csv"), ecgLead, waveforms);
    var numerics = numeric(directory.resolve(recordingId + "_Numerics.csv"), duration);
    var breaths = breaths(directory.resolve(recordingId + "_Breaths.csv"));
    String pseudonym = fix.getOrDefault("MIMIC II matched wdb ID", recordingId);
    return new BidmcRecording(
        recordingId, pseudonym, ecgLead, fix, waveforms, numerics, breaths, duration);
  }

  private Map<String, String> fixed(Path file, String id) throws IOException {
    var lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    if (lines.isEmpty() || !lines.getFirst().trim().equals(id))
      throw new IOException("Fix.txt recording ID mismatch");
    Map<String, String> metadata = new LinkedHashMap<>();
    for (String line : lines.subList(1, lines.size())) {
      int split = line.indexOf(':');
      if (split > 0) metadata.put(line.substring(0, split).trim(), line.substring(split + 1).trim());
    }
    if (!metadata.getOrDefault("Signals sampling frequency", "").equals("125 Hz")
        || !metadata.getOrDefault("Numerics sampling frequency", "").equals("1 Hz"))
      throw new IOException("Unsupported BIDMC sampling frequency");
    return metadata;
  }

  private double signals(Path file, String lead, List<WaveformFrame> frames) throws IOException {
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      var header = header(reader.readLine(), List.of("Time [s]", "RESP", "PLETH", lead));
      var columns = Map.of(
          MeasurementCode.ECG, lead,
          MeasurementCode.PPG, "PLETH",
          MeasurementCode.RESP, "RESP");
      Map<MeasurementCode, List<Double>> pending = new HashMap<>();
      Map<MeasurementCode, Double> starts = new HashMap<>();
      for (var metric : columns.keySet()) pending.put(metric, new ArrayList<>(125));
      String line;
      int row = 0;
      while ((line = reader.readLine()) != null) {
        String[] fields = fields(line, header.size(), file, row + 2);
        double time = number(fields[header.get("Time [s]")], file, row + 2);
        if (Math.abs(time - row / SIGNAL_RATE) > SIGNAL_TIME_TOLERANCE)
          throw new IOException("Signal time misaligned at row " + (row + 2));
        for (var entry : columns.entrySet()) {
          var values = pending.get(entry.getKey());
          String raw = fields[header.get(entry.getValue())].trim();
          if (raw.isEmpty() || raw.equalsIgnoreCase("NaN")) {
            flush(frames, entry.getKey(), entry.getValue(), starts, values);
          } else {
            double value = number(raw, file, row + 2);
            if (values.isEmpty()) starts.put(entry.getKey(), time);
            values.add(value);
            if (values.size() == 125)
              flush(frames, entry.getKey(), entry.getValue(), starts, values);
          }
        }
        row++;
      }
      if (row == 0) throw new IOException("Empty signals CSV");
      for (var entry : columns.entrySet())
        flush(frames, entry.getKey(), entry.getValue(), starts, pending.get(entry.getKey()));
      return row / SIGNAL_RATE;
    }
  }

  private void flush(
      List<WaveformFrame> frames,
      MeasurementCode metric,
      String column,
      Map<MeasurementCode, Double> starts,
      List<Double> values) {
    if (values.isEmpty()) return;
    // ECG lead units are mV in the corresponding published WFDB header. The
    // backend represents PLETH and RESP as arbitrary-unit display channels.
    frames.add(new WaveformFrame(metric, column, metric == MeasurementCode.ECG ? "mV" : "a.u.", starts.get(metric), SIGNAL_RATE,
        List.copyOf(values), QualityFlag.GOOD));
    values.clear();
  }

  private List<NumericObservation> numeric(Path file, double duration) throws IOException {
    var result = new ArrayList<NumericObservation>();
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      var header = header(reader.readLine(), List.of("Time [s]", "HR", "PULSE", "RESP", "SpO2"));
      var columns = Map.of(
          MeasurementCode.HR, "HR",
          MeasurementCode.PULSE, "PULSE",
          MeasurementCode.RR, "RESP",
          MeasurementCode.SPO2, "SpO2");
      String line;
      int row = 0;
      while ((line = reader.readLine()) != null) {
        String[] fields = fields(line, header.size(), file, row + 2);
        double time = number(fields[header.get("Time [s]")], file, row + 2);
        if (Math.abs(time - row) > 0.001 || time >= duration + 0.001)
          throw new IOException("Numeric time misaligned at row " + (row + 2));
        for (var entry : columns.entrySet()) {
          String raw = fields[header.get(entry.getValue())].trim();
          boolean missing = raw.isEmpty() || raw.equalsIgnoreCase("NaN");
          result.add(new NumericObservation(entry.getKey(), entry.getValue(), time,
              missing ? null : number(raw, file, row + 2),
              missing ? QualityFlag.MISSING : QualityFlag.GOOD));
        }
        row++;
      }
      if (row == 0) throw new IOException("Empty numerics CSV");
    }
    return List.copyOf(result);
  }

  private List<BreathAnnotation> breaths(Path file) throws IOException {
    if (!Files.exists(file)) return List.of();
    var result = new ArrayList<BreathAnnotation>();
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      header(reader.readLine(), List.of(
          "breaths ann1 [signal sample no]", "breaths ann2 [signal sample no]"));
      String line;
      while ((line = reader.readLine()) != null) {
        var fields = fields(line, 2, file, result.size() + 2);
        try {
          result.add(new BreathAnnotation(Integer.parseInt(fields[0].trim()),
              Integer.parseInt(fields[1].trim())));
        } catch (NumberFormatException e) {
          throw new IOException("Invalid breath annotation", e);
        }
      }
    }
    return List.copyOf(result);
  }

  private Map<String, Integer> header(String line, List<String> required) throws IOException {
    if (line == null) throw new IOException("Missing CSV header");
    var result = new HashMap<String, Integer>();
    var fields = line.split(",", -1);
    for (int i = 0; i < fields.length; i++) {
      String key = fields[i].trim();
      if (result.put(key, i) != null) throw new IOException("Duplicate CSV column: " + key);
    }
    for (String key : required)
      if (!result.containsKey(key)) throw new IOException("Missing CSV column: " + key);
    return result;
  }

  private String[] fields(String line, int count, Path file, int row) throws IOException {
    var parts = line.split(",", -1);
    if (parts.length != count) throw new IOException(file.getFileName() + " column count at row " + row);
    return parts;
  }

  private double number(String raw, Path file, int row) throws IOException {
    try {
      double value = Double.parseDouble(raw.trim());
      if (Double.isFinite(value)) return value;
    } catch (NumberFormatException ignored) {
    }
    throw new IOException(file.getFileName() + " invalid number at row " + row);
  }
}
