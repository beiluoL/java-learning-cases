package com.learn.springmini.demo.service;

/**
 * 订单服务（<b>具体类，不实现任何接口</b>）。
 *
 * <p>正因为它没有接口，AOP 在增强它时会走 <b>CGLIB 动态代理</b>（基于继承，生成子类）。
 * 这样同一个项目里就能同时演示 JDK 代理与 CGLIB 代理两条路径。</p>
 */
public class OrderServiceImpl {

    /** Setter 注入的配置项。 */
    private String shopName;

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public void placeOrder(String item) {
        System.out.println("  [OrderServiceImpl] 店铺=" + shopName + "，下单商品: " + item);
    }

    public String trackOrder(String orderId) {
        return "物流中: " + orderId;
    }
}
