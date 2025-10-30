package org.sonarsource.sonarlint.core.telemetry.gessie.event;

import com.google.gson.annotations.SerializedName;
import java.util.UUID;

public record GessieMetadata(
  UUID eventId,
  GessieSource source,
  String eventType,
  String eventTimestamp,
  String eventVersion
) {

  public record GessieSource(SonarLintDomain domain) {
  }

  public enum SonarLintDomain {
    @SerializedName("VSCode")
    VS_CODE,
    @SerializedName("VisualStudio")
    VISUAL_STUDIO,
    @SerializedName("Eclipse")
    ECLIPSE,
    @SerializedName("IntelliJ")
    INTELLIJ,
    @SerializedName("SLCore")
    SLCORE;

    public static SonarLintDomain fromProductKey(String productKey) {
      return switch (productKey) {
        case "idea" -> INTELLIJ;
        case "eclipse" -> ECLIPSE;
        case "visualstudio" -> VISUAL_STUDIO;
        case "vscode", "cursor", "windsurf" -> VS_CODE;
        default -> SLCORE;
      };
    }
  }
}
