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
package org.sonarsource.sonarlint.core.plugin;

import java.net.URL;
import java.util.List;

public interface PluginIndexProvider {

  List<PluginReference> references();

  class PluginReference {

    private String hash;
    private URL downloadUrl;
    private String filename;

    public String getHash() {
      return hash;
    }

    public PluginReference setHash(String hash) {
      this.hash = hash;
      return this;
    }

    public URL getDownloadUrl() {
      return downloadUrl;
    }

    public PluginReference setDownloadUrl(URL downloadUrl) {
      this.downloadUrl = downloadUrl;
      return this;
    }

    public String getFilename() {
      return filename;
    }

    public PluginReference setFilename(String filename) {
      this.filename = filename;
      return this;
    }

  }

}
