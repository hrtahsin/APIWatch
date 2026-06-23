package com.hasan.apiwatch.service;

import com.hasan.apiwatch.dto.AuditLogResponse;
import com.hasan.apiwatch.dto.PageResponse;
import com.hasan.apiwatch.entity.AuditLog;
import com.hasan.apiwatch.enums.AuditAction;
import com.hasan.apiwatch.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private static final String SYSTEM_ACTOR = "system";

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(
            AuditAction action,
            String targetType,
            Long targetId,
            String targetName,
            String details
    ) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorUsername(currentActor());
        auditLog.setAction(action);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setTargetName(targetName);
        auditLog.setDetails(details);
        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> findAll(int page, int size) {
        var logs = auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100)
        ));
        return PageResponse.from(logs.map(this::toResponse));
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return SYSTEM_ACTOR;
        }
        return authentication.getName();
    }

    private AuditLogResponse toResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getActorUsername(),
                auditLog.getAction(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getTargetName(),
                auditLog.getDetails(),
                auditLog.getCreatedAt()
        );
    }
}
