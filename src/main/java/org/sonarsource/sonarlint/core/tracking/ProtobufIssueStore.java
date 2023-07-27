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
package org.sonarsource.sonarlint.core.tracking;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.commons.objectstore.HashingPathMapper;
import org.sonarsource.sonarlint.core.commons.objectstore.PathMapper;
import org.sonarsource.sonarlint.core.commons.objectstore.Reader;
import org.sonarsource.sonarlint.core.commons.objectstore.Writer;
import org.sonarsource.sonarlint.core.issuetracking.Trackable;
import org.sonarsource.sonarlint.core.issuetracking.TrackableIssueStore;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

public class ProtobufIssueStore<T> implements TrackableIssueStore<T> {

  private Path basePath;
  private IndexedObjectStore<String, Sonarlint.Issues> store;

  public ProtobufIssueStore(Path storeBasePath, Path projectBasePath) {
    this.basePath = storeBasePath;
    try {
      FileUtils.forceMkdir(basePath.toFile());
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create issue store directory", e);
    }
    StoreIndex<String> index = new StringStoreIndex(storeBasePath);
    PathMapper<String> mapper = new HashingPathMapper(storeBasePath, 2);
    StoreKeyValidator<String> validator = new PathStoreKeyValidator(projectBasePath);
    Reader<Sonarlint.Issues> reader = is -> {
      try {
        return Sonarlint.Issues.parseFrom(is);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to read issues", e);
      }
    };
    Writer<Sonarlint.Issues> writer = (os, issues) -> {
      try {
        issues.writeTo(os);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to save issues", e);
      }
    };
    store = new IndexedObjectStore<>(index, mapper, reader, writer, validator);
    store.deleteInvalid();
  }

  @Override
  public boolean contains(String key) {
    return store.contains(key);
  }

  @Override
  public void save(String key, Collection<Trackable<T>> issues) throws IOException {
    store.write(key, transform(issues));
  }

  @Override
  @CheckForNull
  public Collection<Trackable<T>> read(String key) throws IOException {
    var issues = store.read(key);
    return issues.map(this::transform).orElse(null);
  }

  public void clean() {
    store.deleteInvalid();
  }

  @Override
  public void clear() {
    try {
      FileUtils.deleteDirectory(basePath.toFile());
      FileUtils.forceMkdir(basePath.toFile());
    } catch (IOException e) {
      throw new IllegalStateException("Unable to clear issue store", e);
    }
  }

  private Collection<Trackable<T>> transform(Sonarlint.Issues protoIssues) {
    return protoIssues.getIssueList().stream()
      .map(this::transform)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  private Sonarlint.Issues transform(Collection<Trackable<T>> localIssues) {
    var builder = Sonarlint.Issues.newBuilder();
    localIssues.stream()
      .map(this::transform)
      .filter(Objects::nonNull)
      .forEach(builder::addIssue);

    return builder.build();
  }

  private Trackable<T> transform(Sonarlint.Issues.Issue issue) {
    return new ProtobufIssueTrackable(issue);
  }

  @CheckForNull
  private Sonarlint.Issues.Issue transform(Trackable<T> localIssue) {
    var builder = Sonarlint.Issues.Issue.newBuilder()
      .setRuleKey(localIssue.getRuleKey())
      .setMessage(localIssue.getMessage())
      .setResolved(localIssue.isResolved());

    if (localIssue.getCreationDate() != null) {
      builder.setCreationDate(localIssue.getCreationDate());
    }
    if (localIssue.getLineHash() != null) {
      builder.setLineHash(localIssue.getLineHash());
    }
    if (localIssue.getServerIssueKey() != null) {
      builder.setServerIssueKey(localIssue.getServerIssueKey());
    }
    if (localIssue.getLine() != null) {
      builder.setLine(localIssue.getLine());
    }
    return builder.build();
  }
}
