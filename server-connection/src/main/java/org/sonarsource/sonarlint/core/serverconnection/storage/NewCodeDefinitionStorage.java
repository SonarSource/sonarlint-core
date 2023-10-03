/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.sonarsource.sonarlint.core.commons.NewCodeDefinition;
import org.sonarsource.sonarlint.core.commons.NewCodeMode;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverconnection.FileUtils;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil.writeToFile;

public class NewCodeDefinitionStorage {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  public static final String NEW_CODE_DEFINITION_PB = "new_code_definition.pb";

  private final Path storageFilePath;
  private final RWLock rwLock = new RWLock();

  public NewCodeDefinitionStorage(Path rootPath) {
    this.storageFilePath = rootPath.resolve(NEW_CODE_DEFINITION_PB);
  }

  public void store(NewCodeDefinition newCodeDefinition) {
    FileUtils.mkdirs(storageFilePath.getParent());
    var newCodeDefinitionToStore = adapt(newCodeDefinition);
    LOG.debug("Storing new code definition in {}", storageFilePath);
    rwLock.write(() -> writeToFile(newCodeDefinitionToStore, storageFilePath));
  }

  public Optional<NewCodeDefinition> read() {
    return rwLock.read(() -> Files.exists(storageFilePath) ?
      Optional.of(adapt(ProtobufFileUtil.readFile(storageFilePath, Sonarlint.NewCodeDefinition.parser())))
      : Optional.empty());
  }

  private static Sonarlint.NewCodeDefinition adapt(NewCodeDefinition newCodeDefinition) {
    var builder = Sonarlint.NewCodeDefinition.newBuilder()
      .setMode(Sonarlint.NewCodeDefinitionMode.valueOf(newCodeDefinition.getMode().name()));
    if (newCodeDefinition.getMode() == NewCodeMode.NUMBER_OF_DAYS) {
      var newCodeNumberOfDays = (NewCodeDefinition.NewCodeNumberOfDays) newCodeDefinition;
      builder.setDays(newCodeNumberOfDays.getDays());
    }
    if (newCodeDefinition.getMode() != NewCodeMode.REFERENCE_BRANCH) {
      var newCodeDefinitionWithDate = (NewCodeDefinition.NewCodeDefinitionWithDate) newCodeDefinition;
      builder.setThresholdDate(newCodeDefinitionWithDate.getThresholdDate());
    } else {
      var newCodeReferenceBranch = (NewCodeDefinition.NewCodeReferenceBranch) newCodeDefinition;
      builder.setReferenceBranch(newCodeReferenceBranch.getBranchName());
    }
    if (newCodeDefinition.getMode() == NewCodeMode.PREVIOUS_VERSION) {
      var newCodePreviousVersion = (NewCodeDefinition.NewCodePreviousVersion) newCodeDefinition;
      var version = newCodePreviousVersion.getVersion();
      if (version != null) {
        builder.setVersion(version);
      }
    }
    return builder.build();
  }

  private static NewCodeDefinition adapt(Sonarlint.NewCodeDefinition newCodeDefinition) {
    var thresholdDate = newCodeDefinition.getThresholdDate();
    var mode = newCodeDefinition.getMode();
    switch (mode) {
      case NUMBER_OF_DAYS:
        return NewCodeDefinition.withNumberOfDays(newCodeDefinition.getDays(), thresholdDate);
      case PREVIOUS_VERSION:
        return NewCodeDefinition.withPreviousVersion(thresholdDate, newCodeDefinition.hasVersion() ? newCodeDefinition.getVersion() : null);
      case REFERENCE_BRANCH:
        return NewCodeDefinition.withReferenceBranch(newCodeDefinition.getReferenceBranch());
      case SPECIFIC_ANALYSIS:
        return NewCodeDefinition.withSpecificAnalysis(thresholdDate);
      default:
        throw new IllegalArgumentException("Unsupported mode: " + mode);
    }
  }
}
