package org.biopulse.simulation;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.function.LongSupplier;

/** Monotonic source cursor; pause/resume re-anchors without accumulating scheduled-tick drift. */
public final class ReplayClock {
  private static final Set<Double> SPEEDS = Set.of(0.5, 1.0, 2.0, 5.0, 10.0);
  private final LongSupplier nanos;
  private final Instant end;
  private final double speed;
  private Instant anchor;
  private long startedNanos;
  private boolean running;

  public ReplayClock(Instant start, Instant end, double speed) {
    this(start, end, speed, System::nanoTime);
  }

  ReplayClock(Instant start, Instant end, double speed, LongSupplier nanos) {
    if (start == null || end == null || !start.isBefore(end))
      throw new IllegalArgumentException("Invalid replay interval");
    if (!SPEEDS.contains(speed)) throw new IllegalArgumentException("Unsupported replay speed");
    this.anchor = start;
    this.end = end;
    this.speed = speed;
    this.nanos = nanos;
  }

  public synchronized void start() {
    if (running || !anchor.isBefore(end)) throw new IllegalStateException("Cannot start replay clock");
    startedNanos = nanos.getAsLong();
    running = true;
  }

  public synchronized void pause() {
    if (!running) throw new IllegalStateException("Replay clock is not running");
    anchor = cursor();
    running = false;
  }

  public synchronized Instant cursor() {
    if (!running) return anchor;
    long elapsed = Math.max(0L, nanos.getAsLong() - startedNanos);
    Instant current = anchor.plusNanos(Math.round(elapsed * speed));
    return current.isAfter(end) ? end : current;
  }

  public synchronized boolean completed() { return !cursor().isBefore(end); }

  public synchronized Duration elapsedSourceTime(Instant start) {
    return Duration.between(start, cursor());
  }
}
