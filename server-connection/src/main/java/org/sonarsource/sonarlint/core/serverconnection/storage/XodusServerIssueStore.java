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
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import jetbrains.exodus.entitystore.Entity;
import jetbrains.exodus.entitystore.EntityIterable;
import jetbrains.exodus.entitystore.PersistentEntityStore;
import jetbrains.exodus.entitystore.PersistentEntityStores;
import jetbrains.exodus.entitystore.StoreTransaction;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverconnection.issues.FileLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.LineLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.RangeLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public class XodusServerIssueStore implements ServerIssueStore {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private static final String FILE_NAME = "sonarlint.db";

  private static final String PROJECT_ENTITY_TYPE = "Project";
  private static final String BRANCH_ENTITY_TYPE = "Branch";
  private static final String FILE_ENTITY_TYPE = "File";
  private static final String ISSUE_ENTITY_TYPE = "Issue";
  private static final String TAINT_ISSUE_ENTITY_TYPE = "TaintIssue";
  private static final String FLOW_ENTITY_TYPE = "Flow";
  private static final String LOCATION_ENTITY_TYPE = "Location";

  private static final String PROJECT_TO_BRANCHES_LINK_NAME = "branches";
  private static final String BRANCH_TO_FILES_LINK_NAME = "files";
  private static final String FILE_TO_ISSUES_LINK_NAME = "issues";
  private static final String FILE_TO_TAINT_ISSUES_LINK_NAME = "taintIssues";
  private static final String ISSUE_TO_FILE_LINK_NAME = "file";
  private static final String ISSUE_TO_FLOWS_LINK_NAME = "flows";
  private static final String FLOW_TO_LOCATIONS_LINK_NAME = "locations";

  private static final String START_LINE_PROPERTY_NAME = "startLine";
  private static final String START_LINE_OFFSET_PROPERTY_NAME = "startLineOffset";
  private static final String END_LINE_PROPERTY_NAME = "endLine";
  private static final String END_LINE_OFFSET_PROPERTY_NAME = "endLineOffset";
  private static final String KEY_PROPERTY_NAME = "key";
  private static final String RESOLVED_PROPERTY_NAME = "resolved";
  private static final String RULE_KEY_PROPERTY_NAME = "ruleKey";
  private static final String LINE_HASH_PROPERTY_NAME = "lineHash";
  private static final String RANGE_HASH_PROPERTY_NAME = "rangeHash";
  private static final String CREATION_DATE_PROPERTY_NAME = "creationDate";
  private static final String USER_SEVERITY_PROPERTY_NAME = "userSeverity";
  private static final String SEVERITY_PROPERTY_NAME = "severity";
  private static final String TYPE_PROPERTY_NAME = "type";
  private static final String PATH_PROPERTY_NAME = "path";
  private static final String NAME_PROPERTY_NAME = "name";
  private static final String LAST_ISSUE_SYNC_PROPERTY_NAME = "lastIssueSync";
  private static final String LAST_TAINT_SYNC_PROPERTY_NAME = "lastTaintSync";

  private static final String MESSAGE_BLOB_NAME = "message";

  private final PersistentEntityStore entityStore;

  public XodusServerIssueStore(Path baseDir) {
    entityStore = PersistentEntityStores.newInstance(baseDir.resolve(FILE_NAME).toAbsolutePath().toString());
    entityStore.executeInTransaction(txn -> {
      entityStore.registerCustomPropertyType(txn, IssueSeverity.class, new IssueSeverityBinding());
      entityStore.registerCustomPropertyType(txn, RuleType.class, new IssueTypeBinding());
    });
  }

  private static ServerIssue adapt(Entity storedIssue) {
    var filePath = (String) requireNonNull(storedIssue.getLink(ISSUE_TO_FILE_LINK_NAME).getProperty(PATH_PROPERTY_NAME));
    var startLine = storedIssue.getProperty(START_LINE_PROPERTY_NAME);
    var key = (String) requireNonNull(storedIssue.getProperty(KEY_PROPERTY_NAME));
    var resolved = Boolean.TRUE.equals(storedIssue.getProperty(RESOLVED_PROPERTY_NAME));
    var ruleKey = (String) requireNonNull(storedIssue.getProperty(RULE_KEY_PROPERTY_NAME));
    var msg = requireNonNull(storedIssue.getBlobString(MESSAGE_BLOB_NAME));
    var creationDate = Instant.parse((String) requireNonNull(storedIssue.getProperty(CREATION_DATE_PROPERTY_NAME)));
    var userSeverity = (IssueSeverity) storedIssue.getProperty(USER_SEVERITY_PROPERTY_NAME);
    var type = (RuleType) requireNonNull(storedIssue.getProperty(TYPE_PROPERTY_NAME));
    if (startLine == null) {
      return new FileLevelServerIssue(key, resolved, ruleKey, msg, filePath, creationDate, userSeverity, type);
    } else {
      var rangeHash = storedIssue.getBlobString(RANGE_HASH_PROPERTY_NAME);
      if (rangeHash != null) {
        var startLineOffset = (Integer) storedIssue.getProperty(START_LINE_OFFSET_PROPERTY_NAME);
        var endLine = (Integer) storedIssue.getProperty(END_LINE_PROPERTY_NAME);
        var endLineOffset = (Integer) storedIssue.getProperty(END_LINE_OFFSET_PROPERTY_NAME);
        var textRange = new TextRangeWithHash((int) startLine, startLineOffset, endLine, endLineOffset, rangeHash);
        return new RangeLevelServerIssue(
          key,
          resolved,
          ruleKey,
          msg,
          filePath,
          creationDate,
          userSeverity,
          type,
          textRange);
      } else {
        return new LineLevelServerIssue(
          key,
          resolved,
          ruleKey,
          msg,
          storedIssue.getBlobString(LINE_HASH_PROPERTY_NAME),
          filePath,
          creationDate,
          userSeverity,
          type,
          (Integer) storedIssue.getProperty(START_LINE_PROPERTY_NAME));
      }
    }
  }

  private static ServerTaintIssue adaptTaint(Entity storedIssue) {
    var filePath = (String) requireNonNull(storedIssue.getLink(ISSUE_TO_FILE_LINK_NAME).getProperty(PATH_PROPERTY_NAME));
    var startLine = (Integer) storedIssue.getProperty(START_LINE_PROPERTY_NAME);
    TextRangeWithHash textRange = null;
    if (startLine != null) {
      var startLineOffset = (Integer) storedIssue.getProperty(START_LINE_OFFSET_PROPERTY_NAME);
      var endLine = (Integer) storedIssue.getProperty(END_LINE_PROPERTY_NAME);
      var endLineOffset = (Integer) storedIssue.getProperty(END_LINE_OFFSET_PROPERTY_NAME);
      var hash = storedIssue.getBlobString(RANGE_HASH_PROPERTY_NAME);
      textRange = new TextRangeWithHash(startLine, startLineOffset, endLine, endLineOffset, hash);
    }
    return new ServerTaintIssue(
      (String) requireNonNull(storedIssue.getProperty(KEY_PROPERTY_NAME)),
      Boolean.TRUE.equals(storedIssue.getProperty(RESOLVED_PROPERTY_NAME)),
      (String) requireNonNull(storedIssue.getProperty(RULE_KEY_PROPERTY_NAME)),
      requireNonNull(storedIssue.getBlobString(MESSAGE_BLOB_NAME)),
      filePath,
      Instant.parse((String) requireNonNull(storedIssue.getProperty(CREATION_DATE_PROPERTY_NAME))),
      (IssueSeverity) requireNonNull(storedIssue.getProperty(SEVERITY_PROPERTY_NAME)),
      (RuleType) requireNonNull(storedIssue.getProperty(TYPE_PROPERTY_NAME)),
      textRange)
        .setFlows(StreamSupport.stream(storedIssue.getLinks(ISSUE_TO_FLOWS_LINK_NAME).spliterator(), false).map(XodusServerIssueStore::adaptFlow).collect(Collectors.toList()));
  }

  private static ServerTaintIssue.Flow adaptFlow(Entity flowEntity) {
    return new ServerTaintIssue.Flow(
      StreamSupport.stream(flowEntity.getLinks(FLOW_TO_LOCATIONS_LINK_NAME).spliterator(), false).map(XodusServerIssueStore::adaptLocation).collect(Collectors.toList()));
  }

  private static ServerTaintIssue.ServerIssueLocation adaptLocation(Entity locationEntity) {
    var startLine = locationEntity.getProperty(START_LINE_PROPERTY_NAME);
    TextRangeWithHash textRange = null;
    if (startLine != null) {
      var startLineOffset = (Integer) locationEntity.getProperty(START_LINE_OFFSET_PROPERTY_NAME);
      var endLine = (Integer) locationEntity.getProperty(END_LINE_PROPERTY_NAME);
      var endLineOffset = (Integer) locationEntity.getProperty(END_LINE_OFFSET_PROPERTY_NAME);
      var hash = locationEntity.getBlobString(RANGE_HASH_PROPERTY_NAME);
      textRange = new TextRangeWithHash((int) startLine, startLineOffset, endLine, endLineOffset, hash);
    }
    return new ServerTaintIssue.ServerIssueLocation(
      (String) locationEntity.getProperty(PATH_PROPERTY_NAME),
      textRange,
      locationEntity.getBlobString(MESSAGE_BLOB_NAME));
  }

  @Override
  public List<ServerIssue> load(String projectKey, String branchName, String filePath) {
    return loadIssue(projectKey, branchName, filePath, FILE_TO_ISSUES_LINK_NAME, XodusServerIssueStore::adapt);
  }

  @Override
  public List<ServerTaintIssue> loadTaint(String projectKey, String branchName, String filePath) {
    return loadIssue(projectKey, branchName, filePath, FILE_TO_TAINT_ISSUES_LINK_NAME, XodusServerIssueStore::adaptTaint);
  }

  private <G> List<G> loadIssue(String projectKey, String branchName, String filePath, String linkName, Function<Entity, G> adapter) {
    return entityStore.computeInReadonlyTransaction(txn -> findUnique(txn, PROJECT_ENTITY_TYPE, KEY_PROPERTY_NAME, projectKey)
      .map(project -> project.getLinks(PROJECT_TO_BRANCHES_LINK_NAME))
      .flatMap(branches -> findUnique(txn, BRANCH_ENTITY_TYPE, NAME_PROPERTY_NAME, branchName))
      .map(branch -> branch.getLinks(BRANCH_TO_FILES_LINK_NAME))
      .flatMap(files -> findUnique(txn, FILE_ENTITY_TYPE, PATH_PROPERTY_NAME, filePath))
      .map(fileToLoad -> fileToLoad.getLinks(linkName))
      .map(issueEntities -> StreamSupport.stream(issueEntities.spliterator(), false)
        .map(adapter)
        .collect(Collectors.toList()))
      .orElseGet(Collections::emptyList));
  }

  @Override
  public void replaceAllIssuesOfFile(String projectKey, String branchName, String serverFilePath, List<ServerIssue> issues) {
    timed("Wrote " + issues.size() + " issues in store", () -> entityStore.executeInTransaction(txn -> {
      var project = getOrCreateProject(projectKey, txn);
      var branch = getOrCreateBranch(project, branchName, txn);
      var fileEntity = getOrCreateFile(branch, serverFilePath, txn);
      replaceAllIssuesOfFile(issues, txn, fileEntity);
    }));
  }

  @Override
  public void mergeIssues(String projectKey, String branchName, List<ServerIssue> issuesToMerge, Set<String> closedIssueKeysToDelete, Instant syncTimestamp) {
    timed("Merged " + issuesToMerge.size() + " issues in store. Closed " + closedIssueKeysToDelete.size() + ".", () -> entityStore.executeInTransaction(txn -> {
      var project = getOrCreateProject(projectKey, txn);
      var branch = getOrCreateBranch(project, branchName, txn);
      var issuesByFilePath = issuesToMerge.stream().collect(Collectors.groupingBy(ServerIssue::getFilePath));
      issuesByFilePath.forEach((filePath, issues) -> {
        var fileEntity = getOrCreateFile(branch, filePath, txn);
        issues.forEach(issue -> updateOrCreateIssue(fileEntity, issue, txn));
      });
      closedIssueKeysToDelete.forEach(issueKey -> remove(issueKey, txn));
      branch.setProperty(LAST_ISSUE_SYNC_PROPERTY_NAME, syncTimestamp.toEpochMilli());
    }));
  }

  @Override
  public void mergeTaintIssues(String projectKey, String branchName, List<ServerTaintIssue> issuesToMerge, Set<String> closedIssueKeysToDelete, Instant syncTimestamp) {
    timed("Merged " + issuesToMerge.size() + " taint issues in store. Closed " + closedIssueKeysToDelete.size() + ".", () -> entityStore.executeInTransaction(txn -> {
      var project = getOrCreateProject(projectKey, txn);
      var branch = getOrCreateBranch(project, branchName, txn);
      var issuesByFilePath = issuesToMerge.stream().collect(Collectors.groupingBy(ServerTaintIssue::getFilePath));
      issuesByFilePath.forEach((filePath, issues) -> {
        var fileEntity = getOrCreateFile(branch, filePath, txn);
        issues.forEach(issue -> updateOrCreateTaintIssue(fileEntity, issue, txn));
      });
      closedIssueKeysToDelete.forEach(issueKey -> removeTaint(issueKey, txn));
      branch.setProperty(LAST_TAINT_SYNC_PROPERTY_NAME, syncTimestamp.toEpochMilli());
    }));
  }

  @Override
  public Optional<Instant> getLastIssueSyncTimestamp(String projectKey, String branchName) {
    return entityStore.computeInReadonlyTransaction(txn -> findUnique(txn, PROJECT_ENTITY_TYPE, KEY_PROPERTY_NAME, projectKey)
      .map(project -> project.getLinks(PROJECT_TO_BRANCHES_LINK_NAME))
      .flatMap(branches -> findUnique(txn, BRANCH_ENTITY_TYPE, NAME_PROPERTY_NAME, branchName))
      .flatMap(branch -> ofNullable((Long) branch.getProperty(LAST_ISSUE_SYNC_PROPERTY_NAME)))
      .map(Instant::ofEpochMilli));
  }

  @Override
  public Optional<Instant> getLastTaintSyncTimestamp(String projectKey, String branchName) {
    return entityStore.computeInReadonlyTransaction(txn -> findUnique(txn, PROJECT_ENTITY_TYPE, KEY_PROPERTY_NAME, projectKey)
      .map(project -> project.getLinks(PROJECT_TO_BRANCHES_LINK_NAME))
      .flatMap(branches -> findUnique(txn, BRANCH_ENTITY_TYPE, NAME_PROPERTY_NAME, branchName))
      .flatMap(branch -> ofNullable((Long) branch.getProperty(LAST_TAINT_SYNC_PROPERTY_NAME)))
      .map(Instant::ofEpochMilli));
  }

  @Override
  public void replaceAllIssuesOfProject(String projectKey, String branchName, List<ServerIssue> issues) {
    timed("Wrote " + issues.size() + " issues in store", () -> entityStore.executeInTransaction(txn -> {
      var project = getOrCreateProject(projectKey, txn);
      var branch = getOrCreateBranch(project, branchName, txn);
      var issuesByFile = issues.stream().collect(Collectors.groupingBy(ServerIssue::getFilePath));
      branch.getLinks(BRANCH_TO_FILES_LINK_NAME).forEach(fileEntity -> {
        var entityFilePath = fileEntity.getProperty(PATH_PROPERTY_NAME);
        replaceAllIssuesOfFile(issuesByFile.getOrDefault(entityFilePath, List.of()), txn, fileEntity);
        issuesByFile.remove(entityFilePath);
      });
      issuesByFile.forEach((filePath, fileIssues) -> {
        var fileEntity = getOrCreateFile(branch, filePath, txn);
        replaceAllIssuesOfFile(fileIssues, txn, fileEntity);
      });
    }));
  }

  private static void timed(String msg, Runnable transaction) {
    var startTime = Instant.now();
    transaction.run();
    var duration = Duration.between(startTime, Instant.now());
    LOG.debug("{} | took {}ms", msg, duration.toMillis());
  }

  private static void replaceAllIssuesOfFile(List<ServerIssue> issues, @NotNull StoreTransaction txn, Entity fileEntity) {
    fileEntity.getLinks(FILE_TO_ISSUES_LINK_NAME).forEach(Entity::delete);
    fileEntity.deleteLinks(FILE_TO_ISSUES_LINK_NAME);

    issues.forEach(issue -> updateOrCreateIssue(fileEntity, issue, txn));
  }

  @Override
  public void replaceAllTaintOfFile(String projectKey, String branchName, String serverFilePath, List<ServerTaintIssue> issues) {
    timed("Wrote " + issues.size() + " taint issues in store", () -> entityStore.executeInTransaction(txn -> {
      var project = getOrCreateProject(projectKey, txn);
      var branch = getOrCreateBranch(project, branchName, txn);
      var fileEntity = getOrCreateFile(branch, serverFilePath, txn);

      fileEntity.getLinks(FILE_TO_TAINT_ISSUES_LINK_NAME).forEach(Entity::delete);
      fileEntity.deleteLinks(FILE_TO_TAINT_ISSUES_LINK_NAME);

      issues.forEach(issue -> updateOrCreateTaintIssue(fileEntity, issue, txn));
    }));
  }

  private static Entity getOrCreateProject(String projectKey, StoreTransaction txn) {
    return findUnique(txn, PROJECT_ENTITY_TYPE, KEY_PROPERTY_NAME, projectKey)
      .orElseGet(() -> {
        var project = txn.newEntity(PROJECT_ENTITY_TYPE);
        project.setProperty(KEY_PROPERTY_NAME, projectKey);
        return project;
      });
  }

  private static Entity getOrCreateBranch(Entity projectEntity, String branchName, StoreTransaction txn) {
    var branchIterable = projectEntity.getLinks(PROJECT_TO_BRANCHES_LINK_NAME)
      .intersect(findAll(txn, BRANCH_ENTITY_TYPE, NAME_PROPERTY_NAME, branchName));
    return Optional.ofNullable(branchIterable.getFirst())
      .orElseGet(() -> {
        var branch = txn.newEntity(BRANCH_ENTITY_TYPE);
        branch.setProperty(NAME_PROPERTY_NAME, branchName);
        projectEntity.addLink(PROJECT_TO_BRANCHES_LINK_NAME, branch);
        return branch;
      });
  }

  private static Entity getOrCreateFile(Entity branchEntity, String filePath, StoreTransaction txn) {
    var fileIterable = branchEntity.getLinks(BRANCH_TO_FILES_LINK_NAME)
      .intersect(findAll(txn, FILE_ENTITY_TYPE, PATH_PROPERTY_NAME, filePath));
    return Optional.ofNullable(fileIterable.getFirst())
      .orElseGet(() -> {
        var file = txn.newEntity(FILE_ENTITY_TYPE);
        file.setProperty(PATH_PROPERTY_NAME, filePath);
        branchEntity.addLink(BRANCH_TO_FILES_LINK_NAME, file);
        return file;
      });
  }

  private static void updateOrCreateIssue(Entity fileEntity, ServerIssue issue, StoreTransaction transaction) {
    var issueEntity = updateOrCreateIssueCommon(fileEntity, issue.getKey(), transaction, ISSUE_ENTITY_TYPE, FILE_TO_ISSUES_LINK_NAME);
    updateIssueEntity(issueEntity, issue);
  }

  private static void updateIssueEntity(Entity issueEntity, ServerIssue issue) {
    issueEntity.setProperty(RESOLVED_PROPERTY_NAME, issue.isResolved());
    issueEntity.setProperty(RULE_KEY_PROPERTY_NAME, issue.getRuleKey());
    issueEntity.setBlobString(MESSAGE_BLOB_NAME, issue.getMessage());
    issueEntity.setProperty(CREATION_DATE_PROPERTY_NAME, issue.getCreationDate().toString());
    var userSeverity = issue.getUserSeverity();
    if (userSeverity != null) {
      issueEntity.setProperty(USER_SEVERITY_PROPERTY_NAME, userSeverity);
    }
    issueEntity.setProperty(TYPE_PROPERTY_NAME, issue.getType());
    if (issue instanceof LineLevelServerIssue) {
      var lineIssue = (LineLevelServerIssue) issue;
      issueEntity.setBlobString(LINE_HASH_PROPERTY_NAME, lineIssue.getLineHash());
      issueEntity.setProperty(START_LINE_PROPERTY_NAME, lineIssue.getLine());
    } else if (issue instanceof RangeLevelServerIssue) {
      var rangeIssue = (RangeLevelServerIssue) issue;
      var textRange = rangeIssue.getTextRange();
      issueEntity.setProperty(START_LINE_PROPERTY_NAME, textRange.getStartLine());
      issueEntity.setProperty(START_LINE_OFFSET_PROPERTY_NAME, textRange.getStartLineOffset());
      issueEntity.setProperty(END_LINE_PROPERTY_NAME, textRange.getEndLine());
      issueEntity.setProperty(END_LINE_OFFSET_PROPERTY_NAME, textRange.getEndLineOffset());
      issueEntity.setBlobString(RANGE_HASH_PROPERTY_NAME, textRange.getHash());
    }
  }

  private static void updateOrCreateTaintIssue(Entity fileEntity, ServerTaintIssue issue, StoreTransaction transaction) {
    var issueEntity = updateOrCreateIssueCommon(fileEntity, issue.getKey(), transaction, TAINT_ISSUE_ENTITY_TYPE, FILE_TO_TAINT_ISSUES_LINK_NAME);
    updateTaintIssueEntity(issue, transaction, issueEntity);
  }

  private static void updateTaintIssueEntity(ServerTaintIssue issue, StoreTransaction transaction, Entity issueEntity) {
    issueEntity.setProperty(RESOLVED_PROPERTY_NAME, issue.isResolved());
    issueEntity.setProperty(RULE_KEY_PROPERTY_NAME, issue.getRuleKey());
    issueEntity.setBlobString(MESSAGE_BLOB_NAME, issue.getMessage());
    issueEntity.setProperty(CREATION_DATE_PROPERTY_NAME, issue.getCreationDate().toString());
    issueEntity.setProperty(SEVERITY_PROPERTY_NAME, issue.getSeverity());
    issueEntity.setProperty(TYPE_PROPERTY_NAME, issue.getType());
    var textRange = issue.getTextRange();
    if (textRange != null) {
      issueEntity.setProperty(START_LINE_PROPERTY_NAME, textRange.getStartLine());
      issueEntity.setProperty(START_LINE_OFFSET_PROPERTY_NAME, textRange.getStartLineOffset());
      issueEntity.setProperty(END_LINE_PROPERTY_NAME, textRange.getEndLine());
      issueEntity.setProperty(END_LINE_OFFSET_PROPERTY_NAME, textRange.getEndLineOffset());
      issueEntity.setBlobString(RANGE_HASH_PROPERTY_NAME, textRange.getHash());
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
    locationEntity.setBlobString(MESSAGE_BLOB_NAME, location.getMessage());
    String filePath = location.getFilePath();
    if (filePath != null) {
      locationEntity.setProperty(PATH_PROPERTY_NAME, filePath);
    }
    var locationTextRange = location.getTextRange();
    if (locationTextRange != null) {
      locationEntity.setProperty(START_LINE_PROPERTY_NAME, locationTextRange.getStartLine());
      locationEntity.setProperty(START_LINE_OFFSET_PROPERTY_NAME, locationTextRange.getStartLineOffset());
      locationEntity.setProperty(END_LINE_PROPERTY_NAME, locationTextRange.getEndLine());
      locationEntity.setProperty(END_LINE_OFFSET_PROPERTY_NAME, locationTextRange.getEndLineOffset());
      locationEntity.setBlobString(RANGE_HASH_PROPERTY_NAME, locationTextRange.getHash());
    }
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

  private static void removeTaint(String issueKey, @NotNull StoreTransaction txn) {
    findUnique(txn, TAINT_ISSUE_ENTITY_TYPE, KEY_PROPERTY_NAME, issueKey)
      .ifPresent(issueEntity -> {
        var fileEntity = issueEntity.getLink(ISSUE_TO_FILE_LINK_NAME);
        if (fileEntity != null) {
          fileEntity.deleteLink(FILE_TO_TAINT_ISSUES_LINK_NAME, issueEntity);
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
  public boolean updateIssue(String issueKey, Consumer<ServerIssue> issueUpdater) {
    return entityStore.computeInTransaction(txn -> {
      var optionalEntity = findUnique(txn, ISSUE_ENTITY_TYPE, KEY_PROPERTY_NAME, issueKey);
      if (optionalEntity.isPresent()) {
        var issueEntity = optionalEntity.get();
        var currentIssue = adapt(issueEntity);
        issueUpdater.accept(currentIssue);
        updateIssueEntity(issueEntity, currentIssue);
        return true;
      }
      return false;
    });
  }

  @Override
  public void updateTaintIssue(String issueKey, Consumer<ServerTaintIssue> taintIssueUpdater) {
    entityStore.executeInTransaction(txn -> findUnique(txn, TAINT_ISSUE_ENTITY_TYPE, KEY_PROPERTY_NAME, issueKey)
      .ifPresent(issueEntity -> {
        var currentIssue = adaptTaint(issueEntity);
        taintIssueUpdater.accept(currentIssue);
        updateTaintIssueEntity(currentIssue, txn, issueEntity);
      }));
  }

  @Override
  public void insert(String projectKey, String branchName, ServerTaintIssue taintIssue) {
    entityStore.executeInTransaction(txn -> findUnique(txn, TAINT_ISSUE_ENTITY_TYPE, KEY_PROPERTY_NAME, taintIssue.getKey())
      .ifPresentOrElse(issueEntity -> LOG.error("Trying to store a taint vulnerability that already exists"), () -> {
        var project = getOrCreateProject(projectKey, txn);
        var branch = getOrCreateBranch(project, branchName, txn);
        var fileEntity = getOrCreateFile(branch, taintIssue.getFilePath(), txn);
        updateOrCreateTaintIssue(fileEntity, taintIssue, txn);
      }));
  }

  @Override
  public void deleteTaintIssue(String issueKeyToDelete) {
    entityStore.executeInTransaction(txn -> removeTaint(issueKeyToDelete, txn));
  }

  @Override
  public void close() {
    entityStore.close();
  }
}
