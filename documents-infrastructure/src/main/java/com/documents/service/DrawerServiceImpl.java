package com.documents.service;

import com.documents.domain.Drawer;
import com.documents.repository.DrawerRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DrawerServiceImpl implements DrawerService {

    private final DrawerRepository drawerRepository;

    @Override
    @Transactional
    public Drawer create(Drawer drawer) {
        if (drawer.getId() == null) {
            drawer.setId(UUID.randomUUID());
        }
        return drawerRepository.save(drawer);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Drawer> findById(UUID id) {
        return drawerRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Drawer> findPage(int offset, int limit) {
        int pageSize = Math.max(limit, 1);
        int pageIndex = Math.max(offset, 0) / pageSize;
        return drawerRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(pageIndex, pageSize))
                .getContent();
    }

    @Override
    @Transactional
    public Drawer update(UUID id, Drawer drawer) {
        Drawer persisted = drawerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Drawer not found: " + id));

        if (drawer.getTitle() != null) {
            persisted.setTitle(drawer.getTitle());
        }
        if (drawer.getWidth() != null) {
            persisted.setWidth(drawer.getWidth());
        }
        if (drawer.getHeight() != null) {
            persisted.setHeight(drawer.getHeight());
        }
        if (drawer.getVectorJson() != null) {
            persisted.setVectorJson(drawer.getVectorJson());
        }

        return drawerRepository.save(persisted);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        drawerRepository.deleteById(id);
    }
}
