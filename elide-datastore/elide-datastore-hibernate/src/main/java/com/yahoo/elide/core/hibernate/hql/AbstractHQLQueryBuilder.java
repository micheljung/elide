/*
 * Copyright 2017, Oath Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */
package com.yahoo.elide.core.hibernate.hql;

import com.yahoo.elide.core.EntityDictionary;
import com.yahoo.elide.core.Path;
import com.yahoo.elide.core.filter.FilterPredicate;
import com.yahoo.elide.core.filter.expression.FilterExpression;
import com.yahoo.elide.core.filter.expression.PredicateExtractionVisitor;
import com.yahoo.elide.core.hibernate.Query;
import com.yahoo.elide.core.hibernate.Session;
import com.yahoo.elide.core.pagination.Pagination;
import com.yahoo.elide.core.sort.Sorting;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Abstract class used to construct HQL queries.
 */
public abstract class AbstractHQLQueryBuilder {
    protected final Session session;
    protected final EntityDictionary dictionary;

    protected Optional<Sorting> sorting;
    protected Optional<Pagination> pagination;
    protected Optional<FilterExpression> filterExpression;
    protected static final String SPACE = " ";
    protected static final String UNDERSCORE = "_";
    protected static final String PERIOD = ".";
    protected static final String COMMA = ",";
    protected static final String FROM = " FROM ";
    protected static final String JOIN = " JOIN ";
    protected static final String SELECT = "SELECT ";
    protected static final String AS = " AS ";

    protected static final boolean USE_ALIAS = true;
    protected static final boolean NO_ALIAS = false;

    /**
     * Represents a relationship between two entities.
     */
    public interface Relationship {
        public Class<?> getParentType();

        public Class<?> getChildType();

        public String getRelationshipName();

        public Object getParent();

        public Collection getChildren();
    }

    public AbstractHQLQueryBuilder(EntityDictionary dictionary, Session session) {
        this.session = session;
        this.dictionary = dictionary;

        sorting = Optional.empty();
        pagination = Optional.empty();
        filterExpression = Optional.empty();
    }

    public abstract Query build();

    public AbstractHQLQueryBuilder withPossibleFilterExpression(Optional<FilterExpression> filterExpression) {
        this.filterExpression = filterExpression;
        return this;
    }

    public AbstractHQLQueryBuilder withPossibleSorting(final Optional<Sorting> possibleSorting) {
        this.sorting = possibleSorting;
        return this;
    }

    public AbstractHQLQueryBuilder withPossiblePagination(final Optional<Pagination> possiblePagination) {
        this.pagination = possiblePagination;
        return this;
    }

    /**
     * Given a collection of filter predicates and a Hibernate query, populates the named parameters in the
     * Hibernate query
     * @param query The HQL query
     * @param predicates The predicates to extract named parameter values from
     */
    protected void supplyFilterQueryParameters(Query query, Collection<FilterPredicate> predicates) {
        for (FilterPredicate filterPredicate : predicates) {
            if (filterPredicate.getOperator().isParameterized()) {
                String name = filterPredicate.getParameterName();
                if (filterPredicate.isMatchingOperator()) {
                    query.setParameter(name, filterPredicate.getStringValueEscaped("%", "\\"));
                } else {
                    query.setParameterList(name, filterPredicate.getValues());
                }
            }
        }
    }

    /**
     * Extracts all the HQL JOIN clauses from given filter expression.
     * @param filterExpression
     * @return
     */
    protected String getJoinClauseFromFilters(FilterExpression filterExpression) {
        PredicateExtractionVisitor visitor = new PredicateExtractionVisitor(new ArrayList<>());
        Collection<FilterPredicate> predicates = filterExpression.accept(visitor);

        Set<String> alreadyJoined = new HashSet<>();

        return predicates.stream()
            .map(predicate -> extractJoinClause(predicate, alreadyJoined))
            .collect(Collectors.joining(SPACE));
    }

    /**
     * Modifies the HQL query to add OFFSET and LIMIT.
     * @param query The HQL query object
     */
    protected void addPaginationToQuery(Query query) {
        if (pagination.isPresent()) {
            query.setFirstResult(pagination.get().getOffset());
            query.setMaxResults(pagination.get().getLimit());
        }
    }

    /**
     * Extracts a join clause from a filter predicate (if it exists)
     * @param predicate The predicate to examine
     * @param alreadyJoined A set of joins that have already been computed.
     * @return A HQL string representing the join
     */
    private String extractJoinClause(FilterPredicate predicate, Set<String> alreadyJoined) {
        String joinClause = "";

        String previousAlias = null;

        for (FilterPredicate.PathElement pathElement : predicate.getPath()) {
            String fieldName = pathElement.getFieldName();
            Class<?> typeClass = pathElement.getType();
            String typeAlias = FilterPredicate.getTypeAlias(typeClass);

            //Nothing left to join.
            if (! dictionary.isRelation(pathElement.getType(), fieldName)) {
                return joinClause;
            }

            String alias = typeAlias + UNDERSCORE + fieldName;

            String joinFragment;

            //This is the first path element
            if (previousAlias == null) {
                joinFragment = JOIN + typeAlias + PERIOD + fieldName + SPACE + alias + SPACE;
            } else {
                joinFragment = JOIN + previousAlias + PERIOD + fieldName + SPACE + alias + SPACE;
            }

            if (!alreadyJoined.contains(joinFragment)) {
                joinClause += joinFragment;
                alreadyJoined.add(joinFragment);
            }

            previousAlias = alias;
        }

        return joinClause;
    }

    /**
     * Returns a sorting object into a HQL ORDER BY string.
     * @param sorting The sorting object passed from the client
     * @param sortClass The class to sort.
     * @param prefixWithAlias Whether the sorting fields should be prefixed by an alias.
     * @return The sorting clause
     */
    protected String getSortClause(final Optional<Sorting> sorting, Class<?> sortClass, boolean prefixWithAlias) {
        String sortingRules = "";
        if (sorting.isPresent() && !sorting.get().isDefaultInstance()) {
            final Map<Path, Sorting.SortOrder> validSortingRules = sorting.get().getValidSortingRules(
                    sortClass, dictionary
            );
            if (!validSortingRules.isEmpty()) {
                final List<String> ordering = new ArrayList<>();
                // pass over the sorting rules
                validSortingRules.entrySet().stream().forEachOrdered(entry -> {
                        Path path = entry.getKey();

                        String prefix = (prefixWithAlias) ? Path.getTypeAlias(sortClass) + PERIOD : "";

                        ordering.add(prefix + path.getFieldPath() + SPACE
                                + (entry.getValue().equals(Sorting.SortOrder.desc) ? "desc" : "asc"));
                    }
                );
                sortingRules = " order by " + StringUtils.join(ordering, COMMA);
            }
        }
        return sortingRules;
    }
}
