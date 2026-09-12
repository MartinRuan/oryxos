package com.oryxos.web.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oryxos.core.exception.OryxException;
import com.oryxos.core.exception.StandardErrorCode;
import com.oryxos.provider.exception.ProviderErrorCode;
import com.oryxos.provider.exception.ProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("业务异常映射到约定HTTP状态且响应体统一")
  void 业务异常映射到约定状态码且响应体统一() throws Exception {
    mockMvc
        .perform(get("/test-errors/bad-request"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(40000))
        .andExpect(jsonPath("$.message").isString())
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(jsonPath("$.timestamp").isNumber());

    mockMvc
        .perform(get("/test-errors/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(40400));

    mockMvc
        .perform(get("/test-errors/provider-unavailable"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value(50310));

    mockMvc
        .perform(get("/test-errors/timeout"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.code").value(50400));
  }

  @Test
  @DisplayName("内部异常细节绝不能出现在500响应里")
  void 内部异常细节_绝不能出现在500响应里() throws Exception {
    mockMvc
        .perform(get("/test-errors/internal"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value(50000))
        .andExpect(jsonPath("$.message").value("Internal server error"))
        .andExpect(jsonPath("$.timestamp").isNumber())
        .andExpect(content().string(not(containsString("jdbc:sqlite"))));
  }

  @RestController
  static class ThrowingController {

    @GetMapping("/test-errors/bad-request")
    String badRequest() {
      throw new OryxException(StandardErrorCode.INVALID_PARAMETER, "invalid request");
    }

    @GetMapping("/test-errors/not-found")
    String notFound() {
      throw new OryxException(StandardErrorCode.NOT_FOUND, "missing session");
    }

    @GetMapping("/test-errors/provider-unavailable")
    String providerUnavailable() {
      throw new ProviderException(
          ProviderErrorCode.PROVIDER_SERVICE_UNAVAILABLE,
          "deepseek",
          "deepseek-chat",
          "provider offline");
    }

    @GetMapping("/test-errors/timeout")
    String timeout() {
      throw new AgentInvocationTimeoutException();
    }

    @GetMapping("/test-errors/internal")
    String internal() {
      throw new IllegalStateException("jdbc:sqlite:/data/oryxos.db connect failed");
    }
  }
}
