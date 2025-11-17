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
import java.util.UUID;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverconnection.FileUtils;
import org.sonarsource.sonarlint.core.serverconnection.Organization;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil.writeToFile;

public class OrganizationStorage {
  public static final String ORGANIZATION_PB = "organization.pb";
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Path storageFilePath;
  private final RWLock rwLock = new RWLock();

  public OrganizationStorage(Path rootPath) {
    this.storageFilePath = rootPath.resolve(ORGANIZATION_PB);
  }

  public void store(Organization organization) {
    FileUtils.mkdirs(storageFilePath.getParent());
    var settingsToStore = adapt(organization);
    LOG.debug("Storing organization settings in {}", storageFilePath);
    rwLock.write(() -> writeToFile(settingsToStore, storageFilePath));
    LOG.debug("Stored organization settings");
  }

  public Optional<Organization> read() {
    return rwLock.read(() -> Files.exists(storageFilePath) ? Optional.of(adapt(ProtobufFileUtil.readFile(storageFilePath, Sonarlint.Organization.parser())))
      : Optional.empty());
  }

  private static Sonarlint.Organization adapt(Organization organization) {
    return Sonarlint.Organization.newBuilder().setId(organization.id()).setUuidV4(organization.uuidV4().toString()).build();
  }

  private static Organization adapt(Sonarlint.Organization organization) {
    return new Organization(organization.getId(), UUID.fromString(organization.getUuidV4()));
  }
}
