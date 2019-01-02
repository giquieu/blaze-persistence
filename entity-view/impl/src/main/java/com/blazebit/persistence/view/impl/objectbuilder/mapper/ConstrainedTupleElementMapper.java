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

package com.blazebit.persistence.view.impl.objectbuilder.mapper;

import com.blazebit.persistence.CaseWhenStarterBuilder;
import com.blazebit.persistence.FullQueryBuilder;
import com.blazebit.persistence.MultipleSubqueryInitiator;
import com.blazebit.persistence.ParameterHolder;
import com.blazebit.persistence.SelectBuilder;
import com.blazebit.persistence.SimpleCaseWhenStarterBuilder;
import com.blazebit.persistence.SubqueryBuilder;
import com.blazebit.persistence.SubqueryInitiator;
import com.blazebit.persistence.view.impl.macro.EmbeddingViewJpqlMacro;
import com.blazebit.persistence.view.impl.objectbuilder.transformator.TupleTransformatorFactory;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 * @author Christian Beikov
 * @since 1.2.0
 */
public class ConstrainedTupleElementMapper implements AliasedTupleElementMapper {

    private final Map.Entry<String, TupleElementMapper>[] mappers;
    private final Map.Entry<String, TupleElementMapper>[] subqueryMappers;
    private final String alias;

    @SuppressWarnings("unchecked")
    private ConstrainedTupleElementMapper(List<Map.Entry<String, TupleElementMapper>> mappers, List<Map.Entry<String, TupleElementMapper>> subqueryMappers, String alias) {
        this.mappers = mappers.toArray(new Map.Entry[mappers.size()]);
        this.subqueryMappers = subqueryMappers.toArray(new Map.Entry[subqueryMappers.size()]);
        this.alias = alias;
    }

    @Override
    public String getAlias() {
        return alias;
    }

    @Override
    public void applyMapping(SelectBuilder<?> queryBuilder, ParameterHolder<?> parameterHolder, Map<String, Object> optionalParameters, EmbeddingViewJpqlMacro embeddingViewJpqlMacro) {
        StringBuilder sb = new StringBuilder();
        FullQueryBuilder<?, ?> fullQueryBuilder;
        if (queryBuilder instanceof ConstrainedSelectBuilder) {
            fullQueryBuilder = ((ConstrainedSelectBuilder) queryBuilder).getQueryBuilder();
        } else {
            fullQueryBuilder = (FullQueryBuilder<?, ?>) queryBuilder;
        }
        StringBuilderSelectBuilder selectBuilder = new StringBuilderSelectBuilder(sb, fullQueryBuilder);

        sb.append("CASE");
        for (Map.Entry<String, TupleElementMapper> entry : mappers) {
            if (entry.getKey() != null) {
                sb.append(" WHEN ");
                sb.append(entry.getKey());
                sb.append(" THEN ");
            } else {
                sb.append(" ELSE ");
            }
            entry.getValue().applyMapping(selectBuilder, parameterHolder, optionalParameters, embeddingViewJpqlMacro);
        }
        sb.append(" END");

        if (subqueryMappers.length == 0) {
            if (alias != null) {
                queryBuilder.select(sb.toString(), alias);
            } else {
                queryBuilder.select(sb.toString());
            }
        } else {
            MultipleSubqueryInitiator<?> initiator;
            if (alias != null) {
                initiator = queryBuilder.selectSubqueries(sb.toString(), alias);
            } else {
                initiator = queryBuilder.selectSubqueries(sb.toString());
            }

            for (Map.Entry<String, TupleElementMapper> entry : subqueryMappers) {
                selectBuilder.setInitiator(initiator.with(entry.getKey()));
                entry.getValue().applyMapping(selectBuilder, parameterHolder, optionalParameters, embeddingViewJpqlMacro);
            }

            initiator.end();
        }
    }

    public static void addMappers(int classMappingIndex, List<TupleElementMapper> mappingList, List<String> parameterMappingList, TupleTransformatorFactory tupleTransformatorFactory, List<ConstrainedTupleElementMapperBuilder> builders) {
        List<List<Map.Entry<String, TupleElementMapper>>> subtypeMappersPerAttribute = new ArrayList<>();
        Map<Integer, Object> consumableIndexes = new TreeMap<>();
        // Transpose the subtype grouped attribute lists to attribute grouped subtype lists
        for (ConstrainedTupleElementMapperBuilder builderEntry : builders) {
            String constraint = builderEntry.constraint;
            TupleElementMapperBuilder mapperBuilder = builderEntry.tupleElementMapperBuilder;

            tupleTransformatorFactory.add(consumableIndexes, classMappingIndex, builderEntry.subtypeIndexes, mapperBuilder.getTupleTransformatorFactory());

            int attributeIndex = 0;
            for (TupleElementMapper mapper : mapperBuilder.getMappers()) {
                List<Map.Entry<String, TupleElementMapper>> list = subtypeMappersPerAttribute.size() > attributeIndex ? subtypeMappersPerAttribute.get(attributeIndex) : null;
                if (list == null) {
                    list = new ArrayList<>();
                    subtypeMappersPerAttribute.add(attributeIndex, list);
                }

                list.add(new AbstractMap.SimpleEntry<>(constraint, mapper));
                attributeIndex++;
            }
        }

        // Add a transformer that consumes all the tuple indexes that need to be consumed to reduce tuples
        consumableIndexes.remove(-1);
        final int[] consumableIndexArray = new int[consumableIndexes.size()];
        int i = 0;
        for (Iterator<Integer> iter = consumableIndexes.keySet().iterator(); iter.hasNext(); i++) {
            consumableIndexArray[i] = iter.next();
        }
        tupleTransformatorFactory.add(new ConsumingTupleTransformer(consumableIndexArray));

        // Split up subquery mappers from the rest since they need special handling
        for (List<Map.Entry<String, TupleElementMapper>> attributeEntry : subtypeMappersPerAttribute) {
            List<Map.Entry<String, TupleElementMapper>> mappers = new ArrayList<>();
            List<Map.Entry<String, TupleElementMapper>> subqueryMappers = new ArrayList<>();

            String alias = null;
            Map.Entry<String, TupleElementMapper> defaultEntry = null;
            for (Map.Entry<String, TupleElementMapper> subtypeEntry : attributeEntry) {
                String constraint = subtypeEntry.getKey();
                TupleElementMapper mapper = subtypeEntry.getValue();
                AbstractMap.SimpleEntry<String, TupleElementMapper> entry;
                if (mapper instanceof SubqueryTupleElementMapper) {
                    SubqueryTupleElementMapper subqueryMapper = (SubqueryTupleElementMapper) mapper;
                    String subqueryAlias = "_inheritance_subquery_" + subqueryMappers.size();
                    String subqueryExpression;
                    if (subqueryMapper.getSubqueryAlias() == null) {
                        subqueryExpression = subqueryAlias;
                    } else {
                        subqueryExpression = subqueryMapper.getSubqueryExpression().replaceAll(subqueryMapper.getSubqueryAlias(), subqueryAlias);
                    }

                    entry = new AbstractMap.SimpleEntry<String, TupleElementMapper>(constraint, new ExpressionTupleElementMapper(subqueryExpression, subqueryMapper.getEmbeddingViewPath()));
                    subqueryMappers.add(new AbstractMap.SimpleEntry<>(subqueryAlias, mapper));
                } else {
                    entry = new AbstractMap.SimpleEntry<>(constraint, mapper);
                }

                if (constraint == null) {
                    defaultEntry = entry;
                } else {
                    mappers.add(entry);
                }

                // Extract the alias from the first mapper
                if (mapper instanceof AliasedTupleElementMapper) {
                    alias = ((AliasedTupleElementMapper) mapper).getAlias();
                }
            }

            if (defaultEntry != null) {
                mappers.add(defaultEntry);
            }

            mappingList.add(new ConstrainedTupleElementMapper(mappers, subqueryMappers, alias));
            parameterMappingList.add(null);
        }
    }

    /**
     *
     * @author Christian Beikov
     * @since 1.3.0
     */
    public static class ConstrainedTupleElementMapperBuilder {

        private final String constraint;
        private final int[] subtypeIndexes;
        private final TupleElementMapperBuilder tupleElementMapperBuilder;

        public ConstrainedTupleElementMapperBuilder(String constraint, int[] subtypeIndexes, TupleElementMapperBuilder tupleElementMapperBuilder) {
            this.constraint = constraint;
            this.subtypeIndexes = subtypeIndexes;
            this.tupleElementMapperBuilder = tupleElementMapperBuilder;
        }
    }

    /**
     * @author Christian Beikov
     * @since 1.2.0
     */
    private static class StringBuilderSelectBuilder implements ConstrainedSelectBuilder {

        private final StringBuilder sb;
        private final FullQueryBuilder<?, ?> queryBuilder;
        private SubqueryInitiator<Object> initiator;

        public StringBuilderSelectBuilder(StringBuilder sb, FullQueryBuilder<?, ?> queryBuilder) {
            this.sb = sb;
            this.queryBuilder = queryBuilder;
        }

        @Override
        public FullQueryBuilder<?, ?> getQueryBuilder() {
            return queryBuilder;
        }

        @SuppressWarnings("unchecked")
        public void setInitiator(SubqueryInitiator<?> initiator) {
            this.initiator = (SubqueryInitiator<Object>) initiator;
        }

        @Override
        public CaseWhenStarterBuilder<Object> selectCase() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CaseWhenStarterBuilder<Object> selectCase(String alias) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SimpleCaseWhenStarterBuilder<Object> selectSimpleCase(String caseOperand) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SimpleCaseWhenStarterBuilder<Object> selectSimpleCase(String caseOperand, String alias) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SubqueryInitiator<Object> selectSubquery() {
            return initiator;
        }

        @Override
        public SubqueryInitiator<Object> selectSubquery(String alias) {
            return initiator;
        }

        @Override
        public SubqueryInitiator<Object> selectSubquery(String subqueryAlias, String expression, String selectAlias) {
            return initiator;
        }

        @Override
        public SubqueryInitiator<Object> selectSubquery(String subqueryAlias, String expression) {
            return initiator;
        }

        @Override
        public MultipleSubqueryInitiator<Object> selectSubqueries(String expression, String selectAlias) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MultipleSubqueryInitiator<Object> selectSubqueries(String expression) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SubqueryBuilder<Object> selectSubquery(FullQueryBuilder<?, ?> criteriaBuilder) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SubqueryBuilder<Object> selectSubquery(String alias, FullQueryBuilder<?, ?> criteriaBuilder) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SubqueryBuilder<Object> selectSubquery(String subqueryAlias, String expression, String selectAlias, FullQueryBuilder<?, ?> criteriaBuilder) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SubqueryBuilder<Object> selectSubquery(String subqueryAlias, String expression, FullQueryBuilder<?, ?> criteriaBuilder) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object select(String expression) {
            sb.append(expression);
            return this;
        }

        @Override
        public Object select(String expression, String alias) {
            sb.append(expression);
            return this;
        }
    }
}
