package org.biopulse.reporting;
import java.time.Instant;import java.util.List;
public interface ReportGenerator {String generate(List<Long> admissions,Instant from,Instant to);String contentType();}
