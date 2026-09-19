package com.oryxos.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.memory.MemoryService;
import com.oryxos.web.common.ApiResponse;
import com.oryxos.web.dto.MemoryView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Agent 长期记忆查询接口测试. */
@ExtendWith(MockitoExtension.class)
class MemoryApiControllerTest {

  @Mock private MemoryService memoryService;
  @InjectMocks private MemoryApiController controller;

  @Test
  @DisplayName("按Agent名称读取关联记忆并保持无参数兼容")
  void 按Agent名称读取关联记忆并保持无参数兼容() {
    when(memoryService.load("ops-agent")).thenReturn("ops memory");
    when(memoryService.load()).thenReturn("all memory");

    ApiResponse<MemoryView> associated = controller.load("ops-agent");
    ApiResponse<MemoryView> global = controller.load(null);

    assertThat(associated.getData().content()).isEqualTo("ops memory");
    assertThat(global.getData().content()).isEqualTo("all memory");
    verify(memoryService).load("ops-agent");
    verify(memoryService).load();
  }
}
