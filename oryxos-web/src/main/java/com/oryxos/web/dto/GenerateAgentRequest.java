package com.oryxos.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 一句话生成 Agent 请求.
 *
 * @author oryxos
 */
public record GenerateAgentRequest(
    @NotBlank(message = "must not be blank")
        @Size(max = 4096, message = "must not exceed 4096 characters")
        String sentence) {}
