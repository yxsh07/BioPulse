package org.biopulse.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.biopulse.domain.MeasurementCode;
import org.biopulse.exception.ApiException;
import org.biopulse.security.AccessPolicy;
import org.biopulse.simulation.bidmc.BidmcImportService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/datasets/bidmc")
public class BidmcDatasetController {
  private final BidmcImportService imports;
  private final AccessPolicy access;
  private final String datasetRoot;

  public BidmcDatasetController(
      BidmcImportService imports,
      AccessPolicy access,
      @Value("${BIOPULSE_BIDMC_DATASET_ROOT:}") String datasetRoot) {
    this.imports = imports;
    this.access = access;
    this.datasetRoot = datasetRoot;
  }

  public record ImportRequest(
      @NotBlank @Pattern(regexp = "bidmc_\\d{2}") String recordingId,
      @NotBlank @Pattern(regexp = "II|V|AVR") String ecgLead,
      @Positive long admissionId,
      @NotNull Map<MeasurementCode, @Positive Long> devices) {}

  @PostMapping("/import")
  @PreAuthorize("hasRole('ADMIN')")
  public BidmcImportService.ImportResult importRecording(@Valid @RequestBody ImportRequest request) {
    if (datasetRoot.isBlank()) throw ApiException.bad("BIDMC dataset root is not configured");
    try {
      Path directory = Path.of(datasetRoot).toRealPath();
      if (!Files.isDirectory(directory)) throw ApiException.bad("BIDMC dataset root is not a directory");
      var result =
          imports.importRecording(
              directory,
              request.recordingId(),
              request.ecgLead(),
              request.admissionId(),
              request.devices());
      access.audit("IMPORT_DATASET", "recording:" + result.recordingId());
      return result;
    } catch (IOException invalid) {
      throw ApiException.bad("BIDMC files are unavailable or invalid");
    }
  }
}
