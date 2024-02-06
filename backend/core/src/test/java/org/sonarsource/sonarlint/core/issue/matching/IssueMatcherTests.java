/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.issue.matching;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IssueMatcherTests {


  private final IssueMatcher<FakeIssueType, FakeIssueType> underTest = new IssueMatcher<>(new FakeIssueMatchingAttributeMapper(), new FakeIssueMatchingAttributeMapper());

  private static class FakeIssueType {
    private String ruleKey = "dummy rule key";
    private Integer line;
    private String textRangeHash;
    private String lineHash;
    private String message = "dummy message";
    private String serverKey;

    public FakeIssueType setRuleKey(String ruleKey) {
      this.ruleKey = ruleKey;
      return this;
    }

    public FakeIssueType setLine(Integer line) {
      this.line = line;
      return this;
    }

    public FakeIssueType setTextRangeHash(String hash) {
      this.textRangeHash = hash;
      return this;
    }

    public FakeIssueType setLineHash(String hash) {
      this.lineHash = hash;
      return this;
    }

    public FakeIssueType setMessage(String message) {
      this.message = message;
      return this;
    }

    public FakeIssueType setServerKey(String key) {
      this.serverKey = key;
      return this;
    }
  }

  private static class FakeIssueMatchingAttributeMapper implements MatchingAttributesMapper<FakeIssueType> {

    @Override
    public String getRuleKey(FakeIssueType issue) {
      return issue.ruleKey;
    }

    @Override
    public Optional<Integer> getLine(FakeIssueType issue) {
      return Optional.ofNullable(issue.line);
    }

    @Override
    public Optional<String> getTextRangeHash(FakeIssueType issue) {
      return Optional.ofNullable(issue.textRangeHash);
    }

    @Override
    public Optional<String> getLineHash(FakeIssueType issue) {
      return Optional.ofNullable(issue.lineHash);
    }

    @Override
    public String getMessage(FakeIssueType issue) {
      return issue.message;
    }

    @Override
    public Optional<String> getServerIssueKey(FakeIssueType issue) {
      return Optional.ofNullable(issue.serverKey);
    }

  }

  @Test
  void should_not_match_issues_with_different_rule_key() {
    var issueForRuleA = new FakeIssueType().setRuleKey("ruleA");
    var issueForRuleB = new FakeIssueType().setRuleKey("ruleB");

    var result = underTest.match(List.of(issueForRuleA), List.of(issueForRuleB));

    assertThat(result.getMatchedLefts()).isEmpty();
  }

  @Test
  void should_match_by_line_and_text_range_hash() {
    var baseIssue = new FakeIssueType().setLine(7).setTextRangeHash("same range hash");

    var differentLine = new FakeIssueType().setLine(8).setTextRangeHash("same range hash");
    var differentTextRangeHash = new FakeIssueType().setLine(7).setTextRangeHash("different range hash");
    var differentBoth = new FakeIssueType().setLine(8).setTextRangeHash("different range hash");
    var same = new FakeIssueType().setLine(7).setTextRangeHash("same range hash");

    var result = underTest.match(List.of(differentLine, differentTextRangeHash, differentBoth, same), List.of(baseIssue));

    assertThat(result.getMatchedLefts()).hasSize(1);
    assertThat(result.getMatch(same)).isEqualTo(baseIssue);
  }

  @Test
  void should_match_by_line_and_line_hash_even_if_different_message_and_text_range() {
    var baseIssue = new FakeIssueType().setLine(7).setLineHash("same line hash").setMessage("different message").setTextRangeHash("different range hash");

    var differentLine = new FakeIssueType().setLine(8).setLineHash("same line hash");
    var differentLineHash = new FakeIssueType().setLine(7).setLineHash("different line hash");
    var differentBoth = new FakeIssueType().setLine(8).setLineHash("different line hash");
    var same = new FakeIssueType().setLine(7).setLineHash("same line hash");

    var result = underTest.match(List.of(differentLine, differentLineHash, differentBoth, same), List.of(baseIssue));

    assertThat(result.getMatchedLefts()).hasSize(1);
    assertThat(result.getMatch(same)).isEqualTo(baseIssue);
  }

  @Test
  void should_match_by_line_and_message_even_if_different_hash() {
    var baseIssue = new FakeIssueType().setLine(7).setMessage("same message").setTextRangeHash("different range hash");

    var differentLine = new FakeIssueType().setLine(8).setMessage("same message");
    var differentMessage = new FakeIssueType().setLine(7).setMessage("different message");
    var differentBoth = new FakeIssueType().setLine(8).setMessage("different message");
    var same = new FakeIssueType().setLine(7).setMessage("same message");

    var result = underTest.match(List.of(differentLine, differentMessage, differentBoth, same), List.of(baseIssue));

    assertThat(result.getMatchedLefts()).hasSize(1);
    assertThat(result.getMatch(same)).isEqualTo(baseIssue);
  }

  @Test
  void should_match_by_text_range_hash_even_if_no_line_number_before() {
    var baseIssueWithNoLine = new FakeIssueType().setTextRangeHash("same range hash");

    var differentLine = new FakeIssueType().setLine(8).setTextRangeHash("same range hash");

    var result = underTest.match(List.of(differentLine), List.of(baseIssueWithNoLine));

    assertThat(result.getMatchedLefts()).hasSize(1);
    assertThat(result.getMatch(differentLine)).isEqualTo(baseIssueWithNoLine);
  }

  @Test
  void should_match_by_text_range_hash_even_if_different_line_number() {
    var baseIssue = new FakeIssueType().setLine(7).setTextRangeHash("same range hash");

    var differentLine = new FakeIssueType().setLine(8).setTextRangeHash("same range hash");

    var result = underTest.match(List.of(differentLine), List.of(baseIssue));

    assertThat(result.getMatchedLefts()).hasSize(1);
    assertThat(result.getMatch(differentLine)).isEqualTo(baseIssue);
  }

  @Test
  void should_match_by_line_hash_even_if_no_line_number_before() {
    var baseIssueWithNoLine = new FakeIssueType().setLineHash("same line hash");

    var differentLine = new FakeIssueType().setLine(8).setLineHash("same line hash");

    var result = underTest.match(List.of(differentLine), List.of(baseIssueWithNoLine));

    assertThat(result.getMatchedLefts()).hasSize(1);
    assertThat(result.getMatch(differentLine)).isEqualTo(baseIssueWithNoLine);
  }

  @Test
  void should_match_by_line_hash_even_if_different_line_number() {
    var baseIssue = new FakeIssueType().setLine(7).setLineHash("same line hash");

    var differentLine = new FakeIssueType().setLine(8).setLineHash("same line hash");

    var result = underTest.match(List.of(differentLine), List.of(baseIssue));

    assertThat(result.getMatchedLefts()).hasSize(1);
    assertThat(result.getMatch(differentLine)).isEqualTo(baseIssue);
  }

  @Test
  void should_match_by_serverKey_even_if_no_line_number_before() {
    var baseIssueWithNoLine = new FakeIssueType().setServerKey("same key");

    var differentLine = new FakeIssueType().setLine(8).setServerKey("same key");

    var result = underTest.match(List.of(differentLine), List.of(baseIssueWithNoLine));

    assertThat(result.getMatchedLefts()).hasSize(1);
    assertThat(result.getMatch(differentLine)).isEqualTo(baseIssueWithNoLine);
  }

  @Test
  void should_match_by_serverKey_even_if_different_line_number() {
    var baseIssue = new FakeIssueType().setLine(7).setServerKey("same key");

    var differentLine = new FakeIssueType().setLine(8).setServerKey("same key");

    var result = underTest.match(List.of(differentLine), List.of(baseIssue));

    assertThat(result.getMatchedLefts()).hasSize(1);
    assertThat(result.getMatch(differentLine)).isEqualTo(baseIssue);
  }


}