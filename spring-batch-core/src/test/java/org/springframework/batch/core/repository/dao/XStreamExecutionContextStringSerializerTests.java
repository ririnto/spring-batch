/*
 * Copyright 2008-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.batch.core.repository.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.batch.core.repository.ExecutionContextSerializer;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
import com.thoughtworks.xstream.security.ForbiddenClassException;

/**
 * @author Thomas Risberg
 * @author Michael Minella
 */
public class XStreamExecutionContextStringSerializerTests extends AbstractExecutionContextSerializerTests {

	ExecutionContextSerializer serializer;

	@Before
	public void onSetUp() throws Exception {
		XStreamExecutionContextStringSerializer serializerDeserializer = new XStreamExecutionContextStringSerializer();
		(serializerDeserializer).afterPropertiesSet();

		serializer = serializerDeserializer;
	}

	@Override
	protected ExecutionContextSerializer getSerializer() {
		return this.serializer;
	}

	@Test
	public void testDeserializeContextSerializedByXStream114AndJettison12() throws Exception {
		// fixture produced by XStream 1.4.11.1 + Jettison 1.2, the versions this
		// serializer pinned before the security hardening
		String legacy = "{\"map\":[{\"entry\":[{\"string\":[\"object2\",\"OBJECT TWO\"]},"
				+ "{\"string\":\"object1\",\"long\":12345},"
				+ "{\"string\":\"object4\",\"double\":1234567.1234},"
				+ "{\"string\":\"object3\",\"date\":\"1973-11-29 21:33:10.123 UTC\"}]}]}";

		Map<String, Object> result = serializer.deserialize(new ByteArrayInputStream(legacy.getBytes("ISO-8859-1")));

		assertEquals(Long.valueOf(12345L), result.get("object1"));
		assertEquals("OBJECT TWO", result.get("object2"));
		assertEquals(new Date(123456790123L), result.get("object3"));
		assertEquals(Double.valueOf(1234567.1234D), result.get("object4"));
	}

	@Test
	public void testDeserializeRejectsUntrustedType() throws Exception {
		// payload shaped exactly like a serializer-produced context but planted
		// with an arbitrary gadget type by an actor with write access to the
		// job metadata database
		XStream unrestricted = new XStream(new JettisonMappedXmlDriver());
		Map<String, Object> planted = new HashMap<String, Object>();
		planted.put("gadget", new ProcessBuilder("echo", "boom"));
		String payload = unrestricted.toXML(planted);

		try {
			serializer.deserialize(new ByteArrayInputStream(payload.getBytes("ISO-8859-1")));
			fail("A ForbiddenClassException should have been thrown but wasn't");
		}
		catch (ForbiddenClassException e) {
			// expected
		}
	}
}
