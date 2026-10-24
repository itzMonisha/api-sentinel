package com.sentinel.modules.project.repository;

import com.sentinel.modules.project.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    Optional<Project> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Project> findBySlugAndDeletedAtIsNull(String slug);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    @Query("SELECT p FROM Project p " +
           "JOIN p.members m " +
           "WHERE m.user.id = :userId " +
           "AND p.deletedAt IS NULL " +
           "AND (:search IS NULL OR :search = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Project> findProjectsForUser(@Param("userId") UUID userId, @Param("search") String search, Pageable pageable);

    @Query("SELECT p FROM Project p " +
           "WHERE p.deletedAt IS NULL " +
           "AND (:search IS NULL OR :search = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Project> findAllActive(@Param("search") String search, Pageable pageable);
}
