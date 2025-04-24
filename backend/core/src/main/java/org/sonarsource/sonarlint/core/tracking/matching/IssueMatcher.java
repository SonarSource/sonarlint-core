/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.tracking.matching;

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

  private static final List<MatchingCriterionFactory> MATCHING_CRITERIA = List.of(
    // 1. match issues with same server issue key
    ServerIssueMatchingCriterion::new,
    // 2. match issues with same rule, same line and same text range hash, but not necessarily with same message
    LineAndTextRangeHashMatchingCriterion::new,
    // 3. match issues with same rule, same message and same text range hash
    TextRangeHashAndMessageMatchingCriterion::new,
    // 4. match issues with same rule, same line and same message
    LineAndMessageMatchingCriterion::new,
    // 5. match issues with same rule and same text range hash but different line and different message.
    // See SONAR-2812
    TextRangeHashMatchingCriterion::new,
    // 6. match issues with same rule, same line and same line hash
    LineAndLineHashMatchingCriterion::new,
    // 7. match issues with same rule and same line hash
    LineHashMatchingCriterion::new);

  private final Map<MatchingCriterionFactory, Map<MatchingCriterion, List<RIGHT>>> rightIssuesByCriterion = new HashMap<>();

  public IssueMatcher(MatchingAttributesMapper<RIGHT> rightMapper, Collection<RIGHT> rightIssues) {
    for (var matchingCriterion : MATCHING_CRITERIA) {
      var issuesByCriterion = new HashMap<MatchingCriterion, List<RIGHT>>();
      for (RIGHT right : rightIssues) {
        var criterionAppliedToIssue = matchingCriterion.build(right, rightMapper);
        issuesByCriterion.computeIfAbsent(criterionAppliedToIssue, k -> new ArrayList<>()).add(right);
      }
      rightIssuesByCriterion.put(matchingCriterion, issuesByCriterion);
    }
  }

  public MatchingResult<LEFT, RIGHT> matchWith(MatchingAttributesMapper<LEFT> leftMapper, Collection<LEFT> leftIssues) {
    var result = new MatchingResult<LEFT, RIGHT>(leftIssues);

    for (var matchingCriterion : MATCHING_CRITERIA) {
      if (result.isComplete()) {
        break;
      }
      matchWithCriterion(result, leftMapper, matchingCriterion);
    }

    return result;
  }

  private void matchWithCriterion(MatchingResult<LEFT, RIGHT> result, MatchingAttributesMapper<LEFT> leftMapper, MatchingCriterionFactory criterionFactory) {
    for (LEFT left : result.getUnmatchedLefts()) {
      var leftKey = criterionFactory.build(left, leftMapper);
      var rightsCandidates = rightIssuesByCriterion.get(criterionFactory).get(leftKey);
      if (rightsCandidates != null && !rightsCandidates.isEmpty()) {
        // TODO taking the first one. Could be improved if there are more than 2 issues on the same line.
        // Message could be checked to take the best one.
        var match = rightsCandidates.iterator().next();
        result.recordMatch(left, match);
        MATCHING_CRITERIA.forEach(criterion -> rightIssuesByCriterion.get(criterion).remove(criterion.build(left, leftMapper)));
      }
    }
  }

  private interface MatchingCriterion {
  }

  private interface MatchingCriterionFactory {
    <G> MatchingCriterion build(G issue, MatchingAttributesMapper<G> mapper);
  }

  private static class LineAndTextRangeHashMatchingCriterion implements MatchingCriterion {
    private final String ruleKey;
    @Nullable
    private final String textRangeHash;
    @Nullable
    private final Integer line;

    <G> LineAndTextRangeHashMatchingCriterion(G issue, MatchingAttributesMapper<G> mapper) {
      this.ruleKey = mapper.getRuleKey(issue);
      this.line = mapper.getLine(issue).orElse(null);
      this.textRangeHash = mapper.getTextRangeHash(issue).orElse(null);
    }

    // note: the design of the enclosing caller ensures that 'o' is of the correct class and not null
    @Override
    public boolean equals(Object o) {
      var that = (LineAndTextRangeHashMatchingCriterion) o;
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

  private static class LineAndLineHashMatchingCriterion implements MatchingCriterion {
    private final String ruleKey;
    @Nullable
    private final Integer line;
    private final String lineHash;

    <G> LineAndLineHashMatchingCriterion(G issue, MatchingAttributesMapper<G> mapper) {
      this.ruleKey = mapper.getRuleKey(issue);
      this.line = mapper.getLine(issue).orElse(null);
      this.lineHash = mapper.getLineHash(issue).orElse("");
    }

    // note: the design of the enclosing caller ensures that 'o' is of the correct class and not null
    @Override
    public boolean equals(Object o) {
      var that = (LineAndLineHashMatchingCriterion) o;
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

  private static class LineHashMatchingCriterion implements MatchingCriterion {
    private final String ruleKey;
    private final String lineHash;

    <G> LineHashMatchingCriterion(G issue, MatchingAttributesMapper<G> mapper) {
      this.ruleKey = mapper.getRuleKey(issue);
      this.lineHash = mapper.getLineHash(issue).orElse("");
    }

    // note: the design of the enclosing caller ensures that 'o' is of the correct class and not null
    @Override
    public boolean equals(Object o) {
      var that = (LineHashMatchingCriterion) o;
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

  private static class TextRangeHashAndMessageMatchingCriterion implements MatchingCriterion {
    private final String ruleKey;
    private final String message;
    private final String textRangeHash;

    <G> TextRangeHashAndMessageMatchingCriterion(G issue, MatchingAttributesMapper<G> mapper) {
      this.ruleKey = mapper.getRuleKey(issue);
      this.message = mapper.getMessage(issue);
      this.textRangeHash = mapper.getTextRangeHash(issue).orElse(null);
    }

    // note: the design of the enclosing caller ensures that 'o' is of the correct class and not null
    @Override
    public boolean equals(Object o) {
      var that = (TextRangeHashAndMessageMatchingCriterion) o;
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

  private static class LineAndMessageMatchingCriterion implements MatchingCriterion {
    private final String ruleKey;
    private final String message;
    @Nullable
    private final Integer line;

    <G> LineAndMessageMatchingCriterion(G issue, MatchingAttributesMapper<G> mapper) {
      this.ruleKey = mapper.getRuleKey(issue);
      this.message = mapper.getMessage(issue);
      this.line = mapper.getLine(issue).orElse(null);
    }

    // note: the design of the enclosing caller ensures that 'o' is of the correct class and not null
    @Override
    public boolean equals(Object o) {
      var that = (LineAndMessageMatchingCriterion) o;
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

  private static class TextRangeHashMatchingCriterion implements MatchingCriterion {
    private final String ruleKey;
    private final String textRangeHash;

    <G> TextRangeHashMatchingCriterion(G issue, MatchingAttributesMapper<G> mapper) {
      this.ruleKey = mapper.getRuleKey(issue);
      this.textRangeHash = mapper.getTextRangeHash(issue).orElse("");
    }

    // note: the design of the enclosing caller ensures that 'o' is of the correct class and not null
    @Override
    public boolean equals(Object o) {
      var that = (TextRangeHashMatchingCriterion) o;
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

  private static class ServerIssueMatchingCriterion implements MatchingCriterion {
    @Nullable
    private final String serverIssueKey;

    <G> ServerIssueMatchingCriterion(G issue, MatchingAttributesMapper<G> mapper) {
      serverIssueKey = mapper.getServerIssueKey(issue).orElse(null);
    }

    // note: the design of the enclosing caller ensures that 'o' is of the correct class and not null
    @Override
    public boolean equals(Object o) {
      var that = (ServerIssueMatchingCriterion) o;
      return that != null && !isBlank(serverIssueKey) && !isBlank(that.serverIssueKey) && serverIssueKey.equals(that.serverIssueKey);
    }

    private static boolean isBlank(@Nullable String s) {
      return s == null || s.isEmpty();
    }

    @Override
    public int hashCode() {
      return serverIssueKey != null ? serverIssueKey.hashCode() : 0;
    }
  }

}
