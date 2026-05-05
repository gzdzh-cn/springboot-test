#!/bin/bash
# concurrent-memory-test.sh - 并发内存对比测试

# 配置
CONCURRENT=20      # 并发数
REQUESTS=200       # 总请求数
WARMUP_REQUESTS=50 # 预热请求数

# 应用配置
declare -a APPS=("springboot2:8082:/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home"
                 "springboot3:8081:/Users/lizheng/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home"
                 "springboot4:8080:/Users/lizheng/Library/Java/JavaVirtualMachines/corretto-21.0.6/Contents/Home")

# 结果目录
RESULT_DIR="/Volumes/disk/site/java/demo/springboot/test-results"
mkdir -p "$RESULT_DIR"

# 清理函数
cleanup() {
    echo "清理进程..."
    for config in "${APPS[@]}"; do
        IFS=':' read -r app port java_home <<< "$config"
        pid=$(lsof -ti:$port 2>/dev/null || true)
        if [ -n "$pid" ]; then
            kill -9 $pid 2>/dev/null || true
        fi
    done
    rm -f /tmp/sb_*.pid
}

trap cleanup EXIT INT TERM

# 启动应用
start_app() {
    local app=$1
    local port=$2
    local java_home=$3
    local jar="/Volumes/disk/site/java/demo/springboot/$app/target/employee-management-1.0.0.jar"
    
    echo "启动 $app (端口: $port)..."
    export JAVA_HOME=$java_home
    java -jar -Dspring.profiles.active=h2 "$jar" > "$RESULT_DIR/${app}.log" 2>&1 &
    echo $! > /tmp/sb_${app}.pid
    
    # 等待启动（最多60秒）
    for i in {1..60}; do
        if curl -s "http://localhost:$port/actuator/health" > /dev/null 2>&1; then
            echo "$app 启动成功!"
            return 0
        fi
        sleep 1
    done
    
    echo "$app 启动失败!"
    return 1
}

# 获取内存信息（MB）
get_memory() {
    local pid=$1
    if [ -n "$pid" ] && kill -0 $pid 2>/dev/null; then
        ps -o rss= -p $pid | awk '{printf "%.1f", $1/1024}'
    else
        echo "0"
    fi
}

# 运行ab测试
run_ab_test() {
    local app=$1
    local port=$2
    local url="http://localhost:$port/api/departments?page=0&size=10"
    
    echo "运行ab测试: $app (并发: $CONCURRENT, 总请求: $REQUESTS)..."
    
    # 预热
    ab -n $WARMUP_REQUESTS -c 10 -q "$url" > /dev/null 2>&1
    
    # 正式测试
    ab -n $REQUESTS -c $CONCURRENT -q "$url" > "$RESULT_DIR/${app}_ab.txt" 2>&1
    
    # 提取关键指标
    local rps=$(grep "Requests per second" "$RESULT_DIR/${app}_ab.txt" | awk '{print $4}')
    local mean_time=$(grep "Time per request" "$RESULT_DIR/${app}_ab.txt" | head -1 | awk '{print $4}')
    local failed=$(grep "Failed requests" "$RESULT_DIR/${app}_ab.txt" | awk '{print $3}')
    
    echo "$rps,$mean_time,$failed"
}

# 主测试流程
main() {
    echo "=========================================="
    echo "  Spring Boot 并发内存对比测试"
    echo "=========================================="
    echo "并发数: $CONCURRENT"
    echo "总请求数: $REQUESTS"
    echo ""
    
    # 清理
    cleanup
    
    # 创建结果文件
    echo "App,Startup_RMB,After_Load_RMB,RPS,MeanTime_ms,Failed" > "$RESULT_DIR/results.csv"
    
    # 测试每个应用
    for config in "${APPS[@]}"; do
        IFS=':' read -r app port java_home <<< "$config"
        
        echo ""
        echo "=========================================="
        echo "测试 $app"
        echo "=========================================="
        
        # 启动应用
        start_app "$app" "$port" "$java_home"
        
        # 等待稳定
        sleep 5
        
        # 获取启动后内存
        pid=$(cat /tmp/sb_${app}.pid 2>/dev/null)
        startup_mem=$(get_memory $pid)
        echo "启动后内存: ${startup_mem} MB"
        
        # 运行ab测试
        ab_result=$(run_ab_test "$app" "$port")
        rps=$(echo $ab_result | cut -d',' -f1)
        mean_time=$(echo $ab_result | cut -d',' -f2)
        failed=$(echo $ab_result | cut -d',' -f3)
        
        # 获取测试后内存
        after_mem=$(get_memory $pid)
        echo "测试后内存: ${after_mem} MB"
        
        # 计算内存增长
        growth=$(echo "$after_mem - $startup_mem" | bc 2>/dev/null || echo "N/A")
        echo "内存增长: ${growth} MB"
        
        # 保存结果
        echo "$app,$startup_mem,$after_mem,$rps,$mean_time,$failed" >> "$RESULT_DIR/results.csv"
        
        # 停止应用
        echo "停止 $app..."
        kill $pid 2>/dev/null || true
        sleep 2
    done
    
    # 生成报告
    generate_report
}

# 生成报告
generate_report() {
    echo ""
    echo "=========================================="
    echo "  测试结果汇总"
    echo "=========================================="
    echo ""
    
    # 表格输出
    printf "%-12s %-10s %-10s %-10s %-10s %-8s\n" "应用" "启动内存" "负载后" "RPS" "响应时间" "失败"
    printf "%-12s %-10s %-10s %-10s %-10s %-8s\n" "----" "--------" "------" "---" "--------" "----"
    
    tail -n +2 "$RESULT_DIR/results.csv" | while IFS=',' read app startup after rps mean failed; do
        printf "%-12s %-10s %-10s %-10s %-10s %-8s\n" "$app" "${startup}MB" "${after}MB" "$rps" "${mean}ms" "$failed"
    done
    
    echo ""
    echo "详细结果保存在: $RESULT_DIR"
    echo ""
    
    # 内存对比分析
    echo "内存对比分析:"
    echo "---"
    tail -n +2 "$RESULT_DIR/results.csv" | while IFS=',' read app startup after rps mean failed; do
        growth=$(echo "$after - $startup" | bc 2>/dev/null || echo "N/A")
        echo "$app: 启动 ${startup}MB -> 负载后 ${after}MB (增长 ${growth}MB)"
    done
}

# 运行
main