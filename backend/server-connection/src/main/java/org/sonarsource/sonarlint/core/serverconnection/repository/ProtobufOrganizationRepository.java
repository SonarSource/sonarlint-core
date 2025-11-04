/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverconnection.repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverconnection.FileUtils;
import org.sonarsource.sonarlint.core.serverconnection.Organization;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;
import org.sonarsource.sonarlint.core.serverconnection.storage.RWLock;

import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil.writeToFile;

/**
 * Protobuf-based implementation of OrganizationRepository.
 */
public class ProtobufOrganizationRepository implements OrganizationRepository {
  public static final String ORGANIZATION_PB = "organization.pb";
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final Path storageRoot;

  public ProtobufOrganizationRepository(Path storageRoot) {
    this.storageRoot = storageRoot;
  }

  private Path getStorageFilePath(String connectionId) {
    return storageRoot.resolve(encodeForFs(connectionId)).resolve(ORGANIZATION_PB);
  }

  @Override
  public void store(String connectionId, Organization organization) {
    var storageFilePath = getStorageFilePath(connectionId);
    FileUtils.mkdirs(storageFilePath.getParent());
    var settingsToStore = adapt(organization);
    LOG.debug("Storing organization settings in {}", storageFilePath);
    new RWLock().write(() -> writeToFile(settingsToStore, storageFilePath));
    LOG.debug("Stored organization settings");
  }

  @Override
  public Optional<Organization> read(String connectionId) {
    var storageFilePath = getStorageFilePath(connectionId);
    return new RWLock().read(() -> Files.exists(storageFilePath) ? Optional.of(adapt(ProtobufFileUtil.readFile(storageFilePath, Sonarlint.Organization.parser())))
      : Optional.empty());
  }

  private static Sonarlint.Organization adapt(Organization organization) {
    return Sonarlint.Organization.newBuilder().setId(organization.id()).setUuidV4(organization.uuidV4().toString()).build();
  }

  private static Organization adapt(Sonarlint.Organization organization) {
    return new Organization(organization.getId(), UUID.fromString(organization.getUuidV4()));
  }
}

