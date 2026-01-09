package com.giving.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.giving.entity.LotteryEntity;
import com.giving.service.LotteryService;
import com.giving.mapper.LotteryMapper;
import org.springframework.stereotype.Service;

/**
* @author zzby
* @description 针对表【lottery(彩票种类)】的数据库操作Service实现
* @createDate 2026-01-09 15:57:13
*/
@Service
public class LotteryServiceImpl extends ServiceImpl<LotteryMapper, LotteryEntity>
    implements LotteryService{

}




