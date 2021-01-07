/*
 * SonarLint Core - Client API
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.common;

public abstract class ProgressMonitor {
  /**
   * Returns true if the task should be cancelled as soon as possible.
   */
  public boolean isCanceled() {
    return false;
  }
  
  /**
   * Handles a message regarding the current action
   */
  public void setMessage(String msg) {
    
  }
  
  /**
   * Handles the approximate fraction of the task completed.
   * @param fraction Number between 0.0f and 1.0f
   */
  public void setFraction(float fraction) {
    
  }
  
  /**
   * Handles whether the task in progress can determinate the fraction of its progress.
   * If not set, it should be assumed false
   */
  public void setIndeterminate(boolean indeterminate) {
    
  }
  
  /**
   * Execute a section of code that can't be canceled
   */
  public void executeNonCancelableSection(Runnable nonCancelable) {
    nonCancelable.run();
  }
  
}
