/*
 * Copyright 2017, Oath Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */
package com.yahoo.elide.datastores.hibernate.hql;

import com.yahoo.elide.core.EntityDictionary;
import com.yahoo.elide.core.filter.FilterPredicate;
import com.yahoo.elide.core.filter.Operator;
import com.yahoo.elide.core.hibernate.hql.RelationshipImpl;
import com.yahoo.elide.core.hibernate.hql.SubCollectionFetchQueryBuilder;
import com.yahoo.elide.core.sort.Sorting;
import example.Author;
import example.Book;
import example.Chapter;
import example.Publisher;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SubCollectionFetchQueryBuilderTest {
    private EntityDictionary dictionary;

    private static final String TITLE = "title";
    private static final String BOOKS = "books";
    private static final String NAME = "name";
    private static final String PUBLISHER = "publisher";
    private static final String PUB1 = "Pub1";

    @BeforeClass
    public void initialize() {
        dictionary = new EntityDictionary(new HashMap<>());
        dictionary.bindEntity(Book.class);
        dictionary.bindEntity(Author.class);
        dictionary.bindEntity(Publisher.class);
        dictionary.bindEntity(Chapter.class);
    }

    @Test
    public void testSubCollectionFetch() {
        Author author = new Author();
        author.setId(1L);

        Book book = new Book();
        book.setId(2);

        RelationshipImpl relationship = new RelationshipImpl(
                Author.class,
                Book.class,
                BOOKS,
                author,
                Arrays.asList(book));

        SubCollectionFetchQueryBuilder builder = new SubCollectionFetchQueryBuilder(relationship,
                dictionary, new TestSessionWrapper());

        TestQueryWrapper query = (TestQueryWrapper) builder.build();

        Assert.assertNull(query);
    }

    @Test
    public void testSubCollectionFetchWithSorting() {
        Author author = new Author();
        author.setId(1L);

        Book book = new Book();
        book.setId(2);

        RelationshipImpl relationship = new RelationshipImpl(
                Author.class,
                Book.class,
                BOOKS,
                author,
                Arrays.asList(book));

        SubCollectionFetchQueryBuilder builder = new SubCollectionFetchQueryBuilder(relationship,
                dictionary, new TestSessionWrapper());

        Map<String, Sorting.SortOrder> sorting = new HashMap<>();
        sorting.put(TITLE, Sorting.SortOrder.asc);

        TestQueryWrapper query = (TestQueryWrapper) builder
                .withPossibleSorting(Optional.of(new Sorting(sorting)))
                .build();

        String expected = " order by title asc";
        String actual = query.getQueryText();

        Assert.assertEquals(actual, expected);
    }

    @Test
    public void testSubCollectionFetchWithJoinFilter() {
        Author author = new Author();
        author.setId(1L);

        Book book = new Book();
        book.setId(2);

        RelationshipImpl relationship = new RelationshipImpl(
                Author.class,
                Book.class,
                BOOKS,
                author,
                Arrays.asList(book)
        );

        List<FilterPredicate.PathElement>  publisherNamePath = Arrays.asList(
                new FilterPredicate.PathElement(Author.class, Book.class, BOOKS),
                new FilterPredicate.PathElement(Book.class, Publisher.class, PUBLISHER),
                new FilterPredicate.PathElement(Publisher.class, String.class, NAME)
        );

        FilterPredicate publisherNamePredicate = new FilterPredicate(
                publisherNamePath,
                Operator.IN, Arrays.asList(PUB1));

        SubCollectionFetchQueryBuilder builder = new SubCollectionFetchQueryBuilder(
                relationship, dictionary, new TestSessionWrapper());

        TestQueryWrapper query = (TestQueryWrapper) builder
                .withPossibleFilterExpression(Optional.of(publisherNamePredicate))
                .build();

        String expected = "WHERE books.publisher.name IN (:books_publisher_name_XXX) ";
        String actual = query.getQueryText();
        actual = actual.replaceFirst(":books_publisher_name_\\w+", ":books_publisher_name_XXX");

        Assert.assertEquals(actual, expected);
    }

    @Test
    public void testSubCollectionFetchWithSortingAndFilters() {
        Author author = new Author();
        author.setId(1L);

        Book book = new Book();
        book.setId(2);

        RelationshipImpl relationship = new RelationshipImpl(
                Author.class,
                Book.class,
                BOOKS,
                author,
                Arrays.asList(book)
        );

        List<FilterPredicate.PathElement>  publisherNamePath = Arrays.asList(
                new FilterPredicate.PathElement(Book.class, Publisher.class, PUBLISHER),
                new FilterPredicate.PathElement(Publisher.class, String.class, NAME)
        );

        FilterPredicate publisherNamePredicate = new FilterPredicate(
                publisherNamePath,
                Operator.IN, Arrays.asList(PUB1));

        SubCollectionFetchQueryBuilder builder = new SubCollectionFetchQueryBuilder(
                relationship, dictionary, new TestSessionWrapper());

        Map<String, Sorting.SortOrder> sorting = new HashMap<>();
        sorting.put(TITLE, Sorting.SortOrder.asc);

        TestQueryWrapper query = (TestQueryWrapper) builder
                .withPossibleFilterExpression(Optional.of(publisherNamePredicate))
                .withPossibleSorting(Optional.of(new Sorting(sorting)))
                .build();

        String expected = "WHERE publisher.name IN (:publisher_name_XXX)  order by title asc";
        String actual = query.getQueryText();
        actual = actual.replaceFirst(":publisher_name_\\w+", ":publisher_name_XXX");

        Assert.assertEquals(actual, expected);
    }
}
