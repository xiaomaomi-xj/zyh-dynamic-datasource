package com.zhuyahui.properties;

import com.zhuyahui.exception.ZyhServiceRunTimeException;
import com.zhuyahui.properties.common.MyCreateDefaultDataSourceBean;
import com.zhuyahui.util.constant.ChooseDataSourceTypeEnum;
import com.zhuyahui.util.constant.ChooseSlaveDataSourceWayEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * 对接受配置信息的三个类进行处理
 *
 * @author : Zhu Yahui
 * @version : 1.0.4
 * @date : 2023/1/11
 */
public class MyHandleDataSourceParam {

    private static final Logger LOG = LoggerFactory.getLogger(MyHandleDataSourceParam.class);
    /**
     * 数据源厂家类型
     */
    private static ChooseDataSourceTypeEnum chooseDataSourceTypeEnum;
    /**
     * 从数据源的名字的集合
     */
    public static final List<String> SLAVE_NAME = new ArrayList<>();
    /**
     * 最终处理完的选择从数据库的方式
     */
    public static ChooseSlaveDataSourceWayEnum CHOOSE_SLAVE_WAY;

    @Autowired(required = false)
    private MyCreateDefaultDataSourceBean myCreateDefaultDataSourceBean;

    @Autowired(required = false)
    private MyCreateDynamicHikariDataSourceBean myCreateDynamicHikariDataSourceBean;

    @Autowired(required = false)
    private MyCreateDynamicDruidDataSourceBean myCreateDynamicDruidDataSourceBean;


    /**
     * 获取主数据库
     *
     * @return 主数据库
     */
    public DataSource getMasterDataSource() {
        //在获取主数据库的时候，就应该处理好先择从数据库的方式
        handleDefault();
        //判断用户配置的类型决定使用什么商家的数据源
        if (chooseDataSourceTypeEnum == ChooseDataSourceTypeEnum.DRUID) {
            return myCreateDynamicDruidDataSourceBean.getMaster();
        } else {
            //如果是默认的，必须由默认的返回
            if (!ObjectUtils.isEmpty(myCreateDefaultDataSourceBean.getMaster())) {
                return myCreateDefaultDataSourceBean.getMaster();
            }
            return myCreateDynamicHikariDataSourceBean.getMaster();
        }
    }

    /**
     * 获取从数据库
     *
     * @return 从数据库集合
     */
    public List<DataSource> getSlaveDataSourceList() {
        List<DataSource> slaveDataSourceList = new ArrayList<>();
        //判断用户配置的类型决定使用什么商家的数据源
        if (chooseDataSourceTypeEnum == ChooseDataSourceTypeEnum.DRUID) {
            slaveDataSourceList.addAll(myCreateDynamicDruidDataSourceBean.getSlaves());
        } else {
            //如果默认情况下，主数据源被配置，需要从默认的情况下拿
            if (!ObjectUtils.isEmpty(myCreateDefaultDataSourceBean.getMaster())) {
                slaveDataSourceList.addAll(myCreateDefaultDataSourceBean.getSlaves());
            } else {
                slaveDataSourceList.addAll(myCreateDynamicHikariDataSourceBean.getSlaves());
            }
        }
        return slaveDataSourceList;
    }

    /**
     * 处理默认情况下的从库选择方式,以及默认情况下的数据库驱动问题
     */
    private void handleDefault() {
        //如果用户没定义，就使用随机的方式
        if (myCreateDefaultDataSourceBean.getSwitchSlaveType() == ChooseSlaveDataSourceWayEnum.POLLING) {
            CHOOSE_SLAVE_WAY = ChooseSlaveDataSourceWayEnum.POLLING;
        } else {
            CHOOSE_SLAVE_WAY = ChooseSlaveDataSourceWayEnum.RANDOM;
        }
        chooseDataSourceTypeEnum = myCreateDefaultDataSourceBean.getDataSourceType();
        //如果没有定义，需要给他一个初始值
        if (ObjectUtils.isEmpty(chooseDataSourceTypeEnum)) {
            chooseDataSourceTypeEnum = ChooseDataSourceTypeEnum.HIKARI;
        }
        //检查配置文件是否存在问题
        check();
    }

    private void check() {
        //处理没有依赖的情况
        if (ObjectUtils.isEmpty(myCreateDynamicDruidDataSourceBean)) {
            check1();
        } else {
            check2();
        }
    }

    private void checkCopy() {
        //没指定druid也没指定hikari,直接把master写在了zyh-datasource下面的情况进行处理
        if (!ObjectUtils.isEmpty(myCreateDefaultDataSourceBean.getMaster())) {
            //如果没有指定Druid也没有指定hikari，直接把master写在了zyh-datasource下面，那么就为hikari
            if (chooseDataSourceTypeEnum != ChooseDataSourceTypeEnum.HIKARI) {
                LOG.error("默认的数据源类型为Hikari");
                throw new ZyhServiceRunTimeException("默认的数据源类型为Hikari");
            }
            //查看有没有配置从数据源
            if (ObjectUtils.isEmpty(myCreateDefaultDataSourceBean.getSlaves())) {
                LOG.error("从数据源，最少配置一个");
                throw new ZyhServiceRunTimeException("从数据源，最少配置一个");
            }
            return;
        }
        //看看hikari下面有没有master
        if (!ObjectUtils.isEmpty(myCreateDynamicHikariDataSourceBean.getMaster())) {
            if (chooseDataSourceTypeEnum != ChooseDataSourceTypeEnum.HIKARI) {
                LOG.error("你指定的数据源类型不是hikari");
                throw new ZyhServiceRunTimeException("你指定的数据源类型不是hikari");
            }
            //查看有没有配置从数据源
            if (ObjectUtils.isEmpty(myCreateDynamicHikariDataSourceBean.getSlaves())) {
                LOG.error("从数据源，最少配置一个");
                throw new ZyhServiceRunTimeException("从数据源，最少配置一个");
            }
        }
    }

    private void check1() {
        //如果三个主数据库都不存在，需要报错
        if (ObjectUtils.isEmpty(myCreateDefaultDataSourceBean.getMaster())
                && ObjectUtils.isEmpty(myCreateDynamicHikariDataSourceBean.getMaster())) {
            LOG.error("没有指定master数据源，或者你在druid里面指定的master数据源，而你并没有引入druid的依赖");
            throw new ZyhServiceRunTimeException("没有指定master数据源，或者你在druid里面指定的master数据源，而你并没有引入druid的依赖");
        }
        if (chooseDataSourceTypeEnum == ChooseDataSourceTypeEnum.DRUID) {
            LOG.error("你没有导入druid数据源依赖，不能使用druid数据源");
            throw new ZyhServiceRunTimeException("你没有导入druid数据源依赖，不能使用druid数据源");
        }
        checkCopy();
    }

    private void check2() {
        //如果三个主数据库都不存在，需要报错
        if (ObjectUtils.isEmpty(myCreateDefaultDataSourceBean.getMaster())
                && ObjectUtils.isEmpty(myCreateDynamicDruidDataSourceBean.getMaster())
                && ObjectUtils.isEmpty(myCreateDynamicHikariDataSourceBean.getMaster())) {
            LOG.error("没有指定master数据源");
            throw new ZyhServiceRunTimeException("没有指定master数据源");
        }
        //默认没有master，就看druid下面有没有master
        if (!ObjectUtils.isEmpty(myCreateDynamicDruidDataSourceBean.getMaster())) {
            if (chooseDataSourceTypeEnum != ChooseDataSourceTypeEnum.DRUID) {
                LOG.error("你指定的数据源类型不是druid，或者你没有指定类型，默认为hikari");
                throw new ZyhServiceRunTimeException("你指定的数据源类型不是druid，或者你没有指定类型，默认为hikari");
            }
            //查看有没有配置从数据源
            if (ObjectUtils.isEmpty(myCreateDynamicDruidDataSourceBean.getSlaves())) {
                LOG.error("从数据源，最少配置一个");
                throw new ZyhServiceRunTimeException("从数据源，最少配置一个");
            }
            return;
        }
        checkCopy();
    }

}
