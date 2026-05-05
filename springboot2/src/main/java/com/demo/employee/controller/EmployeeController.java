package com.demo.employee.controller;

import com.demo.employee.dto.ApiResponse;
import com.demo.employee.dto.EmployeeRequest;
import com.demo.employee.entity.Employee;
import com.demo.employee.service.EmployeeService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    public ApiResponse<Page<Employee>> list(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(employeeService.list(departmentId, name, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<Employee> getById(@PathVariable Long id) {
        return ApiResponse.success(employeeService.getById(id));
    }

    @PostMapping
    public ApiResponse<Employee> create(@Valid @RequestBody EmployeeRequest request) {
        return ApiResponse.success(employeeService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Employee> update(@PathVariable Long id, @Valid @RequestBody EmployeeRequest request) {
        return ApiResponse.success(employeeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return ApiResponse.success();
    }
}
