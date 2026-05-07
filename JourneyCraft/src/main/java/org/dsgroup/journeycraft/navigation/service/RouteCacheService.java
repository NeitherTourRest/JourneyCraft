package org.dsgroup.journeycraft.navigation.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.dsgroup.journeycraft.navigation.entity.RouteCache;

import java.math.BigDecimal;

/**
 * 路径规划缓存 Service 接口
 * <p>
 * 提供缓存查询与写入方法
 *
 * @author 后端智能体
 * @since 2026-04-14
 */
public interface RouteCacheService extends IService<RouteCache> {

    /**
     * 查询有效缓存（未过期、匹配起终点/通行方式/策略）
     *
     * @param startNodeId   起始节点ID
     * @param endNodeId     目标节点ID
     * @param transportType 通行方式（null 默认 1）
     * @param strategy      规划策略（null 默认 shortest_distance）
     * @return 有效缓存，无匹配返回 null
     */
    RouteCache findCachedRoute(Long startNodeId, Long endNodeId, Integer transportType, String strategy);

    /**
     * 保存路径规划结果到缓存（过期时间 30 分钟）
     *
     * @param startNodeId    起始节点ID
     * @param endNodeId      目标节点ID
     * @param transportType  通行方式
     * @param strategy       规划策略
     * @param routeNodesJson 路径节点 JSON
     * @param totalDistance  总距离（米）
     * @param estimatedTime  预计耗时（秒）
     * @return 保存后的缓存记录
     */
    RouteCache saveRouteCache(Long startNodeId, Long endNodeId, Integer transportType, String strategy,
                              String routeNodesJson, BigDecimal totalDistance, Integer estimatedTime);
}
