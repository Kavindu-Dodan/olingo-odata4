/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.olingo.server.core.serializer.json;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.olingo.commons.api.data.ComplexIterator;
import org.apache.olingo.commons.api.data.ComplexValue;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.ContextURL.Suffix;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.EntityIterator;
import org.apache.olingo.commons.api.data.Operation;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.PropertyIterator;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.EdmComplexType;
import org.apache.olingo.commons.api.edm.EdmEntityContainer;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmPrimitiveType;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.edm.EdmProperty;
import org.apache.olingo.commons.api.edm.geo.Geospatial.Dimension;
import org.apache.olingo.commons.api.edm.geo.GeospatialCollection;
import org.apache.olingo.commons.api.edm.geo.LineString;
import org.apache.olingo.commons.api.edm.geo.MultiLineString;
import org.apache.olingo.commons.api.edm.geo.MultiPoint;
import org.apache.olingo.commons.api.edm.geo.MultiPolygon;
import org.apache.olingo.commons.api.edm.geo.Point;
import org.apache.olingo.commons.api.edm.geo.Polygon;
import org.apache.olingo.commons.api.edm.geo.SRID;
import org.apache.olingo.commons.api.edmx.EdmxReference;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataContent;
import org.apache.olingo.server.api.ODataContentWriteErrorCallback;
import org.apache.olingo.server.api.ODataContentWriteErrorContext;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.serializer.ComplexSerializerOptions;
import org.apache.olingo.server.api.serializer.EntityCollectionSerializerOptions;
import org.apache.olingo.server.api.serializer.EntitySerializerOptions;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.PrimitiveSerializerOptions;
import org.apache.olingo.server.api.serializer.ReferenceCollectionSerializerOptions;
import org.apache.olingo.server.api.serializer.ReferenceSerializerOptions;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.serializer.SerializerStreamResult;
import org.apache.olingo.server.api.uri.UriHelper;
import org.apache.olingo.server.api.uri.queryoption.CountOption;
import org.apache.olingo.server.api.uri.queryoption.ExpandItem;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.api.uri.queryoption.LevelsExpandOption;
import org.apache.olingo.server.api.uri.queryoption.SelectItem;
import org.apache.olingo.server.api.uri.queryoption.SelectOption;
import org.apache.olingo.server.core.serializer.ExpandSelectMock;
import org.apache.olingo.server.tecsvc.MetadataETagSupport;
import org.apache.olingo.server.tecsvc.data.DataProvider;
import org.apache.olingo.server.tecsvc.provider.EdmTechProvider;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class ODataJsonSerializerTest {
  private static final OData odata = OData.newInstance();
  private static final ServiceMetadata metadata = odata.createServiceMetadata(
      new EdmTechProvider(), Collections.<EdmxReference> emptyList(), new MetadataETagSupport("W/\"metadataETag\""));
  private static final EdmEntityContainer entityContainer = metadata.getEdm().getEntityContainer();
  private final DataProvider data = new DataProvider(odata, metadata.getEdm());
  private final ODataSerializer serializer = new ODataJsonSerializer(ContentType.JSON);
  private final ODataSerializer serializerNoMetadata = new ODataJsonSerializer(ContentType.JSON_NO_METADATA);
  private final ODataSerializer serializerFullMetadata = new ODataJsonSerializer(ContentType.JSON_FULL_METADATA);
  private final ODataSerializer serializerIEEECompatible =
      new ODataJsonSerializer(ContentType.create(ContentType.JSON, ContentType.PARAMETER_IEEE754_COMPATIBLE, "true"));
  private final UriHelper helper = odata.createUriHelper();

  @Test
  public void entitySimple() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(0);
    InputStream result = serializer.entity(metadata, edmEntitySet.getEntityType(), entity,
        EntitySerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).suffix(Suffix.ENTITY).build())
            .build()).getContent();
    final String resultString = IOUtils.toString(result);
    final String expectedResult = "{"
        + "\"@odata.context\":\"$metadata#ESAllPrim/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"PropertyInt16\":32767,"
        + "\"PropertyString\":\"First Resource - positive values\","
        + "\"PropertyBoolean\":true,"
        + "\"PropertyByte\":255,"
        + "\"PropertySByte\":127,"
        + "\"PropertyInt32\":2147483647,"
        + "\"PropertyInt64\":9223372036854775807,"
        + "\"PropertySingle\":1.79E20,"
        + "\"PropertyDouble\":-1.79E19,"
        + "\"PropertyDecimal\":34,"
        + "\"PropertyBinary\":\"ASNFZ4mrze8=\","
        + "\"PropertyDate\":\"2012-12-03\","
        + "\"PropertyDateTimeOffset\":\"2012-12-03T07:16:23Z\","
        + "\"PropertyDuration\":\"PT6S\","
        + "\"PropertyGuid\":\"01234567-89ab-cdef-0123-456789abcdef\","
        + "\"PropertyTimeOfDay\":\"03:26:05\""
        + "}";
    Assert.assertEquals(expectedResult, resultString);
  }
  
  @Test
  public void entitySimpleMetadataFull() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(0);
    InputStream result = serializerFullMetadata.entity(metadata, edmEntitySet.getEntityType(), entity,
        EntitySerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).suffix(Suffix.ENTITY).build())
            .build()).getContent();
    final String resultString = IOUtils.toString(result);
    final String expected = "{\"@odata.context\":\"$metadata#ESAllPrim/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"@odata.type\":\"#olingo.odata.test1.ETAllPrim\","
        + "\"@odata.id\":\"ESAllPrim(32767)\","
        + "\"PropertyInt16@odata.type\":\"#Int16\","
        + "\"PropertyInt16\":32767,"
        + "\"PropertyString\":\"First Resource - positive values\","
        + "\"PropertyBoolean\":true,"
        + "\"PropertyByte@odata.type\":\"#Byte\","
        + "\"PropertyByte\":255,"
        + "\"PropertySByte@odata.type\":\"#SByte\","
        + "\"PropertySByte\":127,"
        + "\"PropertyInt32@odata.type\":\"#Int32\","
        + "\"PropertyInt32\":2147483647,"
        + "\"PropertyInt64@odata.type\":\"#Int64\","
        + "\"PropertyInt64\":9223372036854775807,"
        + "\"PropertySingle@odata.type\":\"#Single\","
        + "\"PropertySingle\":1.79E20,"
        + "\"PropertyDouble\":-1.79E19,"
        + "\"PropertyDecimal@odata.type\":\"#Decimal\","
        + "\"PropertyDecimal\":34,"
        + "\"PropertyBinary@odata.type\":\"#Binary\","
        + "\"PropertyBinary\":\"ASNFZ4mrze8=\","
        + "\"PropertyDate@odata.type\":\"#Date\","
        + "\"PropertyDate\":\"2012-12-03\","
        + "\"PropertyDateTimeOffset@odata.type\":\"#DateTimeOffset\","
        + "\"PropertyDateTimeOffset\":\"2012-12-03T07:16:23Z\","
        + "\"PropertyDuration@odata.type\":\"#Duration\","
        + "\"PropertyDuration\":\"PT6S\","
        + "\"PropertyGuid@odata.type\":\"#Guid\","
        + "\"PropertyGuid\":\"01234567-89ab-cdef-0123-456789abcdef\","
        + "\"PropertyTimeOfDay@odata.type\":\"#TimeOfDay\","
        + "\"PropertyTimeOfDay\":\"03:26:05\","
        + "\"NavPropertyETTwoPrimOne@odata.navigationLink\":\"ESTwoPrim(32767)\","
        + "\"NavPropertyETTwoPrimMany@odata.navigationLink\":\"ESAllPrim(32767)/NavPropertyETTwoPrimMany\","
        + "\"#olingo.odata.test1.BAETAllPrimRT\":{"
        +   "\"title\":\"olingo.odata.test1.BAETAllPrimRT\","
        +   "\"target\":\"ESAllPrim(32767)/olingo.odata.test1.BAETAllPrimRT\""
        + "}}";        

    Assert.assertEquals(expected, resultString);
  }
  
  @Test
  public void entitySetMetadataFull() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final EntityCollection entityCol = data.readAll(edmEntitySet);
    InputStream result = serializerFullMetadata.entityCollection(metadata, edmEntitySet.getEntityType(), entityCol,
        EntityCollectionSerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).build())
            .build()).getContent();
    final String resultString = IOUtils.toString(result);
    final String expected = "{" + 
        "\"@odata.context\":\"$metadata#ESAllPrim\"," + 
        "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\"," + 
        "\"#olingo.odata.test1.BAESAllPrimRTETAllPrim\":{" + 
          "\"title\":\"olingo.odata.test1.BAESAllPrimRTETAllPrim\"," + 
          "\"target\":\"ESAllPrim/olingo.odata.test1.BAESAllPrimRTETAllPrim\"" + 
        "}," + 
        "\"#olingo.odata.test1.BAESAllPrimRT\":{" + 
          "\"title\":\"olingo.odata.test1.BAESAllPrimRT\"," + 
          "\"target\":\"ESAllPrim/olingo.odata.test1.BAESAllPrimRT\"" + 
        "}," + 
        "\"#olingo.odata.test1.BFNESAllPrimRTCTAllPrim\":{" + 
          "\"title\":\"olingo.odata.test1.BFNESAllPrimRTCTAllPrim\"," + 
          "\"target\":\"ESAllPrim/olingo.odata.test1.BFNESAllPrimRTCTAllPrim\"" + 
        "}," + 
        "\"value\":[" + 
          "{" + 
            "\"@odata.type\":\"#olingo.odata.test1.ETAllPrim\"," + 
            "\"@odata.id\":\"ESAllPrim(32767)\"," + 
            "\"PropertyInt16@odata.type\":\"#Int16\"," + 
            "\"PropertyInt16\":32767," + 
            "\"PropertyString\":\"First Resource - positive values\"," + 
            "\"PropertyBoolean\":true," + 
            "\"PropertyByte@odata.type\":\"#Byte\"," + 
            "\"PropertyByte\":255," + 
            "\"PropertySByte@odata.type\":\"#SByte\"," + 
            "\"PropertySByte\":127," + 
            "\"PropertyInt32@odata.type\":\"#Int32\"," + 
            "\"PropertyInt32\":2147483647," + 
            "\"PropertyInt64@odata.type\":\"#Int64\"," + 
            "\"PropertyInt64\":9223372036854775807," + 
            "\"PropertySingle@odata.type\":\"#Single\"," + 
            "\"PropertySingle\":1.79E20," + 
            "\"PropertyDouble\":-1.79E19," + 
            "\"PropertyDecimal@odata.type\":\"#Decimal\"," + 
            "\"PropertyDecimal\":34," + 
            "\"PropertyBinary@odata.type\":\"#Binary\"," + 
            "\"PropertyBinary\":\"ASNFZ4mrze8=\"," + 
            "\"PropertyDate@odata.type\":\"#Date\"," + 
            "\"PropertyDate\":\"2012-12-03\"," + 
            "\"PropertyDateTimeOffset@odata.type\":\"#DateTimeOffset\"," + 
            "\"PropertyDateTimeOffset\":\"2012-12-03T07:16:23Z\"," + 
            "\"PropertyDuration@odata.type\":\"#Duration\"," + 
            "\"PropertyDuration\":\"PT6S\"," + 
            "\"PropertyGuid@odata.type\":\"#Guid\"," + 
            "\"PropertyGuid\":\"01234567-89ab-cdef-0123-456789abcdef\"," + 
            "\"PropertyTimeOfDay@odata.type\":\"#TimeOfDay\"," + 
            "\"PropertyTimeOfDay\":\"03:26:05\"," + 
            "\"NavPropertyETTwoPrimOne@odata.navigationLink\":\"ESTwoPrim(32767)\"," + 
            "\"NavPropertyETTwoPrimMany@odata.navigationLink\":\"ESAllPrim(32767)/NavPropertyETTwoPrimMany\"," + 
        "\"#olingo.odata.test1.BAETAllPrimRT\":{" + 
          "\"title\":\"olingo.odata.test1.BAETAllPrimRT\"," + 
          "\"target\":\"ESAllPrim(32767)/olingo.odata.test1.BAETAllPrimRT\"" + 
        "}},";

    Assert.assertTrue(resultString.startsWith(expected));
  }  
  
  @Test
  public void entityAllPrimAllNull() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    Entity entity = data.readAll(edmEntitySet).getEntities().get(0);
    entity.getProperties().retainAll(Collections.singletonList(entity.getProperties().get(0)));
    final String resultString = IOUtils.toString(serializer.entity(metadata, edmEntitySet.getEntityType(),
        entity,
        EntitySerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).suffix(Suffix.ENTITY).build())
            .build()).getContent());
    final String expectedResult = "{\"@odata.context\":\"$metadata#ESAllPrim/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"PropertyInt16\":32767,"
        + "\"PropertyString\":null,\"PropertyBoolean\":null,"
        + "\"PropertyByte\":null,\"PropertySByte\":null,"
        + "\"PropertyInt32\":null,\"PropertyInt64\":null,"
        + "\"PropertySingle\":null,\"PropertyDouble\":null,\"PropertyDecimal\":null,"
        + "\"PropertyBinary\":null,"
        + "\"PropertyDate\":null,\"PropertyDateTimeOffset\":null,\"PropertyDuration\":null,"
        + "\"PropertyGuid\":null,\"PropertyTimeOfDay\":null}";
    Assert.assertEquals(expectedResult, resultString);
  }

  @Test(expected = SerializerException.class)
  public void entityAllPrimKeyNull() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    Entity entity = data.readAll(edmEntitySet).getEntities().get(0);
    entity.getProperties().clear();
    serializer.entity(metadata, edmEntitySet.getEntityType(), entity,
        EntitySerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).suffix(Suffix.ENTITY).build())
            .build());
  }

  @Test
  public void entityWrongData() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    Entity entity = data.readAll(edmEntitySet).getEntities().get(0);
    entity.getProperties().get(0).setValue(ValueType.PRIMITIVE, false);
    try {
      serializer.entity(metadata, edmEntitySet.getEntityType(), entity,
          EntitySerializerOptions.with()
              .contextURL(ContextURL.with().entitySet(edmEntitySet).suffix(Suffix.ENTITY).build())
              .build());
      Assert.fail("Expected exception not thrown!");
    } catch (final SerializerException e) {
      Assert.assertEquals(SerializerException.MessageKeys.WRONG_PROPERTY_VALUE, e.getMessageKey());
      final String message = e.getLocalizedMessage();
      Assert.assertThat(message, CoreMatchers.containsString("PropertyInt16"));
      Assert.assertThat(message, CoreMatchers.containsString("false"));
    }
  }

  @Test
  public void entitySetCompAllPrim() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESCompAllPrim");
    EntityCollection entitySet = data.readAll(edmEntitySet);
    entitySet.setCount(entitySet.getEntities().size());
    entitySet.setNext(URI.create("/next"));
    CountOption countOption = Mockito.mock(CountOption.class);
    Mockito.when(countOption.getValue()).thenReturn(true);
    InputStream result = serializer.entityCollection(metadata, edmEntitySet.getEntityType(), entitySet,
        EntityCollectionSerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).build())
            .count(countOption)
            .build()).getContent();
    final String resultString = IOUtils.toString(result);

    Assert.assertThat(resultString, CoreMatchers.startsWith("{"
        + "\"@odata.context\":\"$metadata#ESCompAllPrim\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"@odata.count\":4,\"value\":["
        + "{\"@odata.etag\":\"W/\\\"32767\\\"\","));
    Assert.assertThat(resultString, CoreMatchers.endsWith("}}],"
        + "\"@odata.nextLink\":\"/next\"}"));

    int count = 0;
    int index = -1;
    while ((index = resultString.indexOf("PropertyInt16\":", ++index)) > 0) {
      count++;
    }
    Assert.assertEquals(8, count);
  }

  @Test
  public void entityCollectionStreamed() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final EntityIterator entityIterator = new EntityIterator() {
      EntityCollection entityCollection = data.readAll(edmEntitySet);
      Iterator<Entity> innerIterator = entityCollection.iterator();
      
      @Override
      public List<Operation> getOperations() {
        return entityCollection.getOperations();
      } 
      
      @Override
      public boolean hasNext() {
        return innerIterator.hasNext();
      }
      @Override
      public Entity next() {
        return innerIterator.next();
      }
    };
    CountOption countOption = Mockito.mock(CountOption.class);
    Mockito.when(countOption.getValue()).thenReturn(true);

    ODataContent result = serializer.entityCollectionStreamed(
        metadata, edmEntitySet.getEntityType(), entityIterator,
        EntityCollectionSerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).build())
            .build()).getODataContent();
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    result.write(bout);
    final String resultString = new String(bout.toByteArray(), "UTF-8");

    Assert.assertThat(resultString, CoreMatchers.startsWith("{"
        + "\"@odata.context\":\"$metadata#ESAllPrim\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"value\":[{\"PropertyInt16\":32767,\"PropertyString\""));
    Assert.assertThat(resultString, CoreMatchers.endsWith(
        "\"PropertyTimeOfDay\":\"00:01:01\"}]}"));

    int count = 0;
    int index = -1;
    while ((index = resultString.indexOf("PropertyInt16\":", ++index)) > 0) {
      count++;
    }
    Assert.assertEquals(3, count);
  }

  @Test
  public void entityCollectionStreamedWithError() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final EntityIterator entityIterator = new EntityIterator() {
      EntityCollection entityCollection = data.readAll(edmEntitySet);
      Iterator<Entity> innerIterator = entityCollection.iterator();
      
      @Override
      public List<Operation> getOperations() {
        return entityCollection.getOperations();
      } 
      
      @Override
      public boolean hasNext() {
        return innerIterator.hasNext();
      }
      @Override
      public Entity next() {
        Entity e =  new Entity();
        e.setId(URI.create("id"));
        return e;
      }
    };
    CountOption countOption = Mockito.mock(CountOption.class);
    Mockito.when(countOption.getValue()).thenReturn(true);

    ODataContentWriteErrorCallback errorCallback = new ODataContentWriteErrorCallback() {
      @Override
      public void handleError(ODataContentWriteErrorContext context, WritableByteChannel channel) {
        try {
          Exception ex = context.getException();
          Assert.assertEquals(ex, context.getODataLibraryException());
          String msgKey = context.getODataLibraryException().getMessageKey().getKey();
          String toChannel = "ERROR: " + msgKey;
          channel.write(ByteBuffer.wrap(toChannel.getBytes("UTF-8")));
        } catch (IOException e) {
          throw new RuntimeException("Error in error.");
        }
      }
    };

    ODataContent result = serializer.entityCollectionStreamed(
        metadata, edmEntitySet.getEntityType(), entityIterator,
        EntityCollectionSerializerOptions.with()
            .writeContentErrorCallback(errorCallback)
            .contextURL(ContextURL.with().entitySet(edmEntitySet).build())
            .build()).getODataContent();
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    result.write(bout);
    final String resultString = new String(bout.toByteArray(), "UTF-8");
    Assert.assertEquals("ERROR: MISSING_PROPERTY", resultString);
  }


  @Test
  public void entityCollAllPrim() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESCollAllPrim");
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(0);
    InputStream result = serializer.entity(metadata, edmEntitySet.getEntityType(), entity,
        EntitySerializerOptions.with()
            .contextURL(ContextURL.with().serviceRoot(URI.create("http://host/service/"))
                .entitySet(edmEntitySet).suffix(Suffix.ENTITY).build())
            .build()).getContent();
    final String resultString = IOUtils.toString(result);
    final String expectedResult = "{"
        + "\"@odata.context\":\"http://host/service/$metadata#ESCollAllPrim/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"PropertyInt16\":1,"
        + "\"CollPropertyString\":"
        + "[\"Employee1@company.example\",\"Employee2@company.example\",\"Employee3@company.example\"],"
        + "\"CollPropertyBoolean\":[true,false,true],"
        + "\"CollPropertyByte\":[50,200,249],"
        + "\"CollPropertySByte\":[-120,120,126],"
        + "\"CollPropertyInt16\":[1000,2000,30112],"
        + "\"CollPropertyInt32\":[23232323,11223355,10000001],"
        + "\"CollPropertyInt64\":[929292929292,333333333333,444444444444],"
        + "\"CollPropertySingle\":[1790.0,26600.0,3210.0],"
        + "\"CollPropertyDouble\":[-17900.0,-2.78E7,3210.0],"
        + "\"CollPropertyDecimal\":[12,-2,1234],"
        + "\"CollPropertyBinary\":[\"q83v\",\"ASNF\",\"VGeJ\"],"
        + "\"CollPropertyDate\":[\"1958-12-03\",\"1999-08-05\",\"2013-06-25\"],"
        + "\"CollPropertyDateTimeOffset\":[\"2015-08-12T03:08:34Z\",\"1970-03-28T12:11:10Z\","
        + "\"1948-02-17T09:09:09Z\"],"
        + "\"CollPropertyDuration\":[\"PT13S\",\"PT5H28M0S\",\"PT1H0S\"],"
        + "\"CollPropertyGuid\":[\"ffffff67-89ab-cdef-0123-456789aaaaaa\",\"eeeeee67-89ab-cdef-0123-456789bbbbbb\","
        + "\"cccccc67-89ab-cdef-0123-456789cccccc\"],"
        + "\"CollPropertyTimeOfDay\":[\"04:14:13\",\"23:59:59\",\"01:12:33\"]"
        + "}";
    Assert.assertEquals(expectedResult, resultString);
  }

  @Test
  public void entityCompAllPrim() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESCompAllPrim");
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(0);
    InputStream result = serializer.entity(metadata, edmEntitySet.getEntityType(), entity,
        EntitySerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).suffix(Suffix.ENTITY).build())
            .build()).getContent();
    final String resultString = IOUtils.toString(result);
    final String expectedResult = "{"
        + "\"@odata.context\":\"$metadata#ESCompAllPrim/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"@odata.etag\":\"W/\\\"32767\\\"\","
        + "\"PropertyInt16\":32767,"
        + "\"PropertyComp\":{"
        + "\"PropertyString\":\"First Resource - first\","
        + "\"PropertyBinary\":\"ASNFZ4mrze8=\","
        + "\"PropertyBoolean\":true,"
        + "\"PropertyByte\":255,"
        + "\"PropertyDate\":\"2012-10-03\","
        + "\"PropertyDateTimeOffset\":\"2012-10-03T07:16:23.1234567Z\","
        + "\"PropertyDecimal\":34.27,"
        + "\"PropertySingle\":1.79E20,"
        + "\"PropertyDouble\":-1.79E19,"
        + "\"PropertyDuration\":\"PT6S\","
        + "\"PropertyGuid\":\"01234567-89ab-cdef-0123-456789abcdef\","
        + "\"PropertyInt16\":32767,"
        + "\"PropertyInt32\":2147483647,"
        + "\"PropertyInt64\":9223372036854775807,"
        + "\"PropertySByte\":127,"
        + "\"PropertyTimeOfDay\":\"01:00:01\""
        + "}}";
    Assert.assertEquals(expectedResult, resultString);
  }

  @Test
  public void entityMixPrimCollComp() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESMixPrimCollComp");
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(0);
    InputStream result = serializer.entity(metadata, edmEntitySet.getEntityType(), entity,
        EntitySerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).suffix(Suffix.ENTITY).build())
            .build()).getContent();
    final String resultString = IOUtils.toString(result);
    final String expectedResult = "{"
        + "\"@odata.context\":\"$metadata#ESMixPrimCollComp/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"PropertyInt16\":32767,"
        + "\"CollPropertyString\":"
        + "[\"Employee1@company.example\",\"Employee2@company.example\",\"Employee3@company.example\"],"
        + "\"PropertyComp\":{\"PropertyInt16\":111,\"PropertyString\":\"TEST A\"},"
        + "\"CollPropertyComp\":["
        + "{\"PropertyInt16\":123,\"PropertyString\":\"TEST 1\"},"
        + "{\"PropertyInt16\":456,\"PropertyString\":\"TEST 2\"},"
        + "{\"PropertyInt16\":789,\"PropertyString\":\"TEST 3\"}]}";
    Assert.assertEquals(expectedResult, resultString);
  }

  @Test
  public void entityMixPrimCollCompAllNull() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESMixPrimCollComp");
    Entity entity = data.readAll(edmEntitySet).getEntities().get(0);
    entity.getProperties().retainAll(Collections.singletonList(entity.getProperties().get(0)));
    final String resultString = IOUtils.toString(serializer.entity(metadata, edmEntitySet.getEntityType(), entity,
        EntitySerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).suffix(Suffix.ENTITY).build())
            .build()).getContent());
    final String expectedResult = "{\"@odata.context\":\"$metadata#ESMixPrimCollComp/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"PropertyInt16\":32767,"
        + "\"CollPropertyString\":[],\"PropertyComp\":null,\"CollPropertyComp\":[]}";
    Assert.assertEquals(expectedResult, resultString);
  }

  @Test
  public void nullCollectionButInDataMap() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESMixEnumDefCollComp");
    Entity entity = new Entity();
    entity.setId(URI.create("id"));
    entity.addProperty(new Property(null, "PropertyEnumString", ValueType.ENUM, 6));
    entity.addProperty(new Property(null, "CollPropertyEnumString", ValueType.COLLECTION_ENUM, null));
    entity.addProperty(new Property(null, "PropertyDefString", ValueType.PRIMITIVE, "Test"));
    entity.addProperty(new Property(null, "CollPropertyDefString", ValueType.COLLECTION_PRIMITIVE, null));
    ComplexValue complexValue = new ComplexValue();
    complexValue.getValue().add(entity.getProperty("PropertyEnumString"));
    complexValue.getValue().add(entity.getProperty("CollPropertyEnumString"));
    complexValue.getValue().add(entity.getProperty("PropertyDefString"));
    complexValue.getValue().add(entity.getProperty("CollPropertyDefString"));
    entity.addProperty(new Property(null, "PropertyCompMixedEnumDef", ValueType.COMPLEX, complexValue));
    entity.addProperty(new Property(null, "CollPropertyCompMixedEnumDef", ValueType.COLLECTION_COMPLEX, null));
    final String resultString = IOUtils.toString(serializer.entity(metadata, edmEntitySet.getEntityType(), entity,
        EntitySerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).suffix(Suffix.ENTITY).build())
            .build()).getContent());
    Assert.assertEquals("{\"@odata.context\":\"$metadata#ESMixEnumDefCollComp/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"PropertyEnumString\":\"String2,String3\","
        + "\"CollPropertyEnumString\":[],"
        + "\"PropertyDefString\":\"Test\","
        + "\"CollPropertyDefString\":[],"
        + "\"PropertyCompMixedEnumDef\":{\"PropertyEnumString\":\"String2,String3\","
        + "\"CollPropertyEnumString\":[],"
        + "\"PropertyDefString\":\"Test\",\"CollPropertyDefString\":[]},"
        + "\"CollPropertyCompMixedEnumDef\":[]}",
        resultString);
  }
  
  @Test
  public void nullComplexValueButInDataMapAndNullCollectionsNotInDataMap() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESMixEnumDefCollComp");
    Entity entity = new Entity();
    entity.setId(URI.create("id"));
    entity.addProperty(new Property(null, "PropertyEnumString", ValueType.ENUM, 6));
    entity.addProperty(new Property(null, "PropertyDefString", ValueType.PRIMITIVE, "Test"));
    entity.addProperty(new Property(null, "PropertyCompMixedEnumDef", ValueType.COMPLEX, null));
    final String resultString = IOUtils.toString(serializer.entity(metadata, edmEntitySet.getEntityType(), entity,
        EntitySerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).suffix(Suffix.ENTITY).build())
            .build()).getContent());
    Assert.assertEquals("{\"@odata.context\":\"$metadata#ESMixEnumDefCollComp/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"PropertyEnumString\":\"String2,String3\","
        + "\"CollPropertyEnumString\":[],"
        + "\"PropertyDefString\":\"Test\","
        + "\"CollPropertyDefString\":[],"
        + "\"PropertyCompMixedEnumDef\":null,"
        + "\"CollPropertyCompMixedEnumDef\":[]}",
        resultString);
  }

  @Test
  public void enumAndTypeDefinition() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESMixEnumDefCollComp");
    Entity entity = new Entity();
    entity.setId(URI.create("id"));
    entity.addProperty(new Property(null, "PropertyEnumString", ValueType.ENUM, 6));
    entity.addProperty(new Property(null, "CollPropertyEnumString", ValueType.COLLECTION_ENUM,
        Arrays.asList(2, 4, 6)));
    entity.addProperty(new Property(null, "PropertyDefString", ValueType.PRIMITIVE, "Test"));
    entity.addProperty(new Property(null, "CollPropertyDefString", ValueType.COLLECTION_PRIMITIVE,
        Arrays.asList("Test1", "Test2")));
    ComplexValue complexValue = new ComplexValue();
    complexValue.getValue().add(entity.getProperty("PropertyEnumString"));
    complexValue.getValue().add(entity.getProperty("CollPropertyEnumString"));
    complexValue.getValue().add(entity.getProperty("PropertyDefString"));
    complexValue.getValue().add(entity.getProperty("CollPropertyDefString"));
    entity.addProperty(new Property(null, "PropertyCompMixedEnumDef", ValueType.COMPLEX, complexValue));
    entity.addProperty(new Property(null, "CollPropertyCompMixedEnumDef", ValueType.COLLECTION_COMPLEX,
        Collections.singletonList(complexValue)));
    final String resultString = IOUtils.toString(serializer.entity(metadata, edmEntitySet.getEntityType(), entity,
        EntitySerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).suffix(Suffix.ENTITY).build())
            .build()).getContent());
    Assert.assertEquals("{\"@odata.context\":\"$metadata#ESMixEnumDefCollComp/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"PropertyEnumString\":\"String2,String3\","
        + "\"CollPropertyEnumString\":[\"String2\",\"String3\",\"String2,String3\"],"
        + "\"PropertyDefString\":\"Test\","
        + "\"CollPropertyDefString\":[\"Test1\",\"Test2\"],"
        + "\"PropertyCompMixedEnumDef\":{\"PropertyEnumString\":\"String2,String3\","
        + "\"CollPropertyEnumString\":[\"String2\",\"String3\",\"String2,String3\"],"
        + "\"PropertyDefString\":\"Test\",\"CollPropertyDefString\":[\"Test1\",\"Test2\"]},"
        + "\"CollPropertyCompMixedEnumDef\":[{\"PropertyEnumString\":\"String2,String3\","
        + "\"CollPropertyEnumString\":[\"String2\",\"String3\",\"String2,String3\"],"
        + "\"PropertyDefString\":\"Test\",\"CollPropertyDefString\":[\"Test1\",\"Test2\"]}]}",
        resultString);
  }

  @Test
  public void entityTwoPrimNoMetadata() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESTwoPrim");
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(0);
    final String resultString = IOUtils.toString(serializerNoMetadata
        .entity(metadata, edmEntitySet.getEntityType(), entity, null).getContent());
    final String expectedResult = "{\"PropertyInt16\":32766,\"PropertyString\":\"Test String1\"}";
    Assert.assertEquals(expectedResult, resultString);
  }

  @Test
  public void entityTwoPrimWithMetadataMinimal() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESTwoPrim");
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(0);
    InputStream result = serializer
        .entity(metadata, edmEntitySet.getEntityType(), entity, EntitySerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).suffix(Suffix.ENTITY).build())
            .build()).getContent();
    final String resultString = IOUtils.toString(result);
    final String expectedResult = "{\"@odata.context\":\"$metadata#ESTwoPrim/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"PropertyInt16\":32766,\"PropertyString\":\"Test String1\"}";
        Assert.assertEquals(expectedResult, resultString);
  }

  @Test
  public void entitySetTwoPrimWithMetadataFull() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESTwoPrim");
    final EntityCollection entitySet = data.readAll(edmEntitySet);
    InputStream result = serializerFullMetadata
        .entityCollection(metadata, edmEntitySet.getEntityType(), entitySet,
            EntityCollectionSerializerOptions.with()
                .contextURL(ContextURL.with().entitySet(edmEntitySet).build())
                .build())
        .getContent();
    final String resultString = IOUtils.toString(result);
    final String expectedResult = "{\"@odata.context\":\"$metadata#ESTwoPrim\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"value\":[{\"@odata.type\":\"#olingo.odata.test1.ETTwoPrim\",\"@odata.id\":\"ESTwoPrim(32766)\","
        + "\"PropertyInt16@odata.type\":\"#Int16\",\"PropertyInt16\":32766,"
        + "\"PropertyString\":\"Test String1\","
        + "\"#olingo.odata.test1.BAETTwoPrimRTString\":"
        + "{\"title\":\"olingo.odata.test1.BAETTwoPrimRTString\","
        + "\"target\":\"ESTwoPrim(32766)/olingo.odata.test1.BAETTwoPrimRTString\"},"
        + "\"#olingo.odata.test1.BAETTwoPrimRTCollString\":"
        + "{\"title\":\"olingo.odata.test1.BAETTwoPrimRTCollString\","
        + "\"target\":\"ESTwoPrim(32766)/olingo.odata.test1.BAETTwoPrimRTCollString\"},"
        + "\"#olingo.odata.test1.BAETTwoPrimRTCTAllPrim\":"
        + "{\"title\":\"olingo.odata.test1.BAETTwoPrimRTCTAllPrim\","
        + "\"target\":\"ESTwoPrim(32766)/olingo.odata.test1.BAETTwoPrimRTCTAllPrim\"},"
        + "\"#olingo.odata.test1.BAETTwoPrimRTCollCTAllPrim\":"
        + "{\"title\":\"olingo.odata.test1.BAETTwoPrimRTCollCTAllPrim\","
        + "\"target\":\"ESTwoPrim(32766)/olingo.odata.test1.BAETTwoPrimRTCollCTAllPrim\"}"
        + "},"
        + "{\"@odata.type\":\"#olingo.odata.test1.ETTwoPrim\",\"@odata.id\":\"ESTwoPrim(-365)\","
        + "\"PropertyInt16@odata.type\":\"#Int16\",\"PropertyInt16\":-365,"
        + "\"PropertyString\":\"Test String2\","
        + "\"NavPropertyETAllPrimMany@odata.navigationLink\":\"ESTwoPrim(-365)/NavPropertyETAllPrimMany\","
        + "\"#olingo.odata.test1.BAETTwoPrimRTString\":"
        + "{\"title\":\"olingo.odata.test1.BAETTwoPrimRTString\","
        + "\"target\":\"ESTwoPrim(-365)/olingo.odata.test1.BAETTwoPrimRTString\"},"
        + "\"#olingo.odata.test1.BAETTwoPrimRTCollString\":"
        + "{\"title\":\"olingo.odata.test1.BAETTwoPrimRTCollString\","
        + "\"target\":\"ESTwoPrim(-365)/olingo.odata.test1.BAETTwoPrimRTCollString\"},"
        + "\"#olingo.odata.test1.BAETTwoPrimRTCTAllPrim\":"
        + "{\"title\":\"olingo.odata.test1.BAETTwoPrimRTCTAllPrim\","
        + "\"target\":\"ESTwoPrim(-365)/olingo.odata.test1.BAETTwoPrimRTCTAllPrim\"},"
        + "\"#olingo.odata.test1.BAETTwoPrimRTCollCTAllPrim\":"
        + "{\"title\":\"olingo.odata.test1.BAETTwoPrimRTCollCTAllPrim\","
        + "\"target\":\"ESTwoPrim(-365)/olingo.odata.test1.BAETTwoPrimRTCollCTAllPrim\"}},"
        + "{\"@odata.type\":\"#olingo.odata.test1.ETTwoPrim\",\"@odata.id\":\"ESTwoPrim(-32766)\","
        + "\"PropertyInt16@odata.type\":\"#Int16\",\"PropertyInt16\":-32766,"
        + "\"PropertyString\":null,"
        + "\"#olingo.odata.test1.BAETTwoPrimRTString\":"
        + "{\"title\":\"olingo.odata.test1.BAETTwoPrimRTString\","
        + "\"target\":\"ESTwoPrim(-32766)/olingo.odata.test1.BAETTwoPrimRTString\"},"
        + "\"#olingo.odata.test1.BAETTwoPrimRTCollString\":"
        + "{\"title\":\"olingo.odata.test1.BAETTwoPrimRTCollString\","
        + "\"target\":\"ESTwoPrim(-32766)/olingo.odata.test1.BAETTwoPrimRTCollString\"},"
        + "\"#olingo.odata.test1.BAETTwoPrimRTCTAllPrim\":"
        + "{\"title\":\"olingo.odata.test1.BAETTwoPrimRTCTAllPrim\","
        + "\"target\":\"ESTwoPrim(-32766)/olingo.odata.test1.BAETTwoPrimRTCTAllPrim\"},"
        + "\"#olingo.odata.test1.BAETTwoPrimRTCollCTAllPrim\":"
        + "{\"title\":\"olingo.odata.test1.BAETTwoPrimRTCollCTAllPrim\","
        + "\"target\":\"ESTwoPrim(-32766)/olingo.odata.test1.BAETTwoPrimRTCollCTAllPrim\"}},"
        + "{\"@odata.type\":\"#olingo.odata.test1.ETTwoPrim\",\"@odata.id\":\"ESTwoPrim(32767)\","
        + "\"PropertyInt16@odata.type\":\"#Int16\",\"PropertyInt16\":32767,"
        + "\"PropertyString\":\"Test String4\","
        + "\"NavPropertyETAllPrimOne@odata.navigationLink\":\"ESAllPrim(32767)\","
        + "\"#olingo.odata.test1.BAETTwoPrimRTString\":"
        + "{\"title\":\"olingo.odata.test1.BAETTwoPrimRTString\","
        + "\"target\":\"ESTwoPrim(32767)/olingo.odata.test1.BAETTwoPrimRTString\"},"
        + "\"#olingo.odata.test1.BAETTwoPrimRTCollString\":"
        + "{\"title\":\"olingo.odata.test1.BAETTwoPrimRTCollString\","
        + "\"target\":\"ESTwoPrim(32767)/olingo.odata.test1.BAETTwoPrimRTCollString\"},"
        + "\"#olingo.odata.test1.BAETTwoPrimRTCTAllPrim\":"
        + "{\"title\":\"olingo.odata.test1.BAETTwoPrimRTCTAllPrim\","
        + "\"target\":\"ESTwoPrim(32767)/olingo.odata.test1.BAETTwoPrimRTCTAllPrim\"},"
        + "\"#olingo.odata.test1.BAETTwoPrimRTCollCTAllPrim\":"
        + "{\"title\":\"olingo.odata.test1.BAETTwoPrimRTCollCTAllPrim\","
        + "\"target\":\"ESTwoPrim(32767)/olingo.odata.test1.BAETTwoPrimRTCollCTAllPrim\"}"       
        + "}]}";
    Assert.assertEquals(expectedResult, resultString);
  }
  
  @Test
  public void entityWithStreamMetadataMinimal() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESWithStream");
    final EntityCollection collection = data.readAll(edmEntitySet);
    InputStream result = serializer.entityCollection(metadata, edmEntitySet.getEntityType(), collection,
        EntityCollectionSerializerOptions.with()
        .contextURL(ContextURL.with().entitySet(edmEntitySet).build())
        .build()).getContent();
    final String resultString = IOUtils.toString(result);
    final String expectedResult = "{\"@odata.context\":\"$metadata#ESWithStream\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"value\":[{\"PropertyInt16\":32767},"
        + "{\"PropertyInt16\":7,\"PropertyStream@odata.mediaEtag\":\"eTag\","
        + "\"PropertyStream@odata.mediaContentType\":\"image/jpeg\"}]}";
    Assert.assertEquals(expectedResult, resultString);
  }
  
  @Test
  public void entityWithStreamMetadataNone() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESWithStream");
    final EntityCollection collection = data.readAll(edmEntitySet);
    InputStream result = serializerNoMetadata.entityCollection(metadata, edmEntitySet.getEntityType(), collection,
        EntityCollectionSerializerOptions.with()
        .contextURL(ContextURL.with().entitySet(edmEntitySet).build())
        .build()).getContent();
    final String resultString = IOUtils.toString(result);
    final String expectedResult = "{"
        + "\"value\":[{\"PropertyInt16\":32767},"
        + "{\"PropertyInt16\":7}]}";
    Assert.assertEquals(expectedResult, resultString);
  }  

  @Test
  public void entityWithStreamMetadataFull() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESWithStream");
    final EntityCollection collection = data.readAll(edmEntitySet);
    InputStream result = serializerFullMetadata.entityCollection(metadata, edmEntitySet.getEntityType(), collection,
        EntityCollectionSerializerOptions.with()
        .contextURL(ContextURL.with().entitySet(edmEntitySet).build())
        .build()).getContent();
    final String resultString = IOUtils.toString(result);
    final String expectedResult = "{\"@odata.context\":\"$metadata#ESWithStream\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"value\":[{"
        + "\"@odata.type\":\"#olingo.odata.test1.ETWithStream\","
        + "\"@odata.id\":\"ESWithStream(32767)\","
        + "\"PropertyInt16@odata.type\":\"#Int16\","
        + "\"PropertyInt16\":32767,"
        + "\"PropertyStream@odata.type\":\"#Stream\","
        + "\"PropertyStream@odata.mediaReadLink\":\"readLink\"},"
        + "{"
        + "\"@odata.type\":\"#olingo.odata.test1.ETWithStream\","
        + "\"@odata.id\":\"ESWithStream(7)\","
        + "\"PropertyInt16@odata.type\":\"#Int16\","
        + "\"PropertyInt16\":7,"
        + "\"PropertyStream@odata.type\":\"#Stream\","
        + "\"PropertyStream@odata.mediaEtag\":\"eTag\","
        + "\"PropertyStream@odata.mediaContentType\":\"image/jpeg\","
        + "\"PropertyStream@odata.mediaEditLink\":\"http://mediaserver:1234/editLink\""
        + "}]}";
    Assert.assertEquals(expectedResult, resultString);
  }  
  
  @Test
  public void entitySetTwoPrimNoMetadata() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESTwoPrim");
    final EntityCollection entitySet = data.readAll(edmEntitySet);
    final String resultString = IOUtils.toString(serializerNoMetadata
        .entityCollection(metadata, edmEntitySet.getEntityType(), entitySet,
            EntityCollectionSerializerOptions.with()
                .contextURL(ContextURL.with().entitySet(edmEntitySet).build())
                .build()).getContent());
    final String expectedResult = "{\"value\":["
        + "{\"PropertyInt16\":32766,\"PropertyString\":\"Test String1\"},"
        + "{\"PropertyInt16\":-365,\"PropertyString\":\"Test String2\"},"
        + "{\"PropertyInt16\":-32766,\"PropertyString\":null},"
        + "{\"PropertyInt16\":32767,\"PropertyString\":\"Test String4\"}]}";
    Assert.assertEquals(expectedResult, resultString);
  }

  @Test
  public void entityMedia() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESMedia");
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(0);
    final String resultString = IOUtils.toString(serializer.entity(metadata, edmEntitySet.getEntityType(),
        entity,
        EntitySerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).suffix(Suffix.ENTITY).build())
            .build()).getContent());
    final String expectedResult = "{\"@odata.context\":\"$metadata#ESMedia/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"@odata.mediaEtag\":\"W/\\\"1\\\"\","
        + "\"@odata.mediaContentType\":\"image/svg+xml\","
        + "\"@odata.mediaEditLink\":\"ESMedia(1)/$value\","
        + "\"PropertyInt16\":1}";
    Assert.assertEquals(expectedResult, resultString);
  }

  @Test
  public void entitySetMedia() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESMedia");
    final EntityCollection entitySet = data.readAll(edmEntitySet);
    final String resultString = IOUtils.toString(serializer.entityCollection(metadata,
        edmEntitySet.getEntityType(), entitySet,
        EntityCollectionSerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).build()).build()).getContent());
    final String expectedResult = "{\"@odata.context\":\"$metadata#ESMedia\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"value\":["
        + "{\"@odata.mediaEtag\":\"W/\\\"1\\\"\",\"@odata.mediaContentType\":\"image/svg+xml\","
        + "\"@odata.mediaEditLink\":\"ESMedia(1)/$value\",\"PropertyInt16\":1},"
        + "{\"@odata.mediaEtag\":\"W/\\\"2\\\"\",\"@odata.mediaContentType\":\"image/svg+xml\","
        + "\"@odata.mediaEditLink\":\"ESMedia(2)/$value\",\"PropertyInt16\":2},"
        + "{\"@odata.mediaEtag\":\"W/\\\"3\\\"\",\"@odata.mediaContentType\":\"image/svg+xml\","
        + "\"@odata.mediaEditLink\":\"ESMedia(3)/$value\",\"PropertyInt16\":3},"
        + "{\"@odata.mediaEtag\":\"W/\\\"4\\\"\",\"@odata.mediaContentType\":\"image/svg+xml\","
        + "\"@odata.mediaEditLink\":\"ESMedia(4)/$value\",\"PropertyInt16\":4}]}";
    Assert.assertEquals(expectedResult, resultString);
  }

  @Test
  public void entityMediaWithMetadataFull() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESMedia");
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(0);
    entity.setMediaETag("W/\\\"08D25949E3BFB7AB\\\"");
    InputStream result = serializerFullMetadata
        .entity(metadata, edmEntitySet.getEntityType(), entity,
            EntitySerializerOptions.with().contextURL(ContextURL.with()
                .entitySet(edmEntitySet).suffix(Suffix.ENTITY).build()).build())
        .getContent();
    final String resultString = IOUtils.toString(result);
    final String expectedResult = "{\"@odata.context\":\"$metadata#ESMedia/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"@odata.mediaEtag\":\"W/\\\\\\\"08D25949E3BFB7AB\\\\\\\"\",\"@odata.mediaContentType\":\"image/svg+xml\","
        + "\"@odata.mediaEditLink\":\"ESMedia(1)/$value\","
        + "\"@odata.type\":\"#olingo.odata.test1.ETMedia\",\"@odata.id\":\"ESMedia(1)\","
        + "\"PropertyInt16@odata.type\":\"#Int16\",\"PropertyInt16\":1}";
    Assert.assertEquals(expectedResult, resultString);
  }
  
  @Test
  public void primitiveValuesAllNull() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllNullable");
    final EntityCollection entitySet = data.readAll(edmEntitySet);
    final String resultString = IOUtils.toString(serializer.entityCollection(metadata,
        edmEntitySet.getEntityType(), entitySet,
        EntityCollectionSerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).build()).build()).getContent());

    final String expected = "{\"@odata.context\":\"$metadata#ESAllNullable\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"value\":[{\"PropertyKey\":1,"
        + "\"PropertyInt16\":null,\"PropertyString\":null,\"PropertyBoolean\":null,\"PropertyByte\":null,"
        + "\"PropertySByte\":null,\"PropertyInt32\":null,\"PropertyInt64\":null,\"PropertySingle\":null,"
        + "\"PropertyDouble\":null,\"PropertyDecimal\":null,\"PropertyBinary\":null,\"PropertyDate\":null,"
        + "\"PropertyDateTimeOffset\":null,\"PropertyDuration\":null,\"PropertyGuid\":null,\"PropertyTimeOfDay\":null,"
        + "\"CollPropertyString\":[\"spiderman@comic.com\",null,\"spidergirl@comic.com\"],"
        + "\"CollPropertyBoolean\":[true,null,false],\"CollPropertyByte\":[50,null,249],"
        + "\"CollPropertySByte\":[-120,null,126],\"CollPropertyInt16\":[1000,null,30112],"
        + "\"CollPropertyInt32\":[23232323,null,10000001],\"CollPropertyInt64\":[929292929292,null,444444444444],"
        + "\"CollPropertySingle\":[1790.0,null,3210.0],\"CollPropertyDouble\":[-17900.0,null,3210.0],"
        + "\"CollPropertyDecimal\":"
        + "[12,null,1234],\"CollPropertyBinary\":[\"q83v\",null,\"VGeJ\"],\"CollPropertyDate\":"
        + "[\"1958-12-03\",null,\"2013-06-25\"],\"CollPropertyDateTimeOffset\":[\"2015-08-12T03:08:34Z\",null,"
        + "\"1948-02-17T09:09:09Z\"],\"CollPropertyDuration\":[\"PT13S\",null,\"PT1H0S\"],\"CollPropertyGuid\":"
        + "[\"ffffff67-89ab-cdef-0123-456789aaaaaa\",null,\"cccccc67-89ab-cdef-0123-456789cccccc\"],"
        + "\"CollPropertyTimeOfDay\":[\"04:14:13\",null,\"00:37:13\"]}]}";

    Assert.assertEquals(expected, resultString);
  }

  @Test
  public void select() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final EdmEntityType entityType = edmEntitySet.getEntityType();
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(0);
    final SelectItem selectItem1 = ExpandSelectMock.mockSelectItem(edmEntitySet, "PropertyDate");
    final SelectItem selectItem2 = ExpandSelectMock.mockSelectItem(edmEntitySet, "PropertyBoolean");
    final SelectOption select = ExpandSelectMock.mockSelectOption(Arrays.asList(
        selectItem1, selectItem2, selectItem2));
    InputStream result = serializer
        .entity(metadata, entityType, entity,
            EntitySerializerOptions.with()
                .contextURL(ContextURL.with().entitySet(edmEntitySet)
                    .selectList(helper.buildContextURLSelectList(entityType, null, select))
                    .suffix(Suffix.ENTITY).build())
                .select(select)
                .build()).getContent();
    final String resultString = IOUtils.toString(result);
    final String expectedResult = "{"
        + "\"@odata.context\":\"$metadata#ESAllPrim(PropertyBoolean,PropertyDate)/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"@odata.id\":\"ESAllPrim(32767)\","
        + "\"PropertyBoolean\":true,\"PropertyDate\":\"2012-12-03\"}";
    Assert.assertEquals(expectedResult, resultString);
  }

  @Test
  public void selectAll() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESTwoPrim");
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(0);
    final SelectItem selectItem1 = ExpandSelectMock.mockSelectItem(edmEntitySet, "PropertyString");
    SelectItem selectItem2 = Mockito.mock(SelectItem.class);
    Mockito.when(selectItem2.isStar()).thenReturn(true);
    final SelectOption select = ExpandSelectMock.mockSelectOption(Arrays.asList(selectItem1, selectItem2));
    InputStream result = serializer.entity(metadata, edmEntitySet.getEntityType(), entity,
        EntitySerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).suffix(Suffix.ENTITY).build())
            .select(select)
            .build()).getContent();
    final String resultString = IOUtils.toString(result);
    final String expectedResult = "{\"@odata.context\":\"$metadata#ESTwoPrim/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"PropertyInt16\":32766,\"PropertyString\":\"Test String1\"}";
    Assert.assertEquals(expectedResult, resultString);
  }

  @Test
  public void selectComplex() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESFourKeyAlias");
    final EdmEntityType entityType = edmEntitySet.getEntityType();
    final EntityCollection entitySet = data.readAll(edmEntitySet);
   final SelectOption select = ExpandSelectMock.mockSelectOption(Arrays.asList(
        ExpandSelectMock.mockSelectItem(edmEntitySet, "PropertyInt16"),
        ExpandSelectMock.mockSelectItem(edmEntitySet,"PropertyCompComp", "PropertyComp", "PropertyString")));
    InputStream result = serializer
        .entityCollection(metadata, entityType, entitySet,
            EntityCollectionSerializerOptions.with()
                .contextURL(ContextURL.with().entitySet(edmEntitySet)
                    .selectList(helper.buildContextURLSelectList(entityType, null, select))
                    .build())
                .select(select)
                .build()).getContent();
    final String resultString = IOUtils.toString(result);
    final String expected = "{"
        +     "\"@odata.context\":\"$metadata#ESFourKeyAlias"
        +        "(PropertyInt16,PropertyCompComp/PropertyComp/PropertyString)\"," 
        +     "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        +     "\"value\":[" 
        +     "{" 
        +         "\"@odata.id\":\""
        +         "ESFourKeyAlias(PropertyInt16=1,KeyAlias1=11,KeyAlias2='Num11',KeyAlias3='Num111')\"," 
        +         "\"PropertyInt16\":1," 
        +         "\"PropertyCompComp\":{" 
        +             "\"PropertyComp\":{" 
        +             "\"@odata.type\":\"#olingo.odata.test1.CTBase\"," 
        +             "\"PropertyString\":\"Num111\"" 
        +     "}}}]}";

   Assert.assertEquals(expected, resultString);
  }
  
  @Test
  public void selectExtendedComplexType() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESFourKeyAlias");
    final EdmEntityType entityType = edmEntitySet.getEntityType();
    final EntityCollection entitySet = data.readAll(edmEntitySet);
    InputStream result = serializer
        .entityCollection(metadata, entityType, entitySet,
            EntityCollectionSerializerOptions.with()
                .contextURL(ContextURL.with().entitySet(edmEntitySet).build())
                .build()).getContent();
    final String resultString = IOUtils.toString(result);

    final String expected = "{"
        + "\"@odata.context\":\"$metadata#ESFourKeyAlias\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"value\":[{"
        + "\"PropertyInt16\":1,"
        + "\"PropertyComp\":{"
        + "\"PropertyInt16\":11,"
        + "\"PropertyString\":\"Num11\""
        + "},"
        + "\"PropertyCompComp\":{"
        + "\"PropertyComp\":{"
        + "\"@odata.type\":\"#olingo.odata.test1.CTBase\","
        + "\"PropertyInt16\":111,"
        + "\"PropertyString\":\"Num111\","
        + "\"AdditionalPropString\":\"Test123\""
        + "}}}]}";

    Assert.assertEquals(expected, resultString);
  }  

  @Test
  public void selectComplexTwice() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESFourKeyAlias");
    final EdmEntityType entityType = edmEntitySet.getEntityType();
    final EntityCollection entitySet = data.readAll(edmEntitySet);
    final SelectOption select = ExpandSelectMock.mockSelectOption(Arrays.asList(
        ExpandSelectMock.mockSelectItem(edmEntitySet, "PropertyComp", "PropertyString"),
        ExpandSelectMock.mockSelectItem(edmEntitySet, "PropertyCompComp", "PropertyComp")));
    final String resultString = IOUtils.toString(serializer
        .entityCollection(metadata, entityType, entitySet,
            EntityCollectionSerializerOptions.with()
                .contextURL(ContextURL.with().entitySet(edmEntitySet)
                    .selectList(helper.buildContextURLSelectList(entityType, null, select))
                    .build())
                .select(select)
                .build()).getContent());
    
    String expected = "{"
            + "\"@odata.context\":\"$metadata#ESFourKeyAlias"
            +   "(PropertyComp/PropertyString,PropertyCompComp/PropertyComp)\","
            + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
            + "\"value\":[{"
                + "\"@odata.id\":\"ESFourKeyAlias(PropertyInt16=1,KeyAlias1=11,KeyAlias2='Num11',KeyAlias3='Num111')\","
                + "\"PropertyComp\":{"
                    + "\"PropertyString\":\"Num11\""
                + "},"
                + "\"PropertyCompComp\":{"
                    + "\"PropertyComp\":{"
                        + "\"@odata.type\":\"#olingo.odata.test1.CTBase\","
                        + "\"PropertyInt16\":111,"
                        + "\"PropertyString\":\"Num111\","
                        + "\"AdditionalPropString\":\"Test123\""
            + "}}}]}";
    
    Assert.assertEquals(expected, resultString);
  }

  @Test
  public void selectComplexNestedCollectionOfComplexWithMetadataFull() throws Exception{
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESCompCollComp");
    final EntityCollection entitySet = data.readAll(edmEntitySet);
    InputStream result = serializerFullMetadata
        .entityCollection(metadata, edmEntitySet.getEntityType(), entitySet,
             EntityCollectionSerializerOptions.with()
                 .contextURL(ContextURL.with().entitySet(edmEntitySet).build())
                 .build())
        .getContent();
    final String resultString = IOUtils.toString(result);
    final String expectedResult = "{\"@odata.context\":\"$metadata#ESCompCollComp\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"value\":[{\"@odata.type\":\"#olingo.odata.test1.ETCompCollComp\","
        + "\"@odata.id\":\"ESCompCollComp(32767)\","
        + "\"PropertyInt16@odata.type\":\"#Int16\",\"PropertyInt16\":32767,"
        + "\"PropertyComp\":{"
        + "\"@odata.type\":\"#olingo.odata.test1.CTCompCollComp\","
        + "\"CollPropertyComp@odata.type\":\"#Collection(olingo.odata.test1.CTTwoPrim)\","
        + "\"CollPropertyComp\":["
        + "{" 
        + "\"@odata.type\":\"#olingo.odata.test1.CTTwoPrim\","
        + "\"PropertyInt16@odata.type\":\"#Int16\","
        + "\"PropertyInt16\":555,"
        + "\"PropertyString\":\"1 Test Complex in Complex Property\""
        + "},{"
        + "\"@odata.type\":\"#olingo.odata.test1.CTTwoPrim\","
        + "\"PropertyInt16@odata.type\":\"#Int16\","
        + "\"PropertyInt16\":666,"
        + "\"PropertyString\":\"2 Test Complex in Complex Property\""
        + "},{"
        + "\"@odata.type\":\"#olingo.odata.test1.CTTwoPrim\","
        + "\"PropertyInt16@odata.type\":\"#Int16\","
        + "\"PropertyInt16\":777,"
        + "\"PropertyString\":\"3 Test Complex in Complex Property\""
        + "}]}},{"
        + "\"@odata.type\":\"#olingo.odata.test1.ETCompCollComp\","
        + "\"@odata.id\":\"ESCompCollComp(12345)\","
        + "\"PropertyInt16@odata.type\":\"#Int16\","
        + "\"PropertyInt16\":12345,"
        + "\"PropertyComp\":{"
        + "\"@odata.type\":\"#olingo.odata.test1.CTCompCollComp\","
        + "\"CollPropertyComp@odata.type\":\"#Collection(olingo.odata.test1.CTTwoPrim)\","
        + "\"CollPropertyComp\":["
        + "{"
        + "\"@odata.type\":\"#olingo.odata.test1.CTTwoPrim\","
        + "\"PropertyInt16@odata.type\":\"#Int16\","
        + "\"PropertyInt16\":888,"
        + "\"PropertyString\":\"11 Test Complex in Complex Property\""
        + "},{"
        + "\"@odata.type\":\"#olingo.odata.test1.CTTwoPrim\","
        + "\"PropertyInt16@odata.type\":\"#Int16\","
        + "\"PropertyInt16\":999,"
        + "\"PropertyString\":\"12 Test Complex in Complex Property\""
        + "},{"
        + "\"@odata.type\":\"#olingo.odata.test1.CTTwoPrim\","
        + "\"PropertyInt16@odata.type\":\"#Int16\","
        + "\"PropertyInt16\":0,"
        + "\"PropertyString\":\"13 Test Complex in Complex Property\""
        + "}]}}]}";
    Assert.assertEquals(expectedResult, resultString);
  }

  @Test
  public void selectComplexNestedCollectionOfComplexWithMetadataMinimal() throws Exception{
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESCompCollComp");
    final EntityCollection entitySet = data.readAll(edmEntitySet);
    InputStream result = serializer
        .entityCollection(metadata, edmEntitySet.getEntityType(), entitySet,
             EntityCollectionSerializerOptions.with()
                 .contextURL(ContextURL.with().entitySet(edmEntitySet).build())
                 .build())
        .getContent();
    final String resultString = IOUtils.toString(result);
    final String expectedResult = "{\"@odata.context\":\"$metadata#ESCompCollComp\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"value\":[{"
        + "\"PropertyInt16\":32767,"
        + "\"PropertyComp\":{"
        + "\"CollPropertyComp\":["
        + "{" 
        + "\"PropertyInt16\":555,"
        + "\"PropertyString\":\"1 Test Complex in Complex Property\""
        + "},{"
        + "\"PropertyInt16\":666,"
        + "\"PropertyString\":\"2 Test Complex in Complex Property\""
        + "},{"
        + "\"PropertyInt16\":777,"
        + "\"PropertyString\":\"3 Test Complex in Complex Property\""
        + "}]}},{"
        + "\"PropertyInt16\":12345,"
        + "\"PropertyComp\":{"
        + "\"CollPropertyComp\":["
        + "{"
        + "\"PropertyInt16\":888,"
        + "\"PropertyString\":\"11 Test Complex in Complex Property\""
        + "},{"
        + "\"PropertyInt16\":999,"
        + "\"PropertyString\":\"12 Test Complex in Complex Property\""
        + "},{"
        + "\"PropertyInt16\":0,"
        + "\"PropertyString\":\"13 Test Complex in Complex Property\""
        + "}]}}]}";
    Assert.assertEquals(expectedResult, resultString);
  }

  @Test
  public void selectComplexNestedCollectionOfComplexWithMetadataNone() throws Exception{
    final String METADATA_TEXT = "@odata.";
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESCompCollComp");
    final EntityCollection entitySet = data.readAll(edmEntitySet);
    InputStream result = serializerNoMetadata
        .entityCollection(metadata, edmEntitySet.getEntityType(), entitySet,
            EntityCollectionSerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).build())
            .build())
        .getContent();
    final String resultString = IOUtils.toString(result);
    Assert.assertEquals(false, resultString.contains(METADATA_TEXT));
  }

  @Test(expected = SerializerException.class)
  public void selectMissingId() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final EdmEntityType entityType = edmEntitySet.getEntityType();
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(0);
    entity.setId(null);
    final SelectItem selectItem1 = ExpandSelectMock.mockSelectItem(edmEntitySet, "PropertyDate");
    final SelectItem selectItem2 = ExpandSelectMock.mockSelectItem(edmEntitySet, "PropertyBoolean");
    final SelectOption select = ExpandSelectMock.mockSelectOption(Arrays.asList(
        selectItem1, selectItem2, selectItem2));

    try {
    serializer.entity(metadata, entityType, entity,
            EntitySerializerOptions.with()
                .contextURL(ContextURL.with().entitySet(edmEntitySet)
                    .selectList(helper.buildContextURLSelectList(entityType, null, select))
                    .suffix(Suffix.ENTITY).build())
                .select(select)
                .build()).getContent();
      Assert.fail("Expected exception not thrown!");
    } catch (final SerializerException e) {
      Assert.assertEquals(SerializerException.MessageKeys.MISSING_ID, e.getMessageKey());
      throw e;
    }
  }

  @Test
  public void expand() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESTwoPrim");
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(3);
    final ExpandOption expand = ExpandSelectMock.mockExpandOption(Collections.singletonList(
        ExpandSelectMock.mockExpandItem(edmEntitySet, "NavPropertyETAllPrimOne")));
    InputStream result = serializer.entity(metadata, edmEntitySet.getEntityType(), entity,
        EntitySerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).suffix(Suffix.ENTITY).build())
            .expand(expand)
            .build()).getContent();
    final String resultString = IOUtils.toString(result);
    Assert.assertEquals("{\"@odata.context\":\"$metadata#ESTwoPrim/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"PropertyInt16\":32767,\"PropertyString\":\"Test String4\","
        + "\"NavPropertyETAllPrimOne\":{"
        + "\"PropertyInt16\":32767,"
        + "\"PropertyString\":\"First Resource - positive values\","
        + "\"PropertyBoolean\":true,"
        + "\"PropertyByte\":255,"
        + "\"PropertySByte\":127,"
        + "\"PropertyInt32\":2147483647,"
        + "\"PropertyInt64\":9223372036854775807,"
        + "\"PropertySingle\":1.79E20,"
        + "\"PropertyDouble\":-1.79E19,"
        + "\"PropertyDecimal\":34,"
        + "\"PropertyBinary\":\"ASNFZ4mrze8=\","
        + "\"PropertyDate\":\"2012-12-03\","
        + "\"PropertyDateTimeOffset\":\"2012-12-03T07:16:23Z\","
        + "\"PropertyDuration\":\"PT6S\","
        + "\"PropertyGuid\":\"01234567-89ab-cdef-0123-456789abcdef\","
        + "\"PropertyTimeOfDay\":\"03:26:05\"}}",
        resultString);
  }

  @Test
  public void expandSelect() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESTwoPrim");
    final EdmEntityType entityType = edmEntitySet.getEntityType();
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(3);
    final SelectOption select = ExpandSelectMock.mockSelectOption(Collections.singletonList(
        ExpandSelectMock.mockSelectItem(entityContainer.getEntitySet("ESAllPrim"), "PropertyDate")));
    ExpandItem expandItem = ExpandSelectMock.mockExpandItem(edmEntitySet, "NavPropertyETAllPrimOne");
    Mockito.when(expandItem.getSelectOption()).thenReturn(select);
    final ExpandOption expand = ExpandSelectMock.mockExpandOption(Collections.singletonList(expandItem));
    final String resultString = IOUtils.toString(serializer
        .entity(metadata, entityType, entity,
            EntitySerializerOptions.with()
                .contextURL(ContextURL.with().entitySet(edmEntitySet)
                    .selectList(helper.buildContextURLSelectList(entityType, expand, select))
                    .suffix(Suffix.ENTITY).build())
                .expand(expand)
                .build()).getContent());
    Assert.assertEquals("{"
        + "\"@odata.context\":\"$metadata#ESTwoPrim(NavPropertyETAllPrimOne(PropertyDate))/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"PropertyInt16\":32767,\"PropertyString\":\"Test String4\","
        + "\"NavPropertyETAllPrimOne\":{\"@odata.id\":\"ESAllPrim(32767)\",\"PropertyDate\":\"2012-12-03\"}}",
        resultString);
  }

  @Test
  public void expandAll() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final EdmEntityType entityType = edmEntitySet.getEntityType();
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(0);
    final ExpandItem expandItem = ExpandSelectMock.mockExpandItem(edmEntitySet, "NavPropertyETTwoPrimOne");
    ExpandItem expandItemAll = Mockito.mock(ExpandItem.class);
    Mockito.when(expandItemAll.isStar()).thenReturn(true);
    final ExpandOption expand = ExpandSelectMock.mockExpandOption(Arrays.asList(
        expandItem, expandItem, expandItemAll));
    final SelectOption select = ExpandSelectMock.mockSelectOption(Collections.singletonList(
        ExpandSelectMock.mockSelectItem(edmEntitySet, "PropertySByte")));
    final String resultString = IOUtils.toString(serializer
        .entity(metadata, entityType, entity,
            EntitySerializerOptions.with()
                .contextURL(ContextURL.with().entitySet(edmEntitySet)
                    .selectList(helper.buildContextURLSelectList(entityType, expand, select))
                    .suffix(Suffix.ENTITY).build())
                .expand(expand)
                .select(select)
                .build()).getContent());
    Assert.assertEquals("{"
        + "\"@odata.context\":\"$metadata#ESAllPrim(PropertySByte)/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"@odata.id\":\"ESAllPrim(32767)\","
        + "\"PropertySByte\":127,"
        + "\"NavPropertyETTwoPrimOne\":{\"PropertyInt16\":32767,\"PropertyString\":\"Test String4\"},"
        + "\"NavPropertyETTwoPrimMany\":[{\"PropertyInt16\":-365,\"PropertyString\":\"Test String2\"}]}",
        resultString);
  }

  @Test
  public void expandNoData() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final EdmEntityType entityType = edmEntitySet.getEntityType();
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(1);
    ExpandItem expandItemAll = Mockito.mock(ExpandItem.class);
    Mockito.when(expandItemAll.isStar()).thenReturn(true);
    final ExpandOption expand = ExpandSelectMock.mockExpandOption(Collections.singletonList(expandItemAll));
    final SelectOption select = ExpandSelectMock.mockSelectOption(Collections.singletonList(
        ExpandSelectMock.mockSelectItem(edmEntitySet, "PropertyTimeOfDay")));
    final String resultString = IOUtils.toString(serializer
        .entity(metadata, entityType, entity,
            EntitySerializerOptions.with()
                .contextURL(ContextURL.with().entitySet(edmEntitySet)
                    .selectList(helper.buildContextURLSelectList(entityType, expand, select))
                    .suffix(Suffix.ENTITY).build())
                .expand(expand)
                .select(select)
                .build()).getContent());
    Assert.assertEquals("{"
        + "\"@odata.context\":\"$metadata#ESAllPrim(PropertyTimeOfDay)/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"@odata.id\":\"ESAllPrim(-32768)\","
        + "\"PropertyTimeOfDay\":\"23:49:14\","
        + "\"NavPropertyETTwoPrimOne\":null,\"NavPropertyETTwoPrimMany\":[]}",
        resultString);
  }

  @Test
  public void expandTwoLevels() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESTwoPrim");
    final EdmEntityType entityType = edmEntitySet.getEntityType();
    final EdmEntitySet innerEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(1);
    ExpandItem expandItemSecond = Mockito.mock(ExpandItem.class);
    Mockito.when(expandItemSecond.isStar()).thenReturn(true);
    final ExpandOption expandInner = ExpandSelectMock.mockExpandOption(Collections.singletonList(expandItemSecond));
    ExpandItem expandItemFirst = ExpandSelectMock.mockExpandItem(edmEntitySet, "NavPropertyETAllPrimMany");
    Mockito.when(expandItemFirst.getExpandOption()).thenReturn(expandInner);
    final SelectOption select = ExpandSelectMock.mockSelectOption(Collections.singletonList(
        ExpandSelectMock.mockSelectItem(innerEntitySet, "PropertyInt32")));
    Mockito.when(expandItemFirst.getSelectOption()).thenReturn(select);
    final ExpandOption expand = ExpandSelectMock.mockExpandOption(Collections.singletonList(expandItemFirst));
    final String resultString = IOUtils.toString(serializer
        .entity(metadata, entityType, entity,
            EntitySerializerOptions.with()
                .contextURL(ContextURL.with().entitySet(edmEntitySet)
                    .selectList(helper.buildContextURLSelectList(entityType, expand, select))
                    .suffix(Suffix.ENTITY).build())
                .expand(expand)
                .build()).getContent());
    Assert.assertEquals("{"
        + "\"@odata.context\":\"$metadata#ESTwoPrim(NavPropertyETAllPrimMany(PropertyInt32))/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"PropertyInt16\":-365,\"PropertyString\":\"Test String2\","
        + "\"NavPropertyETAllPrimMany\":["
        + "{\"@odata.id\":\"ESAllPrim(-32768)\",\"PropertyInt32\":-2147483648,"
        + "\"NavPropertyETTwoPrimOne\":null,\"NavPropertyETTwoPrimMany\":[]},"
        + "{\"@odata.id\":\"ESAllPrim(0)\",\"PropertyInt32\":0,\"NavPropertyETTwoPrimOne\":null,"
        + "\"NavPropertyETTwoPrimMany\":["
        + "{\"PropertyInt16\":32766,\"PropertyString\":\"Test String1\"},"
        + "{\"PropertyInt16\":-32766,\"PropertyString\":null},"
        + "{\"PropertyInt16\":32767,\"PropertyString\":\"Test String4\"}]}]}",
        resultString);
  }
  
  @Test
  public void expandStarTwoLevels() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESTwoPrim");
    final EdmEntityType entityType = edmEntitySet.getEntityType();
    final EdmEntitySet innerEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(1);
    ExpandItem expandItem = Mockito.mock(ExpandItem.class);
    Mockito.when(expandItem.isStar()).thenReturn(true);
    LevelsExpandOption levels = Mockito.mock(LevelsExpandOption.class);
    Mockito.when(levels.getValue()).thenReturn(2);
    Mockito.when(expandItem.getLevelsOption()).thenReturn(levels);
    final SelectOption select = ExpandSelectMock.mockSelectOption(Collections.singletonList(
        ExpandSelectMock.mockSelectItem(innerEntitySet, "PropertyInt32")));
    final ExpandOption expand = ExpandSelectMock.mockExpandOption(Collections.singletonList(expandItem));
    final String resultString = IOUtils.toString(serializer
        .entity(metadata, entityType, entity,
            EntitySerializerOptions.with()
                .contextURL(ContextURL.with().entitySet(edmEntitySet)
                    .selectList(helper.buildContextURLSelectList(entityType, expand, select))
                    .suffix(Suffix.ENTITY).build())
                .expand(expand)
                .build()).getContent());
    Assert.assertEquals("{\"@odata.context\":\"$metadata#ESTwoPrim/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"PropertyInt16\":-365,\"PropertyString\":\"Test String2\","
        + "\"NavPropertyETAllPrimOne\":null,"
        + "\"NavPropertyETAllPrimMany\":["
        + "{\"PropertyInt16\":-32768,\"PropertyString\":\"Second Resource - negative values\","
        + "\"PropertyBoolean\":false,\"PropertyByte\":0,\"PropertySByte\":-128,\"PropertyInt32\":-2147483648,"
        + "\"PropertyInt64\":-9223372036854775808,\"PropertySingle\":-1.79E8,\"PropertyDouble\":-179000.0,"
        + "\"PropertyDecimal\":-34,\"PropertyBinary\":\"ASNFZ4mrze8=\",\"PropertyDate\":\"2015-11-05\","
        + "\"PropertyDateTimeOffset\":\"2005-12-03T07:17:08Z\",\"PropertyDuration\":\"PT9S\","
        + "\"PropertyGuid\":\"76543201-23ab-cdef-0123-456789dddfff\",\"PropertyTimeOfDay\":\"23:49:14\","
        + "\"NavPropertyETTwoPrimOne\":null,\"NavPropertyETTwoPrimMany\":[]},"
        + "{\"PropertyInt16\":0,\"PropertyString\":\"\",\"PropertyBoolean\":false,\"PropertyByte\":0,"
        + "\"PropertySByte\":0,\"PropertyInt32\":0,\"PropertyInt64\":0,\"PropertySingle\":0.0,"
        + "\"PropertyDouble\":0.0,\"PropertyDecimal\":0,\"PropertyBinary\":\"\","
        + "\"PropertyDate\":\"1970-01-01\",\"PropertyDateTimeOffset\":\"2005-12-03T00:00:00Z\","
        + "\"PropertyDuration\":\"PT0S\",\"PropertyGuid\":\"76543201-23ab-cdef-0123-456789cccddd\","
        + "\"PropertyTimeOfDay\":\"00:01:01\",\"NavPropertyETTwoPrimOne\":null,"
        + "\"NavPropertyETTwoPrimMany\":["
        + "{\"PropertyInt16\":32766,\"PropertyString\":\"Test String1\"},"
        + "{\"PropertyInt16\":-32766,\"PropertyString\":null},"
        + "{\"PropertyInt16\":32767,\"PropertyString\":\"Test String4\"}]}]}",
        resultString);
  }

  @Test
  public void primitiveProperty() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType().getProperty("PropertyString");
    final Property property = data.readAll(edmEntitySet).getEntities().get(0).getProperty(edmProperty.getName());
    final String resultString = IOUtils.toString(serializer
        .primitive(metadata, (EdmPrimitiveType) edmProperty.getType(), property,
            PrimitiveSerializerOptions.with()
                .contextURL(ContextURL.with()
                    .entitySet(edmEntitySet).keyPath("32767").navOrPropertyPath(edmProperty.getName())
                    .build())
                .build()).getContent());
    Assert.assertEquals("{"
        + "\"@odata.context\":\"$metadata#ESAllPrim(32767)/PropertyString\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"value\":\"First Resource - positive values\"}",
        resultString);
  }

  @Test
  public void primitivePropertyNoMetadata() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType().getProperty("PropertyString");
    final Property property = data.readAll(edmEntitySet).getEntities().get(0).getProperty(edmProperty.getName());
    final String resultString = IOUtils.toString(serializerNoMetadata
        .primitive(metadata, (EdmPrimitiveType) edmProperty.getType(), property, null).getContent());
    Assert.assertEquals("{\"value\":\"First Resource - positive values\"}", resultString);
  }
  
  @Test
  public void primitivePropertyWithMetadataFull() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType()
        .getProperty("PropertyString");
    final Property property = data.readAll(edmEntitySet).getEntities().get(0)
        .getProperty(edmProperty.getName());
    final String resultString = IOUtils
        .toString(serializerFullMetadata
            .primitive(metadata, (EdmPrimitiveType) edmProperty.getType(), property,
                PrimitiveSerializerOptions.with()
                    .contextURL(ContextURL.with().entitySet(edmEntitySet)
                        .keyPath("32767")
                        .navOrPropertyPath(edmProperty.getName()).build())
                    .build())
            .getContent());
    Assert.assertEquals(
        "{\"@odata.context\":\"$metadata#ESAllPrim(32767)/PropertyString\","
            + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
            + "\"value\":\"First Resource - positive values\"}",
        resultString);
  } 

  @Test(expected = SerializerException.class)
  public void primitivePropertyNull() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType().getProperty("PropertyString");
    final Property property = new Property("Edm.String", edmProperty.getName(), ValueType.PRIMITIVE, null);
    serializer.primitive(metadata, (EdmPrimitiveType) edmProperty.getType(), property,
        PrimitiveSerializerOptions.with()
            .contextURL(ContextURL.with()
                .entitySet(edmEntitySet).keyPath("4242").navOrPropertyPath(edmProperty.getName())
                .build())
            .build());
  }

  @Test
  public void primitiveCollectionProperty() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESCollAllPrim");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType().getProperty("CollPropertyString");
    final Property property = data.readAll(edmEntitySet).getEntities().get(0).getProperty(edmProperty.getName());

    final String resultString = IOUtils.toString(serializer
        .primitiveCollection(metadata, (EdmPrimitiveType) edmProperty.getType(), property,
            PrimitiveSerializerOptions.with()
                .contextURL(ContextURL.with()
                    .entitySet(edmEntitySet).keyPath("1").navOrPropertyPath(edmProperty.getName())
                    .build())
                .build()).getContent());
    Assert.assertEquals("{"
        + "\"@odata.context\":\"$metadata#ESCollAllPrim(1)/CollPropertyString\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"value\":[\"Employee1@company.example\",\"Employee2@company.example\",\"Employee3@company.example\"]}",
        resultString);
  }

  @Test
  public void primitiveCollectionNoMetadata() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESCollAllPrim");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType().getProperty("CollPropertyString");
    final Property property = data.readAll(edmEntitySet).getEntities().get(0).getProperty(edmProperty.getName());
    final String resultString = IOUtils.toString(serializerNoMetadata
        .primitiveCollection(metadata, (EdmPrimitiveType) edmProperty.getType(), property, null).getContent());
    Assert.assertEquals("{\"value\":[\"Employee1@company.example\","
        + "\"Employee2@company.example\",\"Employee3@company.example\"]}",
        resultString);
  }

  @Test
  public void primitiveCollectionPropertyWithMetadataFull() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESCollAllPrim");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType().getProperty("CollPropertyString");
    final Property property = data.readAll(edmEntitySet).getEntities().get(0).getProperty(edmProperty.getName());

    final String resultString = IOUtils.toString(serializerFullMetadata
                .primitiveCollection(metadata, (EdmPrimitiveType) edmProperty.getType(), property,
                        PrimitiveSerializerOptions.with()
                                .contextURL(ContextURL.with()
                                        .entitySet(edmEntitySet).keyPath("1").navOrPropertyPath(edmProperty.getName())
                                        .build())
                                .build()).getContent());
    Assert.assertEquals("{\"@odata.context\":\"$metadata#ESCollAllPrim(1)/CollPropertyString\"," +
                    "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\"," +
                    "\"@odata.type\":\"#Collection(String)\",\"value\":[\"Employee1@company.example\"," +
                    "\"Employee2@company.example\",\"Employee3@company.example\"]}", resultString);
  }

  @Test
  public void primitiveCollectionStreamed() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESCollAllPrim");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType().getProperty("CollPropertyString");
    final Property property = data.readAll(edmEntitySet).getEntities().get(0).getProperty(edmProperty.getName());

    final Iterator<?> primitiveIterator = property.asCollection().iterator();

    final PropertyIterator valueProvider = new PropertyIterator(property.getName()) {
      @Override
      public boolean hasNext() {
        return primitiveIterator.hasNext();
      }

      @Override
      public Object next() {
        return primitiveIterator.next();
      }
    };

    final SerializerStreamResult serializerStreamResult = serializerNoMetadata.primitiveCollectionStreamed(
            metadata,
            (EdmPrimitiveType) edmProperty.getType(),
            valueProvider,
            null);

    final ByteArrayOutputStream bout = new ByteArrayOutputStream();
    serializerStreamResult.getODataContent().write(bout);

    final String resultString = new String(bout.toByteArray(), "UTF-8");

    Assert.assertEquals("{\"value\":[\"Employee1@company.example\","
        + "\"Employee2@company.example\",\"Employee3@company.example\"]}",
        resultString);
  }

  @Test
  public void primitiveCollectionStreamedWithError() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESCollAllPrim");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType().getProperty("CollPropertyString");
    final Property property = data.readAll(edmEntitySet).getEntities().get(0).getProperty(edmProperty.getName());

    final Iterator<?> primitiveIterator = property.asCollection().iterator();

    // Note - We combine returning null value with serialization option of nullable to throw the error
    final PropertyIterator valueProvider = new PropertyIterator(property.getName()) {
      @Override
      public boolean hasNext() {
        return primitiveIterator.hasNext();
      }

      @Override
      public Object next() {
        return null;
      }
    };

    final ODataContentWriteErrorCallback errorCallback = new ODataContentWriteErrorCallback() {
      @Override
      public void handleError(ODataContentWriteErrorContext context, WritableByteChannel channel) {
        try {
          String msgKey = context.getODataLibraryException().getMessageKey().getKey();
          String toChannel = "ERROR : " + msgKey;
          channel.write(ByteBuffer.wrap(toChannel.getBytes("UTF-8")));
        } catch (IOException e) {
          throw new RuntimeException("Error in error.");
        }
      }
    };

    final SerializerStreamResult serializerStreamResult = serializerNoMetadata.primitiveCollectionStreamed(
            metadata,
            (EdmPrimitiveType) edmProperty.getType(),
            valueProvider,
            PrimitiveSerializerOptions.with()
                    .writeContentErrorCallback(errorCallback)
                    .nullable(false)
                    .build());

    final ByteArrayOutputStream bout = new ByteArrayOutputStream();
    serializerStreamResult.getODataContent().write(bout);

    final String resultString = new String(bout.toByteArray(), "UTF-8");
    Assert.assertEquals("ERROR : WRONG_PROPERTY_VALUE", resultString);
  }

  @Test
  public void complexProperty() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESMixPrimCollComp");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType().getProperty("PropertyComp");
    final Property property = data.readAll(edmEntitySet).getEntities().get(0).getProperty("PropertyComp");

    final String resultString = IOUtils.toString(serializer
        .complex(metadata, (EdmComplexType) edmProperty.getType(), property,
            ComplexSerializerOptions.with()
                .contextURL(ContextURL.with()
                    .entitySet(edmEntitySet).keyPath("32767").navOrPropertyPath(edmProperty.getName())
                    .build())
                .build()).getContent());
    Assert.assertEquals("{"
        + "\"@odata.context\":\"$metadata#ESMixPrimCollComp(32767)/PropertyComp\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"PropertyInt16\":111,\"PropertyString\":\"TEST A\"}",
        resultString);
  }

  @Test
  public void complexPropertyNoMetadata() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESMixPrimCollComp");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType().getProperty("PropertyComp");
    final Property property = data.readAll(edmEntitySet).getEntities().get(0).getProperty("PropertyComp");
    final String resultString = IOUtils.toString(serializerNoMetadata
        .complex(metadata, (EdmComplexType) edmProperty.getType(), property, null).getContent());
    Assert.assertEquals("{\"PropertyInt16\":111,\"PropertyString\":\"TEST A\"}", resultString);
  }

  @Test
  public void complexPropertyWithMetadataFull() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESMixPrimCollComp");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType().getProperty("PropertyComp");
    final Property property = data.readAll(edmEntitySet).getEntities().get(0).getProperty("PropertyComp");
    final String resultString = IOUtils.toString(serializerFullMetadata
             .complex(metadata, (EdmComplexType) edmProperty.getType(), property,
                    ComplexSerializerOptions.with()
                            .contextURL(ContextURL.with()
                                    .entitySet(edmEntitySet).keyPath("32767").navOrPropertyPath(edmProperty.getName())
                                        .build()).build()).getContent());
    Assert.assertEquals("{\"@odata.context\":\"$metadata#ESMixPrimCollComp(32767)/PropertyComp\"," +
                "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\"," +
                "\"@odata.type\":\"#olingo.odata.test1.CTTwoPrim\"," +
                "\"PropertyInt16@odata.type\":\"#Int16\",\"PropertyInt16\":111," +
                "\"PropertyString\":\"TEST A\"}",resultString);
  }

  @Test
  public void complexCollectionProperty() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESMixPrimCollComp");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType().getProperty("CollPropertyComp");
    final Property property = data.readAll(edmEntitySet).getEntities().get(0).getProperty(edmProperty.getName());

    final String resultString = IOUtils.toString(serializer
        .complexCollection(metadata, (EdmComplexType) edmProperty.getType(), property,
            ComplexSerializerOptions.with()
                .contextURL(ContextURL.with()
                    .entitySet(edmEntitySet).keyPath("32767").navOrPropertyPath(edmProperty.getName())
                    .build())
                .build()).getContent());
    Assert.assertEquals("{"
        + "\"@odata.context\":\"$metadata#ESMixPrimCollComp(32767)/CollPropertyComp\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"value\":[{\"PropertyInt16\":123,\"PropertyString\":\"TEST 1\"},"
        + "{\"PropertyInt16\":456,\"PropertyString\":\"TEST 2\"},"
        + "{\"PropertyInt16\":789,\"PropertyString\":\"TEST 3\"}]}",
        resultString);
  }

  @Test
  public void complexCollectionStreamed() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESMixPrimCollComp");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType().getProperty("CollPropertyComp");
    final Property property = data.readAll(edmEntitySet).getEntities().get(0).getProperty(edmProperty.getName());

    final Iterator<?> complexIterator = property.asCollection().iterator();

    final ComplexIterator valueProvider = new ComplexIterator() {
      @Override
      public boolean hasNext() {
        return complexIterator.hasNext();
      }

      @Override
      public ComplexValue next() {
        return (ComplexValue) complexIterator.next();
      }
    };

    final SerializerStreamResult serializerStreamResult = serializerNoMetadata.complexCollectionStreamed(
            metadata,
            (EdmComplexType) edmProperty.getType(),
            valueProvider,
            null);

    final ByteArrayOutputStream bout = new ByteArrayOutputStream();
    serializerStreamResult.getODataContent().write(bout);

    final String resultString = new String(bout.toByteArray(), "UTF-8");

    Assert.assertEquals("{\"value\":[{\"PropertyInt16\":123,\"PropertyString\":\"TEST 1\"},"
        + "{\"PropertyInt16\":456," + "\"PropertyString\":\"TEST 2\"},"
        + "{\"PropertyInt16\":789,\"PropertyString\":\"TEST 3\"}]}",
        resultString);
  }

  @Test
  public void complexCollectionStreamedWithError() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESMixPrimCollComp");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType().getProperty("CollPropertyComp");
    final Property property = data.readAll(edmEntitySet).getEntities().get(0).getProperty(edmProperty.getName());

    final Iterator<?> complexIterator = property.asCollection().iterator();

    final ComplexIterator valueProvider = new ComplexIterator() {
      @Override
      public boolean hasNext() {
        return complexIterator.hasNext();
      }

      @Override
      public ComplexValue next() {
        return new ComplexValue();
      }
    };

    final ODataContentWriteErrorCallback errorCallback = new ODataContentWriteErrorCallback() {
      @Override
      public void handleError(ODataContentWriteErrorContext context, WritableByteChannel channel) {
        try {
          String msgKey = context.getODataLibraryException().getMessageKey().getKey();
          String toChannel = "ERROR : " + msgKey;
          channel.write(ByteBuffer.wrap(toChannel.getBytes("UTF-8")));
        } catch (IOException e) {
          throw new RuntimeException("Error in error.");
        }
      }
    };

    final SerializerStreamResult serializerStreamResult =
            serializerNoMetadata.complexCollectionStreamed(
                    metadata,
                    (EdmComplexType) edmProperty.getType(),
                    valueProvider,
                    ComplexSerializerOptions.with().writeContentErrorCallback(errorCallback).build());

    final ByteArrayOutputStream bout = new ByteArrayOutputStream();
    serializerStreamResult.getODataContent().write(bout);

    final String resultString = new String(bout.toByteArray(), "UTF-8");
    Assert.assertEquals("ERROR : MISSING_PROPERTY", resultString);
  }

  @Test
  public void complexCollectionPropertyNoMetadata() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESMixPrimCollComp");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType().getProperty("CollPropertyComp");
    final Property property = data.readAll(edmEntitySet).getEntities().get(0).getProperty(edmProperty.getName());
    final String resultString = IOUtils.toString(serializerNoMetadata
        .complexCollection(metadata, (EdmComplexType) edmProperty.getType(), property, null).getContent());
    Assert.assertEquals("{\"value\":[{\"PropertyInt16\":123,\"PropertyString\":\"TEST 1\"},"
        + "{\"PropertyInt16\":456,\"PropertyString\":\"TEST 2\"},"
        + "{\"PropertyInt16\":789,\"PropertyString\":\"TEST 3\"}]}",
        resultString);
  }

  @Test
  public void complexCollectionPropertyWithMetadataFull() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESMixPrimCollComp");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType().getProperty("CollPropertyComp");
    final Property property = data.readAll(edmEntitySet).getEntities().get(0).getProperty(edmProperty.getName());
    final String resultString = IOUtils.toString(serializerFullMetadata
            .complexCollection(metadata, (EdmComplexType) edmProperty.getType(),
                property, ComplexSerializerOptions.with()
                    .contextURL(ContextURL.with().entitySet(edmEntitySet)
                        .keyPath("32767")
                        .navOrPropertyPath(edmProperty.getName()).build())
                    .build())
            .getContent());
    final String expectedResult = "{\"@odata.context\":\"$metadata#ESMixPrimCollComp(32767)/CollPropertyComp\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"@odata.type\":\"#Collection(olingo.odata.test1.CTTwoPrim)\","
        + "\"value\":[{\"@odata.type\":\"#olingo.odata.test1.CTTwoPrim\","
        + "\"PropertyInt16@odata.type\":\"#Int16\",\"PropertyInt16\":123,"
        + "\"PropertyString\":\"TEST 1\"},"
        + "{\"@odata.type\":\"#olingo.odata.test1.CTTwoPrim\","
        + "\"PropertyInt16@odata.type\":\"#Int16\",\"PropertyInt16\":456,"
        + "\"PropertyString\":\"TEST 2\"},"
        + "{\"@odata.type\":\"#olingo.odata.test1.CTTwoPrim\","
        + "\"PropertyInt16@odata.type\":\"#Int16\",\"PropertyInt16\":789,"
        + "\"PropertyString\":\"TEST 3\"}]}";
    Assert.assertEquals(expectedResult, resultString);
  }
  
  @Test
  public void entityReference() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(0);

    final SerializerResult serializerResult = serializer.reference(metadata, edmEntitySet, entity,
        ReferenceSerializerOptions.with().contextURL(ContextURL.with().suffix(Suffix.REFERENCE).build()).build());
    final String resultString = IOUtils.toString(serializerResult.getContent());

    Assert.assertEquals("{\"@odata.context\":\"$metadata#$ref\","
        + "\"@odata.id\":\"ESAllPrim(32767)\"}",
        resultString);
  }

  @Test
  public void entityReferenceNoMetadata() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(0);
    final String resultString = IOUtils.toString(
        serializerNoMetadata.reference(metadata, edmEntitySet, entity, null).getContent());
    Assert.assertEquals("{\"@odata.id\":\"ESAllPrim(32767)\"}", resultString);
  }

  @Test
  public void entityCollectionReference() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final EntityCollection entityCollection = data.readAll(edmEntitySet);

    final SerializerResult serializerResult = serializer.referenceCollection(metadata,
        edmEntitySet,
        entityCollection,
        ReferenceCollectionSerializerOptions.with()
            .contextURL(ContextURL.with().asCollection().suffix(Suffix.REFERENCE).build())
            .build());

    final String resultString = IOUtils.toString(serializerResult.getContent());

    Assert.assertEquals("{\"@odata.context\":\"$metadata#Collection($ref)\","
        + "\"value\":[{\"@odata.id\":\"ESAllPrim(32767)\"},"
        + "{\"@odata.id\":\"ESAllPrim(-32768)\"},"
        + "{\"@odata.id\":\"ESAllPrim(0)\"}]}",
        resultString);
  }

  @Test
  public void entityCollectionReferenceEmpty() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final EntityCollection entityCollection = new EntityCollection();

    final SerializerResult serializerResult = serializer.referenceCollection(metadata,
        edmEntitySet, entityCollection,
        ReferenceCollectionSerializerOptions.with()
            .contextURL(ContextURL.with().asCollection().suffix(Suffix.REFERENCE).build())
            .build());

    final String resultString = IOUtils.toString(serializerResult.getContent());

    Assert.assertEquals("{\"@odata.context\":\"$metadata#Collection($ref)\","
        + "\"value\":[]}", resultString);
  }

  @Test
  public void entityCollectionReferenceEmptyNoMetadata() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final EntityCollection entityCollection = new EntityCollection();
    final String resultString = IOUtils.toString(serializerNoMetadata
        .referenceCollection(metadata, edmEntitySet, entityCollection, null).getContent());
    Assert.assertEquals("{\"value\":[]}", resultString);
  }

  @Test
  public void entityIEE754Compatible() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(0);
    InputStream result = serializerIEEECompatible.entity(metadata, edmEntitySet.getEntityType(), entity,
        EntitySerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).suffix(Suffix.ENTITY).build())
            .build()).getContent();
    final String resultString = IOUtils.toString(result);
    final String expectedResult = "{"
        + "\"@odata.context\":\"$metadata#ESAllPrim/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"PropertyInt16\":32767,"
        + "\"PropertyString\":\"First Resource - positive values\","
        + "\"PropertyBoolean\":true,"
        + "\"PropertyByte\":255,"
        + "\"PropertySByte\":127,"
        + "\"PropertyInt32\":2147483647,"
        + "\"PropertyInt64\":\"" + Long.MAX_VALUE + "\","
        + "\"PropertySingle\":1.79E20,"
        + "\"PropertyDouble\":-1.79E19,"
        + "\"PropertyDecimal\":\"34\","
        + "\"PropertyBinary\":\"ASNFZ4mrze8=\","
        + "\"PropertyDate\":\"2012-12-03\","
        + "\"PropertyDateTimeOffset\":\"2012-12-03T07:16:23Z\","
        + "\"PropertyDuration\":\"PT6S\","
        + "\"PropertyGuid\":\"01234567-89ab-cdef-0123-456789abcdef\","
        + "\"PropertyTimeOfDay\":\"03:26:05\""
        + "}";
    Assert.assertEquals(expectedResult, resultString);
  }

  @Test
  public void entityCollAllPrimIEEE754Compatible() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESCollAllPrim");
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(0);
    InputStream result = serializerIEEECompatible.entity(metadata, edmEntitySet.getEntityType(), entity,
        EntitySerializerOptions.with()
            .contextURL(ContextURL.with().serviceRoot(URI.create("http://host/service/"))
                .entitySet(edmEntitySet).suffix(Suffix.ENTITY).build())
            .build()).getContent();
    final String resultString = IOUtils.toString(result);
    final String expectedResult = "{"
        + "\"@odata.context\":\"http://host/service/$metadata#ESCollAllPrim/$entity\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"PropertyInt16\":1,"
        + "\"CollPropertyString\":"
        + "[\"Employee1@company.example\",\"Employee2@company.example\",\"Employee3@company.example\"],"
        + "\"CollPropertyBoolean\":[true,false,true],"
        + "\"CollPropertyByte\":[50,200,249],"
        + "\"CollPropertySByte\":[-120,120,126],"
        + "\"CollPropertyInt16\":[1000,2000,30112],"
        + "\"CollPropertyInt32\":[23232323,11223355,10000001],"
        + "\"CollPropertyInt64\":[\"929292929292\",\"333333333333\",\"444444444444\"],"
        + "\"CollPropertySingle\":[1790.0,26600.0,3210.0],"
        + "\"CollPropertyDouble\":[-17900.0,-2.78E7,3210.0],"
        + "\"CollPropertyDecimal\":[\"12\",\"-2\",\"1234\"],"
        + "\"CollPropertyBinary\":[\"q83v\",\"ASNF\",\"VGeJ\"],"
        + "\"CollPropertyDate\":[\"1958-12-03\",\"1999-08-05\",\"2013-06-25\"],"
        + "\"CollPropertyDateTimeOffset\":[\"2015-08-12T03:08:34Z\",\"1970-03-28T12:11:10Z\","
        + "\"1948-02-17T09:09:09Z\"],"
        + "\"CollPropertyDuration\":[\"PT13S\",\"PT5H28M0S\",\"PT1H0S\"],"
        + "\"CollPropertyGuid\":[\"ffffff67-89ab-cdef-0123-456789aaaaaa\",\"eeeeee67-89ab-cdef-0123-456789bbbbbb\","
        + "\"cccccc67-89ab-cdef-0123-456789cccccc\"],"
        + "\"CollPropertyTimeOfDay\":[\"04:14:13\",\"23:59:59\",\"01:12:33\"]"
        + "}";
    Assert.assertEquals(expectedResult, resultString);
  }

  @Test
  public void primitiveCollectionPropertyIEEE754CompatibleInt64() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESCollAllPrim");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType().getProperty("CollPropertyInt64");
    final Property property = data.readAll(edmEntitySet).getEntities().get(0).getProperty(edmProperty.getName());

    final String resultString = IOUtils.toString(serializerIEEECompatible
        .primitiveCollection(metadata, (EdmPrimitiveType) edmProperty.getType(), property,
            PrimitiveSerializerOptions.with()
                .contextURL(ContextURL.with()
                    .entitySet(edmEntitySet).keyPath("1").navOrPropertyPath(edmProperty.getName()).build())
                .build()).getContent());
    Assert.assertEquals("{"
        + "\"@odata.context\":\"$metadata#ESCollAllPrim(1)/CollPropertyInt64\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"value\":[\"929292929292\",\"333333333333\",\"444444444444\"]}",
        resultString);
  }

  @Test
  public void primitiveCollectionPropertyIEEE754CompatibleDecimal() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESCollAllPrim");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType().getProperty("CollPropertyDecimal");
    final Property property = data.readAll(edmEntitySet).getEntities().get(0).getProperty(edmProperty.getName());

    final String resultString = IOUtils.toString(serializerIEEECompatible
        .primitiveCollection(metadata, (EdmPrimitiveType) edmProperty.getType(), property,
            PrimitiveSerializerOptions.with()
                .contextURL(ContextURL.with()
                    .entitySet(edmEntitySet).keyPath("1").navOrPropertyPath(edmProperty.getName()).build())
                .build()).getContent());
    Assert.assertEquals("{"
        + "\"@odata.context\":\"$metadata#ESCollAllPrim(1)/CollPropertyDecimal\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"value\":[\"12\",\"-2\",\"1234\"]}",
        resultString);
  }

  @Test
  public void primitivePropertyIEEE754CompatibleInt64() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType().getProperty("PropertyInt64");
    final Property property = data.readAll(edmEntitySet).getEntities().get(0).getProperty(edmProperty.getName());
    final String resultString = IOUtils.toString(serializerIEEECompatible
        .primitive(metadata, (EdmPrimitiveType) edmProperty.getType(), property,
            PrimitiveSerializerOptions.with()
                .contextURL(ContextURL.with()
                    .entitySet(edmEntitySet).keyPath("32767").navOrPropertyPath(edmProperty.getName()).build())
                .build()).getContent());
    Assert.assertEquals("{"
        + "\"@odata.context\":\"$metadata#ESAllPrim(32767)/PropertyInt64\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"value\":\"" + Long.MAX_VALUE + "\"}",
        resultString);
  }

  @Test
  public void primitivePropertyIEEE754CompatibleDecimal() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    final EdmProperty edmProperty = (EdmProperty) edmEntitySet.getEntityType().getProperty("PropertyDecimal");
    final Property property = data.readAll(edmEntitySet).getEntities().get(0).getProperty(edmProperty.getName());
    final String resultString = IOUtils.toString(serializerIEEECompatible
        .primitive(metadata, (EdmPrimitiveType) edmProperty.getType(), property,
            PrimitiveSerializerOptions.with()
                .contextURL(ContextURL.with()
                    .entitySet(edmEntitySet).keyPath("32767").navOrPropertyPath(edmProperty.getName()).build())
                .build()).getContent());
    Assert.assertEquals("{"
        + "\"@odata.context\":\"$metadata#ESAllPrim(32767)/PropertyDecimal\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"value\":\"34\"}",
        resultString);
  }

  @Test
  public void entitySetAllPrimIEEE754CompatibleCount() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    EntityCollection entitySet = data.readAll(edmEntitySet);
    entitySet.setCount(entitySet.getEntities().size());
    entitySet.setNext(URI.create("/next"));
    CountOption countOption = Mockito.mock(CountOption.class);
    Mockito.when(countOption.getValue()).thenReturn(true);
    InputStream result = serializerIEEECompatible.entityCollection(metadata, edmEntitySet.getEntityType(), entitySet,
        EntityCollectionSerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).build())
            .count(countOption)
            .build()).getContent();
    final String resultString = IOUtils.toString(result);

    Assert.assertThat(resultString, CoreMatchers.startsWith("{"
        + "\"@odata.context\":\"$metadata#ESAllPrim\","
        + "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\","
        + "\"@odata.count\":\"3\",\"value\":["));
    Assert.assertThat(resultString, CoreMatchers.endsWith("],"
        + "\"@odata.nextLink\":\"/next\"}"));

    int count = 0;
    int index = -1;
    while ((index = resultString.indexOf("PropertyInt16\":", ++index)) > 0) {
      count++;
    }
    Assert.assertEquals(3, count);
  }

  @Test
  public void entitySetAllPrimReferenceIEEE754CompatibleCount() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESAllPrim");
    EntityCollection entitySet = data.readAll(edmEntitySet);
    entitySet.setCount(entitySet.getEntities().size());
    entitySet.setNext(URI.create("/next"));
    CountOption countOption = Mockito.mock(CountOption.class);
    Mockito.when(countOption.getValue()).thenReturn(true);
    InputStream result = serializerIEEECompatible.referenceCollection(metadata, edmEntitySet, entitySet,
        ReferenceCollectionSerializerOptions.with()
            .contextURL(ContextURL.with().asCollection().suffix(Suffix.REFERENCE).build())
            .count(countOption)
            .build()).getContent();
    final String resultString = IOUtils.toString(result);

    Assert.assertThat(resultString, CoreMatchers.startsWith("{"
        + "\"@odata.context\":\"$metadata#Collection($ref)\","
        + "\"@odata.count\":\"3\",\"value\":["));
    Assert.assertThat(resultString, CoreMatchers.endsWith("],"
        + "\"@odata.nextLink\":\"/next\"}"));

    int count = 0;
    int index = -1;
    while ((index = resultString.indexOf("ESAllPrim(", ++index)) > 0) {
      count++;
    }
    Assert.assertEquals(3, count);
  }

  @Test
  public void geoPoint() throws Exception {
    final EdmEntityType entityType = mockEntityType(EdmPrimitiveTypeKind.GeometryPoint);
    Entity entity = new Entity()
        .addProperty(new Property(null, entityType.getPropertyNames().get(0), ValueType.GEOSPATIAL,
            createPoint(1.5, 4.25)));
    Assert.assertEquals("{\"" + entityType.getPropertyNames().get(0) + "\":{"
        + "\"type\":\"Point\",\"coordinates\":[1.5,4.25]}}",
        IOUtils.toString(serializerNoMetadata.entity(metadata, entityType, entity, null).getContent()));

    Point point = new Point(Dimension.GEOMETRY, null);
    point.setZ(42);
    entity = new Entity().addProperty(new Property(null, entityType.getPropertyNames().get(0), ValueType.GEOSPATIAL,
        point));
    Assert.assertEquals("{\"" + entityType.getPropertyNames().get(0) + "\":{"
        + "\"type\":\"Point\",\"coordinates\":[0.0,0.0,42.0]}}",
        IOUtils.toString(serializerNoMetadata.entity(metadata, entityType, entity, null).getContent()));
  }

  @Test
  public void geoMultiPoint() throws Exception {
    final EdmEntityType entityType = mockEntityType(EdmPrimitiveTypeKind.GeometryMultiPoint);
    final Entity entity = new Entity()
        .addProperty(new Property(null, entityType.getPropertyNames().get(0), ValueType.GEOSPATIAL,
            new MultiPoint(Dimension.GEOMETRY, null, Arrays.asList(
                createPoint(2.5, 3.125), createPoint(3.5, 4.125), createPoint(4.5, 5.125)))));
    Assert.assertEquals("{\"" + entityType.getPropertyNames().get(0) + "\":{"
        + "\"type\":\"MultiPoint\",\"coordinates\":[[2.5,3.125],[3.5,4.125],[4.5,5.125]]}}",
        IOUtils.toString(serializerNoMetadata.entity(metadata, entityType, entity, null).getContent()));
  }

  @Test
  public void geoLineString() throws Exception {
    final EdmEntityType entityType = mockEntityType(EdmPrimitiveTypeKind.GeometryLineString);
    final Entity entity = new Entity()
        .addProperty(new Property(null, entityType.getPropertyNames().get(0), ValueType.GEOSPATIAL,
            new LineString(Dimension.GEOMETRY, null, Arrays.asList(
                createPoint(1, 1), createPoint(2, 2), createPoint(3, 3), createPoint(4, 4), createPoint(5, 5)))));
    Assert.assertEquals("{\"" + entityType.getPropertyNames().get(0) + "\":{"
        + "\"type\":\"LineString\",\"coordinates\":[[1.0,1.0],[2.0,2.0],[3.0,3.0],[4.0,4.0],[5.0,5.0]]}}",
        IOUtils.toString(serializerNoMetadata.entity(metadata, entityType, entity, null).getContent()));
  }

  @Test
  public void geoMultiLineString() throws Exception {
    final EdmEntityType entityType = mockEntityType(EdmPrimitiveTypeKind.GeometryMultiLineString);
    final Entity entity = new Entity()
        .addProperty(new Property(null, entityType.getPropertyNames().get(0), ValueType.GEOSPATIAL,
            new MultiLineString(Dimension.GEOMETRY, null, Arrays.asList(
                new LineString(Dimension.GEOMETRY, null, Arrays.asList(
                    createPoint(1, 1), createPoint(2, 2), createPoint(3, 3), createPoint(4, 4), createPoint(5, 5))),
                new LineString(Dimension.GEOMETRY, null, Arrays.asList(
                    createPoint(99.5, 101.5), createPoint(150, 151.25)))))));
    Assert.assertEquals("{\"" + entityType.getPropertyNames().get(0) + "\":{"
        + "\"type\":\"MultiLineString\",\"coordinates\":["
        + "[[1.0,1.0],[2.0,2.0],[3.0,3.0],[4.0,4.0],[5.0,5.0]],"
        + "[[99.5,101.5],[150.0,151.25]]]}}",
        IOUtils.toString(serializerNoMetadata.entity(metadata, entityType, entity, null).getContent()));
  }

  @Test
  public void geoPolygon() throws Exception {
    final EdmEntityType entityType = mockEntityType(EdmPrimitiveTypeKind.GeometryPolygon);
    Entity entity = new Entity()
        .addProperty(new Property(null, entityType.getPropertyNames().get(0), ValueType.GEOSPATIAL,
            new Polygon(Dimension.GEOMETRY, null,
                Arrays.asList(
                    createPoint(1, 1), createPoint(1, 2), createPoint(2, 2), createPoint(2, 1), createPoint(1, 1)),
                Arrays.asList(
                    createPoint(0, 0), createPoint(3, 0), createPoint(3, 3), createPoint(0, 3), createPoint(0, 0)))));
    Assert.assertEquals("{\"" + entityType.getPropertyNames().get(0) + "\":{"
        + "\"type\":\"Polygon\",\"coordinates\":[[[0.0,0.0],[3.0,0.0],[3.0,3.0],[0.0,3.0],[0.0,0.0]],"
        + "[[1.0,1.0],[1.0,2.0],[2.0,2.0],[2.0,1.0],[1.0,1.0]]]}}",
        IOUtils.toString(serializerNoMetadata.entity(metadata, entityType, entity, null).getContent()));

    entity = new Entity().addProperty(new Property(null, entityType.getPropertyNames().get(0), ValueType.GEOSPATIAL,
        new Polygon(Dimension.GEOMETRY, null, null, Arrays.asList(
            createPoint(10, 10), createPoint(30, 10), createPoint(30, 30), createPoint(10, 30),
            createPoint(10, 10)))));
    Assert.assertEquals("{\"" + entityType.getPropertyNames().get(0) + "\":{"
        + "\"type\":\"Polygon\",\"coordinates\":[[[10.0,10.0],[30.0,10.0],[30.0,30.0],[10.0,30.0],[10.0,10.0]]]}}",
        IOUtils.toString(serializerNoMetadata.entity(metadata, entityType, entity, null).getContent()));
  }

  @Test
  public void geoMultiPolygon() throws Exception {
    final EdmEntityType entityType = mockEntityType(EdmPrimitiveTypeKind.GeometryMultiPolygon);
    final Entity entity = new Entity()
        .addProperty(new Property(null, entityType.getPropertyNames().get(0), ValueType.GEOSPATIAL,
            new MultiPolygon(Dimension.GEOMETRY, null, Arrays.asList(
                new Polygon(Dimension.GEOMETRY, null,
                    Arrays.asList(
                        createPoint(1, 1), createPoint(1, 2), createPoint(2, 2), createPoint(2, 1), createPoint(1, 1)),
                    Arrays.asList(
                        createPoint(0, 0), createPoint(3, 0), createPoint(3, 3), createPoint(0, 3),
                        createPoint(0, 0))),
                new Polygon(Dimension.GEOMETRY, null,
                    Arrays.asList(
                        createPoint(10, 10), createPoint(10, 20), createPoint(20, 10), createPoint(10, 10)),
                    Arrays.asList(
                        createPoint(0, 0), createPoint(30, 0), createPoint(0, 30), createPoint(0, 0)))))));
    Assert.assertEquals("{\"" + entityType.getPropertyNames().get(0) + "\":{"
        + "\"type\":\"MultiPolygon\",\"coordinates\":["
        + "[[[0.0,0.0],[3.0,0.0],[3.0,3.0],[0.0,3.0],[0.0,0.0]],"
        + "[[1.0,1.0],[1.0,2.0],[2.0,2.0],[2.0,1.0],[1.0,1.0]]],"
        + "[[[0.0,0.0],[30.0,0.0],[0.0,30.0],[0.0,0.0]],"
        + "[[10.0,10.0],[10.0,20.0],[20.0,10.0],[10.0,10.0]]]]}}",
        IOUtils.toString(serializerNoMetadata.entity(metadata, entityType, entity, null).getContent()));
  }

  @Test
  public void geoCollection() throws Exception {
    final EdmEntityType entityType = mockEntityType(EdmPrimitiveTypeKind.GeometryCollection);
    final Entity entity = new Entity()
        .addProperty(new Property(null, entityType.getPropertyNames().get(0), ValueType.GEOSPATIAL,
            new GeospatialCollection(Dimension.GEOMETRY, null, Arrays.asList(
                createPoint(100, 0),
                new LineString(Dimension.GEOMETRY, null, Arrays.asList(createPoint(101, 0), createPoint(102, 1)))))));
    Assert.assertEquals("{\"" + entityType.getPropertyNames().get(0) + "\":{"
        + "\"type\":\"GeometryCollection\",\"geometries\":["
        + "{\"type\":\"Point\",\"coordinates\":[100.0,0.0]},"
        + "{\"type\":\"LineString\",\"coordinates\":[[101.0,0.0],[102.0,1.0]]}]}}",
        IOUtils.toString(serializerNoMetadata.entity(metadata, entityType, entity, null).getContent()));
  }

  private EdmEntityType mockEntityType(final EdmPrimitiveTypeKind type) {
    EdmProperty property = Mockito.mock(EdmProperty.class);
    final String name = "Property" + type.name();
    Mockito.when(property.getName()).thenReturn(name);
    Mockito.when(property.getType()).thenReturn(odata.createPrimitiveTypeInstance(type));
    Mockito.when(property.isPrimitive()).thenReturn(true);
    EdmEntityType entityType = Mockito.mock(EdmEntityType.class);
    Mockito.when(entityType.getPropertyNames()).thenReturn(Arrays.asList(name));
    Mockito.when(entityType.getStructuralProperty(name)).thenReturn(property);
    return entityType;
  }

  @Test
  public void geoNonstandardSRID() throws Exception {
    final EdmEntityType entityType = mockEntityType(EdmPrimitiveTypeKind.GeometryPoint);
    final Entity entity = new Entity()
        .addProperty(new Property(null, entityType.getPropertyNames().get(0), ValueType.GEOSPATIAL,
            new Point(Dimension.GEOMETRY, SRID.valueOf("42"))));
    try {
      serializerNoMetadata.entity(metadata, entityType, entity, null);
      fail("Expected exception not thrown.");
    } catch (final SerializerException e) {
      assertNotNull(e);
    }
  }

  private Point createPoint(final double x, final double y) {
    Point point = new Point(Dimension.GEOMETRY, null);
    point.setX(x);
    point.setY(y);
    return point;
  }

  @Test
  public void expandCycle() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESPeople");
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(1);
    ExpandItem mockExpandItem = ExpandSelectMock.mockExpandItem(edmEntitySet, "friends");
    LevelsExpandOption levels = Mockito.mock(LevelsExpandOption.class);
    Mockito.when(levels.isMax()).thenReturn(Boolean.TRUE);
    Mockito.when(mockExpandItem.getLevelsOption()).thenReturn(levels);
    final ExpandOption expand = ExpandSelectMock.mockExpandOption(Collections.singletonList(mockExpandItem));
    InputStream result = serializer.entity(metadata, edmEntitySet.getEntityType(), entity,
        EntitySerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).suffix(Suffix.ENTITY).build())
            .expand(expand)
            .build())
        .getContent();
    final String resultString = IOUtils.toString(result);
    final String expected = "{" + 
         "\"@odata.context\":\"$metadata#ESPeople/$entity\"," + 
         "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\"," + 
         "\"id\":1," + 
         "\"name\":\"B\"," + 
         "\"friends\":[" + 
               "{" + 
                  "\"id\":0," + 
                  "\"name\":\"A\"," + 
                  "\"friends\":[" + 
                     "{" + 
                        "\"@odata.id\":\"ESPeople(1)\"" + 
                     "}," + 
                     "{" + 
                        "\"id\":2," + 
                        "\"name\":\"C\"," + 
                        "\"friends\":[" + 
                           "{" + 
                              "\"@odata.id\":\"ESPeople(0)\"" + 
                           "}," + 
                           "{" + 
                              "\"id\":3," + 
                              "\"name\":\"D\"," + 
                              "\"friends\":[" + 
                              "]" + 
                           "}" + 
                        "]" + 
                     "}" + 
                  "]" + 
               "}," + 
               "{" + 
                  "\"id\":2," + 
                  "\"name\":\"C\"," + 
                  "\"friends\":[" + 
                     "{" + 
                        "\"id\":0," + 
                        "\"name\":\"A\"," + 
                        "\"friends\":[" + 
                           "{" + 
                              "\"@odata.id\":\"ESPeople(1)\"" + 
                           "}," + 
                           "{" + 
                              "\"@odata.id\":\"ESPeople(2)\"" + 
                           "}" + 
                        "]" + 
                     "}," + 
                     "{" + 
                        "\"id\":3," + 
                        "\"name\":\"D\"," + 
                        "\"friends\":[" + 
                        "]" + 
                     "}" + 
                  "]" + 
               "}" + 
            "]" + 
         "}";
    Assert.assertEquals(expected, resultString);
  }

  @Test
  public void expandCycleWith3Level() throws Exception {
    final EdmEntitySet edmEntitySet = entityContainer.getEntitySet("ESPeople");
    final Entity entity = data.readAll(edmEntitySet).getEntities().get(1);
    ExpandItem mockExpandItem = ExpandSelectMock.mockExpandItem(edmEntitySet, "friends");
    LevelsExpandOption levels = Mockito.mock(LevelsExpandOption.class);
    Mockito.when(levels.isMax()).thenReturn(Boolean.FALSE);
    Mockito.when(levels.getValue()).thenReturn(3);
    Mockito.when(mockExpandItem.getLevelsOption()).thenReturn(levels);
    final ExpandOption expand = ExpandSelectMock.mockExpandOption(Collections.singletonList(mockExpandItem));
    InputStream result = serializer.entity(metadata, edmEntitySet.getEntityType(), entity,
        EntitySerializerOptions.with()
            .contextURL(ContextURL.with().entitySet(edmEntitySet).suffix(Suffix.ENTITY).build())
            .expand(expand)
            .build())
        .getContent();
    final String resultString = IOUtils.toString(result);
    final String expected = "{" +
       "\"@odata.context\":\"$metadata#ESPeople/$entity\"," + 
       "\"@odata.metadataEtag\":\"W/\\\"metadataETag\\\"\"," + 
       "\"id\":1," + 
       "\"name\":\"B\"," + 
       "\"friends\":[" + 
         "{" + 
           "\"id\":0," + 
           "\"name\":\"A\"," + 
           "\"friends\":[" + 
             "{" + 
               "\"@odata.id\":\"ESPeople(1)\"" + 
             "}," + 
             "{" + 
               "\"id\":2," + 
               "\"name\":\"C\"," + 
               "\"friends\":[" + 
                 "{" + 
                   "\"@odata.id\":\"ESPeople(0)\"" + 
                 "}," + 
                 "{" + 
                   "\"id\":3," + 
                   "\"name\":\"D\"" + 
                 "}" + 
               "]" + 
             "}" + 
           "]" + 
         "}," + 
         "{" + 
           "\"id\":2," + 
           "\"name\":\"C\"," + 
           "\"friends\":[" + 
             "{" + 
               "\"id\":0," + 
               "\"name\":\"A\"," + 
               "\"friends\":[" + 
                 "{" + 
                   "\"@odata.id\":\"ESPeople(1)\"" + 
                 "}," + 
                 "{" + 
                   "\"@odata.id\":\"ESPeople(2)\"" + 
                 "}" + 
               "]" + 
             "}," + 
             "{" + 
               "\"id\":3," + 
               "\"name\":\"D\"," + 
               "\"friends\":[" + 
               "]" + 
             "}" + 
           "]" + 
         "}" + 
       "]" + 
       "}"; 
    Assert.assertEquals(expected, resultString);
  }
}
