package com.example.minijarvis.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customer;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    private LocalDate issueDate;
    private LocalDate dueDate;
    private LocalDate paidDate;
    private String status;

    protected Invoice() {
    }

    public Invoice(String customer, BigDecimal amount, LocalDate issueDate, LocalDate dueDate, LocalDate paidDate,
            String status) {
        this.customer = customer;
        this.amount = amount;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.paidDate = paidDate;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getCustomer() { return customer; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getPaidDate() { return paidDate; }
    public String getStatus() { return status; }
}
