package com.documents.service;

import com.documents.domain.Drawer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DrawerService {
    Drawer create(Drawer drawer);
    Optional<Drawer> findById(UUID id);
    List<Drawer> findPage(int offset, int limit);
    Drawer update(UUID id, Drawer drawer);
    void delete(UUID id);
}
