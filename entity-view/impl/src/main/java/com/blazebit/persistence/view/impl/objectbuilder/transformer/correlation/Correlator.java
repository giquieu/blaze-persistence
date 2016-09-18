package com.blazebit.persistence.view.impl.objectbuilder.transformer.correlation;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.view.impl.EntityViewConfiguration;

/**
 *
 * @author Christian Beikov
 * @since 1.2.0
 */
public interface Correlator {

    public void finish(CriteriaBuilder<?> criteriaBuilder, EntityViewConfiguration entityViewConfiguration, int batchSize, String correlationRoot);

}