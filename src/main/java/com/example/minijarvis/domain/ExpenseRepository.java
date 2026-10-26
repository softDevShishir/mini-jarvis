package com.example.minijarvis.domain;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @Query("select e from Expense e where e.expenseDate >= :from and e.expenseDate <= :to order by e.expenseDate")
    List<Expense> findBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
