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

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {

  @Override
  public void write(JsonWriter jsonWriter, LocalDateTime localDateTime) throws IOException {
    jsonWriter.beginObject()
      .name("date");
    new LocalDateAdapter().nullSafe().write(jsonWriter, localDateTime.toLocalDate());
    jsonWriter.name("time").beginObject()
        .name("hour").value(localDateTime.getHour())
        .name("minute").value(localDateTime.getMinute())
        .name("second").value(localDateTime.getSecond())
        .name("nano").value(localDateTime.getNano())
      .endObject()
    .endObject();
  }

  @Override
  public LocalDateTime read(JsonReader jsonReader) throws IOException {
    LocalDate localDate = null;
    LocalTime localTime = null;
    jsonReader.beginObject();
    while(jsonReader.hasNext()) {
      switch(jsonReader.nextName()) {
        case "date":
          localDate = new LocalDateAdapter().read(jsonReader);
          break;
        case "time":
          localTime = readTime(jsonReader);
          break;
        default:
          break;
      }
    }
    jsonReader.endObject();
    if (localDate == null || localTime == null) {
      throw new IllegalStateException("Unable to parse LocalDateTime");
    }
    return LocalDateTime.of(localDate, localTime);
  }

  private static LocalTime readTime(JsonReader jsonReader) throws IOException {
    var hour = 0;
    var minute = 0;
    var second = 0;
    var nano = 0;
    jsonReader.beginObject();
    while(jsonReader.hasNext()) {
      switch(jsonReader.nextName()) {
        case "hour":
          hour = jsonReader.nextInt();
          break;
        case "minute":
          minute = jsonReader.nextInt();
          break;
        case "second":
          second = jsonReader.nextInt();
          break;
        case "nano":
          nano = jsonReader.nextInt();
          break;
        default:
          break;
      }
    }
    jsonReader.endObject();
    return LocalTime.of(hour, minute, second, nano);
  }
}
