/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2025 SonarSource Sàrl
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverconnection.AiCodeFixFeatureEnablement;
import org.sonarsource.sonarlint.core.serverconnection.AiCodeFixSettings;
import org.sonarsource.sonarlint.core.serverconnection.FileUtils;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil.writeToFile;

public class AiCodeFixStorage {
  public static final String AI_CODEFIX_PB = "ai_codefix.pb";
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Path storageFilePath;
  private final RWLock rwLock = new RWLock();

  public AiCodeFixStorage(Path rootPath) {
    this.storageFilePath = rootPath.resolve(AI_CODEFIX_PB);
  }

  public void store(AiCodeFixSettings settings) {
    FileUtils.mkdirs(storageFilePath.getParent());
    var settingsToStore = adapt(settings);
    LOG.debug("Storing AI CodeFix settings in {}", storageFilePath);
    rwLock.write(() -> writeToFile(settingsToStore, storageFilePath));
    LOG.debug("Stored AI CodeFix settings");
  }

  public Optional<AiCodeFixSettings> read() {
    return rwLock.read(() -> Files.exists(storageFilePath) ? Optional.of(adapt(ProtobufFileUtil.readFile(storageFilePath, Sonarlint.AiCodeFixSettings.parser())))
      : Optional.empty());
  }

  private static Sonarlint.AiCodeFixSettings adapt(AiCodeFixSettings settings) {
    return Sonarlint.AiCodeFixSettings.newBuilder()
      .addAllSupportedRules(settings.supportedRules())
      .setEnablement(Sonarlint.AiCodeFixEnablement.valueOf(settings.enablement().name()))
      .setOrganizationEligible(settings.isOrganizationEligible())
      .addAllEnabledProjectKeys(settings.enabledProjectKeys())
      .build();
  }

  private static AiCodeFixSettings adapt(Sonarlint.AiCodeFixSettings settings) {
    return new AiCodeFixSettings(Set.copyOf(settings.getSupportedRulesList()), settings.getOrganizationEligible(),
      AiCodeFixFeatureEnablement.valueOf(settings.getEnablement().name()), Set.copyOf(settings.getEnabledProjectKeysList()));
  }

}
