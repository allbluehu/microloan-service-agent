package com.example.microloan.customer;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CustomerMapper {

    @Select("""
            SELECT id, customer_no, name, password_hash, status
            FROM customer
            WHERE customer_no = #{customerNo}
            """)
    Customer findByCustomerNo(String customerNo);
}
