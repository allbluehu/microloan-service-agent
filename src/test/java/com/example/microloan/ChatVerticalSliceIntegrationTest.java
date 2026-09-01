package com.example.microloan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.microloan.ticket.ServiceTicket;
import com.example.microloan.ticket.TicketPriority;
import com.example.microloan.ticket.TicketService;
import com.example.microloan.ticket.TicketType;
import java.util.concurrent.CompletableFuture;
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

    @Autowired
    private TicketService ticketService;

    @Test
    void authenticatedCustomerCanCompleteTheNextRepaymentConversation() throws Exception {
        String token = login("C10001");

        MvcResult response = mockMvc.perform(post("/api/v1/chat/sessions/S10001/messages")
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
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("1262.50")))
                .andReturn();

        String traceId = objectMapper.readTree(response.getResponse().getContentAsString()).path("traceId").asText();
        mockMvc.perform(get("/api/v1/agent/traces/{traceId}", traceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].nodeName").value("RISK_CLASSIFIER"))
                .andExpect(jsonPath("$[0].inputSummary").value("messageId=M10001"))
                .andExpect(jsonPath("$[1].nodeName").value("INTENT_CLASSIFIER"))
                .andExpect(jsonPath("$[2].nodeName").value("ROUTE_DECISION"))
                .andExpect(jsonPath("$[3].nodeName").value("TOOL_EXECUTOR"))
                .andExpect(jsonPath("$[3].outputSummary").value("resultType=Repayment"));

        String otherCustomerToken = login("C10002");
        mockMvc.perform(get("/api/v1/agent/traces/{traceId}", traceId)
                        .header("Authorization", "Bearer " + otherCustomerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
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
    void failedStepUpDoesNotIssueAnElevatedToken() throws Exception {
        String loginToken = login("C10001");

        mockMvc.perform(post("/api/v1/auth/step-up")
                        .header("Authorization", "Bearer " + loginToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message").value("二次认证失败"));
    }

    @Test
    void protectedBusinessApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/loans/L10001/next-repayment"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message").value("需要登录后访问"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        mockMvc.perform(get("/api/v1/loans/L10001/next-repayment")
                        .header("Authorization", "Bearer malformed-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void missingBusinessIdentifiersProduceClarificationWithoutCallingTools() throws Exception {
        String token = login("C10001");

        mockMvc.perform(post("/api/v1/chat/sessions/S-CLARIFY/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageId":"M-C1","content":"我下个月要还多少钱？"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessCard").doesNotExist())
                .andExpect(jsonPath("$.answer").value("请先选择需要查询的贷款。"));

        mockMvc.perform(post("/api/v1/chat/sessions/S-CLARIFY/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageId":"M-C2","content":"查询申请进度"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessCard").doesNotExist())
                .andExpect(jsonPath("$.answer").value("请先选择需要查询的贷款申请。"));

        mockMvc.perform(post("/api/v1/chat/sessions/S-CLARIFY/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId":"M-C3",
                                  "content":"提前结清要多少钱？",
                                  "selectedLoanId":"L10001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessCard").doesNotExist())
                .andExpect(jsonPath("$.answer").value("请提供提前结清的试算日期。"));
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

    @Test
    void earlySettlementRequiresStepUpAndUsesJavaCalculatedAmounts() throws Exception {
        String loginToken = login("C10001");
        String request = """
                {
                  "messageId": "M10007",
                  "content": "9月3日提前结清要多少钱？",
                  "selectedLoanId": "L10001",
                  "settlementDate": "2026-09-03"
                }
                """;

        mockMvc.perform(post("/api/v1/chat/sessions/S10001/messages")
                        .header("Authorization", "Bearer " + loginToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STEP_UP_AUTH_REQUIRED"));

        String stepUpToken = stepUp(loginToken);
        mockMvc.perform(post("/api/v1/chat/sessions/S10001/messages")
                        .header("Authorization", "Bearer " + stepUpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("EARLY_SETTLEMENT_QUOTE"))
                .andExpect(jsonPath("$.businessCard.remainingPrincipal").value("8200.00"))
                .andExpect(jsonPath("$.businessCard.interest").value("33.70"))
                .andExpect(jsonPath("$.businessCard.totalAmount").value("8233.70"))
                .andExpect(jsonPath("$.businessCard.notice").value("该结果仅为试算，不代表已经结清"));
    }

    @Test
    void stepUpTokenStillCannotSettleAnotherCustomersLoan() throws Exception {
        String stepUpToken = stepUp(login("C10001"));

        mockMvc.perform(post("/api/v1/loans/L99999/settlement-quotes")
                        .header("Authorization", "Bearer " + stepUpToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"settlementDate":"2026-09-03"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LOAN_ACCESS_DENIED"));
    }

    @Test
    void highRiskComplaintCreatesOneHighPriorityTicketAndHandsOff() throws Exception {
        String token = login("C10001");
        String request = """
                {
                  "messageId": "M10008",
                  "content": "催收把我的欠款告诉同事，我要投诉"
                }
                """;

        MvcResult first = mockMvc.perform(post("/api/v1/chat/sessions/S-RISK-1/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("HIGH_RISK_COMPLAINT"))
                .andExpect(jsonPath("$.businessCard.type").value("PRIVACY_BREACH"))
                .andExpect(jsonPath("$.businessCard.priority").value("HIGH"))
                .andExpect(jsonPath("$.businessCard.status").value("OPEN"))
                .andReturn();

        MvcResult duplicate = mockMvc.perform(post("/api/v1/chat/sessions/S-RISK-1/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn();

        assertThatJsonTicketIdsMatch(first, duplicate);
    }

    @Test
    void concurrentTicketCreationReturnsTheSameTicket() {
        CompletableFuture<ServiceTicket> first = CompletableFuture.supplyAsync(() -> ticketService.create(
                10001L, "S-CONCURRENT", TicketType.PAYMENT_NOT_POSTED, "L10001",
                "已还款但账单未更新", TicketPriority.HIGH));
        CompletableFuture<ServiceTicket> second = CompletableFuture.supplyAsync(() -> ticketService.create(
                10001L, "S-CONCURRENT", TicketType.PAYMENT_NOT_POSTED, "L10001",
                "重复提交的摘要不会创建新工单", TicketPriority.HIGH));

        assertThat(first.join().ticketId()).isEqualTo(second.join().ticketId());
    }

    @Test
    void explicitHumanRequestHandsOffWithoutRepeatedRetention() throws Exception {
        String token = login("C10001");

        mockMvc.perform(post("/api/v1/chat/sessions/S-HUMAN-1/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": "M10009",
                                  "content": "请直接帮我转人工客服"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("HUMAN_HANDOFF"))
                .andExpect(jsonPath("$.businessCard.priority").value("NORMAL"))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("正在为你转接人工客服")));
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

    private String stepUp(String loginToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/step-up")
                        .header("Authorization", "Bearer " + loginToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"Demo123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void assertThatJsonTicketIdsMatch(MvcResult first, MvcResult second) throws Exception {
        String firstId = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("businessCard").path("ticketId").asText();
        String secondId = objectMapper.readTree(second.getResponse().getContentAsString())
                .path("businessCard").path("ticketId").asText();
        assertThat(firstId).isNotBlank().isEqualTo(secondId);
    }
}
