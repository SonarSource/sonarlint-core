/*
 * SonarLint Core - Plugin Commons
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
package org.sonarsource.sonarlint.core.plugin.commons.loading;

import java.io.IOException;

/**
 * Some plugins use SLF4J instead of the Sonar plugin API logging facade. As some IDEs are exposing SLF4J,
 * we don't want plugin logs to ends up in IDE logs, but we want to intercept them. So plugin classloaders will extend this custom classloader, that will intercept
 * attempts to get SLF4J classes, and then load our own classes from the sonarlint-slf4j-sonar-log module.
 *
 */
public class Slf4jBridgeClassLoader extends ClassLoader {

  private final ClassLoader sonarLintClassLoader;

  public Slf4jBridgeClassLoader(ClassLoader sonarLintClassLoader) {
    // Use Platform ClassLoader as parent in order to avoid finding SLF4J in the Application classloader (some IDEs can provide SLF4J to plugins)
    super(ClassLoader.getPlatformClassLoader());
    this.sonarLintClassLoader = sonarLintClassLoader;
  }

  @Override
  protected Class<?> findClass(final String name) throws ClassNotFoundException {
    if (name.startsWith("org.sonarsource.sonarlint.core.commons.log")) {
      return sonarLintClassLoader.loadClass(name);
    }
    if (name.startsWith("org.slf4j")) {
      var path = name.replace('.', '/').concat(".clazz");
      var classContentPath = "slf4j-sonar-log/" + path;
      try (var is = sonarLintClassLoader.getResourceAsStream(classContentPath)) {
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
