USE bomb_vpn;

-- admin / admin123
INSERT INTO `user` (`username`, `password_hash`, `role`, `status`)
VALUES ('admin', '$2b$10$9Bt0yAlgX/M1nlA/WwLhR.XzOzOgQiIoAk6gKbKeIr8XtynoAUhlK', 'ADMIN', 1)
ON DUPLICATE KEY UPDATE `username` = `username`;

INSERT INTO `product` (`name`, `description`, `price`, `duration_days`, `status`, `sort_order`) VALUES
('VPN 月卡', '30 天 VPN 使用权', 19.90, 30, 1, 1),
('VPN 季卡', '90 天 VPN 使用权', 49.90, 90, 1, 2),
('VPN 年卡', '365 天 VPN 使用权', 149.90, 365, 1, 3);
