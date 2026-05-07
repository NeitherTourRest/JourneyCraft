package org.dsgroup.journeycraft.navigation.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.dsgroup.journeycraft.navigation.entity.RouteCache;
import org.dsgroup.journeycraft.navigation.mapper.RouteCacheMapper;
import org.dsgroup.journeycraft.navigation.service.RouteCacheService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 路径规划缓存 Service 实现类
 * <p>
 * 提供缓存查询与写入方法
 *
 * @author 后端智能体
 * @since 2026-04-14
 */
@Slf4j
@Service
public class RouteCacheServiceImpl extends ServiceImpl<RouteCacheMapper, RouteCache> implements RouteCacheService {

    @Override
    public RouteCache findCachedRoute(Long startNodeId, Long endNodeId, Integer transportType, String strategy) {
        return lambdaQuery()
                .eq(RouteCache::getStartNodeId, startNodeId)
                .eq(RouteCache::getEndNodeId, endNodeId)
                .eq(RouteCache::getTransportType, transportType != null ? transportType : 1)
                .eq(RouteCache::getStrategy, strategy != null ? strategy : "shortest_distance")
                .ge(RouteCache::getExpiresAt, new Date())
                .one();
    }

    @Override
    public RouteCache saveRouteCache(Long startNodeId, Long endNodeId, Integer transportType, String strategy,
                                      String routeNodesJson, BigDecimal totalDistance, Integer estimatedTime) {
        RouteCache cache = new RouteCache();
        cache.setStartNodeId(startNodeId);
        cache.setEndNodeId(endNodeId);
        cache.setTransportType(transportType);
        cache.setStrategy(strategy);
        cache.setRouteNodes(routeNodesJson);
        cache.setTotalDistance(totalDistance);
        cache.setTotalTime(estimatedTime);
        cache.setHitCount(0);
        cache.setExpiresAt(new Date(System.currentTimeMillis() + 30 * 60 * 1000L));
        save(cache);
        log.debug("[RouteCache] Saved cache: {}->{} type={} strategy={}", startNodeId, endNodeId, transportType, strategy);
        return cache;
    }
}
