package org.sonarsource.sonarlint.core.rpc.protocol.backend.ai;

import java.nio.file.Path;

public class GetRuleFileContentResponse {
  private final Path relativePath;
  private final String content;

  public GetRuleFileContentResponse(Path relativePath, String content) {
    this.relativePath = relativePath;
    this.content = content;
  }

  public Path getRelativePath() {
    return relativePath;
  }

  public String getContent() {
    return content;
  }
}
