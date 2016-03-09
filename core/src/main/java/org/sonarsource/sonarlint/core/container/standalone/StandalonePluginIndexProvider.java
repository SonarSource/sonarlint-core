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
package org.sonarsource.sonarlint.core.container.standalone;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import org.apache.commons.codec.digest.DigestUtils;
import org.sonar.api.internal.apachecommons.lang.StringUtils;
import org.sonarsource.sonarlint.core.plugin.PluginIndexProvider;

/**
 * List of plugins are provided by client
 */
public class StandalonePluginIndexProvider implements PluginIndexProvider {

  private static final class UrlToPluginReference implements Function<URL, PluginReference> {
    @Override
    public PluginReference apply(URL input) {
      return toPlugin(input);
    }

    private static PluginReference toPlugin(URL input) {
      try {
        PluginReference ref = new PluginReference();
        try (InputStream is = input.openStream()) {
          ref.setHash(DigestUtils.md5Hex(is));
        }
        ref.setDownloadUrl(input);
        ref.setFilename(StringUtils.substringAfterLast(input.getFile(), "/"));
        return ref;
      } catch (Exception e) {
        throw new IllegalStateException("Unable to load local plugins", e);
      }
    }
  }

  private final List<URL> pluginUrls;

  public StandalonePluginIndexProvider(List<URL> pluginUrls) {
    this.pluginUrls = pluginUrls;
  }

  @Override
  public List<PluginReference> references() {
    return Lists.transform(pluginUrls, new UrlToPluginReference());
  }
}
