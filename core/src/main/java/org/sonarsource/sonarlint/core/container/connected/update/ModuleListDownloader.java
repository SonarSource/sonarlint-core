/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.connected.update;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import org.sonarqube.ws.WsComponents;
import org.sonarqube.ws.WsComponents.Component;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.plugin.Version;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleList;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleList.Module.Builder;
import org.sonarsource.sonarlint.core.util.ws.WsResponse;

import static org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient.paginate;

public class ModuleListDownloader {
  static final int PAGE_SIZE = 500;

  private final SonarLintWsClient wsClient;

  public ModuleListDownloader(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  public void fetchModulesListTo(Path dest, String serverVersion) {
    if (Version.create(serverVersion).compareToIgnoreQualifier(Version.create("6.3")) >= 0) {
      fetchModulesListAfter6dot3(dest);
    } else {
      fetchModulesListBefore6dot3(dest);
    }
  }

  private void fetchModulesListAfter6dot3(Path dest) {
    ModuleList.Builder moduleListBuilder = ModuleList.newBuilder();
    Builder moduleBuilder = ModuleList.Module.newBuilder();

    paginate(wsClient, "api/components/search.protobuf?qualifiers=TRK,BRC", WsComponents.SearchWsResponse::parseFrom, WsComponents.SearchWsResponse::getPaging,
      searchResponse -> {
        for (Component module : searchResponse.getComponentsList()) {
          moduleBuilder.clear();
          moduleListBuilder.putModulesByKey(module.getKey(), moduleBuilder
            .setKey(module.getKey())
            .setName(module.getName())
            .setQu(module.getQualifier())
            .build());
        }
      });

    ProtobufUtil.writeToFile(moduleListBuilder.build(), dest.resolve(StorageManager.MODULE_LIST_PB));
  }

  private void fetchModulesListBefore6dot3(Path dest) {
    try (WsResponse response = wsClient.get("api/projects/index?format=json&subprojects=true")) {
      try (Reader contentReader = response.contentReader()) {
        DefaultModule[] results = new Gson().fromJson(contentReader, DefaultModule[].class);

        ModuleList.Builder moduleListBuilder = ModuleList.newBuilder();
        Builder moduleBuilder = ModuleList.Module.newBuilder();
        for (DefaultModule module : results) {
          moduleBuilder.clear();
          moduleListBuilder.putModulesByKey(module.k, moduleBuilder
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
  }

  private static class DefaultModule {
    String k;
    String nm;
    String qu;
  }

}
