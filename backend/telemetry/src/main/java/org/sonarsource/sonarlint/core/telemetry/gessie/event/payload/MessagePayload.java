package org.sonarsource.sonarlint.core.telemetry.gessie.event.payload;

public record MessagePayload(
  String message,
  String trigger
) {
}
