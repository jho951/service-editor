package com.documents.support;

import org.springframework.stereotype.Component;

import com.documents.service.transaction.PersistenceContextManager;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Component
public class JpaPersistenceContextManager implements PersistenceContextManager {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void flush() {
        entityManager.flush();
    }
}
