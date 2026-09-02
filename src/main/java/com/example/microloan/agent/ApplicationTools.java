package com.example.microloan.agent;

import com.example.microloan.application.ApplicationService;
import com.example.microloan.application.ApplicationStatus;
import com.example.microloan.security.CustomerPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
/**
 * 编排器与贷款申请领域服务之间的窄适配层。
 * 仅由 APPLICATION_STATUS_QUERY 分支调用，资源授权和事实查询保留在 ApplicationService。
 */
public class ApplicationTools {

    private final ApplicationService applicationService;

    public ApplicationTools(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 查询认证客户所选申请的实时状态。 */
    public ApplicationStatus getApplicationStatus(String applicationId) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CustomerPrincipal customer)) {
            throw new IllegalStateException("Authenticated customer context is required");
        }
        // 业务服务同时使用登录客户与申请编号校验资源归属，不能只按申请编号查询。
        return applicationService.getStatus(customer.customerId(), applicationId);
    }
}
