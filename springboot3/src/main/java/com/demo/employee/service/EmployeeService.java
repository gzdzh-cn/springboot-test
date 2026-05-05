package com.demo.employee.service;

import com.demo.employee.dto.EmployeeRequest;
import com.demo.employee.entity.Department;
import com.demo.employee.entity.Employee;
import com.demo.employee.exception.BusinessException;
import com.demo.employee.repository.EmployeeRepository;
import com.demo.employee.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;

    public Page<Employee> list(Long departmentId, String name, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        if (departmentId != null && name != null && !name.trim().isEmpty()) {
            return employeeRepository.findByDepartmentIdAndNameContaining(departmentId, name.trim(), pageable);
        } else if (departmentId != null) {
            return employeeRepository.findByDepartmentId(departmentId, pageable);
        } else if (name != null && !name.trim().isEmpty()) {
            return employeeRepository.findByNameContaining(name.trim(), pageable);
        }
        return employeeRepository.findAll(pageable);
    }

    public Employee getById(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("员工不存在，ID: " + id));
    }

    @Transactional
    public Employee create(EmployeeRequest request) {
        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new BusinessException("部门不存在，ID: " + request.getDepartmentId()));
        Employee employee = new Employee();
        employee.setName(request.getName());
        employee.setEmail(request.getEmail());
        employee.setPhone(request.getPhone());
        employee.setDepartment(department);
        return employeeRepository.save(employee);
    }

    @Transactional
    public Employee update(Long id, EmployeeRequest request) {
        Employee employee = getById(id);
        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new BusinessException("部门不存在，ID: " + request.getDepartmentId()));
        employee.setName(request.getName());
        employee.setEmail(request.getEmail());
        employee.setPhone(request.getPhone());
        employee.setDepartment(department);
        return employeeRepository.save(employee);
    }

    @Transactional
    public void delete(Long id) {
        if (!employeeRepository.existsById(id)) {
            throw new BusinessException("员工不存在，ID: " + id);
        }
        employeeRepository.deleteById(id);
    }
}
