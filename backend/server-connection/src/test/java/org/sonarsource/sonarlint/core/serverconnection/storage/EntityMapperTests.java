/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import org.jooq.JSON;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.api.TextRangeWithHash;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;

import static org.assertj.core.api.Assertions.assertThat;

class EntityMapperTests {

  private final EntityMapper underTest = new EntityMapper();

  @Test
  void should_serialize_issue_impacts() {
    var impacts = new EnumMap<SoftwareQuality, ImpactSeverity>(SoftwareQuality.class);
    impacts.put(SoftwareQuality.MAINTAINABILITY, ImpactSeverity.HIGH);
    impacts.put(SoftwareQuality.SECURITY, ImpactSeverity.LOW);

    var json = underTest.serializeImpacts(impacts);

    assertThat(json.data()).isEqualTo("{\"MAINTAINABILITY\":\"HIGH\",\"SECURITY\":\"LOW\"}");
    var impactsDeserialized = underTest.deserializeImpacts(json);

    assertThat(impactsDeserialized).isEqualTo(impacts);
  }

  @Test
  void should_serialize_issue_flows() {
    var flows = new ArrayList<ServerTaintIssue.Flow>();
    var path = Path.of("file/path");
    var stringPath = path.toString().replace("\\", "\\\\");
    flows.add(new ServerTaintIssue.Flow(List.of(
      new ServerTaintIssue.ServerIssueLocation(path,
        new TextRangeWithHash(1, 2, 3, 4, "hash1"), "Message 1"),
      new ServerTaintIssue.ServerIssueLocation(path,
        new TextRangeWithHash(5, 6, 7, 8, "hash2"), "Message 2"))));
    flows.add(new ServerTaintIssue.Flow(List.of(
      new ServerTaintIssue.ServerIssueLocation(path,
        new TextRangeWithHash(1, 2, 3, 4, "hash1"), "Message 1"))));
    var taint = new ServerTaintIssue(null, null, true, null, null, null, null,
      null, null, null, null, null, null, null, flows);

    var json = underTest.serializeFlows(taint.getFlows());

    assertThat(json.data())
      .isEqualTo("[{\"locations\":[{\"filePath\":\"" + stringPath + "\"," +
        "\"textRange\":{\"startLine\":1,\"startLineOffset\":2,\"endLine\":3,\"endLineOffset\":4,\"hash\":\"hash1\"},\"message\":\"Message 1\"}," +
        "{\"filePath\":\"" + stringPath + "\",\"textRange\":{\"startLine\":5,\"startLineOffset\":6,\"endLine\":7,\"endLineOffset\":8,\"hash\":\"hash2\"}," +
        "\"message\":\"Message 2\"}]},{\"locations\":[{\"filePath\":\"" + stringPath + "\"," +
        "\"textRange\":{\"startLine\":1,\"startLineOffset\":2,\"endLine\":3,\"endLineOffset\":4,\"hash\":\"hash1\"},\"message\":\"Message 1\"}]}]");
  }

  @Test
  void should_deserialize_taint_flows() {
    var path = Path.of("file/path");
    var stringPath = path.toString().replace("\\", "\\\\");

    var flows = underTest.deserializeTaintFlows(JSON.valueOf("[{\"locations\":[{\"filePath\":\"" + stringPath + "\"," +
      "\"textRange\":{\"startLine\":1,\"startLineOffset\":2,\"endLine\":3,\"endLineOffset\":4,\"hash\":\"hash1\"},\"message\":\"Message 1\"}," +
      "{\"filePath\":\"" + stringPath + "\",\"textRange\":{\"startLine\":5,\"startLineOffset\":6,\"endLine\":7,\"endLineOffset\":8,\"hash\":\"hash2\"}," +
      "\"message\":\"Message 2\"}]},{\"locations\":[{\"filePath\":\"" + stringPath + "\"," +
      "\"textRange\":{\"startLine\":1,\"startLineOffset\":2,\"endLine\":3,\"endLineOffset\":4,\"hash\":\"hash1\"},\"message\":\"Message 1\"}]}]"));

    assertThat(flows).isEqualTo(List.of(
      new ServerTaintIssue.Flow(List.of(
        new ServerTaintIssue.ServerIssueLocation(path,
          new TextRangeWithHash(1, 2, 3, 4, "hash1"), "Message 1"),
        new ServerTaintIssue.ServerIssueLocation(path,
          new TextRangeWithHash(5, 6, 7, 8, "hash2"), "Message 2"))),
      new ServerTaintIssue.Flow(List.of(
        new ServerTaintIssue.ServerIssueLocation(path,
          new TextRangeWithHash(1, 2, 3, 4, "hash1"), "Message 1")))));
  }

}
