## Spring Boot 4 UI 执行顺序和逻辑分析

### 一、整体架构

```
用户浏览器 → Tomcat → Spring MVC → Controller → Service → Repository → H2数据库
    ↑                                                              ↓
    └────────────────── JSON响应 ←──────────────────────────────────┘
```

### 二、执行顺序详解

#### 第1步：用户访问 http://localhost:8080

```
浏览器发送 GET 请求 → Tomcat 接收
```

#### 第2步：静态资源处理

```java
// Spring Boot 自动配置
// 1. 检查 src/main/resources/static/ 目录
// 2. 找到 index.html
// 3. 返回 HTML 内容
```

**执行流程：**
1. Tomcat 接收请求
2. Spring MVC 的 `ResourceHttpRequestHandler` 处理
3. 从 classpath:/static/ 读取 index.html
4. 返回 HTML 响应

#### 第3步：浏览器解析 HTML 并执行 JavaScript

```javascript
// index.html 第296-301行
document.addEventListener('DOMContentLoaded', function() {
    loadStats();           // 加载统计数据
    loadDepartments();     // 加载部门列表
    loadEmployees();       // 加载员工列表
    loadDepartmentOptions(); // 加载部门选项
});
```

### 三、API 调用流程分析

#### 1. 加载统计数据 (loadStats)

```javascript
// 并行调用3个API
const [deptRes, empRes, healthRes] = await Promise.all([
    fetch('/api/departments?page=0&size=1'),  // 获取部门总数
    fetch('/api/employees?page=0&size=1'),    // 获取员工总数
    fetch('/actuator/health')                 // 获取系统状态
]);
```

**后端执行流程：**

```
GET /api/departments?page=0&size=1
    ↓
DepartmentController.list(page=0, size=1)
    ↓
DepartmentService.list(0, 1)
    ↓
DepartmentRepository.findAll(pageable)
    ↓
H2数据库查询: SELECT * FROM department LIMIT 1
    ↓
返回 Page<Department> 对象
    ↓
包装为 ApiResponse.success(data)
    ↓
JSON响应: {"code":200, "message":"success", "data":{"content":[...], "totalElements":5, ...}}
```

#### 2. 加载部门列表 (loadDepartments)

```javascript
// 第326-338行
async function loadDepartments(page = 0) {
    const response = await fetch(`/api/departments?page=${page}&size=10`);
    const data = await response.json();
    
    if (data.code === 200) {
        renderDepartmentTable(data.data.content);  // 渲染表格
        renderPagination('deptPagination', data.data, currentDeptPage, loadDepartments);
    }
}
```

**后端执行流程：**

```
GET /api/departments?page=0&size=10
    ↓
DepartmentController.list(0, 10)
    ↓
DepartmentService.list(0, 10)
    ↓
PageRequest.of(0, 10, Sort.by(DESC, "id"))
    ↓
DepartmentRepository.findAll(pageable)
    ↓
H2数据库查询: SELECT * FROM department ORDER BY id DESC LIMIT 10
    ↓
返回 Page<Department> (包含content列表、totalElements、totalPages等)
    ↓
JSON响应示例:
{
    "code": 200,
    "data": {
        "content": [
            {"id":5, "name":"技术部", "description":"负责研发", "createTime":"2026-05-05T12:00:00"},
            {"id":4, "name":"市场部", "description":"负责推广", "createTime":"2026-05-05T12:00:00"},
            ...
        ],
        "totalElements": 5,
        "totalPages": 1,
        "size": 10,
        "number": 0
    }
}
```

#### 3. 渲染部门表格 (renderDepartmentTable)

```javascript
// 第344-383行
function renderDepartmentTable(departments) {
    let html = `
        <table>
            <thead>
                <tr>
                    <th>ID</th>
                    <th>部门名称</th>
                    <th>描述</th>
                    <th>创建时间</th>
                    <th>操作</th>
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
    
    html += '</tbody></table>';
    container.innerHTML = html;
}
```

### 四、完整调用链路图

```
用户访问 http://localhost:8080
    │
    ├─→ 1. Tomcat 接收请求
    │       ↓
    ├─→ 2. Spring MVC 路由匹配
    │       ↓
    ├─→ 3. ResourceHttpRequestHandler 处理静态资源
    │       ↓
    ├─→ 4. 返回 index.html
    │       ↓
    ├─→ 5. 浏览器解析 HTML/CSS/JS
    │       ↓
    ├─→ 6. DOMContentLoaded 事件触发
    │       ↓
    ├─→ 7. 并行调用4个API
    │       ├─→ GET /api/departments?page=0&size=1
    │       ├─→ GET /api/employees?page=0&size=1
    │       ├─→ GET /actuator/health
    │       └─→ GET /api/departments?page=0&size=100
    │       ↓
    ├─→ 8. Controller 接收请求
    │       ↓
    ├─→ 9. Service 处理业务逻辑
    │       ↓
    ├─→ 10. Repository 访问数据库
    │       ↓
    ├─→ 11. H2 数据库查询
    │       ↓
    ├─→ 12. 返回 Page 对象
    │       ↓
    ├─→ 13. 包装为 ApiResponse
    │       ↓
    ├─→ 14. JSON 序列化
    │       ↓
    ├─→ 15. 返回 JSON 响应
    │       ↓
    ├─→ 16. JavaScript 解析 JSON
    │       ↓
    ├─→ 17. DOM 操作渲染表格
    │       ↓
    └─→ 18. 用户看到完整 UI
```

### 五、关键代码执行时序

| 顺序 | 组件 | 方法 | 作用 |
|------|------|------|------|
| 1 | Tomcat | - | 接收HTTP请求 |
| 2 | Spring MVC | DispatcherServlet | 路由分发 |
| 3 | ResourceHandler | handle() | 返回静态资源 |
| 4 | Browser | DOMContentLoaded | 触发初始化 |
| 5 | JavaScript | loadStats() | 并行调用API |
| 6 | DepartmentController | list() | 处理请求 |
| 7 | DepartmentService | list() | 业务逻辑 |
| 8 | DepartmentRepository | findAll() | 数据访问 |
| 9 | H2 Database | SELECT | 查询数据 |
| 10 | ApiResponse | success() | 包装响应 |
| 11 | Jackson | serialize() | JSON序列化 |
| 12 | JavaScript | renderDepartmentTable() | 渲染UI |

### 六、数据流转示例

**请求：**
```
GET /api/departments?page=0&size=10
```

**Controller 层：**
```java
@GetMapping
public ApiResponse<Page<Department>> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(departmentService.list(page, size));
}
```

**Service 层：**
```java
public Page<Department> list(int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    return departmentRepository.findAll(pageable);
}
```

**Repository 层：**
```java
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    // 自动生成: SELECT * FROM department ORDER BY id DESC LIMIT 10
}
```

**响应：**
```json
{
    "code": 200,
    "message": "success",
    "data": {
        "content": [...],
        "totalElements": 5,
        "totalPages": 1,
        "size": 10,
        "number": 0
    }
}
```

### 七、虚拟线程的作用

在 Spring Boot 4 中，每个 HTTP 请求都会分配一个虚拟线程：

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

**执行流程：**
1. Tomcat 接收请求
2. 从虚拟线程池获取一个虚拟线程
3. 在该虚拟线程中执行 Controller → Service → Repository
4. 当遇到 I/O 操作（数据库查询）时，虚拟线程自动让出载体线程
5. 查询完成后，虚拟线程恢复执行
6. 返回响应后，虚拟线程被回收

**优势：**
- 内存占用低（几KB vs 1MB）
- 支持百万级并发
- 无需改变编程模型

这就是 Spring Boot 4 UI 的完整执行顺序和逻辑分析。