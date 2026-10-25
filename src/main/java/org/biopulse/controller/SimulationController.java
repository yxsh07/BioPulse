package org.biopulse.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.biopulse.domain.SimulationSession;
import org.biopulse.dto.Page;
import org.biopulse.dto.Requests.SimulationCreate;
import org.biopulse.service.SimulationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulation-sessions")
@Validated
public class SimulationController {
  private final SimulationService service;

  public SimulationController(SimulationService service) {
    this.service = service;
  }

  @GetMapping
  public Page<SimulationSession> list(
      @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
      @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
      @RequestParam(required = false) @Positive Long admissionId) {
    return Page.of(service.list(size + 1, page * size, admissionId), page, size);
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public SimulationSession create(@Valid @RequestBody SimulationCreate r) {
    return service.create(r);
  }

  @GetMapping("/{id}")
  public SimulationSession get(@PathVariable long id) {
    return service.get(id);
  }

  @PostMapping("/{id}/{action:start|pause|resume|stop}")
  @PreAuthorize("hasRole('ADMIN')")
  public SimulationSession action(@PathVariable long id, @PathVariable String action) {
    return service.transition(id, action);
  }
}
