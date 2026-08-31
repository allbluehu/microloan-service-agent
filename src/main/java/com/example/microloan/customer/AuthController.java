package com.example.microloan.customer;

import com.example.microloan.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    public record LoginRequest(@NotBlank String customerNo, @NotBlank String password) {}

    public record LoginResponse(String accessToken, String tokenType, long expiresIn) {}
}
