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
package org.sonarsource.sonarlint.core.telemetry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Telemetry {
  private final TelemetryStorage storage;
  private final Path storageFilePath;
  private final String productName;
  private final String productVersion;

  private TelemetryDataCollection dataCollection;
  private TelemetryClient client;

  /**
   * Creates telemetry based on a file path where data is persisted.
   * Will throw an exception if for any reason the file is not accessible or has data that can't be parsed. 
   */
  public Telemetry(Path storageFilePath, String productName, String productVersion) throws Exception {
    this.storageFilePath = storageFilePath;
    this.productName = productName;
    this.productVersion = productVersion;
    if (Files.exists(storageFilePath)) {
      this.storage = TelemetryStorage.load(storageFilePath);
    } else {
      this.storage = new TelemetryStorage();
    }
  }

  public TelemetryClient getClient() {
    if (client == null) {
      client = new TelemetryClient(productName, productVersion, storage, storageFilePath);
    }
    return client;
  }

  public TelemetryDataCollection getDataCollection() {
    if (dataCollection == null) {
      dataCollection = new TelemetryDataCollection(storage, storageFilePath);
    }
    return dataCollection;
  }

  public void save() throws IOException {
    storage.save(storageFilePath);
  }

  public void enable(boolean enable) {
    storage.setEnabled(enable);
    storage.safeSave(storageFilePath);
  }

  public boolean enabled() {
    return storage.enabled();
  }
}
