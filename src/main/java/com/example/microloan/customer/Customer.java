package com.example.microloan.customer;

public record Customer(Long id, String customerNo, String name, String passwordHash, String status) {}
