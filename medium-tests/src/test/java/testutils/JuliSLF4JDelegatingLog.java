/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2024 SonarSource SA
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
package testutils;

import java.io.Serializable;
import org.apache.juli.logging.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.String.valueOf;

/**
 * Redirect JULI to slf4j
 */
public class JuliSLF4JDelegatingLog implements Log, Serializable {

  private transient volatile Logger log;

  /**
   * The default constructor is mandatory, as per the
   * {@link java.util.ServiceLoader ServiceLoader} specification.
   * <p>
   * This will construct an unusable {@link Log}, though.
   */
  public JuliSLF4JDelegatingLog() {
    super();
  }

  /**
   * This {@link String}-based constructor is required by the default "lean"
   * JULI {@link org.apache.juli.logging.LogFactory LogFactory}, in order to
   * accept this class a {@link Log} implementation
   * {@linkplain java.util.ServiceLoader provider}.
   *
   * @param name
   *            the name of the logger to create.
   * @see org.apache.juli.logging.LogFactory
   */
  public JuliSLF4JDelegatingLog(final String name) {
    super();
    setLogger(LoggerFactory.getLogger(name));
  }

  private void setLogger(final Logger logger) {
    log = logger;
  }

  @Override
  public boolean isTraceEnabled() {
    return log.isTraceEnabled();
  }

  @Override
  public boolean isDebugEnabled() {
    return log.isDebugEnabled();
  }

  @Override
  public boolean isInfoEnabled() {
    return log.isInfoEnabled();
  }

  @Override
  public boolean isWarnEnabled() {
    return log.isWarnEnabled();
  }

  @Override
  public boolean isErrorEnabled() {
    return log.isErrorEnabled();
  }

  /**
   * <p>
   * The implementation here delegates to the {@link Logger#isErrorEnabled
   * isErrorEnabled} method of the wrapped {@link Logger} instance, because
   * SLF4J has no {@code FATAL} level.
   *
   * @return {@code true} if the wrapped logger {@link Logger#isErrorEnabled
   *         isErrorEnabled}, or {@code false} otherwise.
   */
  @Override
  public boolean isFatalEnabled() {
    return log.isErrorEnabled();
  }

  @Override
  public void trace(final Object msg) {
    trace(msg, null);
  }

  @Override
  public void trace(final Object msg, final Throwable thrown) {
    doTrace(msg, thrown);
  }

  private void doTrace(final Object msg, final Throwable thrown) {
    log.trace(valueOf(msg), thrown);
  }

  @Override
  public void debug(final Object msg) {
    debug(msg, null);
  }

  @Override
  public void debug(final Object msg, final Throwable thrown) {
    doDebug(msg, thrown);
  }

  private void doDebug(final Object msg, final Throwable thrown) {
    log.debug(valueOf(msg), thrown);
  }

  @Override
  public void info(final Object msg) {
    info(msg, null);
  }

  @Override
  public void info(final Object msg, final Throwable thrown) {
    doInfo(msg, thrown);
  }

  private void doInfo(final Object msg, final Throwable thrown) {
    log.info(valueOf(msg), thrown);
  }

  @Override
  public void warn(final Object msg) {
    warn(msg, null);
  }

  @Override
  public void warn(final Object msg, final Throwable thrown) {
    doWarn(msg, thrown);
  }

  private void doWarn(final Object msg, final Throwable thrown) {
    log.warn(String.valueOf(msg), thrown);
  }

  @Override
  public void error(final Object msg) {
    error(msg, null);
  }

  @Override
  public void error(final Object msg, final Throwable thrown) {
    doError(msg, thrown);
  }

  private void doError(final Object msg, final Throwable thrown) {
    log.error(valueOf(msg), thrown);
  }

  /**
   * <p>
   * The implementation here converts the message to a {@linkplain String
   * string}, and logs it as {@code ERROR} level using the wrapped
   * {@link Logger} instance, SLF4J has no {@code FATAL} level.
   *
   * @param msg
   *            the message to log, to be converted to {@link String}
   */
  @Override
  public void fatal(final Object msg) {
    error(msg, null);
  }

  /**
   * <p>
   * The implementation here converts the message to a {@linkplain String
   * string}, and logs it along with the given throwable as {@code ERROR}
   * level, using the wrapped {@link Logger} instance, because SLF4J has no
   * {@code FATAL} level.
   *
   * @param msg
   *            the message to log, to be converted to {@link String}
   * @param thrown
   *            the throwable to log
   */
  @Override
  public void fatal(final Object msg, final Throwable thrown) {
    error(msg, thrown);
  }

}
