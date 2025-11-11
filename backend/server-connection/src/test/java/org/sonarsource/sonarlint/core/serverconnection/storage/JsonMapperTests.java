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

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonMapperTests {

  private final JsonMapper underTest = new JsonMapper();

  @Test
  void should_serialize_issue_impacts() {
    var impacts = new EnumMap<SoftwareQuality, ImpactSeverity>(SoftwareQuality.class);
    impacts.put(SoftwareQuality.MAINTAINABILITY, ImpactSeverity.HIGH);
    impacts.put(SoftwareQuality.SECURITY, ImpactSeverity.LOW);

    var json = underTest.serializeImpacts(impacts);

    assertEquals("{\"MAINTAINABILITY\":\"HIGH\",\"SECURITY\":\"LOW\"}", json);
    var impactsDeserialized = underTest.deserializeImpacts(JSON.valueOf(json));

    assertEquals(impacts, impactsDeserialized);
  }

  @Test
  void should_serialize_issue_flows() {
    var flows = new ArrayList<ServerTaintIssue.Flow>();
    var path = Path.of("/file/path");
    var uri = path.toUri().toString();
    flows.add(new ServerTaintIssue.Flow(List.of(
      new ServerTaintIssue.ServerIssueLocation(path,
        new TextRangeWithHash(1, 2,3,4, "hash1"), "Message 1"),
      new ServerTaintIssue.ServerIssueLocation(path,
        new TextRangeWithHash(5, 6,7,8, "hash2"), "Message 2"))));
    flows.add(new ServerTaintIssue.Flow(List.of(
      new ServerTaintIssue.ServerIssueLocation(path,
        new TextRangeWithHash(1, 2,3,4, "hash1"), "Message 1"))));
    var taint = new ServerTaintIssue(null, null, true, null, null, null, null,
      null, null,null, null,null, null,null);
    taint.setFlows(flows);

    var json = underTest.serializeFlows(taint);

    assertEquals("[{\"locations\":[{\"filePath\":\"" + uri + "\"," +
      "\"textRange\":{\"startLine\":1,\"startLineOffset\":2,\"endLine\":3,\"endLineOffset\":4,\"hash\":\"hash1\"},\"message\":\"Message 1\"}," +
      "{\"filePath\":\"" + uri + "\",\"textRange\":{\"startLine\":5,\"startLineOffset\":6,\"endLine\":7,\"endLineOffset\":8,\"hash\":\"hash2\"}," +
      "\"message\":\"Message 2\"}]},{\"locations\":[{\"filePath\":\"" + uri + "\"," +
      "\"textRange\":{\"startLine\":1,\"startLineOffset\":2,\"endLine\":3,\"endLineOffset\":4,\"hash\":\"hash1\"},\"message\":\"Message 1\"}]}]", json);
  }

}
