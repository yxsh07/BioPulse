package org.biopulse;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.biopulse.domain.MeasurementCode;
import org.biopulse.domain.QualityFlag;
import org.biopulse.simulation.bidmc.BidmcRecording;
import org.biopulse.domain.ScenarioMode;
import org.biopulse.simulation.bidmc.ScenarioTransformer;
import org.junit.jupiter.api.Test;

class ScenarioTransformerTest {
  private BidmcRecording source() {
    return new BidmcRecording("bidmc_01", "s01182", "II", Map.of(),
        List.of(new BidmcRecording.WaveformFrame(MeasurementCode.ECG,"II","mV",0,2,
            List.of(1.0,2.0,3.0,4.0),QualityFlag.GOOD)),
        List.of(new BidmcRecording.NumericObservation(MeasurementCode.HR,"HR",0,80.0,QualityFlag.GOOD),
            new BidmcRecording.NumericObservation(MeasurementCode.HR,"HR",1,81.0,QualityFlag.GOOD)),
        List.of(),2);
  }

  @Test void missingWindowLeavesGapAndMissingNumeric() {
    var result=new ScenarioTransformer().apply(source(),ScenarioMode.MISSING_DATA,0.5,1.5,0);
    assertTrue(result.synthetic());
    assertEquals(2,result.recording().waveforms().stream().mapToInt(f->f.samples().size()).sum());
    assertNull(result.recording().numerics().get(1).value());
    assertEquals(QualityFlag.MISSING,result.recording().numerics().get(1).quality());
    assertTrue(result.modificationLog().getFirst().startsWith("SYNTHETIC"));
  }

  @Test void abnormalValuesAreLabeledAndSourceRemainsUntouched() {
    var source=source();
    var result=new ScenarioTransformer().apply(source,ScenarioMode.ABNORMAL_DEMO,0,2,20);
    assertEquals(100,result.recording().numerics().getFirst().value());
    assertEquals(QualityFlag.POOR,result.recording().numerics().getFirst().quality());
    assertEquals(80,source.numerics().getFirst().value());
  }
}
