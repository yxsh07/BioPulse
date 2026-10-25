package org.biopulse.controller;

import jakarta.validation.Valid;
import org.biopulse.domain.SimulationSession;
import org.biopulse.dto.Requests.SimulationCreate;
import org.biopulse.service.SimulationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/simulation-sessions")
public class SimulationController {
  private final SimulationService service;

  public SimulationController(SimulationService service) {
    this.service = service;
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
