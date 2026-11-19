package com.pps.smartenglish.commerce.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * Một gói bán (FR-COM-01, D-127). Gói LEVEL = một level + một mức thời hạn: cùng một khóa có nhiều gói với
 * accessDays khác nhau, số mức do admin quyết định. Gói AI_ADDON không gắn level, không có thời hạn.
 */
@Entity
@Table(name = "products")
public class Product extends Auditable {

    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";
    public static final String LEVEL = "LEVEL";
    public static final String AI_ADDON = "AI_ADDON";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type;
    private String name;

    @Column(name = "price_vnd")
    private long priceVnd;

    @Column(name = "access_days")
    private Integer accessDays;

    @Column(name = "ai_quota")
    private Integer aiQuota;

    private String status = ACTIVE;

    @ElementCollection
    @CollectionTable(name = "product_levels", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "level_id")
    private Set<Long> levelIds = new TreeSet<>();

    protected Product() {}

    public Product(String type, String name, long priceVnd, Integer accessDays, Integer aiQuota, Collection<Long> levelIds) {
        this.type = type;
        this.name = name;
        this.priceVnd = priceVnd;
        this.accessDays = accessDays;
        this.aiQuota = aiQuota;
        this.levelIds.addAll(levelIds);
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getName() { return name; }
    public long getPriceVnd() { return priceVnd; }
    public Integer getAccessDays() { return accessDays; }
    public Integer getAiQuota() { return aiQuota; }
    public String getStatus() { return status; }
    public boolean isActive() { return ACTIVE.equals(status); }
    public Set<Long> getLevelIds() { return levelIds; }

    public void setName(String name) { this.name = name; }
    public void setPriceVnd(long priceVnd) { this.priceVnd = priceVnd; }
    public void setAccessDays(Integer accessDays) { this.accessDays = accessDays; }
    public void setAiQuota(Integer aiQuota) { this.aiQuota = aiQuota; }
    public void setStatus(String status) { this.status = status; }
}
