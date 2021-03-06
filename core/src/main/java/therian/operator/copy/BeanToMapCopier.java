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

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.reflect.TypeUtils;

import therian.Operator.DependsOn;
import therian.TherianContext;
import therian.operation.Convert;
import therian.operation.Copy;
import therian.operator.convert.NOPConverter;
import therian.position.Position;
import therian.position.relative.Keyed;
import therian.position.relative.Property;
import therian.util.BeanProperties;
import therian.util.Positions;

/**
 * Copy a bean's properties into a {@link Map}.
 */
@SuppressWarnings("rawtypes")
@DependsOn({ NOPConverter.class, ConvertingCopier.class })
public class BeanToMapCopier extends Copier<Object, Map> {

    public static final String IGNORE_CLASS_PROPERTY = "class";

    private final Predicate<String> ignored = this::isIgnored;

    protected boolean isIgnored(String propertyName) {
        return IGNORE_CLASS_PROPERTY.equals(propertyName);
    }

    @Override
    public boolean perform(TherianContext context, Copy<? extends Object, ? extends Map> copy) {
        final Type targetKeyType = getKeyType(copy.getTargetPosition());

        final Position.ReadWrite<?> targetKey = Positions.readWrite(targetKeyType);

        @SuppressWarnings("unchecked")
        final Stream<Copy<?, ?>> copyEntries =
            getProperties(context, copy.getSourcePosition()).<Copy<?, ?>> map(
                propertyName -> {
                    final Convert<String, ?> convertKey = Convert.to(targetKey, Positions.readOnly(propertyName));
                    if (!context.supports(convertKey)) {
                        return null;
                    }
                    final Object key = context.eval(convertKey);
                    final Copy<?, ?> copyEntry =
                        Copy.Safely.to(Keyed.value().at(key).of(copy.getTargetPosition()), Property.at(propertyName)
                            .of(copy.getSourcePosition()));
                    return copyEntry;
                }).filter(Objects::nonNull);

        return copyEntries.map(copyEntry -> Boolean.valueOf(context.evalSuccess(copyEntry)))
            .collect(Collectors.toSet()).contains(Boolean.TRUE);
    }

    /**
     * If at least one property name can be converted to an assignable key, say the operation is supported and we'll
     * give it a shot.
     */
    @Override
    public boolean supports(TherianContext context, Copy<? extends Object, ? extends Map> copy) {
        if (!super.supports(context, copy)) {
            return false;
        }

        final Type targetKeyType = getKeyType(copy.getTargetPosition());

        final Position.ReadWrite<?> targetKey = Positions.readWrite(targetKeyType);

        return getProperties(context, copy.getSourcePosition())
            .filter(propertyName -> context.supports(Convert.to(targetKey, Positions.readOnly(propertyName))))
            .findFirst().isPresent();
    }

    private Stream<String> getProperties(TherianContext context, Position.Readable<?> source) {
        return BeanProperties.getPropertyNames(context, source).stream().filter(ignored.negate());
    }

    private Type getKeyType(Position<? extends Map> target) {
        return ObjectUtils.defaultIfNull(
            TypeUtils.unrollVariables(TypeUtils.getTypeArguments(target.getType(), Map.class),
                Map.class.getTypeParameters()[0]), Object.class);
    }
}
