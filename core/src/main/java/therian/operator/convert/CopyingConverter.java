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
package therian.operator.convert;

import java.lang.reflect.Constructor;
import java.lang.reflect.Type;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.TypeUtils;

import therian.Operation;
import therian.TherianContext;
import therian.TypeLiteral;
import therian.operation.Convert;
import therian.operation.Copy;
import therian.position.Box;
import therian.position.Position;

/**
 * Abstract base class for a converter that defers its work to a {@link Copy} {@link Operation}.
 */
public abstract class CopyingConverter<SOURCE, TARGET> extends Converter<SOURCE, TARGET> {

    /**
     * Intermediate step in fluent interface.
     * 
     * @param <TARGET>
     */
    public static class Implementing<TARGET> {
        private final Type targetType;

        private Implementing(Type targetType) {
            super();
            this.targetType = targetType;
        }

        public <C extends TARGET> CopyingConverter<Object, TARGET> with(Class<C> concreteType) {
            return new Fluent<TARGET>(targetType, requireDefaultConstructor(concreteType)) {};
        }
    }

    private static abstract class Fluent<TARGET> extends CopyingConverter<Object, TARGET> {
        private final Type targetType;
        private final Constructor<? extends TARGET> constructor;

        protected Fluent(Type targetType, Constructor<? extends TARGET> constructor) {
            super();
            this.targetType = targetType;
            this.constructor = constructor;
        }

        @Override
        protected TARGET createCopyDestination(Position.Readable<? extends Object> readable) throws Exception {
            return constructor.newInstance();
        }

        @Override
        public boolean supports(Convert<? extends Object, ? super TARGET> convert) {
            return super.supports(convert) && TypeUtils.isAssignable(targetType, convert.getTargetPosition().getType());
        }
    }

    public final void perform(final Convert<? extends SOURCE, ? super TARGET> convert) {
        final TARGET target;
        try {
            target = createCopyDestination(convert.getSourcePosition());
            // make result available to any concurrent equivalent conversions:
            convert.getTargetPosition().setValue(target);
        } catch (Exception e) {
            return;
        }
        final Position.Readable<TARGET> targetPosition = new Position.Readable<TARGET>() {

            public Type getType() {
                return convert.getTargetPosition().getType();
            }

            public TARGET getValue() {
                return target;
            }

        };
        TherianContext.getRequiredInstance().forwardTo(Copy.to(targetPosition, convert.getSourcePosition()));
    }

    @Override
    public boolean supports(Convert<? extends SOURCE, ? super TARGET> convert) {
        return super.supports(convert)
            && TherianContext.getInstance().supports(
                Copy.to(new Box<TARGET>(convert.getTargetPosition().getType()), convert.getSourcePosition()));
    }

    /**
     * Create copy destination object from source position.
     * 
     * @param readable object
     * @return TARGET
     */
    protected abstract TARGET createCopyDestination(Position.Readable<? extends SOURCE> readable) throws Exception;

    private static <T> Constructor<T> requireDefaultConstructor(Class<T> type) {
        return Validate.notNull(ConstructorUtils.getAccessibleConstructor(type),
            "Could not find default constructor for %s", type);
    }

    /**
     * Create a {@link CopyingConverter} instance that instantiates the target type using the default constructor.
     * 
     * @param targetType which must have an accessible no-arg constructor
     * @param <TARGET>
     * @return CopyingConverter instance
     */
    public static <TARGET> CopyingConverter<Object, TARGET> forTargetType(Class<TARGET> targetType) {
        return new Fluent<TARGET>(targetType, requireDefaultConstructor(targetType)) {};
    }

    /**
     * Intermediate step to create a {@link CopyingConverter} instance that instantiates the (most likely abstract)
     * target type using the default constructor of a specific implementation.
     * 
     * @param targetType
     * @return {@link Implementing} step
     */
    public static <TARGET> Implementing<TARGET> implementing(Class<TARGET> targetType) {
        return new Implementing<TARGET>(targetType);
    }

    /**
     * Intermediate step to create a {@link CopyingConverter} instance that instantiates the (most likely abstract)
     * target type using the default constructor of a specific implementation.
     * 
     * @param targetType
     * @return {@link Implementing} step
     */
    public static <TARGET> Implementing<TARGET> implementing(TypeLiteral<TARGET> targetType) {
        return new Implementing<TARGET>(targetType.value);
    }
}
