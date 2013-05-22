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
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;

import therian.OperationException;
import therian.OperatorDefinitionException;
import therian.TherianContext;
import therian.operation.Copy;
import therian.position.Position;
import therian.position.relative.Property;

/**
 * Copies based on annotated property mapping.
 *
 * @param <SOURCE>
 * @param <TARGET>
 */
public abstract class PropertyCopier<SOURCE, TARGET> extends Copier<SOURCE, TARGET> {
    /**
     * Required on {@link PropertyCopier} subclasses.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Mapping {
        /**
         * Blank value implies the position itself.
         */
        public @interface Value {
            String from() default "";

            String to() default "";
        }

        Value[] value();
    }

    private final List<Pair<Property.PositionFactory<?>, Property.PositionFactory<?>>> mappings;

    {
        final List<Pair<Property.PositionFactory<?>, Property.PositionFactory<?>>> m =
            new ArrayList<Pair<Property.PositionFactory<?>, Property.PositionFactory<?>>>();

        try {
            @SuppressWarnings("rawtypes")
            final Class<? extends PropertyCopier> c = getClass();
            final Mapping mapping = c.getAnnotation(Mapping.class);
            Validate.validState(mapping != null, "no @Mapping defined for %s", c);
            Validate.validState(mapping.value().length > 0, "@Mapping cannot be empty");

            for (Mapping.Value v : mapping.value()) {
                final String from = StringUtils.trimToNull(v.from());
                final String to = StringUtils.trimToNull(v.to());

                Validate.validState(from != null || to != null,
                    "both from and to cannot be blank/empty for a single @Mapping.Value");

                final Property.PositionFactory<?> source = from == null ? null : Property.at(from);
                final Property.PositionFactory<?> target = to == null ? null : Property.at(to);

                m.add(Pair.<Property.PositionFactory<?>, Property.PositionFactory<?>> of(source, target));
            }
            mappings = Collections.unmodifiableList(m);
        } catch (Exception e) {
            throw new OperatorDefinitionException(this, e);
        }
    }

    public boolean perform(TherianContext context, Copy<? extends SOURCE, ? extends TARGET> copy) {
        for (Pair<Property.PositionFactory<?>, Property.PositionFactory<?>> mapping : mappings) {
            final Position.Readable<?> source = dereference(mapping.getLeft(), copy.getSourcePosition());
            final Position.Readable<?> target = dereference(mapping.getRight(), copy.getTargetPosition());
            final Copy<?, ?> nested = Copy.to(target, source);
            if (!context.evalSuccess(nested)) {
                throw new OperationException(copy, "nested %s was unsuccessful", nested);
            }
        }
        return true;
    }

    @Override
    public boolean supports(TherianContext context, Copy<? extends SOURCE, ? extends TARGET> copy) {
        if (!super.supports(context, copy)) {
            return false;
        }
        for (Pair<Property.PositionFactory<?>, Property.PositionFactory<?>> mapping : mappings) {
            final Position.Readable<?> source = dereference(mapping.getLeft(), copy.getSourcePosition());
            final Position.Readable<?> target = dereference(mapping.getRight(), copy.getTargetPosition());
            final Copy<?, ?> nested = Copy.to(target, source);
            if (!context.supports(nested)) {
                return false;
            }
        }
        return true;
    }

    private Position.Readable<?> dereference(Property.PositionFactory<?> positionFactory,
        Position.Readable<?> parentPosition) {
        return positionFactory == null ? parentPosition : positionFactory.of(parentPosition);
    }

}
