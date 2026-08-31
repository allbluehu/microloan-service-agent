package com.example.microloan.application;

import com.example.microloan.security.CustomerPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/{applicationId}")
    public ApplicationStatus getStatus(@AuthenticationPrincipal CustomerPrincipal principal,
                                       @PathVariable String applicationId) {
        return applicationService.getStatus(principal.customerId(), applicationId);
    }
}
