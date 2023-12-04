/*
 * SonarLint Core - Telemetry
 * Copyright (C) 2016-2023 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.sonarlint.core.telemetry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class TelemetryAnalyzerPerformance {
  private static final TreeMap<Integer, String> INTERVALS;
  private int analysisCount;

  static {
    INTERVALS = new TreeMap<>();
    INTERVALS.put(300, "0-300");
    INTERVALS.put(500, "300-500");
    INTERVALS.put(1000, "500-1000");
    INTERVALS.put(2000, "1000-2000");
    INTERVALS.put(4000, "2000-4000");
    INTERVALS.put(Integer.MAX_VALUE, "4000+");
  }

  private Map<String, Integer> frequencies;

  public TelemetryAnalyzerPerformance() {
    frequencies = new LinkedHashMap<>();
    INTERVALS.forEach((k, v) -> frequencies.put(v, 0));
  }

  public void registerAnalysis(int analysisTimeMs) {
    var entry = INTERVALS.higherEntry(analysisTimeMs);
    if (entry != null) {
      frequencies.compute(entry.getValue(), (k, v) -> v != null ? (v + 1) : 1);
      analysisCount++;
    }
  }

  public Map<String, Integer> frequencies() {
    return frequencies;
  }

  public int analysisCount() {
    return analysisCount;
  }

}
