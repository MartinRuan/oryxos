package com.oryxos.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oryxos.core.model.Profile;
import com.oryxos.core.model.ProviderDescriptor;
import com.oryxos.provider.ProviderService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

class ProfileRegistryRuntimeTest {

  @Test
  void register后立即可见_remove后exists立即为false() {
    ProfileRegistry registry = new ProfileRegistry();
    Profile profile = validProfile("runtime-agent");

    registry.register(profile);

    assertThat(registry.exists("runtime-agent")).isTrue();
    assertThat(registry.getProfile("runtime-agent")).containsSame(profile);

    registry.remove("runtime-agent");

    assertThat(registry.exists("runtime-agent")).isFalse();
    assertThat(registry.size()).isZero();
  }

  @Test
  void 启动加载与运行时注册非法配置_异常类型和消息完全一致() {
    ProfileRegistry registry = new ProfileRegistry();
    ProfileLoader loader = new ProfileLoader(registry);

    Throwable startupFailure = catchThrowable(() -> loader.parse("name: invalid-agent"));
    Profile invalid = new Profile();
    invalid.setName("invalid-agent");
    Throwable runtimeFailure = catchThrowable(() -> registry.register(invalid));

    assertThat(runtimeFailure).isExactlyInstanceOf(startupFailure.getClass());
    assertThat(runtimeFailure.getMessage()).isEqualTo(startupFailure.getMessage());
  }

  @Test
  void 未配置provider在启动与运行时路径_异常类型和消息完全一致() {
    ProfileLoader loader = new ProfileLoader();
    String yaml =
        """
        name: provider-agent
        provider:
          name: unknown-provider
        """;
    Throwable startupFailure =
        catchThrowable(() -> loader.parse(yaml, java.util.Set.of("deepseek")));

    ProviderService providerService = mock(ProviderService.class);
    when(providerService.getProvider("unknown-provider")).thenReturn(java.util.Optional.empty());
    when(providerService.listProviders())
        .thenReturn(
            List.of(
                ProviderDescriptor.builder()
                    .name("deepseek")
                    .defaultModel("deepseek-chat")
                    .build()));
    ProfileRegistry registry = new ProfileRegistry();
    registry.setProviderService(providerService);
    Profile profile =
        Profile.builder()
            .name("provider-agent")
            .provider(new Profile.ProviderConfig("unknown-provider", "model", 0.2))
            .build();
    Throwable runtimeFailure = catchThrowable(() -> registry.register(profile));

    assertThat(runtimeFailure).isExactlyInstanceOf(startupFailure.getClass());
    assertThat(runtimeFailure.getMessage()).isEqualTo(startupFailure.getMessage());
  }

  @Test
  void 并发注册完成后所有Agent均可见() throws Exception {
    ProfileRegistry registry = new ProfileRegistry();
    int count = 20;
    CountDownLatch ready = new CountDownLatch(count);
    CountDownLatch start = new CountDownLatch(1);
    List<Thread> threads = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      String name = "agent-" + index;
      Thread thread =
          Thread.ofVirtual()
              .unstarted(
                  () -> {
                    ready.countDown();
                    try {
                      start.await();
                      registry.register(validProfile(name));
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                    }
                  });
      threads.add(thread);
      thread.start();
    }
    ready.await();
    start.countDown();
    for (Thread thread : threads) {
      thread.join();
    }

    assertThat(registry.size()).isEqualTo(count);
    assertThat(registry.listProfiles()).extracting(Profile::getName).hasSize(count);
  }

  private Profile validProfile(String name) {
    return Profile.builder()
        .name(name)
        .provider(new Profile.ProviderConfig("deepseek", "deepseek-chat", 0.2))
        .build();
  }
}
