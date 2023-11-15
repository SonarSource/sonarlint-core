package org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry;

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

public class AnalysisDoneOnSingleLanguageParams {
  @Nullable
  private final  Language language;
  private final int analysisTimeMs;

  public AnalysisDoneOnSingleLanguageParams(@Nullable Language language, int analysisTimeMs) {
    this.language = language;
    this.analysisTimeMs = analysisTimeMs;
  }

  @Nullable
  public Language getLanguage() {
    return language;
  }

  public int getAnalysisTimeMs() {
    return analysisTimeMs;
  }
}
