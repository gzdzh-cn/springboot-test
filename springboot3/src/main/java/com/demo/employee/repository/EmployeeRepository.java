package com.demo.employee.repository;

import com.demo.employee.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    Page<Employee> findByDepartmentId(Long departmentId, Pageable pageable);

    Page<Employee> findByNameContaining(String name, Pageable pageable);

    Page<Employee> findByDepartmentIdAndNameContaining(Long departmentId, String name, Pageable pageable);

    List<Employee> findByDepartmentId(Long departmentId);
}
