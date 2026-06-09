package com.hasan.apiwatch.service;

import com.hasan.apiwatch.dto.UpdateNotificationSettingsRequest;
import com.hasan.apiwatch.entity.NotificationSettings;
import com.hasan.apiwatch.repository.NotificationSettingsRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationSettingsServiceTest {

    private static final String TEST_KEY =
            "YXBpd2F0Y2gtdGVzdC1lbmNyeXB0aW9uLWtleSEhISE=";

    @Test
    void encryptsWebhookAndReturnsOnlyMaskedDisplay() {
        NotificationSettingsRepository repository = mock(NotificationSettingsRepository.class);
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(repository.save(any(NotificationSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        NotificationSettingsService service = new NotificationSettingsService(
                repository,
                new SecretEncryptionService(TEST_KEY)
        );

        var response = service.update(new UpdateNotificationSettingsRequest(
                true,
                "https://hooks.example.com/private/token",
                false,
                300
        ));

        ArgumentCaptor<NotificationSettings> captor =
                ArgumentCaptor.forClass(NotificationSettings.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        NotificationSettings saved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(saved.getWebhookUrlEncrypted())
                .doesNotContain("hooks.example.com")
                .startsWith("v1:");
        assertThat(response.webhookDisplay()).isEqualTo("https://hooks.example.com/****");
        assertThat(response.webhookConfigured()).isTrue();
    }
}
