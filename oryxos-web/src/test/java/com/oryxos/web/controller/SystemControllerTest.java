package com.oryxos.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.web.common.ApiResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SystemControllerTest {

  private SystemController systemController;

  @BeforeEach
  void setUp() {
    systemController = new SystemController();
  }

  @Test
  @DisplayName("Health endpoint returns status UP and success code")
  void testHealthEndpoint() {
    ApiResponse<Map<String, Object>> response = systemController.health();
    assertThat(response).isNotNull();
    assertThat(response.getCode()).isZero();
    assertThat(response.getData()).containsEntry("status", "UP");
  }

  @Test
  @DisplayName("Info endpoint returns application metadata")
  void testInfoEndpoint() {
    ApiResponse<Map<String, Object>> response = systemController.info();
    assertThat(response).isNotNull();
    assertThat(response.getCode()).isZero();
    assertThat(response.getData()).containsEntry("name", "OryxOS");
  }
}
