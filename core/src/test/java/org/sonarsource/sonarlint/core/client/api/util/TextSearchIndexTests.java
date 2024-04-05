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
package org.sonarsource.sonarlint.core.client.api.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextSearchIndexTests {
  private TextSearchIndex<String> index;

  @BeforeEach
  void setUp() {
    index = new TextSearchIndex<>();
  }

  @Test
  void testTokenizer() {
    index.index("o1", "org.sonarsource.sonarlint.intellij:sonarlint-intellij SonarLint Intellij");
    index.index("o2", "org.codehaus.sonar-plugins:sonar-scm-jazzrtc-plugin Jazz RTC SCM Plugin");

    assertThat(index.getTokens()).contains("org", "sonarsource", "sonarlint", "intellij",
      "codehaus", "sonar", "plugins", "scm", "jazzrtc", "plugin", "jazz", "rtc");
  }

  @Test
  void testSearch() {
    index.index("o1", "org.sonarsource.sonarlint.intellij:sonarlint-intellij SonarLint Intellij");
    index.index("o2", "org.codehaus.sonar-plugins:sonar-scm-jazzrtc-plugin Jazz RTC SCM Plugin");

    assertThat(index.search("org").keySet()).containsOnly("o1", "o2");
    assertThat(index.search("jazzrtc").keySet()).containsOnly("o2");
    assertThat(index.search("sonarlint").keySet()).containsOnly("o1");
    assertThat(index.search("plugin").keySet()).containsOnly("o2");
    assertThat(index.search("scm").keySet()).containsOnly("o2");
    assertThat(index.search("org.sonarsource.sonarlint.intellij:sonarlint-intellij").keySet()).contains("o1");
    assertThat(index.search("unknown")).isEmpty();
  }

  @Test
  void testSearchNoTerms() {
    assertThat(index.size()).isEqualTo(0);
    assertThat(index.isEmpty()).isTrue();
    index.index("o1", "org.sonarsource.sonarlint.intellij:sonarlint-intellij SonarLint Intellij");
    index.index("o2", "org.codehaus.sonar-plugins:sonar-scm-jazzrtc-plugin Jazz RTC SCM Plugin");

    assertThat(index.size()).isEqualTo(2);
    assertThat(index.isEmpty()).isFalse();
    assertThat(index.search(": .")).isEmpty();

  }

  @Test
  void clear() {
    index.index("o1", "org.sonarsource.sonarlint.intellij:sonarlint-intellij SonarLint Intellij");
    index.index("o2", "org.codehaus.sonar-plugins:sonar-scm-jazzrtc-plugin Jazz RTC SCM Plugin");

    index.clear();

    assertThat(index.getTokens()).isEmpty();
    assertThat(index.search("org")).isEmpty();

    // no errors
    index.index("o1", "org.sonarsource.sonarlint.intellij:sonarlint-intellij SonarLint Intellij");
    index.index("o2", "org.codehaus.sonar-plugins:sonar-scm-jazzrtc-plugin Jazz RTC SCM Plugin");
  }

  @Test
  void cantIndexTwice() {
    index.index("o1", "a");
    assertThrows(IllegalArgumentException.class, () -> {
      index.index("o1", "b");
    });
  }

  @Test
  void testMultiTermPositionalSearch() {
    index.index("o1", "org.sonarsource.sonarlint.intellij:sonarlint-intellij SonarLint Intellij");
    index.index("o2", "org.codehaus.sonar-plugins:sonar-scm-jazzrtc-plugin Jazz RTC SCM Plugin");

    assertThat(index.search("sonar-plugins").keySet()).contains("o2");

    // multi term matches partially
    assertThat(index.search("sona-plugins").keySet()).contains("o2");

    // max distance between terms is 1
    assertThat(index.search("org.plugins")).isEmpty();
  }

  @Test
  void testScoringPartialTermMatch() {
    index.index("o1", "org.codehaus.sonar-plugins");
    index.index("o2", "or.codehaus.sonar-plugins");
    index.index("o3", "org.codehau.sonar-plugins");

    // o2 has higher score than o3 because 's' missing in 'codehaus' has lower impact on score than 'g' missing in 'org'
    assertThat(index.search("or codehau").keySet()).containsExactly("o2", "o3", "o1");
  }

  @Test
  void testNoMultipleMatchesSameObj() {
    index.index("o1", "or.codehau.or.codehau.or.codehau");
    index.index("o2", "or.codehaus.sonar-plugins");
    index.index("o3", "org.codehau.sonar-plugins");

    // o1 should not accumulate score from multiple matches
    assertThat(index.search("or codehau").keySet()).containsExactly("o2", "o3", "o1");
  }

  @Test
  void testMatchSingleTerm() {
    index.index("o1", "mod1");
    index.index("o2", "mod10");
    index.index("o3", "mod103");

    assertThat(index.search("mod1").keySet()).containsExactly("o1", "o2", "o3");
    assertThat(index.search("mod10").keySet()).containsExactly("o2", "o3");
    assertThat(index.search("mod103").keySet()).containsExactly("o3");
  }
}
