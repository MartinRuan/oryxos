package com.oryxos.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Agent 消息请求.
 *
 * @param content 消息正文
 */
public record MessageRequest(
    @NotBlank(message = "content must not be blank")
        @Size(max = 32768, message = "content must not exceed 32768 characters")
        String content) {}
