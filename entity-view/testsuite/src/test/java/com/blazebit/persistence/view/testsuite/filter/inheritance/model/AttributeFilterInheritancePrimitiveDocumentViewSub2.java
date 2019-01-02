/*
 * Copyright 2014 - 2019 Blazebit.
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

package com.blazebit.persistence.view.testsuite.filter.inheritance.model;

import com.blazebit.persistence.testsuite.entity.PrimitiveDocument;
import com.blazebit.persistence.view.AttributeFilter;
import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.EntityViewInheritanceMapping;
import com.blazebit.persistence.view.Mapping;
import com.blazebit.persistence.view.filter.EqualFilter;

/**
 * @author Christian Beikov
 * @since 1.3.0
 */
@EntityView(PrimitiveDocument.class)
@EntityViewInheritanceMapping("name = 'doc2'")
public interface AttributeFilterInheritancePrimitiveDocumentViewSub2 extends AttributeFilterInheritancePrimitiveDocumentView {

    @Mapping("UPPER(owner.name)")
    @AttributeFilter(EqualFilter.class)
    public String getName();
}
