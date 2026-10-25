package org.biopulse.dto;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.biopulse.domain.*;

public final class Requests {
  private Requests() {}

  public record DeviceStatus(@NotNull Boolean online) {}

  public record PatientCreate(
      @NotBlank @Size(max = 40) String code,
      @NotBlank @Size(max = 100) String displayName,
      @Past LocalDate dateOfBirth) {}

  public record AdmissionCreate(@Positive long patientId, @Positive long bedId) {}

  public record DeviceCreate(
      @NotBlank @Size(max = 80) String serial,
      @NotBlank @Pattern(regexp = "ECG|OXIMETER|RESPIRATORY|NIBP|TEMPERATURE|MULTI")
          String adapterType) {}

  public record AssignmentCreate(@Positive long deviceId, @Positive long admissionId) {}

  public record Observation(
      @NotBlank @Size(max = 100) String eventId,
      @Positive long admissionId,
      @Positive long deviceId,
      @NotNull MeasurementCode metric,
      Double value,
      @NotBlank String unit,
      @NotNull Instant observedAt,
      @Positive long sourceId,
      Long recordingId,
      @NotNull QualityFlag quality) {}

  public record Waveform(
      @NotBlank @Size(max = 100) String eventId,
      @Positive long admissionId,
      @Positive long deviceId,
      @NotNull MeasurementCode metric,
      @NotBlank String unit,
      @NotNull Instant observedAt,
      @DecimalMin("0.1") @DecimalMax("2000") double sampleRateHz,
      @NotEmpty @Size(max = 4096) List<Double> samples,
      @Positive long sourceId,
      Long recordingId,
      @NotNull QualityFlag quality) {}

  public record RuleCreate(
      @Positive long admissionId,
      @NotNull MeasurementCode metric,
      Double lowerBound,
      Double upperBound,
      @Min(0) @Max(86400) int persistenceSeconds,
      @Min(1) @Max(3600) int maxGapSeconds,
      @NotNull AlertSeverity severity) {}

  public record Transition(@Min(0) long expectedVersion) {}

  public record SimulationCreate(
      @Positive long admissionId,
      @Positive long recordingId,
      @DecimalMin("0.1") @DecimalMax("16") double speed,
      ScenarioSpec scenario) {}

  public record ReportCreate(
      Long admissionId, Long wardId, @NotNull Instant from, @NotNull Instant to) {}

  public record Login(@NotBlank String username, @NotBlank String password) {}

  public record SourceCreate(
      @NotBlank @Size(max = 100) String name,
      @NotNull SourceType type,
      @NotBlank @Size(max = 500) String provenance) {}

  public record RecordingCreate(
      @Positive long sourceId,
      @NotBlank String name,
      @NotNull Instant startAt,
      @NotNull Instant endAt) {}
}
