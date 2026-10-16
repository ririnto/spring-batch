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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JacksonAnnotation;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;

import org.springframework.batch.core.repository.ExecutionContextSerializer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;

/**
 * Implementation that uses Jackson2 to provide (de)serialization.
 *
 * Deserialization trusts only the classes listed in
 * {@link TrustedTypeIdResolver#TRUSTED_CLASS_NAMES}, classes with an explicit
 * mixin and classes annotated with a Jackson annotation. Types outside that
 * allowlist, for instance a gadget type planted in the stored context by an
 * actor with write access to the job metadata database, are rejected.
 *
 * @author Marten Deinum
 * @since 3.0.7
 *
 * @see ExecutionContextSerializer
 */
public class Jackson2ExecutionContextStringSerializer implements ExecutionContextSerializer {

    private ObjectMapper objectMapper;

    public Jackson2ExecutionContextStringSerializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(MapperFeature.DEFAULT_VIEW_INCLUSION, false);
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        this.objectMapper.configure(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES, true);
        this.objectMapper.setDefaultTyping(createTrustedDefaultTyping());
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        Assert.notNull(objectMapper, "ObjectMapper must not be null");
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> deserialize(InputStream in) throws IOException {

        TypeReference<HashMap<String,Object>> typeRef = new TypeReference<HashMap<String,Object>>() {};
        return objectMapper.readValue(in, typeRef);
    }

    public void serialize(Map<String, Object> context, OutputStream out) throws IOException {

        Assert.notNull(context);
        Assert.notNull(out);

        objectMapper.writeValue(out, context);
    }

    private static TypeResolverBuilder<? extends TypeResolverBuilder> createTrustedDefaultTyping() {
        // OBJECT_AND_NON_CONCRETE with WRAPPER_ARRAY reproduces the wire format
        // of the previous enableDefaultTyping() setup, so contexts stored by
        // earlier 3.0.x releases stay readable and newly written ones unchanged
        TypeResolverBuilder<? extends TypeResolverBuilder> result =
                new TrustedTypeResolverBuilder(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE);
        result = result.init(JsonTypeInfo.Id.CLASS, null);
        result = result.inclusion(JsonTypeInfo.As.WRAPPER_ARRAY);
        return result;
    }

    static class TrustedTypeResolverBuilder extends ObjectMapper.DefaultTypeResolverBuilder {

        TrustedTypeResolverBuilder(ObjectMapper.DefaultTyping defaultTyping) {
            super(
                    defaultTyping,
                    // we do explicit validation in the TypeIdResolver
                    BasicPolymorphicTypeValidator.builder()
                            .allowIfSubType(Object.class)
                            .build()
            );
        }

        @Override
        protected TypeIdResolver idResolver(MapperConfig<?> config,
                                            JavaType baseType,
                                            PolymorphicTypeValidator subtypeValidator,
                                            Collection<NamedType> subtypes, boolean forSer, boolean forDeser) {
            TypeIdResolver result =
                    super.idResolver(config, baseType, subtypeValidator, subtypes, forSer, forDeser);
            return new TrustedTypeIdResolver(result);
        }
    }

    static class TrustedTypeIdResolver implements TypeIdResolver {

        private static final Set<String> TRUSTED_CLASS_NAMES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
                "java.util.ArrayList",
                "java.util.LinkedList",
                "java.util.Collections$EmptyList",
                "java.util.Collections$EmptyMap",
                "java.util.Collections$EmptySet",
                "java.util.Collections$UnmodifiableRandomAccessList",
                "java.util.Collections$UnmodifiableList",
                "java.util.Collections$UnmodifiableMap",
                "java.util.Collections$UnmodifiableSet",
                "java.util.Collections$SingletonList",
                "java.util.Collections$SingletonMap",
                "java.util.Collections$SingletonSet",
                "java.util.Date",
                "java.time.Instant",
                "java.time.Duration",
                "java.time.LocalDate",
                "java.time.LocalTime",
                "java.time.LocalDateTime",
                "java.net.URL",
                "java.util.TreeMap",
                "java.util.HashMap",
                "java.util.LinkedHashMap",
                "java.util.TreeSet",
                "java.util.HashSet",
                "java.util.LinkedHashSet",
                "java.lang.Boolean",
                "java.lang.Byte",
                "java.lang.Short",
                "java.lang.Integer",
                "java.lang.Long",
                "java.lang.Double",
                "java.lang.Float",
                "java.math.BigDecimal",
                "java.math.BigInteger",
                "java.lang.String",
                "java.lang.Character",
                "java.lang.CharSequence",
                "java.util.Properties",
                "[Ljava.util.Properties;",
                "org.springframework.batch.core.JobParameter",
                "org.springframework.batch.core.JobParameters",
                "org.springframework.batch.core.jsr.partition.JsrPartitionHandler$PartitionPlanState"
        )));

        private final TypeIdResolver delegate;

        TrustedTypeIdResolver(TypeIdResolver delegate) {
            this.delegate = delegate;
        }

        @Override
        public void init(JavaType baseType) {
            delegate.init(baseType);
        }

        @Override
        public String idFromValue(Object value) {
            return delegate.idFromValue(value);
        }

        @Override
        public String idFromValueAndType(Object value, Class<?> suggestedType) {
            return delegate.idFromValueAndType(value, suggestedType);
        }

        @Override
        public String idFromBaseType() {
            return delegate.idFromBaseType();
        }

        @Override
        public JavaType typeFromId(DatabindContext context, String id) throws IOException {
            DeserializationConfig config = (DeserializationConfig) context.getConfig();
            JavaType result = delegate.typeFromId(context, id);
            String className = result.getRawClass().getName();
            if (isTrusted(className)) {
                return result;
            }
            boolean isExplicitMixin = config.findMixInClassFor(result.getRawClass()) != null;
            if (isExplicitMixin) {
                return result;
            }
            Class<?> rawClass = result.getRawClass();
            JacksonAnnotation jacksonAnnotation =
                    AnnotationUtils.findAnnotation(rawClass, JacksonAnnotation.class);
            if (jacksonAnnotation != null) {
                return result;
            }
            throw new IllegalArgumentException("The class with " + id + " and name of " + className + " is not trusted. " +
                    "If you believe this class is safe to deserialize, please provide an explicit mapping using Jackson " +
                    "annotations or a custom ObjectMapper. " +
                    "If the serialization is only done by a trusted source, you can also enable default typing.");
        }

        private boolean isTrusted(String id) {
            return TRUSTED_CLASS_NAMES.contains(id);
        }

        @Override
        public String getDescForKnownTypeIds() {
            return delegate.getDescForKnownTypeIds();
        }

        @Override
        public JsonTypeInfo.Id getMechanism() {
            return delegate.getMechanism();
        }
    }
}
