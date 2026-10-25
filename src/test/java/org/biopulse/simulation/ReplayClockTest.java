package org.biopulse.simulation;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ReplayClockTest {
  @Test void pauseResumeAndCompletionUseMonotonicElapsedTime() {
    var monotonic = new AtomicLong(100);
    var start = Instant.parse("2020-01-01T00:00:00Z");
    var clock = new ReplayClock(start, start.plusSeconds(10), 2, monotonic::get);
    clock.start();
    monotonic.addAndGet(1_250_000_000L);
    assertEquals(start.plusMillis(2500), clock.cursor());
    clock.pause();
    monotonic.addAndGet(20_000_000_000L);
    assertEquals(start.plusMillis(2500), clock.cursor());
    clock.start();
    monotonic.addAndGet(3_750_000_000L);
    assertEquals(start.plusSeconds(10), clock.cursor());
    assertTrue(clock.completed());
    assertThrows(IllegalStateException.class, () -> {clock.pause();clock.start();});
  }

  @Test void rejectsUnsupportedSpeed() {
    var start = Instant.EPOCH;
    assertThrows(IllegalArgumentException.class,
        () -> new ReplayClock(start, start.plusSeconds(5), 3, () -> 0));
  }

  @Test void fiveClocksKeepIndependentPatientPositions() {
    var monotonic = new AtomicLong(0);
    var start = Instant.EPOCH;
    var clocks = new ArrayList<ReplayClock>();
    for (double speed : new double[] {0.5, 1, 2, 5, 10}) {
      var clock = new ReplayClock(start, start.plusSeconds(30), speed, monotonic::get);
      clock.start();
      clocks.add(clock);
    }
    monotonic.addAndGet(1_000_000_000L);
    assertEquals(start.plusMillis(500), clocks.get(0).cursor());
    assertEquals(start.plusSeconds(10), clocks.get(4).cursor());
    clocks.get(2).pause();
    monotonic.addAndGet(1_000_000_000L);
    assertEquals(start.plusSeconds(2), clocks.get(2).cursor());
    assertEquals(start.plusSeconds(20), clocks.get(4).cursor());
  }
}
