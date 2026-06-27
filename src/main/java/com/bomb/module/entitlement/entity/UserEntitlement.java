package com.bomb.module.entitlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_entitlement")
public class UserEntitlement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long productId;

    private Long orderId;

    private String productName;

    private LocalDateTime startAt;

    private LocalDateTime expireAt;

    private String status;

    private LocalDateTime createdAt;
}
