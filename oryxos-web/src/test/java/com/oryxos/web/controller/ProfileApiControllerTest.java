package com.oryxos.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oryxos.core.model.Profile;
import com.oryxos.core.profile.ProfileRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProfileApiControllerTest {

  @Test
  void returnsAgentAndSafeScheduleSummary() throws Exception {
    Profile profile = new Profile();
    profile.setName("ops-agent");
    profile.setDescription("Operations agent");

    Profile.ProviderConfig provider = new Profile.ProviderConfig("minimax", "MiniMax-M2.5", 0.2);
    provider.setApiKey("secret-api-key");
    profile.setProvider(provider);
    profile.setTools(List.of("http_get", "notify"));

    Profile.ScheduleConfig schedule =
        new Profile.ScheduleConfig("0 0 9 * * *", "confidential prompt", "Asia/Shanghai");
    schedule.setId("daily-report");
    profile.setSchedules(List.of(schedule));
    profile.setNotifyChannels(
        List.of(
            new Profile.NotifyChannelConfig(
                "dingtalk", "dingtalk", Map.of("url", "https://secret.example/webhook"))));

    ProfileRegistry profileRegistry = mock(ProfileRegistry.class);
    when(profileRegistry.listProfiles()).thenReturn(List.of(profile));
    MockMvc mockMvc =
        MockMvcBuilders.standaloneSetup(new ProfileApiController(profileRegistry)).build();

    mockMvc
        .perform(get("/api/v1/profiles"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].name").value("ops-agent"))
        .andExpect(jsonPath("$.data[0].schedules[0].id").value("daily-report"))
        .andExpect(jsonPath("$.data[0].schedules[0].cron").value("0 0 9 * * *"))
        .andExpect(jsonPath("$.data[0].schedules[0].timezone").value("Asia/Shanghai"))
        .andExpect(content().string(not(containsString("confidential prompt"))))
        .andExpect(content().string(not(containsString("secret-api-key"))))
        .andExpect(content().string(not(containsString("secret.example"))));
  }
}
