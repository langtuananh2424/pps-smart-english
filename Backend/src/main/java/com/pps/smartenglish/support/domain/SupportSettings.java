package com.pps.smartenglish.support.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** BQ-09: cấu hình hỗ trợ do admin nhập; một dòng id = 1. */
@Entity
@Table(name = "support_settings")
public class SupportSettings {

    @Id
    private Integer id = 1;

    private String timezone;

    @Column(name = "eta_message")
    private String etaMessage;

    @Column(name = "zalo_url")
    private String zaloUrl;

    private String hotline;

    protected SupportSettings() {}

    public String getTimezone() { return timezone; }
    public String getEtaMessage() { return etaMessage; }
    public String getZaloUrl() { return zaloUrl; }
    public String getHotline() { return hotline; }

    public void update(String timezone, String etaMessage, String zaloUrl, String hotline) {
        this.timezone = timezone;
        this.etaMessage = etaMessage;
        this.zaloUrl = zaloUrl;
        this.hotline = hotline;
    }
}
