package org.biopulse;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import java.time.*;import java.util.*;import org.biopulse.domain.*;import org.biopulse.alerts.*;
class AlertEvaluatorTest {
 Instant t=Instant.parse("2026-01-01T00:00:00Z");org.biopulse.domain.AlertRule rule=new org.biopulse.domain.AlertRule(1,1,MeasurementCode.HR,null,100d,20,15,AlertSeverity.WARNING,true);
 Measurement m(int s,Double v,QualityFlag q){return new Measurement(s,"e"+s,1,1,MeasurementCode.HR,v,"bpm",t.plusSeconds(s),t,1,null,q);}
 @Test void thresholdRequiresPersistentEvidence(){var e=new PersistentThresholdEvaluator(new ThresholdRule());assertNull(e.violationStart(List.of(m(0,110d,QualityFlag.GOOD),m(10,110d,QualityFlag.GOOD)),rule,t.plusSeconds(10)));assertEquals(t,e.violationStart(List.of(m(0,110d,QualityFlag.GOOD),m(10,110d,QualityFlag.GOOD),m(20,110d,QualityFlag.GOOD)),rule,t.plusSeconds(20)));}
 @Test void gapAndMissingResetPersistence(){var e=new PersistentThresholdEvaluator(new ThresholdRule());assertNull(e.violationStart(List.of(m(0,110d,QualityFlag.GOOD),m(40,110d,QualityFlag.GOOD)),rule,t.plusSeconds(40)));assertNull(e.violationStart(List.of(m(0,110d,QualityFlag.GOOD),m(10,null,QualityFlag.MISSING),m(20,110d,QualityFlag.GOOD)),rule,t.plusSeconds(20)));assertFalse(new ThresholdRule().violated(m(1,100d,QualityFlag.GOOD),rule));}
}