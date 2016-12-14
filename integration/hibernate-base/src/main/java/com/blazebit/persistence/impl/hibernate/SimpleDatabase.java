/*
 * Copyright 2014 - 2016 Blazebit.
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

package com.blazebit.persistence.impl.hibernate;

import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.Mapping;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 *
 * @author Christian Beikov
 * @since 1.2.0
 */
public class SimpleDatabase implements Database {

    private final Map<String, Table> tables;

    public SimpleDatabase(Iterator<Table> iter, Dialect dialect, Mapping mapping) {
        Map<String, Table> map = new HashMap<String, Table>();
        while (iter.hasNext()) {
            Table t = iter.next();
            map.put(getQualifiedTableName(t), t);
            if (t.getSubselect() != null) {
                map.put("( " + t.getSubselect() + " )", t);
                Iterator<Column> columnIter = t.getColumnIterator();
                while (columnIter.hasNext()) {
                    final Column column = columnIter.next();
                    column.getSqlType(dialect, mapping);
                }
            }
        }
        this.tables = Collections.unmodifiableMap(map);
    }

    @Override
    public Table getTable(String name) {
        return tables.get(name);
    }

    public String getQualifiedTableName(Table table) {
        final String catalogName = table.getCatalog();
        final String schemaName = table.getSchema();
        final String objectName = table.getName();

        StringBuilder buff = new StringBuilder();
        if(catalogName != null) {
            buff.append(catalogName.toString()).append('.');
        }

        if(schemaName != null) {
            buff.append(schemaName.toString()).append('.');
        }

        buff.append(objectName.toString());
        return buff.toString();
    }
}
