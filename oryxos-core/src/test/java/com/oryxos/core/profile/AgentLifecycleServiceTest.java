package com.oryxos.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.config.AgentGenerationProperties;
import com.oryxos.core.exception.OryxException;
import com.oryxos.core.exception.StandardErrorCode;
import com.oryxos.core.model.Profile;
import com.oryxos.core.scheduler.AgentScheduler;
import com.oryxos.core.session.SessionManager;
import com.oryxos.provider.ProviderService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

class AgentLifecycleServiceTest {

  @TempDir Path tempDir;
  private AgentStore store;
  private ProfileRegistry registry;
  private AgentScheduler scheduler;
  private ProviderService providerService;
  private SessionManager sessionManager;
  private AgentLifecycleService service;

  @BeforeEach
  void setUp() {
    store = new AgentStore(tempDir.resolve(".oryxos"));
    registry = new ProfileRegistry();
    scheduler = mock(AgentScheduler.class);
    providerService = mock(ProviderService.class);
    sessionManager = mock(SessionManager.class);
    AgentLoader loader =
        new AgentLoader(
            new ProfileLoader(registry),
            registry,
            scheduler,
            Set.of("deepseek", "minimax"),
            Set.of("read_file"));
    service =
        new AgentLifecycleService(
            store,
            loader,
            registry,
            scheduler,
            providerService,
            sessionManager,
            new AgentGenerationProperties());
  }

  @Test
  void 创建后文件注册和调度立即一致() {
    AgentLifecycleService apiService = spy(service);

    apiService.create("ops", markdown("ops", "deepseek"));

    assertThat(store.read("ops")).contains("name: ops");
    assertThat(registry.exists("ops")).isTrue();
    verify(apiService).register(store.agentDirectory("ops"));
    verify(scheduler).registerProfile(any(Profile.class));
  }

  @Test
  void 注册失败_必须回滚已写的Agent目录_不留半个Agent() {
    doThrow(new IllegalStateException("schedule failed"))
        .when(scheduler)
        .registerProfile(any(Profile.class));

    assertThatThrownBy(() -> service.create("ops", markdown("ops", "deepseek")))
        .isInstanceOf(IllegalStateException.class);
    assertThat(Files.exists(store.agentDirectory("ops"))).isFalse();
    assertThat(registry.exists("ops")).isFalse();
  }

  @Test
  void 删除必须先停定时_再动索引和目录() {
    service.create("ops", markdown("ops", "deepseek"));
    AgentScheduler orderedScheduler = mock(AgentScheduler.class);
    ProfileRegistry orderedRegistry = mock(ProfileRegistry.class);
    Profile existing = new Profile();
    existing.setName("ops");
    when(orderedRegistry.getProfile("ops")).thenReturn(java.util.Optional.of(existing));
    AgentLifecycleService ordered = serviceWith(orderedRegistry, orderedScheduler);

    ordered.delete("ops");

    InOrder order = inOrder(orderedScheduler, orderedRegistry);
    order.verify(orderedScheduler).unregisterProfile(existing);
    order.verify(orderedRegistry).remove("ops");
    assertThat(Files.exists(store.agentDirectory("ops"))).isFalse();
    assertThat(store.listDirectory("archive")).hasSize(1);
  }

  @Test
  void 重名创建在任何写入和注册前返回40001() {
    service.create("ops", markdown("ops", "deepseek"));

    assertThatThrownBy(() -> service.create("ops", markdown("ops", "deepseek")))
        .isInstanceOfSatisfying(
            OryxException.class,
            error ->
                assertThat(error.getErrorCode()).isEqualTo(StandardErrorCode.AGENT_ALREADY_EXISTS));
  }

  @Test
  void 更新失败恢复原文件和原运行时() {
    service.create("ops", markdown("ops", "deepseek"));
    clearInvocations(scheduler);
    doThrow(new IllegalStateException("schedule failed"))
        .when(scheduler)
        .registerProfile(
            org.mockito.ArgumentMatchers.argThat(
                profile -> "minimax".equals(profile.getProviderName())));

    assertThatThrownBy(() -> service.update("ops", markdown("ops", "minimax")))
        .isInstanceOf(IllegalStateException.class);
    assertThat(store.read("ops")).contains("name: deepseek");
    assertThat(registry.getRequiredProfile("ops").getProviderName()).isEqualTo("deepseek");
    InOrder order = inOrder(scheduler);
    order.verify(scheduler).unregisterProfile(any(Profile.class));
    order
        .verify(scheduler)
        .registerProfile(
            org.mockito.ArgumentMatchers.argThat(
                profile -> "minimax".equals(profile.getProviderName())));
  }

  @Test
  void 更新恢复再次失败时附加异常并保留原文件() {
    service.create("ops", markdown("ops", "deepseek"));
    doThrow(new IllegalStateException("new schedule failed"))
        .when(scheduler)
        .registerProfile(
            org.mockito.ArgumentMatchers.argThat(
                profile -> "minimax".equals(profile.getProviderName())));
    doThrow(new IllegalArgumentException("old schedule restore failed"))
        .when(scheduler)
        .registerProfile(
            org.mockito.ArgumentMatchers.argThat(
                profile -> "deepseek".equals(profile.getProviderName())));

    assertThatThrownBy(() -> service.update("ops", markdown("ops", "minimax")))
        .isInstanceOf(IllegalStateException.class)
        .satisfies(
            error ->
                assertThat(error.getSuppressed())
                    .singleElement()
                    .isInstanceOf(IllegalArgumentException.class));
    assertThat(store.read("ops")).contains("name: deepseek");
  }

  @Test
  @SuppressWarnings("PMD.AvoidManuallyCreateThreadRule")
  void 并发重名创建只有一个成功且目录完整() throws Exception {
    int attempts = 4;
    CyclicBarrier gate = new CyclicBarrier(attempts);
    AtomicInteger successes = new AtomicInteger();
    List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
    List<Thread> threads = new ArrayList<>();
    for (int index = 0; index < attempts; index++) {
      threads.add(
          Thread.ofVirtual()
              .unstarted(
                  () -> {
                    try {
                      gate.await();
                      service.create("concurrent", markdown("concurrent", "deepseek"));
                      successes.incrementAndGet();
                    } catch (Throwable error) {
                      failures.add(error);
                    }
                  }));
    }
    threads.forEach(Thread::start);
    for (Thread thread : threads) {
      thread.join();
    }

    assertThat(successes).hasValue(1);
    assertThat(failures)
        .hasSize(attempts - 1)
        .allSatisfy(
            error -> {
              assertThat(error).isInstanceOf(OryxException.class);
              assertThat(((OryxException) error).getErrorCode())
                  .isEqualTo(StandardErrorCode.AGENT_ALREADY_EXISTS);
            });
    assertThat(store.read("concurrent")).contains("name: concurrent");
    assertThat(registry.exists("concurrent")).isTrue();
  }

  @Test
  void 查询视图不返回明文密钥() {
    String definition =
        markdown("secure", "deepseek")
            .replace("  model: model", "  model: model\n  api_key: plain-secret");

    com.oryxos.core.model.AgentView view = service.create("secure", definition);

    assertThat(view.agentMarkdown()).contains("api_key: ***").doesNotContain("plain-secret");
    assertThat(view.provider().toString()).doesNotContain("plain-secret");
  }

  @Test
  void 同名同毫秒归档仍生成唯一目录() {
    AgentStore fixedStore =
        new AgentStore(
            tempDir.resolve("fixed-workspace"),
            Clock.fixed(Instant.parse("2026-09-13T08:00:00Z"), ZoneOffset.UTC));
    fixedStore.create("ops", markdown("ops", "deepseek"));
    Path first = fixedStore.archive("ops");
    fixedStore.create("ops", markdown("ops", "deepseek"));
    Path second = fixedStore.archive("ops");

    assertThat(second).isNotEqualTo(first);
    assertThat(first).isDirectory();
    assertThat(second).isDirectory();
  }

  private AgentLifecycleService serviceWith(
      ProfileRegistry targetRegistry, AgentScheduler targetScheduler) {
    AgentLoader loader =
        new AgentLoader(
            new ProfileLoader(targetRegistry),
            targetRegistry,
            targetScheduler,
            Set.of("deepseek", "minimax"),
            Set.of());
    return new AgentLifecycleService(
        store,
        loader,
        targetRegistry,
        targetScheduler,
        providerService,
        sessionManager,
        new AgentGenerationProperties());
  }

  private String markdown(String name, String provider) {
    return "---\nname: "
        + name
        + "\ndescription: test\nidentity:\n  agent_name: test\n  prompt: test\nprovider:\n  name: "
        + provider
        + "\n  model: model\ntools: []\n---\n任务正文";
  }
}
