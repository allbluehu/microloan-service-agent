package com.example.microloan.agent;

import com.example.microloan.application.ApplicationService;
import com.example.microloan.application.ApplicationStatus;
import com.example.microloan.security.CustomerPrincipal;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ApplicationTools {

    private final ApplicationService applicationService;

    public ApplicationTools(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Tool(description = "查询已登录客户指定贷款申请的当前进度、更新时间和缺失资料")
    public ApplicationStatus getApplicationStatus(String applicationId) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CustomerPrincipal customer)) {
            throw new IllegalStateException("Authenticated customer context is required");
        }
        return applicationService.getStatus(customer.customerId(), applicationId);
    }
}
