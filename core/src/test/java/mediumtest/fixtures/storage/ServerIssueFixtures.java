/*
 * SonarLint Core - Implementation
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
package mediumtest.fixtures.storage;

import java.time.Instant;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;

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

    public ServerIssueFixtures.ServerIssue build() {
      return new ServerIssueFixtures.ServerIssue(key, resolved, ruleKey, "message", "file/path", introductionDate, null, ruleType, textRangeWithHash);
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

    public ServerIssueFixtures.ServerIssue build() {
      return new ServerIssueFixtures.ServerIssue(key, resolved, "ruleKey", "message", "file/path", introductionDate, null, ruleType, lineNumber, lineHash);
    }
  }

  public static abstract class AbstractServerIssueBuilder<T extends AbstractServerIssueBuilder<T>> {
    protected final String key;
    protected boolean resolved = false;
    protected Instant introductionDate = Instant.now();
    protected RuleType ruleType = RuleType.BUG;

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

    public T withType(RuleType ruleType) {
      this.ruleType = ruleType;
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

    public ServerIssue(String key, boolean resolved, String ruleKey, String message, String filePath, Instant introductionDate, IssueSeverity userSeverity, RuleType ruleType, TextRangeWithHash textRangeWithHash) {
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
    }

    public ServerIssue(String key, boolean resolved, String ruleKey, String message, String filePath, Instant introductionDate, IssueSeverity userSeverity, RuleType ruleType, int lineNumber, String lineHash) {
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
    }

    public String getFilePath() {
      return filePath;
    }
  }
}
