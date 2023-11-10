/*
 * SonarLint Issue Tracking
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
package org.sonarsource.sonarlint.core.issuetracking;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IssueTrackerTests {

  private final IssueTrackerCache cache = new InMemoryIssueTrackerCache();

  private final CachingIssueTracker tracker = new CachingIssueTracker(cache);

  private final String file1 = "dummyFile1";

  // note: these mock trackables are used by many test cases,
  // with their line numbers to distinguish their identities.
  private final Trackable trackable1 = builder().line(1).textRange(new TextRangeWithHash(1, 2, 3, 4, "md51")).build();
  private final Trackable trackable2 = builder().line(2).textRange(new TextRangeWithHash(2, 2, 3, 4, "md52")).build();

  static class MockTrackableBuilder {
    String ruleKey = "";
    Integer line = null;
    String message = "";
    String lineHash = null;
    Long creationDate = null;
    String serverIssueKey = null;
    boolean resolved = false;
    TextRangeWithHash textRange = null;

    static int counter = Integer.MIN_VALUE;

    MockTrackableBuilder ruleKey(String ruleKey) {
      this.ruleKey = ruleKey;
      return this;
    }

    MockTrackableBuilder line(Integer line) {
      this.line = line;
      return this;
    }

    MockTrackableBuilder message(String message) {
      this.message = message;
      return this;
    }

    MockTrackableBuilder lineHash(String lineHash) {
      this.lineHash = lineHash;
      return this;
    }

    MockTrackableBuilder creationDate(Long creationDate) {
      this.creationDate = creationDate;
      return this;
    }

    MockTrackableBuilder serverIssueKey(String serverIssueKey) {
      this.serverIssueKey = serverIssueKey;
      return this;
    }

    MockTrackableBuilder resolved(boolean resolved) {
      this.resolved = resolved;
      return this;
    }

    MockTrackableBuilder textRange(TextRangeWithHash textRange) {
      this.textRange = textRange;
      return this;
    }

    MockTrackableBuilder copy() {
      return builder()
        .line(line)
        .message(message)
        .lineHash(lineHash)
        .ruleKey(ruleKey)
        .creationDate(creationDate)
        .serverIssueKey(serverIssueKey)
        .textRange(textRange)
        .resolved(resolved);
    }

    Trackable build() {
      var mock = mock(Trackable.class);
      when(mock.getLine()).thenReturn(line);
      when(mock.getLineHash()).thenReturn(lineHash);
      when(mock.getRuleKey()).thenReturn(ruleKey);
      when(mock.getMessage()).thenReturn(message);
      when(mock.getCreationDate()).thenReturn(creationDate);
      when(mock.getServerIssueKey()).thenReturn(serverIssueKey);
      when(mock.isResolved()).thenReturn(resolved);
      when(mock.getTextRange()).thenReturn(textRange);

      // ensure default unique values for fields used by matchers (except server issue key)
      if (line == null) {
        when(mock.getLine()).thenReturn(counter++);
      }
      if (lineHash == null) {
        when(mock.getLineHash()).thenReturn("lineHash" + counter++);
      }
      if (message.isEmpty()) {
        when(mock.getMessage()).thenReturn("m" + (counter++));
      }

      return mock;
    }
  }

  private static MockTrackableBuilder builder() {
    return new MockTrackableBuilder();
  }

  @BeforeEach
  void setUp() {
    cache.clear();
  }

  @Test
  void should_track_first_trackables_exactly() {
    Collection<Trackable> trackables = Arrays.asList(mock(Trackable.class), mock(Trackable.class));
    tracker.matchAndTrackAsNew(file1, trackables);
    assertThat(cache.getCurrentTrackables(file1)).isEqualTo(trackables);
  }

  @Test
  void should_preserve_known_standalone_trackables_with_null_date() {
    Collection<Trackable> trackables = Arrays.asList(trackable1, trackable2);
    tracker.matchAndTrackAsNew(file1, trackables);
    tracker.matchAndTrackAsNew(file1, trackables);

    Collection<Trackable> next = cache.getCurrentTrackables(file1);
    assertThat(next).extracting(Trackable::getLine).containsExactlyInAnyOrder(trackable1.getLine(), trackable2.getLine());
    assertThat(next).extracting(Trackable::getCreationDate).containsExactlyInAnyOrder(null, null);
  }

  @Test
  void should_add_creation_date_for_leaked_trackables() {
    var start = System.currentTimeMillis();

    tracker.matchAndTrackAsNew(file1, Collections.singletonList(trackable1));
    tracker.matchAndTrackAsNew(file1, Arrays.asList(trackable1, trackable2));

    Collection<Trackable> next = cache.getCurrentTrackables(file1);
    assertThat(next).extracting(Trackable::getLine).contains(trackable1.getLine(), trackable2.getLine());

    assertThat(next).extracting(Trackable::getCreationDate).containsOnlyOnce((Long) null);

    var leaked = next.stream().filter(t -> t.getCreationDate() != null).findFirst().get();
    assertThat(leaked.getCreationDate()).isGreaterThanOrEqualTo(start);
    assertThat(leaked.getLine()).isEqualTo(trackable2.getLine());
  }

  @Test
  void should_drop_disappeared_issues() {
    tracker.matchAndTrackAsNew(file1, Arrays.asList(trackable1, trackable2));
    tracker.matchAndTrackAsNew(file1, Collections.singletonList(trackable1));

    Collection<Trackable> next = cache.getCurrentTrackables(file1);
    assertThat(next).extracting(Trackable::getLine).containsExactly(trackable1.getLine());
  }

  @Test
  void should_not_match_trackables_with_different_rule_key() {
    var ruleKey = "dummy ruleKey";
    var base = builder()
      .line(7)
      .message("dummy message")
      .lineHash("md5")
      .ruleKey(ruleKey)
      .serverIssueKey("dummy serverIssueKey")
      .creationDate(17L);

    tracker.matchAndTrackAsNew(file1, Collections.singletonList(base.build()));
    tracker.matchAndTrackAsNew(file1, Collections.singletonList(base.ruleKey(ruleKey + "x").build()));
  }

  @Test
  void should_treat_new_issues_as_leak_when_old_issues_disappeared() {
    var start = System.currentTimeMillis();

    tracker.matchAndTrackAsNew(file1, Collections.singletonList(trackable1));
    tracker.matchAndTrackAsNew(file1, Collections.singletonList(trackable2));

    Collection<Trackable> next = cache.getCurrentTrackables(file1);
    assertThat(next).extracting(Trackable::getLine).containsExactly(trackable2.getLine());

    var leaked = next.stream().filter(t -> t.getCreationDate() != null).findFirst().get();
    assertThat(leaked.getCreationDate()).isGreaterThanOrEqualTo(start);
  }

  @Test
  void should_match_by_line_and_text_range_hash() {
    var base = builder().ruleKey("dummy ruleKey");
    var line = 7;
    tracker.matchAndTrackAsNew(file1, Collections.singletonList(base.copy().line(line).textRange(new TextRangeWithHash(1, 2, 3, 4, "md5")).build()));

    var differentLine = base.line(line + 1).textRange(new TextRangeWithHash(1, 2, 3, 4, "md5")).build();
    var differentTextRangeHash = base.line(line).textRange(new TextRangeWithHash(1, 2, 3, 4, "md6")).build();
    var differentBoth = base.line(line + 1).textRange(new TextRangeWithHash(1, 2, 3, 4, "md6")).build();
    var same = base.line(line).textRange(new TextRangeWithHash(1, 2, 3, 4, "md5")).build();
    tracker.matchAndTrackAsNew(file1, Arrays.asList(differentLine, differentTextRangeHash, differentBoth, same));

    Collection<Trackable> current = cache.getCurrentTrackables(file1);
    assertThat(current).hasSize(4);
    assertThat(current)
      .extracting("line", "textRange.hash")
      .containsOnlyOnce(tuple(line, "md5"));
  }

  @Test
  void should_match_by_line_and_line_hash() {
    var base = builder().ruleKey("dummy ruleKey");
    var line = 7;
    var lineHash = "11";
    tracker.matchAndTrackAsNew(file1, Collections.singletonList(base.copy().line(line).lineHash(lineHash).build()));

    var differentLine = base.line(line + 1).lineHash(lineHash).build();
    var differentLineHash = base.line(line).lineHash(lineHash + 1).build();
    var differentBoth = base.line(line + 1).lineHash(lineHash + 1).build();
    var same = base.line(line).lineHash(lineHash).build();
    tracker.matchAndTrackAsNew(file1, Arrays.asList(differentLine, differentLineHash, differentBoth, same));

    Collection<Trackable> current = cache.getCurrentTrackables(file1);
    assertThat(current).hasSize(4);
    assertThat(current)
      .extracting("line", "lineHash")
      .containsOnlyOnce(tuple(line, lineHash));
  }

  @Test
  void should_match_by_line_and_message() {
    var base = builder().ruleKey("dummy ruleKey");
    var line = 7;
    var message = "should make this condition not always false";
    tracker.matchAndTrackAsNew(file1, Collections.singletonList(base.copy().line(line).message(message).build()));

    var differentLine = base.line(line + 1).message(message).build();
    var differentMessage = base.line(line).message(message + "x").build();
    var differentBoth = base.line(line + 1).message(message + "x").build();
    var same = base.line(line).message(message).build();
    tracker.matchAndTrackAsNew(file1, Arrays.asList(differentLine, differentMessage, differentBoth, same));

    Collection<Trackable> current = cache.getCurrentTrackables(file1);
    assertThat(current).hasSize(4);
    assertThat(current)
      .extracting("line", "message")
      .containsOnlyOnce(tuple(line, message));
  }

  @Test
  void should_match_by_text_range_hash() {
    var base = builder().ruleKey("dummy ruleKey").textRange(new TextRangeWithHash(1, 2, 3, 4, "md5"));
    var newLine = 7;

    tracker.matchAndTrackAsNew(file1, Collections.singletonList(base.copy().line(newLine + 3).build()));
    tracker.matchAndTrackAsNew(file1, Collections.singletonList(base.line(newLine).build()));

    assertThat(cache.getCurrentTrackables(file1))
      .extracting("line")
      .containsExactly(newLine);
  }

  @Test
  void should_match_by_line_hash() {
    var base = builder().ruleKey("dummy ruleKey").lineHash("md5");
    var newLine = 7;

    tracker.matchAndTrackAsNew(file1, Collections.singletonList(base.copy().line(newLine + 3).build()));
    tracker.matchAndTrackAsNew(file1, Collections.singletonList(base.line(newLine).build()));

    assertThat(cache.getCurrentTrackables(file1))
      .extracting("line")
      .containsExactly(newLine);
  }

  @Test
  void should_match_local_issues_by_line_hash() {
    var lineHash = "dummy content";
    var newLine = 7;

    var trackable = builder().line(newLine + 3).lineHash(lineHash).build();
    var movedTrackable = builder().line(newLine).lineHash(lineHash).build();
    var nonMatchingTrackable = builder().lineHash((lineHash + "x")).build();

    tracker.matchAndTrackAsNew(file1, Collections.singletonList(trackable));
    tracker.matchAndTrackAsNew(file1, Arrays.asList(movedTrackable, nonMatchingTrackable));

    assertThat(movedTrackable.getLineHash()).isEqualTo(trackable.getLineHash());
    assertThat(movedTrackable.getLineHash()).isNotEqualTo(nonMatchingTrackable.getLineHash());

    Collection<Trackable> next = cache.getCurrentTrackables(file1);

    // matched trackable has no date
    assertThat(next.stream().filter(t -> t.getCreationDate() == null))
      .extracting("line", "lineHash")
      .containsOnly(tuple(movedTrackable.getLine(), movedTrackable.getLineHash()));

    // unmatched trackable has a date -> it is a leak
    assertThat(next.stream().filter(t -> t.getCreationDate() != null))
      .extracting("line", "lineHash")
      .containsOnly(tuple(nonMatchingTrackable.getLine(), nonMatchingTrackable.getLineHash()));
  }

  @Test
  void should_match_server_issues_by_line_hash() {
    var ruleKey = "dummy ruleKey";
    var message = "dummy message";
    var lineHash = "md5";
    var newLine = 7;

    var trackable = builder().ruleKey(ruleKey).message(message).line(newLine).lineHash(lineHash).build();
    var movedTrackable = builder().line(newLine).lineHash(lineHash).build();
    var nonMatchingTrackable = builder().lineHash((lineHash + "x")).build();

    tracker.matchAndTrackAsNew(file1, Collections.singletonList(trackable));
    tracker.matchAndTrackAsBase(file1, Arrays.asList(movedTrackable, nonMatchingTrackable));

    assertThat(movedTrackable.getLineHash()).isEqualTo(trackable.getLineHash());
    assertThat(movedTrackable.getLineHash()).isNotEqualTo(nonMatchingTrackable.getLineHash());

    assertThat(cache.getCurrentTrackables(file1))
      .extracting("line", "lineHash", "serverIssueKey", "resolved")
      .containsOnly(tuple(newLine, movedTrackable.getLineHash(), movedTrackable.getServerIssueKey(), movedTrackable.isResolved()));
  }

  @Test
  void should_match_by_server_issue_key() {
    var base = builder().ruleKey("dummy ruleKey").serverIssueKey("dummy server issue key");
    var newLine = 7;

    tracker.matchAndTrackAsNew(file1, Collections.singletonList(base.copy().line(newLine + 3).build()));
    tracker.matchAndTrackAsNew(file1, Collections.singletonList(base.line(newLine).build()));

    assertThat(cache.getCurrentTrackables(file1))
      .extracting("line")
      .containsExactly(newLine);
  }

  @Test
  void should_preserve_creation_date() {
    var base = builder().ruleKey("dummy ruleKey").line(7);
    var creationDate = 123L;

    tracker.matchAndTrackAsNew(file1, Collections.singletonList(base.copy().creationDate(creationDate).build()));
    tracker.matchAndTrackAsNew(file1, Collections.singletonList(base.build()));

    assertThat(cache.getCurrentTrackables(file1))
      .extracting("creationDate")
      .containsExactly(creationDate);
  }

  @Test
  void should_preserve_creation_date_of_leaked_issues_in_connected_mode() {
    Long leakCreationDate = 1L;
    var leak = builder().ruleKey("dummy ruleKey").line(7).creationDate(leakCreationDate).build();

    // fake first analysis, trackable has a date
    tracker.matchAndTrackAsNew(file1, Collections.singletonList(leak));

    // fake server issue tracking
    tracker.matchAndTrackAsBase(file1, Collections.emptyList());

    assertThat(cache.getCurrentTrackables(file1)).extracting("creationDate").containsOnly(leakCreationDate);
  }

  @Test
  void should_preserve_server_issue_details() {
    var base = builder().ruleKey("dummy ruleKey").line(7);
    var serverIssueKey = "dummy serverIssueKey";
    var resolved = true;

    tracker.matchAndTrackAsNew(file1, Collections.singletonList(base.copy().serverIssueKey(serverIssueKey).resolved(resolved).build()));
    tracker.matchAndTrackAsNew(file1, Collections.singletonList(base.build()));

    assertThat(cache.getCurrentTrackables(file1))
      .extracting("serverIssueKey", "resolved")
      .containsExactly(tuple(serverIssueKey, resolved));
  }

  @Test
  void should_drop_server_issue_reference_if_gone() {
    var base = builder().ruleKey("dummy ruleKey").line(7);
    var serverIssueKey = "dummy serverIssueKey";
    var resolved = true;

    tracker.matchAndTrackAsNew(file1, Collections.singletonList(base.copy().serverIssueKey(serverIssueKey).resolved(resolved).build()));
    tracker.matchAndTrackAsBase(file1, Collections.singletonList(base.build()));

    assertThat(cache.getCurrentTrackables(file1))
      .extracting("serverIssueKey", "resolved")
      .containsExactly(tuple(null, false));
  }

  @Test
  void should_update_server_issue_details() {
    var serverIssueKey = "dummy serverIssueKey";
    var resolved = true;
    var base = builder().ruleKey("dummy ruleKey").serverIssueKey(serverIssueKey).resolved(resolved);

    tracker.matchAndTrackAsNew(file1, Collections.singletonList(base.copy().resolved(!resolved).build()));
    tracker.matchAndTrackAsBase(file1, Collections.singletonList(base.build()));

    assertThat(cache.getCurrentTrackables(file1))
      .extracting("serverIssueKey", "resolved")
      .containsExactly(tuple(serverIssueKey, resolved));
  }

  @Test
  void should_clear_server_issue_details_if_disappeared() {
    var resolved = true;
    var serverIssueTrackable = builder().ruleKey("dummy ruleKey")
      .serverIssueKey("dummy serverIssueKey").resolved(resolved).creationDate(1L).build();

    var start = System.currentTimeMillis();

    tracker.matchAndTrackAsNew(file1, Collections.emptyList());
    tracker.matchAndTrackAsNew(file1, Collections.singletonList(serverIssueTrackable));

    Collection<Trackable> trackables = cache.getCurrentTrackables(file1);
    assertThat(trackables)
      .extracting("serverIssueKey", "resolved")
      .containsExactly(tuple(null, !resolved));
    assertThat(trackables.iterator().next().getCreationDate()).isGreaterThanOrEqualTo(start);
  }

  @Test
  void should_ignore_server_issues_when_there_are_no_local() {
    var serverIssueKey = "dummy serverIssueKey";
    var resolved = true;
    var base = builder().ruleKey("dummy ruleKey").serverIssueKey(serverIssueKey).resolved(resolved);

    tracker.matchAndTrackAsNew(file1, Collections.emptyList());
    tracker.matchAndTrackAsBase(file1, Collections.singletonList(base.build()));

    assertThat(cache.getCurrentTrackables(file1)).isEmpty();
  }
}
