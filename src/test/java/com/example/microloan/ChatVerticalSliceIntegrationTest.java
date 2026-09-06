package com.example.microloan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.microloan.agent.IntentClassifier;
import com.example.microloan.knowledge.KnowledgeAnswer;
import com.example.microloan.knowledge.KnowledgeChunkForEmbedding;
import com.example.microloan.knowledge.KnowledgeClasspathImporter;
import com.example.microloan.knowledge.KnowledgeEvidence;
import com.example.microloan.knowledge.KnowledgeImportResult;
import com.example.microloan.knowledge.KnowledgeImportService;
import com.example.microloan.knowledge.KnowledgeMapper;
import com.example.microloan.knowledge.KnowledgeDocumentIdentity;
import com.example.microloan.knowledge.KnowledgePublicationService;
import com.example.microloan.knowledge.KnowledgeService;
import com.example.microloan.ticket.ServiceTicket;
import com.example.microloan.ticket.TicketPriority;
import com.example.microloan.ticket.TicketService;
import com.example.microloan.ticket.TicketType;
import java.util.concurrent.CompletableFuture;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "support.api-key=test-support-key-at-least-32-bytes!!",
        "knowledge.admin.api-key=test-knowledge-admin-key-32-bytes!!"
})
@AutoConfigureMockMvc
@Testcontainers
class ChatVerticalSliceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
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

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Autowired
    private KnowledgeImportService knowledgeImportService;

    @Autowired
    private KnowledgePublicationService knowledgePublicationService;

    @Autowired
    private ResourcePatternResolver resourcePatternResolver;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void livenessProbeIsAvailableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                // 即使匿名访问，探针也不暴露内部组件和基础设施细节。
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void chatTestPageIsAvailableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/chat.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/ui/index.html")));
    }

    @Test
    void readinessProbeIncludesAvailableDatabaseWithoutExposingDetails() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

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
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].nodeName").value("RISK_CLASSIFIER"))
                .andExpect(jsonPath("$[0].inputSummary").value("messageId=M10001"))
                .andExpect(jsonPath("$[1].nodeName").value("INTENT_CLASSIFIER"))
                .andExpect(jsonPath("$[1].outputSummary").value(org.hamcrest.Matchers.containsString("source=RULE")))
                .andExpect(jsonPath("$[1].modelVersion").doesNotExist())
                .andExpect(jsonPath("$[1].promptVersion").value(IntentClassifier.PROMPT_VERSION))
                .andExpect(jsonPath("$[2].nodeName").value("ROUTE_DECISION"))
                .andExpect(jsonPath("$[4].nodeName").value("RESULT_VERIFIER"))
                .andExpect(jsonPath("$[5].nodeName").value("OUTPUT_GUARDRAIL"))
                .andExpect(jsonPath("$[5].outputSummary").value("changed=false,reason=PASSED"))
                .andExpect(jsonPath("$[3].nodeName").value("TOOL_EXECUTOR"))
                .andExpect(jsonPath("$[3].outputSummary").value("resultType=Repayment"));

        String otherCustomerToken = login("C10002");
        mockMvc.perform(get("/api/v1/agent/traces/{traceId}", traceId)
                        .header("Authorization", "Bearer " + otherCustomerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void conversationPersistsRedactedUserAndTracedAssistantMessages() throws Exception {
        String token = login("C10001");

        mockMvc.perform(post("/api/v1/chat/sessions/S-HISTORY-1/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": "M-HISTORY-1",
                                  "content": "备注号码 110101199001011234"
                                }
                                """))
                .andExpect(status().isOk());

        List<String> contents = jdbcTemplate.queryForList("""
                SELECT m.content
                FROM chat_message m
                JOIN chat_session s ON s.id = m.session_id
                WHERE s.session_id = 'S-HISTORY-1'
                ORDER BY m.id
                """, String.class);
        Integer tracedAnswers = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM chat_message m
                JOIN chat_session s ON s.id = m.session_id
                WHERE s.session_id = 'S-HISTORY-1'
                  AND m.role = 'ASSISTANT'
                  AND m.trace_id IS NOT NULL
                """, Integer.class);

        assertThat(contents).hasSize(2);
        assertThat(contents.getFirst()).isEqualTo("备注号码 [REDACTED]");
        assertThat(tracedAnswers).isEqualTo(1);

        mockMvc.perform(get("/api/v1/chat/sessions/S-HISTORY-1/messages")
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("ASSISTANT"))
                .andExpect(jsonPath("$[1].content").value("备注号码 [REDACTED]"));

        mockMvc.perform(get("/api/v1/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sessionId == 'S-HISTORY-1')].messageCount")
                        .value(org.hamcrest.Matchers.contains(2)));

        String otherCustomerToken = login("C10002");
        mockMvc.perform(get("/api/v1/chat/sessions/S-HISTORY-1/messages")
                        .header("Authorization", "Bearer " + otherCustomerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/chat/sessions")
                        .header("Authorization", "Bearer " + otherCustomerToken)
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sessionId == 'S-HISTORY-1')]").isEmpty());
    }

    @Test
    void customerCanUpdateFeedbackOnlyForOwnedAssistantMessage() throws Exception {
        String token = login("C10001");
        mockMvc.perform(post("/api/v1/chat/sessions/S-FEEDBACK/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageId":"M-FEEDBACK","content":"我下个月要还多少钱？","selectedLoanId":"L10001"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/chat/messages/M-FEEDBACK/feedback")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"DOWN","reason":"OUTDATED_INFORMATION","comment":"证件 110101199001011234"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value("M-FEEDBACK"))
                .andExpect(jsonPath("$.comment").value("证件 [REDACTED]"));

        mockMvc.perform(post("/api/v1/chat/messages/M-FEEDBACK/feedback")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"UP"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value("UP"))
                .andExpect(jsonPath("$.reason").doesNotExist());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_feedback WHERE customer_id = 10001", Integer.class)).isEqualTo(1);

        String otherCustomerToken = login("C10002");
        mockMvc.perform(post("/api/v1/chat/messages/M-FEEDBACK/feedback")
                        .header("Authorization", "Bearer " + otherCustomerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"DOWN","reason":"INCORRECT_ANSWER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void feedbackRatingAndReasonMustBeConsistent() throws Exception {
        String token = login("C10001");

        mockMvc.perform(post("/api/v1/chat/messages/UNKNOWN/feedback")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"DOWN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        mockMvc.perform(post("/api/v1/chat/messages/UNKNOWN/feedback")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"UP","reason":"NOT_HELPFUL"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void customerCanReloadSubmittedFeedbackButOtherCustomerCannot() throws Exception {
        String token = login("C10001");
        mockMvc.perform(post("/api/v1/chat/sessions/S-FEEDBACK-READ/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageId":"M-FEEDBACK-READ","content":"查询还款记录","selectedLoanId":"L10001"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/chat/messages/M-FEEDBACK-READ/feedback")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"DOWN","reason":"NOT_HELPFUL","comment":"没有解决问题"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/chat/messages/M-FEEDBACK-READ/feedback")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value("DOWN"))
                .andExpect(jsonPath("$.reason").value("NOT_HELPFUL"))
                .andExpect(jsonPath("$.comment").value("没有解决问题"));

        String otherCustomerToken = login("C10002");
        mockMvc.perform(get("/api/v1/chat/messages/M-FEEDBACK-READ/feedback")
                        .header("Authorization", "Bearer " + otherCustomerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void recentMessagesIncludeOwnedFeedbackWithoutAdditionalLookup() throws Exception {
        String token = login("C10001");
        mockMvc.perform(post("/api/v1/chat/sessions/S-FEEDBACK-LIST/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageId":"M-FEEDBACK-LIST","content":"查询还款记录","selectedLoanId":"L10001"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/chat/messages/M-FEEDBACK-LIST/feedback")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rating":"DOWN","reason":"NOT_HELPFUL","comment":"希望展示更多记录"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/chat/sessions/S-FEEDBACK-LIST/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.role == 'ASSISTANT')].feedbackRating")
                        .value(org.hamcrest.Matchers.contains("DOWN")))
                .andExpect(jsonPath("$[?(@.role == 'ASSISTANT')].feedbackReason")
                        .value(org.hamcrest.Matchers.contains("NOT_HELPFUL")))
                .andExpect(jsonPath("$[?(@.role == 'ASSISTANT')].feedbackComment")
                        .value(org.hamcrest.Matchers.contains("希望展示更多记录")))
                .andExpect(jsonPath("$[?(@.role == 'USER')].feedbackRating")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void conversationReusesOnlyPreviouslySelectedResourceIdentifier() throws Exception {
        String token = login("C10001");
        mockMvc.perform(post("/api/v1/chat/sessions/S-CONTEXT-1/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": "M-CONTEXT-1",
                                  "content": "我下个月要还多少钱？",
                                  "selectedLoanId": "L10001"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/chat/sessions/S-CONTEXT-1/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": "M-CONTEXT-2",
                                  "content": "下一期账单是多少？"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("NEXT_REPAYMENT_QUERY"))
                .andExpect(jsonPath("$.businessCard.loanId").value("L10001"));
    }

    @Test
    void duplicateClientMessageIdReusesBusinessFlowWithoutDuplicatePersistence() throws Exception {
        String token = login("C10001");
        String request = """
                {
                  "messageId": "M-IDEMPOTENT-1",
                  "content": "下一期账单是多少？",
                  "selectedLoanId": "L10001"
                }
                """;

        mockMvc.perform(post("/api/v1/chat/sessions/S-IDEMPOTENT-1/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/chat/sessions/S-IDEMPOTENT-1/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessCard.loanId").value("L10001"));

        Integer messageCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM chat_message m
                JOIN chat_session s ON s.id = m.session_id
                WHERE s.session_id = 'S-IDEMPOTENT-1'
                """, Integer.class);
        assertThat(messageCount).isEqualTo(2);
    }

    @Test
    void compositeBusinessRequestClarifiesWithoutCallingATool() throws Exception {
        String token = login("C10001");

        MvcResult result = mockMvc.perform(post("/api/v1/chat/sessions/S-COMPOSITE-1/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": "M-COMPOSITE-1",
                                  "content": "查一下下一期账单，顺便看看还款记录",
                                  "selectedLoanId": "L10001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CLARIFICATION_REQUIRED"))
                .andExpect(jsonPath("$.businessCard").doesNotExist())
                .andReturn();

        String traceId = objectMapper.readTree(result.getResponse().getContentAsString()).path("traceId").asText();
        mockMvc.perform(get("/api/v1/agent/traces/{traceId}", traceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].outputSummary")
                        .value(org.hamcrest.Matchers.containsString("source=COMPOSITE_RULE")))
                .andExpect(jsonPath("$[3].outputSummary").value("resultType=NONE"));
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
        MvcResult result = mockMvc.perform(get("/api/v1/loans/L10001/next-repayment"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message").value("需要登录后访问"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn();

        JsonNode error = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getHeader("X-Trace-Id")).isEqualTo(error.path("traceId").asText());

        mockMvc.perform(get("/api/v1/loans/L10001/next-repayment")
                        .header("Authorization", "Bearer malformed-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void chatResponseReusesRequestTraceIdAcrossHeaderBodyAndPersistedTrace() throws Exception {
        String token = login("C10001");

        MvcResult result = mockMvc.perform(post("/api/v1/chat/sessions/S-HTTP-TRACE/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId": "M-HTTP-TRACE",
                                  "content": "查询下一期账单",
                                  "selectedLoanId": "L10001"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String traceId = response.path("traceId").asText();
        assertThat(result.getResponse().getHeader("X-Trace-Id")).isEqualTo(traceId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_trace WHERE trace_id = ?", Integer.class, traceId)).isEqualTo(6);
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
    void knowledgeAnswerUsesOnlyPublishedEffectiveProductEvidence() throws Exception {
        String token = login("C10001");

        MvcResult result = mockMvc.perform(post("/api/v1/chat/sessions/S-KNOWLEDGE/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId":"M-K1",
                                  "content":"个体工商户经营贷需要什么材料？"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("KNOWLEDGE_QUERY"))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("营业执照")))
                .andExpect(jsonPath("$.businessCard.sources[0].documentId").value("KD-MB-001"))
                .andExpect(jsonPath("$.businessCard.sources[0].chunkId").value("KC-MB-001"))
                .andExpect(jsonPath("$.businessCard.sources[0].version").value("2026.1"))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        assertThat(response).doesNotContain("旧版经营贷只需要", "内部反欺诈审核阈值", "个人消费贷");
    }

    @Test
    void knowledgeMissRefusesToInventAnAnswer() throws Exception {
        String token = login("C10001");

        mockMvc.perform(post("/api/v1/chat/sessions/S-KNOWLEDGE/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId":"M-K2",
                                  "content":"经营贷费用是多少？"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("KNOWLEDGE_QUERY"))
                .andExpect(jsonPath("$.businessCard").doesNotExist())
                .andExpect(jsonPath("$.answer").value("未找到当前有效且可对客展示的知识，请补充产品名称或转人工客服。"));
    }

    @Test
    void explicitProductFilterPreventsCrossProductEvidence() throws Exception {
        String token = login("C10001");

        mockMvc.perform(post("/api/v1/chat/sessions/S-KNOWLEDGE/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageId":"M-K3",
                                  "content":"经营贷需要什么材料？",
                                  "selectedProductCode":"PERSONAL_CONSUMER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessCard").doesNotExist())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("未找到")));
    }

    @Test
    void ragBaselineMeetsRecallIsolationAndRefusalGates() throws Exception {
        JsonNode cases;
        try (InputStream input = Objects.requireNonNull(
                getClass().getResourceAsStream("/evaluation/rag-baseline.json"))) {
            cases = objectMapper.readTree(input);
        }
        assertThat(cases).hasSize(50);

        int evidenceCases = 0;
        int refusalCases = 0;
        List<String> missedEvidence = new ArrayList<>();
        List<String> incorrectAnswers = new ArrayList<>();
        for (JsonNode evaluationCase : cases) {
            String question = evaluationCase.path("question").asText();
            KnowledgeAnswer answer = knowledgeService.answer(
                    question,
                    nullableText(evaluationCase, "selectedProductCode"),
                    nullableText(evaluationCase, "selectedRegion"));
            if (evaluationCase.path("expectRefusal").asBoolean()) {
                refusalCases++;
                if (!answer.sources().isEmpty()) {
                    incorrectAnswers.add(question);
                }
                continue;
            }

            evidenceCases++;
            boolean expectedSourceRecalled = answer.sources().stream().anyMatch(source ->
                    source.documentId().equals(evaluationCase.path("expectedDocumentId").asText())
                            && source.chunkId().equals(evaluationCase.path("expectedChunkId").asText()));
            if (!expectedSourceRecalled) {
                missedEvidence.add(question);
            }
        }

        // These deterministic gates protect retrieval quality before vector recall is introduced.
        assertThat(evidenceCases).isEqualTo(25);
        assertThat(refusalCases).isEqualTo(25);
        assertThat(missedEvidence).isEmpty();
        assertThat(incorrectAnswers).isEmpty();
    }

    @Test
    void pgvectorQueryReturnsOnlyEligibleEmbeddedKnowledge() {
        KnowledgeChunkForEmbedding materialChunk = knowledgeMapper
                .findChunksRequiringEmbedding("test-embedding")
                .stream()
                .filter(chunk -> chunk.content().contains("营业执照"))
                .findFirst()
                .orElseThrow();
        String vector = unitVectorLiteral(1024);
        knowledgeMapper.updateEmbedding(materialChunk.id(), vector, "test-embedding");

        List<KnowledgeEvidence> result = knowledgeMapper.searchPublishedByVector(
                vector, "MICRO_BUSINESS", "ALL", 3);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().documentId()).isEqualTo("KD-MB-001");
        assertThat(result.getFirst().chunkId()).isEqualTo("KC-MB-001");
        assertThat(result.getFirst().score()).isEqualTo(1.0);
    }

    @Test
    void markdownDraftImportIsTransactionalIdempotentAndNotCustomerVisible() {
        String markdown = draftKnowledgeMarkdown("导入的草稿内容仅用于测试。");

        KnowledgeImportResult imported = knowledgeImportService.importMarkdownDraft(markdown);
        KnowledgeImportResult duplicate = knowledgeImportService.importMarkdownDraft(markdown);

        assertThat(imported.duplicate()).isFalse();
        assertThat(imported.chunkCount()).isEqualTo(2);
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.documentId()).isEqualTo(imported.documentId());
        assertThat(knowledgeMapper.findDocumentIdentity("KD-IMPORT-001", "2026.1").status())
                .isEqualTo("DRAFT");
        assertThat(knowledgeMapper.searchPublished("导入测试", "MICRO_BUSINESS", "ALL", 3)).isEmpty();

        assertThatThrownBy(() -> knowledgeImportService.importMarkdownDraft(
                draftKnowledgeMarkdown("同版本被修改的草稿内容。")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("提升版本号");
        assertThat(knowledgeMapper.countChunks(imported.documentId())).isEqualTo(2);
    }

    @Test
    void knowledgePublicationRequiresApprovalAndExpirationRemovesEvidence() {
        knowledgeImportService.importMarkdownDraft(publicationKnowledgeMarkdown());

        assertThatThrownBy(() -> knowledgePublicationService.publish("KD-PUBLISH-001", "2026.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
        assertThat(knowledgeMapper.searchPublished("发布测试", "MICRO_BUSINESS", "ALL", 3)).isEmpty();

        KnowledgeDocumentIdentity approved = knowledgePublicationService.approve(
                "KD-PUBLISH-001", "2026.1", " compliance-reviewer ");
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.reviewer()).isEqualTo("compliance-reviewer");
        assertThat(approved.approvedAt()).isNotNull();
        assertThat(knowledgeMapper.searchPublished("发布测试", "MICRO_BUSINESS", "ALL", 3)).isEmpty();

        KnowledgeDocumentIdentity published = knowledgePublicationService.publish("KD-PUBLISH-001", "2026.1");
        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.publishedAt()).isNotNull();
        assertThat(knowledgeMapper.searchPublished("发布测试", "MICRO_BUSINESS", "ALL", 3))
                .extracting(KnowledgeEvidence::documentId)
                .containsOnly("KD-PUBLISH-001");

        KnowledgeDocumentIdentity expired = knowledgePublicationService.expire("KD-PUBLISH-001", "2026.1");
        assertThat(expired.status()).isEqualTo("EXPIRED");
        assertThat(expired.expiredAt()).isNotNull();
        assertThat(knowledgeMapper.searchPublished("发布测试", "MICRO_BUSINESS", "ALL", 3)).isEmpty();
    }

    @Test
    void knowledgeAdminApiRequiresIndependentCredentialAndControlsPublication() throws Exception {
        String path = "/api/v1/admin/knowledge/documents/markdown";
        String requestBody = objectMapper.writeValueAsString(Map.of("markdown", adminKnowledgeMarkdown()));

        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        String customerToken = login("C10001");
        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post(path)
                        .header("X-Knowledge-Admin-Key", "test-knowledge-admin-key-32-bytes!!")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentNo").value("KD-ADMIN-001"))
                .andExpect(jsonPath("$.duplicate").value(false));

        mockMvc.perform(post("/api/v1/admin/knowledge/documents/KD-ADMIN-001/versions/2026.1/approve")
                        .header("X-Knowledge-Admin-Key", "test-knowledge-admin-key-32-bytes!!")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewer\":\"knowledge-reviewer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewer").value("knowledge-reviewer"));

        mockMvc.perform(post("/api/v1/admin/knowledge/documents/KD-ADMIN-001/versions/2026.1/publish")
                        .header("X-Knowledge-Admin-Key", "test-knowledge-admin-key-32-bytes!!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // 测试上下文关闭了 Embedding，管理端应明确报告配置缺失，不能返回伪成功。
        mockMvc.perform(post("/api/v1/admin/knowledge/embeddings/index")
                        .header("X-Knowledge-Admin-Key", "test-knowledge-admin-key-32-bytes!!"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("向量模型未启用")));
    }

    /**
     * 验证管理页面本身可以匿名加载，但任何数据操作仍必须通过独立管理密钥。
     * 调用链为“静态资源放行 → 管理页携带请求头 → KnowledgeAdminAuthenticationFilter → Controller”。
     */
    @Test
    void knowledgeAdminPageIsPublicButApiRejectsWrongCredential() throws Exception {
        mockMvc.perform(get("/knowledge-admin.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("X-Knowledge-Admin-Key")));

        mockMvc.perform(post("/api/v1/admin/knowledge/embeddings/index")
                        .header("X-Knowledge-Admin-Key", "wrong-admin-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    /**
     * 覆盖管理 API 的参数校验、导入幂等和版本不可覆盖约束；冲突应返回稳定的 409 契约而非内部错误。
     */
    @Test
    void knowledgeAdminApiValidatesInputAndReportsVersionConflict() throws Exception {
        String path = "/api/v1/admin/knowledge/documents/markdown";
        String key = "test-knowledge-admin-key-32-bytes!!";

        mockMvc.perform(post(path).header("X-Knowledge-Admin-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"markdown\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        String markdown = adminKnowledgeMarkdown().replace("KD-ADMIN-001", "KD-ADMIN-IDEMPOTENT");
        String body = objectMapper.writeValueAsString(Map.of("markdown", markdown));
        mockMvc.perform(post(path).header("X-Knowledge-Admin-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(false));
        mockMvc.perform(post(path).header("X-Knowledge-Admin-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));

        String changed = objectMapper.writeValueAsString(Map.of(
                "markdown", markdown.replace("该内容必须经过独立管理员认证、审核和发布后才能参与召回。",
                        "同版本正文已经被修改，禁止覆盖原草稿。")));
        mockMvc.perform(post(path).header("X-Knowledge-Admin-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(changed))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("提升版本号")));
    }

    /** 验证状态机拒绝跳级发布、重复审批和未发布下线，并保持当前数据库状态不变。 */
    @Test
    void knowledgeAdminApiRejectsIllegalPublicationTransitions() throws Exception {
        String key = "test-knowledge-admin-key-32-bytes!!";
        String markdown = adminKnowledgeMarkdown().replace("KD-ADMIN-001", "KD-ADMIN-STATE");
        mockMvc.perform(post("/api/v1/admin/knowledge/documents/markdown")
                        .header("X-Knowledge-Admin-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("markdown", markdown))))
                .andExpect(status().isOk());

        String base = "/api/v1/admin/knowledge/documents/KD-ADMIN-STATE/versions/2026.1";
        mockMvc.perform(post(base + "/publish").header("X-Knowledge-Admin-Key", key))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        mockMvc.perform(post(base + "/expire").header("X-Knowledge-Admin-Key", key))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        mockMvc.perform(post(base + "/approve").header("X-Knowledge-Admin-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reviewer\":\"reviewer-a\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(post(base + "/approve").header("X-Knowledge-Admin-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reviewer\":\"reviewer-b\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        assertThat(knowledgeMapper.findDocumentIdentity("KD-ADMIN-STATE", "2026.1").reviewer())
                .isEqualTo("reviewer-a");
    }

    /** 验证 multipart 协议在进入 PDFBox 前拒绝空文件和缺失元数据，避免模糊的服务端异常。 */
    @Test
    void knowledgeAdminPdfApiRejectsEmptyFileAndMissingMetadata() throws Exception {
        String key = "test-knowledge-admin-key-32-bytes!!";
        MockMultipartFile emptyPdf = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        MockMultipartFile metadata = new MockMultipartFile("metadata", "", "application/json",
                "{\"documentNo\":\"KD-PDF-API\",\"title\":\"PDF API\",\"productCode\":\"MICRO_BUSINESS\",\"region\":\"ALL\",\"version\":\"2026.1\",\"audience\":\"CUSTOMER\",\"keywords\":[\"PDF\"]}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/admin/knowledge/documents/pdf")
                        .file(emptyPdf).file(metadata).header("X-Knowledge-Admin-Key", key))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

        MockMultipartFile content = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", "%PDF-invalid".getBytes());
        mockMvc.perform(multipart("/api/v1/admin/knowledge/documents/pdf")
                        .file(content).header("X-Knowledge-Admin-Key", key))
                .andExpect(status().isBadRequest());
    }

    @Test
    void classpathKnowledgeBootstrapImportsTenDraftsIdempotently() throws Exception {
        KnowledgeClasspathImporter importer = new KnowledgeClasspathImporter(
                resourcePatternResolver, knowledgeImportService, 1024 * 1024);

        importer.run(null);
        importer.run(null);

        List<String> expectedDocuments = List.of(
                "KB-MB-MATERIALS", "KB-MB-ELIGIBILITY", "KB-MB-PROCESS", "KB-MB-TERM",
                "KB-GENERAL-REPAYMENT", "KB-GENERAL-SETTLEMENT", "KB-PC-MATERIALS",
                "KB-PC-PROCESS", "KB-SERVICE-PAYMENT-NOT-POSTED", "KB-SERVICE-ACCOUNT-SECURITY");
        for (String documentNo : expectedDocuments) {
            KnowledgeDocumentIdentity document = knowledgeMapper.findDocumentIdentity(documentNo, "2026.1");
            assertThat(document).isNotNull();
            assertThat(document.status()).isEqualTo("DRAFT");
            assertThat(knowledgeMapper.countChunks(document.id())).isPositive();
        }
        assertThat(knowledgeMapper.searchPublished("账户安全", "GENERAL_SERVICE", "ALL", 3)).isEmpty();
    }

    @Test
    void everyBusinessTableAndColumnHasDatabaseComment() {
        String businessTables = """
                'customer', 'loan', 'repayment_plan', 'loan_application', 'payment_record',
                'service_ticket', 'chat_session', 'chat_message', 'agent_trace',
                'knowledge_document', 'knowledge_chunk'
                """;
        List<String> uncommentedTables = jdbcTemplate.queryForList("""
                SELECT c.relname
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public'
                  AND c.relkind = 'r'
                  AND c.relname IN (%s)
                  AND COALESCE(obj_description(c.oid, 'pg_class'), '') = ''
                ORDER BY c.relname
                """.formatted(businessTables), String.class);
        List<String> uncommentedColumns = jdbcTemplate.queryForList("""
                SELECT c.relname || '.' || a.attname
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                JOIN pg_attribute a ON a.attrelid = c.oid
                LEFT JOIN pg_description d ON d.objoid = c.oid AND d.objsubid = a.attnum
                WHERE n.nspname = 'public'
                  AND c.relkind = 'r'
                  AND c.relname IN (%s)
                  AND a.attnum > 0
                  AND NOT a.attisdropped
                  AND COALESCE(d.description, '') = ''
                ORDER BY c.relname, a.attnum
                """.formatted(businessTables), String.class);

        assertThat(uncommentedTables).isEmpty();
        assertThat(uncommentedColumns).isEmpty();
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


    @Test
    void supportPermissionsTransitionsAndAuditAreIsolated() throws Exception {
        String key = "test-support-key-at-least-32-bytes!!";
        String customer = login("C10001");
        mockMvc.perform(get("/api/v1/support/tickets")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/support/tickets").header("Authorization", "Bearer " + customer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/support/tickets")
                        .header("X-Knowledge-Admin-Key", "test-knowledge-admin-key-32-bytes!!"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/chat/sessions").header("X-Support-Key", key))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/chat/sessions/S-SUPPORT/messages")
                        .header("Authorization", "Bearer " + customer).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageId":"M-SUPPORT","content":"请转人工客服"}
                                """))
                .andExpect(status().isOk());
        String ticket = jdbcTemplate.queryForObject(
                "SELECT ticket_no FROM service_ticket WHERE conversation_id = 'S-SUPPORT'", String.class);
        mockMvc.perform(get("/api/v1/support/tickets").header("X-Support-Key", key))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].ticketId").exists());
        mockMvc.perform(get("/api/v1/support/tickets/" + ticket + "/context").header("X-Support-Key", key))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(post("/api/v1/support/tickets/" + ticket + "/transition")
                        .header("X-Support-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedStatus":"OPEN","note":"联系 13812345678 处理中"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/support/tickets/" + ticket + "/transition")
                        .header("X-Support-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedStatus":"OPEN","note":"重复接单"}
                                """))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/support/tickets/" + ticket + "/transition")
                        .header("X-Support-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedStatus":"IN_PROGRESS","note":"已完成处理"}
                                """))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_action WHERE ticket_no = ?",
                Integer.class, ticket)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT note FROM ticket_action WHERE ticket_no = ? AND from_status = 'OPEN'",
                String.class, ticket)).contains("[REDACTED]").doesNotContain("13812345678");
        mockMvc.perform(get("/api/v1/support/metrics").header("Authorization", "Bearer " + customer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/support/metrics").header("X-Support-Key", key))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("microloan.agent.round.duration")));
    }

    @Test
    void streamPreservesIdentityTraceAndRejectsForeignLoan() throws Exception {
        String token = login("C10001");
        mockMvc.perform(post("/api/v1/chat/sessions/S-SSE/messages")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageId":"M-SSE-BASE","content":"我下个月要还多少钱","selectedLoanId":"L10001"}
                                """))
                .andExpect(status().isOk()).andReturn();
        MvcResult stream = mockMvc.perform(post("/api/v1/chat/sessions/S-SSE/stream")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageId":"M-SSE","content":"我下个月要还多少钱"}
                                """))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted())
                .andReturn();
        stream.getAsyncResult(10000);
        MvcResult completed = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(stream))
                .andExpect(status().isOk()).andReturn();
        String body = completed.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("event:status", "event:answer", "event:done", "L10001")
                .contains(stream.getResponse().getHeader("X-Trace-Id"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM agent_trace WHERE trace_id = ?", Integer.class,
                stream.getResponse().getHeader("X-Trace-Id"))).isGreaterThan(0);
        MvcResult rejected = mockMvc.perform(post("/api/v1/chat/sessions/S-SSE-FOREIGN/stream")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageId":"M-SSE-FOREIGN","content":"我下个月要还多少钱","selectedLoanId":"L20001"}
                                """))
                .andReturn();
        rejected.getAsyncResult(10000);
        MvcResult error = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(rejected))
                .andExpect(status().isOk()).andReturn();
        assertThat(error.getResponse().getContentAsString()).contains("event:error", "ACCESS_DENIED")
                .doesNotContain("event:answer", "event:done");
    }

    @Test
    void persistentContextCanBeRestoredAndClearedOnlyByOwner() throws Exception {
        String token = login("C10001");
        mockMvc.perform(post("/api/v1/chat/sessions/S-CONTEXT-RESET/messages")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageId":"M-CONTEXT-RESET","content":"我下个月要还多少钱","selectedLoanId":"L10001"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/chat/sessions/S-CONTEXT-RESET/context")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.selectedLoanId").value("L10001"))
                .andExpect(jsonPath("$.recentIntents[0]").value("NEXT_REPAYMENT_QUERY"));
        String other = login("C10002");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/chat/sessions/S-CONTEXT-RESET/context").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/v1/chat/sessions/S-CONTEXT-RESET/context").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/chat/sessions/S-CONTEXT-RESET/context")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.selectedLoanId").isEmpty());
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

    private String nullableText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String unitVectorLiteral(int dimensions) {
        return "[1" + ",0".repeat(dimensions - 1) + "]";
    }

    private String draftKnowledgeMarkdown(String content) {
        return """
                ---
                documentNo: KD-IMPORT-001
                title: 经营贷导入测试
                productCode: MICRO_BUSINESS
                region: ALL
                version: 2026.1
                audience: PUBLIC
                effectiveFrom: 2026-09-01
                effectiveTo:
                reviewer: integration-test
                keywords: 经营贷, 导入测试
                ---
                # 经营贷导入测试
                ## 第一节
                %s

                ## 第二节
                第二个切片用于验证事务写入。
                """.formatted(content);
    }

    private String publicationKnowledgeMarkdown() {
        return """
                ---
                documentNo: KD-PUBLISH-001
                title: 经营贷发布流程测试
                productCode: MICRO_BUSINESS
                region: ALL
                version: 2026.1
                audience: PUBLIC
                effectiveFrom: 2026-09-01
                effectiveTo:
                reviewer: draft-author
                keywords: 经营贷, 发布测试
                ---
                # 经营贷发布流程测试
                ## 对客说明
                该内容仅在审核并发布后参与客户检索。
                """;
    }

    private String adminKnowledgeMarkdown() {
        return """
                ---
                documentNo: KD-ADMIN-001
                title: 管理接口发布流程测试
                productCode: MICRO_BUSINESS
                region: ALL
                version: 2026.1
                audience: CUSTOMER
                effectiveFrom: 2026-09-01
                effectiveTo:
                reviewer: draft-author
                keywords: 管理接口, 发布流程
                ---
                # 管理接口发布流程测试
                ## 对客内容
                该内容必须经过独立管理员认证、审核和发布后才能参与召回。
                """;
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
