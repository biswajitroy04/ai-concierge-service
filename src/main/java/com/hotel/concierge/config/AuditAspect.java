package com.hotel.concierge.config;

import com.hotel.concierge.model.AuditLog;
import com.hotel.concierge.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;

    @Around("@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public Object auditModifyingOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        String action = joinPoint.getSignature().getName();
        String entityType = joinPoint.getTarget().getClass().getSimpleName();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = auth != null ? auth.getName() : "anonymous";
        String role = auth != null && !auth.getAuthorities().isEmpty()
                ? auth.getAuthorities().iterator().next().getAuthority() : "NONE";

        String ipAddress = getClientIp();

        Object result = joinPoint.proceed();

        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(action)
                    .entityType(entityType)
                    .userId(userId)
                    .userRole(role)
                    .ipAddress(ipAddress)
                    .details("Method: " + joinPoint.getSignature().toShortString())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to save audit log: {}", e.getMessage());
        }

        return result;
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                return forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
            }
        } catch (Exception e) {
            // Ignore
        }
        return "unknown";
    }
}
