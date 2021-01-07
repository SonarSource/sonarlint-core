/*
 * SonarLint slf4j log adaptor
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
package org.slf4j;

/**
 * The org.slf4j.Logger interface is the main user entry point of SLF4J API. 
 * It is expected that logging takes place through concrete implementations 
 * of this interface.
 *
 * <h3>Typical usage pattern:</h3>
 * <pre>
 * import org.slf4j.Logger;
 * import org.slf4j.LoggerFactory;
 * 
 * public class Wombat {
 *
 *   <span style="color:green">final static Logger logger = LoggerFactory.getLogger(Wombat.class);</span>
 *   Integer t;
 *   Integer oldT;
 *
 *   public void setTemperature(Integer temperature) {
 *     oldT = t;        
 *     t = temperature;
 *     <span style="color:green">logger.debug("Temperature set to {}. Old temperature was {}.", t, oldT);</span>
 *     if(temperature.intValue() &gt; 50) {
 *       <span style="color:green">logger.info("Temperature has risen above 50 degrees.");</span>
 *     }
 *   }
 * }
 </pre>


 
 * @author Ceki G&uuml;lc&uuml;
 */
public interface Logger {


  /**
   * Case insensitive String constant used to retrieve the name of the root logger.
   * @since 1.3
   */
  final public String ROOT_LOGGER_NAME = "ROOT";
  
  /**
   * Return the name of this <code>Logger</code> instance.
   */
  public String getName();

  /**
   * Is the logger instance enabled for the TRACE level?
   * @return True if this Logger is enabled for the TRACE level,
   * false otherwise.
   * 
   * @since 1.4
   */
  public boolean isTraceEnabled();
    

  /**
   * Log a message at the TRACE level.
   *
   * @param msg the message string to be logged
   * @since 1.4
   */
  public void trace(String msg);

  
  /**
   * Log a message at the TRACE level according to the specified format
   * and argument.
   * 
   * <p>This form avoids superfluous object creation when the logger
   * is disabled for the TRACE level. </p>
   *
   * @param format the format string 
   * @param arg  the argument
   * 
   * @since 1.4
   */
  public void trace(String format, Object arg);


   
  /**
   * Log a message at the TRACE level according to the specified format
   * and arguments.
   * 
   * <p>This form avoids superfluous object creation when the logger
   * is disabled for the TRACE level. </p>
   *
   * @param format the format string
   * @param arg1  the first argument
   * @param arg2  the second argument
   * 
   * @since 1.4
   */
  public void trace(String format, Object arg1, Object arg2);

  /**
   * Log a message at the TRACE level according to the specified format
   * and arguments.
   * 
   * <p>This form avoids superfluous object creation when the logger
   * is disabled for the TRACE level. </p>
   *
   * @param format the format string
   * @param argArray an array of arguments
   * 
   * @since 1.4
   */
  public void trace(String format, Object[] argArray);
  
  /**
   * Log an exception (throwable) at the TRACE level with an
   * accompanying message. 
   * 
   * @param msg the message accompanying the exception
   * @param t the exception (throwable) to log
   * 
   * @since 1.4
   */ 
  public void trace(String msg, Throwable t);
 
  
  /**
   * Similar to {@link #isTraceEnabled()} method except that the
   * marker data is also taken into account.
   * 
   * @param marker The marker data to take into consideration
   * 
   * @since 1.4
   */
  public boolean isTraceEnabled(Marker marker);
  
  /**
   * Log a message with the specific Marker at the TRACE level.
   * 
   * @param marker the marker data specific to this log statement
   * @param msg the message string to be logged
   * 
   * @since 1.4
   */
  public void trace(Marker marker, String msg);
  
  /**
   * This method is similar to {@link #trace(String, Object)} method except that the 
   * marker data is also taken into consideration.
   * 
   * @param marker the marker data specific to this log statement
   * @param format the format string
   * @param arg the argument
   * 
   * @since 1.4
   */
  public void trace(Marker marker, String format, Object arg);
 
 
  /**
   * This method is similar to {@link #trace(String, Object, Object)}
   * method except that the marker data is also taken into
   * consideration.
   *
   * @param marker the marker data specific to this log statement
   * @param format  the format string
   * @param arg1  the first argument
   * @param arg2  the second argument
   * 
   * @since 1.4
   */
  public void trace(Marker marker, String format, Object arg1, Object arg2);

  /**
   * This method is similar to {@link #trace(String, Object[])}
   * method except that the marker data is also taken into
   * consideration.
   *
   * @param marker the marker data specific to this log statement
   * @param format  the format string
   * @param argArray an array of arguments
   * 
   * @since 1.4
   */
  public void trace(Marker marker, String format, Object[] argArray);

  
  /**
   * This method is similar to {@link #trace(String, Throwable)} method except that the
   * marker data is also taken into consideration.
   * 
   * @param marker the marker data specific to this log statement
   * @param msg the message accompanying the exception
   * @param t the exception (throwable) to log
   * 
   * @since 1.4
   */ 
  public void trace(Marker marker, String msg, Throwable t);

  
  /**
   * Is the logger instance enabled for the DEBUG level?
   * @return True if this Logger is enabled for the DEBUG level,
   * false otherwise.
   * 
   */
  public boolean isDebugEnabled();
  
  
  /**
   * Log a message at the DEBUG level.
   *
   * @param msg the message string to be logged
   */
  public void debug(String msg);
  
  
  /**
   * Log a message at the DEBUG level according to the specified format
   * and argument.
   * 
   * <p>This form avoids superfluous object creation when the logger
   * is disabled for the DEBUG level. </p>
   *
   * @param format the format string 
   * @param arg  the argument
   */
  public void debug(String format, Object arg);


  
  /**
   * Log a message at the DEBUG level according to the specified format
   * and arguments.
   * 
   * <p>This form avoids superfluous object creation when the logger
   * is disabled for the DEBUG level. </p>
   *
   * @param format the format string
   * @param arg1  the first argument
   * @param arg2  the second argument
   */
  public void debug(String format, Object arg1, Object arg2);

  /**
   * Log a message at the DEBUG level according to the specified format
   * and arguments.
   * 
   * <p>This form avoids superfluous object creation when the logger
   * is disabled for the DEBUG level. </p>
   *
   * @param format the format string
   * @param argArray an array of arguments
   */
  public void debug(String format, Object[] argArray);
  
  /**
   * Log an exception (throwable) at the DEBUG level with an
   * accompanying message. 
   * 
   * @param msg the message accompanying the exception
   * @param t the exception (throwable) to log
   */ 
  public void debug(String msg, Throwable t);
 
  
  /**
   * Similar to {@link #isDebugEnabled()} method except that the
   * marker data is also taken into account.
   * 
   * @param marker The marker data to take into consideration
   */
  public boolean isDebugEnabled(Marker marker);
  
  /**
   * Log a message with the specific Marker at the DEBUG level.
   * 
   * @param marker the marker data specific to this log statement
   * @param msg the message string to be logged
   */
  public void debug(Marker marker, String msg);
  
  /**
   * This method is similar to {@link #debug(String, Object)} method except that the 
   * marker data is also taken into consideration.
   * 
   * @param marker the marker data specific to this log statement
   * @param format the format string
   * @param arg the argument
   */
  public void debug(Marker marker, String format, Object arg);
 
 
  /**
   * This method is similar to {@link #debug(String, Object, Object)}
   * method except that the marker data is also taken into
   * consideration.
   *
   * @param marker the marker data specific to this log statement
   * @param format  the format string
   * @param arg1  the first argument
   * @param arg2  the second argument
   */
  public void debug(Marker marker, String format, Object arg1, Object arg2);

  /**
   * This method is similar to {@link #debug(String, Object[])}
   * method except that the marker data is also taken into
   * consideration.
   *
   * @param marker the marker data specific to this log statement
   * @param format  the format string
   * @param argArray an array of arguments
   */
  public void debug(Marker marker, String format, Object[] argArray);

  
  /**
   * This method is similar to {@link #debug(String, Throwable)} method except that the
   * marker data is also taken into consideration.
   * 
   * @param marker the marker data specific to this log statement
   * @param msg the message accompanying the exception
   * @param t the exception (throwable) to log
   */ 
  public void debug(Marker marker, String msg, Throwable t);
  
  
  /**
   * Is the logger instance enabled for the INFO level?
   * @return True if this Logger is enabled for the INFO level,
   * false otherwise.
   */
  public boolean isInfoEnabled();

  
  /**
   * Log a message at the INFO level.
   *
   * @param msg the message string to be logged
   */
  public void info(String msg);
  

  /**
   * Log a message at the INFO level according to the specified format
   * and argument.
   * 
   * <p>This form avoids superfluous object creation when the logger
   * is disabled for the INFO level. </p>
   *
   * @param format the format string 
   * @param arg  the argument
   */
  public void info(String format, Object arg);

  
  /**
   * Log a message at the INFO level according to the specified format
   * and arguments.
   * 
   * <p>This form avoids superfluous object creation when the logger
   * is disabled for the INFO level. </p>
   *
   * @param format the format string
   * @param arg1  the first argument
   * @param arg2  the second argument
   */
  public void info(String format, Object arg1, Object arg2);

  /**
   * Log a message at the INFO level according to the specified format
   * and arguments.
   * 
   * <p>This form avoids superfluous object creation when the logger
   * is disabled for the INFO level. </p>
   *
   * @param format the format string
   * @param argArray an array of arguments
   */
  public void info(String format, Object[] argArray);
  
  /**
   * Log an exception (throwable) at the INFO level with an
   * accompanying message. 
   * 
   * @param msg the message accompanying the exception
   * @param t the exception (throwable) to log 
   */
  public void info(String msg, Throwable t);

  /**
   * Similar to {@link #isInfoEnabled()} method except that the marker
   * data is also taken into consideration.
   *
   * @param marker The marker data to take into consideration
   */
  public boolean isInfoEnabled(Marker marker);
  
  /**
   * Log a message with the specific Marker at the INFO level.
   * 
   * @param marker The marker specific to this log statement
   * @param msg the message string to be logged
   */
  public void info(Marker marker, String msg);
  
  /**
   * This method is similar to {@link #info(String, Object)} method except that the 
   * marker data is also taken into consideration.
   *
   * @param marker the marker data specific to this log statement
   * @param format the format string
   * @param arg the argument
   */
  public void info(Marker marker, String format, Object arg);
  
  /**
   * This method is similar to {@link #info(String, Object, Object)}
   * method except that the marker data is also taken into
   * consideration.
   * 
   * @param marker the marker data specific to this log statement
   * @param format  the format string
   * @param arg1  the first argument
   * @param arg2  the second argument
   */
  public void info(Marker marker, String format, Object arg1, Object arg2);  
  
  
  /**
   * This method is similar to {@link #info(String, Object[])}
   * method except that the marker data is also taken into
   * consideration.
   *
   * @param marker the marker data specific to this log statement
   * @param format  the format string
   * @param argArray an array of arguments
   */
  public void info(Marker marker, String format, Object[] argArray);

  
  /**
   * This method is similar to {@link #info(String, Throwable)} method
   * except that the marker data is also taken into consideration.
   * 
   * @param marker the marker data for this log statement
   * @param msg the message accompanying the exception
   * @param t the exception (throwable) to log
   */ 
  public void info(Marker marker, String msg, Throwable t); 

  
  /**
   * Is the logger instance enabled for the WARN level?
   * @return True if this Logger is enabled for the WARN level,
   * false otherwise.
   */
  public boolean isWarnEnabled();

  /**
   * Log a message at the WARN level.
   *
   * @param msg the message string to be logged
   */
  public void warn(String msg);

 /**
   * Log a message at the WARN level according to the specified format
   * and argument.
   * 
   * <p>This form avoids superfluous object creation when the logger
   * is disabled for the WARN level. </p>
   *
   * @param format the format string 
   * @param arg  the argument
   */
  public void warn(String format, Object arg);

  
  /**
   * Log a message at the WARN level according to the specified format
   * and arguments.
   * 
   * <p>This form avoids superfluous object creation when the logger
   * is disabled for the WARN level. </p>
   *
   * @param format the format string
   * @param argArray an array of arguments
   */
  public void warn(String format, Object[] argArray);
  
  /**
   * Log a message at the WARN level according to the specified format
   * and arguments.
   * 
   * <p>This form avoids superfluous object creation when the logger
   * is disabled for the WARN level. </p>
   *
   * @param format the format string
   * @param arg1  the first argument
   * @param arg2  the second argument
   */
  public void warn(String format, Object arg1, Object arg2);
  
  /**
   * Log an exception (throwable) at the WARN level with an
   * accompanying message. 
   * 
   * @param msg the message accompanying the exception
   * @param t the exception (throwable) to log 
   */
  public void warn(String msg, Throwable t);
  

  /**
   * Similar to {@link #isWarnEnabled()} method except that the marker
   * data is also taken into consideration.
   *
   * @param marker The marker data to take into consideration
   */
  public boolean isWarnEnabled(Marker marker);
 
  /**
   * Log a message with the specific Marker at the WARN level.
   * 
   * @param marker The marker specific to this log statement
   * @param msg the message string to be logged
   */
  public void warn(Marker marker, String msg); 
  
  /**
   * This method is similar to {@link #warn(String, Object)} method except that the 
   * marker data is also taken into consideration.
   * 
   * @param marker the marker data specific to this log statement
   * @param format the format string
   * @param arg the argument
   */
  public void warn(Marker marker, String format, Object arg);
  
  /**
   * This method is similar to {@link #warn(String, Object, Object)}
   * method except that the marker data is also taken into
   * consideration.
   * 
   * @param marker the marker data specific to this log statement
   * @param format  the format string
   * @param arg1  the first argument
   * @param arg2  the second argument
   */
  public void warn(Marker marker, String format, Object arg1, Object arg2);  
  
  /**
   * This method is similar to {@link #warn(String, Object[])}
   * method except that the marker data is also taken into
   * consideration.
   *
   * @param marker the marker data specific to this log statement
   * @param format  the format string
   * @param argArray an array of arguments
   */
  public void warn(Marker marker, String format, Object[] argArray);

  
  /**
   * This method is similar to {@link #warn(String, Throwable)} method
   * except that the marker data is also taken into consideration.
   * 
   * @param marker the marker data for this log statement
   * @param msg the message accompanying the exception
   * @param t the exception (throwable) to log
   */ 
  public void warn(Marker marker, String msg, Throwable t); 
  

  /**
   * Is the logger instance enabled for the ERROR level?
   * @return True if this Logger is enabled for the ERROR level,
   * false otherwise.
   */
  public boolean isErrorEnabled();
  
  /**
   * Log a message at the ERROR level.
   *
   * @param msg the message string to be logged
   */
  public void error(String msg);
  
 /**
   * Log a message at the ERROR level according to the specified format
   * and argument.
   * 
   * <p>This form avoids superfluous object creation when the logger
   * is disabled for the ERROR level. </p>
   *
   * @param format the format string 
   * @param arg  the argument
   */
  public void error(String format, Object arg);

  /**
   * Log a message at the ERROR level according to the specified format
   * and arguments.
   * 
   * <p>This form avoids superfluous object creation when the logger
   * is disabled for the ERROR level. </p>
   *
   * @param format the format string
   * @param arg1  the first argument
   * @param arg2  the second argument
   */
  public void error(String format, Object arg1, Object arg2);

  /**
   * Log a message at the ERROR level according to the specified format
   * and arguments.
   * 
   * <p>This form avoids superfluous object creation when the logger
   * is disabled for the ERROR level. </p>
   *
   * @param format the format string
   * @param argArray an array of arguments
   */
  public void error(String format, Object[] argArray);
  
  /**
   * Log an exception (throwable) at the ERROR level with an
   * accompanying message. 
   * 
   * @param msg the message accompanying the exception
   * @param t the exception (throwable) to log
   */
  public void error(String msg, Throwable t);


  /**
   * Similar to {@link #isErrorEnabled()} method except that the
   * marker data is also taken into consideration.
   *
   * @param marker The marker data to take into consideration
   */
  public boolean isErrorEnabled(Marker marker);
  
  /**
   * Log a message with the specific Marker at the ERROR level.
   * 
   * @param marker The marker specific to this log statement
   * @param msg the message string to be logged
   */
  public void error(Marker marker, String msg); 
  
  /**
   * This method is similar to {@link #error(String, Object)} method except that the 
   * marker data is also taken into consideration.
   * 
   * @param marker the marker data specific to this log statement
   * @param format the format string
   * @param arg the argument
   */
  public void error(Marker marker, String format, Object arg);
  
  /**
   * This method is similar to {@link #error(String, Object, Object)}
   * method except that the marker data is also taken into
   * consideration.
   * 
   * @param marker the marker data specific to this log statement
   * @param format  the format string
   * @param arg1  the first argument
   * @param arg2  the second argument
   */
  public void error(Marker marker, String format, Object arg1, Object arg2);  
  
  /**
   * This method is similar to {@link #error(String, Object[])}
   * method except that the marker data is also taken into
   * consideration.
   *
   * @param marker the marker data specific to this log statement
   * @param format  the format string
   * @param argArray an array of arguments
   */
  public void error(Marker marker, String format, Object[] argArray);

  
  /**
   * This method is similar to {@link #error(String, Throwable)}
   * method except that the marker data is also taken into
   * consideration.
   * 
   * @param marker the marker data specific to this log statement
   * @param msg the message accompanying the exception
   * @param t the exception (throwable) to log
   */ 
  public void error(Marker marker, String msg, Throwable t);

}
