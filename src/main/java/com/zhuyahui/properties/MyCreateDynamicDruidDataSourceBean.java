package com.zhuyahui.properties;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;

/**
 * druid的方式，获取配置文件中的多个数据源
 *
 * @author : Zhu Yahui
 * @version : 1.0.0
 * @date : 2023/1/3
 */
@ConfigurationProperties(prefix = "zyh-datasource.druid")
public class MyCreateDynamicDruidDataSourceBean {
    @NestedConfigurationProperty
    private DruidDataSource master;
    @NestedConfigurationProperty
    private List<DruidDataSource> slaves;

    public void setMaster(DruidDataSource master) {
        this.master = master;
    }

    public void setSlaves(List<DruidDataSource> slaves) {
        this.slaves = slaves;
    }

    public DruidDataSource getMaster() {
        return master;
    }

    public List<DruidDataSource> getSlaves() {
        return slaves;
    }
}