package org.biopulse.analytics;
import java.time.Instant;import java.util.*;
public interface AnalysisModel {
 record Result(String label,String modelVersion,Map<String,Double> inputFeatures,Double score,Instant inferredAt,String provenance,String status){}
 Result analyze(List<Double> baseline,Double value,String provenance);
}