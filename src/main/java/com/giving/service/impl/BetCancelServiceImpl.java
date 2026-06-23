package com.giving.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.giving.auth.ClientAuthException;
import com.giving.auth.ClientUserSession;
import com.giving.auth.ClientUserSessionHolder;
import com.giving.base.resp.ApiResp;
import com.giving.entity.RoomMasterEntity;
import com.giving.mapper.RoomMasterMapper;
import com.giving.req.BetCancelProjectReq;
import com.giving.resp.BetCancelProjectResp;
import com.giving.service.BetCancelService;
import com.giving.service.BetCancelTxService;
import com.giving.service.UserFundLockTxService;
import com.giving.util.TableNameUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 撤单服务实现
 */
@Slf4j
@Service
public class BetCancelServiceImpl implements BetCancelService {
    private static final int WALLET_TYPE_CANCEL = 3;
    private static final int CANCEL_TYPE_USER = 1;

    @Autowired
    private RoomMasterMapper roomMasterMapper;
    @Autowired
    private UserFundLockTxService userFundLockTxService;
    @Autowired
    private BetCancelTxService betCancelTxService;

    /**
     * 用户撤销普通注单
     * @param req 撤单请求
     * @return 撤单结果
     */
    @Override
    public ApiResp<BetCancelProjectResp> cancelProject(BetCancelProjectReq req) {
        boolean locked = false;
        String title = null;
        try {
            ClientUserSession session = ClientUserSessionHolder.getRequired();
            validateRequestSession(req, session);

            RoomMasterEntity roomMaster = roomMasterMapper.selectOne(new LambdaQueryWrapper<RoomMasterEntity>()
                    .eq(RoomMasterEntity::getMasterId, Integer.valueOf(req.getRoomMasterId())));
            if (roomMaster == null || !Integer.valueOf(1).equals(roomMaster.getIsActive())) {
                throw new CancelBusinessException("厅主不存在或未启用");
            }
            title = TableNameUtil.safePrefix(roomMaster.getTitle());
            if (!title.equals(session.getRoomMasterTitle())) {
                throw new ClientAuthException("roomMasterTitle mismatch");
            }

            locked = userFundLockTxService.doLockUserFund(
                    req.getUserId(), true, WALLET_TYPE_CANCEL, "Cancel_01", title);
            if (!locked) {
                throw new CancelBusinessException("资金帐户因为其他操作被锁定，请稍后重试");
            }

            BetCancelProjectResp resp = betCancelTxService.cancelProject(
                    title, roomMaster, req, CANCEL_TYPE_USER);
            return ApiResp.sucess(resp);
        } catch (ClientAuthException e) {
            return ApiResp.jwtError(e.getMessage());
        } catch (CancelBusinessException | IllegalStateException e) {
            return ApiResp.bussError(e.getMessage());
        } catch (Exception e) {
            log.error("撤单失败，用户ID={}，注单ID={}", req.getUserId(), req.getProjectId(), e);
            return ApiResp.bussError("撤单失败");
        } finally {
            if (locked) {
                if (!userFundLockTxService.doLockUserFund(
                        req.getUserId(), false, WALLET_TYPE_CANCEL, "Cancel_015", title)) {
                    log.error("撤单流程结束后用户资金解锁失败，用户ID={}，厅主表名={}", req.getUserId(), title);
                }
            }
        }
    }

    /**
     * 校验请求用户与Token上下文一致
     * @param req 撤单请求
     * @param session Token上下文
     */
    private void validateRequestSession(BetCancelProjectReq req, ClientUserSession session) {
        if (!normalize(req.getUserId()).equals(normalize(session.getUserId()))) {
            throw new ClientAuthException("you can't access other user's data");
        }
        if (!normalize(req.getRoomMasterId()).equals(String.valueOf(session.getRoomMasterId()))) {
            throw new ClientAuthException("you can't access other room master's data");
        }
    }

    /**
     * 标准化字符串
     * @param value 原始值
     * @return 标准化结果
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 撤单业务异常
     */
    private static class CancelBusinessException extends RuntimeException {
        private CancelBusinessException(String message) {
            super(message);
        }
    }
}
