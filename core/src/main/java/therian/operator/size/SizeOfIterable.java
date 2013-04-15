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
package therian.operator.size;

import org.apache.commons.functor.UnaryProcedure;
import org.apache.commons.lang3.reflect.TypeUtils;

import therian.Operator;
import therian.TherianContext;
import therian.operation.Size;
import therian.position.Ref;

/**
 * {@link Operator} to take the size of an {@link Iterable}.
 */
public class SizeOfIterable implements Operator<Size<Iterable<?>>> {

    public void perform(final Size<Iterable<?>> operation) {
        final Iterable<?> value = operation.getPosition().getValue();
        if (value == null) {
            operation.setSuccessful(true);
            operation.setResult(0);
        } else {
            TherianContext.getRequiredInstance().forwardTo(Size.of(Ref.to(value.iterator())),
                new UnaryProcedure<Integer>() {
                    public void run(Integer obj) {
                        operation.setResult(obj);
                    }
                });
        }
    }

    public boolean supports(Size<Iterable<?>> operation) {
        return TypeUtils.isAssignable(operation.getPosition().getType(), Iterable.class);
    }

}