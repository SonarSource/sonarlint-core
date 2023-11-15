package org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry;

public class HelpAndFeedbackClickedParams {
  private final String itemId;

  public HelpAndFeedbackClickedParams(String itemId) {
    this.itemId = itemId;
  }

  public String getItemId() {
    return itemId;
  }
}
