/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.sonarapi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

public class MultivalueProperty {

  private static final CSVFormat SONAR_CSV_FORMAT = CSVFormat.DEFAULT.builder()
    .setSkipHeaderRecord(true)
    .setIgnoreEmptyLines(false)
    .setIgnoreSurroundingSpaces(true)
    .build();

  private MultivalueProperty() {
    // prevents instantiation
  }

  public static String[] parseAsCsv(String key, String value) {
    List<String> result = new ArrayList<>();
    try (var csvParser = CSVParser.parse(value, SONAR_CSV_FORMAT)) {
      var records = csvParser.getRecords();
      if (records.isEmpty()) {
        return ArrayUtils.EMPTY_STRING_ARRAY;
      }
      processRecords(result, records);
      return result.stream().filter(StringUtils::isNotEmpty).toArray(String[]::new);
    } catch (IOException e) {
      throw new IllegalStateException("Property: '" + key + "' doesn't contain a valid CSV value: '" + value + "'", e);
    }
  }

  /**
   * In most cases we expect a single record. <br>Having multiple records means the input value was split over multiple lines (this is common in Maven).
   * For example:
   * <pre>
   *   &lt;sonar.exclusions&gt;
   *     src/foo,
   *     src/bar,
   *     src/biz
   *   &lt;sonar.exclusions&gt;
   * </pre>
   * In this case records will be merged to form a single list of items. Last item of a record is appended to first item of next record.
   * <p>
   * This is a very curious case, but we try to preserve line break in the middle of an item:
   * <pre>
   *   &lt;sonar.exclusions&gt;
   *     a
   *     b,
   *     c
   *   &lt;sonar.exclusions&gt;
   * </pre>
   * will produce ['a\nb', 'c']
   */
  private static void processRecords(List<String> result, List<CSVRecord> records) {
    for (CSVRecord csvRecord : records) {
      var it = csvRecord.iterator();
      if (!result.isEmpty()) {
        var next = it.next();
        if (!next.isEmpty()) {
          var lastItemIdx = result.size() - 1;
          var previous = result.get(lastItemIdx);
          if (previous.isEmpty()) {
            result.set(lastItemIdx, next);
          } else {
            result.set(lastItemIdx, previous + "\n" + next);
          }
        }
      }
      it.forEachRemaining(result::add);
    }
  }

}
