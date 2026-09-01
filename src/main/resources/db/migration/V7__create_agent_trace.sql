CREATE TABLE agent_trace (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    customer_id BIGINT NOT NULL REFERENCES customer(id),
    node_sequence INTEGER NOT NULL,
    node_name VARCHAR(50) NOT NULL,
    input_summary VARCHAR(300) NOT NULL,
    output_summary VARCHAR(500),
    model_version VARCHAR(100),
    prompt_version VARCHAR(50),
    latency_ms BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_trace_sequence UNIQUE (trace_id, node_sequence)
);

CREATE INDEX idx_agent_trace_customer ON agent_trace(customer_id, created_at DESC);
