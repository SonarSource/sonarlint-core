/*
 * SonarLint slf4j log adaptor
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.slf4j;

/**
 * This class has the same signature has the {@link LoggerFactory} of slf4j.
 * Instead of finding an implementation of slf4j, it creates a bridge between the slf4j API and the sonar logging API.
 * It will always return the same slf4j Logger. This logger forwards all logs to a sonar API logger. 
 */
public class LoggerFactory {
  private static final LoggerAdapter ADAPTER = new LoggerAdapter();

  private LoggerFactory() {
    // only static methods
  }

  public static Logger getLogger(String name) {
    return ADAPTER;
  }

  public static Logger getLogger(Class clazz) {
    return ADAPTER;
  }
}
