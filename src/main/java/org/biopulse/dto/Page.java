package org.biopulse.dto;

import java.util.List;

public record Page<T>(List<T> items, int page, int size, boolean hasNext) {
  public static <T> Page<T> of(List<T> rows, int page, int size) {
    return new Page<>(
        List.copyOf(rows.subList(0, Math.min(size, rows.size()))), page, size, rows.size() > size);
  }
}
