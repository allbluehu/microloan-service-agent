package com.example.microloan.agent;

import java.time.LocalDate;

/** 一轮对话所需的客户输入与显式业务槽位。 */
public record AgentRequest(
        /** 客户端会话标识。 */
        String sessionId,
        /** 客户端消息幂等标识。 */
        String messageId,
        /** 客户自然语言输入。 */
        String content,
        /** 本轮显式选择的贷款编号。 */
        String selectedLoanId,
        /** 本轮显式选择的申请编号。 */
        String selectedApplicationId,
        /** 本轮显式选择的知识产品编码。 */
        String selectedProductCode,
        /** 本轮显式选择的知识地区编码。 */
        String selectedRegion,
        /** 提前结清试算日期。 */
        LocalDate settlementDate
) {}
