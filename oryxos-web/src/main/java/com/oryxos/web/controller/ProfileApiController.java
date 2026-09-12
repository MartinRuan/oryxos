package com.oryxos.web.controller;

import com.oryxos.core.model.Profile;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.web.common.ApiResponse;
import com.oryxos.web.dto.ProfileSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profile 安全只读 REST API.
 *
 * @author OryxOS Team
 */
@RestController
@RequestMapping("/api/v1/profiles")
@Tag(name = "Profiles", description = "Profile 安全元数据")
public class ProfileApiController {

  private final ProfileRegistry profileRegistry;

  /** 创建 Profile 查询 API. */
  public ProfileApiController(ProfileRegistry profileRegistry) {
    this.profileRegistry = profileRegistry;
  }

  /** 列出安全 Profile 摘要. */
  @GetMapping
  @Operation(summary = "列出 Profile")
  public ApiResponse<List<ProfileSummary>> list() {
    List<ProfileSummary> profiles =
        profileRegistry.listProfiles().stream()
            .sorted(Comparator.comparing(Profile::getName))
            .map(this::toSummary)
            .toList();
    return ApiResponse.success(profiles);
  }

  private ProfileSummary toSummary(Profile profile) {
    return new ProfileSummary(
        profile.getName(),
        profile.getDescription(),
        profile.getProviderName(),
        profile.getModelName(),
        List.copyOf(profile.getTools()));
  }
}
