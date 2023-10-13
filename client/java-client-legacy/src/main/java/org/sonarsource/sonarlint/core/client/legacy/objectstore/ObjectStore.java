/*
<<<<<<<< HEAD:client/java-client-legacy/src/main/java/org/sonarsource/sonarlint/core/client/legacy/objectstore/ObjectStore.java
 * SonarLint Core - Java Client Legacy
========
 * SonarLint Core - RPC Protocol
>>>>>>>> 42594bc97 (SLCORE-571 Make the client-api JSON-RPC friendly):rpc-protocol/src/main/java/org/sonarsource/sonarlint/core/rpc/protocol/backend/branch/SonarProjectBranchService.java
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
<<<<<<<< HEAD:client/java-client-legacy/src/main/java/org/sonarsource/sonarlint/core/client/legacy/objectstore/ObjectStore.java
package org.sonarsource.sonarlint.core.client.legacy.objectstore;

import java.io.IOException;
import java.util.Optional;

/**
 * Store and retrieve objects from the filesystem.
 *
 * @param <K> type of the key to store by and used when reading back
 * @param <V> type of the value to store
 */
public interface ObjectStore<K, V> {
========
package org.sonarsource.sonarlint.core.rpc.protocol.backend.branch;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;

@JsonSegment("branch")
public interface SonarProjectBranchService {
>>>>>>>> 42594bc97 (SLCORE-571 Make the client-api JSON-RPC friendly):rpc-protocol/src/main/java/org/sonarsource/sonarlint/core/rpc/protocol/backend/branch/SonarProjectBranchService.java

  void write(K key, V values) throws IOException;
  
  void delete(K key) throws IOException;

  Optional<V> read(K key) throws IOException;
}
