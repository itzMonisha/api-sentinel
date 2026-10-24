package com.sentinel.modules.project.repository;

import com.sentinel.modules.project.entity.ApiEndpoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, UUID> {

    Optional<ApiEndpoint> findByIdAndDeletedAtIsNull(UUID id);

    Optional<ApiEndpoint> findByIdAndProjectIdAndDeletedAtIsNull(UUID id, UUID projectId);

    boolean existsByProjectIdAndHttpMethodAndUrlAndDeletedAtIsNull(UUID projectId, String httpMethod, String url);

    Optional<ApiEndpoint> findByProjectIdAndHttpMethodAndUrlAndDeletedAtIsNull(UUID projectId, String httpMethod, String url);

    int countByProjectIdAndDeletedAtIsNull(UUID projectId);

    @Query("SELECT e FROM ApiEndpoint e " +
           "WHERE e.project.id = :projectId " +
           "AND e.deletedAt IS NULL " +
           "AND (:method IS NULL OR :method = '' OR UPPER(e.httpMethod) = UPPER(:method)) " +
           "AND (:search IS NULL OR :search = '' OR LOWER(e.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(e.url) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<ApiEndpoint> findEndpointsByProject(
            @Param("projectId") UUID projectId,
            @Param("method") String method,
            @Param("search") String search,
            Pageable pageable
    );

    List<ApiEndpoint> findAllByProjectIdAndDeletedAtIsNull(UUID projectId);
}
