/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2022 SonarSource SA
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

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import org.sonarsource.sonarlint.core.commons.objectstore.HashingPathMapper;
import org.sonarsource.sonarlint.core.commons.objectstore.ObjectStore;
import org.sonarsource.sonarlint.core.commons.objectstore.Reader;
import org.sonarsource.sonarlint.core.commons.objectstore.SimpleObjectStore;
import org.sonarsource.sonarlint.core.commons.objectstore.Writer;
import org.sonarsource.sonarlint.core.serverconnection.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public class ProtobufServerIssueStore implements ServerIssueStore {
  private final ObjectStore<String, List<Sonarlint.ServerIssue>> store;

  public ProtobufServerIssueStore(Path base) {
    var pathGenerator = new HashingPathMapper(base, 2);

    Reader<List<Sonarlint.ServerIssue>> reader = input -> ProtobufUtil.readMessages(input,
      Sonarlint.ServerIssue.parser());

    Writer<List<Sonarlint.ServerIssue>> writer = ProtobufUtil::writeMessages;

    store = new SimpleObjectStore<>(pathGenerator, reader, writer);
  }

  @Override
  public synchronized void save(List<ServerIssue> issues) {
    // organize everything in memory to avoid making an IO access per issue
    Map<String, List<ServerIssue>> issuesPerFile = issues.stream()
      .collect(Collectors.groupingBy(ServerIssue::getFilePath));

    for (Map.Entry<String, List<ServerIssue>> entry : issuesPerFile.entrySet()) {
      try {
        store.write(entry.getKey(), entry.getValue().stream().map(ProtobufServerIssueStore::toProtobufIssue).collect(toList()));
      } catch (IOException e) {
        throw new StorageException("failed to save issues for fileKey = " + entry.getKey(), e);
      }
    }
  }

  @Override
  public synchronized List<ServerIssue> load(String sqFilePath) {
    try {
      return store.read(sqFilePath)
        .map(pbIssues -> pbIssues.stream().map(ProtobufServerIssueStore::toApiIssue).collect(toList()))
        .orElse(Collections.emptyList());
    } catch (IOException e) {
      throw new StorageException("failed to load issues for fileKey = " + sqFilePath, e);
    }
  }

  private static ServerIssue toApiIssue(Sonarlint.ServerIssue pbIssue) {
    ServerIssue.TextRange textRange = null;
    String codeSnippet = null;
    if (pbIssue.getPrimaryLocation().hasTextRange()) {
      var pbTextRange = pbIssue.getPrimaryLocation().getTextRange();
      textRange = new ServerIssue.TextRange(pbTextRange.getStartLine(), pbTextRange.getStartLineOffset(), pbTextRange.getEndLine(), pbTextRange.getEndLineOffset());
      codeSnippet = trimToNull(pbIssue.getPrimaryLocation().getCodeSnippet());
    }
    var issue = new ServerIssue(
      pbIssue.getKey(),
      !pbIssue.getResolution().isEmpty(),
      pbIssue.getRuleRepository() + ":" + pbIssue.getRuleKey(),
      pbIssue.getPrimaryLocation().getMsg(),
      pbIssue.getLineHash(),
      pbIssue.getPrimaryLocation().getPath(),
      Instant.ofEpochMilli(pbIssue.getCreationDate()),
      pbIssue.getSeverity(),
      pbIssue.getType(),
      textRange);
    issue.setCodeSnippet(codeSnippet);
    for (Sonarlint.ServerIssue.Flow f : pbIssue.getFlowList()) {
      issue.getFlows().add(new ServerIssue.Flow(f.getLocationList().stream()
        .map(i -> new ServerIssue.ServerIssueLocation(i.getPath(),
          i.hasTextRange() ? convert(i.getTextRange()) : null,
          i.getMsg(), trimToNull(i.getCodeSnippet())))
        .collect(toList())));
    }
    return issue;
  }

  private static ServerIssue.TextRange convert(Sonarlint.ServerIssue.TextRange serverStorageTextRange) {
    return new ServerIssue.TextRange(
      serverStorageTextRange.getStartLine(),
      serverStorageTextRange.getStartLineOffset(),
      serverStorageTextRange.getEndLine(),
      serverStorageTextRange.getEndLineOffset());
  }

  private static Sonarlint.ServerIssue toProtobufIssue(ServerIssue issue) {
    var ruleKey = RuleKey.parse(issue.ruleKey());
    var codeSnippet = issue.getCodeSnippet();
    if (codeSnippet == null) {
      codeSnippet = "";
    }
    var builder = Sonarlint.ServerIssue.newBuilder()
      .setLineHash(issue.lineHash())
      .setCreationDate(issue.creationDate().toEpochMilli())
      .setKey(issue.key())
      .setPrimaryLocation(Sonarlint.ServerIssue.Location.newBuilder()
        .setPath(issue.getFilePath())
        .setCodeSnippet(codeSnippet)
        .setTextRange(convert(issue.getTextRange()))
        .build())
      .setResolution(issue.resolved() ? "RESOLVED" : "")
      .setRuleKey(ruleKey.rule())
      .setRuleRepository(ruleKey.repository())
      .setSeverity(issue.severity())
      .setType(issue.type());

    return builder.build();
  }

  private static Sonarlint.ServerIssue.TextRange convert(ServerIssue.TextRange textRange) {
    var startLine = textRange.getStartLine();
    var startLineOffset = textRange.getStartLineOffset();
    var endLine = textRange.getEndLine();
    var endLineOffset = textRange.getEndLineOffset();
    return Sonarlint.ServerIssue.TextRange.newBuilder()
      .setStartLine(startLine == null ? 0 : startLine)
      .setStartLineOffset(startLineOffset == null ? 0 : startLineOffset)
      .setEndLine(endLine == null ? 0 : endLine)
      .setEndLineOffset(endLineOffset == null ? 0 : endLineOffset)
      .build();
  }
}
