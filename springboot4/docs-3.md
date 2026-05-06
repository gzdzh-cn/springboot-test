## SpringBoot4 UI 执行顺序和逻辑分析

### 一、整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         浏览器                           │
│    index.html - 单页面应用                              │
└─────────────────────────────┬───────────────────────────────────┘
                              │ HTTP/REST API
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Controller 层                                 │
│    DepartmentController.java / EmployeeController.java          │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Service 层                                    │
│    DepartmentService.java / EmployeeService.java                │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Repository 层                                 │
│    DepartmentRepository / EmployeeRepository (JPA)              │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    数据库                                        │
│    H2 内存数据库                                         │
└─────────────────────────────────────────────────────────────────┘
```

---

### 二、页面加载执行顺序

```
┌──────────────────────────────────────────────────────────────────┐
│ 1. DOMContentLoaded 事件触发                                      │
│    (第 296-301 行)                                                │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               │ 并行执行
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│ 2. loadStats() - 加载统计数据                                     │
│    ┌─────────────────────────────────────────────────────────┐   │
│    │ Promise.all 并行请求:                                    │   │
│    │  • GET /api/departments?page=0&size=1                   │   │
│    │  • GET /api/employees?page=0&size=1                     │   │
│    │  • GET /actuator/health                                  │   │
│    └─────────────────────────────────────────────────────────┘   │
│    更新页面:                                                       │
│    • deptCount 元素 - 部门数量                                     │
│    • empCount 元素 - 员工数量                                      │
│    • status 元素 - 系统状态                                        │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│ 3. loadDepartments(0) - 加载部门列表 (第一页)                      │
│    GET /api/departments?page=0&size=10                           │
│    → renderDepartmentTable() 渲染表格                             │
│    → renderPagination() 渲染分页                                  │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│ 4. loadEmployees(0) - 加载员工列表 (第一页)                        │
│    GET /api/employees?page=0&size=10                              │
│    → renderEmployeeTable() 渲染表格                                │
│    → renderPagination() 渲染分页                                   │
└──────────────────────────────┬───────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│ 5. loadDepartmentOptions() - 加载部门下拉选项                     │
│    GET /api/departments?page=0&size=100                           │
│    → 填充 deptFilter 下拉框                                        │
└──────────────────────────────────────────────────────────────────┘
```

---

### 三、核心 API 调用流程

#### 1. 部门列表查询流程

```
用户操作                    前端                     后端
    │                        │                        │
    │ 点击"部门管理"分页      │                        │
    │─────────────────────▶│                        │
    │                      │ GET /api/departments   │
    │                      │   ?page=1&size=10      │
    │                      │───────────────────────▶│
    │                      │                        │ DepartmentController.list()
    │                      │                        │   ↓
    │                      │                        │ DepartmentService.list()
    │                      │                        │   ↓
    │                      │                        │ DepartmentRepository.findAll(pageable)
    │                      │                        │   ↓
    │                      │                        │ H2 数据库查询
    │                      │                        │   ↓
    │                      │◀───────────────────────│
    │                      │ ApiResponse<Page<Dept>>│
    │                      │   ↓                    │
    │                      │ renderDepartmentTable()│
    │                      │ renderPagination()     │
    │◀─────────────────────│                        │
    │ 表格内容更新          │                        │
```

#### 2. 员工搜索流程

```
用户操作                    前端                     后端
    │                        │                        │
    │ 输入姓名 + 选择部门    │                        │
    │ 点击"搜索"            │                        │
    │─────────────────────▶│                        │
    │                      │ searchEmployees()      │
    │                      │   ↓                    │
    │                      │ loadEmployees(0)       │
    │                      │                        │
    │                      │ GET /api/employees     │
    │                      │   ?page=0&size=10      │
    │                      │   &departmentId=1      │
    │                      │   &name=张             │
    │                      │───────────────────────▶│
    │                      │                        │ EmployeeController.list()
    │                      │                        │   ↓
    │                      │                        │ EmployeeService.list()
    │                      │                        │   ↓
    │                      │                        │ 判断条件:
    │                      │                        │   if (deptId && name)
    │                      │                        │     → findByDepartmentIdAndNameContaining()
    │                      │                        │   else if (deptId)
    │                      │                        │     → findByDepartmentId()
    │                      │                        │   else if (name)
    │                      │                        │     → findByNameContaining()
    │                      │                        │   else
    │                      │                        │     → findAll()
    │                      │                        │
    │                      │◀───────────────────────│
    │                      │ ApiResponse<Page<Emp>> │
    │                      │   ↓                    │
    │                      │ renderEmployeeTable()  │
    │◀─────────────────────│                        │
    │ 员工表格更新          │                        │
```

---

### 四、数据流详解

#### 1. 统计数据加载 (`loadStats`)

```javascript
// 第 304-323 行
async function loadStats() {
    // 并行发送 3 个请求
    const [deptRes, empRes, healthRes] = await Promise.all([
        fetch(`${API_BASE}/departments?page=0&size=1`),  // 获取部门总数
        fetch(`${API_BASE}/employees?page=0&size=1`),     // 获取员工总数
        fetch('/actuator/health')                         // 健康检查
    ]);
    
    // 解析 JSON 响应
    const deptData = await deptRes.json();  // { code: 200, data: { totalElements: N } }
    const empData = await empRes.json();
    const healthData = await healthRes.json();  // { status: "UP" }
    
    // 更新 DOM
    document.getElementById('deptCount').textContent = deptData.data.totalElements;
    document.getElementById('empCount').textContent = empData.data.totalElements;
    document.getElementById('status').textContent = healthData.status === 'UP' ? '正常' : '异常';
}
```

**响应数据结构**:
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "content": [...],
        "totalElements": 15,
        "totalPages": 2,
        "number": 0,
        "size": 10
    }
}
```

#### 2. 部门列表渲染 (`renderDepartmentTable`)

```javascript
// 第 344-383 行
function renderDepartmentTable(departments) {
    // 构建表格 HTML
    let html = `
        <table>
            <thead>
                <tr>
                    <th>ID</th><th>部门名称</th><th>描述</th><th>创建时间</th><th>操作</th>
                </tr>
            </thead>
            <tbody>
    `;
    
    departments.forEach(dept => {
        html += `
            <tr>
                <td>${dept.id}</td>
                <td>${dept.name}</td>
                <td>${dept.description || '-'}</td>
                <td>${formatDate(dept.createTime)}</td>
                <td class="actions">
                    <button onclick="editDepartment(${dept.id})">编辑</button>
                    <button onclick="deleteDepartment(${dept.id})">删除</button>
                </td>
            </tr>
        `;
    });
    
    container.innerHTML = html;  // 直接替换 DOM
}
```

---

### 五、后端处理流程

#### 1. 请求处理链

```
HTTP Request
    │
    ▼
┌─────────────────────────────────────┐
│ DispatcherServlet (Spring MVC)      │
│ - 路由分发                           │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌─────────────────────────────────────┐
│ Controller 层                       │
│ @RestController                     │
│ - 接收请求参数                       │
│ - 调用 Service                       │
│ - 封装 ApiResponse 响应             │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌─────────────────────────────────────┐
│ Service 层                          │
│ @Service @Transactional             │
│ - 业务逻辑处理                       │
│ - 事务管理                           │
│ - 异常处理 (BusinessException)      │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌─────────────────────────────────────┐
│ Repository 层                        │
│ extends JpaRepository               │
│ - 数据访问                           │
│ - 自动生成 SQL                       │
└──────────────────┬──────────────────┘
                   │
                   ▼
┌─────────────────────────────────────┐
│ H2 数据库                            │
│ - 内存数据库                         │
│ - JPA 实体映射                       │
└─────────────────────────────────────┘
```

#### 2. 实体关系

```
┌────────────────────┐       ┌────────────────────┐
│    Department      │       │     Employee       │
├────────────────────┤       ├────────────────────┤
│ id (PK)            │       │ id (PK)            │
│ name               │◀──────│ department_id (FK) │
│ description        │  @ManyToOne               │
│ createTime         │       │ name               │
│ updateTime         │       │ email              │
└────────────────────┘       │ phone              │
                             │ createTime         │
                             │ updateTime         │
                             └────────────────────┘
```

---

### 六、关键代码位置

| 功能 | 前端位置 | 后端位置 |
|------|----------|----------|
| 页面初始化 | `index.html:296-301` | - |
| 统计数据加载 | `index.html:304-323` | `DepartmentController:19-24`, `EmployeeController:19-26` |
| 部门列表 | `index.html:326-341` | `DepartmentService:21-24` |
| 员工列表 | `index.html:386-408` | `EmployeeService:24-34` |
| 分页渲染 | `index.html:455-488` | - |
| 部门下拉选项 | `index.html:491-508` | `DepartmentController:19-24` |
| 统一响应格式 | - | `ApiResponse.java:16-22` |
| 异常处理 | - | `GlobalExceptionHandler.java` |

---

### 七、执行时序图

```
时间轴 ─────────────────────────────────────────────────────────▶

T0:  页面加载开始
     │
T1:  DOMContentLoaded 事件
     │
     ├──▶ loadStats() ──────────────────────────────────────────┐
     │    │                                                      │
     │    ├── fetch(/api/departments?page=0&size=1) ────────────┼──▶ Controller → Service → Repository
     │    ├── fetch(/api/employees?page=0&size=1) ─────────────┼──▶ Controller → Service → Repository
     │    └── fetch(/actuator/health) ────────────────────────┼──▶ Actuator
     │                                                           │
     ├──▶ loadDepartments(0) ─────────────────────────────────┤
     │    │                                                      │
     │    └── fetch(/api/departments?page=0&size=10) ──────────┼──▶ Controller → Service → Repository
     │                                                           │
     ├──▶ loadEmployees(0) ────────────────────────────────────┤
     │    │                                                      │
     │    └── fetch(/api/employees?page=0&size=10) ────────────┼──▶ Controller → Service → Repository
     │                                                           │
     └──▶ loadDepartmentOptions() ─────────────────────────────┘
          │
          └── fetch(/api/departments?page=0&size=100) ──────────▶ Controller → Service → Repository

T2:  所有请求完成，页面渲染完成
```

这就是 SpringBoot4 项目 UI 的完整执行顺序和逻辑分析。整个系统采用前后端分离架构，前端通过 REST API 与后端交互，后端使用经典的 Controller-Service-Repository 三层架构。