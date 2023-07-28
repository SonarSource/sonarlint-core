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
package mediumtest.fixtures.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import jetbrains.exodus.entitystore.PersistentEntityStores;
import jetbrains.exodus.env.Environments;
import jetbrains.exodus.util.CompressBackupUtil;
import org.sonarsource.sonarlint.core.commons.IssueStatus;
import org.sonarsource.sonarlint.core.local.only.IssueStatusBinding;
import org.sonarsource.sonarlint.core.local.only.UuidBinding;
import org.sonarsource.sonarlint.core.serverconnection.storage.InstantBinding;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;

import static java.util.Objects.requireNonNull;

public class ConfigurationScopeStorageFixture {
  public static ConfigurationScopeStorageBuilder newBuilder(String configScopeId) {
    return new ConfigurationScopeStorageBuilder(configScopeId);
  }

  public static class ConfigurationScopeStorageBuilder {
    private final List<LocalOnlyIssue> localOnlyIssues = new ArrayList<>();
    private final String configScopeId;

    public ConfigurationScopeStorageBuilder(String configScopeId) {
      this.configScopeId = configScopeId;
    }

    public ConfigurationScopeStorageBuilder withLocalOnlyIssue(LocalOnlyIssue issue) {
      localOnlyIssues.add(issue);
      return this;
    }

    public void create(Path storageRoot) {
      var xodusTempDbPath = storageRoot.resolve("xodus_temp_db");
      var xodusBackupPath = storageRoot.resolve("local_only_issue_backup.tar.gz");
      try {
        Files.createDirectories(xodusBackupPath.getParent());
      } catch (IOException e) {
        throw new IllegalStateException("Unable to create the Xodus backup parent folders", e);
      }
      var environment = Environments.newInstance(xodusTempDbPath.toAbsolutePath().toFile());
      var entityStore = PersistentEntityStores.newInstance(environment);
      entityStore.executeInTransaction(txn -> {
        entityStore.registerCustomPropertyType(txn, Instant.class, new InstantBinding());
        entityStore.registerCustomPropertyType(txn, UUID.class, new UuidBinding());
        entityStore.registerCustomPropertyType(txn, IssueStatus.class, new IssueStatusBinding());
        var scopeEntity = txn.newEntity("Scope");
        localOnlyIssues.stream()
          .collect(Collectors.groupingBy(LocalOnlyIssue::getServerRelativePath))
          .forEach((filePath, localOnlyIssues) -> {
            var fileEntity = txn.newEntity("File");
            localOnlyIssues.forEach(issue -> {
              var issueEntity = txn.newEntity("Issue");
              issueEntity.setProperty("uuid", issue.getId());
              issueEntity.setProperty("ruleKey", issue.getRuleKey());
              issueEntity.setBlobString("message", issue.getMessage());
              var resolution = requireNonNull(issue.getResolution());
              issueEntity.setProperty("resolvedStatus", resolution.getStatus());
              issueEntity.setProperty("resolvedDate", resolution.getResolutionDate());
              var comment = resolution.getComment();
              if (comment != null) {
                issueEntity.setBlobString("comment", comment);
              }
              var textRange = issue.getTextRangeWithHash();
              var lineWithHash = issue.getLineWithHash();
              if (textRange != null) {
                issueEntity.setProperty("startLine", textRange.getStartLine());
                issueEntity.setProperty("startLineOffset", textRange.getStartLineOffset());
                issueEntity.setProperty("endLine", textRange.getEndLine());
                issueEntity.setProperty("endLineOffset", textRange.getEndLineOffset());
                issueEntity.setProperty("rangeHash", textRange.getHash());
              }
              if (lineWithHash != null) {
                issueEntity.setProperty("lineHash", lineWithHash.getHash());
              }

              issueEntity.setLink("file", fileEntity);
              fileEntity.addLink("issues", issueEntity);
            });

            scopeEntity.setProperty("name", configScopeId);
            fileEntity.setProperty("path", filePath);
            scopeEntity.addLink("files", fileEntity);
          });
      });
      try {
        CompressBackupUtil.backup(entityStore, xodusBackupPath.toFile(), false);
      } catch (Exception e) {
        throw new IllegalStateException("Unable to backup server issue database", e);
      }
    }
  }
}
