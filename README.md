# Employee Management System - Spring Boot 3 vs 4 JAR 容量对比

## 项目简介

两个功能完全相同的员工管理系统，分别基于 Spring Boot 4（Java 21）和 Spring Boot 3（Java 8），用于对比打包 JAR 容量差异，验证 Spring Boot 4 模块化重构带来的瘦身效果。

## 功能列表

| 功能 | 说明 |
|------|------|
| 部门 CRUD | 新增/修改/删除/查询部门，分页列表 |
| 员工 CRUD | 新增/修改/删除/查询员工，分页列表 |
| 员工搜索 | 按部门筛选、姓名模糊搜索 |
| 统一响应 | ApiResponse 封装（code/message/data） |
| 全局异常处理 | @RestControllerAdvice |
| 参数校验 | Bean Validation（@NotBlank/@Size/@Email） |
| 数据库初始化 | schema.sql 建表 + data.sql 初始数据 |
| 健康检查 | Spring Boot Actuator（health/info） |

## 技术栈对比

| 维度 | springboot4 | springboot3 |
|------|-------------|-------------|
| Spring Boot | 4.0.0 | 3.4.1 |
| Spring Framework | 7.0 | 6.x |
| Java | 21 | 1.8 |
| MySQL | 8.4 @ 127.0.0.1:13308 | 8.4 @ 127.0.0.1:13308 |
| 数据库名 | employee_db | employee_db3 |
| 服务端口 | 8080 | 8081 |

## 快速开始

### 前置条件

- JDK 21（springboot4 项目）
- JDK 8（springboot3 项目）
- MySQL 8.4 运行在 127.0.0.1:13308
- Maven 3.6+

### 创建数据库

```sql
CREATE DATABASE employee_db DEFAULT CHARACTER SET utf8mb4;
CREATE DATABASE employee_db3 DEFAULT CHARACTER SET utf8mb4;
```

### 构建 & 运行

```bash
# Spring Boot 4 项目
cd springboot4
mvn clean package -DskipTests
java -jar target/employee-management-1.0.0.jar

# Spring Boot 3 项目
cd springboot3
mvn clean package -DskipTests
java -jar target/employee-management-1.0.0.jar
```

### API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/departments?page=0&size=10 | 部门分页列表 |
| GET | /api/departments/{id} | 部门详情 |
| POST | /api/departments | 新增部门 |
| PUT | /api/departments/{id} | 修改部门 |
| DELETE | /api/departments/{id} | 删除部门 |
| GET | /api/employees?departmentId=1&name=张&page=0&size=10 | 员工分页/搜索 |
| GET | /api/employees/{id} | 员工详情 |
| POST | /api/employees | 新增员工 |
| PUT | /api/employees/{id} | 修改员工 |
| DELETE | /api/employees/{id} | 删除员工 |
| GET | /actuator/health | 健康检查 |
| GET | /actuator/info | 应用信息 |

---

## JAR 容量对比

### 对比方法

```bash
# 1. 分别打包
cd springboot4 && mvn clean package -DskipTests && cd ..
cd springboot3 && mvn clean package -DskipTests && cd ..

# 2. 对比 JAR 总大小
ls -lh springboot4/target/employee-management-1.0.0.jar
ls -lh springboot3/target/employee-management-1.0.0.jar

# 3. 对比 BOOT-INF/lib 中依赖体积
# Spring Boot 4
unzip -l springboot4/target/employee-management-1.0.0.jar | grep "BOOT-INF/lib/" | awk '{sum+=$1; print $1, $4} END {print "Total:", sum/1024/1024, "MB"}'

# Spring Boot 3
unzip -l springboot3/target/employee-management-1.0.0.jar | grep "BOOT-INF/lib/" | awk '{sum+=$1; print $1, $4} END {print "Total:", sum/1024/1024, "MB"}'

# 4. 对比 Spring Boot 核心模块体积
# Spring Boot 4
unzip -l springboot4/target/employee-management-1.0.0.jar | grep "BOOT-INF/lib/spring-boot" | awk '{sum+=$1; print $1, $4} END {print "Spring Boot Total:", sum/1024, "KB"}'

# Spring Boot 3
unzip -l springboot3/target/employee-management-1.0.0.jar | grep "BOOT-INF/lib/spring-boot" | awk '{sum+=$1; print $1, $4} END {print "Spring Boot Total:", sum/1024, "KB"}'

# 5. 对比 Actuator 模块体积
# Spring Boot 4
unzip -l springboot4/target/employee-management-1.0.0.jar | grep "actuator" | awk '{sum+=$1; print $1, $4} END {print "Actuator Total:", sum/1024, "KB"}'

# Spring Boot 3
unzip -l springboot3/target/employee-management-1.0.0.jar | grep "actuator" | awk '{sum+=$1; print $1, $4} END {print "Actuator Total:", sum/1024, "KB"}'
```

### 自动化对比脚本

```bash
#!/bin/bash
# compare-jar.sh - 自动对比 Spring Boot 3/4 JAR 容量

SB4_JAR="springboot4/target/employee-management-1.0.0.jar"
SB3_JAR="springboot3/target/employee-management-1.0.0.jar"

echo "=========================================="
echo "  Spring Boot 3 vs 4 JAR 容量对比"
echo "=========================================="

echo ""
echo "--- JAR 总大小 ---"
ls -lh "$SB4_JAR" | awk '{print "Spring Boot 4:", $5}'
ls -lh "$SB3_JAR" | awk '{print "Spring Boot 3:", $5}'

echo ""
echo "--- BOOT-INF/lib 依赖总体积 ---"
sb4_lib=$(unzip -l "$SB4_JAR" | grep "BOOT-INF/lib/" | awk '{sum+=$1} END {printf "%.2f MB", sum/1024/1024}')
sb3_lib=$(unzip -l "$SB3_JAR" | grep "BOOT-INF/lib/" | awk '{sum+=$1} END {printf "%.2f MB", sum/1024/1024}')
echo "Spring Boot 4: $sb4_lib"
echo "Spring Boot 3: $sb3_lib"

echo ""
echo "--- Spring Boot 核心模块体积 ---"
sb4_core=$(unzip -l "$SB4_JAR" | grep "BOOT-INF/lib/spring-boot" | awk '{sum+=$1} END {printf "%.2f KB", sum/1024}')
sb3_core=$(unzip -l "$SB3_JAR" | grep "BOOT-INF/lib/spring-boot" | awk '{sum+=$1} END {printf "%.2f KB", sum/1024}')
echo "Spring Boot 4: $sb4_core"
echo "Spring Boot 3: $sb3_core"

echo ""
echo "--- Actuator 模块体积 ---"
sb4_act=$(unzip -l "$SB4_JAR" | grep "actuator" | awk '{sum+=$1} END {printf "%.2f KB", sum/1024}')
sb3_act=$(unzip -l "$SB3_JAR" | grep "actuator" | awk '{sum+=$1} END {printf "%.2f KB", sum/1024}')
echo "Spring Boot 4: $sb4_act"
echo "Spring Boot 3: $sb3_act"

echo ""
echo "=========================================="
```

### 预期对比维度

| 维度 | 说明 |
|------|------|
| JAR 总大小 | fat JAR 整体体积 |
| BOOT-INF/lib 总体积 | 所有依赖 JAR 的大小之和 |
| Spring Boot 核心模块 | spring-boot-*.jar 体积 |
| Actuator 模块 | 验证 SB4 模块化瘦身效果 |
| Spring Framework | spring-*.jar 体积 |
| 应用代码 | BOOT-INF/classes 体积 |
