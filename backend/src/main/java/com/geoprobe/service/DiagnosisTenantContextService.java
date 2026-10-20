package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geoprobe.entity.User;
import com.geoprobe.entity.v1.Brand;
import com.geoprobe.entity.v1.BrandMembership;
import com.geoprobe.entity.v1.BrandOnboardingSeed;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.entity.v1.Organization;
import com.geoprobe.entity.v1.OrganizationMembership;
import com.geoprobe.entity.v1.QuotaAccount;
import com.geoprobe.entity.v1.WebSurface;
import com.geoprobe.mapper.UserMapper;
import com.geoprobe.mapper.v1.BrandMapper;
import com.geoprobe.mapper.v1.BrandMembershipMapper;
import com.geoprobe.mapper.v1.BrandOnboardingSeedMapper;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import com.geoprobe.mapper.v1.OrganizationMapper;
import com.geoprobe.mapper.v1.OrganizationMembershipMapper;
import com.geoprobe.mapper.v1.QuotaAccountMapper;
import com.geoprobe.mapper.v1.WebSurfaceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DiagnosisTenantContextService {

    private static final int ORG_TYPE_PERSONAL = 0;
    private static final int BILLING_OWNER_USER = 0;
    private static final int MEMBERSHIP_ROLE_OWNER = 0;
    private static final int MEMBERSHIP_SEAT_FULL = 0;
    private static final int MEMBERSHIP_STATUS_ACTIVE = 1;
    private static final int BRAND_STATUS_ACTIVE = 1;
    private static final int BRAND_ACCESS_OWNER = 0;
    private static final int SCOPE_STATUS_ACTIVE = 1;
    private static final int SURFACE_TYPE_DOMAIN = 0;
    private static final int SURFACE_STATUS_ACTIVE = 1;
    private static final int ONBOARDING_SEED_OFFICIAL_WEBSITE = 0;
    private static final String QUOTA_SUBJECT_PERSONAL = "PERSONAL";

    private final UserMapper userMapper;
    private final OrganizationMapper organizationMapper;
    private final OrganizationMembershipMapper organizationMembershipMapper;
    private final BrandMapper brandMapper;
    private final BrandMembershipMapper brandMembershipMapper;
    private final BrandOnboardingSeedMapper brandOnboardingSeedMapper;
    private final BrandScopeMapper brandScopeMapper;
    private final WebSurfaceMapper webSurfaceMapper;
    private final QuotaAccountMapper quotaAccountMapper;

    @Value("${app.database.v1.enabled:false}")
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    @Transactional(rollbackFor = Exception.class)
    public TenantContext ensureUserTenancy(String userId) {
        if (!enabled || !StringUtils.hasText(userId)) {
            return null;
        }
        User user = userMapper.selectById(userId.trim());
        if (user == null) {
            return null;
        }
        return ensureTenantContext(user, null, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public TenantContext ensureBrandTenancy(String userId,
                                            String brandName,
                                            String category,
                                            String brandWebsite) {
        if (!enabled || !StringUtils.hasText(userId) || !StringUtils.hasText(brandName)) {
            return null;
        }
        User user = userMapper.selectById(userId.trim());
        if (user == null) {
            return null;
        }
        return ensureTenantContext(user, brandName, category, brandWebsite);
    }

    @Transactional(rollbackFor = Exception.class)
    public TenantContext ensureTenantContext(User user,
                                             String brandName,
                                             String category,
                                             String brandWebsite) {
        if (!enabled || user == null) {
            return null;
        }
        Objects.requireNonNull(user, "user 不能为空");
        Organization organization = organizationMapper.selectOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getOwnerUserId, user.getId())
                .eq(Organization::getOrganizationTypeCode, ORG_TYPE_PERSONAL)
                .last("limit 1"));
        if (organization == null) {
            organization = Organization.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .ownerUserId(user.getId())
                    .organizationName(resolveOrganizationName(user))
                    .organizationTypeCode(ORG_TYPE_PERSONAL)
                    .billingOwnerTypeCode(BILLING_OWNER_USER)
                    .statusCode(1)
                    .build();
            organizationMapper.insert(organization);
        }
        userMapper.updateDefaultOrganizationId(user.getId(), organization.getId());
        OrganizationMembership membership = organizationMembershipMapper.selectOne(new LambdaQueryWrapper<OrganizationMembership>()
                .eq(OrganizationMembership::getOrganizationId, organization.getId())
                .eq(OrganizationMembership::getUserId, user.getId())
                .last("limit 1"));
        if (membership == null) {
            membership = OrganizationMembership.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .organizationId(organization.getId())
                    .userId(user.getId())
                    .roleCode(MEMBERSHIP_ROLE_OWNER)
                    .seatTypeCode(MEMBERSHIP_SEAT_FULL)
                    .statusCode(MEMBERSHIP_STATUS_ACTIVE)
                    .joinedAt(LocalDateTime.now())
                    .build();
            organizationMembershipMapper.insert(membership);
        }
        QuotaAccount quotaAccount = quotaAccountMapper.selectOne(new LambdaQueryWrapper<QuotaAccount>()
                .eq(QuotaAccount::getOrganizationId, organization.getId())
                .eq(QuotaAccount::getSubjectTypeCode, QUOTA_SUBJECT_PERSONAL)
                .eq(QuotaAccount::getSubjectUserId, user.getId())
                .last("limit 1"));
        if (quotaAccount == null) {
            quotaAccount = QuotaAccount.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .organizationId(organization.getId())
                    .subjectTypeCode(QUOTA_SUBJECT_PERSONAL)
                    .subjectUserId(user.getId())
                    .subjectMembershipId(membership.getId())
                    .statusCode(1)
                    .availableProbeQuota(longOrZero(user.getCreditsRemaining()))
                    .reservedProbeQuota(0L)
                    .consumedProbeQuota(0L)
                    .build();
            quotaAccountMapper.insert(quotaAccount);
        }
        Brand brand = null;
        BrandScope brandScope = null;
        if (StringUtils.hasText(brandName)) {
            brand = brandMapper.selectOne(new LambdaQueryWrapper<Brand>()
                    .eq(Brand::getOrganizationId, organization.getId())
                    .eq(Brand::getBrandName, brandName.trim())
                    .last("limit 1"));
            if (brand == null) {
                brand = Brand.builder()
                        .id(java.util.UUID.randomUUID().toString())
                        .organizationId(organization.getId())
                        .createdByMembershipId(membership.getId())
                        .brandName(brandName.trim())
                        .categoryName(StringUtils.hasText(category) ? category.trim() : null)
                        .canonicalDomain(extractHost(brandWebsite))
                        .officialWebsite(brandWebsite)
                        .officialDomainsJson(extractHost(brandWebsite) == null ? List.of() : List.of(extractHost(brandWebsite)))
                        .brandAliasesJson(List.of(brandName.trim()))
                        .statusCode(BRAND_STATUS_ACTIVE)
                        .build();
                brandMapper.insert(brand);
            }
            ensureBrandMembership(organization.getId(), brand.getId(), membership.getId());
            ensureBrandOnboardingSeed(organization.getId(), brand, membership.getId(), brandWebsite);
            if (StringUtils.hasText(category)) {
                brandScope = brandScopeMapper.selectOne(new LambdaQueryWrapper<BrandScope>()
                        .eq(BrandScope::getBrandId, brand.getId())
                        .eq(BrandScope::getCategorySnapshot, category.trim())
                        .eq(BrandScope::getRegionCode, "CN")
                        .eq(BrandScope::getLanguage, "zh-CN")
                        .last("limit 1"));
                if (brandScope == null) {
                    brandScope = BrandScope.builder()
                            .id(java.util.UUID.randomUUID().toString())
                            .organizationId(organization.getId())
                            .brandId(brand.getId())
                            .createdByMembershipId(membership.getId())
                            .scopeName(brandName.trim() + " / " + category.trim())
                            .categorySnapshot(category.trim())
                            .regionCode("CN")
                            .locale("zh-CN")
                            .language("zh-CN")
                            .primaryDomain(extractHost(brandWebsite))
                            .scopeStatusCode(SCOPE_STATUS_ACTIVE)
                            .build();
                    brandScopeMapper.insert(brandScope);
                }
                createPrimarySurfaceIfNeeded(organization.getId(), brand, brandScope, membership.getId(), brandWebsite);
            }
        }
        return new TenantContext(user, organization, membership, brand, brandScope, quotaAccount);
    }

    private void createPrimarySurfaceIfNeeded(String organizationId,
                                              Brand brand,
                                              BrandScope brandScope,
                                              String membershipId,
                                              String brandWebsite) {
        if (brand == null || brandScope == null || !StringUtils.hasText(brandWebsite)) {
            return;
        }
        String host = extractHost(brandWebsite);
        String path = extractPath(brandWebsite);
        if (!StringUtils.hasText(host)) {
            return;
        }
        WebSurface existing = webSurfaceMapper.selectOne(new LambdaQueryWrapper<WebSurface>()
                .eq(WebSurface::getBrandScopeId, brandScope.getId())
                .eq(WebSurface::getHost, host)
                .eq(WebSurface::getPathPrefix, path == null ? "" : path)
                .last("limit 1"));
        if (existing != null) {
            return;
        }
        webSurfaceMapper.insert(WebSurface.builder()
                .id(java.util.UUID.randomUUID().toString())
                .organizationId(organizationId)
                .brandId(brand.getId())
                .brandScopeId(brandScope.getId())
                .createdByMembershipId(membershipId)
                .surfaceTypeCode(SURFACE_TYPE_DOMAIN)
                .host(host)
                .pathPrefix(path == null ? "" : path)
                .fullUrl(brandWebsite)
                .officialFlag(true)
                .isPrimary(true)
                .regionCode("CN")
                .language("zh-CN")
                .statusCode(SURFACE_STATUS_ACTIVE)
                .build());
    }

    private void ensureBrandMembership(String organizationId, String brandId, String membershipId) {
        if (!StringUtils.hasText(organizationId) || !StringUtils.hasText(brandId) || !StringUtils.hasText(membershipId)) {
            return;
        }
        BrandMembership existing = brandMembershipMapper.selectOne(new LambdaQueryWrapper<BrandMembership>()
                .eq(BrandMembership::getBrandId, brandId)
                .eq(BrandMembership::getMembershipId, membershipId)
                .last("limit 1"));
        if (existing != null) {
            return;
        }
        brandMembershipMapper.insert(BrandMembership.builder()
                .id(java.util.UUID.randomUUID().toString())
                .organizationId(organizationId)
                .brandId(brandId)
                .membershipId(membershipId)
                .accessRoleCode(BRAND_ACCESS_OWNER)
                .statusCode(1)
                .build());
    }

    private void ensureBrandOnboardingSeed(String organizationId,
                                           Brand brand,
                                           String membershipId,
                                           String brandWebsite) {
        if (brand == null || !StringUtils.hasText(brandWebsite) || !StringUtils.hasText(organizationId)) {
            return;
        }
        BrandOnboardingSeed existing = brandOnboardingSeedMapper.selectOne(new LambdaQueryWrapper<BrandOnboardingSeed>()
                .eq(BrandOnboardingSeed::getBrandId, brand.getId())
                .eq(BrandOnboardingSeed::getSourceUrl, brandWebsite.trim())
                .last("limit 1"));
        if (existing != null) {
            return;
        }
        List<String> officialTerms = new ArrayList<>();
        if (StringUtils.hasText(brand.getBrandName())) {
            officialTerms.add(brand.getBrandName().trim());
        }
        if (brand.getBrandAliasesJson() != null) {
            for (String alias : brand.getBrandAliasesJson()) {
                if (StringUtils.hasText(alias) && !officialTerms.contains(alias.trim())) {
                    officialTerms.add(alias.trim());
                }
            }
        }
        brandOnboardingSeedMapper.insert(BrandOnboardingSeed.builder()
                .id(java.util.UUID.randomUUID().toString())
                .organizationId(organizationId)
                .brandId(brand.getId())
                .sourceUrl(brandWebsite.trim())
                .sourceDomain(extractHost(brandWebsite))
                .sourceTypeCode(ONBOARDING_SEED_OFFICIAL_WEBSITE)
                .officialTermsJson(officialTerms)
                .brandAliasesJson(brand.getBrandAliasesJson())
                .seedSnapshotJson(java.util.Map.of(
                        "brandName", brand.getBrandName(),
                        "category", brand.getCategoryName(),
                        "officialWebsite", brandWebsite.trim()
                ))
                .extractionVersion("manual-v1")
                .approvedByMembershipId(membershipId)
                .approvedAt(LocalDateTime.now())
                .build());
    }

    private String resolveOrganizationName(User user) {
        if (StringUtils.hasText(user.getCompanyName())) {
            return user.getCompanyName().trim();
        }
        if (StringUtils.hasText(user.getDisplayName())) {
            return user.getDisplayName().trim() + " Workspace";
        }
        return user.getUsername() + " Workspace";
    }

    private String extractHost(String website) {
        URI uri = toUri(website);
        return uri == null ? null : uri.getHost();
    }

    private String extractPath(String website) {
        URI uri = toUri(website);
        if (uri == null) {
            return "";
        }
        String path = uri.getPath();
        return StringUtils.hasText(path) ? path.trim() : "";
    }

    private URI toUri(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            String normalized = value.trim();
            if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                normalized = "https://" + normalized;
            }
            return URI.create(normalized);
        } catch (Exception ex) {
            return null;
        }
    }

    private long longOrZero(Integer value) {
        return value == null ? 0L : value.longValue();
    }

    public record TenantContext(User user,
                                Organization organization,
                                OrganizationMembership membership,
                                Brand brand,
                                BrandScope brandScope,
                                QuotaAccount quotaAccount) {
    }
}
