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
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;

public class ServerSecurityHotspotFixture {
  public static ServerSecurityHotspotBuilder aServerHotspot(String key) {
    return new ServerSecurityHotspotBuilder(key);
  }

  public static class ServerSecurityHotspotBuilder {
    private final String key;
    private Instant introductionDate = Instant.now();
    private HotspotReviewStatus status = HotspotReviewStatus.TO_REVIEW;
    private VulnerabilityProbability vulnerabilityProbability = VulnerabilityProbability.MEDIUM;
    private String assignee;
    private TextRangeWithHash textRangeWithHash = new TextRangeWithHash(1, 2, 3, 4, "rangeHash");
    private String ruleKey = "ruleKey";

    public ServerSecurityHotspotBuilder(String key) {
      this.key = key;
    }

    public ServerSecurityHotspotBuilder withRuleKey(String ruleKey) {
      this.ruleKey = ruleKey;
      return this;
    }

    public ServerSecurityHotspotBuilder withTextRange(TextRangeWithHash textRange) {
      this.textRangeWithHash = textRange;
      return this;
    }

    public ServerSecurityHotspotBuilder withIntroductionDate(Instant introductionDate) {
      this.introductionDate = introductionDate;
      return this;
    }

    public ServerSecurityHotspotBuilder withStatus(HotspotReviewStatus status) {
      this.status = status;
      return this;
    }

    public ServerSecurityHotspotBuilder withVulnerabilityProbability(VulnerabilityProbability vulnerabilityProbability) {
      this.vulnerabilityProbability = vulnerabilityProbability;
      return this;
    }

    public ServerSecurityHotspotBuilder withAssignee(String assignee) {
      this.assignee = assignee;
      return this;
    }

    public ServerHotspot build() {
      return new ServerHotspot(key, ruleKey, "message", "file/path", introductionDate, null, textRangeWithHash, status, vulnerabilityProbability, assignee);
    }
  }

  public static class ServerHotspot {

    public final String key;
    public final String ruleKey;
    public final String message;
    public final String filePath;
    public final Instant introductionDate;
    public final IssueSeverity userSeverity;
    public final TextRangeWithHash textRangeWithHash;
    public final HotspotReviewStatus status;
    public final VulnerabilityProbability vulnerabilityProbability;
    public final String assignee;

    public ServerHotspot(String key, String ruleKey, String message, String filePath, Instant introductionDate, IssueSeverity userSeverity,
      TextRangeWithHash textRangeWithHash, HotspotReviewStatus status, VulnerabilityProbability vulnerabilityProbability, String assignee) {
      this.key = key;
      this.ruleKey = ruleKey;
      this.message = message;
      this.filePath = filePath;
      this.introductionDate = introductionDate;
      this.userSeverity = userSeverity;
      this.textRangeWithHash = textRangeWithHash;
      this.status = status;
      this.vulnerabilityProbability = vulnerabilityProbability;
      this.assignee = assignee;
    }

    public String getFilePath() {
      return filePath;
    }
  }
}
