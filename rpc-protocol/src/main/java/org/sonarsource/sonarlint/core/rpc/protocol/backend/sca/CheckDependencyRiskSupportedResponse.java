package org.sonarsource.sonarlint.core.rpc.protocol.backend.sca;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class CheckDependencyRiskSupportedResponse {

  private final boolean supported;
  private final String reason;

  public CheckDependencyRiskSupportedResponse(boolean supported, @Nullable String reason) {
    this.supported = supported;
    this.reason = reason;
  }

  public boolean isSupported() {
    return supported;
  }

  @CheckForNull
  public String getReason() {
    return reason;
  }

}
