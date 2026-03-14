package com.documents.service;

import com.documents.domain.Drawer;
import com.documents.repository.DrawerMapper;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DrawerServiceImpl implements DrawerService {

    private final DrawerMapper mapper;

    @Override
    @Transactional
    public UUID create(Drawer drawer) {
        UUID id = UUID.randomUUID();
        drawer.setId(id);
        int rows = mapper.insert(drawer);
        if (rows != 1) {
            throw new IllegalStateException("Insert failed: affected rows=" + rows);
        }
        return id;
    }

    @Override
    @Transactional(readOnly = true)
    public Drawer get(UUID id) {
        return mapper.findById(id);
    }

    @Override
    @Transactional
    public void updatePartial(UUID id, Drawer patch) {
        patch.setId(id);
        int rows = mapper.updatePartial(patch);
        if (rows == 0) {
            throw new IllegalArgumentException("Not found: id=" + id);
        }
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        int rows = mapper.delete(id);
        if (rows == 0) {
            throw new IllegalArgumentException("Not found: id=" + id);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Drawer> list(int page, int size) {
        int limit = Math.max(1, size);
        int offset = Math.max(0, (page - 1) * limit);
        return mapper.findPage(offset, limit);
    }
}
