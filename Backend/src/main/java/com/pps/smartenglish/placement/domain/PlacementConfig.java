package com.pps.smartenglish.placement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Bảng một dòng (id = 1). maxRetakes null = không cho làm lại (D-23). */
@Entity
@Table(name = "placement_config")
public class PlacementConfig {

    public static final int ID = 1;

    @Id
    private Integer id = ID;

    @Column(name = "max_retakes")
    private Integer maxRetakes;

    protected PlacementConfig() {}

    public Integer getMaxRetakes() { return maxRetakes; }
    public void setMaxRetakes(Integer maxRetakes) { this.maxRetakes = maxRetakes; }

    /** Số lượt tối đa của một học viên: 1 + số lần làm lại. */
    public int allowedAttempts() {
        return 1 + (maxRetakes == null ? 0 : maxRetakes);
    }
}
