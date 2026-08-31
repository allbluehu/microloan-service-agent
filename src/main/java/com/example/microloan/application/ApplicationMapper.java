package com.example.microloan.application;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApplicationMapper {

    @Select("""
            SELECT application_no, customer_id, product_code, status, reason_code,
                   missing_documents::text AS missing_documents, updated_at
            FROM loan_application
            WHERE application_no = #{applicationNo}
            """)
    ApplicationRow findByApplicationNo(String applicationNo);
}
