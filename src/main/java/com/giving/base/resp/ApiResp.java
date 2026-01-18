package com.giving.base.resp;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.github.pagehelper.Page;

import com.giving.base.constans.ResCodeContants;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一对外返回类
 * @author zzby
 * @version 创建时间： 2026/1/18 下午4:04
 */
@Data
@NoArgsConstructor
public class ApiResp<T> {
    @ApiModelProperty(value = "响应编码")
    private String resCode;

    @ApiModelProperty(value = "描述信息")
    private String resDesc;

    @ApiModelProperty(value = "结果集")
    private T resultSet;

    private ApiResp(String resCode, String resDesc, T resultSet) {
        super();
        this.resCode = resCode;
        this.resDesc = resDesc;
        this.resultSet = resultSet;
    }
    /**
     * 无数据操作成功
     * @author yangxy
     * @version 创建时间：2023年7月25日 下午4:40:22
     * @return
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ApiResp sucess() {
        return new ApiResp(ResCodeContants.SUCESS,"Successfuloperation",null);
    }

    /**
     * 有数据操作成功
     * @author yangxy
     * @version 创建时间：2023年7月25日 下午4:40:32
     * @param data
     * @return
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ApiResp sucess(Object data) {
        return new ApiResp(ResCodeContants.SUCESS,"Successfuloperation", data);
    }

    /**
     * 有数据操作成功
     * @author yangxy
     * @version 创建时间：2023年7月25日 下午4:40:32
     * @param data
     * @return
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ApiResp rule(Object data) {
        return new ApiResp(ResCodeContants.RULE,"Successfuloperation", data);
    }

    /**
     * 参数错误
     * @author yangxy
     * @version 创建时间：2023年7月25日 下午4:41:42
     * @param desc 错误描述
     * @return
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ApiResp paramError(String desc) {
        return new ApiResp(ResCodeContants.PARAM_ERROR,desc,null);
    }

    /**
     * 业务错误
     * @author yangxy
     * @version 创建时间：2023年7月25日 下午4:41:42
     * @param desc 错误描述
     * @return
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ApiResp bussError(String desc) {
        return new ApiResp(ResCodeContants.BUS_ERROR,desc,null);
    }

    /**
     * 权限错误
     * @author yangxy
     * @version 创建时间：2023年7月25日 下午4:42:56
     * @param desc 错误描述
     * @return
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ApiResp authError(String desc) {
        return new ApiResp(ResCodeContants.AUTH_ERROR,desc,null);
    }

    /**
     * 系统错误
     * @author yangxy
     * @version 创建时间：2023年7月25日 下午5:28:11
     * @return
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ApiResp sysError() {
        return new ApiResp(ResCodeContants.SYS_ERROR,"systemerror",null);
    }

    /**
     * token无效
     * @author yangxy
     * @version 创建时间：2023年7月26日 上午10:48:07
     * @return
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ApiResp jwtError(String desc) {
        return new ApiResp(ResCodeContants.JWT_ERROR,desc,null);
    }

    /**
     * 验证码错误
     * @author yangxy
     * @version 创建时间：2023年7月26日 下午5:27:27
     * @param desc 错误描述
     * @return
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ApiResp codeError(String desc) {
        return new ApiResp(ResCodeContants.CODE_ERROR,desc,null);
    }

    /**
     * 返回分页数据
     * @author yangxy
     * @version 创建时间：2023年8月14日 下午12:55:45
     * @param page
     * @return
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ApiResp page(Page page){
        PageResp pageVo = new PageResp();
        pageVo.setPageNo(page.getPageNum());
        pageVo.setPageSize(page.getPageSize());
        pageVo.setData(page.getResult());
        pageVo.setTotal((int) page.getTotal());
        return sucess(pageVo);
    }

    /**
     * 封装分页返回对象
     *
     * @param pageNo   当前页
     * @param pageSize 每页显示条数
     * @param total    总条数
     * @param data     查询数据
     * @param <T>      返回对象
     * @return
     */
    public static <T> ApiResp page(int pageNo, int pageSize, int total, T data) {
        PageResp pageVo = new PageResp();
        pageVo.setPageNo(pageNo);
        pageVo.setPageSize(pageSize);
        pageVo.setData(data);
        pageVo.setTotal(total);
        return sucess(pageVo);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ApiResp error(String code,String msg){
        return new ApiResp(code,msg, null);
    }

    /**
     * 自定义参数
     * @param code
     * @param msg
     * @return
     */
    public static ApiResp customize(String code, String msg, Object data){
        return new ApiResp(code, msg, data);
    }
}

