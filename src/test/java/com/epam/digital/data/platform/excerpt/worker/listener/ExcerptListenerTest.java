package com.epam.digital.data.platform.excerpt.worker.listener;

import static org.mockito.Mockito.verify;

import com.epam.digital.data.platform.excerpt.model.ExcerptEventDto;
import com.epam.digital.data.platform.excerpt.model.Request;
import com.epam.digital.data.platform.excerpt.worker.service.ExcerptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExcerptListenerTest {

  ExcerptListener instance;

  @Mock
  ExcerptService excerptService;

  @BeforeEach
  void setup() {
    instance = new ExcerptListener(excerptService);
  }

  @Test
  void callService() {
    var input = new Request<ExcerptEventDto>();

    instance.generate(input);

    verify(excerptService).generateExcerpt(input.getPayload());
  }
}
