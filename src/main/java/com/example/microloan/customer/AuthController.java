package com.example.microloan.customer;

import com.example.microloan.security.JwtService;
import com.example.microloan.security.CustomerPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final CustomerMapper customerMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(CustomerMapper customerMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.customerMapper = customerMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Customer customer = customerMapper.findByCustomerNo(request.customerNo());
        if (customer == null || !"ACTIVE".equals(customer.status())
                || !passwordEncoder.matches(request.password(), customer.passwordHash())) {
            // Keep authentication failures indistinguishable to avoid customer-number enumeration.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "客户编号或密码错误");
        }
        return new LoginResponse(jwtService.issue(customer.id(), customer.customerNo()), "Bearer", 3600);
    }

    @PostMapping("/step-up")
    public LoginResponse stepUp(@AuthenticationPrincipal CustomerPrincipal principal,
                                @Valid @RequestBody StepUpRequest request) {
        Customer customer = customerMapper.findByCustomerNo(principal.customerNo());
        if (customer == null || !"ACTIVE".equals(customer.status())
                || !passwordEncoder.matches(request.password(), customer.passwordHash())) {
            // Re-authentication uses the same generic failure semantics as initial login.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "二次认证失败");
        }
        return new LoginResponse(jwtService.issueStepUp(customer.id(), customer.customerNo()), "Bearer", 300);
    }

    public record LoginRequest(
            /** 登录客户编号。 */
            @NotBlank String customerNo,
            /** 登录明文密码，仅用于本次校验。 */
            @NotBlank String password
    ) {}

    public record StepUpRequest(
            /** 二次认证明文密码，仅用于本次校验。 */
            @NotBlank String password
    ) {}

    public record LoginResponse(
            /** 签发的 JWT 访问令牌。 */
            String accessToken,
            /** Authorization 请求头使用的令牌类型。 */
            String tokenType,
            /** 令牌有效期，单位为秒。 */
            long expiresIn
    ) {}
}
