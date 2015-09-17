/*
 * Copyright 2015 Blazebit.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.blazebit.persistence.impl.function.datediff.hour;

import com.blazebit.persistence.spi.FunctionRenderContext;

/**
 *
 * @author Christian Beikov
 * @since 1.0
 */
public class DB2HourDiffFunction extends HourDiffFunction {

    public DB2HourDiffFunction() {
        super("select (days(t1) - days(t2)) * 14 + coalesce((nullif(midnight_seconds(t1) - midnight_seconds(t2),0)) / 1440, 0) from (values (?1,?2)) as temp(t1,t2)");
    }

    @Override
    protected void renderDiff(FunctionRenderContext context) {
        renderer.start(context).addArgument(0).addArgument(1).build();
    }
}