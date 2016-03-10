package org.sonarsource.sonarlint.core.client.api.connected;

import org.sonarsource.sonarlint.core.client.api.SonarLintException;

public class UnsupportedServerException extends SonarLintException {

  public UnsupportedServerException(String msg) {
    super(msg, null);
  }
}
