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
import java.util.Collections;
import java.util.Map;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
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
      return new ServerIssue(key, resolved, ruleKey, message, Path.of(filePath).toString(), introductionDate, issueSeverity, ruleType, textRangeWithHash, impacts);
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
      return new ServerIssue(key, resolved, "ruleKey", "message", Path.of("file/path").toString(), introductionDate, issueSeverity, ruleType, lineNumber, lineHash, impacts);
    }
  }

  public abstract static class AbstractServerIssueBuilder<T extends AbstractServerIssueBuilder<T>> {
    protected final String key;
    protected boolean resolved = false;
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

    public T resolved() {
      this.resolved = true;
      return (T) this;
    }

    public T open() {
      this.resolved = false;
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

  public static class ServerIssue {

    public final String key;
    public final boolean resolved;
    public final String ruleKey;
    public final String message;
    public final String filePath;
    public final Instant introductionDate;
    public final IssueSeverity userSeverity;
    public final RuleType ruleType;
    public final TextRangeWithHash textRangeWithHash;
    public final Integer lineNumber;
    public final String lineHash;
    public final Map<SoftwareQuality, ImpactSeverity> impacts;

    public ServerIssue(String key, boolean resolved, String ruleKey, String message, String filePath, Instant introductionDate, IssueSeverity userSeverity, RuleType ruleType, TextRangeWithHash textRangeWithHash,
      Map<SoftwareQuality, ImpactSeverity> impacts) {
      this.key = key;
      this.resolved = resolved;
      this.ruleKey = ruleKey;
      this.message = message;
      this.filePath = filePath;
      this.introductionDate = introductionDate;
      this.userSeverity = userSeverity;
      this.ruleType = ruleType;
      this.textRangeWithHash = textRangeWithHash;
      this.lineNumber = null;
      this.lineHash = null;
      this.impacts = impacts;
    }

    public ServerIssue(String key, boolean resolved, String ruleKey, String message, String filePath, Instant introductionDate, IssueSeverity userSeverity, RuleType ruleType,
      int lineNumber, String lineHash, Map<SoftwareQuality, ImpactSeverity> impacts) {
      this.key = key;
      this.resolved = resolved;
      this.ruleKey = ruleKey;
      this.message = message;
      this.filePath = filePath;
      this.introductionDate = introductionDate;
      this.userSeverity = userSeverity;
      this.ruleType = ruleType;
      this.textRangeWithHash = null;
      this.lineNumber = lineNumber;
      this.lineHash = lineHash;
      this.impacts = impacts;
    }

    public String getFilePath() {
      return filePath;
    }
  }
}
