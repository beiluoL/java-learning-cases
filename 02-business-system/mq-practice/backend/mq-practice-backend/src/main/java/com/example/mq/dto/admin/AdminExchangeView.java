package com.example.mq.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class AdminExchangeView {

    private String name;

    private String vhost;

    /** direct / topic / fanout / headers / x-delayed-message */
    private String type;

    private boolean durable;

    private boolean autoDelete;

    /** internal=true 表示只允许交换机到交换机转发，客户端不能直接投递 */
    private boolean internal;

    private double publishInRate;

    private double publishOutRate;

    private Map<String, Object> arguments;
}
