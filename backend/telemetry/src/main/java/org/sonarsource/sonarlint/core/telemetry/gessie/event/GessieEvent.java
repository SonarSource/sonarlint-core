package org.sonarsource.sonarlint.core.telemetry.gessie.event;

public record GessieEvent(
  GessieMetadata metadata,
  Object eventPayload
) {
}
