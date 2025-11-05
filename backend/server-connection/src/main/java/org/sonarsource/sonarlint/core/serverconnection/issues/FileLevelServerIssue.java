/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.issues;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.IssueStatus;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;

/**
 * Issues reported at file level.
 */
public class FileLevelServerIssue extends ServerIssue<FileLevelServerIssue> {

  public FileLevelServerIssue(@Nullable UUID id, String key, boolean resolved, @Nullable IssueStatus resolutionStatus, String ruleKey,
    String message, Path filePath, Instant creationDate, @Nullable IssueSeverity userSeverity,
    RuleType type, Map<SoftwareQuality, ImpactSeverity> impacts) {
    super(id, key, resolved, resolutionStatus, ruleKey, message, filePath, creationDate, userSeverity, type, impacts);
  }

  /**
   * constructor for backward compatibility, after finalization of migration from Xodus to H2 should not be used
   * when using with H2 UUID should always be set
   */
  public FileLevelServerIssue(String key, boolean resolved, @Nullable IssueStatus resolutionStatus, String ruleKey,
    String message, Path filePath, Instant creationDate, @Nullable IssueSeverity userSeverity,
    RuleType type, Map<SoftwareQuality, ImpactSeverity> impacts) {
    this(null, key, resolved, resolutionStatus, ruleKey, message, filePath, creationDate, userSeverity, type, impacts);
  }
}
