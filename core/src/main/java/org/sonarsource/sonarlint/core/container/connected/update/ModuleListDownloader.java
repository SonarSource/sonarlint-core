/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.connected.update;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import org.sonarqube.ws.client.WsResponse;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleList;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleList.Module.Builder;

public class ModuleListDownloader {

  private final SonarLintWsClient wsClient;

  public ModuleListDownloader(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  public void fetchModulesList(Path dest) {
    WsResponse response = wsClient.get("api/projects/index?format=json&subprojects=true");
    try (Reader contentReader = response.contentReader()) {
      DefaultModule[] results = new Gson().fromJson(contentReader, DefaultModule[].class);

      ModuleList.Builder moduleListBuilder = ModuleList.newBuilder();
      Builder moduleBuilder = ModuleList.Module.newBuilder();
      for (DefaultModule module : results) {
        moduleBuilder.clear();
        moduleListBuilder.getMutableModulesByKey().put(module.k, moduleBuilder
          .setKey(module.k)
          .setName(module.nm)
          .setQu(module.qu)
          .build());
      }
      ProtobufUtil.writeToFile(moduleListBuilder.build(), dest.resolve(StorageManager.MODULE_LIST_PB));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load module list", e);
    }
  }

  private static class DefaultModule {
    String k;
    String nm;
    String qu;
  }

}
