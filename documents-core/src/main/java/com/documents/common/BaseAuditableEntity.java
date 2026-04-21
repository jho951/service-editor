package com.documents.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@ToString(callSuper = true)
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseAuditableEntity extends BaseEntity {

    @CreatedBy
    @Column(name = "created_by", length = 64, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "modified_by", length = 64)
    private String updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public String getModifiedBy() {
        return updatedBy;
    }

    public void setModifiedBy(String modifiedBy) {
        updatedBy = modifiedBy;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
