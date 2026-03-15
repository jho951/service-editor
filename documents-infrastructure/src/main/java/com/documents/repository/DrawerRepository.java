package com.documents.repository;

import com.documents.domain.Drawer;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrawerRepository extends JpaRepository<Drawer, UUID> {
    Page<Drawer> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
