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
package org.sonarsource.sonarlint.core.issue.matching;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Match a collection of issues.
 *
 * @param <LEFT>  type of the issues that are in the first collection
 * @param <RIGHT> type of the issues that are in the second collection
 */
public class IssueMatcher<LEFT, RIGHT> {

  private final MatchingAttributesMapper<LEFT> leftMapper;
  private final MatchingAttributesMapper<RIGHT> rightMapper;

  public IssueMatcher(MatchingAttributesMapper<LEFT> leftMapper, MatchingAttributesMapper<RIGHT> rightMapper) {
    this.leftMapper = leftMapper;
    this.rightMapper = rightMapper;
  }

  public MatchingResult<LEFT, RIGHT> match(Collection<LEFT> leftIssues, Collection<RIGHT> rightIssues) {
    var result = new MatchingResult<>(leftIssues, rightIssues);

    // 1. match issues with same server issue key
    match(result, ServerIssueSearchKey::new);

    // 2. match issues with same rule, same line and same text range hash, but not necessarily with same message
    match(result, LineAndTextRangeHashKey::new);

    // 3. match issues with same rule, same message and same text range hash
    match(result, TextRangeHashAndMessageKey::new);

    // 4. match issues with same rule, same line and same message
    match(result, LineAndMessageKey::new);

    // 5. match issues with same rule and same text range hash but different line and different message.
    // See SONAR-2812
    match(result, TextRangeHashKey::new);

    // 6. match issues with same rule, same line and same line hash
    match(result, LineAndLineHashKey::new);

    // 7. match issues with same rule and same line hash
    match(result, LineHashKey::new);

    return result;
  }

  private void match(MatchingResult<LEFT, RIGHT> result, SearchKeyFactory keyFactory) {
    if (result.isComplete()) {
      return;
    }

    Map<SearchKey, List<RIGHT>> rightSearch = new HashMap<>();
    for (RIGHT right : result.getUnmatchedRights()) {
      var searchKey = keyFactory.buildKey(right, rightMapper);
      rightSearch.computeIfAbsent(searchKey, k -> new ArrayList<>()).add(right);
    }

    for (LEFT left : result.getUnmatchedLefts()) {
      var leftKey = keyFactory.buildKey(left, leftMapper);
      Collection<RIGHT> rightsCandidates = rightSearch.get(leftKey);
      if (rightsCandidates != null && !rightsCandidates.isEmpty()) {
        // TODO taking the first one. Could be improved if there are more than 2 issues on the same line.
        // Message could be checked to take the best one.
        var match = rightsCandidates.iterator().next();
        result.match(left, match);
        rightSearch.get(leftKey).remove(match);
      }
    }
  }

  private interface SearchKey {
  }

  private interface SearchKeyFactory {
    <G> SearchKey buildKey(G issue, MatchingAttributesMapper<G> mapper);
  }

  private static class LineAndTextRangeHashKey implements SearchKey {
    private final String ruleKey;
    @Nullable
    private final String textRangeHash;
    @Nullable
    private final Integer line;

    <G> LineAndTextRangeHashKey(G issue, MatchingAttributesMapper<G> mapper) {
      this.ruleKey = mapper.getRuleKey(issue);
      this.line = mapper.getLine(issue).orElse(null);
      this.textRangeHash = mapper.getTextRangeHash(issue).orElse(null);
    }

    // note: the design of the enclosing caller ensures that 'o' is of the correct class and not null
    @Override
    public boolean equals(Object o) {
      var that = (LineAndTextRangeHashKey) o;
      // start with most discriminant field
      return Objects.equals(line, that.line)
        && Objects.equals(textRangeHash, that.textRangeHash)
        && ruleKey.equals(that.ruleKey);
    }

    @Override
    public int hashCode() {
      var result = ruleKey.hashCode();
      result = 31 * result + (textRangeHash != null ? textRangeHash.hashCode() : 0);
      result = 31 * result + (line != null ? line.hashCode() : 0);
      return result;
    }
  }

  private static class LineAndLineHashKey implements SearchKey {
    private final String ruleKey;
    @Nullable
    private final Integer line;
    private final String lineHash;

    <G> LineAndLineHashKey(G issue, MatchingAttributesMapper<G> mapper) {
      this.ruleKey = mapper.getRuleKey(issue);
      this.line = mapper.getLine(issue).orElse(null);
      this.lineHash = mapper.getLineHash(issue).orElse("");
    }

    // note: the design of the enclosing caller ensures that 'o' is of the correct class and not null
    @Override
    public boolean equals(Object o) {
      var that = (LineAndLineHashKey) o;
      // start with most discriminant field
      return Objects.equals(line, that.line)
        && Objects.equals(lineHash, that.lineHash)
        && ruleKey.equals(that.ruleKey);
    }

    @Override
    public int hashCode() {
      var result = ruleKey.hashCode();
      result = 31 * result + (lineHash != null ? lineHash.hashCode() : 0);
      result = 31 * result + (line != null ? line.hashCode() : 0);
      return result;
    }
  }

  private static class LineHashKey implements SearchKey {
    private final String ruleKey;
    private final String lineHash;

    <G> LineHashKey(G issue, MatchingAttributesMapper<G> mapper) {
      this.ruleKey = mapper.getRuleKey(issue);
      this.lineHash = mapper.getLineHash(issue).orElse("");
    }

    // note: the design of the enclosing caller ensures that 'o' is of the correct class and not null
    @Override
    public boolean equals(Object o) {
      var that = (LineHashKey) o;
      // start with most discriminant field
      return Objects.equals(lineHash, that.lineHash)
        && ruleKey.equals(that.ruleKey);
    }

    @Override
    public int hashCode() {
      var result = ruleKey.hashCode();
      result = 31 * result + (lineHash != null ? lineHash.hashCode() : 0);
      return result;
    }
  }

  private static class TextRangeHashAndMessageKey implements SearchKey {
    private final String ruleKey;
    private final String message;
    private final String textRangeHash;

    <G> TextRangeHashAndMessageKey(G issue, MatchingAttributesMapper<G> mapper) {
      this.ruleKey = mapper.getRuleKey(issue);
      this.message = mapper.getMessage(issue);
      this.textRangeHash = mapper.getTextRangeHash(issue).orElse(null);
    }

    // note: the design of the enclosing caller ensures that 'o' is of the correct class and not null
    @Override
    public boolean equals(Object o) {
      var that = (TextRangeHashAndMessageKey) o;
      // start with most discriminant field
      return Objects.equals(textRangeHash, that.textRangeHash)
        && message.equals(that.message)
        && ruleKey.equals(that.ruleKey);
    }

    @Override
    public int hashCode() {
      var result = ruleKey.hashCode();
      result = 31 * result + message.hashCode();
      result = 31 * result + (textRangeHash != null ? textRangeHash.hashCode() : 0);
      return result;
    }
  }

  private static class LineAndMessageKey implements SearchKey {
    private final String ruleKey;
    private final String message;
    @Nullable
    private final Integer line;

    <G> LineAndMessageKey(G issue, MatchingAttributesMapper<G> mapper) {
      this.ruleKey = mapper.getRuleKey(issue);
      this.message = mapper.getMessage(issue);
      this.line = mapper.getLine(issue).orElse(null);
    }

    // note: the design of the enclosing caller ensures that 'o' is of the correct class and not null
    @Override
    public boolean equals(Object o) {
      var that = (LineAndMessageKey) o;
      // start with most discriminant field
      return Objects.equals(line, that.line)
        && message.equals(that.message)
        && ruleKey.equals(that.ruleKey);
    }

    @Override
    public int hashCode() {
      var result = ruleKey.hashCode();
      result = 31 * result + message.hashCode();
      result = 31 * result + (line != null ? line.hashCode() : 0);
      return result;
    }
  }

  private static class TextRangeHashKey implements SearchKey {
    private final String ruleKey;
    private final String textRangeHash;

    <G> TextRangeHashKey(G issue, MatchingAttributesMapper<G> mapper) {
      this.ruleKey = mapper.getRuleKey(issue);
      this.textRangeHash = mapper.getTextRangeHash(issue).orElse("");
    }

    // note: the design of the enclosing caller ensures that 'o' is of the correct class and not null
    @Override
    public boolean equals(Object o) {
      var that = (TextRangeHashKey) o;
      // start with most discriminant field
      return Objects.equals(textRangeHash, that.textRangeHash)
        && ruleKey.equals(that.ruleKey);
    }

    @Override
    public int hashCode() {
      var result = ruleKey.hashCode();
      result = 31 * result + (textRangeHash != null ? textRangeHash.hashCode() : 0);
      return result;
    }
  }

  private static class ServerIssueSearchKey implements SearchKey {
    @Nullable
    private final String serverIssueKey;

    <G> ServerIssueSearchKey(G issue, MatchingAttributesMapper<G> mapper) {
      serverIssueKey = mapper.getServerIssueKey(issue).orElse(null);
    }

    // note: the design of the enclosing caller ensures that 'o' is of the correct class and not null
    @Override
    public boolean equals(Object o) {
      var that = (ServerIssueSearchKey) o;
      return !isBlank(serverIssueKey) && !isBlank(that.serverIssueKey) && serverIssueKey.equals(that.serverIssueKey);
    }

    private static boolean isBlank(String s) {
      return s == null || s.isEmpty();
    }

    @Override
    public int hashCode() {
      return serverIssueKey != null ? serverIssueKey.hashCode() : 0;
    }
  }

}
