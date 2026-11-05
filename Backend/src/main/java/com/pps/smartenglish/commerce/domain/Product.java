package com.pps.smartenglish.commerce.domain;

import com.pps.smartenglish.shared.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.Set;
import java.util.TreeSet;

/** Chỉ đọc ở giai đoạn này (CMS sản phẩm làm sau). levelIds: Level lẻ = 1 dòng, Combo = nhiều dòng. */
@Entity
@Table(name = "products")
public class Product extends Auditable {

    public static final String ACTIVE = "ACTIVE";

    @Id
    private Long id;

    private String type;
    private String name;

    @Column(name = "price_vnd")
    private long priceVnd;

    @Column(name = "access_days")
    private Integer accessDays;

    @Column(name = "ai_quota")
    private Integer aiQuota;

    private String status;

    @ElementCollection
    @CollectionTable(name = "product_levels", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "level_id")
    private Set<Long> levelIds = new TreeSet<>();

    protected Product() {}

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getName() { return name; }
    public long getPriceVnd() { return priceVnd; }
    public Integer getAccessDays() { return accessDays; }
    public Integer getAiQuota() { return aiQuota; }
    public boolean isActive() { return ACTIVE.equals(status); }
    public Set<Long> getLevelIds() { return levelIds; }
}
