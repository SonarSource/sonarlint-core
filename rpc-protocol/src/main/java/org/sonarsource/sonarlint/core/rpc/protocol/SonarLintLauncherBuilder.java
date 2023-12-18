/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol;

import com.google.gson.GsonBuilder;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.json.adapters.CollectionTypeAdapter;
import org.eclipse.lsp4j.jsonrpc.json.adapters.EitherTypeAdapter;
import org.eclipse.lsp4j.jsonrpc.json.adapters.MessageTypeAdapter;
import org.eclipse.lsp4j.jsonrpc.json.adapters.ThrowableTypeAdapter;
import org.eclipse.lsp4j.jsonrpc.json.adapters.TupleTypeAdapters;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.InstantTypeAdapter;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.PathTypeAdapter;
import org.sonarsource.sonarlint.core.rpc.protocol.adapter.UuidTypeAdapter;

/**
 * A modified version of the LSP4J LauncherBuilder with some customization/workarounds.
 */
public class SonarLintLauncherBuilder<T> extends Launcher.Builder<T> {

  @Override
  protected MessageJsonHandler createJsonHandler() {
    Map<String, JsonRpcMethod> supportedMethods = getSupportedMethods();
    return new MessageJsonHandler(supportedMethods) {
      @Override
      public GsonBuilder getDefaultGsonBuilder() {
        // We don't want the EnumTypeAdapter from lsp4j, as we want to serialize enums as string (this is the default in Gson)
        return new GsonBuilder()
          .registerTypeAdapterFactory(new CollectionTypeAdapter.Factory())
          .registerTypeAdapterFactory(new ThrowableTypeAdapter.Factory())
          .registerTypeAdapterFactory(new EitherTypeAdapter.Factory())
          .registerTypeAdapterFactory(new TupleTypeAdapters.TwoTypeAdapterFactory())
          .registerTypeAdapterFactory(new MessageTypeAdapter.Factory(this))
          .registerTypeHierarchyAdapter(Path.class, new PathTypeAdapter())
          .registerTypeHierarchyAdapter(Instant.class, new InstantTypeAdapter())
          .registerTypeHierarchyAdapter(UUID.class, new UuidTypeAdapter());
      }
    };
  }
}
