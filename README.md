# bomb-server

VPN 商品购买网站后端服务，基于 Spring Boot 3 + Java 21 + MySQL + Redis。

## 技术栈

- Java 21
- Spring Boot 3.2
- Spring Security + JWT
- MyBatis-Plus
- MySQL 8
- Redis
- Knife4j
- 支付宝 SDK

## 启动前准备

1. 安装并启动 MySQL、Redis
2. 执行数据库脚本（按顺序）：
   - `src/main/resources/sql/init.sql`
   - `src/main/resources/sql/seed.sql`
3. 修改 `src/main/resources/application-dev.yml` 中的数据库、Redis、支付宝配置

## 启动

```bash
cd bomb-server
mvn spring-boot:run
```

服务默认端口：`8080`

## 默认账号

- 管理员：`admin` / `admin123`

## API 文档

启动后访问：http://localhost:8080/doc.html

## 主要接口

| 模块 | 路径前缀 |
|------|----------|
| 健康检查 | `/api/health` |
| 认证 | `/api/auth` |
| 商品 | `/api/products` |
| 管理端商品 | `/api/admin/products` |
| 订单 | `/api/orders` |
| 支付 | `/api/payments/alipay` |
| 权益 | `/api/entitlements` |
