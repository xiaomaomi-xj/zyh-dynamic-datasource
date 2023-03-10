package com.zhuyahui.aop;

import com.zhuyahui.annotation.ZyhDataSourceRead;
import com.zhuyahui.annotation.ZyhDataSourceWrite;
import com.zhuyahui.exception.ZyhServiceRunTimeException;
import com.zhuyahui.properties.MyHandleDataSourceParam;
import com.zhuyahui.util.MyDynamicDataSourceContextHolder;
import com.zhuyahui.util.constant.ChooseSlaveDataSourceWayEnum;
import com.zhuyahui.util.constant.MyDynamicDataSourceConstant;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 对service层进行切面，读的时候，或者写的时候，进行更换数据源
 * order(-1) 是保证了这个aop会发生在Transactional的事务aop之前，这样的话Transactional还会生效
 *
 * @author : Zhu Yahui
 * @version : 1.0.4
 * @date : 2023/1/11
 */
@Aspect
@Order(-1)
public class MyDynamicDataSourceAop {

    /**
     * 对于列表必须要加锁
     */
    private static final Lock POLLING_LIST_LOCK=new ReentrantLock();
    /**
     * 这个属性负责轮询
     */
    private static final List<Integer> POLLING_LIST = new CopyOnWriteArrayList<>();

    /**
     * 在类上使用了@ZyhService注解大的作为切入点
     */
    @Pointcut("@within(com.zhuyahui.annotation.ZyhService)")
    public void dataSourcePointCut() {
    }

    /**
     * 判断含有注解@ZyhService的类上面是否含有@ZyhDataSourceRead和@ZyhDataSourceWrite注解，然后根据注解来使用不同的数据源和不同的功能
     *
     * @param joinPoint 原方法的一些信息
     * @return 返回原方法之后的返回值
     * @throws Throwable 执行原方法可能产生的的异常
     */
    @Around("dataSourcePointCut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        //获取当前的方法
        Method method = signature.getMethod();
        //获取当前的类
        Class<?> targetClass = signature.getDeclaringType();
        //先处理方法，如果方法上面没有再处理类
        if (method.isAnnotationPresent(ZyhDataSourceRead.class)) {
            ZyhDataSourceRead annotation = method.getAnnotation(ZyhDataSourceRead.class);
            if (ObjectUtils.isEmpty(annotation.value())) {
                //处理读
                handleRead();
            } else {
                //指定了某一个从数据库的名字
                handleReadAppoint(annotation.value());
            }
        } else if (method.isAnnotationPresent(ZyhDataSourceWrite.class)) {
            //处理写
            handleWrite();
        } else if (targetClass.isAnnotationPresent(ZyhDataSourceRead.class)) {
            ZyhDataSourceRead annotation = targetClass.getAnnotation(ZyhDataSourceRead.class);
            if (ObjectUtils.isEmpty(annotation.value())) {
                //处理读
                handleRead();
            } else {
                //指定了某一个从数据库的名字
                handleReadAppoint(annotation.value());
            }
        } else if (targetClass.isAnnotationPresent(ZyhDataSourceWrite.class)) {
            //处理写
            handleWrite();
        } else {
            //没加注解默认就是master,就是写
            handleWrite();
        }
        try {
            //注解拥有Transactional注解的功效，不用再单独处理事务
            return joinPoint.proceed();
        } finally {
            MyDynamicDataSourceContextHolder.removeContextKey();
        }
    }

    /**
     * 处理指定从数据库的情况
     *
     * @param name 指定的从数据库名字
     */
    public static void handleReadAppoint(String name) {
        String currentSlaveName = "";
        for (String slaveName : MyHandleDataSourceParam.SLAVE_NAME) {
            if (slaveName.startsWith(name)) {
                if (name.length() >= 6) {
                    currentSlaveName = slaveName;
                    break;
                }
            }
        }
        if (ObjectUtils.isEmpty(currentSlaveName)) {
            throw new ZyhServiceRunTimeException("你指定的从数据库名称不存在");
        }
        MyDynamicDataSourceContextHolder.setContextKey(currentSlaveName);
    }

    /**
     * 处理读的轮询和随机
     */
    public static void handleRead() {
        //如果是读注解,先判断方式是轮询还是随机
        if (ChooseSlaveDataSourceWayEnum.RANDOM == MyHandleDataSourceParam.CHOOSE_SLAVE_WAY) {
            //随机模式(随机模式是没有线程安全问题的)
            int size = MyHandleDataSourceParam.SLAVE_NAME.size();
            Random random = new Random();
            int index = random.nextInt(size);
            MyDynamicDataSourceContextHolder.setContextKey(MyHandleDataSourceParam.SLAVE_NAME.get(index));
        }
        if (ChooseSlaveDataSourceWayEnum.POLLING == MyHandleDataSourceParam.CHOOSE_SLAVE_WAY) {
            //轮询模式（因为list的删除修改，是有线程安全问题的,我们选择加锁）
            POLLING_LIST_LOCK.lock();
            try{
                int size = MyHandleDataSourceParam.SLAVE_NAME.size();
                if (POLLING_LIST.size() == 0) {
                    for (int i = 0; i < size; i++) {
                        POLLING_LIST.add(i);
                    }
                }
                MyDynamicDataSourceContextHolder.setContextKey(MyHandleDataSourceParam.SLAVE_NAME.get(POLLING_LIST.get(0)));
                POLLING_LIST.remove(0);
            }finally {
                POLLING_LIST_LOCK.unlock();
            }
        }
    }

    /**
     * 处理写注解
     */
    public static void handleWrite() {
        //写注解就直接是master
        MyDynamicDataSourceContextHolder.setContextKey(MyDynamicDataSourceConstant.DEFAULT_DATASOURCE_BEAN_NAME);
    }
}
