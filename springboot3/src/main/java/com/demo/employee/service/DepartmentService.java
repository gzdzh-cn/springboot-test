package com.demo.employee.service;

import com.demo.employee.dto.DepartmentRequest;
import com.demo.employee.entity.Department;
import com.demo.employee.exception.BusinessException;
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
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public Page<Department> list(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return departmentRepository.findAll(pageable);
    }

    public Department getById(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("部门不存在，ID: " + id));
    }

    @Transactional
    public Department create(DepartmentRequest request) {
        Department department = new Department();
        department.setName(request.getName());
        department.setDescription(request.getDescription());
        return departmentRepository.save(department);
    }

    @Transactional
    public Department update(Long id, DepartmentRequest request) {
        Department department = getById(id);
        department.setName(request.getName());
        department.setDescription(request.getDescription());
        return departmentRepository.save(department);
    }

    @Transactional
    public void delete(Long id) {
        if (!departmentRepository.existsById(id)) {
            throw new BusinessException("部门不存在，ID: " + id);
        }
        departmentRepository.deleteById(id);
    }
}
