/*
 * SonarLint Core - Telemetry
 * Copyright (C) 2016-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
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
