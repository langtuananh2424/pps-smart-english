package com.pps.smartenglish.retention.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Sổ cái XP: unique (user, source, ref) là cơ chế chống cộng trùng. Chỉ chèn qua XpEventRepository.insertIfAbsent. */
@Entity
@Table(name = "xp_events")
public class XpEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    protected XpEvent() {}
}
