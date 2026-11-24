/*
 * SonarLint Core - Server Connection
 * Copyright (C) SonarSource Sàrl
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

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.commons.codec.digest.DigestUtils;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;

/**
 * Cached static resources for one connection, stored beside {@code plugins/} at {@code resources/{sha256}/{filename}}.
 */
public class StaticResourceStorage {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  static final String EXTRACTED_DIRECTORY = "extracted";
  public static final String EXTRACTION_COMPLETE = ".complete";

  private final Path root;

  public StaticResourceStorage(Path connectionStorageRoot) {
    this.root = connectionStorageRoot.resolve("resources");
  }

  public Path file(String hash, String filename) {
    return directory(hash).resolve(filename);
  }

  public Path extractionDirectory(String hash) {
    return directory(hash).resolve(EXTRACTED_DIRECTORY);
  }

  public boolean matches(Path file, String hash) {
    if (!Files.isRegularFile(file)) {
      return false;
    }
    try (var input = Files.newInputStream(file)) {
      return hash.equalsIgnoreCase(DigestUtils.sha256Hex(input));
    } catch (IOException e) {
      return false;
    }
  }

  public Path store(String hash, String filename, byte[] bytes) throws IOException {
    var target = file(hash, filename);
    Files.createDirectories(target.getParent());
    var temporary = Files.createTempFile(target.getParent(), ".download-", ".tmp");
    try {
      Files.write(temporary, bytes);
      moveIntoPlace(temporary, target);
    } finally {
      Files.deleteIfExists(temporary);
    }
    return target;
  }

  private static void moveIntoPlace(Path temporary, Path target) throws IOException {
    try {
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /**
   * Deletes {@code resources/{sha256}/} directories whose hash is absent from the plugins' inventories.
   * A directory that cannot be removed, because a file is still open, is left in place.
   */
  public void purgeExcept(Collection<StoredPlugin> plugins) {
    var keep = plugins.stream()
      .flatMap(plugin -> plugin.getStaticResources().stream())
      .map(resource -> resource.hash().toLowerCase(Locale.ROOT))
      .collect(Collectors.toSet());
    if (!Files.isDirectory(root)) {
      return;
    }
    try (var directories = Files.list(root)) {
      directories.filter(Files::isDirectory).forEach(directory -> {
        if (!keep.contains(directory.getFileName().toString().toLowerCase(Locale.ROOT))) {
          deleteIfPossible(directory);
        }
      });
    } catch (IOException e) {
      LOG.debug("Cannot list cached static resources in {}", root, e);
    }
  }

  private static void deleteIfPossible(Path directory) {
    var complete = directory.resolve(EXTRACTED_DIRECTORY).resolve(EXTRACTION_COMPLETE);
    try {
      Files.deleteIfExists(complete);
    } catch (IOException e) {
      LOG.debug("Leaving static resource {} in place because it is still in use", complete, e);
      return;
    }
    try (var walk = Files.walk(directory)) {
      for (var path : walk.sorted((left, right) -> right.compareTo(left)).toList()) {
        try {
          Files.deleteIfExists(path);
        } catch (IOException e) {
          LOG.debug("Leaving static resource {} in place because it is still in use", path, e);
          return;
        }
      }
    } catch (IOException e) {
      LOG.debug("Cannot purge static resource {}", directory, e);
    }
  }

  private Path directory(String hash) {
    if (!hash.matches("[a-fA-F0-9]+")) {
      throw new IllegalArgumentException("Invalid static resource hash: " + hash);
    }
    return root.resolve(hash.toLowerCase(Locale.ROOT));
  }

}
