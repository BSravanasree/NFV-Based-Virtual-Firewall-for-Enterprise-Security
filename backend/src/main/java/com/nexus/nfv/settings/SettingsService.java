package com.nexus.nfv.settings;

import com.nexus.nfv.audit.AuditAction;
import com.nexus.nfv.audit.AuditService;
import com.nexus.nfv.common.exception.ApiException;
import com.nexus.nfv.security.CurrentUser;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SettingsService {

    private final SystemSettingRepository repo;
    private final AuditService audit;

    public SettingsService(SystemSettingRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    public record SettingDto(String key, String value, String valueType, String category, String description,
                             String updatedBy, Instant updatedAt) {
        static SettingDto from(SystemSetting s) {
            return new SettingDto(s.getKey(), s.getValue(), s.getValueType(), s.getCategory(), s.getDescription(),
                    s.getUpdatedBy(), s.getUpdatedAt());
        }
    }

    @Transactional(readOnly = true)
    public List<SettingDto> list() {
        return repo.findAll(Sort.by("category", "key")).stream().map(SettingDto::from).toList();
    }

    @Transactional
    public SettingDto update(String key, String value) {
        SystemSetting s = repo.findById(key).orElseThrow(() -> ApiException.notFound("Setting", key));
        String v = value == null ? "" : value.trim();
        switch (s.getValueType()) {
            case "INT" -> {
                try {
                    int n = Integer.parseInt(v);
                    if (n < 1 || n > 1_000_000) throw ApiException.badRequest(key + " must be between 1 and 1000000");
                } catch (NumberFormatException e) {
                    throw ApiException.badRequest(key + " must be an integer");
                }
            }
            case "BOOL" -> {
                if (!v.equals("true") && !v.equals("false")) throw ApiException.badRequest(key + " must be true or false");
            }
            default -> {
                if (v.length() > 255) throw ApiException.badRequest(key + " is too long");
            }
        }
        String old = s.getValue();
        s.setValue(v);
        s.setUpdatedBy(CurrentUser.username());
        s.setUpdatedAt(Instant.now());
        repo.save(s);
        audit.success(AuditAction.SETTING_CHANGED, "SETTINGS", "SETTING", key, key + ": " + old + " -> " + v);
        return SettingDto.from(s);
    }

    @Transactional(readOnly = true)
    public int getInt(String key, int fallback) {
        return repo.findById(key).map(s -> {
            try {
                return Integer.parseInt(s.getValue());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }).orElse(fallback);
    }

    @Transactional(readOnly = true)
    public boolean getBool(String key, boolean fallback) {
        return repo.findById(key).map(s -> Boolean.parseBoolean(s.getValue())).orElse(fallback);
    }
}
