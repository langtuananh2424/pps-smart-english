package com.pps.smartenglish.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_task_weights")
public class AiTaskWeight {

    @Id
    @Column(length = 30)
    private String code;

    @Column(nullable = false)
    private int weight;

    protected AiTaskWeight() {}

    public String getCode() { return code; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }
}
