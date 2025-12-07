package com.rag.lecturelens.entity;

import com.rag.lecturelens.domain.PlanType;
import com.rag.lecturelens.domain.Provider;
import com.rag.lecturelens.domain.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // 내부용 PK

    @Column(name = "user_id", nullable = false, unique = true, length = 100)
    private String userId;  // 로그인 ID (JWT sub 등에서 사용)

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Provider provider;   // LOCAL, GOOGLE...

    @Column(name = "provider_id")
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus role;       // ROLE_USER ...

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Lecture> lectures = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type",nullable = false, length = 30)
    private PlanType planType;

    @Column(name = "auto_billing",nullable = false)
    private boolean autoBilling;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "billing_info", columnDefinition = "jsonb")
    private Map<String, Object> billingInfo;

    @Column(name = "daily_usage_limit",nullable = false)
    private int dailyUsageLimit;

    @Column(name = "usage_limit",nullable = false)
    private int usageLimit;

    @PrePersist
    public void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
        this.planType = PlanType.FREE;
        this.autoBilling = false;
        this.dailyUsageLimit = 3;
        if (this.provider == null) this.provider = Provider.LOCAL;
        if (this.role == null) this.role = UserStatus.USER;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
