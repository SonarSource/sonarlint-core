/*
 * SonarLint Core - Medium Tests
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
package mediumtest.fixtures.storage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;

public class ServerTaintIssueFixtures {

  public static ServerTaintIssueBuilder aServerTaintIssue(String key) {
    return new ServerTaintIssueBuilder(key);
  }

  public static class ServerTaintIssueBuilder extends AbstractServerTaintIssueBuilder<ServerTaintIssueBuilder> {
    private TextRangeWithHash textRangeWithHash = new TextRangeWithHash(1, 2, 3, 4, "rangeHash");
    private String ruleKey = "ruleKey";

    public ServerTaintIssueBuilder(String key) {
      super(key);
    }

    public ServerTaintIssueBuilder withRuleKey(String ruleKey) {
      this.ruleKey = ruleKey;
      return this;
    }

    public ServerTaintIssueBuilder withTextRange(TextRangeWithHash textRange) {
      this.textRangeWithHash = textRange;
      return this;
    }

    public ServerTaintIssue build() {
      return new ServerTaintIssue(key, resolved, ruleKey, "message", "file/path", introductionDate,
        null, ruleType, textRangeWithHash, "contextKey", CleanCodeAttribute.CONVENTIONAL, Map.of(SoftwareQuality.MAINTAINABILITY, ImpactSeverity.MEDIUM));
    }
  }

  public static abstract class AbstractServerTaintIssueBuilder<T extends AbstractServerTaintIssueBuilder<T>> {
    protected final String key;
    protected boolean resolved = false;
    protected Instant introductionDate = Instant.now();
    protected RuleType ruleType = RuleType.BUG;

    protected AbstractServerTaintIssueBuilder(String key) {
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

  public static class ServerTaintIssue {

    public String key;
    public boolean resolved;
    public String ruleKey;
    public String message;
    public String filePath;
    public Instant creationDate;
    public IssueSeverity severity;
    public RuleType type;
    public List<org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue.Flow> flows = new ArrayList<>();
    public TextRangeWithHash textRange;
    @Nullable
    public final String ruleDescriptionContextKey;
    @Nullable
    public final CleanCodeAttribute cleanCodeAttribute;
    public final Map<SoftwareQuality, ImpactSeverity> impacts;

    public ServerTaintIssue(String key, boolean resolved, String ruleKey, String message, String filePath, Instant creationDate, IssueSeverity severity, RuleType type,
      @Nullable TextRangeWithHash textRange, @Nullable String ruleDescriptionContextKey, @Nullable CleanCodeAttribute cleanCodeAttribute,
      Map<SoftwareQuality, ImpactSeverity> impacts) {
      this.key = key;
      this.resolved = resolved;
      this.ruleKey = ruleKey;
      this.message = message;
      this.filePath = filePath;
      this.creationDate = creationDate;
      this.severity = severity;
      this.type = type;
      this.textRange = textRange;
      this.ruleDescriptionContextKey = ruleDescriptionContextKey;
      this.cleanCodeAttribute = cleanCodeAttribute;
      this.impacts = impacts;
    }

    public String getFilePath() {
      return filePath;
    }
  }

}
