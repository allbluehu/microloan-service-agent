package com.example.microloan.agent;

import com.example.microloan.application.ApplicationService;
import com.example.microloan.application.ApplicationStatus;
import com.example.microloan.security.CustomerPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ApplicationTools {

    private final ApplicationService applicationService;

    public ApplicationTools(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public ApplicationStatus getApplicationStatus(String applicationId) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CustomerPrincipal customer)) {
            throw new IllegalStateException("Authenticated customer context is required");
        }
        // 业务服务同时使用登录客户与申请编号校验资源归属，不能只按申请编号查询。
        return applicationService.getStatus(customer.customerId(), applicationId);
    }
}
