/*
<<<<<<<< HEAD:client/java-client-legacy/src/main/java/org/sonarsource/sonarlint/core/client/legacy/objectstore/Writer.java
 * SonarLint Core - Java Client Legacy
========
 * SonarLint Core - RPC Protocol
>>>>>>>> 42594bc97 (SLCORE-571 Make the client-api JSON-RPC friendly):rpc-protocol/src/main/java/org/sonarsource/sonarlint/core/rpc/protocol/backend/telemetry/TelemetryService.java
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
<<<<<<<< HEAD:client/java-client-legacy/src/main/java/org/sonarsource/sonarlint/core/client/legacy/objectstore/Writer.java
package org.sonarsource.sonarlint.core.client.legacy.objectstore;

import java.io.OutputStream;
import java.util.function.BiConsumer;

@FunctionalInterface
public interface Writer<V> extends BiConsumer<OutputStream, V> {
========
package org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;

@JsonSegment("telemetry")
public interface TelemetryService {

  @JsonRequest
  CompletableFuture<GetStatusResponse> getStatus();
>>>>>>>> 42594bc97 (SLCORE-571 Make the client-api JSON-RPC friendly):rpc-protocol/src/main/java/org/sonarsource/sonarlint/core/rpc/protocol/backend/telemetry/TelemetryService.java
}
