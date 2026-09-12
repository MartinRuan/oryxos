package com.oryxos.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建 Web 会话请求.
 *
 * @param profileName Profile 名称
 * @param userId 用户标识
 */
public record CreateSessionRequest(
    @NotBlank(message = "profileName must not be blank") String profileName,
    @NotBlank(message = "userId must not be blank") String userId) {}
