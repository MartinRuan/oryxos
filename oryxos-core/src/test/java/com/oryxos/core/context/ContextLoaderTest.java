package com.oryxos.core.context;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oryxos.core.context.impl.ContextLoaderImpl;
import com.oryxos.core.model.Profile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContextLoaderTest {

  @TempDir Path tempDir;

  private ContextLoader contextLoader;

  @BeforeEach
  void setUp() {
    contextLoader = new ContextLoaderImpl(tempDir);
  }

  @Test
  @DisplayName("动态加载无内存缓存：Skill 外部修改后即时读取最新内容")
  void Skill修改即时生效_无内存缓存() throws IOException {
    Path skillDir = tempDir.resolve(".oryxos").resolve("skills").resolve("k8s-debug");
    Files.createDirectories(skillDir);
    Path skillFile = skillDir.resolve("SKILL.md");

    // 1. 写入初始版本内容
    Files.writeString(skillFile, "v1: 排查 Pod 状态与重启次数", StandardCharsets.UTF_8);

    Profile profile = Profile.builder().name("ops-agent").skills(List.of("k8s-debug")).build();

    String firstLoad = contextLoader.loadContext(profile);
    assertTrue(firstLoad.contains("v1: 排查 Pod 状态与重启次数"));

    // 2. 修改文件内容（模拟热更新/外部编辑）
    Files.writeString(skillFile, "v2: 增加日志排查与事件监听", StandardCharsets.UTF_8);

    // 3. 再次加载，必须立即获取最新内容，不可被内存缓存滞留
    String secondLoad = contextLoader.loadContext(profile);
    assertTrue(secondLoad.contains("v2: 增加日志排查与事件监听"));
    assertFalse(secondLoad.contains("v1: 排查 Pod 状态与重启次数"));
  }

  @Test
  @DisplayName("边界处理：缺失 Bootstrap 优雅跳过，缺失必需 Skill 抛出明确异常")
  void 文件不存在边界处理() {
    // 1. 缺失 Bootstrap 文件：优雅降级不抛异常
    Profile profileWithMissingBootstrap =
        Profile.builder().name("test-agent").bootstrap(List.of("MISSING_BOOTSTRAP.md")).build();

    String context = contextLoader.loadContext(profileWithMissingBootstrap);
    assertNotNull(context);
    assertTrue(context.isEmpty());

    // 2. 缺失必需 Skill 文件：抛出 IllegalStateException 明确告警
    Profile profileWithMissingSkill =
        Profile.builder().name("test-agent").skills(List.of("non-existent-skill")).build();

    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class, () -> contextLoader.loadContext(profileWithMissingSkill));

    assertTrue(ex.getMessage().contains("Required skill file not found"));
  }
}
