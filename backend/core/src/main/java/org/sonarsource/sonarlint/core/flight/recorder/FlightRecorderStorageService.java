/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.flight.recorder;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.commons.SonarLintUserHome;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class FlightRecorderStorageService {

  public static final String FILE_NAME = "flight-records.txt";
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final Path LOG_FOLDER = SonarLintUserHome.get().resolve("log");
  private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("'flight-recording-session'-dd-MM-yyyy-HH-mm");
  private static final DateTimeFormatter RECORD_HEADER_FORMATTER = DateTimeFormatter.ofPattern("_____dd/MM/yyyy-HH:mm_____");

  private String sessionFolderName;
  private final Map<String, String> sessionInitData = new HashMap<>();

  public void populateSessionInitData(Map<String, String> initData) {
    sessionInitData.putAll(initData);
  }

  public void appendData(Clock clock, Map<String, String> data) {
    var dateTime = LocalDateTime.now(clock);
    try {
      var filePath = getRecordFile(dateTime);
      var isEmptyFile = Files.size(filePath) == 0;
      Map<String, String> records = data;
      if (isEmptyFile) {
        records = populateWithInitialData(data);
      }
      var recordsWithHeader = getRecordsWithHeader(records, dateTime);
      Files.write(filePath, recordsWithHeader, StandardOpenOption.APPEND);
    } catch (IOException e) {
      LOG.error("Failed to write to a flight recorder file.", e);
    }
  }

  @NotNull
  private HashMap<String, String> populateWithInitialData(Map<String, String> data) {
    var populated = new HashMap<>(sessionInitData);
    populated.putAll(data);
    return populated;
  }

  @NotNull
  private static List<String> getRecordsWithHeader(Map<String, String> data, LocalDateTime dateTime) {
    var records = data.entrySet()
      .stream()
      .map(entry -> entry.getKey() + "=" + entry.getValue())
      .toArray(String[]::new);
    return Lists.asList(RECORD_HEADER_FORMATTER.format(dateTime), records);
  }

  @NotNull
  private Path getRecordFile(LocalDateTime dateTime) throws IOException {
    if (sessionFolderName == null) {
      sessionFolderName = FILE_NAME_FORMATTER.format(dateTime);
    }
    if (!Files.exists(getFilePath())) {
      sessionFolderName = FILE_NAME_FORMATTER.format(dateTime);
      Files.createDirectories(LOG_FOLDER.resolve(sessionFolderName));
      Files.createFile(getFilePath());
    }
    return getFilePath();
  }

  @NotNull
  private Path getFilePath() {
    return LOG_FOLDER.resolve(sessionFolderName).resolve(FILE_NAME);
  }
}
