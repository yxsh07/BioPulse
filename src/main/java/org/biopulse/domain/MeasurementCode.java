package org.biopulse.domain;
public enum MeasurementCode {
 ECG("mV", true), PPG("a.u.", true), RESP("a.u.", true), HR("bpm", false), PULSE("bpm", false), SPO2("%", false), RR("breaths/min", false), SBP("mmHg", false), DBP("mmHg", false), TEMP("Cel", false);
 private final String unit; private final boolean waveform;
 MeasurementCode(String unit, boolean waveform) { this.unit=unit; this.waveform=waveform; }
 public String unit(){return unit;} public boolean waveform(){return waveform;}
}