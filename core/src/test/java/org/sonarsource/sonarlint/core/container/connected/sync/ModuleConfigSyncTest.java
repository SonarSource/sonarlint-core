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
package org.sonarsource.sonarlint.core.container.connected.sync;

import java.io.File;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.utils.TempFolder;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ModuleConfiguration;
import org.sonarsource.sonarlint.core.proto.Sonarlint.Rules;
import org.sonarsource.sonarlint.core.proto.Sonarlint.Rules.Builder;
import org.sonarsource.sonarlint.core.proto.Sonarlint.Rules.QProfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ModuleConfigSyncTest {

  private static final String MODULE_KEY_WITH_BRANCH = "module:key/with_branch";
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void project_sync() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithStreamResponse("/api/qualityprofiles/search.protobuf?projectKey=module%3Akey%2Fwith_branch",
      "/sync/qualityprofiles.pb");
    WsClientTestUtils.addResponse(wsClient, "api/properties?format=json&key=module%3Akey%2Fwith_branch",
      "[{\"key\":\"sonar.qualitygate\",\"value\":\"1\",\"values\": []},"
        + "{\"key\":\"sonar.core.version\",\"value\":\"5.5-SNAPSHOT\"},"
        + "{\"key\":\"sonar.java.someProp\",\"value\":\"foo\"}]");

    File tempDir = temp.newFolder();
    File destDir = temp.newFolder();

    TempFolder tempFolder = mock(TempFolder.class);
    when(tempFolder.newDir()).thenReturn(tempDir);
    StorageManager storageManager = mock(StorageManager.class);
    org.sonarsource.sonarlint.core.proto.Sonarlint.GlobalProperties.Builder propBuilder = GlobalProperties.newBuilder();
    propBuilder.getMutableProperties().put("sonar.qualitygate", "2");
    propBuilder.getMutableProperties().put("sonar.core.version", "5.5-SNAPSHOT");
    when(storageManager.readGlobalPropertiesFromStorage()).thenReturn(propBuilder.build());
    Builder builder = Rules.newBuilder();
    Map<String, QProfile> mutableQprofilesByKey = builder.getMutableQprofilesByKey();
    mutableQprofilesByKey.put("cobol-sonar-way-65068", QProfile.newBuilder().build());
    mutableQprofilesByKey.put("cs-sonar-way-45021", QProfile.newBuilder().build());
    mutableQprofilesByKey.put("grvy-sonar-way-01377", QProfile.newBuilder().build());
    mutableQprofilesByKey.put("java-sonar-way-48319", QProfile.newBuilder().build());
    mutableQprofilesByKey.put("js-sonar-way-70754", QProfile.newBuilder().build());
    mutableQprofilesByKey.put("web-sonar-way-88384", QProfile.newBuilder().build());
    mutableQprofilesByKey.put("xoo-basic-50876", QProfile.newBuilder().build());
    mutableQprofilesByKey.put("xoo2-basic-34035", QProfile.newBuilder().build());
    when(storageManager.readRulesFromStorage()).thenReturn(builder.build());

    when(storageManager.getModuleStorageRoot(MODULE_KEY_WITH_BRANCH)).thenReturn(destDir.toPath());

    ModuleConfigSync projectSync = new ModuleConfigSync(storageManager, wsClient, tempFolder);

    projectSync.sync(MODULE_KEY_WITH_BRANCH);

    ModuleConfiguration moduleConfiguration = ProtobufUtil.readFile(destDir.toPath().resolve(StorageManager.MODULE_CONFIGURATION_PB), ModuleConfiguration.parser());
    assertThat(moduleConfiguration.getQprofilePerLanguage()).containsOnly(
      entry("cobol", "cobol-sonar-way-65068"),
      entry("cs", "cs-sonar-way-45021"),
      entry("grvy", "grvy-sonar-way-01377"),
      entry("java", "java-sonar-way-48319"),
      entry("js", "js-sonar-way-70754"),
      entry("web", "web-sonar-way-88384"),
      entry("xoo", "xoo-basic-50876"),
      entry("xoo2", "xoo2-basic-34035"));
  }

}
