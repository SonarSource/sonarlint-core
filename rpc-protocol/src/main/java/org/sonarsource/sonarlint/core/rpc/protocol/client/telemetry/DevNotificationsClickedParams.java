package org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry;

public class DevNotificationsClickedParams {
  private final String eventType;

  public DevNotificationsClickedParams(String eventType) {
    this.eventType = eventType;
  }

  public String getEventType() {
    return eventType;
  }
}
