/*
 * SonarLint Core - Server API
 * Copyright (C) 2016-2024 SonarSource SA
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

import java.nio.file.Path;
import java.time.Instant;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;

public class ServerHotspot {
  private final String key;
  private final String ruleKey;
  private final String message;
  private Path filePath;
  private final TextRange textRange;
  private final Instant creationDate;
  private HotspotReviewStatus status;
  private final VulnerabilityProbability vulnerabilityProbability;
  @Nullable
  private String assignee;

  public ServerHotspot(String key,
    String ruleKey,
    String message,
    Path filePath,
    TextRange textRange,
    Instant creationDate,
    HotspotReviewStatus status,
    VulnerabilityProbability vulnerabilityProbability,
    @Nullable String assignee) {
    this.key = key;
    this.ruleKey = ruleKey;
    this.message = message;
    this.filePath = filePath;
    this.textRange = textRange;
    this.creationDate = creationDate;
    this.status = status;
    this.vulnerabilityProbability = vulnerabilityProbability;
    this.assignee = assignee;
  }

  public void setFilePath(Path filePath) {
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

  public Path getFilePath() {
    return filePath;
  }

  public TextRange getTextRange() {
    return textRange;
  }

  public Instant getCreationDate() {
    return creationDate;
  }

  public HotspotReviewStatus getStatus() {
    return status;
  }

  public ServerHotspot withStatus(HotspotReviewStatus newStatus) {
    return new ServerHotspot(key, ruleKey, message, filePath, textRange, creationDate, newStatus, vulnerabilityProbability, assignee);
  }

  public VulnerabilityProbability getVulnerabilityProbability() {
    return vulnerabilityProbability;
  }

  public String getAssignee() {
    return assignee;
  }

  public void setStatus(HotspotReviewStatus status) {
    this.status = status;
  }

  public void setAssignee(String assignee) {
    this.assignee = assignee;
  }
}
