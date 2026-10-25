package org.biopulse.controller;
import org.biopulse.domain.Report;import org.biopulse.dao.ReportDao;import org.biopulse.dto.Page;import org.biopulse.dto.Requests.ReportCreate;import org.biopulse.service.ReportService;import org.biopulse.security.AccessPolicy;import org.springframework.web.bind.annotation.*;import org.springframework.http.*;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import org.springframework.validation.annotation.Validated;
@RestController @RequestMapping("/api/v1/reports") @Validated public class ReportController {
 private final ReportDao dao;private final ReportService service;private final AccessPolicy access;public ReportController(ReportDao dao,ReportService service,AccessPolicy access){this.dao=dao;this.service=service;this.access=access;}
 @GetMapping public Page<Report> list(@RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="25") @Min(1) @Max(100) int size){return Page.of(dao.list(access.actor(),access.admin(),size+1,page*size),page,size);}
 @PostMapping public Report create(@Valid @RequestBody ReportCreate r){return service.create(r);}
 @GetMapping("/{id}") public Report get(@PathVariable long id){return service.get(id);}
 @GetMapping("/{id}/download") public ResponseEntity<String> download(@PathVariable long id){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=biopulse-report-"+id+".csv").contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(service.download(id));}
}