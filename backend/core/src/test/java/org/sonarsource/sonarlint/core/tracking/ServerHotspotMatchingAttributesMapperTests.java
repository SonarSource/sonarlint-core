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
package org.sonarsource.sonarlint.core.tracking;

import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;

import static org.assertj.core.api.Assertions.assertThat;

class ServerHotspotMatchingAttributesMapperTests {

  @Test
  void should_delegate_fields_to_server_issue() {
    var creationDate = Instant.now();
    var textRange = new TextRangeWithHash(1, 2, 3, 4, "realHash");
    var serverHotspot = new ServerHotspot("key", "ruleKey", "message", Path.of("filePath"), textRange, creationDate, HotspotReviewStatus.SAFE, VulnerabilityProbability.LOW, null);

    var underTest = new ServerHotspotMatchingAttributesMapper();

    assertThat(underTest.getServerIssueKey(serverHotspot)).contains("key");
    assertThat(underTest.getMessage(serverHotspot)).isEqualTo("message");
    assertThat(underTest.getLineHash(serverHotspot)).isEmpty();
    assertThat(underTest.getRuleKey(serverHotspot)).isEqualTo("ruleKey");
    assertThat(underTest.getLine(serverHotspot)).contains(1);
    assertThat(underTest.getTextRangeHash(serverHotspot)).contains(textRange.getHash());
  }
}
