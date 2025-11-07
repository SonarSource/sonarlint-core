/*
 * SonarLint Core - Test Utils
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
package org.sonarsource.sonarlint.core.test.utils.storage;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.IssueStatus;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;

public class ServerIssueFixtures {
  public static ServerIssueBuilder aServerIssue(String key) {
    return new ServerIssueBuilder(key);
  }

  public static LegacyServerIssueBuilder aLegacyServerIssue(String key) {
    return new LegacyServerIssueBuilder(key);
  }

  public static class ServerIssueBuilder extends AbstractServerIssueBuilder<ServerIssueBuilder> {
    private TextRangeWithHash textRangeWithHash = new TextRangeWithHash(1, 2, 3, 4, "rangeHash");
    private String ruleKey = "ruleKey";

    private String filePath = "file/path";
    private String message = "message";

    public ServerIssueBuilder(String key) {
      super(key);
    }

    public ServerIssueBuilder withRuleKey(String ruleKey) {
      this.ruleKey = ruleKey;
      return this;
    }

    public ServerIssueBuilder withTextRange(TextRangeWithHash textRange) {
      this.textRangeWithHash = textRange;
      return this;
    }

    public ServerIssueBuilder withFilePath(String filePath) {
      this.filePath = filePath;
      return this;
    }

    public ServerIssueBuilder withMessage(String message) {
      this.message = message;
      return this;
    }

    public ServerIssue build() {
      return new ServerIssue(key, resolved, resolutionStatus, ruleKey,
        message, Path.of(filePath).toString(), introductionDate, issueSeverity, ruleType,
        textRangeWithHash, null, null, impacts);
    }
  }

  public static class LegacyServerIssueBuilder extends AbstractServerIssueBuilder<LegacyServerIssueBuilder> {
    private int lineNumber = 1;
    private String lineHash = "lineHash";

    public LegacyServerIssueBuilder(String key) {
      super(key);
    }

    public LegacyServerIssueBuilder withLine(int number, String hash) {
      this.lineNumber = number;
      this.lineHash = hash;
      return this;
    }

    public ServerIssue build() {
      return new ServerIssue(key, resolved, resolutionStatus, "ruleKey", "message", Path.of("file/path").toString(), introductionDate, issueSeverity, ruleType,
        null, lineNumber, lineHash, impacts);
    }
  }

  public abstract static class AbstractServerIssueBuilder<T extends AbstractServerIssueBuilder<T>> {
    protected final String key;
    protected boolean resolved = false;
    protected IssueStatus resolutionStatus;
    protected Instant introductionDate = Instant.now();
    protected RuleType ruleType = RuleType.BUG;
    protected IssueSeverity issueSeverity;
    protected Map<SoftwareQuality, ImpactSeverity> impacts = Collections.emptyMap();

    protected AbstractServerIssueBuilder(String key) {
      this.key = key;
    }

    public T withIntroductionDate(Instant introductionDate) {
      this.introductionDate = introductionDate;
      return (T) this;
    }

    public T resolved(IssueStatus resolutionStatus) {
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

    public T withImpacts(Map<SoftwareQuality, ImpactSeverity> impacts) {
      this.impacts = impacts;
      return (T) this;
    }
  }

  public record ServerIssue(String key, boolean resolved, IssueStatus resolutionStatus, String ruleKey, String message, String filePath, Instant introductionDate,
                            IssueSeverity userSeverity, RuleType ruleType, @Nullable TextRangeWithHash textRangeWithHash, @Nullable Integer lineNumber, @Nullable String lineHash,
                            Map<SoftwareQuality, ImpactSeverity> impacts) { }
}
