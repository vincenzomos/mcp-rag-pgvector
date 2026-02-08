package com.ragpgvector.service;

import org.springframework.context.annotation.Description;

/**
 * A small DTO used for elicitation flows when a specific time code is requested.
 * Fields are annotated with @Description so MCP/clients can present helpful labels in UI flows.
 */
public record TimeCodeRequest(
        @Description("The full user question") String fullPrompt,
        @Description("The specific time code if mentioned (e.g. STLBL or STBH)") String timeCode
) {}

