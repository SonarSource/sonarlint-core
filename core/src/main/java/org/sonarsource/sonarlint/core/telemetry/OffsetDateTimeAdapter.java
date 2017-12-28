package org.sonarsource.sonarlint.core.telemetry;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

public class OffsetDateTimeAdapter implements JsonSerializer<OffsetDateTime>, JsonDeserializer<OffsetDateTime> {

  private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
    .appendInstant(3)
    .appendOffsetId()
    .toFormatter();

  @Override
  public JsonElement serialize(OffsetDateTime src, Type typeOfSrc, JsonSerializationContext context) {
    return new JsonPrimitive(FORMATTER.format(src));
  }

  @Override
  public OffsetDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
    JsonPrimitive jsonPrimitive = json.getAsJsonPrimitive();
    return OffsetDateTime.parse(jsonPrimitive.getAsString(), FORMATTER);
  }
}
