package com.giving.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.giving.entity.OrdersEntity;
import com.giving.service.OrdersService;
import com.giving.mapper.OrdersMapper;
import org.springframework.stereotype.Service;

/**
* @author zzby
* @description 针对表【TEMP_orders(帐变纪录)】的数据库操作Service实现
* @createDate 2026-01-09 16:57:42
*/
@Service
public class OrdersServiceImpl extends ServiceImpl<OrdersMapper, OrdersEntity>
    implements OrdersService {

}




