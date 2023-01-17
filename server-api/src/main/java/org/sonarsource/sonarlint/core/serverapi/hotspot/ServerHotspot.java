/*
 * SonarLint Core - Server API
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverapi.hotspot;

import java.time.Instant;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;

public class ServerHotspot {
  private final String key;
  private final String ruleKey;
  private final String message;
  private String filePath;
  private final TextRange textRange;
  private final Instant creationDate;
  private final boolean resolved;

  private final VulnerabilityProbability vulnerabilityProbability;

  public ServerHotspot(String key,
    String ruleKey,
    String message,
    String filePath,
    TextRange textRange,
    Instant creationDate,
    boolean resolved, VulnerabilityProbability vulnerabilityProbability) {
    this.key = key;
    this.ruleKey = ruleKey;
    this.message = message;
    this.filePath = filePath;
    this.textRange = textRange;
    this.creationDate = creationDate;
    this.resolved = resolved;
    this.vulnerabilityProbability = vulnerabilityProbability;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public String getKey() {
    return key;
  }

  public String getRuleKey() {
    return ruleKey;
  }

  public String getMessage() {
    return message;
  }

  public String getFilePath() {
    return filePath;
  }

  public TextRange getTextRange() {
    return textRange;
  }

  public Instant getCreationDate() {
    return creationDate;
  }

  public boolean isResolved() {
    return resolved;
  }

  public VulnerabilityProbability getVulnerabilityProbability() {
    return vulnerabilityProbability;
  }
}
