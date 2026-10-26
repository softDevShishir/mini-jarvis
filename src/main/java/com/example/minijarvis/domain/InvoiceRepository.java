package com.example.minijarvis.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Read-only queries used by agents. Overdue is derived from dates, not the stored status. */
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @Query("select i from Invoice i where i.paidDate is null and i.dueDate < :today order by i.dueDate asc")
    List<Invoice> findOverdue(@Param("today") LocalDate today);

    @Query("select i from Invoice i where i.paidDate is null order by i.amount desc")
    List<Invoice> findLargestUnpaid(Pageable pageable);

    @Query("select coalesce(sum(i.amount), 0) from Invoice i where i.paidDate is null")
    BigDecimal totalUnpaid();

    @Query("select coalesce(sum(i.amount), 0) from Invoice i where i.paidDate >= :from and i.paidDate <= :to")
    BigDecimal totalPaidBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
