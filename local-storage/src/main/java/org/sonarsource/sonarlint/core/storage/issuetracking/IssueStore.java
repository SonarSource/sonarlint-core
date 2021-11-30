/*
 * SonarLint Core - Local Storage
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.storage.issuetracking;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.storage.FileUtils;
import org.sonarsource.sonarlint.core.storage.objectstore.HashingPathMapper;
import org.sonarsource.sonarlint.core.storage.objectstore.PathMapper;
import org.sonarsource.sonarlint.core.storage.objectstore.Reader;
import org.sonarsource.sonarlint.core.storage.objectstore.Writer;

public class IssueStore {

  private Path basePath;
  private IndexedObjectStore<String, Sonarlint.Issues> store;

  public IssueStore(Path storeBasePath, Path projectBasePath) {
    this.basePath = storeBasePath;
    FileUtils.mkdirs(storeBasePath);
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

  public boolean contains(String key) {
    return store.contains(key);
  }

  public <G> void save(String key, Collection<G> issues, Function<G, Sonarlint.Issues.Issue> converter) throws IOException {
    store.write(key, transform(issues, converter));
  }

  private static <G> Collection<G> transform(Sonarlint.Issues protoIssues, Function<Sonarlint.Issues.Issue, G> transformer) {
    return protoIssues.getIssueList().stream()
      .map(transformer::apply)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  private static <G> Sonarlint.Issues transform(Collection<G> localIssues, Function<G, Sonarlint.Issues.Issue> transformer) {
    Sonarlint.Issues.Builder builder = Sonarlint.Issues.newBuilder();
    localIssues.stream()
      .map(transformer::apply)
      .filter(Objects::nonNull)
      .forEach(builder::addIssue);

    return builder.build();
  }

  @CheckForNull
  public <G> Collection<G> read(String key, Function<Sonarlint.Issues.Issue, G> converter) throws IOException {
    Optional<Sonarlint.Issues> issuesOpt = store.read(key);
    return issuesOpt.map(issues -> IssueStore.transform(issues, converter)).orElse(null);
  }

  public void clean() {
    store.deleteInvalid();
  }

  public void clear() {
    FileUtils.deleteRecursively(basePath);
    FileUtils.mkdirs(basePath);
  }
}
