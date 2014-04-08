/*
 *  Copyright the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package therian.operator.copy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;

import therian.OperationException;
import therian.OperatorDefinitionException;
import therian.TherianContext;
import therian.Operator.DependsOn;
import therian.TherianContext.Hint;
import therian.operation.Copy;
import therian.operator.convert.DefaultCopyingConverter;
import therian.operator.convert.ELCoercionConverter;
import therian.operator.convert.NOPConverter;
import therian.position.Position;
import therian.position.relative.Property;
import therian.util.BeanProperties;
import therian.util.BeanProperties.ReturnProperties;

/**
 * Copies based on annotated property mapping. Concrete subclasses must specify one or both of {@link Mapping} and
 * {@link Matching}.
 *
 * @param <SOURCE>
 * @param <TARGET>
 */
@DependsOn({ ConvertingCopier.class, NOPConverter.class, ELCoercionConverter.class, DefaultCopyingConverter.class })
public abstract class PropertyCopier<SOURCE, TARGET> extends Copier<SOURCE, TARGET> {
    /**
     * Configures a {@link PropertyCopier} subclass for property mapping, using a fluent syntax of
     * "mapping: value from foo to bar, value from x to y, etc.".
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Mapping {
        /**
         * Specifies mapping for a property value.
         */
        public @interface Value {
            /**
             * Property name; implies source {@link Position}.
             */
            String from() default "";

            /**
             * Property name; blank implies target {@link Position}.
             */
            String to() default "";
        }

        /**
         * Mapping {@link Value}s
         */
        Value[] value();
    }

    /**
     * Configures a {@link PropertyCopier} subclass for property matching.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Matching {
        /**
         * Property names to match. An empty array implies all matching properties that are readable on the source and
         * writable on the target.
         */
        String[] value() default {};

        /**
         * Property names to exclude from matching.
         */
        String[] exclude() default {};
    }

    /**
     * Describes the {@link PropertyCopier}'s behavior when presented with a {@code null} source value.
     *
     * @since 0.2
     */
    public enum NullBehavior implements Hint {
        /**
         * Indicates that the operation will be unsupported.
         */
        UNSUPPORTED,

        /**
         * Indicates that the operation will be supported, but will do nothing.
         */
        NOOP,

        /**
         * Indicates that the operation will be implemented by writing(/converting) {@code null} values to target
         * properties.
         */
        SET_NULLS;

        @Override
        public Class<? extends Hint> getType() {
            return NullBehavior.class;
        }
    }

    private final List<Pair<Property.PositionFactory<?>, Property.PositionFactory<?>>> mappings;
    private final Matching matching;

    {
        final List<Pair<Property.PositionFactory<?>, Property.PositionFactory<?>>> m =
            new ArrayList<Pair<Property.PositionFactory<?>, Property.PositionFactory<?>>>();

        try {
            @SuppressWarnings("rawtypes")
            final Class<? extends PropertyCopier> c = getClass();
            final Mapping mapping = c.getAnnotation(Mapping.class);
            if (mapping == null) {
                Validate.validState(c.isAnnotationPresent(Matching.class),
                    "%s specifies neither @Mapping nor @Matching", c);
                mappings = Collections.emptyList();
            } else {
                Validate.validState(mapping.value().length > 0, "@Mapping cannot be empty");

                for (Mapping.Value v : mapping.value()) {
                    final String from = StringUtils.trimToNull(v.from());
                    final String to = StringUtils.trimToNull(v.to());

                    Validate.validState(from != null || to != null,
                        "both from and to cannot be blank/empty for a single @Mapping.Value");

                    final Property.PositionFactory<?> target = to == null ? null : Property.at(to);
                    final Property.PositionFactory<?> source = from == null ? null : Property.optional(from);

                    m.add(Pair.<Property.PositionFactory<?>, Property.PositionFactory<?>> of(source, target));
                }
                mappings = Collections.unmodifiableList(m);
            }
            matching = c.getAnnotation(Matching.class);
        } catch (Exception e) {
            throw new OperatorDefinitionException(this, e);
        }
    }

    @Override
    public boolean perform(TherianContext context, Copy<? extends SOURCE, ? extends TARGET> copy) {
        final NullBehavior nullBehavior = copy.getSourcePosition().getValue() == null ? getNullBehavior(context) : null;
        if (nullBehavior == NullBehavior.UNSUPPORTED) {
            return false;
        }
        final Iterable<Copy<?, ?>> mapped = map(context, copy);
        if (nullBehavior == NullBehavior.NOOP && mapped.iterator().hasNext()) {
            return true;
        }
        final Iterable<Copy<?, ?>> matched = match(context, copy);
        if (nullBehavior == NullBehavior.NOOP && matched.iterator().hasNext()) {
            return true;
        }

        boolean result = handle(context, Phase.EVALUATION, mapped);
        result = handle(context, Phase.EVALUATION, matched) || result;
        return result;
    }

    @Override
    public boolean supports(TherianContext context, Copy<? extends SOURCE, ? extends TARGET> copy) {
        if (!super.supports(context, copy)) {
            return false;
        }
        final NullBehavior nullBehavior = copy.getSourcePosition().getValue() == null ? getNullBehavior(context) : null;
        if (nullBehavior == NullBehavior.UNSUPPORTED) {
            return false;
        }

        return handle(context, Phase.SUPPORT_CHECK, map(context, copy))
            || handle(context, Phase.SUPPORT_CHECK, match(context, copy));
    }

    private boolean handle(TherianContext context, Phase phase, Iterable<Copy<?, ?>> nestedOperations) {
        boolean result = false;
        for (Copy<?, ?> nestedCopy : nestedOperations) {
            switch (phase) {
            case SUPPORT_CHECK:
                // safe copies have already been verified supported:
                if (!(nestedCopy instanceof Copy.Safely || context.supports(nestedCopy))) {
                    return false;
                }
                result = true;
                break;
            case EVALUATION:
                if (!context.evalSuccess(nestedCopy)) {
                    throw new OperationException(nestedCopy);
                }
                result = true;
                break;
            default:
                throw new IllegalArgumentException("phase " + phase);
            }
        }
        return result;
    }

    protected NullBehavior defaultNullBehavior() {
        return NullBehavior.NOOP;
    }

    private NullBehavior getNullBehavior(TherianContext context) {
        final NullBehavior nullBehavior = context.getTypedContext(NullBehavior.class);
        return nullBehavior == null ? defaultNullBehavior() : nullBehavior;
    }

    private Position.Readable<?> dereference(Property.PositionFactory<?> positionFactory,
        Position.Readable<?> parentPosition) {
        return positionFactory == null ? parentPosition : positionFactory.of(parentPosition);
    }

    private Iterable<Copy<?, ?>> map(final TherianContext context, Copy<? extends SOURCE, ? extends TARGET> copy) {
        final List<Copy<?, ?>> result = new ArrayList<Copy<?, ?>>();

        for (Pair<Property.PositionFactory<?>, Property.PositionFactory<?>> mapping : mappings) {
            final Position.Readable<?> propertySource = dereference(mapping.getLeft(), copy.getSourcePosition());
            final Position.Readable<?> propertyTarget = dereference(mapping.getRight(), copy.getTargetPosition());
            result.add(Copy.to(propertyTarget, propertySource));
        }
        return result;
    }

    private Iterable<Copy<?, ?>> match(final TherianContext context, Copy<? extends SOURCE, ? extends TARGET> copy) {
        if (matching == null) {
            return Collections.emptySet();
        }
        final Set<String> properties = new HashSet<String>(Arrays.asList(matching.value()));

        // if no properties were explicitly specified for matching, take whatever we can get
        final boolean lenient = properties.isEmpty();
        if (lenient) {
            properties.addAll(BeanProperties.getPropertyNames(ReturnProperties.WRITABLE, context,
                copy.getTargetPosition()));
            properties.retainAll(BeanProperties.getPropertyNames(ReturnProperties.ALL, context,
                copy.getSourcePosition()));
        }
        properties.removeAll(Arrays.asList(matching.exclude()));

        final List<Copy<?, ?>> result = new ArrayList<Copy<?, ?>>();
        for (String property : properties) {
            final Position.ReadWrite<?> target = Property.at(property).of(copy.getTargetPosition());
            final Position.ReadWrite<?> source = Property.optional(property).of(copy.getSourcePosition());

            if (lenient) {
                final Copy<?, ?> propertyCopy = Copy.Safely.to(target, source);
                if (context.supports(propertyCopy)) {
                    result.add(propertyCopy);
                }
            } else {
                result.add(Copy.to(target, source));
            }
        }
        return result;
    }
}
