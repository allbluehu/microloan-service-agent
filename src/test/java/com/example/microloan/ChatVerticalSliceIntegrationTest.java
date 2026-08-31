package com.example.microloan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.ai.model.chat=none")
@AutoConfigureMockMvc
@Testcontainers
class ChatVerticalSliceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("microloan")
            .withUsername("microloan")
            .withPassword("microloan");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void authenticatedCustomerCanCompleteTheNextRepaymentConversation() throws Exception {
        String token = login("C10001");

        mockMvc.perform(post("/api/v1/chat/sessions/S10001/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": "M10001",
                                  "content": "我下个月要还多少钱？",
                                  "selectedLoanId": "L10001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("NEXT_REPAYMENT_QUERY"))
                .andExpect(jsonPath("$.businessCard.totalAmount").value("1262.50"))
                .andExpect(jsonPath("$.businessCard.dueDate").value("2026-09-15"))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("1262.50")));
    }

    @Test
    void customerCannotReadAnotherCustomersLoanThroughApiOrAgent() throws Exception {
        String token = login("C10001");

        mockMvc.perform(get("/api/v1/loans/L99999/next-repayment")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LOAN_ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/chat/sessions/S10001/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": "M10002",
                                  "content": "查询下一期账单",
                                  "selectedLoanId": "L99999"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LOAN_ACCESS_DENIED"));
    }

    @Test
    void loginDoesNotRevealWhetherCustomerOrPasswordWasWrong() throws Exception {
        String expectedMessage = "客户编号或密码错误";

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerNo":"C10001","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message").value(expectedMessage));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerNo":"C-NOT-FOUND","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message").value(expectedMessage));
    }

    @Test
    void protectedBusinessApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/loans/L10001/next-repayment"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message").value("需要登录后访问"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    private String login(String customerNo) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerNo":"%s","password":"Demo123!"}
                                """.formatted(customerNo)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }
}
