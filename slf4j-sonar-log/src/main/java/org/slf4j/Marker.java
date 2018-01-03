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

import java.io.Serializable;
import java.util.Iterator;

/**
 * Markers are named objects used to enrich log statements. Conforming logging
 * system Implementations of SLF4J determine how information conveyed by markers
 * are used, if at all. In particular, many conforming logging systems ignore
 * marker data.
 * 
 * <p>
 * Markers can contain references to other markers, which in turn may contain 
 * references of their own.
 * 
 * @author Ceki G&uuml;lc&uuml;
 */
public interface Marker extends Serializable {

    /**
     * This constant represents any marker, including a null marker.
     */
    public final String ANY_MARKER = "*";

    /**
     * This constant represents any non-null marker.
     */
    public final String ANY_NON_NULL_MARKER = "+";

    /**
     * Get the name of this Marker.
     * 
     * @return name of marker
     */
    public String getName();

    /**
     * Add a reference to another Marker.
     * 
     * @param reference
     *                a reference to another marker
     * @throws IllegalArgumentException
     *                 if 'reference' is null
     */
    public void add(Marker reference);

    /**
     * Remove a marker reference.
     * 
     * @param reference
     *                the marker reference to remove
     * @return true if reference could be found and removed, false otherwise.
     */
    public boolean remove(Marker reference);

    /**
     * @deprecated Replaced by {@link #hasReferences()}.
     */
    public boolean hasChildren();

    /**
     * Does this marker have any references?
     * 
     * @return true if this marker has one or more references, false otherwise.
     */
    public boolean hasReferences();

    /**
     * Returns an Iterator which can be used to iterate over the references of this
     * marker. An empty iterator is returned when this marker has no references.
     * 
     * @return Iterator over the references of this marker
     */
    public Iterator<Marker> iterator();

    /**
     * Does this marker contain a reference to the 'other' marker? Marker A is defined 
     * to contain marker B, if A == B or if B is referenced by A, or if B is referenced
     * by any one of A's references (recursively).
     * 
     * @param other
     *                The marker to test for inclusion.
     * @throws IllegalArgumentException
     *                 if 'other' is null
     * @return Whether this marker contains the other marker.
     */
    public boolean contains(Marker other);

    /**
     * Does this marker contain the marker named 'name'?
     * 
     * If 'name' is null the returned value is always false.
     * 
     * @param name The marker name to test for inclusion.
     * @return Whether this marker contains the other marker.
     */
    public boolean contains(String name);

    /**
     * Markers are considered equal if they have the same name.
     *
     * @param o
     * @return true, if this.name equals o.name
     *
     * @since 1.5.1
     */
    public boolean equals(Object o);

    /**
     * Compute the hash code based on the name of this marker.
     * Note that markers are considered equal if they have the same name.
     * 
     * @return the computed hashCode
     * @since 1.5.1
     */
    public int hashCode();

}
