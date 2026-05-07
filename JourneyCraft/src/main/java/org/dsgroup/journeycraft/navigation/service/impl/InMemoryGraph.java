package org.dsgroup.journeycraft.navigation.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.dsgroup.journeycraft.navigation.entity.RoadEdge;
import org.dsgroup.journeycraft.navigation.entity.RoadNode;
import org.dsgroup.journeycraft.navigation.mapper.RoadEdgeMapper;
import org.dsgroup.journeycraft.navigation.mapper.RoadNodeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 路网内存图（CSR 压缩稀疏行格式）
 * <p>
 * 在应用启动时通过 {@link PostConstruct} 将所有启用的路网节点和边加载到内存中，
 * 构建为 CSR 格式供 Dijkstra/A* 等路径规划算法快速遍历，完全消除路径规划过程中的
 * 逐节点/逐边数据库查询。
 * <p>
 * CSR 结构说明：
 * <ul>
 *   <li>{@code nodeOffsets[i]} — 节点 i 的邻接边在 edge* 数组中的起始索引</li>
 *   <li>{@code nodeOffsets[i+1] - nodeOffsets[i]} — 节点 i 的出度</li>
 *   <li>{@code edgeTargets[offsets[i] .. offsets[i+1])} — 邻居节点索引</li>
 *   <li>{@code edgeDistances[ .. ]} — 对应边的距离（米）</li>
 *   <li>{@code edgeTransportTypes[ .. ]} — 对应边的通行方式</li>
 * </ul>
 *
 * @author 后端智能体
 * @since 2026-05-07
 */
@Slf4j
@Component
public class InMemoryGraph {

    @Autowired
    private RoadNodeMapper roadNodeMapper;

    @Autowired
    private RoadEdgeMapper roadEdgeMapper;

    /** 是否已成功加载 */
    private volatile boolean loaded = false;

    // ========== CSR 邻接结构 ==========

    /** 节点偏移数组，长度 = nodeCount + 1 */
    private int[] nodeOffsets;

    /** 邻居节点索引 */
    private int[] edgeTargets;

    /** 边距离（米） */
    private float[] edgeDistances;

    /** 边通行方式（0=未知,1=步行,2=自行车,3=车辆,4=步行+自行车,5=全部） */
    private int[] edgeTransportTypes;

    /** 步行时间（秒） */
    private int[] edgeWalkTimes;

    /** 自行车时间（秒） */
    private int[] edgeBikeTimes;

    /** 电瓶车时间（秒） */
    private int[] edgeShuttleTimes;

    /** 基础拥挤度（0-1） */
    private float[] edgeCongestions;

    /** 是否为反向边（双向边 from→to 正向插入后，to→from 方向时标记 true） */
    private boolean[] edgeIsReverse;

    // ========== 节点数据 ==========

    /** 所有启用的路网节点（按索引顺序） */
    private List<RoadNode> allNodes;

    /** 节点纬度数组（度） */
    private double[] nodeLats;

    /** 节点经度数组（度） */
    private double[] nodeLngs;

    /** 节点 ID → 节点索引 映射 */
    private Map<Long, Integer> nodeIdToIndex;

    // ========== 边迭代器 ==========

    /**
     * 边访问回调函数式接口。
     * <p>
     * 在一次 {@link #forEachEdge(int, EdgeConsumer)} 调用中，
     * 对当前节点的每一条邻接边调用一次此方法。
     */
    @FunctionalInterface
    public interface EdgeConsumer {
        /**
         * @param targetIndex  邻居节点索引
         * @param distance     边距离（米）
         * @param transportType 通行方式（0=未知,1=步行,2=自行车,3=车辆,4=步行+自行车,5=全部）
         * @param walkTime     步行时间（秒）
         * @param bikeTime     自行车时间（秒）
         * @param shuttleTime  电瓶车时间（秒）
         * @param congestion   基础拥挤度（0-1）
         * @param isReverse    是否为反向（遍历双向边时 true — 由原边的 TO→FROM 方向到达）
         */
        void accept(int targetIndex, float distance, int transportType,
                    int walkTime, int bikeTime, int shuttleTime, float congestion,
                    boolean isReverse);
    }

    // ========== 生命周期 ==========

    /**
     * 加载所有启用的路网节点和边到内存中，构建 CSR 结构。
     * <p>
     * 在 Spring 容器初始化完成后自动调用。
     * 如果加载过程发生异常，{@link #isLoaded()} 返回 false，业务层可降级为 DB 查询。
     */
    @PostConstruct
    public void load() {
        long startTime = System.currentTimeMillis();
        try {
            // 1. 加载所有启用的节点
            List<RoadNode> nodes = roadNodeMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RoadNode>()
                            .eq("is_enabled", 1)
                            .eq("is_deleted", 0)
            );
            if (nodes == null || nodes.isEmpty()) {
                log.warn("InMemoryGraph: 未加载到任何路网节点，图加载终止");
                return;
            }
            log.info("InMemoryGraph: 加载 {} 个路网节点", nodes.size());

            int n = nodes.size();
            allNodes = nodes;
            nodeLats = new double[n];
            nodeLngs = new double[n];
            nodeIdToIndex = new HashMap<>(n * 2);

            for (int i = 0; i < n; i++) {
                RoadNode node = nodes.get(i);
                nodeIdToIndex.put(node.getId(), i);
                nodeLats[i] = node.getLatitude() != null ? node.getLatitude().doubleValue() : 0.0;
                nodeLngs[i] = node.getLongitude() != null ? node.getLongitude().doubleValue() : 0.0;
            }

            // 2. 加载所有未删除的边
            List<RoadEdge> allEdges = roadEdgeMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RoadEdge>()
                            .eq("is_deleted", 0)
            );
            log.info("InMemoryGraph: 加载 {} 条边", allEdges != null ? allEdges.size() : 0);

            // 3. 第一遍：计算每个节点的出度（出边 + 双向边的反向）
            int[] degree = new int[n];
            for (RoadEdge edge : allEdges) {
                Integer fromIdxObj = nodeIdToIndex.get(edge.getFromNodeId());
                if (fromIdxObj != null) {
                    degree[fromIdxObj]++;
                }
                // 双向边：反向也可达
                if (Boolean.TRUE.equals(edge.getBidirectional())) {
                    Integer toIdxObj = nodeIdToIndex.get(edge.getToNodeId());
                    if (toIdxObj != null) {
                        degree[toIdxObj]++;
                    }
                }
            }

            // 4. 构建 nodeOffsets 前缀和
            nodeOffsets = new int[n + 1];
            int totalEdges = 0;
            for (int i = 0; i < n; i++) {
                nodeOffsets[i] = totalEdges;
                totalEdges += degree[i];
            }
            nodeOffsets[n] = totalEdges;

            // 5. 分配并行数组
            edgeTargets = new int[totalEdges];
            edgeDistances = new float[totalEdges];
            edgeTransportTypes = new int[totalEdges];
            edgeWalkTimes = new int[totalEdges];
            edgeBikeTimes = new int[totalEdges];
            edgeShuttleTimes = new int[totalEdges];
            edgeCongestions = new float[totalEdges];
            edgeIsReverse = new boolean[totalEdges];

            // 6. 第二遍：填充边数据
            int[] writePos = Arrays.copyOf(nodeOffsets, n);

            for (RoadEdge edge : allEdges) {
                Integer fromIdx = nodeIdToIndex.get(edge.getFromNodeId());
                Integer toIdx = nodeIdToIndex.get(edge.getToNodeId());
                // 正向方向：fromNode → toNode
                if (fromIdx != null && toIdx != null) {
                    int pos = writePos[fromIdx]++;
                    fillEdgeData(pos, edge, toIdx, false);
                }
                // 双向边：反向方向 toNode → fromNode
                if (Boolean.TRUE.equals(edge.getBidirectional()) && toIdx != null && fromIdx != null) {
                    int pos = writePos[toIdx]++;
                    fillEdgeData(pos, edge, fromIdx, true);
                }
            }

            loaded = true;
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("InMemoryGraph: 加载完成 — {} 节点, {} 边, 耗时 {}ms",
                    n, totalEdges, elapsed);

        } catch (Exception e) {
            log.error("InMemoryGraph: 加载失败", e);
            loaded = false;
        }
    }

    /** 填充单个 CSR 槽位 */
    private void fillEdgeData(int pos, RoadEdge edge, int targetIdx, boolean isReverse) {
        edgeTargets[pos] = targetIdx;
        edgeDistances[pos] = edge.getDistance() != null ? edge.getDistance().floatValue() : 0f;
        edgeTransportTypes[pos] = edge.getTransportType() != null ? edge.getTransportType() : 0;
        edgeWalkTimes[pos] = edge.getWalkTime() != null ? edge.getWalkTime() : 0;
        edgeBikeTimes[pos] = edge.getBikeTime() != null ? edge.getBikeTime() : 0;
        edgeShuttleTimes[pos] = edge.getShuttleTime() != null ? edge.getShuttleTime() : 0;
        edgeCongestions[pos] = edge.getBaseCongestion() != null ? edge.getBaseCongestion().floatValue() : 0f;
        edgeIsReverse[pos] = isReverse;
    }

    // ========== 查询方法 ==========

    /** 图是否已成功加载到内存 */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * 遍历节点 nodeIndex 的所有邻接边。
     * <p>
     * 对每一条出边（以及双向边的反向）调用 consumer。
     * 遍历期间不应修改图结构。
     */
    public void forEachEdge(int nodeIndex, EdgeConsumer consumer) {
        if (nodeIndex < 0 || nodeIndex >= nodeOffsets.length - 1) return;
        int start = nodeOffsets[nodeIndex];
        int end = nodeOffsets[nodeIndex + 1];
        for (int i = start; i < end; i++) {
            consumer.accept(
                    edgeTargets[i],
                    edgeDistances[i],
                    edgeTransportTypes[i],
                    edgeWalkTimes[i],
                    edgeBikeTimes[i],
                    edgeShuttleTimes[i],
                    edgeCongestions[i],
                    edgeIsReverse[i]
            );
        }
    }

    /** 节点 ID → 节点索引，未找到返回 -1 */
    public int getNodeIndex(Long nodeId) {
        Integer idx = nodeIdToIndex.get(nodeId);
        return idx != null ? idx : -1;
    }

    /** 根据索引获取完整节点对象 */
    public RoadNode getNodeByIndex(int index) {
        if (index < 0 || index >= allNodes.size()) return null;
        return allNodes.get(index);
    }

    /** 根据索引获取节点 ID */
    public Long getNodeId(int index) {
        if (index < 0 || index >= allNodes.size()) return null;
        return allNodes.get(index).getId();
    }

    /** 获取节点纬度（度） */
    public double getNodeLat(int index) {
        return nodeLats[index];
    }

    /** 获取节点经度（度） */
    public double getNodeLng(int index) {
        return nodeLngs[index];
    }

    /** 节点总数 */
    public int getNodeCount() {
        return allNodes != null ? allNodes.size() : 0;
    }

    /**
     * 查找距离坐标最近的路网节点索引。
     * <p>
     * O(n) Haversine 全扫描，全部在内存中完成。
     *
     * @param lat 目标纬度（度）
     * @param lng 目标经度（度）
     * @return 最近节点索引，图未加载或无节点返回 -1
     */
    public int findNearestIndex(double lat, double lng) {
        if (allNodes == null || allNodes.isEmpty()) return -1;
        int nearestIdx = -1;
        double minDist = Double.MAX_VALUE;

        for (int i = 0; i < allNodes.size(); i++) {
            if (nodeLats[i] == 0.0 && nodeLngs[i] == 0.0) continue;
            double d = haversineMeters(lat, lng, nodeLats[i], nodeLngs[i]);
            if (d < minDist) {
                minDist = d;
                nearestIdx = i;
            }
        }
        return nearestIdx;
    }

    /**
     * 查询 nodeIndex 到 targetIndex 的直接边距离。
     * <p>
     * 遍历 nodeIndex 的邻接表查找 targetIndex，返回距离。
     * 适用于 {@code getDistanceBetweenNodes} 和 {@code getTimeBetweenNodes} 的降级/兼容场景。
     *
     * @param fromIdx 起始节点索引
     * @param toIdx   目标节点索引
     * @return 距离（米），无边连接返回 -1
     */
    public float getEdgeWeight(int fromIdx, int toIdx) {
        if (fromIdx < 0 || fromIdx >= nodeOffsets.length - 1) return -1f;
        int start = nodeOffsets[fromIdx];
        int end = nodeOffsets[fromIdx + 1];
        for (int i = start; i < end; i++) {
            if (edgeTargets[i] == toIdx) {
                return edgeDistances[i];
            }
        }
        return -1f;
    }

    /**
     * 查询从 fromIdx 到 toIdx 的通行时间。
     *
     * @param fromIdx       起始节点索引
     * @param toIdx         目标节点索引
     * @param transportMode 交通方式（1=步行,2=骑行,3=驾驶）
     * @return 时间（秒），无边连接返回 -1
     */
    public int getTimeForEdge(int fromIdx, int toIdx, int transportMode) {
        if (fromIdx < 0 || fromIdx >= nodeOffsets.length - 1) return -1;
        int start = nodeOffsets[fromIdx];
        int end = nodeOffsets[fromIdx + 1];
        for (int i = start; i < end; i++) {
            if (edgeTargets[i] == toIdx) {
                return switch (transportMode) {
                    case 2 -> edgeBikeTimes[i];
                    case 3 -> edgeShuttleTimes[i];
                    default -> edgeWalkTimes[i]; // 1 = walk
                };
            }
        }
        return -1;
    }

    // ========== 静态工具 ==========

    /**
     * Haversine 公式计算两点间直线距离（米）
     */
    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dlat = Math.toRadians(lat2 - lat1);
        double dlon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dlat / 2) * Math.sin(dlat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dlon / 2) * Math.sin(dlon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371000.0 * c;
    }
}
