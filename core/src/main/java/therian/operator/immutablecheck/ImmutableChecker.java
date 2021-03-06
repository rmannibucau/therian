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
package therian.operator.immutablecheck;

import therian.Operator;
import therian.TherianContext;
import therian.operation.ImmutableCheck;
import therian.operator.OptimisticOperatorBase;

/**
 * {@link ImmutableCheck} {@link Operator}.
 */
public abstract class ImmutableChecker extends OptimisticOperatorBase<ImmutableCheck<?>> {
    @Override
    public final boolean perform(TherianContext context, ImmutableCheck<?> immutableCheck) {
        return isImmutable(immutableCheck.getPosition().getValue());
    }

    protected abstract boolean isImmutable(Object object);
}
