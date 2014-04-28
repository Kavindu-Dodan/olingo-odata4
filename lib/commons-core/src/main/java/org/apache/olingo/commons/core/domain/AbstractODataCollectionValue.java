/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.olingo.commons.core.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.apache.olingo.commons.api.domain.AbstractODataValue;
import org.apache.olingo.commons.api.domain.ODataCollectionValue;
import org.apache.olingo.commons.api.domain.ODataValue;

/**
 * OData collection property value.
 *
 * @param <OV> The actual ODataValue interface.
 */
public abstract class AbstractODataCollectionValue<OV extends ODataValue>
        extends AbstractODataValue implements ODataCollectionValue<OV> {

  private static final long serialVersionUID = -3665659846001987187L;

  /**
   * Values.
   */
  protected final List<OV> values = new ArrayList<OV>();

  /**
   * Constructor.
   *
   * @param typeName type name.
   */
  public AbstractODataCollectionValue(final String typeName) {
    super(typeName);
  }
  
  protected abstract ODataCollectionValue<OV> getThis();

  /**
   * Adds a value to the collection.
   *
   * @param value value to be added.
   */
  @Override
  @SuppressWarnings("unchecked")
  public ODataCollectionValue<OV> add(final ODataValue value) {
    values.add((OV) value);
    return getThis();
  }

  /**
   * Value iterator.
   *
   * @return value iterator.
   */
  @Override
  public Iterator<OV> iterator() {
    return values.iterator();
  }

  /**
   * Gets collection size.
   *
   * @return collection size.
   */
  @Override
  public int size() {
    return values.size();
  }

  /**
   * Checks if collection is empty.
   *
   * @return 'TRUE' if empty; 'FALSE' otherwise.
   */
  @Override
  public boolean isEmpty() {
    return values.isEmpty();
  }

  @Override
  public Collection<Object> asJavaCollection() {
    final List<Object> result = new ArrayList<Object>();
    for (OV value : values) {
      if (value.isPrimitive()) {
        result.add(value.asPrimitive().toValue());
      } else if (value.isComplex()) {
        result.add(value.asComplex().asJavaMap());
      } else if (value.isCollection()) {
        result.add(value.asCollection().asJavaCollection());
      }
    }

    return result;
  }

}