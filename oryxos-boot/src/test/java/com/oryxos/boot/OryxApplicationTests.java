package com.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class OryxApplicationTests {

  @Autowired private ApplicationContext applicationContext;

  @Test
  @DisplayName("Spring Application Context loads successfully")
  void contextLoads() {
    assertThat(applicationContext).isNotNull();
  }
}
