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
import java.util.function.Function;
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
import org.sonarsource.sonarlint.core.serverconnection.ServerTaintIssue;

import static java.util.Objects.requireNonNull;

public class XodusServerIssueStore implements ServerIssueStore {
  private static final String FILE_NAME = "sonarlint.db";

  private static final String ISSUE_ENTITY_TYPE = "Issue";
  private static final String TAINT_ISSUE_ENTITY_TYPE = "Issue";
  private static final String FILE_ENTITY_TYPE = "File";
  private static final String PROJECT_ENTITY_TYPE = "Project";
  private static final String FLOW_ENTITY_TYPE = "Flow";
  private static final String LOCATION_ENTITY_TYPE = "Location";

  private static final String PROJECT_TO_FILES_LINK_NAME = "files";
  private static final String FILE_TO_ISSUES_LINK_NAME = "issues";
  private static final String FILE_TO_TAINT_ISSUES_LINK_NAME = "taintIssues";
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
  private static final String RANGE_HASH_PROPERTY_NAME = "rangeHash";
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

  public Optional<ServerTaintIssue> getTaintByKey(String issueKey) {
    return entityStore.computeInTransaction(txn -> findUnique(txn, TAINT_ISSUE_ENTITY_TYPE, KEY_PROPERTY_NAME, issueKey)
      .map(XodusServerIssueStore::adaptTaint));
  }

  @CheckForNull
  private static ServerIssue adapt(@Nullable Entity storedIssue) {
    if (storedIssue == null) {
      return null;
    }
    var rangeHash = (String) storedIssue.getProperty(RANGE_HASH_PROPERTY_NAME);
    if (rangeHash != null) {
      var startLine = storedIssue.getProperty(START_LINE_PROPERTY_NAME);
      var startLineOffset = (Integer) storedIssue.getProperty(START_LINE_OFFSET_PROPERTY_NAME);
      var endLine = (Integer) storedIssue.getProperty(END_LINE_PROPERTY_NAME);
      var endLineOffset = (Integer) storedIssue.getProperty(END_LINE_OFFSET_PROPERTY_NAME);
      ServerIssue.TextRange textRange = new ServerIssue.TextRange((int) startLine, startLineOffset, endLine, endLineOffset);
      return new ServerIssue(
        (String) requireNonNull(storedIssue.getProperty(KEY_PROPERTY_NAME)),
        Boolean.TRUE.equals(storedIssue.getProperty(RESOLVED_PROPERTY_NAME)),
        (String) requireNonNull(storedIssue.getProperty(RULE_KEY_PROPERTY_NAME)),
        (String) requireNonNull(storedIssue.getProperty(MESSAGE_PROPERTY_NAME)),
        rangeHash,
        (String) requireNonNull(storedIssue.getProperty(FILE_PATH_PROPERTY_NAME)),
        Instant.parse((String) requireNonNull(storedIssue.getProperty(CREATION_DATE_PROPERTY_NAME))),
        (String) requireNonNull(storedIssue.getProperty(SEVERITY_PROPERTY_NAME)),
        (String) requireNonNull(storedIssue.getProperty(TYPE_PROPERTY_NAME)),
        textRange);
    } else {
      return new ServerIssue(
        (String) requireNonNull(storedIssue.getProperty(KEY_PROPERTY_NAME)),
        Boolean.TRUE.equals(storedIssue.getProperty(RESOLVED_PROPERTY_NAME)),
        (String) requireNonNull(storedIssue.getProperty(RULE_KEY_PROPERTY_NAME)),
        (String) requireNonNull(storedIssue.getProperty(MESSAGE_PROPERTY_NAME)),
        (String) storedIssue.getProperty(LINE_HASH_PROPERTY_NAME),
        (String) requireNonNull(storedIssue.getProperty(FILE_PATH_PROPERTY_NAME)),
        Instant.parse((String) requireNonNull(storedIssue.getProperty(CREATION_DATE_PROPERTY_NAME))),
        (String) requireNonNull(storedIssue.getProperty(SEVERITY_PROPERTY_NAME)),
        (String) requireNonNull(storedIssue.getProperty(TYPE_PROPERTY_NAME)),
        (Integer) storedIssue.getProperty(START_LINE_PROPERTY_NAME));
    }
  }

  @CheckForNull
  private static ServerTaintIssue adaptTaint(@Nullable Entity storedIssue) {
    if (storedIssue == null) {
      return null;
    }
    var startLine = storedIssue.getProperty(START_LINE_PROPERTY_NAME);
    ServerTaintIssue.TextRange textRange = null;
    if (startLine != null) {
      var startLineOffset = (Integer) storedIssue.getProperty(START_LINE_OFFSET_PROPERTY_NAME);
      var endLine = (Integer) storedIssue.getProperty(END_LINE_PROPERTY_NAME);
      var endLineOffset = (Integer) storedIssue.getProperty(END_LINE_OFFSET_PROPERTY_NAME);
      textRange = new ServerTaintIssue.TextRange((int) startLine, startLineOffset, endLine, endLineOffset);
    }
    return new ServerTaintIssue(
      (String) requireNonNull(storedIssue.getProperty(KEY_PROPERTY_NAME)),
      Boolean.TRUE.equals(storedIssue.getProperty(RESOLVED_PROPERTY_NAME)),
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

  private static ServerTaintIssue.Flow adaptFlow(Entity flowEntity) {
    return new ServerTaintIssue.Flow(
      StreamSupport.stream(flowEntity.getLinks(FLOW_TO_LOCATIONS_LINK_NAME).spliterator(), false).map(XodusServerIssueStore::adaptLocation).collect(Collectors.toList()));
  }

  private static ServerTaintIssue.ServerIssueLocation adaptLocation(Entity locationEntity) {
    var startLine = locationEntity.getProperty(START_LINE_PROPERTY_NAME);
    ServerTaintIssue.TextRange textRange = null;
    if (startLine != null) {
      var startLineOffset = (Integer) locationEntity.getProperty(START_LINE_OFFSET_PROPERTY_NAME);
      var endLine = (Integer) locationEntity.getProperty(END_LINE_PROPERTY_NAME);
      var endLineOffset = (Integer) locationEntity.getProperty(END_LINE_OFFSET_PROPERTY_NAME);
      textRange = new ServerTaintIssue.TextRange((int) startLine, startLineOffset, endLine, endLineOffset);
    }
    return new ServerTaintIssue.ServerIssueLocation(
      (String) locationEntity.getProperty(FILE_PATH_PROPERTY_NAME),
      textRange,
      (String) locationEntity.getProperty(MESSAGE_PROPERTY_NAME),
      (String) locationEntity.getProperty(CODE_SNIPPET_PROPERTY_NAME));
  }

  @Override
  public List<ServerIssue> load(String projectKey, String filePath) {
    return loadIssue(projectKey, filePath, FILE_TO_ISSUES_LINK_NAME, XodusServerIssueStore::adapt);
  }

  @Override
  public List<ServerTaintIssue> loadTaint(String projectKey, String filePath) {
    return loadIssue(projectKey, filePath, FILE_TO_TAINT_ISSUES_LINK_NAME, XodusServerIssueStore::adaptTaint);
  }

  private <G> List<G> loadIssue(String projectKey, String filePath, String linkName, Function<Entity, G> adapter) {
    return entityStore.computeInTransaction(txn -> findUnique(txn, PROJECT_ENTITY_TYPE, KEY_PROPERTY_NAME, projectKey)
      .map(project -> project.getLinks(PROJECT_TO_FILES_LINK_NAME))
      .flatMap(files -> findUnique(txn, FILE_ENTITY_TYPE, PATH_PROPERTY_NAME, filePath))
      .map(fileToLoad -> fileToLoad.getLinks(linkName))
      .map(issueEntities -> StreamSupport.stream(issueEntities.spliterator(), false)
        .map(adapter)
        .collect(Collectors.toList()))
      .orElseGet(Collections::emptyList));
  }

  @Override
  public void save(String projectKey, List<ServerIssue> issues) {
    saveIssue(projectKey, issues, ServerIssue::getFilePath, XodusServerIssueStore::updateOrCreateIssue);
  }

  @Override
  public void saveTaint(String projectKey, List<ServerTaintIssue> issues) {
    saveIssue(projectKey, issues, ServerTaintIssue::getFilePath, XodusServerIssueStore::updateOrCreateTaintIssue);
  }

  public <G> void saveIssue(String projectKey, List<G> issues, Function<G, String> filePathGetter, IssueWriter<G> writer) {
    entityStore.executeInTransaction(txn -> {
      var issuesByFile = issues.stream().collect(Collectors.groupingBy(filePathGetter));
      issuesByFile.forEach((filePath, fileIssues) -> {
        var project = getOrCreateProject(projectKey, txn);
        var file = getOrCreateFile(project, filePath, txn);
        fileIssues.forEach(issue -> writer.updateOrCreateIssue(file, issue, txn));
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

  @FunctionalInterface
  private interface IssueWriter<G> {
    void updateOrCreateIssue(Entity fileEntity, G issue, StoreTransaction transaction);
  }

  private static void updateOrCreateIssue(Entity fileEntity, ServerIssue issue, StoreTransaction transaction) {
    var issueEntity = updateOrCreateIssueCommon(fileEntity, issue.getKey(), transaction, ISSUE_ENTITY_TYPE, FILE_TO_ISSUES_LINK_NAME);
    issueEntity.setProperty(RESOLVED_PROPERTY_NAME, issue.resolved());
    issueEntity.setProperty(RULE_KEY_PROPERTY_NAME, issue.ruleKey());
    issueEntity.setProperty(MESSAGE_PROPERTY_NAME, issue.getMessage());
    String lineHash = issue.getLineHash();
    if (lineHash != null) {
      issueEntity.setProperty(LINE_HASH_PROPERTY_NAME, lineHash);
    }
    String rangeHash = issue.getRangeHash();
    if (rangeHash != null) {
      issueEntity.setProperty(RANGE_HASH_PROPERTY_NAME, rangeHash);
    }
    issueEntity.setProperty(FILE_PATH_PROPERTY_NAME, issue.getFilePath());
    issueEntity.setProperty(CREATION_DATE_PROPERTY_NAME, issue.creationDate().toString());
    issueEntity.setProperty(SEVERITY_PROPERTY_NAME, issue.severity());
    issueEntity.setProperty(TYPE_PROPERTY_NAME, issue.type());
    var line = issue.getLine();
    if (line != null) {
      issueEntity.setProperty(START_LINE_PROPERTY_NAME, line);
    }
    var textRange = issue.getTextRange();
    if (textRange != null) {
      issueEntity.setProperty(START_LINE_PROPERTY_NAME, textRange.getStartLine());
      issueEntity.setProperty(START_LINE_OFFSET_PROPERTY_NAME, textRange.getStartLineOffset());
      issueEntity.setProperty(END_LINE_PROPERTY_NAME, textRange.getEndLine());
      issueEntity.setProperty(END_LINE_OFFSET_PROPERTY_NAME, textRange.getEndLineOffset());
    }
  }

  private static void updateOrCreateTaintIssue(Entity fileEntity, ServerTaintIssue issue, StoreTransaction transaction) {
    var issueEntity = updateOrCreateIssueCommon(fileEntity, issue.key(), transaction, TAINT_ISSUE_ENTITY_TYPE, FILE_TO_TAINT_ISSUES_LINK_NAME);
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
      issueEntity.setProperty(START_LINE_PROPERTY_NAME, textRange.getStartLine());
      issueEntity.setProperty(START_LINE_OFFSET_PROPERTY_NAME, textRange.getStartLineOffset());
      issueEntity.setProperty(END_LINE_PROPERTY_NAME, textRange.getEndLine());
      issueEntity.setProperty(END_LINE_OFFSET_PROPERTY_NAME, textRange.getEndLineOffset());
    }
    var codeSnippet = issue.getCodeSnippet();
    if (codeSnippet != null) {
      issueEntity.setProperty(CODE_SNIPPET_PROPERTY_NAME, codeSnippet);
    }
    deleteFlowAndLocations(issueEntity);
    issue.getFlows().forEach(flow -> storeFlow(flow, issueEntity, transaction));
  }

  private static Entity updateOrCreateIssueCommon(Entity fileEntity, String issueKey, StoreTransaction transaction, String entityType, String fileToIssueLink) {
    var issueEntity = findUnique(transaction, entityType, KEY_PROPERTY_NAME, issueKey)
      .orElseGet(() -> transaction.newEntity(entityType));
    var oldFileEntity = issueEntity.getLink(ISSUE_TO_FILE_LINK_NAME);
    if (oldFileEntity != null && !fileEntity.equals(oldFileEntity)) {
      // issue might have moved file
      oldFileEntity.deleteLink(fileToIssueLink, issueEntity);
    }
    issueEntity.setLink(ISSUE_TO_FILE_LINK_NAME, fileEntity);
    fileEntity.addLink(fileToIssueLink, issueEntity);
    issueEntity.setProperty(KEY_PROPERTY_NAME, issueKey);
    return issueEntity;
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

  private static void storeFlow(ServerTaintIssue.Flow flow, Entity issueEntity, StoreTransaction transaction) {
    var flowEntity = transaction.newEntity(FLOW_ENTITY_TYPE);
    issueEntity.addLink(ISSUE_TO_FLOWS_LINK_NAME, flowEntity);
    flow.locations().forEach(location -> storeLocation(location, flowEntity, transaction));
  }

  private static void storeLocation(ServerTaintIssue.ServerIssueLocation location, Entity flowEntity, StoreTransaction transaction) {
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
      locationEntity.setProperty(START_LINE_PROPERTY_NAME, locationTextRange.getStartLine());
      locationEntity.setProperty(START_LINE_OFFSET_PROPERTY_NAME, locationTextRange.getStartLineOffset());
      locationEntity.setProperty(END_LINE_PROPERTY_NAME, locationTextRange.getEndLine());
      locationEntity.setProperty(END_LINE_OFFSET_PROPERTY_NAME, locationTextRange.getEndLineOffset());
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

  @Override
  public void close() {
    entityStore.close();
  }
}
