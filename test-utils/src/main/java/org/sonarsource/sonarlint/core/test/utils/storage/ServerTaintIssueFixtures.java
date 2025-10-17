/*
 * SonarLint Core - Test Utils
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.test.utils.storage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.IssueStatus;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;

import static org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue.*;

public class ServerTaintIssueFixtures {

  public static ServerTaintIssueBuilder aServerTaintIssue(String key) {
    return new ServerTaintIssueBuilder(key);
  }

  public static class ServerTaintIssueBuilder extends AbstractServerTaintIssueBuilder<ServerTaintIssueBuilder> {
    private TextRangeWithHash textRangeWithHash = new TextRangeWithHash(1, 2, 3, 4, "rangeHash");
    private String ruleKey = "ruleKey";
    private String filePath = "file/path";

    public ServerTaintIssueBuilder(String key) {
      super(key);
    }

    public ServerTaintIssueBuilder withRuleKey(String ruleKey) {
      this.ruleKey = ruleKey;
      return this;
    }

    public ServerTaintIssueBuilder withFilePath(String filePath) {
      this.filePath = filePath;
      return this;
    }

    public ServerTaintIssueBuilder withTextRange(TextRangeWithHash textRange) {
      this.textRangeWithHash = textRange;
      return this;
    }

    public ServerTaintIssue build() {
      return new ServerTaintIssue(UUID.randomUUID(), key, resolved, resolutionStatus, ruleKey, "message", Path.of(filePath).toString(), introductionDate,
        issueSeverity, ruleType, new ArrayList<>(), textRangeWithHash, "contextKey", CleanCodeAttribute.CONVENTIONAL,
        Map.of(SoftwareQuality.MAINTAINABILITY, ImpactSeverity.MEDIUM));
    }
  }

  public abstract static class AbstractServerTaintIssueBuilder<T extends AbstractServerTaintIssueBuilder<T>> {
    protected final String key;
    protected boolean resolved = false;
    protected IssueStatus resolutionStatus;
    protected Instant introductionDate = Instant.now();
    protected RuleType ruleType = RuleType.BUG;
    protected IssueSeverity issueSeverity = IssueSeverity.MINOR;

    protected AbstractServerTaintIssueBuilder(String key) {
      this.key = key;
    }

    public T withIntroductionDate(Instant introductionDate) {
      this.introductionDate = introductionDate;
      return (T) this;
    }

    public T resolvedWithStatus(IssueStatus resolutionStatus) {
      this.resolved = true;
      this.resolutionStatus = resolutionStatus;
      return (T) this;
    }

    public T open() {
      this.resolved = false;
      resolutionStatus = null;
      return (T) this;
    }

    public T withType(RuleType ruleType) {
      this.ruleType = ruleType;
      return (T) this;
    }

    public T withSeverity(IssueSeverity issueSeverity) {
      this.issueSeverity = issueSeverity;
      return (T) this;
    }

  }

  public record ServerTaintIssue(UUID id, String key, boolean resolved, IssueStatus resolutionStatus, String ruleKey, String message,
                                 String filePath, Instant creationDate, IssueSeverity severity, RuleType type, List<Flow> flows,
                                 @Nullable TextRangeWithHash textRange, @Nullable String ruleDescriptionContextKey, @Nullable CleanCodeAttribute cleanCodeAttribute,
                                 Map<SoftwareQuality, ImpactSeverity> impacts) { }
}
