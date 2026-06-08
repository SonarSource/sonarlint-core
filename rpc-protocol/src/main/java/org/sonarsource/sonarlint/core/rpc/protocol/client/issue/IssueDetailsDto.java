/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.issue;

import java.nio.file.Path;
import java.util.List;
import org.sonarsource.sonarlint.core.rpc.protocol.common.FlowDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto;

public class IssueDetailsDto {
  private final String issueKey;
  private final String ruleKey;
  private final Path ideFilePath;
  private final String message;
  private final String creationDate;
  private final String codeSnippet;
  private final boolean isTaint;
  private final List<FlowDto> flows;
  private final TextRangeDto textRange;

  private IssueDetailsDto(Builder builder) {
    this.issueKey = builder.issueKey;
    this.ruleKey = builder.ruleKey;
    this.textRange = builder.textRange;
    this.ideFilePath = builder.ideFilePath;
    this.message = builder.message;
    this.creationDate = builder.creationDate;
    this.codeSnippet = builder.codeSnippet;
    this.isTaint = builder.isTaint;
    this.flows = builder.flows;
  }

  public TextRangeDto getTextRange() {
    return textRange;
  }

  public String getRuleKey() {
    return ruleKey;
  }

  public String getIssueKey() {
    return issueKey;
  }

  public String getCreationDate() {
    return creationDate;
  }

  public Path getIdeFilePath() {
    return ideFilePath;
  }

  public String getCodeSnippet() {
    return codeSnippet;
  }

  public String getMessage() {
    return message;
  }

  public boolean isTaint() {
    return isTaint;
  }

  public List<FlowDto> getFlows() {
    return flows;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String issueKey;
    private String ruleKey;
    private Path ideFilePath;
    private String message;
    private String creationDate;
    private String codeSnippet;
    private boolean isTaint;
    private List<FlowDto> flows;
    private TextRangeDto textRange;

    private Builder() {
    }

    public Builder issueKey(String issueKey) {
      this.issueKey = issueKey;
      return this;
    }

    public Builder ruleKey(String ruleKey) {
      this.ruleKey = ruleKey;
      return this;
    }

    public Builder ideFilePath(Path ideFilePath) {
      this.ideFilePath = ideFilePath;
      return this;
    }

    public Builder message(String message) {
      this.message = message;
      return this;
    }

    public Builder creationDate(String creationDate) {
      this.creationDate = creationDate;
      return this;
    }

    public Builder codeSnippet(String codeSnippet) {
      this.codeSnippet = codeSnippet;
      return this;
    }

    public Builder isTaint(boolean isTaint) {
      this.isTaint = isTaint;
      return this;
    }

    public Builder flows(List<FlowDto> flows) {
      this.flows = flows;
      return this;
    }

    public Builder textRange(TextRangeDto textRange) {
      this.textRange = textRange;
      return this;
    }

    public IssueDetailsDto build() {
      return new IssueDetailsDto(this);
    }
  }
}
