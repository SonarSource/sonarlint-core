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
import java.time.LocalDate;

public class LocalDateAdapter implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
  @Override
  public LocalDate deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
    JsonObject localDateObject = jsonElement.getAsJsonObject();
    return LocalDate.of(
      localDateObject.get("year").getAsInt(),
      localDateObject.get("month").getAsInt(),
      localDateObject.get("day").getAsInt()
    );
  }

  @Override
  public JsonElement serialize(LocalDate localDate, Type type, JsonSerializationContext jsonSerializationContext) {
    JsonObject localDateObject = new JsonObject();
    localDateObject.add("year", new JsonPrimitive(localDate.getYear()));
    localDateObject.add("month", new JsonPrimitive(localDate.getMonthValue()));
    localDateObject.add("day", new JsonPrimitive(localDate.getDayOfMonth()));
    return localDateObject;
  }
}
