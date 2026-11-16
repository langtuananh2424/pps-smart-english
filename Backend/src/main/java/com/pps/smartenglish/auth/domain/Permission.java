package com.pps.smartenglish.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Quyền là đơn vị nhỏ nhất, mã dạng module.action (FR-ADM-04). Danh mục cố định, do migration tạo. */
@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false, updatable = false)
    private String module;

    @Column(nullable = false, updatable = false)
    private String description;

    protected Permission() {}

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getModule() { return module; }
    public String getDescription() { return description; }
}
