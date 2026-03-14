package com.documents.service;

import com.documents.domain.Drawer;
import java.util.List;
import java.util.UUID;

public interface DrawerService {
    UUID create(Drawer drawer);
    Drawer get(UUID id);
    void updatePartial(UUID id, Drawer patch);
    void delete(UUID id);
    List<Drawer> list(int page, int size);
}
