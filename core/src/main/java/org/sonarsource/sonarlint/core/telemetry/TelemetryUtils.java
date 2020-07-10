/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2020 SonarSource SA
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

class TelemetryUtils {

  private TelemetryUtils() {
    // utility class, forbidden constructor
  }

  static String getLanguage(@Nullable String fileExtension) {
    if (fileExtension == null) {
      return "others";
    }
    String fileExtensionLow = fileExtension.toLowerCase(Locale.US);
    switch (fileExtensionLow) {
      case "cpp":
      case "c":
      case "h":
      case "m":
      case "cc":
      case "cxx":
      case "c++":
      case "hh":
      case "hpp":
      case "hxx":
      case "h++":
      case "ipp":
        return "cfamily";
      case "java":
      case "php":
        return fileExtensionLow;
      case "ts":
        return "typescript";
      case "js":
        return "javascript";
      case "py":
        return "python";
      default:
        return "others";
    }
  }

  /**
   * Check if "now" is a different day than the reference.
   *
   * @param date reference date
   * @return true if it's a different day than the reference
   */
  static boolean dayChanged(@Nullable LocalDate date) {
    return date == null || !date.equals(LocalDate.now());
  }

  /**
   * Transforms stored information about analyzers performance to payload to be sent to server.
   */
  static TelemetryAnalyzerPerformancePayload[] toPayload(Map<String, TelemetryAnalyzerPerformance> analyzers) {
    return analyzers.entrySet().stream()
      .map(TelemetryUtils::toPayload)
      .toArray(size -> new TelemetryAnalyzerPerformancePayload[size]);
  }

  private static TelemetryAnalyzerPerformancePayload toPayload(Map.Entry<String, TelemetryAnalyzerPerformance> entry) {
    TelemetryAnalyzerPerformance analyzerPerformance = entry.getValue();
    String language = entry.getKey();
    int analysisCount = analyzerPerformance.analysisCount();
    Map<String, BigDecimal> distribution = analyzerPerformance
      .frequencies().entrySet().stream()
      .collect(Collectors.toMap(Map.Entry::getKey, e -> {
        if (analysisCount == 0) {
          return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(100)
          .multiply(BigDecimal.valueOf(e.getValue()))
          .divide(BigDecimal.valueOf(analysisCount), 2, RoundingMode.HALF_EVEN);
      }, throwingMerger(), LinkedHashMap::new));

    return new TelemetryAnalyzerPerformancePayload(language, distribution);
  }

  /**
   * Check if "now" is a different day than the reference, and some hours have elapsed.
   *
   * @param dateTime reference date
   * @param hours minimum hours that must have elapsed
   * @return true if it's a different day than the reference and at least hours have elapsed
   */
  static boolean dayChanged(@Nullable LocalDateTime dateTime, long hours) {
    return dateTime == null ||
      (!LocalDate.now().equals(dateTime.toLocalDate())
        && (dateTime.until(LocalDateTime.now(), ChronoUnit.HOURS) >= hours));
  }

  private static <T> BinaryOperator<T> throwingMerger() {
    return (u, v) -> {
      throw new IllegalStateException(String.format("Duplicate key %s", u));
    };
  }
}
