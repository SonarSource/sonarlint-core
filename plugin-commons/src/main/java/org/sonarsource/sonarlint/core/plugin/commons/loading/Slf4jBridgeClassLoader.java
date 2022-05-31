/*
 * SonarLint Core - Plugin Commons
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.plugin.commons.loading;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Some plugins uses slf4j instead of the Sonar plugin API logging facade. As some IDEs are exposing slf4j,
 * we don't want plugin logs to ends up in IDE logs, but we want to intercept them. So plugin classloaders will extend this custom classloader, that will intercept
 * attempts to get slf4j classes, and then load our own classes from the sonarlint-slf4j-sonar-log module.
 *
 */
public class Slf4jBridgeClassLoader extends URLClassLoader {

  public Slf4jBridgeClassLoader(ClassLoader parent) {
    super(new URL[0], parent);
  }

  @Override
  protected Class<?> findClass(final String name) throws ClassNotFoundException {
    if (name.startsWith("org.slf4j")) {
      var path = name.replace('.', '/').concat(".clazz");
      var classContentPath = "slf4j-sonar-log/" + path;
      try (var is = getParent().getResourceAsStream(classContentPath)) {
        if (is == null) {
          throw new IllegalStateException("Unable to find resource " + classContentPath);
        }
        var classBytes = is.readAllBytes();
        return defineClass(name, classBytes, 0, classBytes.length);
      } catch (IOException e) {
        throw new ClassNotFoundException("Unable to load class " + name, e);
      }
    }
    return super.findClass(name);
  }

}
