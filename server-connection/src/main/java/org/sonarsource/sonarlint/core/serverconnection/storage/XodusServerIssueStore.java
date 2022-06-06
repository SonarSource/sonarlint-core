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

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.EntityIterable;
import jetbrains.exodus.entitystore.PersistentEntityStore;
import jetbrains.exodus.entitystore.PersistentEntityStores;
import jetbrains.exodus.entitystore.StoreTransaction;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.serverconnection.ServerIssue;

import static java.util.Objects.requireNonNull;

public class XodusServerIssueStore implements ServerIssueStore {
  private static final String FILE_NAME = "sonarlint.db";

  private static final String ISSUE_ENTITY_TYPE = "Issue";
  private static final String FILE_ENTITY_TYPE = "File";
  private static final String PROJECT_ENTITY_TYPE = "Project";
  private static final String FLOW_ENTITY_TYPE = "Flow";
  private static final String LOCATION_ENTITY_TYPE = "Location";

  private static final String PROJECT_TO_FILES_LINK_NAME = "files";
  private static final String FILE_TO_ISSUES_LINK_NAME = "issues";
  private static final String ISSUE_TO_FILE_LINK_NAME = "file";
  private static final String ISSUE_TO_FLOWS_LINK_NAME = "flows";
  private static final String FLOW_TO_LOCATIONS_LINK_NAME = "locations";

  private static final String START_LINE_PROPERTY_NAME = "startLine";
  private static final String START_LINE_OFFSET_PROPERTY_NAME = "startLineOffset";
  private static final String END_LINE_PROPERTY_NAME = "endLine";
  private static final String END_LINE_OFFSET_PROPERTY_NAME = "endLineOffset";
  private static final String CODE_SNIPPET_PROPERTY_NAME = "codeSnippet";
  private static final String KEY_PROPERTY_NAME = "key";
  private static final String RESOLVED_PROPERTY_NAME = "resolved";
  private static final String RULE_KEY_PROPERTY_NAME = "ruleKey";
  private static final String MESSAGE_PROPERTY_NAME = "message";
  private static final String LINE_HASH_PROPERTY_NAME = "lineHash";
  private static final String FILE_PATH_PROPERTY_NAME = "filePath";
  private static final String CREATION_DATE_PROPERTY_NAME = "creationDate";
  private static final String SEVERITY_PROPERTY_NAME = "severity";
  private static final String TYPE_PROPERTY_NAME = "type";
  private static final String PATH_PROPERTY_NAME = "path";

  private final PersistentEntityStore entityStore;

  public XodusServerIssueStore(Path baseDir) {
    entityStore = PersistentEntityStores.newInstance(baseDir.resolve(FILE_NAME).toAbsolutePath().toString());
  }

  public Optional<ServerIssue> getByKey(String issueKey) {
    return entityStore.computeInTransaction(txn -> findUnique(txn, ISSUE_ENTITY_TYPE, KEY_PROPERTY_NAME, issueKey)
      .map(XodusServerIssueStore::adapt));
  }

  @CheckForNull
  private static ServerIssue adapt(@Nullable Entity storedIssue) {
    if (storedIssue == null) {
      return null;
    }
    var startLine = storedIssue.getProperty(START_LINE_PROPERTY_NAME);
    ServerIssue.TextRange textRange = null;
    if (startLine != null) {
      var startLineOffset = (Integer) storedIssue.getProperty(START_LINE_OFFSET_PROPERTY_NAME);
      var endLine = (Integer) storedIssue.getProperty(END_LINE_PROPERTY_NAME);
      var endLineOffset = (Integer) storedIssue.getProperty(END_LINE_OFFSET_PROPERTY_NAME);
      textRange = new ServerIssue.TextRange((int) startLine, startLineOffset, endLine, endLineOffset);
    }
    return new ServerIssue(
      (String) requireNonNull(storedIssue.getProperty(KEY_PROPERTY_NAME)),
      storedIssue.getProperty(RESOLVED_PROPERTY_NAME) != null,
      (String) requireNonNull(storedIssue.getProperty(RULE_KEY_PROPERTY_NAME)),
      (String) requireNonNull(storedIssue.getProperty(MESSAGE_PROPERTY_NAME)),
      (String) requireNonNull(storedIssue.getProperty(LINE_HASH_PROPERTY_NAME)),
      (String) requireNonNull(storedIssue.getProperty(FILE_PATH_PROPERTY_NAME)),
      Instant.parse((String) requireNonNull(storedIssue.getProperty(CREATION_DATE_PROPERTY_NAME))),
      (String) requireNonNull(storedIssue.getProperty(SEVERITY_PROPERTY_NAME)),
      (String) requireNonNull(storedIssue.getProperty(TYPE_PROPERTY_NAME)),
      textRange)
        .setCodeSnippet((String) storedIssue.getProperty(CODE_SNIPPET_PROPERTY_NAME))
        .setFlows(StreamSupport.stream(storedIssue.getLinks(ISSUE_TO_FLOWS_LINK_NAME).spliterator(), false).map(XodusServerIssueStore::adaptFlow).collect(Collectors.toList()));
  }

  private static ServerIssue.Flow adaptFlow(Entity flowEntity) {
    return new ServerIssue.Flow(
      StreamSupport.stream(flowEntity.getLinks(FLOW_TO_LOCATIONS_LINK_NAME).spliterator(), false).map(XodusServerIssueStore::adaptLocation).collect(Collectors.toList()));
  }

  private static ServerIssue.ServerIssueLocation adaptLocation(Entity locationEntity) {
    var startLine = locationEntity.getProperty(START_LINE_PROPERTY_NAME);
    ServerIssue.TextRange textRange = null;
    if (startLine != null) {
      var startLineOffset = (Integer) locationEntity.getProperty(START_LINE_OFFSET_PROPERTY_NAME);
      var endLine = (Integer) locationEntity.getProperty(END_LINE_PROPERTY_NAME);
      var endLineOffset = (Integer) locationEntity.getProperty(END_LINE_OFFSET_PROPERTY_NAME);
      textRange = new ServerIssue.TextRange((int) startLine, startLineOffset, endLine, endLineOffset);
    }
    return new ServerIssue.ServerIssueLocation(
      (String) locationEntity.getProperty(FILE_PATH_PROPERTY_NAME),
      textRange,
      (String) locationEntity.getProperty(MESSAGE_PROPERTY_NAME),
      (String) locationEntity.getProperty(CODE_SNIPPET_PROPERTY_NAME));
  }

  @Override
  public List<ServerIssue> load(String projectKey, String filePath) {
    return entityStore.computeInTransaction(txn -> findUnique(txn, PROJECT_ENTITY_TYPE, KEY_PROPERTY_NAME, projectKey)
      .map(project -> project.getLinks(PROJECT_TO_FILES_LINK_NAME))
      .flatMap(files -> findUnique(txn, FILE_ENTITY_TYPE, PATH_PROPERTY_NAME, filePath))
      .map(fileToLoad -> fileToLoad.getLinks(FILE_TO_ISSUES_LINK_NAME))
      .map(issueEntities -> StreamSupport.stream(issueEntities.spliterator(), false)
        .map(XodusServerIssueStore::adapt)
        .collect(Collectors.toList()))
      .orElseGet(Collections::emptyList));
  }

  @Override
  public void save(String projectKey, List<ServerIssue> issues) {
    entityStore.executeInTransaction(txn -> {
      var issuesByFile = issues.stream().collect(Collectors.groupingBy(ServerIssue::getFilePath));
      issuesByFile.forEach((filePath, fileIssues) -> {
        var project = getOrCreateProject(projectKey, txn);
        var file = getOrCreateFile(project, filePath, txn);
        fileIssues.forEach(issue -> updateOrCreateIssue(file, issue, txn));
      });
    });
  }

  private static Entity getOrCreateProject(String projectKey, StoreTransaction txn) {
    return findUnique(txn, PROJECT_ENTITY_TYPE, KEY_PROPERTY_NAME, projectKey)
      .orElseGet(() -> {
        var project = txn.newEntity(PROJECT_ENTITY_TYPE);
        project.setProperty(KEY_PROPERTY_NAME, projectKey);
        return project;
      });
  }

  private static Entity getOrCreateFile(Entity projectEntity, String filePath, StoreTransaction txn) {
    var fileIterable = projectEntity.getLinks(PROJECT_TO_FILES_LINK_NAME).intersect(findAll(txn, FILE_ENTITY_TYPE, PATH_PROPERTY_NAME, filePath));
    return Optional.ofNullable(fileIterable.getFirst())
      .orElseGet(() -> {
        var file = txn.newEntity(FILE_ENTITY_TYPE);
        file.setProperty(PATH_PROPERTY_NAME, filePath);
        projectEntity.addLink(PROJECT_TO_FILES_LINK_NAME, file);
        return file;
      });
  }

  private static void updateOrCreateIssue(Entity fileEntity, ServerIssue issue, StoreTransaction transaction) {
    var issueEntity = findUnique(transaction, ISSUE_ENTITY_TYPE, KEY_PROPERTY_NAME, issue.key())
      .orElseGet(() -> transaction.newEntity(ISSUE_ENTITY_TYPE));
    var oldFileEntity = issueEntity.getLink(ISSUE_TO_FILE_LINK_NAME);
    if (oldFileEntity != null && !fileEntity.equals(oldFileEntity)) {
      // issue might have moved file
      oldFileEntity.deleteLink(FILE_TO_ISSUES_LINK_NAME, issueEntity);
    }
    issueEntity.setProperty(KEY_PROPERTY_NAME, issue.key());
    issueEntity.setProperty(RESOLVED_PROPERTY_NAME, issue.resolved());
    issueEntity.setProperty(RULE_KEY_PROPERTY_NAME, issue.ruleKey());
    issueEntity.setProperty(MESSAGE_PROPERTY_NAME, issue.getMessage());
    issueEntity.setProperty(LINE_HASH_PROPERTY_NAME, issue.lineHash());
    issueEntity.setProperty(FILE_PATH_PROPERTY_NAME, issue.getFilePath());
    issueEntity.setProperty(CREATION_DATE_PROPERTY_NAME, issue.creationDate().toString());
    issueEntity.setProperty(SEVERITY_PROPERTY_NAME, issue.severity());
    issueEntity.setProperty(TYPE_PROPERTY_NAME, issue.type());
    var textRange = issue.getTextRange();
    if (textRange != null) {
      var startLine = textRange.getStartLine();
      if (startLine != null) {
        issueEntity.setProperty(START_LINE_PROPERTY_NAME, startLine);
      }
      var startLineOffset = textRange.getStartLineOffset();
      if (startLineOffset != null) {
        issueEntity.setProperty(START_LINE_OFFSET_PROPERTY_NAME, startLineOffset);
      }
      var endLine = textRange.getEndLine();
      if (endLine != null) {
        issueEntity.setProperty(END_LINE_PROPERTY_NAME, endLine);
      }
      var endLineOffset = textRange.getEndLineOffset();
      if (endLineOffset != null) {
        issueEntity.setProperty(END_LINE_OFFSET_PROPERTY_NAME, endLineOffset);
      }
    }
    var codeSnippet = issue.getCodeSnippet();
    if (codeSnippet != null) {
      issueEntity.setProperty(CODE_SNIPPET_PROPERTY_NAME, codeSnippet);
    }
    issueEntity.setLink(ISSUE_TO_FILE_LINK_NAME, fileEntity);
    fileEntity.addLink(FILE_TO_ISSUES_LINK_NAME, issueEntity);
    deleteFlowAndLocations(issueEntity);
    issue.getFlows().forEach(flow -> storeFlow(flow, issueEntity, transaction));
  }

  private static Optional<Entity> findUnique(StoreTransaction transaction, String entityType, String propertyName, String caseSensitivePropertyValue) {
    // the find is case-insensitive but we need an exact match
    var entities = transaction.find(entityType, propertyName, caseSensitivePropertyValue);
    return StreamSupport.stream(entities.spliterator(), false)
      .filter(e -> caseSensitivePropertyValue.equals(e.getProperty(propertyName)))
      .findFirst();
  }

  private static EntityIterable findAll(StoreTransaction transaction, String entityType, String propertyName, String propertyValue) {
    // the find is case-insensitive but we need an exact match
    var entities = transaction.find(entityType, propertyName, propertyValue);

    var entityIterator = entities.iterator();
    while (entityIterator.hasNext()) {
      if (!propertyValue.equals(entityIterator.next().getProperty(propertyName))) {
        entityIterator.remove();
      }
    }
    return entities;
  }

  private static void storeFlow(ServerIssue.Flow flow, Entity issueEntity, StoreTransaction transaction) {
    var flowEntity = transaction.newEntity(FLOW_ENTITY_TYPE);
    issueEntity.addLink(ISSUE_TO_FLOWS_LINK_NAME, flowEntity);
    flow.locations().forEach(location -> storeLocation(location, flowEntity, transaction));
  }

  private static void storeLocation(ServerIssue.ServerIssueLocation location, Entity flowEntity, StoreTransaction transaction) {
    var locationEntity = transaction.newEntity(LOCATION_ENTITY_TYPE);
    flowEntity.addLink(FLOW_TO_LOCATIONS_LINK_NAME, locationEntity);
    locationEntity.setProperty(MESSAGE_PROPERTY_NAME, location.getMessage());
    locationEntity.setProperty(FILE_PATH_PROPERTY_NAME, location.getFilePath());
    var locationCodeSnippet = location.getCodeSnippet();
    if (locationCodeSnippet != null) {
      locationEntity.setProperty(CODE_SNIPPET_PROPERTY_NAME, locationCodeSnippet);
    }
    var locationTextRange = location.getTextRange();
    if (locationTextRange != null) {
      var startLine = locationTextRange.getStartLine();
      if (startLine != null) {
        locationEntity.setProperty(START_LINE_PROPERTY_NAME, startLine);
      }
      var startLineOffset = locationTextRange.getStartLineOffset();
      if (startLineOffset != null) {
        locationEntity.setProperty(START_LINE_OFFSET_PROPERTY_NAME, startLineOffset);
      }
      var endLine = locationTextRange.getEndLine();
      if (endLine != null) {
        locationEntity.setProperty(END_LINE_PROPERTY_NAME, endLine);
      }
      var endLineOffset = locationTextRange.getEndLineOffset();
      if (endLineOffset != null) {
        locationEntity.setProperty(END_LINE_OFFSET_PROPERTY_NAME, endLineOffset);
      }
    }
  }

  public void removeAll(Collection<String> issueKeys) {
    entityStore.executeInTransaction(txn -> issueKeys.forEach(issueKey -> remove(issueKey, txn)));
  }

  private static void remove(String issueKey, @NotNull StoreTransaction txn) {
    findUnique(txn, ISSUE_ENTITY_TYPE, KEY_PROPERTY_NAME, issueKey)
      .ifPresent(issueEntity -> {
        var fileEntity = issueEntity.getLink(ISSUE_TO_FILE_LINK_NAME);
        if (fileEntity != null) {
          fileEntity.deleteLink(FILE_TO_ISSUES_LINK_NAME, issueEntity);
        }
        issueEntity.deleteLinks(ISSUE_TO_FILE_LINK_NAME);
        deleteFlowAndLocations(issueEntity);
        issueEntity.delete();
      });
  }

  private static void deleteFlowAndLocations(Entity issueEntity) {
    issueEntity.getLinks(ISSUE_TO_FLOWS_LINK_NAME).forEach(flow -> {
      flow.getLinks(FLOW_TO_LOCATIONS_LINK_NAME).forEach(Entity::delete);
      flow.deleteLinks(FLOW_TO_LOCATIONS_LINK_NAME);
      flow.delete();
    });
    issueEntity.deleteLinks(ISSUE_TO_FLOWS_LINK_NAME);
  }

  public void close() {
    entityStore.close();
  }
}
