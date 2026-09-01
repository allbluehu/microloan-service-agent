package com.example.microloan.ticket;

import java.time.OffsetDateTime;

public record ServiceTicket(
        /** 工单对外编号。 */
        String ticketId,
        /** 创建工单的对话会话标识。 */
        String conversationId,
        /** 工单关联的贷款编号，可为空。 */
        String relatedLoanId,
        /** 工单业务类型。 */
        TicketType type,
        /** 工单处理优先级。 */
        TicketPriority priority,
        /** 已控制长度的客户问题摘要。 */
        String summary,
        /** 工单当前处理状态。 */
        String status,
        /** 工单创建时间。 */
        OffsetDateTime createdAt
) {}
