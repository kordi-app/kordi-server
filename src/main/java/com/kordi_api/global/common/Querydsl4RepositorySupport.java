package com.kordi_api.global.common;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.JPQLTemplates;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.data.jpa.repository.support.Querydsl;
import org.springframework.data.querydsl.SimpleEntityPathResolver;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

@Repository
public abstract class Querydsl4RepositorySupport<T> {

  private final Class<T> domainClass;
  private Querydsl querydsl;
  private EntityManager entityManager;
  protected JPAQueryFactory queryFactory;

  public Querydsl4RepositorySupport(Class<T> domainClass) {
    Assert.notNull(domainClass, "Domain class must not be null!");
    this.domainClass = domainClass;
  }

  @Autowired
  public void setEntityManager(EntityManager entityManager) {
    Assert.notNull(entityManager, "EntityManager must not be null!");
    JpaEntityInformation<T, ?> entityInformation =
        JpaEntityInformationSupport.getEntityInformation(domainClass, entityManager);
    SimpleEntityPathResolver resolver = SimpleEntityPathResolver.INSTANCE;
    EntityPath<T> path = resolver.createPath(entityInformation.getJavaType());
    this.entityManager = entityManager;
    this.querydsl =
        new Querydsl(entityManager, new PathBuilder<>(path.getType(), path.getMetadata()));
    this.queryFactory = new JPAQueryFactory(JPQLTemplates.DEFAULT, entityManager);
  }

  @PostConstruct
  public void validate() {
    Assert.notNull(entityManager, "EntityManager must not be null!");
    Assert.notNull(querydsl, "Querydsl must not be null!");
    Assert.notNull(queryFactory, "QueryFactory must not be null!");
  }

  protected JPAQueryFactory getQueryFactory() {
    return queryFactory;
  }

  protected Querydsl getQuerydsl() {
    return querydsl;
  }

  protected EntityManager getEntityManager() {
    return entityManager;
  }

  protected <R> JPAQuery<R> select(Expression<R> expr) {
    return getQueryFactory().select(expr);
  }

  protected <R> JPAQuery<R> selectFrom(EntityPath<R> from) {
    return getQueryFactory().selectFrom(from);
  }

  protected <R> Page<R> applyPagination(
      Pageable pageable, Function<JPAQueryFactory, JPAQuery<R>> contentQuery) {
    JPAQuery<R> jpaQuery = contentQuery.apply(getQueryFactory());
    List<R> content = getQuerydsl().applyPagination(pageable, jpaQuery).fetch();
    return PageableExecutionUtils.getPage(content, pageable, jpaQuery::fetchCount);
  }

  protected <R> Page<R> applyPagination(
      Pageable pageable,
      Function<JPAQueryFactory, JPAQuery<R>> contentQuery,
      Function<JPAQueryFactory, JPAQuery<Long>> countQuery) {
    JPAQuery<R> jpaContentQuery = contentQuery.apply(getQueryFactory());
    List<R> content = getQuerydsl().applyPagination(pageable, jpaContentQuery).fetch();
    JPAQuery<Long> countResult = countQuery.apply(getQueryFactory());
    return PageableExecutionUtils.getPage(content, pageable, () -> countResult.fetchOne());
  }

  protected <R> Page<R> applyPaginationAndCustomSorting(
      Pageable pageable,
      Function<JPAQueryFactory, JPAQuery<R>> contentQueryFunction,
      Function<JPAQueryFactory, JPAQuery<Long>> countQueryFunction,
      Function<Sort, List<OrderSpecifier<?>>> orderFunction) {
    JPAQuery<R> contentQuery = contentQueryFunction.apply(getQueryFactory());
    applyCustomSorting(contentQuery, pageable.getSort(), orderFunction);
    contentQuery.offset(pageable.getOffset());
    contentQuery.limit(pageable.getPageSize());
    List<R> content = contentQuery.fetch();
    Long total = countQueryFunction.apply(getQueryFactory()).fetchOne();
    return PageableExecutionUtils.getPage(content, pageable, () -> total);
  }

  protected <Q extends JPAQuery<?>> Q applyCustomSorting(
      Q query, Sort sort, Function<Sort, List<OrderSpecifier<?>>> orderFunction) {
    List<OrderSpecifier<?>> orderSpecifiers = orderFunction.apply(sort);
    if (orderSpecifiers != null && !orderSpecifiers.isEmpty()) {
      query.orderBy(orderSpecifiers.toArray(new OrderSpecifier[0]));
    }
    return query;
  }
}
