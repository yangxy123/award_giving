package com.giving.mapper;

import com.giving.entity.OrdersEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author zzby
* @description 针对表【TEMP_orders(帐变纪录)】的数据库操作Mapper
* @createDate 2026-01-09 16:57:42
* @Entity com.giving.entity.TempOrders
*/
public interface OrdersMapper extends BaseMapper<OrdersEntity> {

    int addOrdersList(@Param("order") OrdersEntity order,@Param("title") String title);

    int addOrdersListAll(@Param("orders") List<OrdersEntity> orders, @Param("title") String title);

    OrdersEntity selectUncancelledPrizeOrderForUpdate(@Param("title") String title,
                                                      @Param("projectId") String projectId);

    OrdersEntity selectUncancelledDeductOrderForUpdate(@Param("title") String title,
                                                       @Param("projectId") String projectId);

    int deleteOrderByEntryAndType(@Param("title") String title,
                                  @Param("entry") String entry,
                                  @Param("orderTypeId") Integer orderTypeId);

    int addCancelAwardOrder(@Param("order") OrdersEntity order, @Param("title") String title);
}




