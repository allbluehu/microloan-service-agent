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

    @Test
    void authenticatedCustomerCanQueryApplicationStatusThroughAgent() throws Exception {
        String token = login("C10001");

        mockMvc.perform(post("/api/v1/chat/sessions/S10001/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": "M10003",
                                  "content": "我的贷款申请进度到哪了？",
                                  "selectedApplicationId": "A10001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("APPLICATION_STATUS_QUERY"))
                .andExpect(jsonPath("$.businessCard.applicationId").value("A10001"))
                .andExpect(jsonPath("$.businessCard.status").value("PENDING_DOCUMENT"))
                .andExpect(jsonPath("$.businessCard.missingDocuments[0]").value("营业执照"));
    }

    @Test
    void customerCannotReadAnotherCustomersApplicationThroughApiOrAgent() throws Exception {
        String token = login("C10001");

        mockMvc.perform(get("/api/v1/applications/A99999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPLICATION_ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/chat/sessions/S10001/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": "M10004",
                                  "content": "查询申请状态",
                                  "selectedApplicationId": "A99999"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPLICATION_ACCESS_DENIED"));
    }

    @Test
    void authenticatedCustomerCanQueryMaskedPaymentRecordsThroughAgent() throws Exception {
        String token = login("C10001");

        mockMvc.perform(post("/api/v1/chat/sessions/S10001/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": "M10005",
                                  "content": "查一下这笔贷款的还款记录",
                                  "selectedLoanId": "L10001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("PAYMENT_RECORDS_QUERY"))
                .andExpect(jsonPath("$.businessCard.loanId").value("L10001"))
                .andExpect(jsonPath("$.businessCard.records.length()").value(2))
                .andExpect(jsonPath("$.businessCard.records[0].bankCardLastFour").value("1234"))
                .andExpect(jsonPath("$.businessCard.records[0].amount").value("1262.50"));
    }

    @Test
    void customerCannotReadAnotherCustomersPaymentsThroughApiOrAgent() throws Exception {
        String token = login("C10001");

        mockMvc.perform(get("/api/v1/loans/L99999/payments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LOAN_ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/chat/sessions/S10001/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": "M10006",
                                  "content": "查询还款记录",
                                  "selectedLoanId": "L99999"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LOAN_ACCESS_DENIED"));
    }

    @Test
    void paymentApiRejectsAnInvalidTimeRange() throws Exception {
        String token = login("C10001");

        mockMvc.perform(get("/api/v1/loans/L10001/payments")
                        .header("Authorization", "Bearer " + token)
                        .queryParam("from", "2026-09-01T00:00:00+08:00")
                        .queryParam("to", "2026-08-01T00:00:00+08:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
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
