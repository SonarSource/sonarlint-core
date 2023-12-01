package org.sonarsource.sonarlint.core.rpc.protocol.backend.config.exclusion;

import java.util.List;

public class DidUpdateGlobalExclusionsParams {

  /**
   * Patterns using the Java glob syntax.
   * https://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob
   */
  private final List<String> globPatterns;

  public DidUpdateGlobalExclusionsParams(List<String> globPatterns) {
    this.globPatterns = globPatterns;
  }

  public List<String> getGlobPatterns() {
    return globPatterns;
  }
}
