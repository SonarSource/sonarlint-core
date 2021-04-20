/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.time.LocalDateTime;

public class LocalDateTimeAdapter implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
  @Override
  public LocalDateTime deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
    JsonObject localDateTimeObject = jsonElement.getAsJsonObject();
    JsonObject date = localDateTimeObject.get("date").getAsJsonObject();
    JsonObject time = localDateTimeObject.get("time").getAsJsonObject();
    return LocalDateTime.of(
      date.get("year").getAsInt(),
      date.get("month").getAsInt(),
      date.get("day").getAsInt(),
      time.get("hour").getAsInt(),
      time.get("minute").getAsInt(),
      time.get("second").getAsInt(),
      time.get("nano").getAsInt()
    );
  }

  @Override
  public JsonElement serialize(LocalDateTime localDateTime, Type type, JsonSerializationContext jsonSerializationContext) {
    JsonObject date = new JsonObject();
    date.add("year", new JsonPrimitive(localDateTime.getYear()));
    date.add("month", new JsonPrimitive(localDateTime.getMonthValue()));
    date.add("day", new JsonPrimitive(localDateTime.getDayOfMonth()));
    JsonObject time = new JsonObject();
    time.add("hour", new JsonPrimitive(localDateTime.getHour()));
    time.add("minute", new JsonPrimitive(localDateTime.getMinute()));
    time.add("second", new JsonPrimitive(localDateTime.getSecond()));
    time.add("nano", new JsonPrimitive(localDateTime.getNano()));
    JsonObject dateTime = new JsonObject();
    dateTime.add("date", date);
    dateTime.add("time", time);
    return dateTime;
  }
}
