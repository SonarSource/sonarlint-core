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
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.plugin.Version;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleList;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleList.Module.Builder;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.StringUtils;
import org.sonarsource.sonarlint.core.util.ws.WsResponse;

public class ModuleListDownloader {
  static final int PAGE_SIZE = 500;

  private final SonarLintWsClient wsClient;

  public ModuleListDownloader(SonarLintWsClient wsClient) {
    this.wsClient = wsClient;
  }

  public void fetchModulesListTo(Path dest, String serverVersion, ProgressWrapper progress) {
    if (Version.create(serverVersion).compareToIgnoreQualifier(Version.create("6.3")) >= 0) {
      fetchModulesListAfter6dot3(dest, progress);
    } else {
      fetchModulesListBefore6dot3(dest);
    }
  }

  private void fetchModulesListAfter6dot3(Path dest, ProgressWrapper progress) {
    ModuleList.Builder moduleListBuilder = ModuleList.newBuilder();
    Builder moduleBuilder = ModuleList.Module.newBuilder();

    String baseUrl = "api/components/search.protobuf?qualifiers=TRK,BRC";
    if (wsClient.getOrganizationKey() != null) {
      baseUrl += "&organization=" + StringUtils.urlEncode(wsClient.getOrganizationKey());
    }
    SonarLintWsClient.getPaginated(wsClient, baseUrl,
      WsComponents.SearchWsResponse::parseFrom,
      WsComponents.SearchWsResponse::getPaging,
      WsComponents.SearchWsResponse::getComponentsList,
      module -> {
        moduleBuilder.clear();
        moduleListBuilder.putModulesByKey(module.getKey(), moduleBuilder
          .setKey(module.getKey())
          .setName(module.getName())
          .setQu(module.getQualifier())
          .build());
      },
      progress);

    ProtobufUtil.writeToFile(moduleListBuilder.build(), dest.resolve(StoragePaths.MODULE_LIST_PB));
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
        ProtobufUtil.writeToFile(moduleListBuilder.build(), dest.resolve(StoragePaths.MODULE_LIST_PB));
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
